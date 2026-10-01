package com.stardew.craft.client.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.tooltip.WaterAmountTooltipComponent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class WaterAmountClientTooltipComponent implements ClientTooltipComponent {

    private static final ResourceLocation BAR_TEXTURE = new ResourceLocation(
            StardewCraft.MODID, "textures/gui/stardew_bars.png");
    private static final ResourceLocation BAR_CONTENT_TEXTURE = new ResourceLocation(
            StardewCraft.MODID, "textures/gui/bar_content.png");

    private static final int BAR_WIDTH = 108;
    private static final int BAR_HEIGHT = 12;

    private static final int FILL_OFFSET_X = 3;
    private static final int FILL_OFFSET_Y = 3;
    private static final int FILL_WIDTH = 102;
    private static final int FILL_HEIGHT = 6;

    private static final int LABEL_SPACING = 4;
    private static final Component LABEL = Component.translatable("tooltip.stardewcraft.water_capacity_label");

    private final int water;
    private final int max;
    private final boolean bottomless;

    public WaterAmountClientTooltipComponent(WaterAmountTooltipComponent component) {
        this.bottomless = component.bottomless();
        this.water = bottomless ? Math.max(1, component.max()) : Math.max(0, component.water());
        this.max = Math.max(1, component.max());
    }

    @Override
    public int getHeight() {
        return BAR_HEIGHT;
    }

    @SuppressWarnings("null")
    @Override
    public int getWidth(@SuppressWarnings("null") Font font) {
        return font.width(LABEL) + LABEL_SPACING + BAR_WIDTH;
    }

    @SuppressWarnings("null")
    @Override
    public void renderImage(@SuppressWarnings("null") Font font, int x, int y, @SuppressWarnings("null") GuiGraphics graphics) {
        @SuppressWarnings("null")
        int labelWidth = font.width(LABEL);
        int barX = x + labelWidth + LABEL_SPACING;

        float fillRatio = bottomless ? 1.0f : Math.min(1.0f, Math.max(0.0f, (float) water / (float) max));
        int fillWidth = (int) (FILL_WIDTH * fillRatio);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 左侧 label（白色）
        int textBaselineY = y + BAR_HEIGHT / 2 - 4;
        graphics.drawString(font, LABEL, x, textBaselineY, 0xFFFFFF, false);

        // 背景槽
        graphics.blit(BAR_TEXTURE, barX, y, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);

        // 蓝色进度条（左右渐变 + 多层次：底色 + 高光 + 底部阴影）
        if (fillWidth > 0) {
            int fx = barX + FILL_OFFSET_X;
            int fy = y + FILL_OFFSET_Y;

            // 只保留“左->右”柔和渐变；不做上下高光/阴影（避免出现上下渐变）
            if (bottomless) {
                blitHorizontalGradient(
                    graphics,
                    fx,
                    fy,
                    fillWidth,
                    FILL_HEIGHT,
                    0x98 / 255f,
                    0x38 / 255f,
                    0xFF / 255f,
                    0xD8 / 255f,
                    0x82 / 255f,
                    0xFF / 255f,
                    1.0f);
            } else {
                blitHorizontalGradient(
                    graphics,
                    fx,
                    fy,
                    fillWidth,
                    FILL_HEIGHT,
                    0x1E / 255f,
                    0x86 / 255f,
                    0xFF / 255f,
                    0x4F / 255f,
                    0xB6 / 255f,
                    0xFF / 255f,
                    1.0f);
            }
        }

        // 中间数字
        String text = bottomless ? "∞/∞" : water + "/" + max;
        int textWidth = Minecraft.getInstance().font.width(text);
        int textX = barX + BAR_WIDTH / 2 - textWidth / 2;
        int textY = y + BAR_HEIGHT / 2 - 4;

        graphics.drawString(font, text, textX + 1, textY, 0x000000, false);
        graphics.drawString(font, text, textX - 1, textY, 0x000000, false);
        graphics.drawString(font, text, textX, textY + 1, 0x000000, false);
        graphics.drawString(font, text, textX, textY - 1, 0x000000, false);
        graphics.drawString(font, text, textX, textY, 0xFFFFFF, false);

        RenderSystem.disableBlend();
    }

    @SuppressWarnings("null")
    private static void blitHorizontalGradient(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            float rLeft,
            float gLeft,
            float bLeft,
            float rRight,
            float gRight,
            float bRight,
            float alpha
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int slices = Math.min(width, 24);
        for (int i = 0; i < slices; i++) {
            int sx0 = x + (width * i) / slices;
            int sx1 = x + (width * (i + 1)) / slices;
            int sw = Math.max(1, sx1 - sx0);
            float t = slices <= 1 ? 1.0f : (float) i / (float) (slices - 1);

            float r = rLeft + (rRight - rLeft) * t;
            float g = gLeft + (gRight - gLeft) * t;
            float b = bLeft + (bRight - bLeft) * t;

            graphics.setColor(r, g, b, alpha);
            graphics.blit(
                    BAR_CONTENT_TEXTURE,
                    sx0,
                    y,
                    0,
                    0,
                    Math.min(sw, x + width - sx0),
                    height,
                    FILL_WIDTH,
                    FILL_HEIGHT);
        }

        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
