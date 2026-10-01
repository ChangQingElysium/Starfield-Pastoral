package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.TidePhasePayload;
import com.stardew.craft.combat.network.TidePhasePayload.Phase;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.combat.skill.WeaponGroundContact;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Contact bursts and real movement cues. World depth remains enabled throughout. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class TideWeaponVisuals {
    private record Segment(Vec3 a, Vec3 b) {}
    private record Burst(TidePhasePayload event, List<Segment> boundary) {}
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static ClientLevel activeLevel;
    private static final Vec3 UP = new Vec3(0, 1, 0), X = new Vec3(1, 0, 0);
    private TideWeaponVisuals() {}
    public static boolean isAction(String id) {
        return TIDE_MARK.equals(id) || TIDE_ANCHOR.equals(id) || TIDE_READY.equals(id) || TIDE_STAB.equals(id) || TIDE_REEL.equals(id);
    }
    public static void start(WeaponSkillAnimPayload p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || TIDE_READY.equals(p.skillId())) return;
        Vec3 point = new Vec3(p.originX(), p.originY() + 0.8, p.originZ());
        if (mc.player.distanceToSqr(point) > 48 * 48) return;
        mc.level.playLocalSound(point.x, point.y, point.z,
                TIDE_MARK.equals(p.skillId()) ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, TIDE_STAB.equals(p.skillId()) ? 0.26f : 0.4f,
                TIDE_STAB.equals(p.skillId()) ? 1.7f : TIDE_ANCHOR.equals(p.skillId()) ? 0.75f : 1.1f, false);
    }
    public static void phase(TidePhasePayload p) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || mc.player == null || fade(mc.level.getGameTime() - p.tick(), p.duration()) <= 0
                || Math.min(mc.player.distanceToSqr(p.from()), mc.player.distanceToSqr(p.to())) > 48 * 48
                || BURSTS.stream().anyMatch(b -> b.event.equals(p))) return;
        if (BURSTS.size() >= 48) BURSTS.removeFirst();
        List<Segment> boundary = new ArrayList<>();
        // The existing AOE is a box. Trace its actual square footprint instead of promising a circular range.
        if (p.phase() == Phase.ANCHOR && Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
            double radius = com.stardew.craft.entity.projectile.TideAnchorProjectileEntity.AOE_RADIUS;
            Vec3[] corners = {new Vec3(-radius, 0, -radius), new Vec3(radius, 0, -radius),
                    new Vec3(radius, 0, radius), new Vec3(-radius, 0, radius)};
            for (int edge = 0; edge < 4; edge++) {
                Vec3 previous = null;
                for (int step = 0; step <= 12; step++) {
                    Vec3 q = p.to().add(corners[edge].lerp(corners[(edge + 1) % 4], step / 12.0));
                    var hit = WeaponGroundContact.find(mc.level, mc.player, q);
                    Vec3 point = hit == null ? null : hit.getLocation().add(0, 0.035, 0);
                    if (previous != null && point != null && Math.abs(previous.y - point.y) < 0.26)
                        boundary.add(new Segment(previous, point));
                    previous = point;
                }
            }
        }
        BURSTS.add(new Burst(p, boundary));
        Vec3 point = p.to();
        mc.level.playLocalSound(point.x, point.y, point.z,
                p.phase() == Phase.ANCHOR ? SoundEvents.TRIDENT_RIPTIDE_1.value() : SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.PLAYERS, p.phase() == Phase.ANCHOR ? 0.65f : 0.4f, p.phase() == Phase.ANCHOR ? 0.8f : 1.0f, false);
        if (p.phase() == Phase.ANCHOR) mc.level.playLocalSound(point.x, point.y, point.z,
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.65f, 0.8f, false);
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) return;
        for (int i = 0; i < (p.phase() == Phase.ANCHOR ? 16 : 6); i++) {
            double angle = i * Math.PI * 2 / 16;
            mc.level.addParticle(ParticleTypes.SPLASH, point.x, point.y + 0.15, point.z,
                    Math.cos(angle) * 0.18, 0.10 + i % 3 * 0.04, Math.sin(angle) * 0.18);
        }
    }
    static float fade(float age, int duration) {
        if (duration <= 0 || age < 0 || age >= duration) return 0;
        float f = 1 - age / duration; return f * f;
    }
    /** The moving glint indicates force direction, never predicts where the victim will end up. */
    static Vec3 reelPoint(Vec3 from, Vec3 to, double t) {
        double p = Math.clamp(t, 0, 1);
        return from.lerp(to, p).add(0, -0.28 * Math.sin(p * Math.PI), 0);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || mc.isPaused()) return;
        BURSTS.removeIf(b -> mc.level.getGameTime() >= b.event.tick() + b.event.duration());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || BURSTS.isEmpty() || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) return;
        double now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition(); var stack = event.getPoseStack();
        stack.pushPose(); stack.translate(-camera.x, -camera.y, -camera.z);
        var matrix = stack.last().pose(); var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        for (var burst : BURSTS) {
            var p = burst.event; float age = (float) (now - p.tick()), f = fade(age, p.duration());
            if (f <= 0 || Math.min(camera.distanceToSqr(p.from()), camera.distanceToSqr(p.to())) > 48 * 48) continue;
            if (p.phase() == Phase.REEL) {
                for (int i = 0; i < 12; i++) {
                    Vec3 a = reelPoint(p.from(), p.to(), i / 12.0), b = reelPoint(p.from(), p.to(), (i + 1) / 12.0);
                    Vec3 width = b.subtract(a).cross(camera.subtract(a)).normalize().scale((p.empowered() ? 0.04 : 0.025) * f);
                    strip(out, matrix, a, b, width, 43, 204, 228, (int) (175 * f));
                }
                double t = Math.min(1, age / 5);
                Vec3 a = reelPoint(p.from(), p.to(), Math.max(0, t - 0.2)), b = reelPoint(p.from(), p.to(), t);
                strip(out, matrix, a, b, UP.scale(0.045 * f), 226, 255, 255, (int) (245 * f));
            } else {
                for (Vec3 point : p.phase() == Phase.TRANSFER ? new Vec3[]{p.from(), p.to()} : new Vec3[]{p.to()}) {
                    if (camera.distanceToSqr(point) > 48 * 48) continue;
                    Vec3 normal = camera.subtract(point).normalize(), side = normal.cross(UP);
                    side = side.lengthSqr() < 1e-6 ? X : side.normalize();
                    Vec3 up = side.cross(normal).normalize();
                    double radius = p.phase() == Phase.ANCHOR ? 0.25 + age * 0.19 : 0.18 + 0.60 * f;
                    ring(out, matrix, point, side, up, radius, 0.085 * f, 44, 197, 232, (int) (210 * f));
                    ring(out, matrix, point, side, up, radius * 0.86, 0.02 * f, 225, 255, 255, (int) (245 * f));
                    if (p.phase() == Phase.ANCHOR) for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        Vec3 ray = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                        strip(out, matrix, point.add(ray.scale(radius * 0.5)), point.add(ray.scale(radius + f * 0.75)),
                                ray.cross(normal).scale(0.03 * f), 197, 251, 255, (int) (220 * f));
                    }
                }
            }
            for (var segment : burst.boundary) strip(out, matrix, segment.a, segment.b,
                    segment.b.subtract(segment.a).cross(UP).normalize().scale(0.045 * f), 62, 215, 232, (int) (160 * f));
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW); stack.popPose();
    }
    private static void ensureLevel(ClientLevel level) {
        if (level != activeLevel) { activeLevel = level; BURSTS.clear(); }
    }
}
