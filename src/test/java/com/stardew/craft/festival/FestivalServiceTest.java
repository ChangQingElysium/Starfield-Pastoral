package com.stardew.craft.festival;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
        assertTrue(FestivalRegistry.requiresPlayerContext(id("location")));
        assertFalse(FestivalRegistry.requiresPlayerContext(id("season")));
        assertFalse(FestivalRegistry.requiresPlayerContext(
                ResourceLocation.fromNamespaceAndPath("addon", "world_condition")));
    }

    @Test
    void bareAddonMechanicReferencesBelongToTheFestivalNamespace() {
        ResourceLocation addonFestival = ResourceLocation
                .fromNamespaceAndPath("orchard_addon", "apple_day");
        ResourceLocation coreFestival = ResourceLocation
                .fromNamespaceAndPath("stardewcraft", "spring13");

        assertEquals(
                "orchard_addon:apple_game",
                FestivalRegistry.normalizeOwnedReference(
                        addonFestival, "apple_game"));
        assertEquals(
                "shared:apple_game",
                FestivalRegistry.normalizeOwnedReference(
                        addonFestival, "shared:apple_game"));
        assertEquals(
                "egg_hunt",
                FestivalRegistry.normalizeOwnedReference(
                        coreFestival, "egg_hunt"));
    }

    @Test
    void festivalDefinitionsAndRuntimeViewsShareOneSnapshot() {
        String before = FestivalRegistry.getCachedJson();
        try {
            FestivalRegistry.applyFromJson("""
                    {
                      "festival_test:first": {
                        "type": "active",
                        "legacy_id": "legacy_first",
                        "display_name": "First",
                        "season": 0,
                        "start_day": 2,
                        "end_day": 2,
                        "start_time": 900,
                        "end_time": 1400,
                        "start_message_key": "first.started",
                        "world": {"location": "Town"}
                      }
                    }
                    """);
            assertCoherent(FestivalRegistry.catalog());
            assertTrue(FestivalRegistry.get(
                    "legacy_first").isPresent());

            FestivalRegistry.applyFromJson("""
                    {
                      "festival_test:second": {
                        "type": "passive",
                        "display_name": "Second",
                        "season": 1,
                        "start_day": 3,
                        "end_day": 3,
                        "start_time": 1000,
                        "end_time": 1500,
                        "start_message_key": "second.started",
                        "world": {"location": "Town"}
                      }
                    }
                    """);
            assertCoherent(FestivalRegistry.catalog());
            assertTrue(FestivalRegistry.get(
                    "legacy_first").isEmpty());
            assertEquals(1,
                    FestivalRegistry.passiveFestivals().size());
            assertTrue(FestivalRegistry.activeFestivals().isEmpty());
        } finally {
            FestivalRegistry.applyFromJson(before);
        }
    }

    private static void assertCoherent(
            FestivalRegistry.Catalog catalog
    ) {
        Set<ResourceLocation> definitionIds =
                catalog.definitions().definitions().keySet();
        Set<ResourceLocation> runtimeIds = catalog.ordered().stream()
                .map(FestivalDefinition::resourceId)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(definitionIds, runtimeIds);
        assertEquals(catalog.ordered().size(),
                catalog.activeOrdered().size()
                        + catalog.passiveOrdered().size());
        for (FestivalDefinition definition : catalog.ordered()) {
            assertEquals(definition,
                    catalog.aliases().get(
                            definition.resourceId()
                                    .toString()
                                    .toLowerCase(
                                            java.util.Locale.ROOT)));
        }
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
