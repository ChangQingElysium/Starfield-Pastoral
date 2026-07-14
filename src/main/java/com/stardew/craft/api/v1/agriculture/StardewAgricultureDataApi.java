package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.data.StardewDataMaps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dynamic provider layer over the crop, tree, animal and building Data Maps. */
public final class StardewAgricultureDataApi {
    private static final Map<ResourceLocation, Registered<CropProvider>> CROPS = new HashMap<>();
    private static final Map<ResourceLocation, Registered<TreeProvider>> TREES = new HashMap<>();
    private static final Map<ResourceLocation, Registered<AnimalProvider>> ANIMALS = new HashMap<>();
    private static final Map<ResourceLocation, Registered<BuildingProvider>> BUILDINGS = new HashMap<>();
    private static volatile List<Registered<CropProvider>> cropSnapshot = List.of();
    private static volatile List<Registered<TreeProvider>> treeSnapshot = List.of();
    private static volatile List<Registered<AnimalProvider>> animalSnapshot = List.of();
    private static volatile List<Registered<BuildingProvider>> buildingSnapshot = List.of();

    private StardewAgricultureDataApi() {
    }

    public static synchronized void registerCropProvider(ResourceLocation id, int priority, CropProvider provider) {
        cropSnapshot = register(CROPS, id, priority, provider);
    }

    public static synchronized void registerTreeProvider(ResourceLocation id, int priority, TreeProvider provider) {
        treeSnapshot = register(TREES, id, priority, provider);
    }

    public static synchronized void registerAnimalProvider(ResourceLocation id, int priority, AnimalProvider provider) {
        animalSnapshot = register(ANIMALS, id, priority, provider);
    }

    public static synchronized void registerBuildingProvider(ResourceLocation id, int priority, BuildingProvider provider) {
        buildingSnapshot = register(BUILDINGS, id, priority, provider);
    }

    @Nullable
    public static StardewCropData crop(Level level, BlockPos pos, BlockState state) {
        for (Registered<CropProvider> entry : cropSnapshot) {
            try {
                StardewCropData data = entry.provider().resolve(level, pos, state);
                if (data != null) return data;
            } catch (RuntimeException exception) {
                logFailure("crop", entry.id(), BuiltInRegistries.BLOCK.getKey(state.getBlock()), exception);
            }
        }
        return crop(state);
    }

    @Nullable
    public static StardewCropData crop(BlockState state) {
        return state.getBlock().builtInRegistryHolder().getData(StardewDataMaps.CROP_DATA);
    }

    @Nullable
    public static StardewTreeData tree(Level level, BlockPos pos, BlockState state) {
        for (Registered<TreeProvider> entry : treeSnapshot) {
            try {
                StardewTreeData data = entry.provider().resolve(level, pos, state);
                if (data != null) return data;
            } catch (RuntimeException exception) {
                logFailure("tree", entry.id(), BuiltInRegistries.BLOCK.getKey(state.getBlock()), exception);
            }
        }
        return state.getBlock().builtInRegistryHolder().getData(StardewDataMaps.TREE_DATA);
    }

    @Nullable
    public static StardewAnimalData animal(Entity entity) {
        for (Registered<AnimalProvider> entry : animalSnapshot) {
            try {
                StardewAnimalData data = entry.provider().resolve(entity);
                if (data != null) return data;
            } catch (RuntimeException exception) {
                logFailure("animal", entry.id(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), exception);
            }
        }
        return entity.getType().builtInRegistryHolder().getData(StardewDataMaps.ANIMAL_DATA);
    }

    @Nullable
    public static StardewBuildingData building(Level level, BlockPos pos, BlockState state) {
        for (Registered<BuildingProvider> entry : buildingSnapshot) {
            try {
                StardewBuildingData data = entry.provider().resolve(level, pos, state);
                if (data != null) return data;
            } catch (RuntimeException exception) {
                logFailure("building", entry.id(), BuiltInRegistries.BLOCK.getKey(state.getBlock()), exception);
            }
        }
        return state.getBlock().builtInRegistryHolder().getData(StardewDataMaps.BUILDING_DATA);
    }

    private static <T> List<Registered<T>> register(Map<ResourceLocation, Registered<T>> entries,
                                                    ResourceLocation id, int priority, T provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (entries.containsKey(id)) {
            throw new IllegalStateException("Agriculture provider already registered: " + id);
        }
        entries.put(id, new Registered<>(id, priority, provider));
        ArrayList<Registered<T>> sorted = new ArrayList<>(entries.values());
        sorted.sort(Comparator.comparingInt((Registered<T> entry) -> entry.priority()).reversed()
                .thenComparing(entry -> entry.id().toString()));
        return List.copyOf(sorted);
    }

    private static void logFailure(String kind, ResourceLocation providerId, ResourceLocation targetId,
                                   RuntimeException exception) {
        StardewCraft.LOGGER.error("Stardew agriculture {} provider {} failed for {}",
                kind, providerId, targetId, exception);
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
