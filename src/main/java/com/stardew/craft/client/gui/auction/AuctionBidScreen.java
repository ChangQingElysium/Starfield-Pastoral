package com.stardew.craft.client.gui.auction;

import com.stardew.craft.client.auction.AuctionClientState;
import com.stardew.craft.client.gui.StardewRealtimeScreen;
import com.stardew.craft.auction.AuctionService;
import com.stardew.craft.network.payload.AuctionBidSubmitPayload;
import com.stardew.craft.network.payload.OpenAuctionBidPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

@SuppressWarnings("null")
public class AuctionBidScreen extends AuctionScreen implements StardewRealtimeScreen {
    private final OpenAuctionBidPayload seed;
    private boolean wasLive, wide, customBid;
    private long lastSubmitMs;
    private String draft;
    private int shownLot = -1, lotY, priceX, priceY, priceW, quickY, fieldY, hintY, bidderY;
    private AuctionLayout.Bid geometry;
    private EditBox bidField;
    private Button submit;
    private final Button[] quick = new Button[3];
    public AuctionBidScreen(OpenAuctionBidPayload payload) {
        super("stardewcraft.auction.bid.title"); this.seed = payload;
    }
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

    @Override protected Component heading() { return Component.literal(auctionName()); }
    @Override protected void layout() {
        wide = contentW >= 350;
        int lotHeight = wide ? 58 : Math.max(22, line + 6);
        priceX = wide ? 172 : 0;
        priceW = contentW - priceX;
        int quickHeight = buttonHeight(Component.literal(String.valueOf(Integer.MAX_VALUE)), (priceW - 8) / 3);
        geometry = AuctionLayout.bid(contentW, line,
                wrappedHeight(tr("bid.lot_meta", lotIndex(), lotCount(), AuctionService.LOT_SECONDS), contentW), lotHeight,
                wrappedHeight(tr("bid.current", wide ? "" : Integer.MAX_VALUE + "g"), priceW), wrappedHeight(tr("bid.next", Integer.MAX_VALUE + "g"), priceW), quickHeight);
        lotY = geometry.lotY(); priceY = geometry.priceY(); quickY = geometry.quickY();
        int qw = (priceW - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int index = i;
            quick[i] = button(priceX + i * (qw + 4), quickY, qw, Component.literal(String.valueOf(Integer.MAX_VALUE)),
                    () -> { bidField.setValue(String.valueOf(raise(index))); bidField.setFocused(true); setFocused(bidField); });
        }
        fieldY = geometry.fieldY();
        if (draft == null) draft = String.valueOf(nextBid());
        bidField = field(priceX, fieldY, priceW, tr("bid.custom"), draft, 10, true, v -> { draft = v; customBid = true; });
        hintY = geometry.hintY();
        bidderY = wide ? lotY + 116 + 2 * (line + 3) : hintY;
        // Reserve enough space for every status message, including a live outbid and blocked state.
        int hints = Math.max(wrappedHeight(tr("bid.ready_hint"), priceW),
                Math.max(wrappedHeight(tr("bid.blocked_hint"), priceW), wrappedHeight(tr("bid.need_next", Integer.MAX_VALUE), priceW)));
        if (!wide) bidderY = hintY + hints + 8;
        contentHeight = Math.max(hintY + hints, bidderY + 2 * (line + 3)) + 10;
        footer(tr("picker.cancel"), false, false, this::onClose);
        // Allocate height for the longest supported integer before live amounts change.
        submit = footer(tr("bid.submit_amount", Integer.MAX_VALUE), true, true, this::submit);
        shownLot = lotIndex();
    }
    private int raise(int n) { return (int) Math.min(Integer.MAX_VALUE, (long) nextBid() + (long) quickStep() * n); }
    @Override protected void updateState() {
        if (live()) wasLive = true;
        else if (wasLive) { onClose(); return; }
        if (shownLot != lotIndex()) { draft = String.valueOf(nextBid()); customBid = false; resetScroll(); rebuild(); }
        if (!customBid && amount(draft) != nextBid()) {
            bidField.setValue(String.valueOf(nextBid())); customBid = false;
        }
        for (int i = 0; i < quick.length; i++) {
            quick[i].setMessage(Component.literal(String.valueOf(raise(i))));
            quick[i].active = canBid();
        }
        submit.active = canBid() && amount(draft) >= nextBid();
        submit.setMessage(tr("bid.submit_amount", amount(draft)));
    }
    @Override protected void drawBody(GuiGraphics g, float partialTick) {
        paragraph(g, tr("bid.lot_meta", lotIndex(), lotCount(), Math.max(0, remainingSeconds())), 0, 0, contentW, AuctionUi.BODY);
        float ratio = Math.max(0, Math.min(1, remainingSeconds() / (float) AuctionService.LOT_SECONDS));
        int timerY = lotY - 9;
        g.fill(0, timerY, contentW, timerY + 3, 0xFFCCB590);
        g.fill(0, timerY, Math.round(contentW * ratio), timerY + 3,
                remainingSeconds() <= AuctionService.FINAL_EXTENSION_SECONDS ? AuctionUi.ERROR : AuctionUi.GOLD);
        if (wide) {
            AuctionUi.sprite(g, "pedestal", 36, lotY + 12, 80, 52);
            item(g, lotStack(), 60, lotY + 13, 2);
            text(g, lotStack().getHoverName(), 0, lotY + 78, 154, AuctionUi.INK);
            text(g, tr("bid.seller", sellerName()), 0, lotY + 82 + line, 154, AuctionUi.MUTED);
        } else {
            item(g, lotStack(), 0, lotY, 1);
            text(g, lotStack().getHoverName(), 24, lotY + 2, contentW - 24, AuctionUi.INK);
        }
        if (wide) {
            paragraph(g, tr("bid.current", ""), priceX, priceY, priceW, AuctionUi.MUTED);
            String price = currentPrice() + "g";
            int scale = font.width(price) * 2 <= priceW ? 2 : 1;
            g.pose().pushPose();
            g.pose().translate(priceX, geometry.valueY(), 0); g.pose().scale(scale, scale, 1);
            g.drawString(font, price, 0, 0, AuctionUi.INK, false); g.pose().popPose();
        } else paragraph(g, tr("bid.current", currentPrice() + "g"), priceX, priceY, priceW, AuctionUi.INK);
        paragraph(g, tr("bid.next", nextBid() + "g"), priceX, geometry.minimumY(), priceW, AuctionUi.GOLD);
        if (wide) text(g, tr("bid.custom"), priceX, fieldY - line - 5, priceW, AuctionUi.INK);
        Component hint = tr(!canBid() ? "bid.blocked_hint" : amount(draft) < nextBid() ? "bid.need_next" : "bid.ready_hint", nextBid());
        paragraph(g, hint, priceX, hintY, priceW, submit.active ? AuctionUi.MUTED : AuctionUi.ERROR);
        Component bidder = highestBidderName().isBlank() ? tr("bid.no_bidder") : tr("bid.bidder", highestBidderName());
        text(g, bidder, 0, bidderY, wide ? 154 : contentW, AuctionUi.BODY);
        if (!wide) text(g, tr("bid.seller", sellerName()), 0, bidderY + line + 3, contentW, AuctionUi.MUTED);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (bidField.isFocused()) {
            if (key == 257 || key == 335) { submit(); return true; }
            if ((key == 264 || key == 265) && canBid()) {
                long proposed = (long) amount(draft) + (key == 265 ? quickStep() : -quickStep());
                bidField.setValue(String.valueOf(Math.min(Integer.MAX_VALUE, Math.max(nextBid(), proposed))));
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }
    private void submit() {
        int bid = amount(draft);
        if (!canBid() || bid < nextBid()) { cancelSound(); return; }
        long now = System.currentTimeMillis();
        if (now - lastSubmitMs < 250) return;
        lastSubmitMs = now;
        PacketDistributor.sendToServer(new AuctionBidSubmitPayload(bid));
        customBid = false;
        bidField.setFocused(false);
    }
}
