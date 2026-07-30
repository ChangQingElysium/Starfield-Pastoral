package com.stardew.craft.cutscene.runtime;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only substitutions for the reusable combat-rescue cutscene.
 *
 * <p>The server sends this context only after the destination chunk is ready,
 * immediately before it starts the event. Keeping the NPC and dialogue out of
 * the event ID avoids duplicating the same authored camera path for every
 * possible spouse rescuer.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class CombatRescueCutsceneContext {
    public static final String RESCUER_NPC_TOKEN = "$combat_rescuer";
    public static final String RESCUER_DIALOGUE_TOKEN = "$combat_rescuer_dialogue";

    private static String rescuerNpcId = "linus";
    private static String rescuerDialogueKey = "event.combat_rescue.mine.linus";

    private CombatRescueCutsceneContext() {
    }

    public static void set(String npcId, String dialogueKey) {
        rescuerNpcId = normalize(npcId, "linus");
        rescuerDialogueKey = normalize(dialogueKey, "event.combat_rescue.mine.linus");
    }

    public static String resolveNpcId(String value) {
        return RESCUER_NPC_TOKEN.equals(value) ? rescuerNpcId : value;
    }

    public static String resolveDialogue(String value) {
        return RESCUER_DIALOGUE_TOKEN.equals(value) ? rescuerDialogueKey : value;
    }

    public static void reset() {
        rescuerNpcId = "linus";
        rescuerDialogueKey = "event.combat_rescue.mine.linus";
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
