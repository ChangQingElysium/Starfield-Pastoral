package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.ShadowFootprintBlock;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.cutscene.network.ClientEventSeenCache;
import com.stardew.craft.secretnote.SecretNote31FootprintTrail;
import com.stardew.craft.secretnote.SecretNoteService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Renders event 520702's trail only for the eligible local player. */
public final class SecretNote31FootprintRenderer {
    private static final double RENDER_RANGE_SQ = 96.0D * 96.0D;

    private SecretNote31FootprintRenderer() {}

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !ModDimensions.STARDEW_VALLEY.equals(minecraft.level.dimension())
                || !ClientEventSeenCache.isSynced()
                || !ClientPlayerDataCache.isSynced()
                || !ClientEventSeenCache.hasSeen(SecretNote31FootprintTrail.BUS_STOP_EVENT_ID)
                || hasMagnifyingGlass()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean rendered = false;

        for (SecretNote31FootprintTrail.Footprint footprint : SecretNote31FootprintTrail.FOOTPRINTS) {
            Vec3 center = Vec3.atCenterOf(footprint.pos());
            if (center.distanceToSqr(camera) > RENDER_RANGE_SQ) continue;

            BlockState state = ModBlocks.SHADOW_FOOTPRINT.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, footprint.direction())
                    .setValue(ShadowFootprintBlock.FOOT, footprint.foot());

            poseStack.pushPose();
            poseStack.translate(
                    footprint.pos().getX() - camera.x,
                    footprint.pos().getY() - camera.y + 0.002D,
                    footprint.pos().getZ() - camera.z);
            minecraft.getBlockRenderer().renderSingleBlock(
                    state,
                    poseStack,
                    buffers,
                    LevelRenderer.getLightColor(minecraft.level, footprint.pos()),
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            rendered = true;
        }

        if (rendered) {
            buffers.endBatch(RenderType.translucent());
        }
    }

    private static boolean hasMagnifyingGlass() {
        return ClientPlayerDataCache.hasMailFlag(SecretNoteService.MAGNIFYING_GLASS_FLAG)
                || ClientPlayerDataCache.hasSpecialItem(SecretNoteService.MAGNIFYING_GLASS_SPECIAL_ITEM);
    }
}
