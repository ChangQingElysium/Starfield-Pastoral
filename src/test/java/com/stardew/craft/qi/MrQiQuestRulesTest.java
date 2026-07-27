package com.stardew.craft.qi;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MrQiQuestRulesTest {
    @Test
    void tunnelIsTheHiddenStarterAndConsumesHeldBattery() {
        MrQiQuestRules.Decision decision = evaluate(
                MrQiQuestAnchor.TUNNEL_POWER_PANEL,
                Set.of(),
                MrQiQuestRules.BATTERY_PACK_ID,
                1,
                Map.of()
        );

        assertEquals(MrQiQuestRules.Outcome.SUCCESS, decision.outcome());
        assertEquals(MrQiQuestRules.TUNNEL_FLAG, decision.flagToAdd());
        assertEquals("", decision.questToRemove());
        assertEquals(MrQiQuestRules.QUEST_RAILROAD, decision.questToAdd());
        assertEquals(MrQiQuestRules.ConsumeSource.HELD_ITEM, decision.cost().source());
        assertEquals(MrQiQuestRules.SoundCue.TUNNEL_OPEN, decision.soundCue());
        assertEquals(2, decision.dialogueKeys().size());
    }

    @Test
    void tunnelIgnoresBatteryElsewhereInInventory() {
        MrQiQuestRules.Decision decision = evaluate(
                MrQiQuestAnchor.TUNNEL_POWER_PANEL,
                Set.of(),
                "",
                0,
                Map.of(MrQiQuestRules.BATTERY_PACK_ID, 1)
        );

        assertEquals(MrQiQuestRules.Outcome.INITIAL_TEXT, decision.outcome());
        assertEquals("stardewcraft.qi.tunnel.initial", decision.dialogueKeys().getFirst());
    }

    @Test
    void railroadRequiresTunnelAndHeldRainbowShell() {
        MrQiQuestRules.Decision blocked = evaluate(
                MrQiQuestAnchor.RAILROAD_BOX,
                Set.of(),
                MrQiQuestRules.RAINBOW_SHELL_ID,
                1,
                Map.of()
        );
        assertEquals(MrQiQuestRules.Outcome.INITIAL_TEXT, blocked.outcome());

        MrQiQuestRules.Decision success = evaluate(
                MrQiQuestAnchor.RAILROAD_BOX,
                Set.of(MrQiQuestRules.TUNNEL_FLAG),
                MrQiQuestRules.RAINBOW_SHELL_ID,
                1,
                Map.of()
        );
        assertEquals(MrQiQuestRules.Outcome.SUCCESS, success.outcome());
        assertEquals(MrQiQuestRules.RAILROAD_FLAG, success.flagToAdd());
        assertEquals(MrQiQuestRules.QUEST_RAILROAD, success.questToRemove());
        assertEquals(MrQiQuestRules.QUEST_MAYOR_FRIDGE, success.questToAdd());
        assertEquals(MrQiQuestRules.SoundCue.SHIP, success.soundCue());
    }

    @Test
    void mayorFridgeConsumesTenBeetsAcrossInventoryNotFromHeldRequirement() {
        MrQiQuestRules.Decision shortStack = evaluate(
                MrQiQuestAnchor.MAYOR_FRIDGE,
                Set.of(MrQiQuestRules.RAILROAD_FLAG),
                "",
                0,
                Map.of(MrQiQuestRules.BEET_ID, 9)
        );
        assertEquals(MrQiQuestRules.Outcome.INITIAL_TEXT, shortStack.outcome());

        MrQiQuestRules.Decision success = evaluate(
                MrQiQuestAnchor.MAYOR_FRIDGE,
                Set.of(MrQiQuestRules.RAILROAD_FLAG),
                "",
                0,
                Map.of(MrQiQuestRules.BEET_ID, 10)
        );
        assertEquals(MrQiQuestRules.Outcome.SUCCESS, success.outcome());
        assertEquals(10, success.cost().count());
        assertEquals(MrQiQuestRules.ConsumeSource.INVENTORY, success.cost().source());
        assertEquals(MrQiQuestRules.MAYOR_FRIDGE_FLAG, success.flagToAdd());
        assertEquals(MrQiQuestRules.QUEST_MAYOR_FRIDGE, success.questToRemove());
        assertEquals(MrQiQuestRules.QUEST_SAND_DRAGON, success.questToAdd());
        assertEquals(MrQiQuestRules.SoundCue.COIN, success.soundCue());
    }

    @Test
    void sandDragonRequiresMayorFlagAndHeldSolarEssence() {
        MrQiQuestRules.Decision success = evaluate(
                MrQiQuestAnchor.SAND_DRAGON,
                Set.of(MrQiQuestRules.MAYOR_FRIDGE_FLAG),
                MrQiQuestRules.SOLAR_ESSENCE_ID,
                1,
                Map.of()
        );

        assertEquals(MrQiQuestRules.Outcome.SUCCESS, success.outcome());
        assertEquals(MrQiQuestRules.SAND_DRAGON_FLAG, success.flagToAdd());
        assertEquals(MrQiQuestRules.QUEST_SAND_DRAGON, success.questToRemove());
        assertEquals(MrQiQuestRules.QUEST_LUMBER_PILE, success.questToAdd());
        assertEquals(MrQiQuestRules.SoundCue.EAT, success.soundCue());
    }

    @Test
    void lumberPileGrantsClubCardOnlyOnceAndHasNoFallbackDialogue() {
        MrQiQuestRules.Decision success = evaluate(
                MrQiQuestAnchor.FARM_LUMBER_PILE,
                Set.of(MrQiQuestRules.SAND_DRAGON_FLAG),
                "",
                0,
                Map.of()
        );
        assertEquals(MrQiQuestRules.Outcome.SUCCESS, success.outcome());
        assertTrue(success.grantClubCard());
        assertEquals(MrQiQuestRules.LUMBER_PILE_FLAG, success.flagToAdd());
        assertEquals(MrQiQuestRules.QUEST_LUMBER_PILE, success.questToRemove());
        assertTrue(success.dialogueKeys().isEmpty());

        MrQiQuestRules.Decision repeated = evaluate(
                MrQiQuestAnchor.FARM_LUMBER_PILE,
                Set.of(MrQiQuestRules.SAND_DRAGON_FLAG, MrQiQuestRules.LUMBER_PILE_FLAG),
                "",
                0,
                Map.of()
        );
        assertEquals(MrQiQuestRules.Outcome.NO_ACTION, repeated.outcome());
        assertFalse(repeated.grantClubCard());
    }

    @Test
    void completedInteractionsRepeatOnlyTheMrQiNote() {
        MrQiQuestRules.Decision tunnel = evaluate(
                MrQiQuestAnchor.TUNNEL_POWER_PANEL,
                Set.of(MrQiQuestRules.TUNNEL_FLAG),
                "",
                0,
                Map.of()
        );
        MrQiQuestRules.Decision railroad = evaluate(
                MrQiQuestAnchor.RAILROAD_BOX,
                Set.of(MrQiQuestRules.RAILROAD_FLAG),
                "",
                0,
                Map.of()
        );
        MrQiQuestRules.Decision fridge = evaluate(
                MrQiQuestAnchor.MAYOR_FRIDGE,
                Set.of(MrQiQuestRules.MAYOR_FRIDGE_FLAG),
                "",
                0,
                Map.of()
        );
        MrQiQuestRules.Decision dragon = evaluate(
                MrQiQuestAnchor.SAND_DRAGON,
                Set.of(MrQiQuestRules.SAND_DRAGON_FLAG),
                "",
                0,
                Map.of()
        );

        for (MrQiQuestRules.Decision decision : new MrQiQuestRules.Decision[]{
                tunnel, railroad, fridge, dragon
        }) {
            assertEquals(MrQiQuestRules.Outcome.REPEAT_NOTE, decision.outcome());
            assertEquals(1, decision.dialogueKeys().size());
            assertEquals(MrQiQuestRules.SoundCue.NONE, decision.soundCue());
        }
    }

    private static MrQiQuestRules.Decision evaluate(
            MrQiQuestAnchor anchor,
            Set<String> flags,
            String heldItem,
            int heldCount,
            Map<String, Integer> inventory
    ) {
        return MrQiQuestRules.evaluate(
                anchor,
                new MrQiQuestRules.Snapshot(flags, heldItem, heldCount, inventory)
        );
    }
}
