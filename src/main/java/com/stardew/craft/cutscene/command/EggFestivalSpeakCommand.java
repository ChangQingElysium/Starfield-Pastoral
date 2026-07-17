package com.stardew.craft.cutscene.command;

import com.stardew.craft.client.gui.common.StardewNpcDialogueScreen;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.festival.client.EggFestivalCutsceneClientState;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import net.minecraft.client.Minecraft;

public class EggFestivalSpeakCommand implements EventCommand {
    private static final String MODE_WINNER = "winner";
    private static final String MODE_BRANCH = "branch";

    private final String npcId;
    private final String mode;
    private final String text;
    private final String playerText;
    private final String npcText;
    private boolean done;

    public EggFestivalSpeakCommand(String npcId, String mode, String text, String playerText, String npcText) {
        this.npcId = npcId == null || npcId.isBlank() ? "lewis" : npcId;
        this.mode = mode == null || mode.isBlank() ? MODE_WINNER : mode;
        this.text = text == null ? "" : text;
        this.playerText = playerText == null ? "" : playerText;
        this.npcText = npcText == null ? "" : npcText;
    }

    @Override
    public void start(EventPlayer player) {
        done = false;
        Minecraft mc = Minecraft.getInstance();
        var localPlayer = mc.player;
        if (localPlayer == null) {
            done = true;
            return;
        }
        String displayText = switch (mode) {
            case MODE_WINNER -> EggFestivalCutsceneClientState.winnerText();
            case MODE_BRANCH -> EggFestivalCutsceneClientState.playerWon() ? playerText : npcText;
            default -> text;
        };
        if (displayText == null || displayText.isBlank()) {
            done = true;
            return;
        }
        displayText = resolveDisplayText(displayText, localPlayer.getName().getString());
        mc.setScreen(new StardewNpcDialogueScreen(npcId, displayText, 0));
    }

    @Override
    public void tick(EventPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || !(mc.screen instanceof StardewNpcDialogueScreen)) {
            done = true;
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    private static String resolveDisplayText(String input, String playerName) {
        String displayText = OpenNpcDialogueScreenPayload.resolveClientTextSource(input);
        return OpenNpcDialogueScreenPayload.resolvePlayerDialogueText(displayText, playerName);
    }
}
