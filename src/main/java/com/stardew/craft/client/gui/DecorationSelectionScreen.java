package com.stardew.craft.client.gui;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.deco.PaintbrushSelectionManager;
import com.stardew.craft.network.payload.ApplyDecorationStylePayload;
import com.stardew.craft.network.payload.SetWallpaperSegmentPayload;
import com.stardew.craft.network.payload.OpenDecorationScreenPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class DecorationSelectionScreen extends Screen {

    private static final ResourceLocation LOCKED_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/locked.png");
    private static final ResourceLocation WALLPAPER_FRAME = common("decoration_wallpaper_frame");
    private static final ResourceLocation FLOORING_FRAME = common("decoration_flooring_frame");
    
    private static final int BG_OVERLAY = 0xD8111116;
    private static final int TITLE_COLOR = 0xFFF7F2DB; 
    private static final int TEXT_MUTED = 0xFFA0A0A0;

    private static final int UI_WIDTH = 380;
    private static final int UI_HEIGHT = 200;

    private final OpenDecorationScreenPayload payload;
    private final List<OpenDecorationScreenPayload.DecorationOption> options;
    private final boolean isWallpaper;

    private int scrollRow = 0;
    private int currentFocusIndex = -1;
    private float showcaseAlpha = 1.0f;
    private float[] itemHoverScales;

    // Segment override for wallpaper blocks (-1 = auto, 0/1/2 = bottom/middle/top)
    private int selectedSegment;
    private static final String[] SEGMENT_LABEL_KEYS = {
            "stardewcraft.ui.deco.segment.bottom",
            "stardewcraft.ui.deco.segment.middle",
            "stardewcraft.ui.deco.segment.top"
    };

    // derived constants
    private static final int COLS = 10;
    private static final int VISIBLE_ROWS = 7;
    private static final int CELL_SIZE = 22;

    public DecorationSelectionScreen(OpenDecorationScreenPayload payload) {
        super(Component.translatable("stardewcraft.deco.screen.title"));
        this.payload = payload;
        this.options = new ArrayList<>(payload.options());
        this.isWallpaper = "WALLPAPER".equals(payload.decorationType());
        this.itemHoverScales = new float[options.size()];
        this.selectedSegment = payload.currentSegment();
        for (int i = 0; i < itemHoverScales.length; i++) {
            itemHoverScales[i] = 1.0f;
        }
    }

    @Override
    protected void init() {
        super.init();
        currentFocusIndex = -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).styleId().equals(payload.currentStyleId())) {
                currentFocusIndex = i;
                scrollRow = Math.max(0, (i / COLS) - VISIBLE_ROWS / 2);
                break;
            }
        }
        if (currentFocusIndex == -1 && !options.isEmpty()) {
            currentFocusIndex = 0;
        }
        capScroll();
    }

    private void capScroll() {
        int maxRows = Math.max(0, ((options.size() - 1) / COLS) + 1);
        int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) {
            scrollRow -= 1;
            capScroll();
            return true;
        } else if (scrollY < 0) {
            scrollRow += 1;
            capScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check segment buttons first (wallpaper only)
            if (isWallpaper) {
                int segResult = checkSegmentButtonClick(mouseX, mouseY);
                if (segResult != Integer.MIN_VALUE) {
                    selectedSegment = segResult;
                    PaintbrushSelectionManager mgr = PaintbrushSelectionManager.get();
                    if (mgr.hasCompleteSelection() && mgr.getFirstPos() != null && mgr.getSecondPos() != null) {
                        PacketDistributor.sendToServer(SetWallpaperSegmentPayload.region(
                            payload.targetPos(), selectedSegment, mgr.getFirstPos(), mgr.getSecondPos()));
                    } else {
                        PacketDistributor.sendToServer(new SetWallpaperSegmentPayload(payload.targetPos(), selectedSegment));
                    }
                    return true;
                }
            }

            int gridX = getGridX();
            int gridY = getGridY();
            
            for (int r = 0; r < VISIBLE_ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int idx = (scrollRow + r) * COLS + c;
                    if (idx >= options.size()) continue;

                    int cx = gridX + c * CELL_SIZE;
                    int cy = gridY + r * CELL_SIZE;

                    if (mouseX >= cx && mouseX <= cx + CELL_SIZE && mouseY >= cy && mouseY <= cy + CELL_SIZE) {
                        onConfirm(idx);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onConfirm(int index) {
        OpenDecorationScreenPayload.DecorationOption option = options.get(index);
        if (!option.unlocked()) {
            com.stardew.craft.client.hud.StardewHudMessageManager.showError(
                    Component.translatable(option.unlockHintKey()));
            return;
        }
        PacketDistributor.sendToServer(buildApplyPayload(option.styleId()));
        // Clear selection state after applying
        PaintbrushSelectionManager.get().clearSelection();
        onClose();
    }

    private ApplyDecorationStylePayload buildApplyPayload(String styleId) {
        PaintbrushSelectionManager mgr = PaintbrushSelectionManager.get();
        if (mgr.hasCompleteSelection() && mgr.getFirstPos() != null && mgr.getSecondPos() != null) {
            return ApplyDecorationStylePayload.region(
                payload.decorationType(), payload.targetPos(), styleId,
                mgr.getFirstPos(), mgr.getSecondPos()
            );
        }
        return new ApplyDecorationStylePayload(payload.decorationType(), payload.targetPos(), styleId);
    }

    private int getPanelX() {
        return (width - UI_WIDTH) / 2;
    }

    private int getPanelY() {
        return (height - UI_HEIGHT) / 2;
    }

    private int getGridX() {
        return getPanelX() + 154;
    }

    private int getGridY() {
        return getPanelY() + 26;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int px = getPanelX();
        int py = getPanelY();

        // 更加优雅深邃的底色和高光边缘
        g.fill(px - 16, py - 10, px + UI_WIDTH + 16, py + UI_HEIGHT + 10, BG_OVERLAY);
        g.fillGradient(px + 144, py + 10, px + 145, py + UI_HEIGHT - 10, 0x40FFFFFF, 0x00FFFFFF);

        String titleStr = this.title.getString();
        g.drawString(this.font, titleStr, px + 154, py + 6, TITLE_COLOR, false);

        // Scroll indicator
        int totalRows = Math.max(1, ((options.size() - 1) / COLS) + 1);
        String pageTxt = (scrollRow + 1) + " / " + Math.max(1, totalRows - VISIBLE_ROWS + 1);
        g.drawString(this.font, pageTxt, px + UI_WIDTH - this.font.width(pageTxt), py + 6, TEXT_MUTED, false);

        updateVisualFocus(mouseX, mouseY);

        if (currentFocusIndex >= 0 && currentFocusIndex < options.size()) {
            drawElegantShowcase(g, px + 10, py + 10, currentFocusIndex);
        }

        // Segment toggle buttons for wallpaper mode
        if (isWallpaper) {
            drawSegmentButtons(g, px + 10, py + UI_HEIGHT - 28, 126);
        }

        int gridX = getGridX();
        int gridY = getGridY();

        for (int r = 0; r < VISIBLE_ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scrollRow + r) * COLS + c;
                if (idx >= options.size()) continue;

                int cx = gridX + c * CELL_SIZE;
                int cy = gridY + r * CELL_SIZE;

                // 绘制内嵌槽位背景
                g.fill(cx, cy, cx + CELL_SIZE - 2, cy + CELL_SIZE - 2, 0x4A000000);

                OpenDecorationScreenPayload.DecorationOption option = options.get(idx);
                float scale = itemHoverScales[idx];

                g.pose().pushPose();
                // Move center to center of cell for scaling
                g.pose().translate(cx + (CELL_SIZE - 2) / 2.0f, cy + (CELL_SIZE - 2) / 2.0f, 0);

                if (scale > 1.0f) {
                    g.pose().scale(scale, scale, 1.0f);
                    float lift = (scale - 1.0f) * -5.0f; // 更有张力的弹升
                    g.pose().translate(0, lift, 100); // 抬高Z轴防止被其他图形覆盖
                }

                if (!option.unlocked()) {
                    RenderSystem.setShaderColor(0.1F, 0.1F, 0.1F, 1.0F); // 更暗的未解锁状态
                    drawDecorationIcon(g, option, -8, -8, 16, 16);
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                } else {
                    drawDecorationIcon(g, option, -8, -8, 16, 16);
                }

                // 绘制当前正在使用的样式提示框（一圈淡淡的金光）
                if (option.styleId().equals(payload.currentStyleId())) {
                    int shimmerLight = 180 + (int)(50 * Math.sin(System.currentTimeMillis() / 200.0));
                    int goldC = (0xFF << 24) | (255 << 16) | (shimmerLight << 8) | 50;
                    g.renderOutline(-10, -10, 20, 20, goldC);
                    g.fill(-9, -9, -9+18, -9+18, 0x30FFD700); // 内部微光
                }

                g.pose().popPose();
            }
        }
    }

    private void updateVisualFocus(int mouseX, int mouseY) {
        int gridX = getGridX();
        int gridY = getGridY();

        for (int r = 0; r < VISIBLE_ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scrollRow + r) * COLS + c;
                if (idx >= options.size()) continue;

                int cx = gridX + c * CELL_SIZE;
                int cy = gridY + r * CELL_SIZE;

                boolean isHover = mouseX >= cx - 2 && mouseX <= cx + CELL_SIZE + 2 &&
                                  mouseY >= cy - 2 && mouseY <= cy + CELL_SIZE + 2;

                if (isHover) {
                    if (currentFocusIndex != idx) {
                        currentFocusIndex = idx;
                        showcaseAlpha = 0.0f;
                    }
                    itemHoverScales[idx] = Mth.lerp(0.3f, itemHoverScales[idx], 1.35f);
                } else {
                    itemHoverScales[idx] = Mth.lerp(0.15f, itemHoverScales[idx], 1.0f);
                }
            }
        }
        
        if (showcaseAlpha < 1.0f) {
            showcaseAlpha = Mth.lerp(0.1f, showcaseAlpha, 1.0f);
        }
    }

    private void drawElegantShowcase(GuiGraphics g, int px, int py, int focusIdx) {
        int width = 126; // area width available left of divider
        OpenDecorationScreenPayload.DecorationOption option = options.get(focusIdx);
        boolean unlocked = option.unlocked();

        int alpha = (int)(showcaseAlpha * 255);
        alpha = Mth.clamp(alpha, 0, 255);
        int alphaMask = alpha << 24;

        Component title = unlocked ? Component.literal(option.styleId()).withStyle(net.minecraft.ChatFormatting.BOLD) : Component.literal("???").withStyle(net.minecraft.ChatFormatting.BOLD);
        String printTitle = title.getString();
        
        g.pose().pushPose();
        float titleScale = 1.25f;
        int scaledWidth = (int)(width / titleScale);
        if (this.font.width(printTitle) > scaledWidth - 4) {
            printTitle = this.font.plainSubstrByWidth(printTitle, scaledWidth - 12) + "...";
        }
        int tx = px + (width - (int)(this.font.width(printTitle) * titleScale)) / 2;
        g.pose().translate(tx, py, 0);
        g.pose().scale(titleScale, titleScale, 1.0f);
        
        // 🌟 Cinematic label shimmer (breathing gold)
        int shimmerR = 255;
        int shimmerG = 200 + (int)(30 * Math.sin(System.currentTimeMillis() / 250.0));
        int shimmerB = 100;
        int shimmerColor = (shimmerR << 16) | (shimmerG << 8) | shimmerB;
        g.drawString(this.font, printTitle, 0, 0, alphaMask | shimmerColor, false);
        g.pose().popPose();

        // draw preview area
        int areaY = py + 22;
        int areaH = 80;
        
        // 🌟 视觉优化：底部动态椭圆阴影，配合上下浮动产生真实的空间Z轴感
        float floatY = (float)Math.sin((System.currentTimeMillis() % 6000) / 6000.0f * Math.PI * 2) * 4.0f;
        float shadowScale = 1.0f - (floatY + 4.0f) / 16.0f;
        int shadowWidth = (int)(40 * shadowScale);
        int shadowAlpha = (int)(showcaseAlpha * 120 * shadowScale);
        g.fillGradient(px + width / 2 - shadowWidth, areaY + areaH + 10, px + width / 2 + shadowWidth, areaY + areaH + 16, (shadowAlpha << 24) | 0x000000, 0x00000000);

        // Preview box translation & floating
        g.pose().pushPose();
        g.pose().translate(0, floatY, 0);

        // Frame shadow behind the preview
        g.fill(px + 4, areaY + 4, px + width - 4, areaY + areaH - 4, (alpha << 24) | 0x050505);

        // Border of preview
        int borderColor = (alphaMask | 0xFF353540);
        g.renderOutline(px + 3, areaY + 3, width - 6, areaH - 6, borderColor);
        
        if (unlocked) {
            drawTiledPreview(g, option, px + 5, areaY + 5, width - 10, areaH - 10, alpha);
        } else {
            // lock display
            g.fill(px + 5, areaY + 5, px + width - 5, areaY + areaH - 5, (alpha << 24) | 0x111111);
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, showcaseAlpha * 0.5f);
            g.blit(LOCKED_ICON, px + width / 2 - 8, areaY + areaH / 2 - 8, 0, 0, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // Inner elegant shadow/vignette overlay on top of the tiles for depth
        g.fillGradient(px + 5, areaY + 5, px + width - 5, areaY + 15, (alphaMask | 0x88000000), 0x00000000);
        g.fillGradient(px + 5, areaY + areaH - 15, px + width - 5, areaY + areaH - 5, 0x00000000, (alphaMask | 0x88000000));

        g.pose().popPose(); // End floaty preview box

        if (!unlocked) {
            Component desc = Component.translatable(option.unlockHintKey());
            List<FormattedCharSequence> descLines = this.font.split(desc, width - 4);
            int descY = areaY + areaH + 24;
            for (int i = 0; i < Math.min(4, descLines.size()); i++) {
                int dx = px + (width - this.font.width(descLines.get(i))) / 2;
                g.drawString(this.font, descLines.get(i), dx, descY, alphaMask | 0xFFF38D8D, false);
                descY += 10;
            }
        } else if (option.styleId().equals(payload.currentStyleId())) {
            // Display currently equipped text brightly
            Component eqText = Component.translatable("stardewcraft.ui.deco.currently_equipped");
            int eqWidth = this.font.width(eqText);
            int dx = px + (width - eqWidth) / 2;
            int descY = areaY + areaH + 24;
            g.drawString(this.font, eqText, dx, descY, alphaMask | 0xFF88FF88, false);
        }
    }

    private void drawSegmentButtons(GuiGraphics g, int px, int py, int areaW) {
        // 4 buttons: Auto, Bottom(0), Middle(1), Top(2)
        Component label = Component.translatable("stardewcraft.ui.deco.position");
        g.drawString(this.font, label, px, py + 4, 0xFFAAAAAA, false);
        int btnX = px + this.font.width(label) + 4;
        int btnW = 24;
        int btnH = 14;
        int gap = 3;

        // Auto button
        drawSegBtn(g, btnX, py, btnW, btnH,
                Component.translatable("stardewcraft.ui.deco.segment.auto"), selectedSegment == -1);
        btnX += btnW + gap;

        // Segment 0/1/2
        for (int i = 0; i < 3; i++) {
            drawSegBtn(g, btnX, py, btnW, btnH, Component.translatable(SEGMENT_LABEL_KEYS[i]), selectedSegment == i);
            btnX += btnW + gap;
        }
    }

    private void drawSegBtn(GuiGraphics g, int x, int y, int w, int h, Component text, boolean active) {
        int bg = active ? 0xCC3A5A1F : 0x66333333;
        int border = active ? 0xCC88CC44 : 0x66666666;
        int textColor = active ? 0xFFFFFFDD : 0xFFAAAAAA;
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, border);
        int tw = this.font.width(text);
        g.drawString(this.font, text, x + (w - tw) / 2, y + 3, textColor, false);
    }

    /**
     * Returns the segment value if a segment button was clicked, or Integer.MIN_VALUE if not.
     */
    private int checkSegmentButtonClick(double mouseX, double mouseY) {
        int px = getPanelX() + 10;
        int py = getPanelY() + UI_HEIGHT - 28;
        Component label = Component.translatable("stardewcraft.ui.deco.position");
        int btnX = px + this.font.width(label) + 4;
        int btnW = 24;
        int btnH = 14;
        int gap = 3;

        // Auto button → returns -1
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= py && mouseY <= py + btnH) {
            return -1;
        }
        btnX += btnW + gap;

        // Segment 0, 1, 2
        for (int i = 0; i < 3; i++) {
            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= py && mouseY <= py + btnH) {
                return i;
            }
            btnX += btnW + gap;
        }
        return Integer.MIN_VALUE;
    }
    
    private void drawTiledPreview(GuiGraphics graphics, OpenDecorationScreenPayload.DecorationOption option, int areaX, int areaY, int areaW, int areaH, int alpha) {
        ResourceLocation tex = ResourceLocation.parse(option.texture());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, showcaseAlpha);
        
        // 开启裁剪，防止预览溢出边框
        graphics.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);

        if (isWallpaper) {
            int idx = parseStyleIndex(option.styleId());
            int baseX = (idx % 16) * 16;
            int srcY = (idx / 16) * 48 + 8;
            int sampleW = 16;
            int sampleH = 28;
            int sampleY = areaY + (areaH - sampleH) / 2;
            for (int px = 0; px < areaW; px += sampleW) {
                graphics.blit(tex, areaX + px, sampleY, sampleW, sampleH, baseX, srcY, 16, 28, option.texWidth(), option.texHeight());
            }
        } else {
            int idx = parseStyleIndex(option.styleId());
            int bx = (idx % 8) * 32;
            int by = option.styleId().startsWith("MoreFloors:") ? (idx / 8) * 32 : 336 + (idx / 8) * 32;
            int tile = 16;
            for (int py = 0; py < areaH; py += tile) {
                int pz = (py / tile) % 2;
                for (int px = 0; px < areaW; px += tile) {
                    int px2 = (px / tile) % 2;
                    int srcX = bx + px2 * 16;
                    int srcY = by + pz * 16;
                    graphics.blit(tex, areaX + px, areaY + py, tile, tile, srcX, srcY, 16, 16, option.texWidth(), option.texHeight());
                }
            }
        }
        
        graphics.disableScissor();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    // Kept helper
    private int parseStyleIndex(String styleId) {
        int split = styleId.indexOf(':');
        try {
            if (split >= 0 && split + 1 < styleId.length()) {
                return Integer.parseInt(styleId.substring(split + 1));
            }
            return Integer.parseInt(styleId);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void drawDecorationIcon(GuiGraphics graphics,
                                    OpenDecorationScreenPayload.DecorationOption option,
                                    int x,
                                    int y,
                                    int w,
                                    int h) {
        int FRAME_SRC_SIZE = 16;
        ResourceLocation frameTexture = isWallpaper ? WALLPAPER_FRAME : FLOORING_FRAME;
        graphics.blit(frameTexture, x, y, w, h, 0.0f, 0.0f, FRAME_SRC_SIZE, FRAME_SRC_SIZE, FRAME_SRC_SIZE, FRAME_SRC_SIZE);

        ResourceLocation tex = ResourceLocation.parse(option.texture());
        int srcW = option.sourceWidth();
        int srcH = option.sourceHeight();
        int innerW;
        int innerH;
        int innerX;
        int innerY;

        if (isWallpaper) {
            innerW = 8;
            innerH = 14;
            innerX = x + (w - innerW) / 2;
            innerY = y + 1;
        } else {
            innerW = 14;
            innerH = 13;
            innerX = x + (w - innerW) / 2;
            innerY = y + 1;
        }

        graphics.blit(tex, innerX, innerY, innerW, innerH,
            option.sourceX(), option.sourceY(), srcW, srcH,
            option.texWidth(), option.texHeight());
    }

    private static ResourceLocation common(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/common/" + name + ".png");
    }
}
