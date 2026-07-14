package com.stardew.craft.cutscene.command;

import com.stardew.craft.client.festival.WinterStarCutsceneContext;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.network.payload.ClientNpcVisibilityState;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import net.minecraft.client.Minecraft;

/** Dynamic beats used by the shared Winter Star return-gift cutscene. */
public final class WinterStarStageCommand implements EventCommand {
    private final String action;
    private boolean done;
    private boolean waitingForDialogue;

    public WinterStarStageCommand(String action) {
        this.action = action;
    }

    @Override
    public void start(EventPlayer player) {
        done = false;
        waitingForDialogue = false;
        switch (action) {
            case "hide_giver" -> {
                String giverId = WinterStarCutsceneContext.giverId();
                ClientNpcVisibilityState.hide(giverId);
                player.trackHiddenNpc(giverId);
                done = true;
            }
            case "before_dialogue" -> openDialogue(WinterStarCutsceneContext.beforeKey());
            case "after_dialogue" -> openDialogue(WinterStarCutsceneContext.afterKey());
            case "open_gift" -> {
                TemporaryBlockCommand.restore("winter_star_gift_box");
                new GroundItemCommand(
                    "winter_star_return_gift",
                    WinterStarCutsceneContext.itemId(),
                    2.5D, 64.0D, 3.5D,
                    0.8F, 0.0F
                ).start(player);
                player.sendServerAction("winter_star_open_gift", "");
                done = true;
            }
            default -> done = true;
        }
    }

    private void openDialogue(String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || key == null || key.isBlank()) {
            done = true;
            return;
        }
        String playerName = mc.player.getName().getString();
        String text = OpenNpcDialogueScreenPayload.rawTranslation(key).replace("@", playerName);
        text = OpenNpcDialogueScreenPayload.resolveInlineGenderTokens(text, true);
        text = OpenNpcDialogueScreenPayload.resolveGenderSplit(text, true);
        text = OpenNpcDialogueScreenPayload.resolveDialogueCommands(text);
        text = OpenNpcDialogueScreenPayload.resolvePercentTokens(text, playerName);
        mc.setScreen(new com.stardew.craft.client.gui.common.StardewNpcDialogueScreen(
            WinterStarCutsceneContext.giverId(), text, 0));
        waitingForDialogue = true;
    }

    @Override
    public void tick(EventPlayer player) {
        if (!waitingForDialogue) {
            return;
        }
        if (!(Minecraft.getInstance().screen
            instanceof com.stardew.craft.client.gui.common.StardewNpcDialogueScreen)) {
            waitingForDialogue = false;
            done = true;
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }
}
