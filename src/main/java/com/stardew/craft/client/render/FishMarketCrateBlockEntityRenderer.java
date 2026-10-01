package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.FishMarketCrateBlockEntity;
import com.stardew.craft.client.fishpond.ClientFishPondFishRenderer;
import com.stardew.craft.fishing.FishMarketCrateLayout;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public final class FishMarketCrateBlockEntityRenderer implements BlockEntityRenderer<FishMarketCrateBlockEntity>, com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension<FishMarketCrateBlockEntity> {
    public FishMarketCrateBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(FishMarketCrateBlockEntity crate, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var state = crate.getBlockState();
        if (state.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.MAIN) return;
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-90 * FishMarketCrateLayout.quarterTurns(state.getValue(MapDecorStaticBlock.FACING))));
        pose.translate(-.5, 0, -.5);
        for (int slot = 0; slot < FishMarketCrateLayout.SLOTS; slot++) {
            pose.pushPose();
            pose.translate(FishMarketCrateLayout.centerX(slot), 0, .5);
            ClientFishPondFishRenderer.renderMarketFish(crate.fish(slot), pose, buffers, light, slot);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override public AABB getRenderBoundingBox(FishMarketCrateBlockEntity crate) {
        var pos = crate.getBlockPos();
        var other = pos.relative(crate.getBlockState().getValue(MapDecorStaticBlock.FACING).getClockWise());
        return new AABB(pos).minmax(new AABB(other));
    }
}
