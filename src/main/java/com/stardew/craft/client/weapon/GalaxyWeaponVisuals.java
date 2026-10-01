package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.GalaxyPhasePayload;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Short world-space accents. Movement is sampled, while starfalls/teleports require server phase events. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class GalaxyWeaponVisuals {
    private record Cast(WeaponSkillAnimPayload payload, long tick) {}
    private record Burst(GalaxyPhasePayload payload, long tick, Vec3 ceiling) {}
    private static final Map<Integer, Cast> CASTS = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static ClientLevel activeLevel;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private GalaxyWeaponVisuals() {}
    public static boolean isAction(String id) {
        return GALAXY_RIFT.equals(id) || GALAXY_JUDGEMENT.equals(id) || GALAXY_READY.equals(id)
                || GALAXY_STAB.equals(id) || GALAXY_LEAP.equals(id);
    }
    public static void start(WeaponSkillAnimPayload p) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || mc.player == null || GALAXY_READY.equals(p.skillId())) return;
        Vec3 origin = origin(p);
        if (mc.player.distanceToSqr(origin) > 48 * 48) return;
        Cast prior = CASTS.get(p.casterEntityId());
        if (prior != null && prior.payload.startGameTick() >= p.startGameTick()) return;
        if (CASTS.size() >= 64) CASTS.remove(CASTS.keySet().iterator().next());
        CASTS.put(p.casterEntityId(), new Cast(p, mc.level.getGameTime()));
        boolean dagger = GALAXY_STAB.equals(p.skillId()) || GALAXY_LEAP.equals(p.skillId());
        mc.level.playLocalSound(origin.x, origin.y, origin.z, SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, dagger ? 0.3f : 0.55f, dagger ? 1.65f : 0.8f, false);
        if (!dagger) mc.level.playLocalSound(origin.x, origin.y, origin.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.4f, GALAXY_RIFT.equals(p.skillId()) ? 1.35f : 0.8f, false);
    }
    public static void phase(GalaxyPhasePayload p) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || mc.player == null || Math.min(mc.player.distanceToSqr(p.from()), mc.player.distanceToSqr(p.to())) > 48 * 48) return;
        if (BURSTS.stream().anyMatch(b -> b.payload.casterId() == p.casterId() && b.payload.tick() == p.tick()
                && b.payload.leap() == p.leap())) return;
        Vec3 bottom = p.to().add(0, 0.15, 0);
        Vec3 ceiling = mc.level.clip(new ClipContext(bottom, bottom.add(0, 3.8, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getLocation();
        if (BURSTS.size() >= 64) com.stardew.craft.port.PortJava.removeFirst(BURSTS);
        BURSTS.add(new Burst(p, mc.level.getGameTime(), ceiling));
        mc.level.playLocalSound(bottom.x, bottom.y, bottom.z,
                p.leap() ? SoundEvents.ENDERMAN_TELEPORT : SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, p.leap() ? 0.5f : 0.65f, p.leap() ? 1.4f : 0.65f, false);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        BURSTS.removeIf(b -> now - b.tick >= 8);
        CASTS.entrySet().removeIf(e -> now - e.getValue().tick >= 10
                || !(mc.level.getEntity(e.getKey()) instanceof Player player) || !player.isAlive()
                || !(player.getMainHandItem().getItem() instanceof IStardewWeapon w)
                || !w.getWeaponId().equals(e.getValue().payload.weaponId()));
    }
    static float fade(float age, float lifetime) {
        if (age < 0 || age >= lifetime) return 0;
        float value = 1 - age / lifetime; return value * value;
    }
    static boolean validDash(Vec3 from, Vec3 to) { return from.distanceToSqr(to) <= 36; }
    private static Vec3 facingSide(Vec3 point, Vec3 camera) {
        Vec3 side = camera.subtract(point).cross(UP);
        return side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
    }
    private static Vec3 origin(WeaponSkillAnimPayload p) { return new Vec3(p.originX(), p.originY() + 0.8, p.originZ()); }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get() || CASTS.isEmpty() && BURSTS.isEmpty()) return;
        float partial = event.getPartialTick();
        double now = mc.level.getGameTime() + partial;
        var stack = event.getPoseStack(); var camera = event.getCamera().getPosition();
        stack.pushPose(); stack.translate(-camera.x, -camera.y, -camera.z);
        var pose = stack.last().pose(); var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        for (var entry : CASTS.entrySet()) {
            Cast cast = entry.getValue(); var p = cast.payload;
            float age = (float) (now - cast.tick), fade = fade(age, 10);
            Vec3 center = origin(p), f = DragonCutlassVisuals.forward(p.yaw()), side = f.cross(UP);
            if (fade == 0 || center.distanceToSqr(camera) > 48 * 48) continue;
            if (GALAXY_RIFT.equals(p.skillId()) && mc.level.getEntity(entry.getKey()) instanceof Player player) {
                Vec3 end = player.getPosition(partial).add(0, 0.8, 0);
                if (!validDash(center, end) || age > 6) continue;
                for (int sign : new int[]{-1, 1}) {
                    Vec3 offset = side.scale(sign * 0.3);
                    strip(out, pose, center.add(offset), end.add(offset), UP.scale(0.045 * fade), 112, 116, 250, (int) (170 * fade));
                    strip(out, pose, center.add(offset), end.add(offset), UP.scale(0.012 * fade), 224, 248, 255, (int) (240 * fade));
                }
            } else if (GALAXY_JUDGEMENT.equals(p.skillId())) {
                double radius = 0.4 + Math.min(age / 2.22, 1) * 2.6;
                ring(out, pose, center, side, f, radius, 0.10 * fade, 119, 136, 255, (int) (180 * fade));
                ring(out, pose, center, side, f, radius * 0.97, 0.023 * fade, 228, 249, 255, (int) (235 * fade));
            }
        }
        for (Burst b : BURSTS) {
            var p = b.payload; float age = (float) (now - b.tick), fade = fade(age, 8);
            if (fade == 0) continue;
            if (p.leap()) {
                // Endpoint gates, not a fictitious flight path through the intervening blocks.
                for (Vec3 point : new Vec3[]{p.from(), p.to()}) {
                    if (point.distanceToSqr(camera) > 48 * 48) continue;
                    point = point.add(0, 0.8, 0);
                    Vec3 side = facingSide(point, camera);
                    ring(out, pose, point, side, UP, 0.38 + age * 0.025, 0.065 * fade, 144, 115, 255, (int) (200 * fade));
                    strip(out, pose, point.add(0, -0.55, 0), point.add(0, 0.55, 0), side.scale(0.025 * fade), 225, 249, 255, (int) (240 * fade));
                }
            } else {
                Vec3 point = p.to().add(0, 0.15, 0);
                if (point.distanceToSqr(camera) > 48 * 48) continue;
                Vec3 side = facingSide(point, camera);
                // Already landed on receipt. The brief column is an impact afterglow, clipped by the local ceiling.
                strip(out, pose, point, b.ceiling, side.scale(0.14 * fade), 123, 100, 255, (int) (145 * fade));
                strip(out, pose, point, b.ceiling, side.scale(0.035 * fade), 235, 251, 255, (int) (235 * fade));
                ring(out, pose, point, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.3 + Math.min(age / 4, 1) * 3.7,
                        0.10 * fade, 149, 162, 255, (int) (210 * fade));
            }
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW); stack.popPose();
    }
    private static void ensureLevel(ClientLevel level) {
        if (level == activeLevel) return;
        activeLevel = level; CASTS.clear(); BURSTS.clear();
    }
}
