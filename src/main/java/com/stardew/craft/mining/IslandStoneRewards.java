package com.stardew.craft.mining;

import com.stardew.craft.api.v1.world.StardewLocation;
import com.stardew.craft.api.v1.world.StardewLocations;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import java.util.Set;

/** Location rewards are separate from the appearance: placing a mussel stone in a mine does not make it an island. */
public final class IslandStoneRewards {
    private static final ResourceLocation ISLAND_TAG = ResourceLocation.fromNamespaceAndPath("stardewcraft", "ginger_island");
    private static final Set<String> ISLAND_LOCATIONS = Set.of("islandwest", "islandnorth", "islandsouth",
            "islandsoutheast", "islandsoutheastcave", "islandfarmcave", "islandsecret", "islandforestlocation",
            "islandhut", "islandwestcave1", "islandfieldoffice", "volcanodungeon", "caldera");
    private static final ResourceLocation VOLCANO_TAG = ResourceLocation.fromNamespaceAndPath("stardewcraft", "volcano_dungeon");
    private IslandStoneRewards() {}

    public static boolean isWest(StardewLocation location) {
        return nameMatches(location, Set.of("islandwest"));
    }

    public static boolean isIsland(StardewLocation location) {
        return location.hasTag(ISLAND_TAG) || isVolcano(location) || nameMatches(location, ISLAND_LOCATIONS);
    }

    public static boolean isVolcano(StardewLocation location) {
        return location.hasTag(VOLCANO_TAG) || nameMatches(location, Set.of("volcanodungeon"));
    }

    public static boolean rollVolcanoBat(String source, RandomSource globalRandom) {
        return (source.equals("845") || source.equals("846") || source.equals("847")) && globalRandom.nextDouble() < 0.005;
    }

    private static boolean nameMatches(StardewLocation location, Set<String> names) {
        if (names.contains(normalize(location.ledgerId()))) return true;
        if (location.id().getNamespace().equals("stardewcraft") && names.contains(normalize(location.id().getPath()))) return true;
        return location.aliases().stream().anyMatch(alias -> names.contains(normalize(alias)));
    }

    private static String normalize(String name) {
        return name.replace("_", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** IslandWest.breakStone runs this before GameLocation.breakStone, for every stone appearance. */
    public static void beforeNodeDrop(ServerLevel level, BlockPos pos, String source, boolean hasPlayer, RandomSource random) {
        if (StardewLocations.hierarchy(level.dimension().location(), pos).stream().anyMatch(IslandStoneRewards::isWest)
                && random.nextDouble() < (source.equals("25") ? 0.025 : 0.01)) {
            Block.popResource(level, pos, new ItemStack(ModItems.SNAKE_VERTEBRAE.get()));
        }
        // VolcanoDungeon.breakStone runs before the common node reward, and requires a credited player.
        if (hasPlayer && StardewLocations.hierarchy(level.dimension().location(), pos).stream().anyMatch(IslandStoneRewards::isVolcano)) {
            if (rollVolcanoBat(source, level.getRandom())) Block.popResource(level, pos, new ItemStack(ModItems.MUMMIFIED_BAT.get()));
            if (random.nextDouble() < 0.03 && GoldenWalnutData.at(level, pos).reserveVolcanoDrop()) {
                com.stardew.craft.gingerisland.WalnutDebris.drop(level, pos, 1);
            }
        }
    }

    public static void afterNodeDrop(ServerLevel level, BlockPos pos, String source, RandomSource random) {
        if (source.equals("25")
                && StardewLocations.hierarchy(level.dimension().location(), pos).stream().anyMatch(IslandStoneRewards::isIsland)
                && random.nextDouble() < 0.1 && GoldenWalnutData.at(level, pos).reserveMusselDrop()) {
            com.stardew.craft.gingerisland.WalnutDebris.drop(level, pos, 1);
        }
    }
}
