package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** An emissive heat brand attached to the target surface, with normal world occlusion. */
public final class LavaKatanaMarkRenderer {
    private static final RenderType MATERIAL = WeaponEffectRenderTypes.MOLTEN_GLOW;

    private LavaKatanaMarkRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var ids = LavaKatanaMarkClientState.markedEntityIds();
        if (ids.isEmpty()) return;
        var stack = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(MATERIAL);
        double now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (int id : ids) {
            if (!(mc.level.getEntity(id) instanceof LivingEntity target) || !target.isAlive()
                    || target.distanceToSqr(camera) > 32 * 32
                    || !LavaKatanaMarkClientState.isMarked(id, mc.level.getGameTime())) continue;
            double partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Vec3 position = target.getPosition((float) partial).add(0, target.getBbHeight() * 0.64, 0);
            Vec3 offset = camera.subtract(position).normalize().scale(target.getBbWidth() * 0.52 + 0.025);
            position = position.add(offset).subtract(camera);
            int heat = Math.min(12, LavaKatanaMarkClientState.getHeat(id));
            float size = 0.18f + heat * 0.009f;
            size *= 1 + 0.08f * (float) Math.sin(now * 0.65);
            stack.pushPose();
            stack.translate(position.x, position.y, position.z);
            stack.mulPose(event.getCamera().rotation());
            Matrix4f pose = stack.last().pose();
            quad(out, pose, size * 1.3f, 48);
            quad(out, pose, size, 195 + Math.min(45, heat * 4));
            stack.popPose();
        }
        buffers.endBatch(MATERIAL);
    }

    private static void quad(VertexConsumer out, Matrix4f pose, float size, int alpha) {
        WeaponEffectShapes.mark(out, pose, WeaponEffectShapes.Mark.HEAT, size, 255, 125, 43, alpha);
    }


}
