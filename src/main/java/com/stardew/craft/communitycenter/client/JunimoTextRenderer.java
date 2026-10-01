package com.stardew.craft.communitycenter.client;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * SDV SpriteText junimoText=true 的精确复刻。
 * <p>
 * SDV 源码 (SpriteText.cs):
 *   getSourceRectForChar(c, junimoText):
 *     int i = c - 32;
 *     return new Rectangle(i * 8 % texWidth, i * 8 / texWidth * 16 + (junimoText ? 224 : 0), 8, 16);
 * <p>
 *   drawString(..., junimoText: true):
 *     - fontPixelZoom = 3f
 *     - 强制 Latin 路径
 *     - Color.White
 *     - 不做大写偏移
 *     - 字符宽度 = 8 * fontPixelZoom + widthOffset
 * <p>
 * 纹理: LooseSprites/font_bold.png (128×592)，Y=224 起为 Junimo 字形行。
 */
@SuppressWarnings("null")
public final class JunimoTextRenderer {

    private JunimoTextRenderer() {}

    private static final ResourceLocation FONT_BOLD = new ResourceLocation(
            StardewCraft.MODID, "textures/gui/font_bold.png");
    private static final int TEX_WIDTH = 128;
    private static final int TEX_HEIGHT = 592;
    /** SDV junimoText Y offset */
    private static final int JUNIMO_Y_OFFSET = 224;
    /** Each glyph is 8×16 pixels in the spritesheet */
    private static final int GLYPH_W = 8;
    private static final int GLYPH_H = 16;

    /**
     * SDV SpriteText 字符宽度偏移表 (getWidthOffsetForChar)。
     * 部分字符有 -1 到 +2 的偏移。此处简化为常用 ASCII 范围。
     */
    private static int getWidthOffset(char c) {
        // SDV 完整实现中有大量字符宽度微调。
        // 关键值: 空格=-1, 多数小写字母=-1, 大写字母0
        // 这里用简化近似(视觉效果足够接近)
        return switch (c) {
            case ' ' -> -1;
            case 'l', 'i', '!', '|', '\'', '.' -> -1;
            case 'm', 'w', 'M', 'W' -> 1;
            default -> 0;
        };
    }

    /**
     * 在 GUI 上绘制 junimoText 乱码文字 (复刻 SDV SpriteText.drawString junimoText=true)。
     *
     * @param g      GuiGraphics
     * @param text   要渲染的字符串 (原始文本，每个字符映射到其对应的 Junimo glyph)
     * @param x      左上角 X (GUI pixels)
     * @param y      左上角 Y (GUI pixels)
     * @param scale  渲染缩放 (SDV fontPixelZoom=3)
     * @param alpha  透明度 (0~1)
     */
    public static void drawString(GuiGraphics g, String text, int x, int y, float scale, float alpha) {
        g.setColor(1.0f, 1.0f, 1.0f, alpha);
        float posX = x;
        float posY = y;

        for (int idx = 0; idx < text.length(); idx++) {
            char c = text.charAt(idx);
            if (c == '^') {
                // SDV newline
                posX = x;
                posY += GLYPH_H * scale;
                continue;
            }

            int i = c - 32;
            if (i < 0) i = 0;

            int srcX = (i * GLYPH_W) % TEX_WIDTH;
            int srcY = (i * GLYPH_W) / TEX_WIDTH * GLYPH_H + JUNIMO_Y_OFFSET;

            // Clamp to texture bounds
            if (srcY + GLYPH_H > TEX_HEIGHT) {
                srcY = JUNIMO_Y_OFFSET; // fallback to first Junimo glyph row
            }

            g.pose().pushPose();
            g.pose().translate(posX, posY, 0);
            g.pose().scale(scale, scale, 1.0f);
            g.blit(FONT_BOLD, 0, 0, srcX, srcY, GLYPH_W, GLYPH_H, TEX_WIDTH, TEX_HEIGHT);
            g.pose().popPose();

            // Advance position: SDV uses 8 * fontPixelZoom + widthOffset * fontPixelZoom
            float charAdvance = (GLYPH_W + getWidthOffset(c)) * scale;
            if (idx < text.length() - 1) {
                charAdvance += getWidthOffset(text.charAt(idx + 1)) * scale;
            }
            posX += charAdvance;
        }

        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawStringWrapped(GuiGraphics g, String text, int x, int y,
                                         int maxWidth, float scale, float alpha) {
        int lineHeight = Math.max(1, Math.round(GLYPH_H * scale));
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int currentY = y;

        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && getStringWidth(candidate, scale) > maxWidth) {
                drawString(g, line.toString(), x, currentY, scale, alpha);
                currentY += lineHeight;
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }

        if (!line.isEmpty()) {
            drawString(g, line.toString(), x, currentY, scale, alpha);
        }
    }

    public static void drawStringCenteredClamped(GuiGraphics g, String text, int centerX, int y,
                                                 int maxWidth, float scale, float alpha) {
        String shown = ellipsize(text, maxWidth, scale);
        drawStringCentered(g, shown, centerX, y, scale, alpha);
    }

    /**
     * 水平居中绘制 junimoText。
     */
    public static void drawStringCentered(GuiGraphics g, String text, int centerX, int y,
                                          float scale, float alpha) {
        int width = getStringWidth(text, scale);
        drawString(g, text, centerX - width / 2, y, scale, alpha);
    }

    /**
     * 计算文字渲染宽度。
     */
    public static int getStringWidth(String text, float scale) {
        float width = 0;
        for (int idx = 0; idx < text.length(); idx++) {
            char c = text.charAt(idx);
            if (c == '^') continue;
            float charW = (GLYPH_W + getWidthOffset(c)) * scale;
            if (idx < text.length() - 1) {
                charW += getWidthOffset(text.charAt(idx + 1)) * scale;
            }
            width += charW;
        }
        return (int) width;
    }

    private static String ellipsize(String text, int maxWidth, float scale) {
        if (maxWidth <= 0 || getStringWidth(text, scale) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String candidate = out + text.substring(i, i + 1) + ellipsis;
            if (getStringWidth(candidate, scale) > maxWidth) {
                break;
            }
            out.append(text.charAt(i));
        }
        return out + ellipsis;
    }
}
