package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.block.utility.HeaterBlock;
import com.stardew.craft.blockentity.HeaterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

public class HeaterBlockEntityRenderer implements BlockEntityRenderer<HeaterBlockEntity> {
    public HeaterBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SuppressWarnings({ "null", "deprecation" })
    @Override
    public void render(@Nonnull HeaterBlockEntity be, float partialTick, @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        Level level = be.getLevel();
        if (level == null || !com.stardew.craft.block.utility.UtilityMachineRenderState.rendersDynamicBody(state)) {
            return;
        }

        poseStack.pushPose();
        boolean working = state.hasProperty(HeaterBlock.WORKING) && state.getValue(HeaterBlock.WORKING);
        if (working) {
            UtilityWorkingAnimation.applyKegWorkingPoseByTicks(poseStack, be.getClientWorkingAnimationTicks(partialTick));
        }

        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        ModelBlockRenderer renderer = mc.getBlockRenderer().getModelRenderer();
        RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
        RandomSource rand = RandomSource.create(0L);
        SpatialBlockModelRenderer.render(renderer,
            level,
            model,
            state,
            be.getBlockPos(),
            poseStack,
            buffer.getBuffer(renderType),
            true,
            rand,
            0L,
            packedOverlay
        );
        poseStack.popPose();
    }
}