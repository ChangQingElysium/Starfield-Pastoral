package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.FridgeBlock;
import com.stardew.craft.blockentity.FridgeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.data.ModelData;

/** Only the lower door moves; the cabinet is a normal baked block model. */
public final class FridgeBlockEntityRenderer implements BlockEntityRenderer<FridgeBlockEntity> {
    private static final ModelResourceLocation DOOR = new ModelResourceLocation(
            new ResourceLocation(StardewCraft.MODID, "block/utility/fridge_door"), "standalone");

    public FridgeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(FridgeBlockEntity fridge) {
        return new AABB(fridge.getBlockPos()).expandTowards(0, 1, 0).inflate(1, 0, 1);
    }

    @Override
    public void render(FridgeBlockEntity fridge, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        if (fridge.getLevel() == null) return;
        pose.pushPose();
        pose.translate(.5, 0, .5);
        int yaw = switch (fridge.getBlockState().getValue(FridgeBlock.FACING)) {
            case EAST -> -90;
            case SOUTH -> -180;
            case WEST -> -270;
            default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-.5, 0, -.5);
        // Authoring model is already open 90 degrees. Rotate back around its actual hinge to close it.
        pose.translate(15.0 / 16, 0, 2.0 / 16);
        pose.mulPose(Axis.YP.rotationDegrees(90 * (1 - fridge.getDoorOpenness(partialTick))));
        pose.translate(-15.0 / 16, 0, -2.0 / 16);
        var minecraft = Minecraft.getInstance();
        minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffer.getBuffer(Sheets.cutoutBlockSheet()), fridge.getBlockState(),
                minecraft.getModelManager().getModel(DOOR), 1, 1, 1, light, overlay,
                ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }
}
