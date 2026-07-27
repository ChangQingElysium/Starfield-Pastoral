package com.stardew.craft.qi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure source-parity rules for Stardew Valley's original Mr. Qi scavenger hunt. */
public final class MrQiQuestRules {
    public static final String TUNNEL_FLAG = "TH_Tunnel";
    public static final String RAILROAD_FLAG = "TH_Railroad";
    public static final String MAYOR_FRIDGE_FLAG = "TH_MayorFridge";
    public static final String SAND_DRAGON_FLAG = "TH_SandDragon";
    public static final String LUMBER_PILE_FLAG = "TH_LumberPile";

    public static final String CLUB_CARD_FLAG = "HasClubCard";

    public static final String BATTERY_PACK_ID = "stardewcraft:battery_pack";
    public static final String RAINBOW_SHELL_ID = "stardewcraft:rainbow_shell";
    public static final String BEET_ID = "stardewcraft:beet";
    public static final String SOLAR_ESSENCE_ID = "stardewcraft:solar_essence";

    public static final String QUEST_RAILROAD = "2";
    public static final String QUEST_MAYOR_FRIDGE = "3";
    public static final String QUEST_SAND_DRAGON = "4";
    public static final String QUEST_LUMBER_PILE = "5";

    private MrQiQuestRules() {
    }

    public static Decision evaluate(MrQiQuestAnchor anchor, Snapshot snapshot) {
        return switch (anchor) {
            case TUNNEL_POWER_PANEL -> tunnel(snapshot);
            case RAILROAD_BOX -> railroad(snapshot);
            case MAYOR_FRIDGE -> mayorFridge(snapshot);
            case SAND_DRAGON -> sandDragon(snapshot);
            case FARM_LUMBER_PILE -> lumberPile(snapshot);
        };
    }

    private static Decision tunnel(Snapshot snapshot) {
        if (!snapshot.hasFlag(TUNNEL_FLAG) && snapshot.holds(BATTERY_PACK_ID, 1)) {
            return success(
                    new ItemCost(BATTERY_PACK_ID, 1, ConsumeSource.HELD_ITEM),
                    TUNNEL_FLAG,
                    "",
                    QUEST_RAILROAD,
                    false,
                    SoundCue.TUNNEL_OPEN,
                    "stardewcraft.qi.tunnel.consume_battery",
                    "stardewcraft.qi.tunnel.mr_qi_note"
            );
        }
        return snapshot.hasFlag(TUNNEL_FLAG)
                ? repeat("stardewcraft.qi.tunnel.mr_qi_note")
                : initial("stardewcraft.qi.tunnel.initial");
    }

    private static Decision railroad(Snapshot snapshot) {
        if (snapshot.hasFlag(TUNNEL_FLAG)
                && !snapshot.hasFlag(RAILROAD_FLAG)
                && snapshot.holds(RAINBOW_SHELL_ID, 1)) {
            return success(
                    new ItemCost(RAINBOW_SHELL_ID, 1, ConsumeSource.HELD_ITEM),
                    RAILROAD_FLAG,
                    QUEST_RAILROAD,
                    QUEST_MAYOR_FRIDGE,
                    false,
                    SoundCue.SHIP,
                    "stardewcraft.qi.railroad.consume_shell",
                    "stardewcraft.qi.railroad.mr_qi_note"
            );
        }
        return snapshot.hasFlag(RAILROAD_FLAG)
                ? repeat("stardewcraft.qi.railroad.mr_qi_note")
                : initial("stardewcraft.qi.railroad.initial");
    }

    private static Decision mayorFridge(Snapshot snapshot) {
        if (snapshot.hasFlag(RAILROAD_FLAG)
                && !snapshot.hasFlag(MAYOR_FRIDGE_FLAG)
                && snapshot.inventoryCount(BEET_ID) >= 10) {
            return success(
                    new ItemCost(BEET_ID, 10, ConsumeSource.INVENTORY),
                    MAYOR_FRIDGE_FLAG,
                    QUEST_MAYOR_FRIDGE,
                    QUEST_SAND_DRAGON,
                    false,
                    SoundCue.COIN,
                    "stardewcraft.qi.mayor_fridge.consume_beets",
                    "stardewcraft.qi.mayor_fridge.mr_qi_note"
            );
        }
        return snapshot.hasFlag(MAYOR_FRIDGE_FLAG)
                ? repeat("stardewcraft.qi.mayor_fridge.mr_qi_note")
                : initial("stardewcraft.qi.mayor_fridge.initial");
    }

    private static Decision sandDragon(Snapshot snapshot) {
        if (snapshot.hasFlag(MAYOR_FRIDGE_FLAG)
                && !snapshot.hasFlag(SAND_DRAGON_FLAG)
                && snapshot.holds(SOLAR_ESSENCE_ID, 1)) {
            return success(
                    new ItemCost(SOLAR_ESSENCE_ID, 1, ConsumeSource.HELD_ITEM),
                    SAND_DRAGON_FLAG,
                    QUEST_SAND_DRAGON,
                    QUEST_LUMBER_PILE,
                    false,
                    SoundCue.EAT,
                    "stardewcraft.qi.sand_dragon.consume_essence",
                    "stardewcraft.qi.sand_dragon.mr_qi_note"
            );
        }
        return snapshot.hasFlag(SAND_DRAGON_FLAG)
                ? repeat("stardewcraft.qi.sand_dragon.mr_qi_note")
                : initial("stardewcraft.qi.sand_dragon.initial");
    }

    private static Decision lumberPile(Snapshot snapshot) {
        if (snapshot.hasFlag(SAND_DRAGON_FLAG) && !snapshot.hasFlag(LUMBER_PILE_FLAG)) {
            return success(
                    ItemCost.NONE,
                    LUMBER_PILE_FLAG,
                    QUEST_LUMBER_PILE,
                    "",
                    true,
                    SoundCue.CLUB_CARD_REWARD
            );
        }
        // GameLocation.cs:LumberPile has no fallback dialogue.
        return Decision.NO_ACTION;
    }

    private static Decision success(
            ItemCost cost,
            String flagToAdd,
            String questToRemove,
            String questToAdd,
            boolean grantClubCard,
            SoundCue soundCue,
            String... dialogueKeys
    ) {
        return new Decision(
                Outcome.SUCCESS,
                cost,
                flagToAdd,
                questToRemove,
                questToAdd,
                grantClubCard,
                soundCue,
                List.of(dialogueKeys)
        );
    }

    private static Decision initial(String dialogueKey) {
        return new Decision(
                Outcome.INITIAL_TEXT,
                ItemCost.NONE,
                "",
                "",
                "",
                false,
                SoundCue.NONE,
                List.of(dialogueKey)
        );
    }

    private static Decision repeat(String dialogueKey) {
        return new Decision(
                Outcome.REPEAT_NOTE,
                ItemCost.NONE,
                "",
                "",
                "",
                false,
                SoundCue.NONE,
                List.of(dialogueKey)
        );
    }

    public enum Outcome {
        SUCCESS,
        INITIAL_TEXT,
        REPEAT_NOTE,
        NO_ACTION
    }

    public enum ConsumeSource {
        NONE,
        HELD_ITEM,
        INVENTORY
    }

    /** Semantic cue names mirror the original source; presentation wiring is added with the map. */
    public enum SoundCue {
        NONE,
        TUNNEL_OPEN,
        SHIP,
        COIN,
        EAT,
        CLUB_CARD_REWARD
    }

    public record ItemCost(String itemId, int count, ConsumeSource source) {
        public static final ItemCost NONE = new ItemCost("", 0, ConsumeSource.NONE);

        public ItemCost {
            itemId = itemId == null ? "" : itemId;
            count = Math.max(0, count);
            source = source == null ? ConsumeSource.NONE : source;
        }
    }

    public record Snapshot(
            Set<String> flags,
            String heldItemId,
            int heldItemCount,
            Map<String, Integer> inventoryCounts
    ) {
        public Snapshot {
            flags = flags == null ? Set.of() : Set.copyOf(flags);
            heldItemId = heldItemId == null ? "" : heldItemId;
            heldItemCount = Math.max(0, heldItemCount);
            inventoryCounts = inventoryCounts == null ? Map.of() : Map.copyOf(inventoryCounts);
        }

        public boolean hasFlag(String flag) {
            return flags.contains(flag);
        }

        public boolean holds(String itemId, int count) {
            return heldItemId.equals(itemId) && heldItemCount >= count;
        }

        public int inventoryCount(String itemId) {
            return Math.max(0, inventoryCounts.getOrDefault(itemId, 0));
        }
    }

    public record Decision(
            Outcome outcome,
            ItemCost cost,
            String flagToAdd,
            String questToRemove,
            String questToAdd,
            boolean grantClubCard,
            SoundCue soundCue,
            List<String> dialogueKeys
    ) {
        public static final Decision NO_ACTION = new Decision(
                Outcome.NO_ACTION,
                ItemCost.NONE,
                "",
                "",
                "",
                false,
                SoundCue.NONE,
                List.of()
        );

        public Decision {
            outcome = outcome == null ? Outcome.NO_ACTION : outcome;
            cost = cost == null ? ItemCost.NONE : cost;
            flagToAdd = flagToAdd == null ? "" : flagToAdd;
            questToRemove = questToRemove == null ? "" : questToRemove;
            questToAdd = questToAdd == null ? "" : questToAdd;
            soundCue = soundCue == null ? SoundCue.NONE : soundCue;
            dialogueKeys = dialogueKeys == null ? List.of() : List.copyOf(dialogueKeys);
        }
    }
}
