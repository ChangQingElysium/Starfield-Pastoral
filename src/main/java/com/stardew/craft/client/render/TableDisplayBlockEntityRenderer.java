package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.utility.OakTableBlock;
import com.stardew.craft.block.utility.KitchenCounterBlock;
import com.stardew.craft.block.utility.OakRoundTableBlock;
import com.stardew.craft.block.utility.SpruceCounterBlock;
import com.stardew.craft.blockentity.TableDisplayBlockEntity;
import com.stardew.craft.client.festival.FairGrangeDisplayClientCache;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.item.PowerSpecialItemService;
import com.stardew.craft.world.WitchAreaService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

public class TableDisplayBlockEntityRenderer implements BlockEntityRenderer<TableDisplayBlockEntity> {
    public TableDisplayBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SuppressWarnings("null")
    @Override
    public void render(@Nonnull TableDisplayBlockEntity be, float partialTick, @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (WitchAreaService.MAGIC_INK_TABLE_POS.equals(be.getBlockPos())
                && (ClientPlayerDataCache.hasMailFlag(PowerSpecialItemService.MAGIC_INK_FLAG)
                || ClientPlayerDataCache.hasMailFlag(WitchAreaService.PICKED_UP_MAGIC_INK_FLAG))) {
            return;
        }
        ItemStack display = be.getDisplayItem();
        float yawDegrees = be.getDisplayYawDegrees();
        var fairOverride = FairGrangeDisplayClientCache.get(be.getBlockPos());
        if (fairOverride.isPresent()) {
            display = fairOverride.get().stack();
            yawDegrees = fairOverride.get().yawDegrees();
        }
        if (display.isEmpty()) {
            return;
        }

        float y = 16.02f / 16.0f;
        BlockState state = be.getBlockState();
        if (state.getBlock() instanceof OakTableBlock tableBlock) {
            y = tableBlock.getDisplayItemY(state);
        } else if (state.getBlock() instanceof SpruceCounterBlock) {
            y = 16.02f / 16.0f;
        } else if (state.getBlock() instanceof OakRoundTableBlock) {
            y = 16.02f / 16.0f;
        } else if (state.getBlock() instanceof KitchenCounterBlock) {
            y = 16.02f / 16.0f;
        }

        poseStack.pushPose();
        poseStack.translate(0.5f, y, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yawDegrees));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        poseStack.scale(0.62f, 0.62f, 0.62f);

        Minecraft.getInstance().getItemRenderer().renderStatic(
            display,
            ItemDisplayContext.FIXED,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            buffer,
            be.getLevel(),
            0
        );

        poseStack.popPose();
    }
}
