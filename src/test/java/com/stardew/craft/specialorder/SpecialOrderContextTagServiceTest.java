package com.stardew.craft.specialorder;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.FlavoredArtisanDrinkItem;
import com.stardew.craft.item.artisan.PreserveType;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialOrderContextTagServiceTest {
    @Test
    void flavoredPotatoJuiceKeepsVanillaPreserveContextTags() {
        ItemStack juice = FlavoredArtisanDrinkItem.createFlavored(PreserveType.JUICE,
                new ItemStack(ModItems.POTATO.get()), new ItemStack(ModItems.JUICE.get()));

        var tags = SpecialOrderContextTagService.tagsFor(juice);

        assertTrue(tags.contains("drink_item"));
        assertTrue(tags.contains("juice_item"));
        assertTrue(tags.contains("preserve_sheet_index_192"));
    }
}
