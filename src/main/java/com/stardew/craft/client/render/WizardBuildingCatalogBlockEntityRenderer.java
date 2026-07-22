package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.blockentity.WizardBuildingCatalogBlockEntity;
import com.stardew.craft.client.model.block.WizardBuildingCatalogGeoModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public final class WizardBuildingCatalogBlockEntityRenderer
        extends StardewGeoBlockRenderer<WizardBuildingCatalogBlockEntity> {
    public WizardBuildingCatalogBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new WizardBuildingCatalogGeoModel());
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        // The supplied catalog geometry faces away from the confirmed Wizard Tower layout.
        // GeckoLib calls this after translating to the block center, so this is a centered Y rotation.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
    }
}
