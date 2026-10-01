package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.InfinityPhasePayload;
import com.stardew.craft.combat.network.InfinityPhasePayload.Phase;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Visible field outlines and confirmed stage accents; no screen distortion or predicted damage pulses. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class InfinityWeaponVisuals {
    private record Key(int caster, boolean evolve) {}
    private static final Map<Key, InfinityPhasePayload> FIELDS = new HashMap<>();
    private static final Map<Key, Long> SEEN = new HashMap<>();
    private static final List<InfinityPhasePayload> BURSTS = new ArrayList<>();
    private static ClientLevel activeLevel;
    private static final Vec3 UP = new Vec3(0, 1, 0), X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
    private InfinityWeaponVisuals() {}
    public static boolean isAction(String id) {
        return INFINITY_EVOLVE.equals(id) || INFINITY_RELEASE.equals(id) || INFINITY_COLLAPSE.equals(id)
                || INFINITY_READY.equals(id) || INFINITY_STAB.equals(id) || INFINITY_BACK.equals(id);
    }
    public static void start(WeaponSkillAnimPayload p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || INFINITY_READY.equals(p.skillId())) return;
        Vec3 point = new Vec3(p.originX(), p.originY() + 0.8, p.originZ());
        if (mc.player.distanceToSqr(point) > 48 * 48) return;
        boolean stab = INFINITY_STAB.equals(p.skillId()) || INFINITY_BACK.equals(p.skillId());
        mc.level.playLocalSound(point.x, point.y, point.z, stab || INFINITY_RELEASE.equals(p.skillId())
                        ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, stab ? 0.3f : 0.4f, stab ? 1.7f : 0.75f, false);
    }
    static boolean isNewCast(long incoming, Long previous) { return previous == null || incoming > previous; }
    static boolean matchesCast(long incoming, long active) { return incoming == active; }
    public static void phase(InfinityPhasePayload p) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null) return;
        boolean evolve = p.phase() == Phase.EVOLVE || p.phase() == Phase.RELEASE || p.phase() == Phase.END_EVOLVE;
        Key key = new Key(p.casterId(), evolve);
        if (p.phase() == Phase.EVOLVE || p.phase() == Phase.COLLAPSE) {
            if (!isNewCast(p.castTick(), SEEN.get(key))) return;
            if (FIELDS.size() >= 64) FIELDS.remove(FIELDS.keySet().iterator().next());
            FIELDS.put(key, p); SEEN.put(key, p.castTick());
            return;
        }
        if (p.phase() == Phase.END_EVOLVE || p.phase() == Phase.END_COLLAPSE || p.phase() == Phase.RELEASE) {
            var field = FIELDS.get(key);
            if (field != null && matchesCast(p.castTick(), field.castTick())) FIELDS.remove(key);
            SEEN.merge(key, p.castTick(), Math::max);
            if (p.phase() != Phase.RELEASE) return;
        }
        if (mc.player == null || Math.min(mc.player.distanceToSqr(p.from()), mc.player.distanceToSqr(p.to())) > 48 * 48
                || mc.level.getGameTime() > p.tick() + p.duration()) return;
        if (BURSTS.stream().anyMatch(b -> b.casterId() == p.casterId() && b.castTick() == p.castTick()
                && b.tick() == p.tick() && b.phase() == p.phase())) return;
        if (BURSTS.size() >= 64) BURSTS.removeFirst();
        BURSTS.add(p);
        Vec3 point = p.to();
        mc.level.playLocalSound(point.x, point.y, point.z, p.phase() == Phase.LEAP ? SoundEvents.ENDERMAN_TELEPORT
                        : p.phase() == Phase.FINISH ? SoundEvents.RESPAWN_ANCHOR_DEPLETE.value() : SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, p.phase() == Phase.FINISH ? 0.65f : 0.4f, p.phase() == Phase.PULSE ? 1.2f : 0.7f, false);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        FIELDS.entrySet().removeIf(e -> now > e.getValue().tick() + e.getValue().duration()
                || !(mc.level.getEntity(e.getKey().caster) instanceof Player player) || !player.isAlive());
        SEEN.entrySet().removeIf(e -> now - e.getValue() > 160);
        BURSTS.removeIf(p -> now >= p.tick() + p.duration());
    }
    static float fade(float age, int duration) {
        if (age < 0 || age >= duration) return 0;
        float f = 1 - age / duration; return f * f;
    }
    static double pullRadius(float progress) { return 3.6 - 2.7 * Math.clamp(progress, 0, 1); }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean() || FIELDS.isEmpty() && BURSTS.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        var stack = event.getPoseStack(); var camera = event.getCamera().getPosition();
        stack.pushPose(); stack.translate(-camera.x, -camera.y, -camera.z);
        var pose = stack.last().pose(); var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        for (var field : FIELDS.values()) {
            float age = (float) (now - field.tick());
            if (age < 0 || age > field.duration()) continue;
            Vec3 center = field.from();
            if (field.phase() == Phase.EVOLVE && mc.level.getEntity(field.casterId()) instanceof Player player) center = player.getPosition(partial);
            center = center.add(0, 0.3, 0);
            if (center.distanceToSqr(camera) > 48 * 48) continue;
            int red = field.evolved() ? 255 : 150, green = field.evolved() ? 199 : 161, blue = field.evolved() ? 108 : 255;
            ring(out, pose, center, X, Z, 4, 0.035, red, green, blue, 95);
            double radius = field.phase() == Phase.EVOLVE ? pullRadius(age / field.duration()) : 0.8 + 0.08 * Math.sin(age * 0.35);
            ring(out, pose, center, X, Z, radius, 0.065, red, green, blue, 160);
            // Thin broken spokes spiral inward, leaving creatures and terrain readable.
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4 + age * 0.06;
                Vec3 ray = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 across = ray.cross(UP);
                Vec3 a = center.add(ray.scale(radius + 0.55));
                Vec3 b = center.add(ray.scale(radius)).add(0, 0.12, 0);
                strip(out, pose, a, b, across.scale(0.018), 255, 244, 218, 180);
            }
        }
        for (var burst : BURSTS) {
            float age = (float) (now - burst.tick()), fade = fade(age, burst.duration());
            if (fade <= 0) continue;
            boolean leap = burst.phase() == Phase.LEAP, finish = burst.phase() == Phase.FINISH;
            for (Vec3 point : leap ? new Vec3[]{burst.from(), burst.to()} : new Vec3[]{burst.to()}) {
                if (point.distanceToSqr(camera) > 48 * 48) continue;
                point = point.add(0, leap ? 0.9 : 0.4, 0);
                Vec3 side = camera.subtract(point).cross(UP);
                side = side.lengthSqr() < 1e-6 ? X : side.normalize();
                if (leap) {
                    ring(out, pose, point, side, UP, 0.16 + 0.5 * fade, 0.075 * fade, 249, 200, 112, (int) (220 * fade));
                    strip(out, pose, point.add(0, -0.6, 0), point.add(0, 0.6, 0), side.scale(0.022 * fade), 255, 249, 229, (int) (240 * fade));
                } else {
                    double radius = finish ? 0.35 + age * 0.33 : burst.phase() == Phase.RELEASE ? 0.3 + age * 0.38 : 0.35 + fade * 2.2;
                    ring(out, pose, point, X, Z, radius, (finish ? 0.13 : 0.07) * fade, 249, 203, 116, (int) (220 * fade));
                    for (int i = 0; i < (finish ? 8 : 4); i++) {
                        double angle = i * Math.PI * 2 / (finish ? 8 : 4) + 0.4;
                        Vec3 ray = side.scale(Math.cos(angle)).add(UP.scale(Math.sin(angle)));
                        strip(out, pose, point, point.add(ray.scale((finish ? 2.4 : 0.7) * fade)),
                                ray.cross(camera.subtract(point).normalize()).scale(0.027 * fade), 255, 249, 224, (int) (235 * fade));
                    }
                }
            }
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW); stack.popPose();
    }
    private static void ensureLevel(ClientLevel level) {
        if (level == activeLevel) return;
        activeLevel = level; FIELDS.clear(); SEEN.clear(); BURSTS.clear();
    }
}
