package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import net.minecraft.client.Minecraft;

/**
 * speak: opens a dialogue screen with the given NPC portrait and text.
 * The command blocks until the player dismisses the dialogue.
 * Uses the same text resolution pipeline as NPC dialogue (@ player name,
 * gender tokens, $ control commands, % substitutions).
 * JSON: { "cmd": "speak", "npc_id": "abigail", "text": "stardewcraft.event.test.line1" }
 */
public class SpeakCommand implements EventCommand {

    private final String npcId;
    private final String text;
    private boolean done;

    public SpeakCommand(String npcId, String text) {
        this.npcId = npcId;
        this.text = text;
    }

    @SuppressWarnings("null")
    @Override
    public void start(EventPlayer player) {
        done = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            done = true;
            return;
        }

        // Resolve translation key or use raw text
        String displayText;
        if (text.startsWith("stardewcraft.") || text.startsWith("event.")) {
            displayText = OpenNpcDialogueScreenPayload.rawTranslation(text);
        } else {
            displayText = text;
        }

        // Full SDV text resolution pipeline (same as NPC dialogue)
        String playerName = mc.player.getName().getString();
        displayText = OpenNpcDialogueScreenPayload.resolvePlayerDialogueText(displayText, playerName);

        mc.setScreen(new com.stardew.craft.client.gui.common.StardewNpcDialogueScreen(
                npcId, displayText, 0
        ));
    }

    @Override
    public void tick(EventPlayer player) {
        // Complete when dialogue screen is closed
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || !(mc.screen instanceof com.stardew.craft.client.gui.common.StardewNpcDialogueScreen)) {
            done = true;
        }
    }

    @Override
    public boolean isComplete() { return done; }
}
