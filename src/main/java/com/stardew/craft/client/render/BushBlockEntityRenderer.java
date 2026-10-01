package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.block.nature.BerryBushBlock;
import com.stardew.craft.blockentity.BushBlockEntity;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class BushBlockEntityRenderer implements BlockEntityRenderer<BushBlockEntity>, com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension<BushBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public BushBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(@Nonnull BushBlockEntity be, float partialTick, @Nonnull PoseStack poseStack,
                       @Nonnull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (state.hasProperty(BerryBushBlock.PART) && state.getValue(BerryBushBlock.PART) != BerryBushBlock.Part.MAIN) {
            return;
        }

        BlockState renderState = state;
        if (state.hasProperty(BerryBushBlock.BERRY)) {
            renderState = state.setValue(BerryBushBlock.BERRY, currentBerry(be));
        }

        renderBlockModel(renderState, poseStack, bufferSource, packedLight, packedOverlay);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(@Nonnull BushBlockEntity blockEntity) {
        // The bush spans several cells. Supply its real bounds instead of bypassing
        // frustum culling and redrawing every bush within 128 blocks behind the camera.
        return blockEntity.getRenderBoundingBox();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    private static BerryBushBlock.BerryKind currentBerry(BushBlockEntity be) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof BerryBushBlock)) {
            return BerryBushBlock.BerryKind.NONE;
        }
        StardewTimeManager time = StardewTimeHud.getClientTimeCache();
        if (be.getLastHarvestAbsoluteDay() == time.getAbsoluteDay()) {
            return BerryBushBlock.BerryKind.NONE;
        }
        BerryBushBlock.BerryKind berry = BerryBushBlock.getBloomBerry(time.getCurrentSeason(), time.getCurrentDay());
        if (berry == BerryBushBlock.BerryKind.NONE) {
            return BerryBushBlock.BerryKind.NONE;
        }
        return BerryBushBlock.hasBerriesToday(be.getBlockPos(), berry, time.getAbsoluteDay())
            ? berry
            : BerryBushBlock.BerryKind.NONE;
    }

    private void renderBlockModel(BlockState renderState,
                                  PoseStack poseStack,
                                  MultiBufferSource bufferSource,
                                  int packedLight,
                                  int packedOverlay) {
        ModelData modelData = ModelData.EMPTY;
        BakedModel model = blockRenderer.getBlockModel(renderState);

        for (RenderType renderType : model.getRenderTypes(renderState, RandomSource.create(42L), modelData)) {
            blockRenderer.getModelRenderer().renderModel(
                poseStack.last(),
                bufferSource.getBuffer(RenderTypeHelper.getEntityRenderType(renderType, false)),
                renderState,
                model,
                1.0F,
                1.0F,
                1.0F,
                packedLight,
                packedOverlay,
                modelData,
                renderType
            );
        }
    }
}
