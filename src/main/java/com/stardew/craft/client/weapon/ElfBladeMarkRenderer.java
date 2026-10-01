package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** A small growing sprig carries the existing stack count without a spinning icon wheel. */
public final class ElfBladeMarkRenderer {
    private static final RenderType MATERIAL = WeaponEffectRenderTypes.MOLTEN_GLOW;
    private ElfBladeMarkRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        var ids = ElfBladeMarkClientState.markedEntityIds();
        if (ids.isEmpty()) return;
        float partial = event.getPartialTick();
        long tick = mc.level.getGameTime();
        Vec3 camera = event.getCamera().getPosition();
        var stack = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(MATERIAL);
        for (int id : ids) {
            if (!(mc.level.getEntity(id) instanceof LivingEntity target) || !target.isAlive()
                    || target.distanceToSqr(camera) > 32 * 32) continue;
            var info = ElfBladeMarkClientState.getMarkInfo(id, tick);
            if (info == null || info.stacks <= 0) continue;
            var bounds = target.getBoundingBox().move(target.getPosition(partial).subtract(target.position()));
            Vec3 center = bounds.getCenter();
            Vec3 point = bounds.clip(camera, center).orElse(center)
                    .add(camera.subtract(center).normalize().scale(0.04)).subtract(camera);
            float fade = Math.min(1, (info.endTick - tick) / 20f);
            stack.pushPose(); stack.translate(point.x, point.y, point.z);
            stack.mulPose(event.getCamera().rotation());
            GroveEffectGeometry.sprig(out, stack.last().pose(), info.stacks, fade);
            stack.popPose();
        }
        buffers.endBatch(MATERIAL);
    }

}
