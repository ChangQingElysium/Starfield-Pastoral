package com.stardew.craft.client.gui.auction;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.network.payload.AuctionJoinSubmitPayload;
import com.stardew.craft.network.payload.OpenAuctionJoinListPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class AuctionConsignScreen extends Screen {
    // Fixed design canvas, scaled uniformly to fit the screen so the layout is identical at every GUI scale.
    private static final int DESIGN_W = 720;
    private static final int DESIGN_H = 500;
    private final OpenAuctionJoinListPayload.AuctionSummary target;
    private float fitScale = 1.0f;
    private int fitOriginX, fitOriginY;
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int itemX, itemY, itemW, itemH;
    private int detailX, detailY, detailW, detailH;
    private int inventoryX, inventoryY, slotSize, slotGap;
    private int priceBoxX, priceBoxY, priceBoxW, priceBoxH;
    private int submitX, submitY, submitW, submitH;
    private int nextX, nextY, nextW, nextH;
    private int tabY, tabH;
    private int itemTabX, ledgerTabX, tabW;
    private int selectedSlot = -1;
    private int compactPage;
    private boolean stacked;
    private boolean opened;
    private EditBox priceField;

    public AuctionConsignScreen(OpenAuctionJoinListPayload.AuctionSummary target) {
        super(Component.translatable("stardewcraft.auction.consign.title"));
        this.target = target;
    }

    @Override
    protected void init() {
        fitScale = Math.min(1.5f, 0.94f * Math.min(width / (float) DESIGN_W, height / (float) DESIGN_H));
        fitOriginX = Math.round((width - DESIGN_W * fitScale) / 2f);
        fitOriginY = Math.round((height - DESIGN_H * fitScale) / 2f);
        panelW = DESIGN_W;
        panelH = DESIGN_H;
        panelX = 0;
        panelY = 0;

        int pad = Math.max(14, Math.min(24, panelW / 25));
        contentX = panelX + pad;
        contentY = panelY + pad;
        contentW = panelW - pad * 2;
        contentH = panelH - pad * 2;

        int bodyY = contentY + 52;
        int bodyH = contentH - 52;
        int gap = Math.max(12, contentW / 42);
        stacked = contentW < 560;
        if (stacked) {
            tabY = bodyY;
            tabH = 24;
            tabW = Math.max(92, (contentW - 6) / 2);
            itemTabX = contentX;
            ledgerTabX = itemTabX + tabW + 6;
            int pageY = bodyY + tabH + 8;
            int pageH = Math.max(1, contentY + contentH - pageY - 42);
            itemX = contentX;
            itemY = pageY;
            itemW = contentW;
            itemH = pageH;
            detailX = contentX;
            detailY = pageY;
            detailW = contentW;
            detailH = pageH;
        } else {
            itemX = contentX;
            itemY = bodyY;
            itemW = Math.min(304, Math.max(260, (contentW - gap) * 45 / 100));
            itemH = bodyH;
            detailX = itemX + itemW + gap;
            detailY = bodyY;
            detailW = contentW - itemW - gap;
            detailH = bodyH;
        }

        layoutInventory();
        layoutPriceField();

        if (!opened) {
            opened = true;
            playOpen();
        }
    }

    private void layoutInventory() {
        slotSize = Math.max(20, Math.min(28, (itemW - 28) / 9));
        slotGap = Math.max(2, slotSize / 9);
        int gridW = 9 * slotSize + 8 * slotGap;
        inventoryX = itemX + (itemW - gridW) / 2;
        inventoryY = itemY + itemH - (4 * slotSize + 3 * slotGap) - 12;
    }

    private void layoutPriceField() {
        int fieldH = Math.max(30, font.lineHeight + 18);
        priceBoxX = detailX + 14;
        priceBoxW = detailW - 28;
        priceBoxH = fieldH;
        submitW = Math.min(188, Math.max(144, detailW / 2));
        submitH = 31;
        submitX = detailX + detailW - submitW - 14;
        submitY = detailY + detailH - submitH - 14;
        nextW = submitW;
        nextH = submitH;
        nextX = contentX + contentW - nextW - 14;
        nextY = itemY + itemH + 9;
        priceBoxY = submitY - fieldH - 28;

        priceField = new EditBox(com.stardew.craft.client.font.StardewFonts.small(), priceBoxX + 31, priceBoxY + (fieldH - font.lineHeight) / 2,
            priceBoxW - 43, font.lineHeight, Component.translatable("stardewcraft.auction.consign.price_hint"));
        priceField.setBordered(false);
        priceField.setTextShadow(false);
        priceField.setTextColor(0xFF3F2411);
        priceField.setTextColorUneditable(0xFF3F2411);
        priceField.setHint(Component.translatable("stardewcraft.auction.consign.price_hint"));
        priceField.setMaxLength(9);
        priceField.setValue("100");
        addWidget(priceField);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int rawMouseX, int rawMouseY, float partialTick) {
        renderTransparentBackground(g);
        int mouseX = lmx(rawMouseX);
        int mouseY = lmy(rawMouseY);
        g.pose().pushPose();
        g.pose().translate(fitOriginX, fitOriginY, 0);
        g.pose().scale(fitScale, fitScale, 1f);
        StardewGuiUtil.drawDialogueBoxFrame(g, panelX, panelY, panelW, panelH);
        AuctionUi.ledgerPanel(g, contentX, contentY, contentW, contentH);
        AuctionUi.title(g, font, Component.translatable("stardewcraft.auction.consign.title"),
            Component.translatable("stardewcraft.auction.consign.subtitle", target.name()),
            contentX + 8, contentY + 7, contentW - 16);
        if (stacked) {
            drawTabs(g, mouseX, mouseY);
            if (compactPage == 0) {
                drawItemPanel(g, mouseX, mouseY);
                drawStackedNext(g, mouseX, mouseY);
            } else {
                drawDetailPanel(g, mouseX, mouseY);
            }
        } else {
            drawItemPanel(g, mouseX, mouseY);
            drawDetailPanel(g, mouseX, mouseY);
        }
        if (!stacked || compactPage == 1) {
            priceField.render(g, mouseX, mouseY, partialTick);
        }
        g.pose().popPose();
    }

    private int lmx(double mouseX) { return (int) Math.round((mouseX - fitOriginX) / fitScale); }
    private int lmy(double mouseY) { return (int) Math.round((mouseY - fitOriginY) / fitScale); }
    private double ldx(double mouseX) { return (mouseX - fitOriginX) / fitScale; }
    private double ldy(double mouseY) { return (mouseY - fitOriginY) / fitScale; }

    private void drawStackedNext(GuiGraphics g, int mouseX, int mouseY) {
        AuctionUi.divider(g, contentX + 8, itemY + itemH + 2, contentW - 16);
        boolean enabled = selectedSlot >= 0;
        boolean hover = enabled && AuctionUi.inside(mouseX, mouseY, nextX, nextY, nextW, nextH);
        AuctionUi.drawClamped(g, font,
            Component.translatable(enabled ? "stardewcraft.auction.consign.next_ledger" : "stardewcraft.auction.consign.need_item"),
            contentX + 10, nextY + 9, Math.max(40, nextX - contentX - 18), enabled ? AuctionUi.MUTED : AuctionUi.ERROR);
        AuctionUi.actionButton(g, font, Component.translatable("stardewcraft.auction.ui.next"),
            nextX, nextY, nextW, nextH, enabled, hover);
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        drawTab(g, mouseX, mouseY, itemTabX, tabY, tabW, tabH, 0,
            Component.translatable("stardewcraft.auction.create.step.item"));
        drawTab(g, mouseX, mouseY, ledgerTabX, tabY, tabW, tabH, 1,
            Component.translatable("stardewcraft.auction.consign.step.ledger"));
    }

    private void drawTab(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, int page, Component label) {
        boolean selected = compactPage == page;
        boolean hover = AuctionUi.inside(mouseX, mouseY, x, y, w, h);
        AuctionUi.plainButton(g, font, label, x, y, w, h, true, selected || hover);
        if (selected) {
            g.fill(x + 9, y + h - 7, x + w - 9, y + h - 5, AuctionUi.GOLD_BRIGHT);
        }
    }

    private void drawItemPanel(GuiGraphics g, int mouseX, int mouseY) {
        AuctionUi.card(g, itemX, itemY, itemW, itemH, selectedSlot >= 0, false);
        AuctionUi.sectionLabel(g, font, Component.translatable("stardewcraft.auction.consign.item"), itemX + 13, itemY + 11, itemW - 26);
        int receiptY = itemY + 32;
        int receiptH = Math.max(58, Math.min(84, inventoryY - receiptY - 26));
        drawReceipt(g, itemX + 12, receiptY, itemW - 24, receiptH);
        AuctionUi.sectionLabel(g, font, Component.translatable("stardewcraft.auction.create.inventory"), itemX + 13, inventoryY - 18, itemW - 26);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row == 3 ? col : 9 + row * 9 + col;
                int x = inventoryX + col * (slotSize + slotGap);
                int y = inventoryY + row * (slotSize + slotGap);
                ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(slot);
                AuctionUi.slot(g, font, stack, x, y, slotSize, selectedSlot == slot,
                    AuctionUi.inside(mouseX, mouseY, x, y, slotSize, slotSize));
            }
        }
    }

    private void drawReceipt(GuiGraphics g, int x, int y, int w, int h) {
        CommonGuiTextures.drawEntryBox(g, x, y, w, h, 1.0f, false);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, AuctionUi.CARD_STRONG);
        g.fill(x + 8, y + 8, x + w - 8, y + 9, 0x55FFFFFF);
        ItemStack selected = selectedStack();
        int slot = Math.min(46, Math.max(34, h - 18));
        int slotX = x + 12;
        int slotY = y + (h - slot) / 2;
        AuctionUi.slot(g, font, selected, slotX, slotY, slot, true, false);
        if (selected.isEmpty()) {
            AuctionUi.drawClamped(g, font, Component.translatable("stardewcraft.auction.consign.item_empty"),
                slotX + slot + 12, y + 15, w - slot - 28, AuctionUi.MUTED);
            AuctionUi.drawClamped(g, font, Component.translatable("stardewcraft.auction.consign.item_empty_hint"),
                slotX + slot + 12, y + 32, w - slot - 28, AuctionUi.MUTED);
        } else {
            AuctionUi.drawClamped(g, font, selected.getHoverName(), slotX + slot + 12, y + 15,
                w - slot - 28, AuctionUi.INK);
            AuctionUi.drawClamped(g, font, Component.translatable("stardewcraft.auction.consign.item_ready"),
                slotX + slot + 12, y + 32, w - slot - 28, AuctionUi.GOLD);
        }
    }

    private void drawDetailPanel(GuiGraphics g, int mouseX, int mouseY) {
        AuctionUi.card(g, detailX, detailY, detailW, detailH, false, false);
        AuctionUi.sectionLabel(g, font, Component.translatable("stardewcraft.auction.consign.ledger"), detailX + 14, detailY + 13, detailW - 28);
        AuctionUi.drawClamped(g, font, target.name(), detailX + 16, detailY + 40, detailW - 32, AuctionUi.INK);
        AuctionUi.drawClamped(g, font, Component.translatable("stardewcraft.auction.join.target_meta",
            target.creatorName(), target.lotCount()), detailX + 16, detailY + 59, detailW - 32, AuctionUi.MUTED);
        AuctionUi.drawClamped(g, font, Component.translatable("stardewcraft.auction.consign.note"),
            detailX + 16, detailY + 82, detailW - 32, AuctionUi.BODY);

        g.drawString(font, Component.translatable("stardewcraft.auction.consign.price"), priceBoxX, priceBoxY - 14, AuctionUi.BODY, false);
        AuctionUi.inputBox(g, priceBoxX, priceBoxY, priceBoxW, priceBoxH, priceField.isFocused(),
            AuctionUi.inside(mouseX, mouseY, priceBoxX, priceBoxY, priceBoxW, priceBoxH), parsePrice() <= 0);
        CommonGuiTextures.drawGoldCoin16(g, priceBoxX + 10, priceBoxY + (priceBoxH - 14) / 2, 0.58f);
        g.fill(priceBoxX + 28, priceBoxY + 7, priceBoxX + 29, priceBoxY + priceBoxH - 7, 0x55A66B26);

        boolean enabled = selectedSlot >= 0 && parsePrice() > 0;
        boolean hover = enabled && AuctionUi.inside(mouseX, mouseY, submitX, submitY, submitW, submitH);
        AuctionUi.drawClamped(g, font, submitHint(), detailX + 16, submitY + 9,
            Math.max(40, submitX - detailX - 26), enabled ? AuctionUi.MUTED : AuctionUi.ERROR);
        AuctionUi.actionButton(g, font, Component.translatable("stardewcraft.auction.consign.submit"),
            submitX, submitY, submitW, submitH, enabled, hover);
    }

    private Component submitHint() {
        if (selectedSlot < 0) {
            return Component.translatable("stardewcraft.auction.consign.need_item");
        }
        if (parsePrice() <= 0) {
            return Component.translatable("stardewcraft.auction.create.need_price");
        }
        return Component.translatable("stardewcraft.auction.consign.ready_hint");
    }

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mouseX = ldx(rawX);
        double mouseY = ldy(rawY);
        if (stacked && clickTab(mouseX, mouseY)) {
            return true;
        }
        if (!stacked || compactPage == 0) {
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    int slot = row == 3 ? col : 9 + row * 9 + col;
                    int x = inventoryX + col * (slotSize + slotGap);
                    int y = inventoryY + row * (slotSize + slotGap);
                    if (AuctionUi.inside(mouseX, mouseY, x, y, slotSize, slotSize)) {
                        selectedSlot = slot;
                        playSelect();
                        return true;
                    }
                }
            }
        }
        if ((!stacked || compactPage == 1) && AuctionUi.inside(mouseX, mouseY, priceBoxX, priceBoxY, priceBoxW, priceBoxH)) {
            priceField.setFocused(true);
            setFocused(priceField);
            return priceField.mouseClicked(mouseX, mouseY, button) || true;
        }
        if (stacked && compactPage == 0 && AuctionUi.inside(mouseX, mouseY, nextX, nextY, nextW, nextH)) {
            footerAction();
            return true;
        }
        if ((!stacked || compactPage == 1) && AuctionUi.inside(mouseX, mouseY, submitX, submitY, submitW, submitH)) {
            footerAction();
            return true;
        }
        priceField.setFocused(false);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickTab(double mouseX, double mouseY) {
        if (AuctionUi.inside(mouseX, mouseY, itemTabX, tabY, tabW, tabH)) {
            changeCompactPage(0);
            return true;
        }
        if (AuctionUi.inside(mouseX, mouseY, ledgerTabX, tabY, tabW, tabH)) {
            changeCompactPage(1);
            return true;
        }
        return false;
    }

    private void changeCompactPage(int page) {
        compactPage = Math.max(0, Math.min(1, page));
        priceField.setFocused(false);
        playSelect();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (priceField != null && priceField.isFocused() && !Character.isDigit(codePoint)) {
            return false;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            footerAction();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void footerAction() {
        if (stacked && compactPage == 0) {
            if (selectedSlot < 0) {
                playCancel();
            } else {
                changeCompactPage(1);
            }
            return;
        }
        submit();
    }

    private void submit() {
        int price = parsePrice();
        if (selectedSlot < 0 || price <= 0) {
            playCancel();
            return;
        }
        PacketDistributor.sendToServer(new AuctionJoinSubmitPayload(target.id(), selectedSlot, price));
        playSelect();
        onClose();
    }

    private ItemStack selectedStack() {
        return minecraft != null && minecraft.player != null && selectedSlot >= 0
            ? minecraft.player.getInventory().getItem(selectedSlot)
            : ItemStack.EMPTY;
    }

    private int parsePrice() {
        try {
            return Math.max(0, Integer.parseInt(priceField.getValue().trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void playSelect() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(), 0.7f, 1.05f));
        }
    }

    private void playOpen() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.BOOK_READ.get(), 0.82f, 0.70f));
        }
    }

    private void playCancel() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CANCEL.get(), 0.92f, 0.45f));
        }
    }
}
