package com.stardew.craft.client.gui.overnight;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("null")
public class StardewGuiUtil {

    public static final ResourceLocation CURSORS = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/cursors.png");
    public static final int CURSORS_WIDTH = 704;
    public static final int CURSORS_HEIGHT = 2256;
    public static final ResourceLocation CURSORS2 = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/mouse_cursors2.png");
    public static final int CURSORS2_WIDTH = 256;
    public static final int CURSORS2_HEIGHT = 320;
    public static final ResourceLocation CURSORS_1_6 = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/cursors_1_6.png");
    public static final int CURSORS_1_6_WIDTH = 512;
    public static final int CURSORS_1_6_HEIGHT = 512;
    public static final ResourceLocation MENU_TILES = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/menu_tiles.png");
    public static final int MENU_TILES_WIDTH = 256;
    public static final int MENU_TILES_HEIGHT = 1152;
    private static final ResourceLocation COMMON_TEXTURE_BOX_18 = common("texture_box_18");
    private static final ResourceLocation COMMON_ENTRY_BOX_15 = common("entry_box_15");
    private static final ResourceLocation COMMON_SCROLL_TRACK_BOX = common("scroll_track_box");
    private static final ResourceLocation COMMON_SCROLL_BANNER_BOX_11 = common("scroll_banner_box_11");
    private static final ResourceLocation COMMON_OPTION_HIGHLIGHT_BOX_3 = common("option_highlight_box_3");
    private static final ResourceLocation COMMON_CALENDAR_TODAY_BOX_3 = common("calendar_today_box_3");
    private static final ResourceLocation COMMON_BILLBOARD_ACCEPT_BOX_9 = common("billboard_accept_box_9");
    private static final ResourceLocation MENU_TEXTURE_BOX_60 = animalQuery("menu_texture_box_60");

    public static void drawFromCursors(GuiGraphics graphics, int x, int y, int u, int v, int width, int height, float scale) {
        drawFromCursors(graphics, x, y, u, v, width, height, scale, 1.0f);
    }
    
    public static void drawFromCursors(GuiGraphics graphics, int x, int y, int u, int v, int width, int height, float scale, float alpha) {
        drawFromCursorsTint(graphics, x, y, u, v, width, height, scale, 1.0F, 1.0F, 1.0F, alpha);
    }

    public static void drawFromCursorsTint(GuiGraphics graphics, int x, int y, int u, int v, int width, int height, float scale, float red, float green, float blue, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(red, green, blue, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(CURSORS, 0, 0, u, v, width, height, CURSORS_WIDTH, CURSORS_HEIGHT);
        graphics.pose().popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawFromCursorsTintFlipped(
            GuiGraphics graphics,
            int x,
            int y,
            int u,
            int v,
            int width,
            int height,
            float scale,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(red, green, blue, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x + width * scale, y, 0);
        graphics.pose().scale(-scale, scale, 1.0f);
        graphics.blit(CURSORS, 0, 0, u, v, width, height, CURSORS_WIDTH, CURSORS_HEIGHT);
        graphics.pose().popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawFromCursors2(GuiGraphics graphics, int x, int y, int u, int v, int width, int height, float scale) {
        drawFromCursors2(graphics, x, y, u, v, width, height, scale, 1.0f);
    }

    public static void drawFromCursors2(GuiGraphics graphics, int x, int y, int u, int v, int width, int height, float scale, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(CURSORS2, 0, 0, u, v, width, height, CURSORS2_WIDTH, CURSORS2_HEIGHT);
        graphics.pose().popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawFromCursors16(GuiGraphics graphics, int x, int y, int u, int v, int width, int height, float scale) {
        drawFromCursors16(graphics, x, y, u, v, width, height, scale, 1.0f);
    }

    public static void drawFromCursors16(GuiGraphics graphics, int x, int y, int u, int v, int width, int height, float scale, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(CURSORS_1_6, 0, 0, u, v, width, height, CURSORS_1_6_WIDTH, CURSORS_1_6_HEIGHT);
        graphics.pose().popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawTextureBox(GuiGraphics graphics, int x, int y, int width, int height) {
        drawTextureBox(graphics, MENU_TILES, MENU_TILES_WIDTH, MENU_TILES_HEIGHT, 0, 256, 60, 60, x, y, width, height, 1.0f / getGuiScale(), true);
    }

    // Equivalent to IClickableMenu.drawHorizontalPartition(..., small=false)
    public static void drawHorizontalPartition(GuiGraphics graphics, int x, int y, int width, float scale) {
        int unit = Math.max(1, Math.round(16.0f * scale));
        drawHorizontalPartition(graphics, x, y, width, unit);
    }

    public static void drawHorizontalPartition(GuiGraphics graphics, int x, int y, int width, int unit) {
        int middleWidth = Math.max(0, width - unit * 2);
        drawMenuTile(graphics, x, y, unit, unit, 4);
        drawMenuTile(graphics, x + unit, y, middleWidth, unit, 6);
        drawMenuTile(graphics, x + width - unit, y, unit, unit, 7);
    }

    // Equivalent to IClickableMenu.drawHorizontalPartition(..., small=true)
    public static void drawHorizontalPartitionSmall(GuiGraphics graphics, int x, int y, int width, float scale) {
        int unit = Math.max(1, Math.round(16.0f * scale));
        int startX = x + unit / 2;
        int middleWidth = Math.max(0, width - unit);
        drawMenuTile(graphics, startX, y, middleWidth, unit, 25);
    }

    // Equivalent to IClickableMenu.drawVerticalPartition(..., small=true)
    public static void drawVerticalPartitionSmall(GuiGraphics graphics, int x, int menuY, int menuHeight, float scale) {
        drawVerticalPartitionSmall(graphics, x, menuY, menuHeight, scale, -1);
    }

    /** Equivalent to Stardew's small partition with AnimalPage's optional heightOverride. */
    public static void drawVerticalPartitionSmall(GuiGraphics graphics, int x, int menuY,
                                                  int menuHeight, float scale, int heightOverride) {
        int unit = Math.max(1, Math.round(16.0f * scale));
        int y = menuY + unit + unit / 2;
        int height = Math.max(0, heightOverride >= 0 ? heightOverride : menuHeight - unit * 2);
        drawMenuTile(graphics, x, y, unit, height, 26);
    }

    /** Draw tile 26 over an explicit interval when the surrounding frame owns the endpoints. */
    public static void drawVerticalPartitionSmallSpan(GuiGraphics graphics, int x,
                                                       int startY, int endY, float scale) {
        int unit = Math.max(1, Math.round(16.0f * scale));
        drawMenuTile(graphics, x, startY, unit, Math.max(0, endY - startY), 26);
    }

    // Equivalent to IClickableMenu.drawVerticalIntersectingPartition
    public static void drawVerticalIntersectingPartition(GuiGraphics graphics, int x, int y, int menuY, int menuHeight, float scale) {
        int unit = Math.max(1, Math.round(16.0f * scale));
        drawVerticalIntersectingPartition(graphics, x, y, menuY, menuHeight, unit);
    }

    public static void drawVerticalIntersectingPartition(GuiGraphics graphics, int x, int y, int menuY, int menuHeight, int unit) {
        int middleHeight = Math.max(0, menuY + menuHeight - unit - (y + unit));
        drawMenuTile(graphics, x, y, unit, unit, 59);
        drawMenuTile(graphics, x, y + unit, unit, middleHeight, 63);
        drawMenuTile(graphics, x, menuY + menuHeight - unit, unit, unit, 62);
    }

    public static void drawTextureBoxNoShadow(GuiGraphics graphics, int x, int y, int width, int height) {
        drawTextureBox(graphics, CURSORS, CURSORS_WIDTH, CURSORS_HEIGHT, 384, 373, 18, 18, x, y, width, height, 4.0f / getGuiScale(), false);
    }

    // Equivalent to Game1.drawDialogueBox(..., drawOnlyBox:true)
    public static void drawDialogueBoxFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        int unit = Math.max(1, Math.round(64.0f / getGuiScale()));
        int inset = Math.max(0, Math.round(28.0f / getGuiScale()));

        int centerX = x + inset;
        int centerY = y + inset;
        int centerW = Math.max(0, width - unit);
        int centerH = Math.max(0, height - unit);
        drawMenuTile(graphics, centerX, centerY, centerW, centerH, 9);

        drawMenuTile(graphics, x, y, unit, unit, 0);
        drawMenuTile(graphics, x + width - unit, y, unit, unit, 3);
        drawMenuTile(graphics, x + width - unit, y + height - unit, unit, unit, 15);
        drawMenuTile(graphics, x, y + height - unit, unit, unit, 12);

        drawMenuTile(graphics, x + unit, y, Math.max(0, width - unit * 2), unit, 2);
        drawMenuTile(graphics, x + unit, y + height - unit, Math.max(0, width - unit * 2), unit, 14);
        drawMenuTile(graphics, x, y + unit, unit, Math.max(0, height - unit * 2), 8);
        drawMenuTile(graphics, x + width - unit, y + unit, unit, Math.max(0, height - unit * 2), 11);
    }

    public static void drawTextureBox(GuiGraphics graphics, ResourceLocation texture, int texWidth, int texHeight, int srcX, int srcY, int srcW, int srcH, int x, int y, int width, int height, float scale, boolean drawShadow) {
		drawTextureBox(graphics, texture, texWidth, texHeight, srcX, srcY, srcW, srcH,
			x, y, width, height, scale, drawShadow, 1.0f);
	}

	public static void drawTextureBox(GuiGraphics graphics, ResourceLocation texture, int texWidth, int texHeight,
			int srcX, int srcY, int srcW, int srcH, int x, int y, int width, int height,
			float scale, boolean drawShadow, float alpha) {
		drawTextureBox(graphics, texture, texWidth, texHeight, srcX, srcY, srcW, srcH,
			x, y, width, height, scale, drawShadow, alpha, 8);
	}

	public static void drawTextureBox(GuiGraphics graphics, ResourceLocation texture, int texWidth, int texHeight,
			int srcX, int srcY, int srcW, int srcH, int x, int y, int width, int height,
			float scale, boolean drawShadow, float alpha, int shadowOffset) {
		drawTextureBoxTint(graphics, texture, texWidth, texHeight, srcX, srcY, srcW, srcH,
			x, y, width, height, scale, drawShadow, 1.0F, 1.0F, 1.0F, alpha, shadowOffset);
	}

	public static void drawTextureBoxTint(GuiGraphics graphics, ResourceLocation texture, int texWidth, int texHeight,
			int srcX, int srcY, int srcW, int srcH, int x, int y, int width, int height,
			float scale, boolean drawShadow, float red, float green, float blue, float alpha,
			int shadowOffset) {
		red = Math.max(0.0F, Math.min(1.0F, red));
		green = Math.max(0.0F, Math.min(1.0F, green));
		blue = Math.max(0.0F, Math.min(1.0F, blue));
		alpha = Math.max(0.0f, Math.min(1.0f, alpha));
		shadowOffset = Math.max(0, shadowOffset);
        if (texture.equals(CURSORS)) {
            if (srcX == 384 && srcY == 373 && srcW == 18 && srcH == 18) {
                texture = COMMON_TEXTURE_BOX_18;
                texWidth = 18;
                texHeight = 18;
                srcX = 0;
                srcY = 0;
            } else if (srcX == 384 && srcY == 396 && srcW == 15 && srcH == 15) {
                texture = COMMON_ENTRY_BOX_15;
                texWidth = 15;
                texHeight = 15;
                srcX = 0;
                srcY = 0;
            } else if (srcX == 403 && srcY == 383 && srcW == 6 && srcH == 6) {
                texture = COMMON_SCROLL_TRACK_BOX;
                texWidth = 6;
                texHeight = 6;
                srcX = 0;
                srcY = 0;
            } else if (srcX == 325 && srcY == 318 && srcW == 11 && srcH == 18) {
                texture = COMMON_SCROLL_BANNER_BOX_11;
                texWidth = 11;
                texHeight = 18;
                srcX = 0;
                srcY = 0;
            } else if (srcX == 375 && srcY == 357 && srcW == 3 && srcH == 3) {
                texture = COMMON_OPTION_HIGHLIGHT_BOX_3;
                texWidth = 3;
                texHeight = 3;
                srcX = 0;
                srcY = 0;
            } else if (srcX == 379 && srcY == 357 && srcW == 3 && srcH == 3) {
                texture = COMMON_CALENDAR_TODAY_BOX_3;
                texWidth = 3;
                texHeight = 3;
                srcX = 0;
                srcY = 0;
            } else if (srcX == 403 && srcY == 373 && srcW == 9 && srcH == 9) {
                texture = COMMON_BILLBOARD_ACCEPT_BOX_9;
                texWidth = 9;
                texHeight = 9;
                srcX = 0;
                srcY = 0;
            }
        } else if (texture.equals(MENU_TILES) && srcX == 0 && srcY == 256 && srcW == 60 && srcH == 60) {
            texture = MENU_TEXTURE_BOX_60;
            texWidth = 60;
            texHeight = 60;
            srcX = 0;
            srcY = 0;
        }

        int cornerSize = srcW / 3;
        int scaledCorner = Math.max(1, (int)(cornerSize * scale));

        if (drawShadow) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
			graphics.setColor(0.0F, 0.0F, 0.0F, 0.4F * alpha);
			drawRegion(graphics, texture, texWidth, texHeight, x + width - scaledCorner - shadowOffset, y + shadowOffset, scaledCorner, scaledCorner, srcX + cornerSize * 2, srcY, cornerSize, cornerSize);
			drawRegion(graphics, texture, texWidth, texHeight, x - shadowOffset, y + height - scaledCorner + shadowOffset, scaledCorner, scaledCorner, srcX, srcY + cornerSize * 2, cornerSize, cornerSize);
			drawRegion(graphics, texture, texWidth, texHeight, x + width - scaledCorner - shadowOffset, y + height - scaledCorner + shadowOffset, scaledCorner, scaledCorner, srcX + cornerSize * 2, srcY + cornerSize * 2, cornerSize, cornerSize);
			drawRegion(graphics, texture, texWidth, texHeight, x + scaledCorner - shadowOffset, y + shadowOffset, Math.max(0, width - scaledCorner * 2), scaledCorner, srcX + cornerSize, srcY, cornerSize, cornerSize);
			drawRegion(graphics, texture, texWidth, texHeight, x + scaledCorner - shadowOffset, y + height - scaledCorner + shadowOffset, Math.max(0, width - scaledCorner * 2), scaledCorner, srcX + cornerSize, srcY + cornerSize * 2, cornerSize, cornerSize);
			drawRegion(graphics, texture, texWidth, texHeight, x - shadowOffset, y + scaledCorner + shadowOffset, scaledCorner, Math.max(0, height - scaledCorner * 2), srcX, srcY + cornerSize, cornerSize, cornerSize);
			drawRegion(graphics, texture, texWidth, texHeight, x + width - scaledCorner - shadowOffset, y + scaledCorner + shadowOffset, scaledCorner, Math.max(0, height - scaledCorner * 2), srcX + cornerSize * 2, srcY + cornerSize, cornerSize, cornerSize);
			drawRegion(graphics, texture, texWidth, texHeight, x + scaledCorner / 2 - shadowOffset, y + scaledCorner / 2 + shadowOffset, Math.max(0, width - scaledCorner), Math.max(0, height - scaledCorner), srcX + cornerSize, srcY + cornerSize, cornerSize, cornerSize);
			graphics.setColor(red, green, blue, alpha);
        }
		if (!drawShadow) {
			graphics.setColor(red, green, blue, alpha);
		}

        drawRegion(graphics, texture, texWidth, texHeight, x + scaledCorner, y + scaledCorner, Math.max(0, width - scaledCorner * 2), Math.max(0, height - scaledCorner * 2), srcX + cornerSize, srcY + cornerSize, cornerSize, cornerSize);
        drawRegion(graphics, texture, texWidth, texHeight, x, y, scaledCorner, scaledCorner, srcX, srcY, cornerSize, cornerSize);
        drawRegion(graphics, texture, texWidth, texHeight, x + width - scaledCorner, y, scaledCorner, scaledCorner, srcX + cornerSize * 2, srcY, cornerSize, cornerSize);
        drawRegion(graphics, texture, texWidth, texHeight, x, y + height - scaledCorner, scaledCorner, scaledCorner, srcX, srcY + cornerSize * 2, cornerSize, cornerSize);
        drawRegion(graphics, texture, texWidth, texHeight, x + width - scaledCorner, y + height - scaledCorner, scaledCorner, scaledCorner, srcX + cornerSize * 2, srcY + cornerSize * 2, cornerSize, cornerSize);
        drawRegion(graphics, texture, texWidth, texHeight, x + scaledCorner, y, Math.max(0, width - scaledCorner * 2), scaledCorner, srcX + cornerSize, srcY, cornerSize, cornerSize);
        drawRegion(graphics, texture, texWidth, texHeight, x + scaledCorner, y + height - scaledCorner, Math.max(0, width - scaledCorner * 2), scaledCorner, srcX + cornerSize, srcY + cornerSize * 2, cornerSize, cornerSize);
        drawRegion(graphics, texture, texWidth, texHeight, x, y + scaledCorner, scaledCorner, Math.max(0, height - scaledCorner * 2), srcX, srcY + cornerSize, cornerSize, cornerSize);
        drawRegion(graphics, texture, texWidth, texHeight, x + width - scaledCorner, y + scaledCorner, scaledCorner, Math.max(0, height - scaledCorner * 2), srcX + cornerSize * 2, srcY + cornerSize, cornerSize, cornerSize);
		graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawRegion(GuiGraphics graphics, ResourceLocation texture, int texWidth, int texHeight, int x, int y, int width, int height, int u, int v, int srcWidth, int srcHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.blit(texture, x, y, width, height, u, v, srcWidth, srcHeight, texWidth, texHeight);
    }

    private static float getGuiScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return 1.0f;
        }
        return (float) mc.getWindow().getGuiScale();
    }

    private static ResourceLocation common(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/common/" + name + ".png");
    }

    private static ResourceLocation animalQuery(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/" + name + ".png");
    }

    private static ResourceLocation menuTile(int tileIndex) {
        return animalQuery("menu_tile_" + tileIndex);
    }

    private static void drawMenuTile(GuiGraphics graphics, int x, int y, int width, int height, int tileIndex) {
        if (width <= 0 || height <= 0) {
            return;
        }
        drawRegion(graphics, menuTile(tileIndex), 64, 64, x, y, width, height, 0, 0, 64, 64);
    }

    public static void drawMenuTileIndex(GuiGraphics graphics, int x, int y, int width, int height, int tileIndex) {
        drawMenuTile(graphics, x, y, width, height, tileIndex);
    }

}
