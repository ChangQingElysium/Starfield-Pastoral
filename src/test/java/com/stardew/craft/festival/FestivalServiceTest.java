package com.stardew.craft.festival;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FestivalServiceTest {
    @Test
    void activeAndPassiveCandidatesUseTheSameAvailabilityFilter() {
        FestivalDefinition unavailableActive = festival("addon:z_active", "z_active", FestivalType.ACTIVE);
        FestivalDefinition availableActive = festival("addon:a_active", "a_active", FestivalType.ACTIVE);
        FestivalDefinition unavailablePassive = festival("addon:z_passive", "z_passive", FestivalType.PASSIVE);
        FestivalDefinition availablePassive = festival("addon:a_passive", "a_passive", FestivalType.PASSIVE);

        List<FestivalDefinition> active = FestivalService.availableFestivalsForDate(
                List.of(unavailableActive, availableActive), 13, FestivalRegistry.SPRING,
                definition -> definition == availableActive);
        List<FestivalDefinition> passive = FestivalService.availableFestivalsForDate(
                List.of(unavailablePassive, availablePassive), 13, FestivalRegistry.SPRING,
                definition -> definition == availablePassive);

        assertEquals(List.of(availableActive), active);
        assertEquals(List.of(availablePassive), passive);
    }

    @Test
    void sameDayFestivalsHaveAStableNamespacedIdOrder() {
        FestivalDefinition later = festival("addon:z_festival", "z_festival", FestivalType.ACTIVE);
        FestivalDefinition earlier = festival("addon:a_festival", "a_festival", FestivalType.ACTIVE);

        assertEquals(List.of(earlier, later), FestivalService.availableFestivalsForDate(
                List.of(later, earlier), 13, FestivalRegistry.SPRING, definition -> true));
    }

    @Test
    void builtInPlayerConditionsAreRejectedForWorldScopedFestivalAvailability() {
        assertTrue(FestivalRegistry.requiresPlayerContext(id("money")));
        assertTrue(FestivalRegistry.requiresPlayerContext(id("has_item")));
        assertTrue(FestivalRegistry.requiresPlayerContext(id("flag")));
        assertTrue(FestivalRegistry.requiresPlayerContext(id("skill")));
        assertFalse(FestivalRegistry.requiresPlayerContext(id("season")));
        assertFalse(FestivalRegistry.requiresPlayerContext(
                ResourceLocation.fromNamespaceAndPath("addon", "world_condition")));
    }

    private static FestivalDefinition festival(String resourceId, String legacyId, FestivalType type) {
        return new FestivalDefinition(
                ResourceLocation.parse(resourceId), legacyId, type, legacyId, "", "", List.of(),
                FestivalRegistry.SPRING, 13, 13, 900, 1400, true, false,
                "", "", "", "Town", "", Map.of(), List.of(), "");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("stardewcraft", path);
    }
}
