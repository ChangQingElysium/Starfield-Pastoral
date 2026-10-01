package com.stardew.craft.client.gui.auction;

import com.stardew.craft.client.gui.StardewRealtimeScreen;
import com.stardew.craft.network.payload.LewisConfirmResponsePayload;
import com.stardew.craft.network.payload.OpenLewisConfirmPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/** The two host decisions share auction materials and retain the server's confirmation protocol. */
@SuppressWarnings("null")
public final class AuctionConfirmScreen extends AuctionScreen implements StardewRealtimeScreen {
    private final OpenLewisConfirmPayload request;
    private final boolean cancellation;
    private boolean answered;
    private int textX, textY, textW;

    public AuctionConfirmScreen(OpenLewisConfirmPayload request) {
        super(request.acceptKey());
        this.request = request;
        cancellation = request.kind() == OpenLewisConfirmPayload.KIND_AUCTION_CANCEL;
    }
    @Override protected int preferredWidth() { return 360; }
    @Override protected int preferredHeight() {
        int w = Math.min(preferredWidth(), width - 12) - 34;
        int x = w >= 290 ? 96 : 0;
        int textHeight = wrappedHeight(question(), w - x);
        int bodyHeight = x == 0 ? 76 + textHeight : Math.max(68, textHeight + 8);
        int backW = Math.min((w - 8) / 3, Math.max(56, font.width(tr("picker.cancel")) + 20));
        int actions = Math.max(buttonHeight(Component.translatable(request.rejectKey()), backW),
                buttonHeight(Component.translatable(request.acceptKey()), w - backW - 10));
        return Math.max(38, line + 24) + bodyHeight + 30 + actions;
    }
    private Component question() {
        return Component.translatable(request.questionKey(), request.args().toArray());
    }
    @Override protected void layout() {
        textX = contentW >= 290 ? 96 : 0;
        textY = textX == 0 ? 76 : Math.max(4, (68 - wrappedHeight(question(), contentW - textX)) / 2);
        textW = contentW - textX;
        contentHeight = Math.max(68, textY + wrappedHeight(question(), textW)) + 10;
        var reject = footer(Component.translatable(request.rejectKey()), false, false, () -> answer(false));
        footer(Component.translatable(request.acceptKey()), true, true, () -> answer(true));
        setInitialFocus(reject);
    }
    @Override protected void drawBody(GuiGraphics g, float partialTick) {
        AuctionUi.illustration(g, cancellation ? "register" : "gavel",
                textX == 0 ? (contentW - 64) / 2 : 8, 4);
        paragraph(g, question(), textX, textY, textW, cancellation ? AuctionUi.ERROR : AuctionUi.INK);
    }
    private void answer(boolean accepted) {
        if (answered) return;
        answered = true;
        PacketDistributor.sendToServer(new LewisConfirmResponsePayload(request.requestId(), request.kind(), accepted));
        minecraft.setScreen(null);
    }
    @Override public void onClose() { answer(false); }
}
