package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.WizardBuildingBlockEntity;
import com.stardew.craft.client.model.block.WizardBuildingGeoModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public final class WizardBuildingBlockEntityRenderer extends StardewGeoBlockRenderer<WizardBuildingBlockEntity> {
    public WizardBuildingBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new WizardBuildingGeoModel());
    }

    @Override
    public void render(WizardBuildingBlockEntity animatable, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = animatable.getBlockState().hasProperty(MapDecorStaticBlock.FACING)
                ? animatable.getBlockState().getValue(MapDecorStaticBlock.FACING) : Direction.NORTH;
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180.0F));
        poseStack.translate(-0.5, 0.0, -0.5);
        if (animatable.kind().isGoldClock()) {
            if (animatable.isGoldClockEnabled()) {
                float[] angles = currentClockHandAngles();
                BlockbenchElementRenderer.renderGoldClock(
                        animatable.kind().goldClockElementModel(true),
                        poseStack, bufferSource, packedLight, packedOverlay, angles[0], angles[1]);
            } else {
                BlockbenchElementRenderer.renderAllWithReversedU(
                        animatable.kind().goldClockElementModel(false),
                        poseStack, bufferSource, packedLight, packedOverlay);
            }
        } else {
            super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    @Override
    protected void rotateBlock(@Nonnull Direction facing, @Nonnull PoseStack poseStack) {
    }

    /** [hour, minute], with positive Z matching Blockbench's clockwise direction. */
    private static float[] currentClockHandAngles() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        double totalMinutes;
        if (minecraft.level != null) {
            long dayTime = Math.floorMod(minecraft.level.getDayTime(), 24_000L);
            // Stardew's shared clock maps MC dayTime 0 to 06:00 and 1000 ticks to one hour.
            totalMinutes = (dayTime / 1000.0D + 6.0D) * 60.0D;
        } else {
            totalMinutes = com.stardew.craft.client.hud.StardewTimeHud
                    .getClientTimeCache().getCurrentTime();
        }
        return clockHandAnglesForMinutes(totalMinutes);
    }

    static float[] clockHandAnglesForMinutes(double totalMinutes) {
        double normalized = ((totalMinutes % 720.0D) + 720.0D) % 720.0D;
        float minuteDegrees = (float) (normalized % 60.0D * 6.0D);
        float hourDegrees = (float) (normalized * 0.5D);
        return new float[] {hourDegrees, minuteDegrees};
    }
}
