package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.mine.MineChestBlock;
import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.blockentity.MineChestBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;

/** The native lid model rotates as one assembly: no swapped open-state geometry or UVs. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MineChestBlockEntityRenderer implements BlockEntityRenderer<MineChestBlockEntity>, com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension<MineChestBlockEntity> {
    private static final ModelResourceLocation BODY = model("body");
    private static final ModelResourceLocation LID = model("lid");

    private static final ModelResourceLocation SPECIAL_BODY = specialModel("body");
    private static final ModelResourceLocation SPECIAL_LID = specialModel("lid");

    private static ModelResourceLocation specialModel(String part) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/mine/desert_special_chest/" + part), "standalone");
    }

    public MineChestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    private static ModelResourceLocation model(String part) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/mine/reward_chest/" + part), "standalone");
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(BODY);
        event.register(LID);
        event.register(SPECIAL_BODY);
        event.register(SPECIAL_LID);
    }

    @Override
    public void render(MineChestBlockEntity chest, float partialTick, PoseStack pose, MultiBufferSource buffer,
            int light, int overlay) {
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - chest.getBlockState().getValue(MineChestBlock.FACING).toYRot()));
        pose.translate(-.5, 0, -.5);
        renderPart(chest, chest.getBlockState().getValue(MineChestBlock.SPECIAL) ? SPECIAL_BODY : BODY, pose, buffer, light, overlay);
        pose.translate(.5, .5, .875);
        pose.mulPose(Axis.XP.rotationDegrees(chest.getLidAngle(partialTick)));
        pose.translate(-.5, -.5, -.875);
        renderPart(chest, chest.getBlockState().getValue(MineChestBlock.SPECIAL) ? SPECIAL_LID : LID, pose, buffer, light, overlay);
        pose.popPose();
    }

    private static void renderPart(MineChestBlockEntity chest, ModelResourceLocation id, PoseStack pose,
            MultiBufferSource buffer, int light, int overlay) {
        var minecraft = Minecraft.getInstance();
        int tint = WoodenChestColorPalette.rgbAt(chest.getColorSelection());
        minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffer.getBuffer(Sheets.cutoutBlockSheet()), chest.getBlockState(),
                minecraft.getModelManager().getModel(id),
                ((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f,
                light, overlay, ModelData.EMPTY, RenderType.cutout());
    }

    @Override
    public AABB getRenderBoundingBox(MineChestBlockEntity chest) {
        return new AABB(chest.getBlockPos()).inflate(.5).expandTowards(0, .5, 0);
    }
}
