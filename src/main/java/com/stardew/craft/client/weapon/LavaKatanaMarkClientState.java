package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LavaKatanaMarkClientState {

    private record MarkData(long endTick, int heat) {}

    private static final Map<Integer, MarkData> MARKS = new ConcurrentHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel activeLevel;

    private LavaKatanaMarkClientState() {}

    public static void apply(int entityId, int remainingTicks, int heat) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ensureLevel(mc.level);
        long nowTick = mc.level.getGameTime();
        int clampedHeat = Math.max(0, heat);
        int clampedRemaining = Math.max(0, remainingTicks);
        if (clampedRemaining == 0) {
            MARKS.remove(entityId);
            return;
        }
        MARKS.put(entityId, new MarkData(nowTick + clampedRemaining, clampedHeat));
    }

    public static boolean isMarked(int entityId, long nowTick) {
        MarkData data = MARKS.get(entityId);
        return data != null && nowTick < data.endTick;
    }

    public static int getHeat(int entityId) {
        MarkData data = MARKS.get(entityId);
        return data != null ? data.heat : 0;
    }

    public static Set<Integer> markedEntityIds() {
        ensureLevel(Minecraft.getInstance().level);
        return Set.copyOf(MARKS.keySet());
    }

    private static void ensureLevel(net.minecraft.client.multiplayer.ClientLevel level) {
        if (activeLevel != level) {
            MARKS.clear();
            activeLevel = level;
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null) {
            MARKS.clear();
            return;
        }
        long nowTick = mc.level.getGameTime();
        Iterator<Map.Entry<Integer, MarkData>> it = MARKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, MarkData> entry = it.next();
            if (nowTick >= entry.getValue().endTick) {
                it.remove();
            }
        }
    }
}
