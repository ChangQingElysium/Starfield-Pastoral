package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class YetiFreezeClientState {

    private static final Map<Integer, Long> FROZEN = new ConcurrentHashMap<>();

    private YetiFreezeClientState() {}

    public static void apply(int entityId, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long nowTick = mc.level.getGameTime();
        FROZEN.put(entityId, nowTick + durationTicks);
    }

    public static boolean isFrozen(int entityId, long nowTick) {
        Long end = FROZEN.get(entityId);
        return end != null && nowTick < end;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            FROZEN.clear();
            return;
        }
        long nowTick = mc.level.getGameTime();
        Iterator<Map.Entry<Integer, Long>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            if (nowTick >= entry.getValue()) {
                it.remove();
            }
        }
    }
}
