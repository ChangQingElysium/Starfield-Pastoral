package com.stardew.craft.client.gui;

import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.network.payload.ApplySofaColorPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
public class SofaColorSelectionScreen extends Screen {
    private static final int SWATCH_WIDTH = 12;
    private static final int SWATCH_HEIGHT = 20;
    private static final int GRID_COLS = 21;
    private static final int PANEL_PADDING = 8;

    private final BlockPos targetPos;
    private final int targetEntityId;
    private int selectedColor;

    public SofaColorSelectionScreen(BlockPos targetPos, int currentColor) {
        this(targetPos, currentColor, -1);
    }

    public SofaColorSelectionScreen(BlockPos targetPos, int currentColor, int targetEntityId) {
        super(Component.translatable("stardewcraft.furniture.color_picker"));
        this.targetPos = targetPos;
        this.targetEntityId = targetEntityId;
        int clamped = WoodenChestColorPalette.clampIndex(currentColor);
        this.selectedColor = clamped < 0 ? WoodenChestColorPalette.defaultColorIndex() : clamped;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int panelW = GRID_COLS * SWATCH_WIDTH + PANEL_PADDING * 2;
        int panelH = SWATCH_HEIGHT + PANEL_PADDING * 2;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xD0101010);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, 0x40000000);

        int swatchX0 = panelX + PANEL_PADDING;
        int swatchY0 = panelY + PANEL_PADDING;

        for (int i = 0; i < WoodenChestColorPalette.size(); i++) {
            int x = swatchX0 + i * SWATCH_WIDTH;
            int y = swatchY0;
            int rgb = WoodenChestColorPalette.rgbAt(i) | 0xFF000000;
            graphics.fill(x, y, x + SWATCH_WIDTH, y + SWATCH_HEIGHT, rgb);

            if (i == selectedColor) {
                graphics.fill(x, y, x + SWATCH_WIDTH, y + 2, 0xFFFFFFFF);
                graphics.fill(x, y + SWATCH_HEIGHT - 2, x + SWATCH_WIDTH, y + SWATCH_HEIGHT, 0xFFFFFFFF);
            }

            if (mouseX >= x && mouseX < x + SWATCH_WIDTH && mouseY >= y && mouseY < y + SWATCH_HEIGHT) {
                graphics.fill(x, y, x + SWATCH_WIDTH, y + SWATCH_HEIGHT, 0x40FFFFFF);
                graphics.renderTooltip(this.font, Component.translatable("stardewcraft.sofa.color_tooltip", i + 1), mouseX, mouseY);
            }
        }

        GuiText.drawCenteredClamped(graphics, this.font, this.title, this.width / 2,
            panelY - 14, Math.max(1, this.width - 32), 0xFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            this.onClose();
            return true;
        }

        int panelW = GRID_COLS * SWATCH_WIDTH + PANEL_PADDING * 2;
        int panelH = SWATCH_HEIGHT + PANEL_PADDING * 2;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        int swatchX0 = panelX + PANEL_PADDING;
        int swatchY0 = panelY + PANEL_PADDING;

        for (int i = 0; i < WoodenChestColorPalette.size(); i++) {
            int x = swatchX0 + i * SWATCH_WIDTH;
            int y = swatchY0;
            if (mouseX >= x && mouseX < x + SWATCH_WIDTH && mouseY >= y && mouseY < y + SWATCH_HEIGHT) {
                this.selectedColor = i;
                PacketDistributor.sendToServer(new ApplySofaColorPayload(targetPos, i, targetEntityId));
                this.onClose();
                return true;
            }
        }

        this.onClose();
        return true;
    }
}
