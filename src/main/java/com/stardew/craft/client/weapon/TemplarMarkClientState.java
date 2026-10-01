package com.stardew.craft.client.weapon;

import com.stardew.craft.combat.network.TemplarMarkPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

public final class TemplarMarkClientState {
    record Key(int caster, long cast, int target) {}
    private static final Map<Key, TemplarMarkPayload> MARKS = new HashMap<>();
    private static ClientLevel activeLevel;
    private TemplarMarkClientState() {}
    static Key key(TemplarMarkPayload p) { return new Key(p.casterId(), p.castTick(), p.entityId()); }
    public static void apply(TemplarMarkPayload p) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null) return;
        if (p.durationTicks() <= 0) { MARKS.remove(key(p)); return; }
        if (mc.level.getGameTime() >= p.castTick() + p.durationTicks()) return;
        if (MARKS.size() >= 256) MARKS.remove(MARKS.keySet().iterator().next());
        MARKS.put(key(p), p);
    }
    public static boolean isMarked(int entityId, long now) { return progress(entityId, now) >= 0; }
    public static float progress(int entityId, double now) {
        ensureLevel(Minecraft.getInstance().level);
        float latest = -1;
        for (var p : MARKS.values()) if (p.entityId() == entityId && now >= p.castTick() && now < p.castTick() + p.durationTicks())
            latest = Math.max(latest, (float) ((now - p.castTick()) / p.durationTicks()));
        return latest;
    }
    public static void onClientTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || mc.isPaused()) return;
        MARKS.entrySet().removeIf(e -> mc.level.getGameTime() >= e.getValue().castTick() + e.getValue().durationTicks());
    }
    private static void ensureLevel(ClientLevel level) {
        if (activeLevel == level) return;
        activeLevel = level; MARKS.clear();
    }
}
