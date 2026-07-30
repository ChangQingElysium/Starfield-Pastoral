package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.menu.FishPondManagerMenu;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@SuppressWarnings("null")
public class FishPondManagerScreen extends AbstractContainerScreen<FishPondManagerMenu> {
    private static final int CLOSE_W = 12, CLOSE_SH = 12;

    private static final int COL_TITLE = 0xFF5B3A1A;
    private static final int COL_SUBTITLE = 0xFF8B7355;
    private static final int COL_TEXT = 0xFF5B3A1A;
    private static final int COL_GRAY = 0xFF9E9282;
    private static final int COL_RED = 0xFFC62828;
    private static final int COL_GOLD = 0xFFDAA520;
    private static final int COL_OK = 0xFF4CAF50;
    private static final int COL_OVERLAY = 0x88000000;
    private static final int COL_BAR_BG = 0xFF3A3228;
    private static final int COL_BAR_FILL = 0xFF6B8E23;
    private static final int COL_BAR_WANT = 0xFFB5651D;

    private static final int SDV_W = 660;

    private float guiScale = 1.0f;
    private long openedAtMs = -1;
    private final float[] btnScale = {1.0f, 1.0f, 1.0f};
    private float closeScale = 1.0f;
    private float entryProgress;
    private float populationBarProgress;
    private int hoveredButton = -1;
    private long lastHoverSoundMs;

    private enum ConfirmType { NONE, DEMOLISH, CLEAR }
    private ConfirmType confirmType = ConfirmType.NONE;
    private long confirmOpenMs;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int closeX;
    private int closeY;
    private int closeW;
    private int closeH;
    private int pad;
    private int lineH;
    private int secGap;
    private int btnH;
    private int clearBtnY;
    private int btnY;
    private int contentViewportTop;
    private int contentViewportBottom;
    private int contentHeight;
    private int contentScroll;
    private int contentLayoutKey = Integer.MIN_VALUE;

    public FishPondManagerScreen(FishPondManagerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 1;
        this.imageHeight = 1;
        this.inventoryLabelY = Integer.MAX_VALUE;
        this.titleLabelY = Integer.MAX_VALUE;
    }

    private int ui(int sdvPx) {
        return Math.round(sdvPx / guiScale);
    }

    private float s4() {
        return 4.0f / guiScale;
    }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        guiScale = (float) mc.getWindow().getGuiScale();
        float s4 = s4();
        int borderCorner = Math.max(1, (int) (6.0f * s4));
        int fh = this.font.lineHeight;

        lineH = fh + 5;
        pad = borderCorner + 6;
        secGap = lineH;
        btnH = fh + 14;
        panelW = Math.min(ui(SDV_W), this.width - 8);
        panelH = Math.min(pad * 2 + 11 * lineH + 2 * secGap + btnH * 2 + 8, this.height - 8);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        clearBtnY = panelY + panelH - pad - btnH * 2 - 8;
        btnY = panelY + panelH - pad - btnH;

        closeW = (int) (CLOSE_W * s4) + 2;
        closeH = (int) (CLOSE_SH * s4) + 2;
        closeX = panelX + panelW - borderCorner - closeW;
        closeY = panelY + borderCorner;

        this.leftPos = panelX;
        this.topPos = panelY;
        this.imageWidth = panelW;
        this.imageHeight = panelH;

        if (openedAtMs < 0) {
            openedAtMs = System.currentTimeMillis();
            playSound(ModSounds.DOOR_CREAK.get(), 0.4f, 1.0f);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        ItemStack fishPreview = menu.getFishPreviewStack();
        int layoutKey = (menu.isFormed() ? 1 : 0)
            | (fishPreview.isEmpty() ? 2 : 0)
            | (menu.hasGoldenAnimalCracker() ? 4 : 0)
            | (menu.hasUnresolvedRequest() ? 8 : 0);
        if (layoutKey != contentLayoutKey) {
            contentLayoutKey = layoutKey;
            contentHeight = 0;
            contentScroll = 0;
        }

        entryProgress += (1.0f - entryProgress) * 0.12f;
        if (entryProgress > 0.99f) {
            entryProgress = 1.0f;
        }

        float targetBar = menu.isFormed() && menu.getMaxPopulation() > 0
            ? ((float) menu.getCurrentPopulation() / menu.getMaxPopulation()) * entryProgress
            : 0.0f;
        populationBarProgress += (targetBar - populationBarProgress) * 0.12f;

        for (int i = 0; i < btnScale.length; i++) {
            float target = hoveredButton == i ? 1.08f : 1.0f;
            btnScale[i] += (target - btnScale[i]) * 0.18f;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        updateHover(mouseX, mouseY);

        float s4 = s4();
        CommonGuiTextures.drawTextureBox(g, panelX, panelY, panelW, panelH, s4, true);

        int cx = panelX + pad;
        int cw = panelW - pad * 2;
        int contentWidth = Math.min(cw, 300);
        int contentX = panelX + (panelW - contentWidth) / 2;
        int y = panelY + pad;

        Component titleText = Component.translatable("container.stardew_craft.fish_pond_manager");
        y = GuiText.drawWrappedCentered(
            g, this.font, titleText, panelX + panelW / 2, y,
            panelW - pad * 2, COL_TITLE, true, 0);

        Component formedText;
        int formedColor;
        if (menu.isOwnerMismatch()) {
            formedText = Component.translatable("gui.stardew_craft.fish_pond_manager.owner_mismatch");
            formedColor = COL_RED;
        } else if (menu.isFormed()) {
            formedText = Component.translatable("gui.stardew_craft.fish_pond_manager.formed");
            formedColor = COL_OK;
        } else {
            formedText = Component.translatable("gui.stardew_craft.fish_pond_manager.unformed");
            formedColor = COL_GRAY;
        }
        y = GuiText.drawWrappedCentered(
            g, this.font, formedText, panelX + panelW / 2, y,
            panelW - pad * 2, formedColor, false, 0);

        StardewGuiUtil.drawHorizontalPartitionSmall(g, contentX, y + secGap / 2 - 2, contentWidth, s4);
        y += secGap;

        int actionTop = menu.isFormed() ? clearBtnY : btnY;
        int div2Y = actionTop - secGap;
        contentViewportTop = y;
        contentViewportBottom = Math.max(y, div2Y);
        int viewportHeight = Math.max(
            0,
            contentViewportBottom - contentViewportTop);
        clampContentScroll(viewportHeight);
        if (viewportHeight > 0) {
            g.enableScissor(
                panelX,
                contentViewportTop,
                panelX + panelW,
                contentViewportBottom);
            int contentTop = contentViewportTop - contentScroll;
            int contentBottom = menu.isFormed()
                ? renderFormedContent(
                    g, contentX, contentTop, contentWidth - 6)
                : renderUnformedContent(
                    g, contentX, contentTop, contentWidth - 6);
            contentHeight = Math.max(
                viewportHeight,
                contentBottom - contentTop);
            g.disableScissor();
            clampContentScroll(viewportHeight);
            renderContentScrollbar(
                g,
                contentX + contentWidth - 3,
                contentViewportTop,
                viewportHeight);
        } else {
            contentHeight = 0;
            contentScroll = 0;
        }

        StardewGuiUtil.drawHorizontalPartitionSmall(g, cx, div2Y + secGap / 2 - 2, cw, s4);

        int gap = 8;
        int btnW = (cw - gap) / 2;
        if (menu.isFormed()) {
            drawButton(g, 2, cx, clearBtnY, cw, btnH,
                Component.translatable("gui.stardew_craft.fish_pond_manager.clear"),
                menu.canManagePond(), mouseX, mouseY);
            drawButton(g, 0, cx, btnY, btnW, btnH,
                Component.translatable("gui.stardew_craft.fish_pond_manager.refresh"),
                !menu.isOwnerMismatch(), mouseX, mouseY);
            drawButton(g, 1, cx + btnW + gap, btnY, btnW, btnH,
                Component.translatable("gui.stardew_craft.fish_pond_manager.demolish"),
                menu.isFormed() && !menu.isOwnerMismatch(), mouseX, mouseY);
        } else {
            drawButton(g, 0, cx, btnY, cw, btnH,
                Component.translatable("gui.stardew_craft.fish_pond_manager.build"),
                menu.canBuild(), mouseX, mouseY);
        }

        boolean closeHovered = inside(mouseX, mouseY, closeX, closeY, closeW, closeH);
        closeScale += ((closeHovered ? 1.15f : 1.0f) - closeScale) * 0.15f;
        float cs = s4 * closeScale;
        int cdx = closeX + closeW / 2 - (int) (CLOSE_W * cs / 2);
        int cdy = closeY + closeH / 2 - (int) (CLOSE_SH * cs / 2);
        CommonGuiTextures.drawCloseButton(g, cdx, cdy, cs);

        if (confirmType != ConfirmType.NONE) {
            renderConfirmDialog(g, mouseX, mouseY);
        }

        this.renderTooltip(g, mouseX, mouseY);
    }

    private int renderUnformedContent(GuiGraphics g, int x, int y, int width) {
        y = GuiText.drawWrapped(
            g,
            this.font,
            Component.translatable("gui.stardew_craft.fish_pond_manager.requirements"),
            x,
            y,
            width,
            COL_GOLD,
            false,
            0);

        y = renderRequirementRow(
            g,
            x,
            y,
            width,
            new ItemStack(Items.WATER_BUCKET),
            Component.translatable(
                "gui.stardew_craft.fish_pond_manager.need.water",
                menu.getCurrentWaterWidth(),
                menu.getCurrentWaterLength(),
                menu.getRequiredWaterWidth(),
                menu.getRequiredWaterLength()),
            menu.getWaterCellCount(),
            menu.getRequiredWaterCells());
        y = renderRequirementRow(
            g,
            x,
            y,
            width,
            new ItemStack(ModItems.FISH_NET.get()),
            Component.translatable("gui.stardew_craft.fish_pond_manager.need.net"),
            menu.getNetCount(),
            menu.getRequiredNetCount());
        y = renderRequirementRow(
            g,
            x,
            y,
            width,
            new ItemStack(ModItems.FISH_POND_BUCKET.get()),
            Component.translatable("gui.stardew_craft.fish_pond_manager.need.bucket"),
            menu.getCurrentBucketCount(),
            menu.getRequiredBucketCount());

        y += 2;
        Component footer = Component.translatable(
            menu.canBuild()
                ? "gui.stardew_craft.fish_pond_manager.ready"
                : "gui.stardew_craft.fish_pond_manager.not_ready");
        return GuiText.drawWrapped(
            g,
            this.font,
            footer,
            x,
            y,
            width,
            menu.canBuild() ? COL_GOLD : COL_RED,
            false,
            0);
    }

    private int renderFormedContent(GuiGraphics g, int x, int y, int width) {
        ItemStack fishPreview = menu.getFishPreviewStack();
        if (fishPreview.isEmpty()) {
            CommonGuiTextures.drawItem(g, new ItemStack(Items.COD), x, y - 3, 1.0f);
            y = drawWrappedLines(g, this.font.split(menu.getStatusText(), width - 24), x + 20, y, COL_SUBTITLE);
            return y;
        }

        CommonGuiTextures.drawItem(g, fishPreview, x, y - 3, 1.0f);
        y = drawWrappedLines(
            g,
            this.font.split(fishPreview.getHoverName(), width - 24),
            x + 20,
            y,
            COL_TEXT) + 2;

        Component populationText = Component.translatable(
            "gui.stardew_craft.fish_pond_manager.population",
            menu.getCurrentPopulation(),
            menu.getMaxPopulation());
        y = GuiText.drawWrapped(
            g, this.font, populationText, x, y, width,
            COL_TEXT, false, 0);

        y = drawPopulationIcons(g, fishPreview, x, y, width, menu.getCurrentPopulation(), menu.getMaxPopulation());
        y += 2;

        y = drawWrappedLines(g, this.font.split(menu.getStatusText(), width), x, y, COL_SUBTITLE);

        if (menu.hasGoldenAnimalCracker()) {
            y += 2;
            CommonGuiTextures.drawItem(g, new ItemStack(ModItems.GOLDEN_ANIMAL_CRACKER.get()), x, y - 3, 1.0f);
            y = drawWrappedLines(
                g,
                this.font.split(
                    Component.translatable(
                        "gui.stardew_craft.fish_pond_manager.golden_cracker_yes"),
                    width - 24),
                x + 20,
                y,
                COL_GOLD);
        }

        if (menu.hasUnresolvedRequest()) {
            y += 2;
            StardewGuiUtil.drawHorizontalPartitionSmall(g, x, y + secGap / 2 - 2, width, s4());
            y += secGap;
            y = GuiText.drawWrapped(
                g,
                this.font,
                Component.translatable(
                    "gui.stardew_craft.fish_pond_manager.request_bring"),
                x,
                y,
                width,
                COL_GOLD,
                false,
                0);
            ItemStack neededPreview = menu.getNeededItemPreviewStack();
            CommonGuiTextures.drawItem(g, neededPreview.isEmpty() ? new ItemStack(Items.PAPER) : neededPreview, x, y - 3, 1.0f);
            y = drawWrappedLines(g, this.font.split(
                Component.translatable("gui.stardew_craft.fish_pond_manager.request_needed", menu.getNeededItemCount()),
                width - 24), x + 20, y, COL_GOLD);
        }

        return y;
    }

    private int renderRequirementRow(GuiGraphics g, int x, int y, int width, ItemStack icon, Component label, int current, int required) {
        boolean met = current >= required;
        String countText = current + "/" + required;
        int countWidth = this.font.width(countText);
        int barWidth = Math.min(70, width / 4);
        int labelX = x + this.font.lineHeight + 6;
        int labelWidth = Math.max(1, width - (labelX - x));

        float iconScale = (this.font.lineHeight + 2) / 16.0f;
        CommonGuiTextures.drawItem(g, icon, x, y - 1, iconScale);

        int labelY = y;
        for (FormattedCharSequence line : this.font.split(label, labelWidth)) {
            g.drawString(this.font, line, labelX, labelY,
                COL_TEXT, false);
            labelY += lineH;
        }
        int progressY = Math.max(y + lineH, labelY);
        g.drawString(
            this.font,
            countText,
            x + width - countWidth,
            progressY,
            met ? COL_OK : COL_RED,
            false);
        int availableBarWidth = Math.max(
            12,
            width - (labelX - x) - countWidth - 8);
        drawRequirementBar(
            g,
            labelX,
            progressY + 2,
            Math.min(barWidth, availableBarWidth),
            this.font.lineHeight - 2,
            required > 0
                ? (float) Math.min(current, required) / required
                : 1.0f,
            met);
        return progressY + lineH + 3;
    }

    private void clampContentScroll(int viewportHeight) {
        int maxScroll = Math.max(
            0,
            contentHeight - Math.max(0, viewportHeight));
        contentScroll = Mth.clamp(contentScroll, 0, maxScroll);
    }

    private void renderContentScrollbar(
        GuiGraphics g,
        int x,
        int y,
        int viewportHeight
    ) {
        if (contentHeight <= viewportHeight || viewportHeight <= 0) {
            return;
        }
        g.fill(x, y, x + 2, y + viewportHeight, 0x403A3228);
        int thumbHeight = Math.max(
            10,
            viewportHeight * viewportHeight / contentHeight);
        int maxScroll = contentHeight - viewportHeight;
        int travel = viewportHeight - thumbHeight;
        int thumbY = y + (maxScroll <= 0
            ? 0
            : contentScroll * travel / maxScroll);
        g.fill(x, thumbY, x + 2, thumbY + thumbHeight, COL_GOLD);
    }

    private void drawRequirementBar(GuiGraphics g, int x, int y, int width, int height, float progress, boolean met) {
        g.fill(x, y, x + width, y + height, COL_BAR_BG);
        int fillWidth = (int) ((width - 2) * Mth.clamp(progress, 0.0f, 1.0f));
        if (fillWidth > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillWidth, y + height - 1, met ? COL_BAR_FILL : COL_BAR_WANT);
        }
    }

    private int drawPopulationIcons(GuiGraphics g, ItemStack fishPreview, int x, int y, int width, int current, int max) {
        int slotCount = Math.max(1, max);
        int rows = (slotCount + 4) / 5;
        int iconStep = 18;
        float iconScale = 0.75f;

        for (int row = 0; row < rows; row++) {
            int itemsThisRow = Math.min(5, slotCount - row * 5);
            int rowWidth = (itemsThisRow - 1) * iconStep + 12;
            int rowStartX = x + (width - rowWidth) / 2;
            for (int col = 0; col < itemsThisRow; col++) {
                int index = row * 5 + col;
                int drawX = rowStartX + col * iconStep;
                int drawY = y + row * iconStep;

                if (index >= current) {
                    CommonGuiTextures.drawItemTint(g, fishPreview, drawX, drawY, iconScale, 0.0F, 0.0F, 0.0F, 0.45F);
                } else {
                    CommonGuiTextures.drawItem(g, fishPreview, drawX, drawY, iconScale);
                }
            }
        }

        return y + rows * iconStep;
    }

    private int drawWrappedLines(GuiGraphics g, java.util.List<FormattedCharSequence> lines, int x, int y, int color) {
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x, y, color);
            y += lineH;
        }
        return y;
    }

    private void drawButton(GuiGraphics g, int idx, int x, int y, int w, int h, Component label, boolean active, int mx, int my) {
        boolean hovered = hoveredButton == idx && active;
        float scale = btnScale[idx];

        g.pose().pushPose();
        float cxf = x + w / 2f;
        float cyf = y + h / 2f;
        g.pose().translate(cxf, cyf, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.pose().translate(-cxf, -cyf, 0);

        float s4 = s4();
        CommonGuiTextures.drawTextureBox(g, x, y, w, h, s4, false);
        int inset = (int) (4 * s4);
        if (!active) {
            g.fill(x + inset, y + inset, x + w - inset, y + h - inset, 0x50888888);
        } else if (hovered) {
            g.fill(x + inset, y + inset, x + w - inset, y + h - inset, 0x30FFD700);
        }
        int textColor = !active ? 0xFF909090 : (hovered ? COL_TITLE : COL_TEXT);
        GuiText.drawCenteredFitted(g, this.font, label, x + w / 2,
            y + h / 2, w - 8, textColor, hovered);
        g.pose().popPose();
    }

    private void renderConfirmDialog(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, this.height, COL_OVERLAY);

        int dw = Math.min(panelW - pad, this.width - 16);
        int contentWidth = Math.max(1, dw - pad * 2);
        Component title = confirmDialogTitle();
        Component line1 = confirmDialogLine1();
        Component line2 = confirmDialogLine2();
        int textLines = GuiText.wrappedLineCount(
            this.font, title, contentWidth, 0)
            + GuiText.wrappedLineCount(
                this.font, line1, contentWidth, 0)
            + GuiText.wrappedLineCount(
                this.font, line2, contentWidth, 0);
        int dh = pad * 2 + textLines * lineH + secGap + btnH;
        int dx = (this.width - dw) / 2;
        int dy = (this.height - dh) / 2;

        long elapsed = System.currentTimeMillis() - confirmOpenMs;
        float scale = elapsed < 200 ? 0.85f + 0.15f * easeOutCubic(elapsed / 200f) : 1.0f;

        g.pose().pushPose();
        float cxf = dx + dw / 2f;
        float cyf = dy + dh / 2f;
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
        ty = GuiText.drawWrapped(
            g, this.font, line1, tx, ty, contentWidth,
            COL_TEXT, false, 0);
        GuiText.drawWrapped(
            g, this.font, line2, tx, ty, contentWidth,
            COL_RED, false, 0);

        int cbW = 70;
        int cbH = this.font.lineHeight + 12;
        int cbY = dy + dh - pad - cbH;
        int confirmBtnX = dx + dw / 2 - cbW - 4;
        int cancelBtnX = dx + dw / 2 + 4;

        drawDialogButton(g, confirmBtnX, cbY, cbW, cbH,
            Component.translatable("gui.stardew_craft.fish_pond_manager.dialog.confirm"),
            true, inside(mx, my, confirmBtnX, cbY, cbW, cbH));
        drawDialogButton(g, cancelBtnX, cbY, cbW, cbH,
            Component.translatable("gui.stardew_craft.fish_pond_manager.dialog.back"),
            true, inside(mx, my, cancelBtnX, cbY, cbW, cbH));

        g.pose().popPose();
    }

    private void drawDialogButton(GuiGraphics g, int x, int y, int w, int h,
                                  Component label, boolean active, boolean hovered) {
        float s4 = s4();
        CommonGuiTextures.drawTextureBox(g, x, y, w, h, s4, false);

        int inset = (int) (4 * s4);
        if (!active) {
            g.fill(x + inset, y + inset, x + w - inset, y + h - inset, 0x50888888);
        } else if (hovered) {
            g.fill(x + inset, y + inset, x + w - inset, y + h - inset, 0x30FFD700);
        }

        int textColor = !active ? 0xFF909090 : (hovered ? COL_TITLE : COL_TEXT);
        GuiText.drawCenteredFitted(g, this.font, label, x + w / 2,
            y + h / 2, w - 8, textColor, hovered);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

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
                contentViewportTop,
                panelW,
                Math.max(0, contentViewportBottom - contentViewportTop))
            && contentHeight
                > contentViewportBottom - contentViewportTop) {
            contentScroll -= (int) Math.round(
                scrollY * lineH * 2.0D);
            clampContentScroll(
                contentViewportBottom - contentViewportTop);
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
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (inside(mx, my, closeX, closeY, closeW, closeH)) {
            playSound(ModSounds.CANCEL.get(), 0.3f, 1.0f);
            this.onClose();
            return true;
        }
        if (confirmType != ConfirmType.NONE) {
            return handleConfirmClick(mx, my);
        }

        int cx = panelX + pad;
        int cw = panelW - pad * 2;
        int gap = 8;
        int btnW = (cw - gap) / 2;
        if (menu.isFormed() && menu.canManagePond() && inside(mx, my, cx, clearBtnY, cw, btnH)) {
            enterConfirm(ConfirmType.CLEAR);
            return true;
        }
        if (!menu.isFormed() && !menu.isOwnerMismatch() && menu.canBuild() && inside(mx, my, cx, btnY, cw, btnH)) {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FishPondManagerMenu.ACTION_BUILD_OR_REFRESH);
                playSound(ModSounds.HAMMER.get(), 0.6f, 1.0f);
            }
            return true;
        }
        if (menu.isFormed() && !menu.isOwnerMismatch() && inside(mx, my, cx, btnY, btnW, btnH)) {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FishPondManagerMenu.ACTION_BUILD_OR_REFRESH);
                playSound(ModSounds.HAMMER.get(), 0.6f, 1.0f);
            }
            return true;
        }
        if (menu.isFormed() && !menu.isOwnerMismatch() && inside(mx, my, cx + btnW + gap, btnY, btnW, btnH)) {
            enterConfirm(ConfirmType.DEMOLISH);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleConfirmClick(int mx, int my) {
        int dw = Math.min(panelW - pad, this.width - 16);
        int contentWidth = Math.max(1, dw - pad * 2);
        int textLines = GuiText.wrappedLineCount(
            this.font, confirmDialogTitle(), contentWidth, 0)
            + GuiText.wrappedLineCount(
                this.font, confirmDialogLine1(), contentWidth, 0)
            + GuiText.wrappedLineCount(
                this.font, confirmDialogLine2(), contentWidth, 0);
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

    private Component confirmDialogTitle() {
        return Component.translatable(confirmType == ConfirmType.CLEAR
            ? "gui.stardew_craft.fish_pond_manager.dialog.clear.title"
            : "gui.stardew_craft.fish_pond_manager.dialog.demolish.title");
    }

    private Component confirmDialogLine1() {
        return Component.translatable(confirmType == ConfirmType.CLEAR
            ? "gui.stardew_craft.fish_pond_manager.dialog.clear.line1"
            : "gui.stardew_craft.fish_pond_manager.dialog.demolish.line1");
    }

    private Component confirmDialogLine2() {
        return Component.translatable(confirmType == ConfirmType.CLEAR
            ? "gui.stardew_craft.fish_pond_manager.dialog.clear.line2"
            : "gui.stardew_craft.fish_pond_manager.dialog.demolish.line2");
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

    private void enterConfirm(ConfirmType type) {
        confirmType = type;
        confirmOpenMs = System.currentTimeMillis();
        playSound(ModSounds.BIG_SELECT.get(), 0.5f, 1.0f);
    }

    private void exitConfirm() {
        if (confirmType == ConfirmType.NONE) {
            return;
        }
        confirmType = ConfirmType.NONE;
        playSound(ModSounds.BIG_DESELECT.get(), 0.4f, 1.0f);
    }

    private void executeConfirm() {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            if (confirmType == ConfirmType.CLEAR) {
                playSound(ModSounds.CANCEL.get(), 0.3f, 1.0f);
                this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FishPondManagerMenu.ACTION_CLEAR_POND);
            } else if (confirmType == ConfirmType.DEMOLISH) {
                playSound(ModSounds.EXPLOSION.get(), 0.3f, 1.0f);
                this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FishPondManagerMenu.ACTION_DEMOLISH);
            }
        }
        exitConfirm();
    }

    private void updateHover(int mx, int my) {
        if (confirmType != ConfirmType.NONE) {
            hoveredButton = -1;
            return;
        }

        int cx = panelX + pad;
        int cw = panelW - pad * 2;
        int gap = 8;
        int btnW = (cw - gap) / 2;

        int oldHover = hoveredButton;
        hoveredButton = -1;
        if (inside(mx, my, cx, clearBtnY, cw, btnH)) {
            hoveredButton = 2;
        } else if (inside(mx, my, cx, btnY, btnW, btnH)) {
            hoveredButton = 0;
        } else if (inside(mx, my, cx + btnW + gap, btnY, btnW, btnH)) {
            hoveredButton = 1;
        }

        if (hoveredButton >= 0 && hoveredButton != oldHover) {
            long now = System.currentTimeMillis();
            if (now - lastHoverSoundMs > 300) {
                playSound(ModSounds.SMALL_SELECT.get(), 0.2f, 1.0f);
                lastHoverSoundMs = now;
            }
        }
    }

    private static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        float f = 1.0f - t;
        return 1.0f - f * f * f;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void playSound(SoundEvent sound, float volume, float pitch) {
        var player = this.minecraft != null ? this.minecraft.player : null;
        if (player != null) {
            player.playSound(sound, volume, pitch);
        }
    }
}
