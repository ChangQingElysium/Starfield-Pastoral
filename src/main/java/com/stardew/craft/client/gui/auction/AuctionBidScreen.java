package com.stardew.craft.client.gui.auction;

import com.stardew.craft.client.auction.AuctionClientState;
import com.stardew.craft.client.gui.StardewRealtimeScreen;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.auction.AuctionService;
import com.stardew.craft.network.payload.AuctionBidSubmitPayload;
import com.stardew.craft.network.payload.OpenAuctionBidPayload;
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
public class AuctionBidScreen extends Screen implements StardewRealtimeScreen {
    // Fixed design canvas, scaled uniformly to fit the screen so the layout is identical at every GUI scale.
    private static final int DESIGN_W = 720;
    private static final int DESIGN_H = 470;
    /** Snapshot the screen was opened with; only seeds the first frames before live sync arrives. */
    private final OpenAuctionBidPayload seed;
    private float fitScale = 1.0f;
    private int fitOriginX, fitOriginY;
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int ribbonCx, ribbonY, metaY, timerX, timerY, timerW;
    private int stageX, stageY, stageW, stageH;
    private int railX, railY, railW, railH;
    private int plateY, plateH, plateGap;
    private int quickX, quickY, quickW;
    private int fieldBoxX, fieldBoxY, fieldBoxW, fieldBoxH;
    private int submitX, submitY, submitW, submitH;
    private boolean compact;
    private boolean opened;
    private boolean wasLive;
    private long lastSubmitMs;
    private EditBox bidField;

    public AuctionBidScreen(OpenAuctionBidPayload payload) {
        super(Component.translatable("stardewcraft.auction.bid.title"));
        this.seed = payload;
    }

    // ── Live data: prefer the realtime board sync, fall back to the open snapshot before the first sync ──

    private boolean live() {
        return AuctionClientState.board().active();
    }

    private int currentPrice() {
        return live() ? AuctionClientState.board().currentPrice() : seed.currentPrice();
    }

    private int nextBid() {
        return live() ? AuctionClientState.board().nextBid() : seed.nextBid();
    }

    private int remainingSeconds() {
        return live() ? AuctionClientState.liveRemainingSeconds() : seed.remainingSeconds();
    }

    private boolean canBid() {
        return live() ? AuctionClientState.board().canBid() : seed.canBid();
    }

    private ItemStack lotStack() {
        return live() ? AuctionClientState.board().stack() : seed.stack();
    }

    private String auctionName() {
        return live() ? AuctionClientState.board().auctionName() : seed.auctionName();
    }

    private String sellerName() {
        return live() ? AuctionClientState.board().sellerName() : seed.sellerName();
    }

    private String highestBidderName() {
        return live() ? AuctionClientState.board().bidderName() : seed.highestBidderName();
    }

    private int lotIndex() {
        return live() ? AuctionClientState.board().lotIndex() : seed.lotIndex();
    }

    private int lotCount() {
        return live() ? AuctionClientState.board().lotCount() : seed.lotCount();
    }

    private int quickStep() {
        return AuctionService.bidStep(currentPrice());
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
        int pad = Math.max(30, Math.min(50, panelW / 15));
        contentX = panelX + pad;
        contentY = panelY + pad;
        contentW = panelW - pad * 2;
        contentH = panelH - pad * 2;

        ribbonCx = contentX + contentW / 2;
        ribbonY = contentY;
        metaY = contentY + 24;
        timerX = contentX + 4;
        timerY = contentY + 40;
        timerW = contentW - 8;

        compact = contentW < 560;
        int gap = Math.max(12, contentW / 42);
        int bodyY = contentY + 54;
        int bodyH = contentH - 54;
        plateGap = 8;

        int innerX;
        int innerW;
        if (compact) {
            // Single column: a slim lot strip over the essential bidding controls.
            stageX = contentX;
            stageY = bodyY;
            stageW = contentW;
            stageH = 66;
            railX = contentX;
            railY = stageY + stageH + 10;
            railW = contentW;
            railH = contentY + contentH - railY;
            innerX = railX + 12;
            innerW = railW - 24;
            plateH = 40;
            plateY = railY + 12;
            quickX = innerX;
            quickW = innerW;
            quickY = plateY + plateH + 22;
            submitW = innerW;
            submitH = 30;
            submitX = innerX;
            submitY = railY + railH - submitH - 10;
            // The custom field is dropped on small screens; the EditBox still backs the bid value off-screen.
            fieldBoxX = -1000;
            fieldBoxY = -1000;
            fieldBoxW = 0;
            fieldBoxH = font.lineHeight + 10;
        } else {
            stageX = contentX;
            stageY = bodyY;
            stageW = Math.min(296, Math.max(224, (contentW - gap) * 46 / 100));
            stageH = bodyH;
            railX = stageX + stageW + gap;
            railY = bodyY;
            railW = contentW - stageW - gap;
            railH = bodyH;
            innerX = railX + 12;
            innerW = railW - 24;
            plateH = 50;
            plateY = railY + 14;
            quickX = innerX;
            quickW = innerW;
            quickY = plateY + plateH + 26;
            fieldBoxX = innerX;
            fieldBoxW = innerW;
            fieldBoxH = Math.max(28, font.lineHeight + 15);
            submitW = innerW;
            submitH = 30;
            submitX = innerX;
            submitY = railY + railH - submitH - 14;
            fieldBoxY = submitY - fieldBoxH - 22;
        }

        bidField = new EditBox(font, fieldBoxX + 31, fieldBoxY + (fieldBoxH - font.lineHeight) / 2,
            Math.max(10, fieldBoxW - 43), font.lineHeight, Component.translatable("stardewcraft.auction.bid.input_hint"));
        bidField.setBordered(false);
        bidField.setTextShadow(false);
        bidField.setTextColor(0xFF3F2411);
        bidField.setTextColorUneditable(0xFF3F2411);
        bidField.setHint(Component.translatable("stardewcraft.auction.bid.input_hint"));
        bidField.setMaxLength(9);
        bidField.setValue(String.valueOf(nextBid()));
        addWidget(bidField);

        if (!opened) {
            opened = true;
            playOpen();
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int rawMouseX, int rawMouseY, float partialTick) {
        // Auto-close once the auction we were following ends (the board stops syncing / goes inactive).
        if (live()) {
            wasLive = true;
        } else if (wasLive) {
            onClose();
            return;
        }
        // While the player isn't editing a custom amount, track the current minimum legal bid so a live
        // outbid is reflected without forcing a re-open.
        if (bidField != null && !bidField.isFocused() && parseBid() != nextBid()) {
            bidField.setValue(String.valueOf(nextBid()));
        }

        renderTransparentBackground(g);
        int mouseX = lmx(rawMouseX);
        int mouseY = lmy(rawMouseY);
        g.pose().pushPose();
        g.pose().translate(fitOriginX, fitOriginY, 0);
        g.pose().scale(fitScale, fitScale, 1f);
        StardewGuiUtil.drawDialogueBoxFrame(g, panelX, panelY, panelW, panelH);
        drawHeader(g);
        if (compact) {
            drawStageCompact(g);
            drawRailCompact(g, mouseX, mouseY);
        } else {
            drawStage(g);
            drawRail(g, mouseX, mouseY);
            bidField.render(g, mouseX, mouseY, partialTick);
        }
        g.pose().popPose();
    }

    private int lmx(double mouseX) { return (int) Math.round((mouseX - fitOriginX) / fitScale); }
    private int lmy(double mouseY) { return (int) Math.round((mouseY - fitOriginY) / fitScale); }
    private double ldx(double mouseX) { return (mouseX - fitOriginX) / fitScale; }
    private double ldy(double mouseY) { return (mouseY - fitOriginY) / fitScale; }

    private void drawStageCompact(GuiGraphics g) {
        AuctionUi.band(g, stageX, stageY, stageW, stageH);
        int ped = stageH - 16;
        int pedX = stageX + 12;
        int pedY = stageY + 8;
        AuctionUi.inset(g, pedX, pedY, ped, ped);
        ItemStack stack = lotStack();
        float itemScale = Math.max(1.4f, (ped * 0.6f) / 16.0f);
        int itemSize = Math.round(16 * itemScale);
        CommonGuiTextures.drawItem(g, stack, pedX + (ped - itemSize) / 2, pedY + (ped - itemSize) / 2, itemScale);
        int textX = pedX + ped + 12;
        int textW = stageX + stageW - textX - 12;
        AuctionUi.drawClamped(g, font, stack.getHoverName(), textX, stageY + 12, textW, AuctionUi.INK);
        AuctionUi.drawClamped(g, font, lewisCall(), textX, stageY + 30, textW, AuctionUi.GOLD);
        String bidder = highestBidderName().isBlank()
            ? Component.translatable("stardewcraft.auction.bid.no_bidder").getString()
            : Component.translatable("stardewcraft.auction.bid.bidder", highestBidderName()).getString();
        AuctionUi.drawClamped(g, font, Component.literal(bidder), textX, stageY + 45, textW, AuctionUi.MUTED);
    }

    private void drawRailCompact(GuiGraphics g, int mouseX, int mouseY) {
        AuctionUi.band(g, railX, railY, railW, railH);
        int innerX = railX + 12;
        int innerW = railW - 24;
        int plateW = (innerW - plateGap) / 2;
        AuctionUi.pricePlate(g, font, Component.translatable("stardewcraft.auction.bid.current", ""),
            currentPrice(), innerX, plateY, plateW, plateH, false);
        AuctionUi.pricePlate(g, font, Component.translatable("stardewcraft.auction.bid.next", ""),
            nextBid(), innerX + plateW + plateGap, plateY, innerW - plateW - plateGap, plateH, true);
        AuctionUi.sectionLabel(g, font, Component.translatable("stardewcraft.auction.bid.quick"), quickX, quickY - 15, quickW);
        drawQuickButtons(g, mouseX, mouseY);
        boolean enabled = canBid() && parseBid() >= nextBid();
        boolean hover = enabled && AuctionUi.inside(mouseX, mouseY, submitX, submitY, submitW, submitH);
        AuctionUi.drawClamped(g, font, bidHint(), railX + 13, submitY - 13, innerW - 2,
            enabled ? AuctionUi.MUTED : AuctionUi.ERROR);
        AuctionUi.actionButton(g, font,
            Component.translatable(canBid() ? "stardewcraft.auction.bid.submit" : "stardewcraft.auction.bid.blocked"),
            submitX, submitY, submitW, submitH, enabled, hover);
    }

    private void drawHeader(GuiGraphics g) {
        AuctionUi.ribbon(g, font, Component.literal(AuctionUi.fit(font, auctionName(), contentW - 60)), ribbonCx, ribbonY);
        AuctionUi.drawCentered(g, font, Component.translatable("stardewcraft.auction.bid.lot_meta",
            lotIndex(), lotCount(), Math.max(0, remainingSeconds())), ribbonCx, metaY, contentW - 12, AuctionUi.MUTED);
        drawTimer(g);
    }

    private void drawTimer(GuiGraphics g) {
        int remaining = remainingSeconds();
        float ratio = Math.max(0.0F, Math.min(1.0F, remaining / (float) AuctionService.LOT_SECONDS));
        int h = 7;
        g.fill(timerX, timerY, timerX + timerW, timerY + h, 0x66A66B26);
        g.fill(timerX, timerY, timerX + timerW, timerY + 1, 0x55FFE9B9);
        int fill = Math.max(3, Math.round(timerW * ratio));
        int color = remaining <= AuctionService.FINAL_EXTENSION_SECONDS
            ? AuctionUi.blend(0xB93A24, AuctionUi.GOLD_BRIGHT, AuctionUi.pulse())
            : AuctionUi.GOLD;
        g.fill(timerX, timerY, timerX + fill, timerY + h, color);
    }

    private void drawStage(GuiGraphics g) {
        AuctionUi.band(g, stageX, stageY, stageW, stageH);
        int innerX = stageX + 14;
        int innerW = stageW - 28;

        // Lot on its pedestal.
        int ped = Math.min(innerW - 24, Math.max(58, stageH / 3));
        int pedX = stageX + (stageW - ped) / 2;
        int pedY = stageY + 16;
        AuctionUi.inset(g, pedX, pedY, ped, ped);
        ItemStack stack = lotStack();
        float itemScale = Math.max(1.6f, (ped * 0.58f) / 16.0f);
        int itemSize = Math.round(16 * itemScale);
        CommonGuiTextures.drawItem(g, stack, pedX + (ped - itemSize) / 2, pedY + (ped - itemSize) / 2, itemScale);

        int textY = pedY + ped + 9;
        AuctionUi.drawCentered(g, font, stack.getHoverName(), stageX + stageW / 2, textY, innerW, AuctionUi.INK);
        AuctionUi.drawCentered(g, font, Component.translatable("stardewcraft.auction.bid.seller", sellerName()),
            stageX + stageW / 2, textY + 16, innerW, AuctionUi.MUTED);
        String bidder = highestBidderName().isBlank()
            ? Component.translatable("stardewcraft.auction.bid.no_bidder").getString()
            : Component.translatable("stardewcraft.auction.bid.bidder", highestBidderName()).getString();
        AuctionUi.drawCentered(g, font, Component.literal(bidder), stageX + stageW / 2, textY + 31, innerW, AuctionUi.BODY);

        // Lewis's call slip at the foot of the stage.
        int callH = 26;
        int callY = stageY + stageH - callH - 12;
        AuctionUi.inset(g, innerX, callY, innerW, callH);
        AuctionUi.drawCentered(g, font, lewisCall(), stageX + stageW / 2, callY + (callH - font.lineHeight) / 2,
            innerW - 14, AuctionUi.GOLD);
    }

    private Component lewisCall() {
        String bidder = highestBidderName();
        if (remainingSeconds() <= AuctionService.THIRD_CALL_SECONDS && !bidder.isBlank()) {
            return Component.translatable("stardewcraft.auction.bid.call_last", currentPrice());
        }
        if (bidder.isBlank()) {
            return Component.translatable("stardewcraft.auction.bid.call_open", currentPrice());
        }
        return Component.translatable("stardewcraft.auction.bid.call_lead", currentPrice(), bidder);
    }

    private void drawRail(GuiGraphics g, int mouseX, int mouseY) {
        AuctionUi.band(g, railX, railY, railW, railH);
        int innerX = railX + 12;
        int innerW = railW - 24;

        // Price plates.
        int plateW = (innerW - plateGap) / 2;
        AuctionUi.pricePlate(g, font, Component.translatable("stardewcraft.auction.bid.current", ""),
            currentPrice(), innerX, plateY, plateW, plateH, false);
        AuctionUi.pricePlate(g, font, Component.translatable("stardewcraft.auction.bid.next", ""),
            nextBid(), innerX + plateW + plateGap, plateY, innerW - plateW - plateGap, plateH, true);

        // Quick raise paddles.
        AuctionUi.sectionLabel(g, font, Component.translatable("stardewcraft.auction.bid.quick"), quickX, quickY - 15, quickW);
        drawQuickButtons(g, mouseX, mouseY);

        // Custom amount field.
        g.drawString(font, Component.translatable("stardewcraft.auction.bid.custom"), fieldBoxX, fieldBoxY - 14, AuctionUi.BODY, false);
        AuctionUi.inputBox(g, fieldBoxX, fieldBoxY, fieldBoxW, fieldBoxH, bidField.isFocused(),
            AuctionUi.inside(mouseX, mouseY, fieldBoxX, fieldBoxY, fieldBoxW, fieldBoxH), parseBid() < nextBid());
        CommonGuiTextures.drawGoldCoin16(g, fieldBoxX + 10, fieldBoxY + (fieldBoxH - 14) / 2, 0.58f);
        g.fill(fieldBoxX + 28, fieldBoxY + 7, fieldBoxX + 29, fieldBoxY + fieldBoxH - 7, 0x55A66B26);

        // Hint + confirm.
        boolean enabled = canBid() && parseBid() >= nextBid();
        boolean hover = enabled && AuctionUi.inside(mouseX, mouseY, submitX, submitY, submitW, submitH);
        AuctionUi.drawClamped(g, font, bidHint(), railX + 13, submitY - 13, innerW - 2,
            enabled ? AuctionUi.MUTED : AuctionUi.ERROR);
        AuctionUi.actionButton(g, font,
            Component.translatable(canBid() ? "stardewcraft.auction.bid.submit" : "stardewcraft.auction.bid.blocked"),
            submitX, submitY, submitW, submitH, enabled, hover);
    }

    private void drawQuickButtons(GuiGraphics g, int mouseX, int mouseY) {
        int step = quickStep();
        int base = nextBid();
        int gap = 7;
        int w = Math.max(48, (quickW - gap * 2) / 3);
        drawRaiseButton(g, mouseX, mouseY, quickX, quickY, w, 28, base);
        drawRaiseButton(g, mouseX, mouseY, quickX + w + gap, quickY, w, 28, base + step);
        drawRaiseButton(g, mouseX, mouseY, quickX + (w + gap) * 2, quickY, w, 28, base + step * 2);
    }

    private void drawRaiseButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, int value) {
        boolean enabled = canBid();
        boolean hover = enabled && AuctionUi.inside(mouseX, mouseY, x, y, w, h);
        AuctionUi.paddleButton(g, font, value + "g", x, y, w, h, enabled, hover);
    }

    private Component bidHint() {
        if (!canBid()) {
            return Component.translatable("stardewcraft.auction.bid.blocked_hint");
        }
        if (parseBid() < nextBid()) {
            return Component.translatable("stardewcraft.auction.bid.need_next", nextBid());
        }
        return Component.translatable("stardewcraft.auction.bid.ready_hint");
    }

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mouseX = ldx(rawX);
        double mouseY = ldy(rawY);
        if (clickQuick(mouseX, mouseY)) return true;
        if (AuctionUi.inside(mouseX, mouseY, fieldBoxX, fieldBoxY, fieldBoxW, fieldBoxH)) {
            bidField.setFocused(true);
            setFocused(bidField);
            return bidField.mouseClicked(mouseX, mouseY, button) || true;
        }
        if (AuctionUi.inside(mouseX, mouseY, submitX, submitY, submitW, submitH)) {
            submit();
            return true;
        }
        bidField.setFocused(false);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickQuick(double mouseX, double mouseY) {
        int step = quickStep();
        int base = nextBid();
        int gap = 7;
        int w = Math.max(48, (quickW - gap * 2) / 3);
        if (clickRaise(mouseX, mouseY, quickX, quickY, w, 28, base)) return true;
        if (clickRaise(mouseX, mouseY, quickX + w + gap, quickY, w, 28, base + step)) return true;
        return clickRaise(mouseX, mouseY, quickX + (w + gap) * 2, quickY, w, 28, base + step * 2);
    }

    private boolean clickRaise(double mouseX, double mouseY, int x, int y, int w, int h, int value) {
        if (canBid() && AuctionUi.inside(mouseX, mouseY, x, y, w, h)) {
            bidField.setValue(String.valueOf(value));
            bidField.setFocused(true);
            setFocused(bidField);
            playSelect();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (bidField != null && bidField.isFocused() && !Character.isDigit(codePoint)) {
            return false;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        }
        int step = quickStep();
        if (keyCode == 265 && canBid()) {
            bidField.setValue(String.valueOf(Math.max(nextBid(), parseBid() + step)));
            bidField.setFocused(true);
            setFocused(bidField);
            playSelect();
            return true;
        }
        if (keyCode == 264 && canBid()) {
            bidField.setValue(String.valueOf(Math.max(nextBid(), parseBid() - step)));
            bidField.setFocused(true);
            setFocused(bidField);
            playSelect();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit() {
        int bid = parseBid();
        if (!canBid() || bid < nextBid()) {
            playCancel();
            return;
        }
        // Debounce double-submits; the server echoes the new price back via the board sync.
        long now = System.currentTimeMillis();
        if (now - lastSubmitMs < 250L) {
            return;
        }
        lastSubmitMs = now;
        PacketDistributor.sendToServer(new AuctionBidSubmitPayload(bid));
        playSelect();
        // Stay open: the live board refresh shows the new highest bid and the next legal bid in place,
        // so the player can keep raising without re-opening the screen.
        bidField.setFocused(false);
    }

    private int parseBid() {
        try {
            return Math.max(0, Integer.parseInt(bidField.getValue().trim()));
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
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.BIG_SELECT.get(), 0.82f, 0.78f));
        }
    }

    private void playCancel() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CANCEL.get(), 0.92f, 0.45f));
        }
    }
}
