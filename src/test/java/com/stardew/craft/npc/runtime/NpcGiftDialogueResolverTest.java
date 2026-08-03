package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcGiftDialogueResolverTest {
    @Test
    void itemSpecificDialogueWinsOverTasteContextTag() {
        JsonObject dialogue = dialogue(
                "AcceptGift_(O)774", "exact",
                "AcceptGift_Positive_category_fish", "fish");

        assertEquals("exact", NpcInteractionService.resolveGiftDialogueText(
                dialogue, "AcceptGift", "774", Set.of("category_fish"),
                NpcInteractionService.GiftTaste.LIKED, 1));
    }

    @Test
    void positiveContextTagDialogueMatchesLikedFishAndGreens() {
        JsonObject dialogue = dialogue(
                "AcceptGift_Positive_category_fish", "fish",
                "AcceptGift_Positive_category_greens", "greens");

        assertEquals("fish", NpcInteractionService.resolveGiftDialogueText(
                dialogue, "AcceptGift", "145", Set.of("category_fish"),
                NpcInteractionService.GiftTaste.LIKED, 1));
        assertEquals("greens", NpcInteractionService.resolveGiftDialogueText(
                dialogue, "AcceptGift", "22", Set.of("category_greens"),
                NpcInteractionService.GiftTaste.NEUTRAL, 1));
    }

    @Test
    void preservesVanillaContextTagOrderAndFallsBackThroughTasteLabels() {
        JsonObject dialogue = dialogue(
                "AcceptGift_Liked_second", "second-liked",
                "AcceptGift_Positive_first", "first-positive",
                "AcceptGift_category_fish", "direct-tag",
                "AcceptGift_Liked", "liked");
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add("first");
        tags.add("second");
        tags.add("category_fish");

        assertEquals("first-positive", NpcInteractionService.resolveGiftDialogueText(
                dialogue, "AcceptGift", "145", tags,
                NpcInteractionService.GiftTaste.LIKED, 1));
    }

    private static JsonObject dialogue(String... keyValues) {
        JsonObject root = new JsonObject();
        JsonObject entries = new JsonObject();
        for (int index = 0; index < keyValues.length; index += 2) {
            entries.addProperty(keyValues[index], keyValues[index + 1]);
        }
        root.add("entries", entries);
        return root;
    }
}
