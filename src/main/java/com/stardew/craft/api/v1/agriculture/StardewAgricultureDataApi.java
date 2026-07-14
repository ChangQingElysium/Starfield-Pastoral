package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.data.StardewDataMaps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Dynamic provider layer over the crop, tree, animal and building Data Maps. */
public final class StardewAgricultureDataApi {
    private static final List<Registered<CropProvider>> CROPS = new ArrayList<>();
    private static final List<Registered<TreeProvider>> TREES = new ArrayList<>();
    private static final List<Registered<AnimalProvider>> ANIMALS = new ArrayList<>();
    private static final List<Registered<BuildingProvider>> BUILDINGS = new ArrayList<>();

    private StardewAgricultureDataApi() {
    }

    public static synchronized void registerCropProvider(ResourceLocation id, int priority, CropProvider provider) {
        register(CROPS, id, priority, provider);
    }

    public static synchronized void registerTreeProvider(ResourceLocation id, int priority, TreeProvider provider) {
        register(TREES, id, priority, provider);
    }

    public static synchronized void registerAnimalProvider(ResourceLocation id, int priority, AnimalProvider provider) {
        register(ANIMALS, id, priority, provider);
    }

    public static synchronized void registerBuildingProvider(ResourceLocation id, int priority, BuildingProvider provider) {
        register(BUILDINGS, id, priority, provider);
    }

    @Nullable
    public static synchronized StardewCropData crop(Level level, BlockPos pos, BlockState state) {
        for (Registered<CropProvider> entry : CROPS) {
            StardewCropData data = entry.provider().resolve(level, pos, state);
            if (data != null) return data;
        }
        return crop(state);
    }

    @Nullable
    public static StardewCropData crop(BlockState state) {
        return state.getBlock().builtInRegistryHolder().getData(StardewDataMaps.CROP_DATA);
    }

    @Nullable
    public static synchronized StardewTreeData tree(Level level, BlockPos pos, BlockState state) {
        for (Registered<TreeProvider> entry : TREES) {
            StardewTreeData data = entry.provider().resolve(level, pos, state);
            if (data != null) return data;
        }
        return state.getBlock().builtInRegistryHolder().getData(StardewDataMaps.TREE_DATA);
    }

    @Nullable
    public static synchronized StardewAnimalData animal(Entity entity) {
        for (Registered<AnimalProvider> entry : ANIMALS) {
            StardewAnimalData data = entry.provider().resolve(entity);
            if (data != null) return data;
        }
        return entity.getType().builtInRegistryHolder().getData(StardewDataMaps.ANIMAL_DATA);
    }

    @Nullable
    public static synchronized StardewBuildingData building(Level level, BlockPos pos, BlockState state) {
        for (Registered<BuildingProvider> entry : BUILDINGS) {
            StardewBuildingData data = entry.provider().resolve(level, pos, state);
            if (data != null) return data;
        }
        return state.getBlock().builtInRegistryHolder().getData(StardewDataMaps.BUILDING_DATA);
    }

    private static <T> void register(List<Registered<T>> entries, ResourceLocation id, int priority, T provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (entries.stream().anyMatch(entry -> entry.id().equals(id))) {
            throw new IllegalStateException("Agriculture provider already registered: " + id);
        }
        entries.add(new Registered<>(id, priority, provider));
        entries.sort(Comparator.comparingInt((Registered<T> entry) -> entry.priority()).reversed()
                .thenComparing(entry -> entry.id().toString()));
    }

    @FunctionalInterface public interface CropProvider {
        @Nullable StardewCropData resolve(Level level, BlockPos pos, BlockState state);
    }
    @FunctionalInterface public interface TreeProvider {
        @Nullable StardewTreeData resolve(Level level, BlockPos pos, BlockState state);
    }
    @FunctionalInterface public interface AnimalProvider {
        @Nullable StardewAnimalData resolve(Entity entity);
    }
    @FunctionalInterface public interface BuildingProvider {
        @Nullable StardewBuildingData resolve(Level level, BlockPos pos, BlockState state);
    }

    private record Registered<T>(ResourceLocation id, int priority, T provider) {
    }
}
