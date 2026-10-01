package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TideMarkClientState {

    private static final Map<Integer, Long> MARKS = new ConcurrentHashMap<>();

    private static net.minecraft.client.multiplayer.ClientLevel activeLevel;
    private TideMarkClientState() {}
    private static void ensureLevel() {
        var level = Minecraft.getInstance().level;
        if (level != activeLevel) { MARKS.clear(); activeLevel = level; }
    }
    public static java.util.Set<Integer> markedEntityIds() { ensureLevel(); return java.util.Set.copyOf(MARKS.keySet()); }
    public static float getRemainingRatio(int id, long tick) {
        return com.stardew.craft.port.PortJava.clamp((MARKS.getOrDefault(id, tick) - tick) / 100f, 0, 1);
    }

    public static void apply(int entityId, int durationTicks) {
        ensureLevel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long nowTick = mc.level.getGameTime();
        MARKS.put(entityId, nowTick + durationTicks);
    }

    public static boolean isMarked(int entityId, long nowTick) {
        Long end = MARKS.get(entityId);
        return end != null && nowTick < end;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        ensureLevel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            MARKS.clear();
            return;
        }
        long nowTick = mc.level.getGameTime();
        Iterator<Map.Entry<Integer, Long>> it = MARKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            if (nowTick >= entry.getValue()) {
                it.remove();
            }
        }
    }
}
