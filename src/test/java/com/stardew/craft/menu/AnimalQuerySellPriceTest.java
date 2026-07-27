package com.stardew.craft.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimalQuerySellPriceTest {
    @Test
    void quoteResolverUsesTheFriendshipValueProvidedAtTransactionTime() {
        assertEquals(
                450,
                AnimalQueryMenu.resolveBaseAnimalSellPrice(
                        "cow", 0)
        );
        assertEquals(
                1950,
                AnimalQueryMenu.resolveBaseAnimalSellPrice(
                        "cow", 1000)
        );
    }
}
