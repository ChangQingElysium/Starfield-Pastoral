package com.stardew.craft.client.model.terrain;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.desert.DesertConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Texture selection only: never changes saved variants, block states or placement weights. */
public final class TerrainSeasonTextures {
    private static final String[] GRASS = {"grass_block", "grass_blades", "grass_flowers"};
    private static final String[] DIRT = {"dirt", "dirt_tuft", "dirt_impression", "dirt_marks", "dirt_stones"};
    private static volatile int currentSeason = -1;

    private TerrainSeasonTextures() {}

    public static int textureSet(int season) {
        return season >= 0 && season < 4 ? season : 0;
    }

    public static String directory(int season) {
        return switch (textureSet(season)) {
            case 1 -> "summer/";
            case 2 -> "fall/";
            case 3 -> "winter/";
            default -> "";
        };
    }

    public static String farmlandDirectory(int season) {
        return "block/terrain/farmland/" + (textureSet(season) == 0 ? "spring/" : directory(season));
    }

    public static String farmlandDirectory(int season, boolean sandy) {
        return sandy ? farmlandDirectory(season).replace("/farmland/", "/sandy_farmland/") : farmlandDirectory(season);
    }

    public static String farmlandDirectory(int season, int family) {
        return family == 2 ? "block/terrain/hard_soil/" + new String[]{"spring", "summer", "fall", "winter"}[textureSet(season)] + "/"
                : farmlandDirectory(season, family == 1);
    }

    public static int currentTextureSet() {
        return textureSet(currentSeason);
    }

    public static int sandTextureSet(int season, ResourceKey<Level> dimension, BlockPos pos) {
        return DesertConstants.isInDesertRegion(dimension, pos) ? 0 : textureSet(season);
    }

    public static int textureSetAt(int season, ResourceKey<Level> dimension, BlockPos pos, BlockState state) {
        return state.is(ModBlocks.SAND.get()) || state.is(ModBlocks.SANDY_FARMLAND.get())
                ? sandTextureSet(season, dimension, pos) : textureSet(season);
    }

    /** Returns true once per actual calendar change, not on every time-sync packet. */
    public static boolean updateSeason(int season) {
        if (currentSeason == season) return false;
        currentSeason = season;
        return true;
    }

    public static String modelPath(int season, String block, int variant, boolean snowy) {
        if (block.equals("cliff")) return "block/cliff/"
                + new String[]{"spring", "summer", "fall", "winter"}[textureSet(season)] + "/"
                + new String[]{"plain", "broad", "split", "plain_moss", "broad_moss", "split_moss"}[variant];
        if (block.equals("sand")) return "block/terrain/sand/"
                + new String[]{"spring", "summer", "fall", "winter"}[textureSet(season)] + "/sand";
        if (block.equals("hard_soil")) return farmlandDirectory(season, 2) + "hard_soil";
        if (block.equals("infertile_farmland")) return farmlandDirectory(season, 2) + (variant > 0 ? "wet" : "dry");
        if (block.equals("sandy_farmland")) return farmlandDirectory(season, true) + (variant > 0 ? "wet" : "dry");
        if (block.equals("farmland")) return farmlandDirectory(season) + (variant > 0 ? "wet" : "dry");
        String name = switch (block) {
            case "grass_block" -> GRASS[variant];
            case "dirt" -> DIRT[variant];
            case "dark_grass_block" -> "dark_grass_block";
            default -> throw new IllegalArgumentException("Not an authored terrain block: " + block);
        };
        if (snowy && !block.equals("dirt")) name += "_snow";
        return "block/terrain/" + directory(season) + name;
    }
}
