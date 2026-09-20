package com.stardew.craft.interior.door;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Authored town doorway pairs read from pregen map version 17. */
public final class TownDoorDefinitions {
    private static final int ENTER_NORTH = -1;
    private static final int EXIT_SOUTH = 1;

    public record LegacyArea(BlockPos base, int height, int xSize, int zSize, String targetId) {
        public LegacyArea {
            base = base.immutable();
        }
    }

    public record Definition(int id, String name, List<BlockPos> outsideDoors, List<BlockPos> insideDoors,
                             LegacyArea outsideLegacy, LegacyArea insideLegacy) {
        public Definition {
            outsideDoors = outsideDoors.stream().map(BlockPos::immutable).toList();
            insideDoors = insideDoors.stream().map(BlockPos::immutable).toList();
        }

        public String enterTarget() { return outsideLegacy.targetId(); }
        public String exitTarget() { return insideLegacy.targetId(); }

        public boolean containsDoor(BlockPos pos) {
            BlockPos lower = pos.below();
            return outsideDoors.contains(pos) || outsideDoors.contains(lower)
                    || insideDoors.contains(pos) || insideDoors.contains(lower);
        }

        public boolean isOutsideDoor(BlockPos pos) {
            return outsideDoors.contains(pos) || outsideDoors.contains(pos.below());
        }

        /** Approximation used before chunks load; runtime replaces it with the actual open aperture. */
        public DoorConnection authoredConnection() {
            int leaves = Math.min(outsideDoors.size(), insideDoors.size());
            return new DoorConnection(center(outsideDoors), center(insideDoors), width(leaves),
                    ENTER_NORTH, EXIT_SOUTH);
        }
    }

    public static final List<Definition> ALL = List.of(
            pair(1, "pierre_house", row(27, 65, -8, 2), row(20, 36, -10, 2),
                    area(27, 65, -8, 2, 1, "pierre_house_enter"), area(20, 36, -11, 3, 3, "pierre_house_exit")),
            pair(2, "museum", row(124, 64, 41, 1), row(112, 38, 48, 1),
                    area(124, 64, 42, 2, 1, "museum_enter"), area(111, 38, 47, 3, 3, "museum_exit")),
            pair(3, "blacksmith", row(108, 64, 28, 1), row(107, 46, 31, 1),
                    area(108, 64, 29, 2, 1, "blacksmith_enter"), area(106, 46, 31, 3, 3, "blacksmith_exit")),
            pair(4, "saloon", row(29, 66, 13, 2), row(26, 36, 20, 2),
                    area(29, 66, 14, 2, 2, "saloon_enter"), area(25, 36, 19, 3, 3, "saloon_exit")),
            pair(5, "mayor_house", row(50, 66, 33, 2), row(54, 50, 31, 2),
                    area(50, 66, 34, 2, 2, "mayor_house_enter"), area(53, 50, 30, 3, 3, "mayor_house_exit")),
            pair(6, "clinic", row(13, 65, -9, 1), row(9, 43, -9, 1),
                    area(13, 65, -8, 2, 1, "clinic_enter"), area(8, 43, -10, 3, 3, "clinic_exit")),
            pair(7, "1_river_road", row(48, 64, 0, 1), row(48, 22, 6, 1),
                    area(48, 64, 1, 2, 1, "1_river_road_enter"), area(47, 22, 5, 3, 3, "1_river_road_exit")),
            pair(8, "carpenter_shop", row(28, 81, -115, 2), row(29, 51, -115, 2),
                    area(28, 85, -115, 2, 2, "carpenter_shop_enter"), area(29, 51, -116, 3, 3, "carpenter_shop_exit")),
            pair(9, "1_willow_lane", row(-29, 65, 38, 2), row(-29, 38, 45, 2),
                    area(-29, 65, 39, 2, 2, "1_willow_lane_enter"), area(-29, 38, 44, 3, 3, "1_willow_lane_exit")),
            pair(10, "2_willow_lane", row(-11, 64, 38, 2), row(-10, 24, 42, 2),
                    area(-11, 64, 39, 2, 2, "2_willow_lane_enter"), area(-11, 24, 41, 3, 3, "2_willow_lane_exit")),
            pair(11, "marnie_ranch", row(-92, 64, 20, 2), row(-87, 34, 27, 2),
                    area(-92, 64, 21, 2, 2, "marnie_ranch_enter"), area(-87, 34, 26, 3, 3, "marnie_ranch_exit")),
            pair(12, "leah_cottage", row(-83, 64, 51, 2), row(-83, 38, 53, 2),
                    area(-83, 64, 52, 2, 2, "leah_cottage_enter"), area(-84, 38, 52, 3, 3, "leah_cottage_exit")),
            pair(13, "fish_shop", row(64, 60, 149, 2), row(65, 31, 149, 2),
                    area(64, 60, 150, 2, 2, "fish_shop_enter"), area(64, 31, 148, 3, 3, "fish_shop_exit")),
            pair(14, "elliott_cabin", row(80, 60, 100, 2), row(75, 45, 104, 2),
                    area(80, 60, 101, 2, 2, "elliott_cabin_enter"), area(74, 45, 103, 3, 3, "elliott_cabin_exit")),
            pair(15, "wizard_tower", row(-179, 69, 49, 1), row(-178, 34, 65, 1),
                    area(-179, 69, 50, 2, 1, "wizard_tower_enter"), area(-179, 34, 64, 3, 3, "wizard_tower_exit")),
            pair(16, "oasis", row(-251, 64, -143, 1), row(-252, 30, -145, 1),
                    area(-251, 64, -142, 2, 1, "oasis_enter"), area(-253, 30, -146, 3, 3, "oasis_exit")),
            pair(17, "joja_mart", row(108, 65, -17, 2), row(108, 45, -16, 2),
                    area(108, 65, -17, 2, 2, "joja_mart_enter"), area(108, 45, -17, 3, 2, "joja_mart_exit")),
            pair(18, "trailer", row(72, 65, 8, 2), row(71, 35, 6, 2),
                    area(72, 64, 9, 2, 2, "trailer_enter"), area(71, 35, 5, 3, 3, "trailer_exit")),
            pair(19, "adventurer_guild", row(106, 81, -143, 1), row(106, 60, -142, 1),
                    area(106, 81, -142, 2, 1, "adventurer_guild_enter"),
                    area(105, 60, -142, 3, 3, "adventurer_guild_exit"))
    );

    // Mine, sewer, instanced farm/CC interiors, and non-door travel remain legacy too.

    private TownDoorDefinitions() {}

    public static boolean replacesLegacy(ResourceKey<Level> dimension, String targetId) {
        if (!"stardewcraft:stardew_valley".equals(dimension.location().toString())) return false;
        String normalized = targetId.startsWith("sdv_portal_target:")
                ? targetId.substring("sdv_portal_target:".length()) : targetId;
        return ALL.stream().anyMatch(pair -> pair.enterTarget().equals(normalized) || pair.exitTarget().equals(normalized));
    }

    private static Definition pair(int id, String name, List<BlockPos> outside, List<BlockPos> inside,
                                   LegacyArea outsideLegacy, LegacyArea insideLegacy) {
        return new Definition(id, name, outside, inside, outsideLegacy, insideLegacy);
    }

    private static LegacyArea area(int x, int y, int z, int height, int xSize, String target) {
        return new LegacyArea(new BlockPos(x, y, z), height, xSize, 1, target);
    }

    private static List<BlockPos> row(int x, int y, int z, int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(dx -> new BlockPos(x + dx, y, z)).toList();
    }

    private static Vec3 center(List<BlockPos> doors) {
        BlockPos first = doors.getFirst();
        BlockPos last = doors.getLast();
        return new Vec3((first.getX() + last.getX() + 1) * .5, first.getY() + 1, first.getZ() + .5);
    }

    private static double width(int leaves) {
        return leaves == 1 ? 13.0 / 16.0 : leaves - 6.0 / 16.0;
    }
}
