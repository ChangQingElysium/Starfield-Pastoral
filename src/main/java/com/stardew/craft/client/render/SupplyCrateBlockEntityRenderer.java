package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.blockentity.SupplyCrateBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;

public final class SupplyCrateBlockEntityRenderer implements BlockEntityRenderer<SupplyCrateBlockEntity> {
    public SupplyCrateBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    public static void floatingPose(PoseStack pose, BlockPos pos, double ticks, double waterline) {
        double phase = (ticks % 80) * Math.PI / 40 + (net.minecraft.util.Mth.getSeed(pos) & 1023) * Math.PI / 512;
        pose.translate(.5, waterline + (Math.sin(phase) + .18 * Math.sin(2 * phase + .6)) / 16, .5);
        pose.mulPose(Axis.XP.rotationDegrees((float) (Math.sin(phase + 1.1) * 2.2)));
        pose.mulPose(Axis.ZP.rotationDegrees((float) (Math.sin(phase - .7) * 3.5)));
        pose.translate(-.5, -3.5 / 16, -.5);
    }

    public static void draw(BlockState state, BakedModel model, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffer.getBuffer(RenderTypeHelper.getEntityRenderType(RenderType.cutout(), false)), state, model,
                1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
    }

    @Override public void render(SupplyCrateBlockEntity entity, float partialTick, PoseStack pose,
            MultiBufferSource buffer, int light, int overlay) {
        var level = entity.getLevel();
        if (level == null) return;
        var pos = entity.getBlockPos();
        double now = level.getGameTime() + partialTick;
        double waterline = level.getFluidState(pos.below()).getHeight(level, pos.below()) - 1;
        pose.pushPose();
        floatingPose(pose, pos, now, waterline);
        double hitAge = (now - entity.lastHitTick) / 20;
        if (hitAge >= 0 && hitAge < .2) {
            double envelope = Math.pow(1 - hitAge / .2, 2);
            pose.translate(.5 + Math.sin(hitAge * Math.PI * 40) * .85 * envelope / 16, 3.5 / 16, .5);
            pose.mulPose(Axis.ZP.rotation((float) (Math.sin(hitAge * Math.PI * 30) * .045 * envelope)));
            pose.translate(-.5, -3.5 / 16, -.5);
        }
        draw(entity.getBlockState(), Minecraft.getInstance().getBlockRenderer().getBlockModel(entity.getBlockState()), pose, buffer, light, overlay);
        pose.popPose();
    }
}
