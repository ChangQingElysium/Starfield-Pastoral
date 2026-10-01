package com.stardew.craft.client.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.tooltip.MaxChargeRangeTooltipComponent;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MaxChargeRangeClientTooltipComponent implements ClientTooltipComponent {

    private static final ResourceLocation TILE_TEXTURE = new ResourceLocation(
            StardewCraft.MODID, "textures/gui/range_overlay.png");

    private static final Component LABEL = Component.translatable("tooltip.stardewcraft.max_charge_range_label");

    private static final int TILE_SIZE = 8;
    private static final int TILE_GAP = 0;
    private static final int LABEL_SPACING = 6;

    private final int rows;
    private final int cols;

    public MaxChargeRangeClientTooltipComponent(MaxChargeRangeTooltipComponent component) {
        this.rows = Math.max(1, component.rows());
        this.cols = Math.max(1, component.cols());
    }

    private int getGridHeight() {
        return rows * TILE_SIZE + (rows - 1) * TILE_GAP;
    }

    private int getGridWidth() {
        return cols * TILE_SIZE + (cols - 1) * TILE_GAP;
    }

    private int getOuterPadding() {
        // 1 行的网格(例如 1x1/1x3/1x5)视觉上很“扁”，需要更大的上下留白
        return rows == 1 ? 4 : 2;
    }

    private int getComponentHeight() {
        return getGridHeight() + getOuterPadding() * 2;
    }

    @Override
    public int getHeight() {
        return getComponentHeight();
    }

    @Override
    public int getWidth(@SuppressWarnings("null") Font font) {
        @SuppressWarnings("null")
        int labelWidth = font.width(LABEL);
        int gridWidth = getGridWidth();
        return labelWidth + LABEL_SPACING + gridWidth;
    }

    @SuppressWarnings("null")
    @Override
    public void renderImage(@SuppressWarnings("null") Font font, int x, int y, @SuppressWarnings("null") GuiGraphics graphics) {
        @SuppressWarnings("null")
        int labelWidth = font.width(LABEL);
        int gridX = x + labelWidth + LABEL_SPACING;

        int componentHeight = getComponentHeight();
        int gridTop = y + (componentHeight - getGridHeight()) / 2;

        int labelRow = 0;
        if (rows > 1) {
            // center vertically; for even rows, pick the upper-middle row
            labelRow = (rows - 1) / 2;
        }

        int rowHeight = TILE_SIZE + TILE_GAP;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Draw label only on the centered row (so 3x3 puts it on row 2)
        int labelY = gridTop + labelRow * rowHeight + (TILE_SIZE - font.lineHeight) / 2;
        graphics.drawString(font, LABEL, x, labelY, 0xFFFFFF, false);

        // Draw grid
        // Slight tint to match stardew vibe (soft green)
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int tx = gridX + c * (TILE_SIZE + TILE_GAP);
                int ty = gridTop + r * (TILE_SIZE + TILE_GAP);

                // Depth: bottom shadow + main tile + top highlight
                graphics.setColor(0.10f, 0.25f, 0.10f, 0.35f);
                graphics.blit(TILE_TEXTURE, tx, ty + 1, 0, 0, TILE_SIZE, TILE_SIZE, TILE_SIZE, TILE_SIZE);

                graphics.setColor(0.55f, 0.95f, 0.55f, 0.80f);
                graphics.blit(TILE_TEXTURE, tx, ty, 0, 0, TILE_SIZE, TILE_SIZE, TILE_SIZE, TILE_SIZE);

                graphics.setColor(0.85f, 1.0f, 0.85f, 0.55f);
                graphics.blit(TILE_TEXTURE, tx, ty, 0, 0, TILE_SIZE, 2, TILE_SIZE, TILE_SIZE);
            }
        }

        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
