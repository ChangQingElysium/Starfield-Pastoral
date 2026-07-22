package com.stardew.craft.item.tool;

import com.stardew.craft.item.ModItems;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingRodItemTemporaryOwnerTest {

    @Test
    void temporaryFestivalRodCanOnlyBeClaimedByItsOwner() {
        ItemStack rod = new ItemStack(ModItems.FISHING_ROD.get());

        FishingRodItem.configureFairTemporaryRod(
            rod,
            "fall16_fishing_game",
            ItemStack.EMPTY,
            ItemStack.EMPTY
        );

        assertTrue(FishingRodItem.isFairTemporaryRod(rod));
        assertTrue(FishingRodItem.isFairTemporaryRod(rod, "fall16_fishing_game"));
        assertFalse(FishingRodItem.isFairTemporaryRod(rod, "winter8_ice_fishing"));
    }
}
