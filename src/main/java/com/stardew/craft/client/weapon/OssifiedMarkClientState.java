package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OssifiedMarkClientState {

    private static net.minecraft.client.multiplayer.ClientLevel activeLevel;
    private static final Map<Integer, Long> MARKS = new ConcurrentHashMap<>();

    private OssifiedMarkClientState() {}

    public static void apply(int entityId, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null) {
            return;
        }
        long nowTick = mc.level.getGameTime();
        if (durationTicks <= 0) MARKS.remove(entityId);
        else {
            if (MARKS.size() >= 128) MARKS.remove(MARKS.keySet().iterator().next());
            MARKS.put(entityId, nowTick + durationTicks);
        }
    }

    public static boolean isMarked(int entityId, long nowTick) {
        ensureLevel(Minecraft.getInstance().level);
        Long end = MARKS.get(entityId);
        return end != null && nowTick < end;
    }

    public static float fade(int id, double now) {
        Long end = MARKS.get(id);
        return end == null ? 0 : (float)com.stardew.craft.port.PortJava.clamp((end-now)/10, 0, 1);
    }
    private static void ensureLevel(net.minecraft.client.multiplayer.ClientLevel level) {
        if (level != activeLevel) { activeLevel = level; MARKS.clear(); }
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
