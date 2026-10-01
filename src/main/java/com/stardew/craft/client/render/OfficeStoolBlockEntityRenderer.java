package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.OfficeChair2Block;
import com.stardew.craft.block.utility.OfficeChair2TopRenderBlock;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.utility.OfficeStoolBlock;
import com.stardew.craft.block.utility.OfficeStoolTopRenderBlock;
import com.stardew.craft.blockentity.OfficeStoolBlockEntity;
import com.stardew.craft.entity.seat.SofaSeatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class OfficeStoolBlockEntityRenderer implements BlockEntityRenderer<OfficeStoolBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public OfficeStoolBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(@Nonnull OfficeStoolBlockEntity be, float partialTick, @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) {
            return;
        }

        BlockState sourceState = be.getBlockState();
        if (sourceState.hasProperty(MapDecorStaticBlock.PART)
            && sourceState.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.MAIN) {
            return;
        }

        Integer color = null;
        BlockState renderState;
        if (sourceState.hasProperty(OfficeStoolBlock.COLOR)) {
            color = sourceState.getValue(OfficeStoolBlock.COLOR);
            renderState = ModBlocks.OFFICE_STOOL_TOP_RENDER.get().defaultBlockState()
                .setValue(OfficeStoolTopRenderBlock.COLOR, color);
        } else if (sourceState.hasProperty(OfficeChair2Block.COLOR)) {
            color = sourceState.getValue(OfficeChair2Block.COLOR);
            renderState = ModBlocks.OFFICE_CHAIR_2_TOP_RENDER.get().defaultBlockState()
                .setValue(OfficeChair2TopRenderBlock.COLOR, color);
        } else {
            return;
        }

        float targetYaw = be.getTopYawDegrees();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
            && minecraft.player.getVehicle() instanceof SofaSeatEntity seat
            && seat.getSofaPos().equals(be.getBlockPos())) {
            targetYaw = minecraft.gameRenderer.getMainCamera().getYRot() + 180.0F;
        }
        float renderYaw = be.getSmoothedClientRenderYaw(targetYaw);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-renderYaw));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        blockRenderer.renderSingleBlock(
            renderState,
            poseStack,
            buffer,
            packedLight,
            packedOverlay,
            net.minecraftforge.client.model.data.ModelData.EMPTY,
            RenderType.cutout()
        );

        poseStack.popPose();
    }
}
