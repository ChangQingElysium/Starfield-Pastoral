package com.stardew.craft.client.gui.auction;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.AuctionEntryChoicePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

public class AuctionEntryChoiceScreen extends AuctionScreen {
    private int choiceW, houseX, hintY;
    public AuctionEntryChoiceScreen() { super("stardewcraft.auction.entry.title"); }
    @Override protected int preferredWidth() { return 340; }
    @Override protected int preferredHeight() { return 244; }
    @Override protected void layout() {
        choiceW = (contentW - 20) / 2; houseX = choiceW + 20;
        var auction = button(0, 56, choiceW, tr("entry.auction"), () -> choose(true));
        var house = button(houseX, 56, choiceW, tr("entry.house"), () -> choose(false));
        hintY = 56 + Math.max(auction.getHeight(), house.getHeight()) + 9;
        contentHeight = hintY + Math.max(wrappedHeight(tr("entry.auction_hint"), choiceW - 8),
                wrappedHeight(tr("entry.house_hint"), choiceW - 8)) + 8;
        footer(tr("picker.cancel"), false, false, this::onClose);
    }
    @Override protected void drawBody(GuiGraphics g, float partialTick) {
        item(g, new ItemStack(ModItems.AUCTION_PADDLE.get()), (choiceW - 32) / 2, 10, 2);
        item(g, new ItemStack(Items.OAK_DOOR), houseX + (choiceW - 32) / 2, 10, 2);
        paragraph(g, tr("entry.auction_hint"), 4, hintY, choiceW - 8, AuctionUi.MUTED);
        paragraph(g, tr("entry.house_hint"), houseX + 4, hintY, choiceW - 8, AuctionUi.MUTED);
    }
    private void choose(boolean auction) {
        PacketDistributor.sendToServer(new AuctionEntryChoicePayload(auction)); onClose();
    }
}
