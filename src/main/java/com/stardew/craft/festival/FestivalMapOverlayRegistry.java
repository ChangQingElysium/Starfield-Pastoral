package com.stardew.craft.festival;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class FestivalMapOverlayRegistry {
    private static final Map<String, FestivalMapOverlayDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        register(new FestivalMapOverlayDefinition(
            "Town-EggFestival",
            "Town",
            new BlockPos(-38, 63, -23),
            "",
            "data/stardewcraft/structures/festivals/egg_festival_town.schem",
            new BlockPos(-38, 63, -23),
            new BlockPos(67, 68, 52),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Town-Fair",
            "Town",
            new BlockPos(-41, 49, -47),
            "",
            "data/stardewcraft/structures/festivals/stardew_valley_fair_town.schem",
            new BlockPos(-41, 49, -47),
            new BlockPos(80, 77, 57),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Town-Halloween",
            "Town",
            new BlockPos(-36, 63, -77),
            "",
            "data/stardewcraft/structures/festivals/spirit_eve_town.schem",
            new BlockPos(-36, 63, -77),
            new BlockPos(74, 71, 22),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Town-Christmas",
            "Town",
            new BlockPos(-31, 64, -26),
            "",
            "data/stardewcraft/structures/festivals/winter_star_town.schem",
            new BlockPos(-31, 64, -26),
            new BlockPos(41, 78, 25),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Forest-FlowerFestival",
            "Forest",
            new BlockPos(-270, 59, 90),
            "",
            "data/stardewcraft/structures/festivals/huawujie.schem",
            new BlockPos(-270, 59, 90),
            new BlockPos(-172, 74, 138),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Beach-Luau",
            "Beach",
            new BlockPos(30, 59, 88),
            "",
            "data/stardewcraft/structures/festivals/luau_beach.schem",
            new BlockPos(30, 59, 88),
            new BlockPos(90, 63, 130),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Beach-Jellies",
            "Beach",
            new BlockPos(20, 59, 95),
            "",
            "data/stardewcraft/structures/festivals/moonlight_jellies_beach.schem",
            new BlockPos(20, 59, 95),
            new BlockPos(92, 63, 166),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "DesertFestival",
            "Desert",
            new BlockPos(-273, 62, -228),
            "",
            "data/stardewcraft/structures/festivals/desert_festival.schem",
            new BlockPos(-273, 62, -228),
            new BlockPos(-181, 77, -135),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Forest-TroutDerby",
            "Forest",
            new BlockPos(-149, 64, 82),
            "",
            "data/stardewcraft/structures/festivals/trout_derby_forest.schem",
            new BlockPos(-149, 64, 82),
            new BlockPos(-136, 67, 88),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Beach-SquidFest",
            "Beach",
            new BlockPos(26, 59, 96),
            "",
            "data/stardewcraft/structures/festivals/squid_fest_beach.schem",
            new BlockPos(26, 59, 96),
            new BlockPos(43, 64, 101),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "BeachNightMarket",
            "Beach",
            new BlockPos(13, 40, 127),
            "",
            "data/stardewcraft/structures/festivals/night_market_beach.schem",
            new BlockPos(13, 40, 127),
            new BlockPos(127, 78, 180),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Forest-IceFestival",
            "Forest",
            new BlockPos(-192, 63, -2),
            "",
            "data/stardewcraft/structures/festivals/festival_of_ice_forest.schem",
            new BlockPos(-192, 63, -2),
            new BlockPos(-32, 73, 83),
            List.of(),
            true,
            true,
            true,
            new FestivalMapOverlayDefinition.TreeClearance(8, 48, 0)
        ));
    }

    private FestivalMapOverlayRegistry() {
    }

    public static void register(FestivalMapOverlayDefinition definition) {
        if (definition == null) {
            return;
        }
        DEFINITIONS.put(normalize(definition.overlayId()), definition);
    }

    public static Optional<FestivalMapOverlayDefinition> get(String overlayId) {
        if (overlayId == null || overlayId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DEFINITIONS.get(normalize(overlayId)));
    }

    public static boolean isRegistered(String overlayId) {
        return get(overlayId).isPresent();
    }

    public static Collection<FestivalMapOverlayDefinition> all() {
        return java.util.List.copyOf(DEFINITIONS.values());
    }

    private static String normalize(String overlayId) {
        return overlayId.toLowerCase(Locale.ROOT);
    }
}
