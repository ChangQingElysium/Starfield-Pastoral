package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Existing frost-fang texture follows the target's visible surface, with world occlusion. */
public final class YetiMarkRenderer {
    private static final RenderType MATERIAL = WeaponEffectRenderTypes.MOLTEN_GLOW;
    private YetiMarkRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        var ids = YetiMarkClientState.markedEntityIds();
        if (ids.isEmpty()) return;
        float partial = event.getPartialTick();
        long tick = mc.level.getGameTime();
        Vec3 camera = event.getCamera().getPosition();
        var stack = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(MATERIAL);
        for (int id : ids) {
            if (!(mc.level.getEntity(id) instanceof LivingEntity target) || !target.isAlive()
                    || target.distanceToSqr(camera) > 32 * 32 || !YetiMarkClientState.isMarked(id, tick)) continue;
            var bounds = target.getBoundingBox().move(target.getPosition(partial).subtract(target.position()));
            Vec3 center = bounds.getCenter();
            Vec3 point = bounds.clip(camera, center).orElse(center)
                    .add(camera.subtract(center).normalize().scale(0.04)).subtract(camera);
            float size = 0.25f * (1 + 0.055f * (float) Math.sin((tick + partial) * 0.55));
            stack.pushPose(); stack.translate(point.x, point.y, point.z);
            stack.mulPose(event.getCamera().rotation());
            Matrix4f pose = stack.last().pose();
            WeaponEffectShapes.mark(out, pose, WeaponEffectShapes.Mark.FROST, size, 151, 228, 255, 230);
            stack.popPose();
        }
        buffers.endBatch(MATERIAL);
    }

}
