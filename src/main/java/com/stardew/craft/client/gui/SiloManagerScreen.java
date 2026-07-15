package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.menu.SiloManagerMenu;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * SDV-themed Silo manager screen. Simpler than coop/barn: shows hay storage
 * and build/demolish/relocate buttons.
 */
@SuppressWarnings("null")
public class SiloManagerScreen extends AbstractContainerScreen<SiloManagerMenu> {

    private static final int CLOSE_W = 12, CLOSE_SH = 12;

    // Colors
    private static final int COL_TITLE    = 0xFF5B3A1A;
    @SuppressWarnings("unused")
    private static final int COL_SUBTITLE = 0xFF8B7355;
    private static final int COL_TEXT     = 0xFF5B3A1A;
    private static final int COL_GRAY     = 0xFF9E9282;
    private static final int COL_RED      = 0xFFC62828;
    private static final int COL_GOLD     = 0xFFDAA520;
    private static final int COL_OK       = 0xFF4CAF50;
    private static final int COL_OVERLAY  = 0x88000000;
    private static final int COL_BAR_BG   = 0xFF3A3228;
    private static final int COL_BAR_FILL = 0xFF6B8E23;

    private final SiloManagerMenu mgr;
    private float guiScale = 1.0f;

    // Animation
    private long openedAtMs = -1;
    private int tickCount;
    private final float[] btnScale = {1.0f, 1.0f, 1.0f};
    private float closeScale = 1.0f;
    private float entryProgress;
    private float barProgress;

    // Hover
    private int hoveredButton = -1;
    private long lastHoverSoundMs;

    // Confirm
    private enum ConfirmType { NONE, DEMOLISH, RELOCATE }
    private ConfirmType confirmType = ConfirmType.NONE;
    private long confirmOpenMs;

    // SDV layout
    private static final int SDV_W = 860;

    // Layout
    private int panelX, panelY, panelW, panelH;
    private int closeX, closeY, closeW, closeH;
    private int pad;
    private int lineH;
    private int secGap;
    private int btnH;
    private int btnY;

    public SiloManagerScreen(SiloManagerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.mgr = menu;
        this.imageWidth = 1;
        this.imageHeight = 1;
        this.inventoryLabelY = Integer.MAX_VALUE;
        this.titleLabelY = Integer.MAX_VALUE;
    }

    private int ui(int sdvPx) { return Math.round(sdvPx / guiScale); }
    private float s4() { return 4.0f / guiScale; }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        guiScale = (float) mc.getWindow().getGuiScale();
        float s4 = s4();

        int fh = this.font.lineHeight;
        lineH = fh + 5;

        int borderCorner = Math.max(1, (int)(6.0f * s4));
        pad = borderCorner + 6;
        secGap = lineH;
        btnH = fh + 14;

        panelW = ui(SDV_W);
        panelW = Math.min(panelW, this.width - 8);

        recalcPanelHeight();

        this.leftPos = panelX;
        this.topPos = panelY;
        this.imageWidth = panelW;
        this.imageHeight = panelH;

        closeW = (int)(CLOSE_W * s4) + 2;
        closeH = (int)(CLOSE_SH * s4) + 2;
        closeX = panelX + panelW - borderCorner - closeW;
        closeY = panelY + borderCorner;

        if (openedAtMs < 0) {
            openedAtMs = System.currentTimeMillis();
            playSound(ModSounds.DOOR_CREAK.get(), 0.4f, 1.0f);
        }
    }

    private void recalcPanelHeight() {
        // 固定成一张完整的管理卡片，不再根据文字行数伸缩并穿插分隔线。
        panelH = Math.min(Math.max(220, ui(600)), this.height - 16);

        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        btnY = panelY + panelH - pad - btnH;

        float s4 = s4();
        int borderCorner = Math.max(1, (int)(6.0f * s4));
        closeX = panelX + panelW - borderCorner - closeW;
        closeY = panelY + borderCorner;

        this.leftPos = panelX;
        this.topPos = panelY;
        this.imageWidth = panelW;
        this.imageHeight = panelH;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        tickCount++;
        recalcPanelHeight();

        entryProgress += (1.0f - entryProgress) * 0.12f;
        if (entryProgress > 0.99f) entryProgress = 1.0f;

        float targetBar = mgr.isFormed() && mgr.getHayCapacity() > 0
            ? (float) mgr.getHayAmount() / mgr.getHayCapacity() * entryProgress
            : 0;
        barProgress += (targetBar - barProgress) * 0.12f;

        for (int i = 0; i < 3; i++) {
            float target = (hoveredButton == i) ? 1.08f : 1.0f;
            btnScale[i] += (target - btnScale[i]) * 0.18f;
        }
    }

    // ============================
    // Rendering
    // ============================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g, mouseX, mouseY, pt);
        updateHover(mouseX, mouseY);

        float s4 = s4();
        int cx = panelX + pad;
        int cw = panelW - pad * 2;

        CommonGuiTextures.drawTextureBox(g, panelX, panelY, panelW, panelH, s4, true);

        renderTitleBanner(g, s4);
        int cardY = panelY + Math.max(52, Math.round(28 * s4));
        int cardH = Math.max(72, btnY - cardY - Math.max(10, pad / 2));
        renderStorageCard(g, cx, cardY, cw, cardH);

        // == BUTTONS ==
        renderButtons(g, cx, btnY, cw, mouseX, mouseY);

        // -- Close button --
        boolean closeHov = inside(mouseX, mouseY, closeX, closeY, closeW, closeH);
        closeScale += ((closeHov ? 1.15f : 1.0f) - closeScale) * 0.15f;
        float cs = s4 * closeScale;
        int cdx = closeX + closeW / 2 - (int)(CLOSE_W * cs / 2);
        int cdy = closeY + closeH / 2 - (int)(CLOSE_SH * cs / 2);
        CommonGuiTextures.drawCloseButton(g, cdx, cdy, cs);

        // -- Confirm overlay --
        if (confirmType != ConfirmType.NONE) {
            // Item rendering is buffered separately. Flush the hay icon before
            // drawing the modal so it cannot be emitted above the dim layer.
            g.flush();
            renderConfirmDialog(g, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) { }
    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) { }

    private void renderTitleBanner(GuiGraphics g, float scale) {
        Component title = GuiText.ellipsize(this.font,
                Component.translatable("container.stardew_craft.silo_manager"), panelW - pad * 3);
        int textWidth = this.font.width(title);
        int textX = panelX + (panelW - textWidth) / 2;
        int bannerY = panelY + Math.max(5, Math.round(5 * scale));
        CommonGuiTextures.drawScrollBanner(g, textX, bannerY, textWidth, scale);
        int textY = bannerY + Math.max(0, (Math.round(18 * scale) - this.font.lineHeight) / 2);
        g.drawString(this.font, title, textX, textY, COL_TITLE, false);
    }

    private void renderStorageCard(GuiGraphics g, int x, int y, int w, int h) {
        CommonGuiTextures.drawEntryBox(g, x, y, w, h, s4(), false);
        int inset = Math.max(12, Math.round(8 * s4()));
        int tx = x + inset;
        int tw = Math.max(1, w - inset * 2);

        if (mgr.isFormed()) {
            Component status = Component.translatable("gui.stardew_craft.silo_manager.stage.formed");
            g.drawString(this.font, GuiText.ellipsize(this.font, status, tw), tx, y + inset, COL_OK, false);

            int iconBox = Math.min(46, Math.max(28, h - inset * 2 - lineH));
            int iconY = y + (h - iconBox) / 2 + lineH / 2;
            CommonGuiTextures.drawTextureBoxNoShadow(g, tx, iconY, iconBox, iconBox, 1.0f);
            g.pose().pushPose();
            float itemScale = Math.max(1.0f, Math.min(2.0f, (iconBox - 8) / 16.0f));
            g.pose().translate(tx + iconBox / 2.0f, iconY + iconBox / 2.0f, 0);
            g.pose().scale(itemScale, itemScale, 1.0f);
            g.renderItem(new ItemStack(ModItems.HAY.get()), -8, -8);
            g.pose().popPose();

            int infoX = tx + iconBox + Math.max(10, inset);
            int infoW = Math.max(1, x + w - inset - infoX);
            Component hayLabel = Component.translatable(
                "gui.stardew_craft.silo_manager.hay",
                mgr.getHayAmount(), mgr.getHayCapacity()
            );
            int infoY = y + inset + lineH + Math.max(4, (h - inset * 2 - lineH * 2) / 4);
            g.drawString(this.font, GuiText.ellipsize(this.font, hayLabel, infoW), infoX, infoY, COL_TEXT, false);
            int barY = infoY + lineH + 2;
            drawHayBar(g, infoX, barY, infoW, Math.max(10, this.font.lineHeight + 2), barProgress);
        } else {
            Component status = Component.translatable("gui.stardew_craft.silo_manager.stage.unformed");
            g.drawString(this.font, GuiText.ellipsize(this.font, status, tw), tx, y + inset, COL_GRAY, false);
            Component hint = Component.translatable("gui.stardew_craft.silo_manager.build_hint");
            int hintY = y + inset + lineH + 5;
            int readyY = GuiText.drawWrapped(g, this.font, hint, tx, hintY, tw, COL_TEXT, false, 3) + 7;

            if (mgr.canBuild()) {
                float breathe = 0.6f + 0.4f * Mth.sin(tickCount * 0.1f);
                Component ready = Component.translatable("gui.stardew_craft.silo_manager.ready");
                g.drawString(this.font, GuiText.ellipsize(this.font, ready, tw), tx, readyY, withAlpha(COL_GOLD, breathe), false);
            } else {
                Component notReady = Component.translatable("gui.stardew_craft.silo_manager.not_ready");
                g.drawString(this.font, GuiText.ellipsize(this.font, notReady, tw), tx, readyY, COL_RED, false);
            }
        }
    }

    private void drawHayBar(GuiGraphics g, int x, int y, int w, int h, float progress) {
        g.fill(x, y, x + w, y + h, 0xFF7B5636);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_BAR_BG);
        int fillW = (int)((w - 4) * Mth.clamp(progress, 0, 1));
        if (fillW > 0) {
            g.fillGradient(x + 2, y + 2, x + 2 + fillW, y + h - 2, 0xFF9DBA48, COL_BAR_FILL);
        }
    }

    // ============================
    // Buttons
    // ============================

    private void renderButtons(GuiGraphics g, int bx, int by, int bw, int mx, int my) {
        int gap = 8;
        int btnW = (bw - gap * 2) / 3;

        boolean canBuildBtn = !mgr.isFormed() && mgr.canBuild();
        boolean canManage = mgr.isFormed();

        Component buildLabel = Component.translatable("gui.stardew_craft.silo_manager.build");
        Component demolishLabel = Component.translatable("gui.stardew_craft.silo_manager.demolish");
        Component relocateLabel = Component.translatable("gui.stardew_craft.silo_manager.relocate");

        drawSdvButton(g, 0, bx, by, btnW, btnH, buildLabel, canBuildBtn, mx, my);
        drawSdvButton(g, 1, bx + btnW + gap, by, btnW, btnH, demolishLabel, canManage, mx, my);
        drawSdvButton(g, 2, bx + (btnW + gap) * 2, by, btnW, btnH, relocateLabel, canManage, mx, my);
    }

    private void drawSdvButton(GuiGraphics g, int idx, int x, int y, int w, int h,
                               Component label, boolean active, int mx, int my) {
        boolean hovered = hoveredButton == idx && active;
        float scale = btnScale[idx];

        g.pose().pushPose();
        float cxf = x + w / 2f, cyf = y + h / 2f;
        g.pose().translate(cxf, cyf, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.pose().translate(-cxf, -cyf, 0);

        float s4 = s4();
        CommonGuiTextures.drawTextureBox(g, x, y, w, h, s4, false);

        int inset = (int)(4 * s4);
        if (!active) {
            g.fill(x + inset, y + inset, x + w - inset, y + h - inset, 0x50888888);
        } else if (hovered) {
            g.fill(x + inset, y + inset, x + w - inset, y + h - inset, 0x30FFD700);
        }

        int textColor = !active ? 0xFF909090 : (hovered ? COL_TITLE : COL_TEXT);
        int th = this.font.lineHeight;
        GuiText.drawCenteredClamped(g, this.font, label, x + w / 2, y + (h - th) / 2, w - 8, textColor, hovered);
        g.pose().popPose();
    }

    // ============================
    // Confirm Dialog
    // ============================

    private void renderConfirmDialog(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, this.height, COL_OVERLAY);

        int dw = Math.min(panelW - pad, this.width - 16);
        int contentWidth = Math.max(1, dw - pad * 2);
        Component line1 = confirmType == ConfirmType.DEMOLISH
            ? Component.translatable("gui.stardew_craft.silo_manager.dialog.demolish.line1")
            : Component.translatable("gui.stardew_craft.silo_manager.dialog.relocate.line1");
        Component line2 = confirmType == ConfirmType.DEMOLISH
            ? Component.translatable("gui.stardew_craft.silo_manager.dialog.demolish.line2")
            : Component.translatable("gui.stardew_craft.silo_manager.dialog.relocate.line2");
        int textLines = 1
            + GuiText.wrappedLineCount(this.font, line1, contentWidth, 3)
            + GuiText.wrappedLineCount(this.font, line2, contentWidth, 3);
        int dh = pad * 2 + textLines * lineH + secGap + btnH;
        int dx = (this.width - dw) / 2;
        int dy = (this.height - dh) / 2;

        long elapsed = System.currentTimeMillis() - confirmOpenMs;
        float scale = elapsed < 200 ? 0.85f + 0.15f * easeOutCubic(elapsed / 200f) : 1.0f;

        g.pose().pushPose();
        float cxf = dx + dw / 2f, cyf = dy + dh / 2f;
        g.pose().translate(cxf, cyf, 200);
        g.pose().scale(scale, scale, 1.0f);
        g.pose().translate(-cxf, -cyf, 0);

        float s4 = s4();
        CommonGuiTextures.drawTextureBox(g, dx, dy, dw, dh, s4, true);

        int tx = dx + pad;
        int ty = dy + pad;

        Component title = confirmType == ConfirmType.DEMOLISH
            ? Component.translatable("gui.stardew_craft.silo_manager.dialog.demolish.title")
            : Component.translatable("gui.stardew_craft.silo_manager.dialog.relocate.title");
        g.drawString(this.font, GuiText.ellipsize(this.font, title, contentWidth), tx, ty, COL_TITLE, false);
        ty += lineH;

        ty = GuiText.drawWrapped(g, this.font, line1, tx, ty, contentWidth, COL_TEXT, false, 3) + 3;
        GuiText.drawWrapped(g, this.font, line2, tx, ty, contentWidth, COL_RED, false, 3);

        int cbW = 70;
        int cbH = this.font.lineHeight + 12;
        int cbY = dy + dh - pad - cbH;
        int confirmBtnX = dx + dw / 2 - cbW - 4;
        int cancelBtnX = dx + dw / 2 + 4;

        drawDialogButton(g, confirmBtnX, cbY, cbW, cbH,
                Component.translatable("gui.stardew_craft.silo_manager.dialog.confirm"),
                true, inside(mx, my, confirmBtnX, cbY, cbW, cbH));
        drawDialogButton(g, cancelBtnX, cbY, cbW, cbH,
                Component.translatable("gui.stardew_craft.silo_manager.dialog.back"),
                true, inside(mx, my, cancelBtnX, cbY, cbW, cbH));

        g.pose().popPose();
    }

    private void drawDialogButton(GuiGraphics g, int x, int y, int w, int h,
                                  Component label, boolean active, boolean hovered) {
        float s4 = s4();
        CommonGuiTextures.drawTextureBox(g, x, y, w, h, s4, false);

        int inset = (int)(4 * s4);
        if (!active) {
            g.fill(x + inset, y + inset, x + w - inset, y + h - inset, 0x50888888);
        } else if (hovered) {
            g.fill(x + inset, y + inset, x + w - inset, y + h - inset, 0x30FFD700);
        }

        int textColor = !active ? 0xFF909090 : (hovered ? COL_TITLE : COL_TEXT);
        int th = this.font.lineHeight;
        GuiText.drawCenteredClamped(g, this.font, label, x + w / 2, y + (h - th) / 2, w - 8, textColor, hovered);
    }

    // ============================
    // Input
    // ============================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        if (inside(mx, my, closeX, closeY, closeW, closeH)) {
            playSound(ModSounds.CANCEL.get(), 0.3f, 1.0f);
            this.onClose();
            return true;
        }
        if (confirmType != ConfirmType.NONE) return handleConfirmClick(mx, my);

        int bx = panelX + pad;
        int bw = panelW - pad * 2;
        int gap = 8;
        int btnW = (bw - gap * 2) / 3;
        int by = btnY;

        boolean canBuildBtn = !mgr.isFormed() && mgr.canBuild();
        boolean canManage = mgr.isFormed();

        if (canBuildBtn && inside(mx, my, bx, by, btnW, btnH)) {
            playSound(ModSounds.HAMMER.get(), 0.6f, 1.0f);
            var mc = this.minecraft;
            if (mc != null && mc.gameMode != null)
                mc.gameMode.handleInventoryButtonClick(menu.containerId, SiloManagerMenu.ACTION_BUILD);
            return true;
        }
        if (canManage && inside(mx, my, bx + btnW + gap, by, btnW, btnH)) {
            enterConfirm(ConfirmType.DEMOLISH);
            return true;
        }
        if (canManage && inside(mx, my, bx + (btnW + gap) * 2, by, btnW, btnH)) {
            enterConfirm(ConfirmType.RELOCATE);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleConfirmClick(int mx, int my) {
        int dw = Math.min(panelW - pad, this.width - 16);
        int contentWidth = Math.max(1, dw - pad * 2);
        Component line1 = confirmType == ConfirmType.DEMOLISH
            ? Component.translatable("gui.stardew_craft.silo_manager.dialog.demolish.line1")
            : Component.translatable("gui.stardew_craft.silo_manager.dialog.relocate.line1");
        Component line2 = confirmType == ConfirmType.DEMOLISH
            ? Component.translatable("gui.stardew_craft.silo_manager.dialog.demolish.line2")
            : Component.translatable("gui.stardew_craft.silo_manager.dialog.relocate.line2");
        int textLines = 1
            + GuiText.wrappedLineCount(this.font, line1, contentWidth, 3)
            + GuiText.wrappedLineCount(this.font, line2, contentWidth, 3);
        int dh = pad * 2 + textLines * lineH + secGap + btnH;
        int dx = (this.width - dw) / 2;
        int dy = (this.height - dh) / 2;

        int cbW = 70;
        int cbH = this.font.lineHeight + 12;
        int cbY = dy + dh - pad - cbH;
        int confirmBtnX = dx + dw / 2 - cbW - 4;
        int cancelBtnX = dx + dw / 2 + 4;

        if (inside(mx, my, confirmBtnX, cbY, cbW, cbH)) {
            executeConfirm();
            return true;
        }
        if (inside(mx, my, cancelBtnX, cbY, cbW, cbH)) {
            exitConfirm();
            return true;
        }
        if (!inside(mx, my, dx, dy, dw, dh)) {
            exitConfirm();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmType != ConfirmType.NONE && keyCode == 256) {
            exitConfirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        playSound(ModSounds.DOOR_CREAK_REVERSE.get(), 0.4f, 1.0f);
        super.onClose();
    }

    // ============================
    // Confirm logic
    // ============================

    private void enterConfirm(ConfirmType type) {
        confirmType = type;
        confirmOpenMs = System.currentTimeMillis();
        playSound(ModSounds.BIG_SELECT.get(), 0.5f, 1.0f);
    }

    private void exitConfirm() {
        if (confirmType == ConfirmType.NONE) return;
        confirmType = ConfirmType.NONE;
        playSound(ModSounds.BIG_DESELECT.get(), 0.4f, 1.0f);
    }

    private void executeConfirm() {
        var mc = this.minecraft;
        if (mc == null || mc.gameMode == null) return;
        switch (confirmType) {
            case DEMOLISH -> {
                playSound(ModSounds.EXPLOSION.get(), 0.3f, 1.0f);
                mc.gameMode.handleInventoryButtonClick(menu.containerId, SiloManagerMenu.ACTION_DEMOLISH);
            }
            case RELOCATE -> {
                playSound(ModSounds.BACKPACK_IN.get(), 0.5f, 1.0f);
                mc.gameMode.handleInventoryButtonClick(menu.containerId, SiloManagerMenu.ACTION_RELOCATE);
            }
            default -> {}
        }
        exitConfirm();
    }

    // ============================
    // Utility
    // ============================

    private void updateHover(int mx, int my) {
        if (confirmType != ConfirmType.NONE) { hoveredButton = -1; return; }

        int bx = panelX + pad;
        int bw = panelW - pad * 2;
        int gap = 8;
        int btnW = (bw - gap * 2) / 3;
        int by = btnY;

        int oldHover = hoveredButton;
        hoveredButton = -1;

        if (inside(mx, my, bx, by, btnW, btnH)) hoveredButton = 0;
        else if (inside(mx, my, bx + btnW + gap, by, btnW, btnH)) hoveredButton = 1;
        else if (inside(mx, my, bx + (btnW + gap) * 2, by, btnW, btnH)) hoveredButton = 2;

        if (hoveredButton >= 0 && hoveredButton != oldHover) {
            long now = System.currentTimeMillis();
            if (now - lastHoverSoundMs > 300) {
                playSound(ModSounds.SMALL_SELECT.get(), 0.2f, 1.0f);
                lastHoverSoundMs = now;
            }
        }
    }

    private static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0, 1);
        float f = 1 - t;
        return 1 - f * f * f;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Mth.clamp((int)(alpha * 255), 0, 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private void playSound(SoundEvent sound, float volume, float pitch) {
        var player = this.minecraft != null ? this.minecraft.player : null;
        if (player != null) player.playSound(sound, volume, pitch);
    }
}
