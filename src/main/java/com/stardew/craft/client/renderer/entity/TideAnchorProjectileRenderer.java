package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.entity.projectile.TideAnchorProjectileEntity;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class TideAnchorProjectileRenderer extends EntityRenderer<TideAnchorProjectileEntity> {

    private record Sample(net.minecraft.world.phys.Vec3 point, long tick) {}
    private final java.util.Map<Integer, java.util.ArrayDeque<Sample>> wakes = new java.util.HashMap<>();
    private net.minecraft.world.level.Level wakeLevel;

    public TideAnchorProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(@SuppressWarnings("null") TideAnchorProjectileEntity entity) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS; // Required renderer API; geometry does not sample an image.
    }

    @SuppressWarnings("null")
    @Override
    public void render(@SuppressWarnings("null") TideAnchorProjectileEntity entity, float entityYaw, float partialTicks,
                       @SuppressWarnings("null")    PoseStack poseStack, @SuppressWarnings("null")    MultiBufferSource buffer, int packedLight) {
        renderWake(entity, partialTicks, poseStack, buffer);
        poseStack.pushPose();
        poseStack.scale(0.9f, 0.9f, 0.9f);
        var direction = entity.getDeltaMovement().normalize();
        if (direction.lengthSqr() > 1e-6) poseStack.mulPose(new org.joml.Quaternionf().rotationTo(
                0, 1, 0, (float) direction.x, (float) direction.y, (float) direction.z));

        com.stardew.craft.client.weapon.WeaponProjectileGeometry.render(
                buffer.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.PROJECTILE_BODY),
                poseStack.last().pose(), com.stardew.craft.client.weapon.WeaponProjectileGeometry.Shape.ANCHOR);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void renderWake(TideAnchorProjectileEntity entity, float partial, PoseStack stack, MultiBufferSource buffers) {
        if (wakeLevel != entity.level()) { wakes.clear(); wakeLevel = entity.level(); }
        long tick = entity.level().getGameTime();
        wakes.entrySet().removeIf(e -> e.getValue().isEmpty() || tick - e.getValue().getLast().tick > 8);
        if (!com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) { wakes.clear(); return; }
        var camera = this.entityRenderDispatcher.camera.getPosition();
        if (entity.distanceToSqr(camera) > 48 * 48) return;
        if (!wakes.containsKey(entity.getId()) && wakes.size() >= 64) wakes.remove(wakes.keySet().iterator().next());
        var samples = wakes.computeIfAbsent(entity.getId(), id -> new java.util.ArrayDeque<>());
        var current = entity.getPosition(partial);
        if (samples.isEmpty() || samples.getLast().tick != tick) {
            var previous = new net.minecraft.world.phys.Vec3(entity.xo, entity.yo, entity.zo);
            if (!samples.isEmpty() && samples.getLast().point.distanceToSqr(previous) > 16) samples.clear();
            samples.addLast(new Sample(previous, tick));
            while (samples.size() > 7) samples.removeFirst();
        }
        stack.pushPose(); stack.translate(-current.x, -current.y, -current.z);
        var pose = stack.last().pose();
        var out = buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW);
        Sample last = null;
        for (Sample sample : samples) {
            if (last != null) wakeSegment(out, pose, last.point, sample.point, camera, (float) Math.max(0, 1 - (tick + partial - last.tick) / 8));
            last = sample;
        }
        if (last != null) wakeSegment(out, pose, last.point, current, camera, 1);
        stack.popPose();
    }
    private static void wakeSegment(VertexConsumer out, Matrix4f pose, net.minecraft.world.phys.Vec3 a,
                                    net.minecraft.world.phys.Vec3 b, net.minecraft.world.phys.Vec3 camera, float fade) {
        var width = b.subtract(a).cross(camera.subtract(b)).normalize();
        com.stardew.craft.client.weapon.WeaponGlowGeometry.strip(out, pose, a, b, width.scale(0.12 * fade),
                27, 169, 203, (int) (125 * fade * fade));
        com.stardew.craft.client.weapon.WeaponGlowGeometry.strip(out, pose, a, b, width.scale(0.025 * fade),
                209, 255, 255, (int) (235 * fade * fade));
    }


}
