package com.stardew.craft.npc.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcFriendshipRecipeMailServiceTest {

    @Test
    void sourceOrderAllowsOnlyOnePendingRecipeLetterPerNpc() {
        var first = new NpcFriendshipRecipeMailService.RecipeMailRule(
                "linus", 7, "fish_taco", "linusFish");
        var second = new NpcFriendshipRecipeMailService.RecipeMailRule(
                "linus", 3, "sashimi", "linusSashimi");

        assertEquals(List.of(first),
                NpcFriendshipRecipeMailService.selectDailyLetters(
                        List.of(first, second),
                        ignored -> 2500,
                        ignored -> false,
                        ignored -> false));
    }

    @Test
    void unreadLetterBlocksTheNextRecipeForTheSameNpc() {
        var first = new NpcFriendshipRecipeMailService.RecipeMailRule(
                "linus", 7, "fish_taco", "linusFish");
        var second = new NpcFriendshipRecipeMailService.RecipeMailRule(
                "linus", 3, "sashimi", "linusSashimi");

        assertEquals(List.of(),
                NpcFriendshipRecipeMailService.selectDailyLetters(
                        List.of(first, second),
                        ignored -> 2500,
                        ignored -> false,
                        Set.of("linusFish")::contains));
    }

    @Test
    void learnedEarlierRecipeFallsThroughToTheNextSourceEntry() {
        var first = new NpcFriendshipRecipeMailService.RecipeMailRule(
                "linus", 7, "fish_taco", "linusFish");
        var second = new NpcFriendshipRecipeMailService.RecipeMailRule(
                "linus", 3, "sashimi", "linusSashimi");

        assertEquals(List.of(second),
                NpcFriendshipRecipeMailService.selectDailyLetters(
                        List.of(first, second),
                        ignored -> 2500,
                        Set.of("fish_taco")::contains,
                        ignored -> false));
    }
}
