package com.stardew.craft.cutscene.command;

import com.stardew.craft.client.render.CutsceneTextAboveHeadRenderer;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;

/**
 * text_above_head: show a short SDV-style overhead text above an actor.
 * JSON: {"cmd":"text_above_head","actor":"willy","text":"Ahoy!","ticks":40}
 */
public class TextAboveHeadCommand implements EventCommand {

    private final String actorTag;
    private final String text;
    private final int durationTicks;
    private final double offsetY;

    private int actorEntityId = Integer.MIN_VALUE;
    private int elapsed;

    public TextAboveHeadCommand(String actorTag, String text, int durationTicks, double offsetY) {
        this.actorTag = actorTag;
        this.text = text;
        this.durationTicks = Math.max(1, durationTicks);
        this.offsetY = offsetY;
    }

    @SuppressWarnings("null")
    @Override
    public void start(EventPlayer player) {
        elapsed = 0;

        Minecraft mc = Minecraft.getInstance();
        Mob actor = player.getActor(actorTag);
        if (mc.level == null || mc.player == null || actor == null) {
            elapsed = durationTicks;
            return;
        }

        String resolved = resolveText(mc);
        actorEntityId = actor.getId();
        CutsceneTextAboveHeadRenderer.show(actorEntityId, resolved, durationTicks, offsetY);
    }

    @Override
    public void tick(EventPlayer player) {
        elapsed++;
        if (actorEntityId != Integer.MIN_VALUE) {
            CutsceneTextAboveHeadRenderer.tick(actorEntityId);
        }
        if (elapsed >= durationTicks) {
            cleanup();
        }
    }

    @Override
    public boolean isComplete() {
        return elapsed >= durationTicks;
    }

    @Override
    public void onSkip(EventPlayer player) {
        cleanup();
        elapsed = durationTicks;
    }

    private String resolveText(Minecraft mc) {
        String displayText = (text.startsWith("stardewcraft.") || text.startsWith("event."))
                ? OpenNpcDialogueScreenPayload.rawTranslation(text)
                : text;
        String playerName = mc.player == null ? "" : mc.player.getName().getString();
        return OpenNpcDialogueScreenPayload.resolvePlayerDialogueText(displayText, playerName);
    }

    private void cleanup() {
        if (actorEntityId != Integer.MIN_VALUE) {
            CutsceneTextAboveHeadRenderer.hide(actorEntityId);
            actorEntityId = Integer.MIN_VALUE;
        }
    }
}
