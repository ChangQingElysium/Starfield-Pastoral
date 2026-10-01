package com.stardew.craft.client.building;

import com.stardew.craft.building.runtime.*;
import com.stardew.craft.client.gui.FarmFolioScreen;
import com.stardew.craft.network.payload.BuildingDraftCancelPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.PacketDistributor;

/** No site permission is required to leave or unpin the player's own draft. */
public final class BuildingPreviewControlsScreen extends FarmFolioScreen {
    private final InteractionHand hand;

    public BuildingPreviewControlsScreen(InteractionHand hand) {
        super(Component.translatable("building.stardewcraft.preview_controls"), null);
        this.hand = hand;
    }

    @Override
    protected int preferredWidth() {
        return 404;
    }

    @Override
    protected int preferredHeight() {
        return 204;
    }

    @Override
    protected void layout() {
        var stack = minecraft.player.getItemInHand(hand);
        int bw = (w - 42) / 2;
        button(
                                Component.translatable("building.stardewcraft.unpin_preview"),
                                x + 16,
                                y + h - 76,
                                bw,
                                28,
                                "button",
                                "move",
                                () -> cancel(false))
                        .active =
                BuildingBlueprintItem.draft(stack).contains("DraftAnchor");
        button(
                Component.translatable("menu.returnToGame"),
                x + 26 + bw,
                y + h - 76,
                bw,
                28,
                this::onClose);
        button(
                BuildingBlueprintItem.isMove(stack)
                        ? Component.translatable("building.stardewcraft.cancel_move")
                        : ui("end_preview"),
                x + 16,
                y + h - 38,
                w - 128,
                24,
                () -> {
                    if (BuildingBlueprintItem.isMove(stack)) cancel(true);
                    else {
                        cancel(false);
                        BuildingPlacementPreview.hideHeldPreview();
                        com.stardew.craft.client.hud.StardewHudMessageManager.showGlobalMessage(
                                ui("resume_preview_hint"));
                    }
                });
        button(
                Component.translatable("menu.game"),
                x + w - 104,
                y + h - 38,
                88,
                24,
                () -> minecraft.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
    }

    private void cancel(boolean end) {
        PacketDistributor.sendToServer(
                new BuildingDraftCancelPayload(
                        hand, BuildingDrafts.id(minecraft.player.getItemInHand(hand)), end));
        onClose();
    }

    @Override
    protected void paint(GuiGraphics g) {
        var stack = minecraft.player.getItemInHand(hand);
        paper(g, x + 16, y + 36, w - 32, h - 124);
        item(g, stack, x + 30, y + 50, 2);
        label(g, stack.getHoverName(), x + 80, y + 49, w - 108, INK);
        paragraph(
                g,
                ui(BuildingBlueprintItem.isMove(stack) ? "move_preserved" : "preview_help"),
                x + 80,
                y + 69,
                w - 108,
                y + h - 94,
                MUTED);
    }
}
