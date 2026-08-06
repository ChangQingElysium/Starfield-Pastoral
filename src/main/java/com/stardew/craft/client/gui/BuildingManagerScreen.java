package com.stardew.craft.client.gui;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.menu.IBuildingManagerMenu;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified SDV-themed building manager screen for Coop / Barn.
 * V3: content-driven layout with font-relative spacing.
 */
@SuppressWarnings("null")
public class BuildingManagerScreen extends AbstractContainerScreen<AbstractContainerMenu> {

    private static final int CLOSE_W = 12, CLOSE_SH = 12;

    // Colors
    private static final int COL_TITLE    = 0xFF5B3A1A;
    private static final int COL_SUBTITLE = 0xFF8B7355;
    private static final int COL_TEXT     = 0xFF5B3A1A;
    private static final int COL_GRAY     = 0xFF9E9282;
    private static final int COL_RED      = 0xFFC62828;
    private static final int COL_RED_SOFT = 0xFFE57373;
    private static final int COL_GOLD     = 0xFFDAA520;
    private static final int COL_OK       = 0xFF4CAF50;
    private static final int COL_OVERLAY  = 0x88000000;
    private static final int COL_BAR_BG   = 0xFF3A3228;
    private static final int COL_BAR_FILL = 0xFF6B8E23;
    private static final int COL_BAR_WANT = 0xFFD4A017;

    // State
    private final IBuildingManagerMenu mgr;
    private final String family;
    private float guiScale = 1.0f;

    // Animation
    private long openedAtMs = -1;
    private int tickCount;
    private final float[] barProgress = new float[8];
    private final float[] btnScale = {1.0f, 1.0f, 1.0f};
    private float closeScale = 1.0f;
    private float entryProgress;

    // Hover
    private int hoveredButton = -1;
    private long lastHoverSoundMs;

    // Confirm
    private enum ConfirmType { NONE, DEMOLISH, RELOCATE }
    private ConfirmType confirmType = ConfirmType.NONE;
    private long confirmOpenMs;

    // SDV layout constant (sprite-px × 4)
    private static final int SDV_W = 860;   // panel width

    // Layout (GUI pixels, computed in init())
    private int panelX, panelY, panelW, panelH;
    private int closeX, closeY, closeW, closeH;
    private int pad;       // inner padding (border corner + margin)
    private int lineH;     // text line height (font.lineHeight + gap)
    private int secGap;    // section gap (includes divider)
    private int btnH;      // button height
    private int btnY;      // button row Y (anchored to bottom)
    private int requirementsViewportTop;
    private int requirementsViewportBottom;
    private int requirementsContentHeight;
    private int requirementsScroll;
    private int requirementsLayoutKey = Integer.MIN_VALUE;

    public BuildingManagerScreen(AbstractContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.mgr = (IBuildingManagerMenu) menu;
        this.family = mgr.buildingFamily();
        this.imageWidth = 1;
        this.imageHeight = 1;
        this.inventoryLabelY = Integer.MAX_VALUE;
        this.titleLabelY = Integer.MAX_VALUE;
    }

    /** SDV sprite-px to gui-px. Only for texture / border sizing. */
    private int ui(int sdvPx) { return Math.round(sdvPx / guiScale); }
    /** Texture scale factor (sprite-px * s4 = gui-px). */
    private float s4() { return 4.0f / guiScale; }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        guiScale = (float) mc.getWindow().getGuiScale();
        float s4 = s4();

        // Font-relative spacing (constant across guiScale)
        int fh = this.font.lineHeight;                       // always 9 gui px
        lineH = fh + 5;                                      // 14 px per text row

        // 9-slice corner size in gui pixels = (srcW/3) * s4
        int borderCorner = Math.max(1, (int)(6.0f * s4));
        pad = borderCorner + 6;                               // content safely inside border

        // Section gap: enough room for the decorative partition + whitespace
        secGap = lineH;                                       // one full line height

        // Button height
        btnH = fh + 14;

        // Panel width (fixed)
        panelW = ui(SDV_W);
        panelW = Math.min(panelW, this.width - 8);

        // Compute initial panel layout from data
        recalcPanelHeight();

        this.leftPos = panelX;
        this.topPos = panelY;
        this.imageWidth = panelW;
        this.imageHeight = panelH;

        // Close button — inside top-right border region
        closeW = (int)(CLOSE_W * s4) + 2;
        closeH = (int)(CLOSE_SH * s4) + 2;
        closeX = panelX + panelW - borderCorner - closeW;
        closeY = panelY + borderCorner;

        if (openedAtMs < 0) {
            openedAtMs = System.currentTimeMillis();
            playSound(ModSounds.DOOR_CREAK.get(), 0.4f, 1.0f);
        }
    }

    /**
     * Recomputes panelH, panelX, panelY, btnY from current data.
     * Called in init() and every tick, so the panel dynamically
     * adjusts to match the actual requirement row count.
     */
    private void recalcPanelHeight() {
        // Header: title + stage + (animal/unbuilt line) + optional enclosed
        int headerLines = 3;
        if ((mgr.hasExistingBuilding() || mgr.getCurrentTier() > 0)
                && mgr.isEnclosedRequired()) {
            headerLines = 4;
        }

        // Requirements: section title + data rows + optional "Ready!"
        int reqLines;
        if (mgr.isAtMaxTier()) {
            reqLines = 2;
        } else {
            List<RequirementRow> rows = collectRequirements();
            reqLines = 1 + rows.size();
            if (mgr.canBuildOrUpgrade() && !rows.isEmpty()) reqLines++;
        }

        panelH = pad                              // top padding
               + headerLines * lineH              // header section
               + secGap                           // divider 1
               + reqLines * lineH                 // requirements section
               + secGap                           // divider 2
               + btnH                             // button row
               + pad;                             // bottom padding

        // Clamp: never exceed screen minus small margin
        panelH = Math.min(panelH, this.height - 8);

        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        // Buttons always anchored to bottom
        btnY = panelY + panelH - pad - btnH;

        // Update close button position (tracks with panel position)
        float s4 = s4();
        int borderCorner = Math.max(1, (int)(6.0f * s4));
        closeX = panelX + panelW - borderCorner - closeW;
        closeY = panelY + borderCorner;

        // Keep AbstractContainerScreen in sync
        this.leftPos = panelX;
        this.topPos = panelY;
        this.imageWidth = panelW;
        this.imageHeight = panelH;
    }

    // ============================
    // Tick
    // ============================

    @Override
    protected void containerTick() {
        super.containerTick();
        tickCount++;

        // Recalculate panel height every tick in case data changed
        // (container data syncs asynchronously from server)
        recalcPanelHeight();

        entryProgress += (1.0f - entryProgress) * 0.12f;
        if (entryProgress > 0.99f) entryProgress = 1.0f;

        List<RequirementRow> rows = collectRequirements();
        int layoutKey = 31 * mgr.getCurrentTier()
                + rows.size()
                + (mgr.isAtMaxTier() ? 1_000 : 0);
        if (layoutKey != requirementsLayoutKey) {
            requirementsLayoutKey = layoutKey;
            requirementsContentHeight = 0;
            requirementsScroll = 0;
        }
        for (int i = 0; i < barProgress.length; i++) {
            float target = i < rows.size() ? rows.get(i).ratio() * entryProgress : 0;
            barProgress[i] += (target - barProgress[i]) * 0.12f;
        }
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
        int cx = panelX + pad;   // content left
        int cw = panelW - pad * 2; // content width

        // Main panel border (SDV 9-slice)
        CommonGuiTextures.drawTextureBox(g, panelX, panelY, panelW, panelH, s4, true);

        int y = panelY + pad;

        // == HEADER SECTION ==
        y = renderHeader(g, cx, y, cw);

        // -- Divider 1 (centered in secGap) --
        StardewGuiUtil.drawHorizontalPartitionSmall(g, cx, y + secGap / 2 - 2, cw, s4);
        y += secGap;

        // == REQUIREMENTS SECTION ==
        // Long localized requirement labels wrap inside a scrollable viewport.
        int reqClipBottom = btnY - secGap;
        requirementsViewportTop = y;
        requirementsViewportBottom = Math.max(y, reqClipBottom);
        int viewportHeight = Math.max(0,
                requirementsViewportBottom - requirementsViewportTop);
        clampRequirementsScroll(viewportHeight);
        if (viewportHeight > 0) {
            g.enableScissor(
                    panelX,
                    requirementsViewportTop,
                    panelX + panelW,
                    requirementsViewportBottom);
            int contentTop = requirementsViewportTop - requirementsScroll;
            int contentBottom = renderRequirements(g, cx, contentTop, cw - 6);
            requirementsContentHeight = Math.max(
                    viewportHeight,
                    contentBottom - contentTop);
            g.disableScissor();
            clampRequirementsScroll(viewportHeight);
            renderRequirementsScrollbar(
                    g,
                    cx + cw - 3,
                    requirementsViewportTop,
                    viewportHeight);
        } else {
            requirementsContentHeight = 0;
            requirementsScroll = 0;
        }

        // -- Divider 2 (above buttons) --
        int div2Y = btnY - secGap;
        StardewGuiUtil.drawHorizontalPartitionSmall(g, cx, div2Y + secGap / 2 - 2, cw, s4);

        // == BUTTONS (anchored to bottom) ==
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
            renderConfirmDialog(g, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) { }
    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) { }

    // ============================
    // Header
    // ============================

    private int renderHeader(GuiGraphics g, int cx, int y, int cw) {
        int tier = mgr.getCurrentTier();

        // Title row: building name + tier stars. The name wraps while the
        // compact tier marker keeps its own slot on the first line.
        Component name = getBuildingDisplayName();
        String stars = tier > 0 ? "\u2605".repeat(tier) : "";
        int starWidth = this.font.width(stars);
        int titleMaxW = tier > 0
                ? Math.max(1, cw - starWidth - 6)
                : cw;
        int titleY = y;
        y = GuiText.drawWrapped(
                g, this.font, name, cx, y, titleMaxW,
                COL_TITLE, false, 0);
        if (tier > 0) {
            int starCol = switch (tier) {
                case 1 -> 0xFFCD7F32;
                case 2 -> 0xFFC0C0C0;
                default -> 0xFFFFD700;
            };
            g.drawString(
                    this.font,
                    stars,
                    cx + cw - starWidth,
                    titleY,
                    starCol,
                    false);
        }

        // Stage subtitle
        Component stage = Component.translatable(
                "gui.stardew_craft." + family + "_manager.stage", stageLabel());
        y = GuiText.drawWrapped(
                g, this.font, stage, cx, y, cw,
                COL_SUBTITLE, false, 0);

        // Animal count or unbuilt text
        if (mgr.hasExistingBuilding() || tier > 0) {
            int maxCap = getMaxCapacity();
            int curAnimals = mgr.getBoundAnimalCount();
            Component animals = Component.literal(
                    "\u2767 " + curAnimals + " / " + maxCap);
            g.drawString(
                    this.font,
                    animals,
                    cx,
                    y,
                    curAnimals >= maxCap ? COL_RED : COL_TEXT,
                    false);
            y += lineH;

            // Enclosed status
            if (mgr.isEnclosedRequired()) {
                boolean enc = mgr.isEnclosed();
                String icon = enc ? "\u2714 " : "\u2718 ";
                Component encText = Component.literal(icon).append(
                        Component.translatable("gui.stardew_craft." + family + "_manager."
                                + (enc ? "enclosed_ok" : "need.enclosed")));
                y = GuiText.drawWrapped(
                        g, this.font, encText, cx, y, cw,
                        enc ? COL_OK : COL_RED, false, 0);
            }
        } else {
            Component noBuilding = Component.translatable(
                    "gui.stardew_craft." + family + "_manager.stage.unformed");
            y = GuiText.drawWrapped(
                    g, this.font, noBuilding, cx, y, cw,
                    COL_GRAY, false, 0);
        }

        return y;
    }

    // ============================
    // Requirements
    // ============================

    private int renderRequirements(GuiGraphics g, int rx, int ry, int rw) {
        if (mgr.isAtMaxTier()) {
            Component maxText = Component.translatable(
                    "gui.stardew_craft." + family + "_manager.max");
            ry = GuiText.drawWrapped(
                    g, this.font, maxText, rx, ry, rw,
                    COL_GOLD, false, 0);
            Component readyText = Component.translatable(
                    "gui.stardew_craft." + family + "_manager.ready");
            ry = GuiText.drawWrapped(
                    g, this.font, readyText, rx, ry, rw,
                    COL_OK, false, 0);
            return ry;
        }

        // Section title
        Component header = mgr.isRevalidating()
                ? Component.translatable(
                        "gui.stardew_craft.animal_manager.revalidate")
                : mgr.getCurrentTier() <= 0
                ? Component.translatable("gui.stardew_craft." + family + "_manager.build")
                : Component.translatable("gui.stardew_craft." + family + "_manager.upgrade");
        ry = GuiText.drawWrapped(
                g, this.font, header, rx, ry, rw,
                COL_GOLD, false, 0);
        ry += 2;

        // Requirement rows
        List<RequirementRow> rows = collectRequirements();
        int barW = Math.min(60, rw / 4);
        int barH = this.font.lineHeight - 2;

        for (int i = 0; i < rows.size(); i++) {
            RequirementRow row = rows.get(i);
            int rowY = ry;

            // Item icon (scaled to match line height)
            float iconScale = (this.font.lineHeight + 2) / 16.0f;
            CommonGuiTextures.drawItem(g, row.icon, rx, rowY - 1, iconScale);

            // The label owns the whole first row and can wrap. Progress and
            // count use a dedicated row below it, so localization never has
            // to compete with the bar for a narrow single-line slot.
            int labelX = rx + this.font.lineHeight + 6;
            int labelWidth = Math.max(1, rw - (labelX - rx));
            List<FormattedCharSequence> labelLines =
                    this.font.split(row.label, labelWidth);
            int labelY = rowY;
            for (FormattedCharSequence line : labelLines) {
                g.drawString(this.font, line, labelX, labelY,
                        COL_TEXT, false);
                labelY += lineH;
            }
            int labelBottom = Math.max(rowY + lineH, labelY);

            // Count and progress bar on the following row.
            String countStr = row.current + "/" + row.required;
            int countColor = row.met ? COL_OK : COL_RED;
            int countW = this.font.width(countStr);
            int progressY = labelBottom;
            g.drawString(this.font, countStr,
                    rx + rw - countW, progressY,
                    countColor, false);
            int availableBarWidth = Math.max(
                    12,
                    rw - (labelX - rx) - countW - 8);
            int actualBarW = Math.min(barW, availableBarWidth);
            drawProgressBar(
                    g,
                    labelX,
                    progressY + 2,
                    actualBarW,
                    barH,
                    barProgress[i],
                    row.met);

            ry = progressY + lineH + 3;
        }

        // "Ready!" message
        if (mgr.canBuildOrUpgrade() && !rows.isEmpty()) {
            float breathe = 0.6f + 0.4f * Mth.sin(tickCount * 0.1f);
            Component ready = Component.translatable(
                    "gui.stardew_craft." + family + "_manager.ready");
            ry = GuiText.drawWrappedCentered(
                    g,
                    this.font,
                    ready,
                    rx + rw / 2,
                    ry,
                    rw,
                    withAlpha(COL_GOLD, breathe),
                    true,
                    0);
        }

        return ry;
    }

    private void clampRequirementsScroll(int viewportHeight) {
        int maxScroll = Math.max(
                0,
                requirementsContentHeight - Math.max(0, viewportHeight));
        requirementsScroll = Mth.clamp(
                requirementsScroll,
                0,
                maxScroll);
    }

    private void renderRequirementsScrollbar(
            GuiGraphics g,
            int x,
            int y,
            int viewportHeight
    ) {
        if (requirementsContentHeight <= viewportHeight
                || viewportHeight <= 0) {
            return;
        }
        g.fill(x, y, x + 2, y + viewportHeight, 0x403A3228);
        int thumbHeight = Math.max(
                10,
                viewportHeight * viewportHeight
                        / requirementsContentHeight);
        int maxScroll = requirementsContentHeight - viewportHeight;
        int travel = viewportHeight - thumbHeight;
        int thumbY = y + (maxScroll <= 0
                ? 0
                : requirementsScroll * travel / maxScroll);
        g.fill(x, thumbY, x + 2, thumbY + thumbHeight, COL_GOLD);
    }

    private void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, float progress, boolean met) {
        g.fill(x, y, x + w, y + h, COL_BAR_BG);
        int fillW = (int)((w - 2) * Mth.clamp(progress, 0, 1));
        if (fillW > 0) {
            int barColor = met ? COL_BAR_FILL : COL_BAR_WANT;
            g.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, barColor);
        }
    }

    // ============================
    // Buttons
    // ============================

    private void renderButtons(GuiGraphics g, int bx, int by, int bw, int mx, int my) {
        int gap = 8;
        int btnW = (bw - gap * 2) / 3;

        boolean canBuild = !mgr.isAtMaxTier() && mgr.canBuildOrUpgrade();
        boolean canManage = mgr.hasExistingBuilding();

        drawSdvButton(g, 0, bx, by, btnW, btnH, getActionLabel(), canBuild, mx, my);
        drawSdvButton(g, 1, bx + btnW + gap, by, btnW, btnH, getDemolishLabel(), canManage, mx, my);
        drawSdvButton(g, 2, bx + (btnW + gap) * 2, by, btnW, btnH, getRelocateLabel(), canManage, mx, my);
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
        GuiText.drawCenteredFitted(
                g, this.font, label, x + w / 2, y + h / 2,
                w - 8, textColor, hovered);
        g.pose().popPose();
    }

    // ============================
    // Confirm Dialog
    // ============================

    private void renderConfirmDialog(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, this.height, COL_OVERLAY);

        int dw = Math.min(panelW - pad, this.width - 16);
        int contentWidth = Math.max(1, dw - pad * 2);
        List<Component> dialogLines = confirmDialogLines();
        Component title = confirmDialogTitle();
        int dLines = GuiText.wrappedLineCount(
                this.font, title, contentWidth, 0);
        for (Component line : dialogLines) {
            dLines += GuiText.wrappedLineCount(this.font, line, contentWidth, 0);
        }
        int dh = pad * 2 + dLines * lineH + secGap + btnH;
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

        ty = GuiText.drawWrapped(
                g, this.font, title, tx, ty, contentWidth,
                COL_TITLE, false, 0);

        for (int i = 0; i < dialogLines.size(); i++) {
            int color = i == dialogLines.size() - 1 ? COL_RED : COL_TEXT;
            if (confirmType == ConfirmType.DEMOLISH && i == dialogLines.size() - 1 && mgr.getBoundAnimalCount() > 0) {
                color = COL_RED_SOFT;
            }
            ty = GuiText.drawWrapped(g, this.font, dialogLines.get(i), tx, ty, contentWidth, color, false, 0) + 3;
        }

        int cbW = 70;
        int cbH = this.font.lineHeight + 12;
        int cbY = dy + dh - pad - cbH;
        int confirmBtnX = dx + dw / 2 - cbW - 4;
        int cancelBtnX = dx + dw / 2 + 4;

        boolean canConfirm = canConfirmCurrentAction();
        drawDialogButton(g, confirmBtnX, cbY, cbW, cbH, getConfirmButtonLabel(),
                canConfirm, canConfirm && inside(mx, my, confirmBtnX, cbY, cbW, cbH));
        drawDialogButton(g, cancelBtnX, cbY, cbW, cbH,
                Component.translatable("gui.stardew_craft." + family + "_manager.dialog.back"),
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
        GuiText.drawCenteredFitted(
                g, this.font, label, x + w / 2, y + h / 2,
                w - 8, textColor, hovered);
    }

    private List<Component> confirmDialogLines() {
        if (confirmType == ConfirmType.DEMOLISH) {
            return List.of(
                Component.translatable("gui.stardew_craft." + family + "_manager.dialog.demolish.line1"),
                Component.translatable("gui.stardew_craft." + family + "_manager.dialog.demolish.line2"),
                mgr.getBoundAnimalCount() > 0
                    ? Component.translatable("gui.stardew_craft." + family + "_manager.dialog.demolish.blocked_animals", mgr.getBoundAnimalCount())
                    : Component.translatable("gui.stardew_craft." + family + "_manager.dialog.demolish.line3")
            );
        }
        if (confirmType == ConfirmType.RELOCATE) {
            return List.of(
                Component.translatable("gui.stardew_craft." + family + "_manager.dialog.relocate.line1"),
                Component.translatable("gui.stardew_craft." + family + "_manager.dialog.relocate.line2"),
                Component.translatable("gui.stardew_craft." + family + "_manager.dialog.relocate.line3")
            );
        }
        return List.of();
    }

    // ============================
    // Input
    // ============================

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (confirmType == ConfirmType.NONE
                && inside(
                        (int) mouseX,
                        (int) mouseY,
                        panelX,
                        requirementsViewportTop,
                        panelW,
                        Math.max(
                                0,
                                requirementsViewportBottom
                                        - requirementsViewportTop))
                && requirementsContentHeight
                        > requirementsViewportBottom
                                - requirementsViewportTop) {
            requirementsScroll -= (int) Math.round(
                    scrollY * lineH * 2.0D);
            clampRequirementsScroll(
                    requirementsViewportBottom
                            - requirementsViewportTop);
            return true;
        }
        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY);
    }

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

        // Buttons layout (must match renderButtons)
        int bx = panelX + pad;
        int bw = panelW - pad * 2;
        int gap = 8;
        int btnW = (bw - gap * 2) / 3;
        int by = btnY;

        boolean canBuild = !mgr.isAtMaxTier() && mgr.canBuildOrUpgrade();
        boolean canManage = mgr.hasExistingBuilding();

        if (canBuild && inside(mx, my, bx, by, btnW, btnH)) {
            playSound(ModSounds.HAMMER.get(), 0.6f, 1.0f);
            var mc = this.minecraft;
            if (mc != null && mc.gameMode != null)
                mc.gameMode.handleInventoryButtonClick(menu.containerId, IBuildingManagerMenu.ACTION_BUILD_OR_UPGRADE);
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
        int dLines = GuiText.wrappedLineCount(
                this.font, confirmDialogTitle(), contentWidth, 0);
        for (Component line : confirmDialogLines()) {
            dLines += GuiText.wrappedLineCount(this.font, line, contentWidth, 0);
        }
        int dh = pad * 2 + dLines * lineH + secGap + btnH;
        int dx = (this.width - dw) / 2;
        int dy = (this.height - dh) / 2;

        int cbW = 70;
        int cbH = this.font.lineHeight + 12;
        int cbY = dy + dh - pad - cbH;
        int confirmBtnX = dx + dw / 2 - cbW - 4;
        int cancelBtnX = dx + dw / 2 + 4;

        if (canConfirmCurrentAction() && inside(mx, my, confirmBtnX, cbY, cbW, cbH)) {
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

    private Component confirmDialogTitle() {
        return switch (confirmType) {
            case DEMOLISH -> Component.translatable(
                    "gui.stardew_craft." + family
                            + "_manager.dialog.demolish.title");
            case RELOCATE -> Component.translatable(
                    "gui.stardew_craft." + family
                            + "_manager.dialog.relocate.title");
            default -> Component.empty();
        };
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

    private boolean canConfirmCurrentAction() {
        return confirmType != ConfirmType.DEMOLISH || mgr.getBoundAnimalCount() <= 0;
    }

    private void executeConfirm() {
        var mc = this.minecraft;
        if (mc == null || mc.gameMode == null) return;
        switch (confirmType) {
            case DEMOLISH -> {
                playSound(ModSounds.EXPLOSION.get(), 0.3f, 1.0f);
                mc.gameMode.handleInventoryButtonClick(menu.containerId, IBuildingManagerMenu.ACTION_DEMOLISH);
            }
            case RELOCATE -> {
                playSound(ModSounds.BACKPACK_IN.get(), 0.5f, 1.0f);
                mc.gameMode.handleInventoryButtonClick(menu.containerId, IBuildingManagerMenu.ACTION_RELOCATE);
            }
            default -> {}
        }
        exitConfirm();
    }

    // ============================
    // Data helpers
    // ============================

    private record RequirementRow(ItemStack icon, Component label, int current, int required, boolean met) {
        float ratio() { return required > 0 ? (float) Math.min(current, required) / required : 1.0f; }
    }

    private List<RequirementRow> collectRequirements() {
        List<RequirementRow> rows = new ArrayList<>();
        if (mgr.isEnclosedRequired() && !mgr.isEnclosed()) {
            rows.add(new RequirementRow(new ItemStack(Items.BRICKS),
                    Component.translatable("gui.stardew_craft." + family + "_manager.need.enclosed"), 0, 1, false));
        }
        addReq(rows, new ItemStack(ModBlocks.FEED_TROUGH.get().asItem()),
                Component.translatable("gui.stardew_craft." + family + "_manager.need.feed_trough",
                        Math.max(0, mgr.getReqFeedTrough() - mgr.getCurFeedTrough())),
                mgr.getCurFeedTrough(), mgr.getReqFeedTrough());
        addReq(rows, new ItemStack(ModBlocks.AUTOFEED_TROUGH.get().asItem()),
                Component.translatable("gui.stardew_craft." + family + "_manager.need.auto_feed_trough",
                        Math.max(0, mgr.getReqAutoFeedTrough() - mgr.getCurAutoFeedTrough())),
                mgr.getCurAutoFeedTrough(), mgr.getReqAutoFeedTrough());
        addReq(rows, new ItemStack(ModBlocks.HAY_HOPPER.get().asItem()),
                Component.translatable("gui.stardew_craft." + family + "_manager.need.hay_hopper",
                        Math.max(0, mgr.getReqHayHopper() - mgr.getCurHayHopper())),
                mgr.getCurHayHopper(), mgr.getReqHayHopper());
        addReq(rows, new ItemStack(ModBlocks.INCUBATOR.get().asItem()),
                Component.translatable("gui.stardew_craft." + family + "_manager.need.incubator",
                        Math.max(0, mgr.getReqIncubator() - mgr.getCurIncubator())),
                mgr.getCurIncubator(), mgr.getReqIncubator());
        if (mgr.hasInteriorSpace()) {
            addReq(rows, new ItemStack(Items.SCAFFOLDING),
                    Component.translatable("gui.stardew_craft." + family + "_manager.need.interior_blocks",
                            mgr.getReqInteriorBlocks(), mgr.getCurInteriorBlocks()),
                    mgr.getCurInteriorBlocks(), mgr.getReqInteriorBlocks());
        } else if (mgr.getReqInteriorBlocks() > 0) {
            rows.add(new RequirementRow(new ItemStack(Items.BARRIER),
                    Component.translatable("gui.stardew_craft." + family + "_manager.need.interior"), 0, 1, false));
        }
        if (mgr.isDoorRequired() && mgr.getReqDoorCount() > 0) {
            addReq(rows, new ItemStack(Items.OAK_DOOR),
                    Component.translatable("gui.stardew_craft." + family + "_manager.need.door",
                            mgr.getReqDoorCount(), mgr.getCurDoorCount()),
                    mgr.getCurDoorCount(), mgr.getReqDoorCount());
        }
        return rows;
    }

    private void addReq(List<RequirementRow> rows, ItemStack icon, Component label, int cur, int req) {
        if (req > 0) rows.add(new RequirementRow(icon, label, cur, req, cur >= req));
    }

    private Component getBuildingDisplayName() {
        return Component.translatable("container.stardew_craft." + family + "_manager");
    }

    private Component stageLabel() {
        return switch (mgr.getCurrentTier()) {
            case 1 -> Component.translatable("gui.stardew_craft." + family + "_manager.stage.t1");
            case 2 -> Component.translatable("gui.stardew_craft." + family + "_manager.stage.t2");
            case 3 -> Component.translatable("gui.stardew_craft." + family + "_manager.stage.t3");
            default -> Component.translatable("gui.stardew_craft." + family + "_manager.stage.unformed");
        };
    }

    private Component getActionLabel() {
        if (mgr.isRevalidating()) {
            return Component.translatable(
                    "gui.stardew_craft.animal_manager.revalidate");
        }
        if (mgr.isAtMaxTier()) return Component.translatable("gui.stardew_craft." + family + "_manager.max");
        if (mgr.getCurrentTier() <= 0) return Component.translatable("gui.stardew_craft." + family + "_manager.build");
        return Component.translatable("gui.stardew_craft." + family + "_manager.upgrade");
    }

    private Component getDemolishLabel() {
        return Component.translatable("gui.stardew_craft." + family + "_manager.demolish");
    }

    private Component getRelocateLabel() {
        return Component.translatable("gui.stardew_craft." + family + "_manager.relocate");
    }

    private Component getConfirmButtonLabel() {
        return switch (confirmType) {
            case DEMOLISH -> Component.translatable("gui.stardew_craft." + family + "_manager.dialog.confirm_demolish");
            case RELOCATE -> Component.translatable("gui.stardew_craft." + family + "_manager.dialog.confirm_relocate");
            default -> Component.translatable("gui.stardew_craft." + family + "_manager.dialog.confirm");
        };
    }

    private int getMaxCapacity() {
        return switch (mgr.getCurrentTier()) { case 1 -> 4; case 2 -> 8; case 3 -> 12; default -> 0; };
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
