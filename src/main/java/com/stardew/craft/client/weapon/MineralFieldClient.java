package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.combat.skill.WeaponGroundContact;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** Bounded release-scoped fields. Ground probes are cached once; pulses require a server phase packet. */
final class MineralFieldClient {
    record Key(int casterId, long castTick) {}
    record Segment(Vec3 a, Vec3 b, int index) {}
    private static final class Field {
        final Vec3 center;
        final long start;
        final int duration;
        final List<Segment> segments;
        long pulse = Long.MIN_VALUE;
        Field(Vec3 center, long start, int duration, List<Segment> segments) {
            this.center = center; this.start = start; this.duration = duration; this.segments = segments;
        }
    }
    private final boolean bone;
    private final Map<Key, Field> fields = new LinkedHashMap<>();
    // Tombstones prevent a delayed phase from reviving a cancelled release.
    private final Map<Key, Long> ended = new LinkedHashMap<>();
    private ClientLevel level;
    MineralFieldClient(boolean bone) { this.bone = bone; }
    private void ensureLevel() {
        var current = Minecraft.getInstance().level;
        if (current != level) { level = current; fields.clear(); ended.clear(); }
    }
    void accept(int casterId, long castTick, int phase, Vec3 center, float yaw, float size, int duration) {
        ensureLevel();
        if (level == null) return;
        var key = new Key(casterId, castTick);
        long now = level.getGameTime();
        if (phase == 2) {
            fields.remove(key); ended.put(key, now);
            while (ended.size() > 64) ended.remove(ended.keySet().iterator().next());
            return;
        }
        if (ended.containsKey(key)) return;
        Field existing = fields.get(key);
        if (phase == 1) { if (existing != null) existing.pulse = now; return; }
        var mc = Minecraft.getInstance();
        if (phase != 0 || existing != null || duration <= 0 || duration > 100 || size <= 0 || size > 16
                || !Float.isFinite(size) || !Double.isFinite(center.lengthSqr()) || !Float.isFinite(yaw)
                || mc.player == null || mc.player.distanceToSqr(center) > 48*48
                || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        var segments = new ArrayList<Segment>();
        Vec3 forward = Vec3.directionFromRotation(0, yaw), side = new Vec3(forward.z, 0, -forward.x);
        int count = bone ? 36 : 24;
        for (int i = 0; i < count; i++) {
            Vec3 a, b;
            if (bone) {
                double angle = i * Math.PI*2/count;
                double end = angle + Math.PI*2/count*0.7;
                a = center.add(Math.cos(angle)*size, 0, Math.sin(angle)*size);
                b = center.add(Math.cos(end)*size, 0, Math.sin(end)*size);
            } else {
                a = center.add(forward.scale(-size/2 + size*i/count)).add(side.scale(jag(i)));
                b = center.add(forward.scale(-size/2 + size*(i+1)/count)).add(side.scale(jag(i+1)));
            }
            addGroundSegment(segments, a, b, i);
        }
        if (bone) {
            // Damage uses the original square AABB; pulling and crit bonus use the inner circle.
            int index = 36;
            for (int x : new int[]{-1,1}) for (int z : new int[]{-1,1}) {
                Vec3 corner = center.add(x*size,0,z*size);
                addGroundSegment(segments, corner.add(-x*0.6,0,0), corner, index++);
                addGroundSegment(segments, corner, corner.add(0,0,-z*0.6), index++);
            }
        }
        if (fields.size() >= 32) fields.remove(fields.keySet().iterator().next());
        fields.put(key, new Field(center, now, duration, segments));
    }
    private void addGroundSegment(List<Segment> segments, Vec3 a, Vec3 b, int index) {
        var player = Minecraft.getInstance().player;
        var floorA = WeaponGroundContact.find(level, player, a);
        var floorB = WeaponGroundContact.find(level, player, b);
        if (floorA != null && floorB != null && Math.abs(floorA.getLocation().y-floorB.getLocation().y) < 0.2)
            segments.add(new Segment(floorA.getLocation().add(0,0.025,0), floorB.getLocation().add(0,0.025,0), index));
    }
    private static double jag(int i) { return Math.sin(i*2.13)*0.075 + Math.sin(i*0.83)*0.055; }
    void tick() {
        ensureLevel();
        if (level == null || Minecraft.getInstance().isPaused()) return;
        long now = level.getGameTime();
        fields.values().removeIf(f -> now - f.start >= f.duration);
        ended.values().removeIf(t -> now-t > 120);
    }
    void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ensureLevel();
        if (level == null || fields.isEmpty() || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        var mc = Minecraft.getInstance(); var camera = event.getCamera().getPosition();
        double now = level.getGameTime() + event.getPartialTick();
        var stack = event.getPoseStack(); var buffers = mc.renderBuffers().bufferSource();
        stack.pushPose(); stack.translate(-camera.x, -camera.y, -camera.z);
        for (boolean edge : new boolean[]{true, false}) {
            var type = edge ? WeaponEffectRenderTypes.IMPACT_EDGE : WeaponEffectRenderTypes.MOLTEN_GLOW;
            var out = buffers.getBuffer(type);
            for (Field f : fields.values()) {
                if (f.center.distanceToSqr(camera) > 48*48) continue;
                float age = (float)(now-f.start), fade = Math.min(1, age/3)*Math.clamp((f.duration-age)/6, 0, 1);
                float pulse = f.pulse == Long.MIN_VALUE ? -1 : (float)(now-f.pulse);
                for (int i = 0; i < f.segments.size(); i++) {
                    Segment segment = f.segments.get(i);
                    MineralEffectGeometry.fieldSegment(out, stack.last().pose(), segment.a, segment.b,
                            f.center, bone, segment.index, age, pulse, fade, edge);
                }
            }
            buffers.endBatch(type);
        }
        stack.popPose();
    }
}
