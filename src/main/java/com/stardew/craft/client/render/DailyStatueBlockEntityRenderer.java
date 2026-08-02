package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.blockentity.DailyStatueBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses the standard utility output bubble while leaving the statue model in
 * the normal chunk renderer.
 */
public final class DailyStatueBlockEntityRenderer
        extends UtilityMachineBlockEntityRenderer<DailyStatueBlockEntity> {

    public DailyStatueBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderBlockModel(
            DailyStatueBlockEntity blockEntity,
            BlockState state,
            Level level,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedOverlay
    ) {
        // Daily statues use a normal block model; this renderer only adds the
        // same ready-product bubble used by other utility facilities.
    }
}
