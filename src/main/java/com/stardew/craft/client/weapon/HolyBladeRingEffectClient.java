package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.combat.skill.WeaponGroundContact;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** One short ground imprint per actual sanctuary pulse, at that pulse's caster position. */
public final class HolyBladeRingEffectClient {
    private record Segment(Vec3 a, Vec3 b) {}
    private record Ring(List<Segment> segments, long tick, int duration) {}
    private static final List<Ring> RINGS = new ArrayList<>();
    private static ClientLevel activeLevel;
    private HolyBladeRingEffectClient() {}
    public static void add(double x, double y, double z, float radius, int duration) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || mc.player == null || radius <= 0 || duration <= 0
                || mc.player.distanceToSqr(x, y, z) > 48 * 48 || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        List<Segment> segments = new ArrayList<>();
        // Actual targeting uses a box: four square corners show its full extent immediately.
        for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
            Vec3 corner = new Vec3(x + sx * radius, y, z + sz * radius);
            probe(segments, corner, corner.add(-sx * Math.min(radius, 1.2), 0, 0));
            probe(segments, corner, corner.add(0, 0, -sz * Math.min(radius, 1.2)));
        }
        // A small inner halo is decoration, not a delayed damage wave.
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16, b = (i + 1) * Math.PI / 16;
            probe(segments, new Vec3(x + Math.cos(a) * 1.15, y, z + Math.sin(a) * 1.15),
                    new Vec3(x + Math.cos(b) * 1.15, y, z + Math.sin(b) * 1.15));
        }
        if (RINGS.size() >= 32) com.stardew.craft.port.PortJava.removeFirst(RINGS);
        RINGS.add(new Ring(List.copyOf(segments), mc.level.getGameTime(), Math.min(20, duration)));
        mc.level.playLocalSound(x, y, z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.3f, 1.4f, false);
    }
    private static void probe(List<Segment> out, Vec3 a, Vec3 b) {
        var mc = Minecraft.getInstance();
        int count = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.22));
        Vec3 previous = null;
        for (int i = 0; i <= count; i++) {
            var hit = WeaponGroundContact.find(mc.level, mc.player, a.lerp(b, i / (double) count));
            Vec3 current = hit == null ? null : hit.getLocation().add(0, 0.028, 0);
            if (previous != null && current != null && Math.abs(previous.y - current.y) < 0.26) out.add(new Segment(previous, current));
            previous = current;
        }
    }
    public static void onClientTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || mc.isPaused()) return;
        RINGS.removeIf(r -> mc.level.getGameTime() - r.tick >= r.duration);
    }
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || RINGS.isEmpty() || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        var stack = event.getPoseStack(); var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        stack.pushPose(); stack.translate(-camera.x, -camera.y, -camera.z);
        double now = mc.level.getGameTime() + event.getPartialTick();
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        for (var ring : RINGS) {
            float fade = opacity((float) (now - ring.tick), ring.duration);
            for (var segment : ring.segments) {
                if (segment.a.distanceToSqr(camera) > 48 * 48) continue;
                Vec3 width = segment.b.subtract(segment.a).cross(new Vec3(0, 1, 0)).normalize();
                WeaponGlowGeometry.strip(out, stack.last().pose(), segment.a, segment.b, width.scale(0.055 * fade), 255, 195, 77, Math.round(115 * fade));
                WeaponGlowGeometry.strip(out, stack.last().pose(), segment.a, segment.b, width.scale(0.016 * fade), 255, 245, 210, Math.round(235 * fade));
            }
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW); stack.popPose();
    }
    static float opacity(float age, int duration) {
        if (age < 0 || duration <= 0 || age >= duration) return 0;
        float f = 1 - age / duration; return f * f;
    }
    private static void ensureLevel(ClientLevel level) {
        if (activeLevel == level) return;
        activeLevel = level; RINGS.clear();
    }
}
