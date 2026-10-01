package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Contracting brackets telegraph the deadline; the actual verdict arrives only on applied damage. */
public final class TemplarMarkRenderer {
    private TemplarMarkRenderer() {}
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        var camera = event.getCamera().getPosition(); var stack = event.getPoseStack();
        var entities = mc.level.getEntitiesOfClass(LivingEntity.class, new AABB(camera, camera).inflate(48),
                e -> e.isAlive() && e.distanceToSqr(camera) <= 48 * 48);
        var buffers = mc.renderBuffers().bufferSource();
        stack.pushPose(); stack.translate(-camera.x, -camera.y, -camera.z);
        for (int pass = 0; pass < 2; pass++) {
            boolean edge = pass == 0;
            var type = edge ? WeaponEffectRenderTypes.IMPACT_EDGE : WeaponEffectRenderTypes.MOLTEN_GLOW;
            var out = buffers.getBuffer(type);
            for (var entity : entities) {
                float progress = TemplarMarkClientState.progress(entity.getId(), now);
                if (progress < 0) continue;
                var box = entity.getBoundingBox().move(entity.getPosition(partial).subtract(entity.position()));
                Vec3 center = box.getCenter();
                Vec3 normal = camera.subtract(center).normalize();
                Vec3 at = box.clip(camera, center).orElse(center).add(normal.scale(edge ? 0.045 : 0.055));
                Vec3 right = normal.cross(new Vec3(0, 1, 0)).normalize();
                if (right.lengthSqr() < 1.0E-6) right = new Vec3(1, 0, 0);
                Vec3 up = right.cross(normal).normalize();
                double size = 0.23 + (1 - progress) * 0.16;
                int alpha = Math.round((180 + 65 * progress) * Math.min(1, progress * 25));
                for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
                    Vec3 corner = at.add(right.scale(sx * size)).add(up.scale(sy * size * 1.25));
                    strip(out, stack.last().pose(), corner, corner.subtract(right.scale(sx * 0.13)), up.scale(edge ? 0.025 : 0.012),
                            edge ? 45 : 255, edge ? 29 : 230, edge ? 12 : 157, alpha);
                    strip(out, stack.last().pose(), corner, corner.subtract(up.scale(sy * 0.15)), right.scale(edge ? 0.025 : 0.012),
                            edge ? 45 : 255, edge ? 29 : 230, edge ? 12 : 157, alpha);
                }
                if (!edge) {
                    strip(out, stack.last().pose(), at.subtract(up.scale(0.18)), at.add(up.scale(0.24)), right.scale(0.008), 255, 246, 206, alpha);
                    strip(out, stack.last().pose(), at.subtract(right.scale(0.1)), at.add(right.scale(0.1)), up.scale(0.008), 255, 246, 206, alpha);
                }
            }
            buffers.endBatch(type);
        }
        stack.popPose();
    }
}
