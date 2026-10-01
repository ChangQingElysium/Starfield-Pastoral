package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class YetiMarkClientState {

    private static final Map<Integer, Long> MARKS = new ConcurrentHashMap<>();

    private static net.minecraft.client.multiplayer.ClientLevel activeLevel;

    private YetiMarkClientState() {}

    public static void apply(int entityId, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ensureLevel(mc.level);
        long nowTick = mc.level.getGameTime();
        MARKS.put(entityId, nowTick + durationTicks);
    }

    public static java.util.Set<Integer> markedEntityIds() {
        ensureLevel(Minecraft.getInstance().level);
        return java.util.Set.copyOf(MARKS.keySet());
    }
    private static void ensureLevel(net.minecraft.client.multiplayer.ClientLevel level) {
        if (activeLevel == level) return;
        activeLevel = level;
        MARKS.clear();
    }

    public static boolean isMarked(int entityId, long nowTick) {
        Long end = MARKS.get(entityId);
        return end != null && nowTick < end;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
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
