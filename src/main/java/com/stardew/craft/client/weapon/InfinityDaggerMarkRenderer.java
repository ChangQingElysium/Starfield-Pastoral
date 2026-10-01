package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Existing star texture follows the target's visible surface, with world occlusion. */
public final class InfinityDaggerMarkRenderer {
    private static final RenderType MATERIAL = WeaponEffectRenderTypes.MOLTEN_GLOW;
    private InfinityDaggerMarkRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        var ids = InfinityDaggerMarkClientState.markedEntityIds();
        if (ids.isEmpty()) return;
        float partial = event.getPartialTick();
        long tick = mc.level.getGameTime();
        Vec3 camera = event.getCamera().getPosition();
        var stack = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(MATERIAL);
        for (int id : ids) {
            if (!(mc.level.getEntity(id) instanceof LivingEntity target) || !target.isAlive()
                    || target.distanceToSqr(camera) > 32 * 32 || !InfinityDaggerMarkClientState.isMarked(id, tick)) continue;
            var bounds = target.getBoundingBox().move(target.getPosition(partial).subtract(target.position()));
            Vec3 center = bounds.getCenter();
            Vec3 point = bounds.clip(camera, center).orElse(center)
                    .add(camera.subtract(center).normalize().scale(0.04)).subtract(camera);
            float size = 0.25f * (1 + 0.055f * (float) Math.sin((tick + partial) * 0.55));
            int alpha = (int) (230 * Math.min(1, InfinityDaggerMarkClientState.getRemainingRatio(id, tick) / 0.2f));
            stack.pushPose(); stack.translate(point.x, point.y, point.z);
            stack.mulPose(event.getCamera().rotation());
            Matrix4f pose = stack.last().pose();
            WeaponEffectShapes.mark(out, pose, WeaponEffectShapes.Mark.INFINITY, size, 255, 211, 131, alpha);
            stack.popPose();
        }
        buffers.endBatch(MATERIAL);
    }

}
