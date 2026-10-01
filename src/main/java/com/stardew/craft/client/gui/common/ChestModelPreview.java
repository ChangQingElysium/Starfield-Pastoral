package com.stardew.craft.client.gui.common;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Isolated preview entities never have a level, inventory, position or network side effect. */
@OnlyIn(Dist.CLIENT)
public final class ChestModelPreview {
    private final java.util.Map<com.stardew.craft.block.utility.ChestVariant, net.minecraft.world.level.block.entity.BlockEntity> previews = new java.util.EnumMap<>(com.stardew.craft.block.utility.ChestVariant.class);

    public void draw(GuiGraphics graphics, int x, int y, int color, boolean wooden, boolean stoneChest) {
        draw(graphics, x, y, color, wooden ? com.stardew.craft.block.utility.ChestVariant.WOOD
                : stoneChest ? com.stardew.craft.block.utility.ChestVariant.STONE : null);
    }

    public void draw(GuiGraphics graphics, int x, int y, int color, com.stardew.craft.block.utility.ChestVariant variant) {
        if (variant == null) {
            // A reused chest menu may describe an addon's model. Do not pretend it is our wood chest.
            graphics.blit(new ResourceLocation("stardewcraft", "textures/gui/color_wheel.png"),
                    x - 12, y - 12, 0, 0, 24, 24, 24, 24);
            int rgb = com.stardew.craft.block.utility.WoodenChestColorPalette.rgbAt(color);
            graphics.fill(x - 13, y + 14, x + 13, y + 18, 0xFF000000 | rgb);
            return;
        }
        var entity = previews.computeIfAbsent(variant, key ->
                ((net.minecraft.world.level.block.EntityBlock) key.block()).newBlockEntity(BlockPos.ZERO, key.block().defaultBlockState()));
        ((com.stardew.craft.inventory.ChestStorage) entity).setColorSelection(color);
        graphics.flush();
        var pose = graphics.pose();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        pose.pushPose();
        try {
            pose.translate(x, y, 70);
            float scale = variant.capacity == 70 ? 26 : 29;
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(23));
            // GUI depth faces +Z; the authored latch is on -Z. Turn the front toward the viewer.
            pose.mulPose(Axis.YP.rotationDegrees(145));
            pose.translate(-.5, -.5, -.5);
            Lighting.setupFor3DItems();
            Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(entity,
                    pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            buffers.endBatch();
        } finally {
            pose.popPose();
            Lighting.setupFor3DItems();
        }
    }
}
