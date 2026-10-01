package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.utility.WoodSignBlock;
import com.stardew.craft.blockentity.WoodSignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

@SuppressWarnings("null")
public final class WoodSignBlockEntityRenderer implements BlockEntityRenderer<WoodSignBlockEntity>, com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension<WoodSignBlockEntity> {
    public WoodSignBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WoodSignBlockEntity sign, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        ItemStack item = sign.getDisplayItem();
        if (item.isEmpty()) return;
        var state = sign.getBlockState();
        if (!(state.getBlock() instanceof WoodSignBlock block)) return;
        var facing = state.getValue(WoodSignBlock.FACING);
        // Light comes from in front of the board, not from inside its support wall.
        if (sign.getLevel() != null) light = LevelRenderer.getLightColor(sign.getLevel(), sign.getBlockPos().relative(facing));
        poses.pushPose();
        poses.translate(0.5, 0, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        poses.translate(0, (block.isWall() ? 8 : 14) / 16.0, (block.isWall() ? 13 : 6) / 16.0 - 0.5 - 0.01);
        poses.mulPose(Axis.YP.rotationDegrees(180));
        // Use each item's actual GUI model (including addon models), flattened onto the board.
        poses.scale(0.625F, 0.625F, 0.005F);
        Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.GUI,
                light, OverlayTexture.NO_OVERLAY, poses, buffers, sign.getLevel(), 0);
        poses.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(WoodSignBlockEntity sign) {
        return new AABB(sign.getBlockPos()).expandTowards(0, 0.5, 0);
    }
}
