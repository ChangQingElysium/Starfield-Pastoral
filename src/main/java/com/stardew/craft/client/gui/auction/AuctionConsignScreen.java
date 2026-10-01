package com.stardew.craft.client.gui.auction;

import com.stardew.craft.network.payload.AuctionJoinSubmitPayload;
import com.stardew.craft.network.payload.OpenAuctionJoinListPayload.AuctionSummary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

@SuppressWarnings("null")
public class AuctionConsignScreen extends AuctionScreen {
    private final AuctionSummary target;
    private final Screen parent;
    private int selectedSlot = -1, lotY, priceLabelY, noteY, hostY, rightX, rightW;
    private String draftPrice = "100";
    private Button submit;
    public AuctionConsignScreen(AuctionSummary target) { this(target, null); }
    AuctionConsignScreen(AuctionSummary target, Screen parent) {
        super("stardewcraft.auction.consign.title"); this.target = target; this.parent = parent;
    }
    private Component when() {
        return tr("join_list.day_time", AuctionJoinListScreen.season(target.scheduledDay()),
                Math.max(0, target.scheduledDay() - 1) % 28 + 1, AuctionJoinListScreen.time(target.startMinute()));
    }
    @Override protected void layout() {
        hostY = wrappedHeight(Component.literal(target.name()), contentW - 62) + 5;
        lotY = Math.max(66, hostY + wrappedHeight(when(), contentW - 62) + line + 12) + 14;
        rightX = contentW >= 350 ? 152 : 138; rightW = contentW - rightX;
        var pick = button(0, lotY + 64, 124, tr("create.pick_item"),
                () -> minecraft.setScreen(new AuctionItemPickerScreen(this, slot -> selectedSlot = slot)));
        priceLabelY = lotY + line + 18;
        int fieldY = priceLabelY + wrappedHeight(tr("consign.price"), rightW) + 5;
        field(rightX, fieldY, rightW, tr("consign.price_hint"), draftPrice, 9, true, v -> draftPrice = v);
        noteY = Math.max(fieldY + controlH, lotY + 64 + pick.getHeight()) + 18;
        contentHeight = noteY + Math.max(wrappedHeight(tr("consign.item_ready"), contentW), wrappedHeight(tr("create.need_price"), contentW)) + 5
                + wrappedHeight(tr("consign.note"), contentW) + 8;
        footer(tr("picker.cancel"), false, false, this::onClose);
        submit = footer(tr("consign.submit"), true, true, this::submit);
    }
    @Override protected void drawBody(GuiGraphics g, float partialTick) {
        AuctionUi.sprite(g, "date_leaf", 4, 2, 40, 48);
        String day = String.valueOf(Math.max(0, target.scheduledDay() - 1) % 28 + 1);
        number(g, day, 24 - font.width(day), 16, 2, AuctionUi.INK);
        paragraph(g, Component.literal(target.name()), 60, 0, contentW - 62, AuctionUi.INK);
        text(g, tr("join_list.detail_host", target.creatorName()), 60, hostY, contentW - 62, AuctionUi.MUTED);
        paragraph(g, when(), 60, hostY + line + 5, contentW - 62, AuctionUi.GOLD);
        AuctionUi.rule(g, 0, lotY - 10, contentW);
        AuctionUi.sprite(g, "pedestal", 20, lotY, 80, 52); item(g, inventory(selectedSlot), 44, lotY + 1, 2);
        text(g, inventory(selectedSlot).isEmpty() ? tr("create.item_empty") : inventory(selectedSlot).getHoverName(),
                rightX, lotY + 4, rightW, AuctionUi.INK);
        paragraph(g, tr("consign.price"), rightX, priceLabelY, rightW, AuctionUi.INK);
        int y = paragraph(g, tr(amount(draftPrice) > 0 ? "consign.item_ready" : "create.need_price"), 0, noteY, contentW,
                amount(draftPrice) > 0 ? AuctionUi.MUTED : AuctionUi.ERROR);
        paragraph(g, tr("consign.note"), 0, y + 5, contentW, AuctionUi.MUTED);
    }
    @Override protected void updateState() { submit.active = !inventory(selectedSlot).isEmpty() && amount(draftPrice) > 0; }
    private void submit() {
        if (inventory(selectedSlot).isEmpty() || amount(draftPrice) <= 0) { cancelSound(); return; }
        PacketDistributor.sendToServer(new AuctionJoinSubmitPayload(target.id(), selectedSlot, amount(draftPrice)));
        minecraft.setScreen(null);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
