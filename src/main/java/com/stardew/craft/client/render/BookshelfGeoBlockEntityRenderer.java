package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.BookshelfGeoBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class BookshelfGeoBlockEntityRenderer implements BlockEntityRenderer<BookshelfGeoBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public BookshelfGeoBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(@Nonnull BookshelfGeoBlockEntity blockEntity, float partialTick,
                       @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        // Old worlds may still contain the former GeckoLib block entity for the
        // regular bookshelf. Its model now renders statically, so ignore that BE.
        if (!state.is(ModBlocks.BOOKSHELF_TALL_2.get())) {
            return;
        }
        if (state.hasProperty(MapDecorStaticBlock.PART)
            && state.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.MAIN) {
            return;
        }

        poseStack.pushPose();
        // The source Java model is authored from Y=-16..32. Moving the rendered
        // model up one block places it at Y=0..48 without illegal model coordinates.
        poseStack.translate(0.0D, 1.0D, 0.0D);
        renderBakedModel(blockEntity, state, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull BookshelfGeoBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    private void renderBakedModel(BookshelfGeoBlockEntity blockEntity, BlockState state,
                                  PoseStack poseStack, MultiBufferSource bufferSource,
                                  int packedLight, int packedOverlay) {
        ModelData modelData = ModelData.EMPTY;
        BakedModel model = blockRenderer.getBlockModel(state);
        int color = Minecraft.getInstance().getBlockColors().getColor(
            state, blockEntity.getLevel(), blockEntity.getBlockPos(), 0
        );
        float red = (float) (color >> 16 & 0xFF) / 255.0F;
        float green = (float) (color >> 8 & 0xFF) / 255.0F;
        float blue = (float) (color & 0xFF) / 255.0F;

        for (RenderType renderType : model.getRenderTypes(state, RandomSource.create(42L), modelData)) {
            blockRenderer.getModelRenderer().renderModel(
                poseStack.last(),
                bufferSource.getBuffer(RenderTypeHelper.getEntityRenderType(renderType, false)),
                state,
                model,
                red,
                green,
                blue,
                packedLight,
                packedOverlay,
                modelData,
                renderType
            );
        }
    }
}
