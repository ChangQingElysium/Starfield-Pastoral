package com.stardew.craft.cutscene.command;

import com.stardew.craft.client.gui.common.StardewObjectDialogueScreen;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Opens the source-style object dialogue box and waits for it to close. */
public final class ObjectDialogueCommand implements EventCommand {
    private final String text;
    private boolean done;

    public ObjectDialogueCommand(String text) {
        this.text = text;
    }

    @Override
    public void start(EventPlayer player) {
        done = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            done = true;
            return;
        }
		String displayText = isTranslationKey(text)
			? OpenNpcDialogueScreenPayload.rawTranslation(text)
			: text;
		displayText = OpenNpcDialogueScreenPayload.resolvePlayerDialogueText(
			displayText, minecraft.player.getName().getString());
		Component message = Component.literal(displayText);
        minecraft.setScreen(new StardewObjectDialogueScreen(List.of(message)));
    }

    @Override
    public void tick(EventPlayer player) {
        if (!(Minecraft.getInstance().screen instanceof StardewObjectDialogueScreen)) {
            done = true;
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

	private static boolean isTranslationKey(String value) {
		return value.startsWith("stardewcraft.") || value.startsWith("event.") || value.startsWith("message.");
	}
}
