package com.stardew.craft.blockentity;

import com.stardew.craft.item.ModItems;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaskBlockEntityTest {
    @Test
    void retiredWineRegistryIdsRemainAgeable() {
        ItemStack legacyWine = new ItemStack(
                ModItems.LEGACY_FLAVORED_DRINKS.get("grape_wine").get());

        assertEquals(1f, CaskBlockEntity.resolveAgingRate(legacyWine));
    }
}
