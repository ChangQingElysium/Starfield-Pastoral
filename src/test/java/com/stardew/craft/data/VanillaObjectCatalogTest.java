package com.stardew.craft.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaObjectCatalogTest {
    @Test
    void routesObjectsWithVanillaCollectionRules() {
        assertEquals(2, entry("100", "Arch", -9, false, false).collectionTab());
        assertEquals(1, entry("101", "Fish", -4, false, false).collectionTab());
        assertEquals(-1, entry("102", "Fish", -4, true, false).collectionTab());
        assertEquals(3, entry("103", "Basic", -2, false, false).collectionTab());
        assertEquals(4, entry("104", "Basic", -7, false, false).collectionTab());
        assertEquals(-1, entry("217", "Cooking", -7, false, false).collectionTab());
    }

    @Test
    void matchesVanillaBasicShippingExclusions() {
        assertTrue(entry("433", "Fish", -4, false, true).isPotentialBasicShipped());
        assertFalse(entry("100", "Basic", -74, false, false).isPotentialBasicShipped());
        assertFalse(entry("101", "Basic", 1, false, true).isPotentialBasicShipped());
        assertTrue(entry("102", "Basic", 1, false, false).isPotentialBasicShipped());
    }

    @Test
    void keepsVanillaProcessedGoodsInsertionOrder() {
        List<VanillaObjectCatalog.Entry> entries = new ArrayList<>(List.of(
                entry("350", "Basic", -26, false, false),
                entry("348", "Basic", -26, false, false),
                entry("344", "Basic", -26, false, false),
                entry("342", "Basic", -26, false, false)
        ));

        entries.sort(VanillaObjectCatalog.sourceOrder());

        assertEquals(List.of("342", "344", "348", "350"),
                entries.stream().map(VanillaObjectCatalog.Entry::key).toList());
    }

    @Test
    void mapsDynamicProcessedGoodsToTheirSingleVanillaObjects() {
        assertTrue(VanillaObjectCatalog.matchesItemId(
                entry("350", "Basic", -26, false, false), "stardewcraft:juice"));
        assertTrue(VanillaObjectCatalog.matchesItemId(
                entry("348", "Basic", -26, false, false), "stardewcraft:wine"));
        assertTrue(VanillaObjectCatalog.matchesItemId(
                entry("SmokedFish", "Basic", -26, false, false), "stardewcraft:smoked_eel"));
        assertFalse(VanillaObjectCatalog.matchesItemId(
                entry("348", "Basic", -26, false, false), "stardewcraft:melon_wine"));
        assertFalse(VanillaObjectCatalog.matchesItemId(
                entry("350", "Basic", -26, false, false), "stardewcraft:tomato"));
    }

    private static VanillaObjectCatalog.Entry entry(String key, String type, int category,
                                                     boolean excludeFishing, boolean excludeShipping) {
        return new VanillaObjectCatalog.Entry(
                key, switch (key) {
                    case "342" -> "Pickles";
                    case "344" -> "Jelly";
                    case "348" -> "Wine";
                    case "350" -> "Juice";
                    default -> key;
                },
                type, category, "Maps\\springobjects", key.chars().allMatch(Character::isDigit)
                        ? Integer.parseInt(key) : 0,
                excludeFishing, excludeShipping
        );
    }
}
