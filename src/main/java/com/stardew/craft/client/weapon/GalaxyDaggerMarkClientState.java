package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.ParticleTypes;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GalaxyDaggerMarkClientState {

    private record MarkData(long endTick, int durationTicks) {}

    private static final Map<Integer, MarkData> MARKS = new ConcurrentHashMap<>();

    private static net.minecraft.client.multiplayer.ClientLevel activeLevel;
    public static java.util.Set<Integer> markedEntityIds() {
        ensureLevel();
        return java.util.Set.copyOf(MARKS.keySet());
    }
    private static void ensureLevel() {
        var level = Minecraft.getInstance().level;
        if (level == activeLevel) return;
        activeLevel = level; MARKS.clear();
    }

    private GalaxyDaggerMarkClientState() {}

    @SuppressWarnings("null")
    public static void apply(int entityId, int durationTicks) {
        ensureLevel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var level = mc.level;
        int clampedDuration = Math.max(0, durationTicks);
        if (clampedDuration == 0) {
            MARKS.remove(entityId);
            return;
        }
        long nowTick = level.getGameTime();
        MARKS.put(entityId, new MarkData(nowTick + clampedDuration, clampedDuration));
    }

    public static boolean isMarked(int entityId, long nowTick) {
        MarkData data = MARKS.get(entityId);
        return data != null && nowTick < data.endTick;
    }

    public static float getRemainingRatio(int entityId, long nowTick) {
        MarkData data = MARKS.get(entityId);
        if (data == null) {
            return 0.0f;
        }
        float remaining = Math.max(0.0f, data.endTick - nowTick);
        return Math.min(1.0f, remaining / Math.max(1.0f, data.durationTicks));
    }

    @SuppressWarnings("null")
    public static void onClientTick(ClientTickEvent.Post event) {
        ensureLevel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            MARKS.clear();
            return;
        }
        var level = mc.level;
        long nowTick = level.getGameTime();
        Iterator<Map.Entry<Integer, MarkData>> it = MARKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, MarkData> entry = it.next();
            MarkData data = entry.getValue();
            if (nowTick >= data.endTick) {
                it.remove();
                continue;
            }
            if (!com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get() || mc.isPaused()) continue;
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || mc.player == null || living.distanceToSqr(mc.player) > 32 * 32) {
                continue;
            }
            if (((nowTick + entry.getKey()) & 1) != 0) {
                continue;
            }

            double x = living.getX();
            double y = living.getY() + living.getBbHeight() * 0.6;
            double z = living.getZ();

            double radius = 0.38;
            double angle = (nowTick * 0.35 + entry.getKey()) * 0.35;
            double ox = Mth.cos((float) angle) * radius;
            double oz = Mth.sin((float) angle) * radius;

            level.addParticle(ParticleTypes.END_ROD, x + ox, y, z + oz, 0.0, 0.01, 0.0);
            level.addParticle(ParticleTypes.ENCHANT, x - ox, y, z - oz, 0.0, 0.01, 0.0);
        }
    }
}
