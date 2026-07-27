package com.stardew.craft.animal.model;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.stardew.craft.StardewCraft;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strict, all-or-nothing data-pack reload for farm animals and their building tiers.
 */
public final class AnimalDefinitionReloadListener
        extends SimplePreparableReloadListener<
        AnimalDefinitionReloadListener.Prepared> {
    private static final Gson GSON = new Gson();

    @Override
    protected Prepared prepare(
            ResourceManager manager,
            ProfilerFiller profiler
    ) {
        try {
            return new Prepared(
                    loadStrict(
                            manager,
                            FarmAnimalDefinitions.DATA_DIRECTORY),
                    loadStrict(
                            manager,
                            AnimalBuildingTierDefinitions.DATA_DIRECTORY),
                    null
            );
        } catch (RuntimeException exception) {
            return new Prepared(Map.of(), Map.of(), exception);
        }
    }

    @Override
    protected void apply(
            Prepared prepared,
            ResourceManager manager,
            ProfilerFiller profiler
    ) {
        FarmAnimalDefinitions.Snapshot currentAnimals =
                FarmAnimalDefinitions.currentSnapshot();
        AnimalBuildingTierDefinitions.Snapshot currentBuildings =
                AnimalBuildingTierDefinitions.currentSnapshot();
        if (prepared.failure() != null) {
            logRejected(
                    currentAnimals,
                    currentBuildings,
                    prepared.failure());
            return;
        }
        try {
            long generation = Math.max(
                    currentAnimals.generation(),
                    currentBuildings.generation()) + 1L;
            FarmAnimalDefinitions.Snapshot animals =
                    FarmAnimalDefinitions.decodeSnapshot(
                            prepared.animals(), generation);
            AnimalBuildingTierDefinitions.Snapshot buildings =
                    AnimalBuildingTierDefinitions.decodeSnapshot(
                            prepared.buildings(), generation);
            FarmAnimalDefinitions.validateRuntimeReferences(
                    animals);
            AnimalBuildingTierDefinitions.validateRuntimeReferences(
                    buildings);
            AnimalDefinitionSnapshot.validateCrossReferences(
                    animals, buildings);
            AnimalDefinitionSnapshot.publish(
                    animals, buildings);
            StardewCraft.LOGGER.info(
                    "[ANIMAL_DATA] Atomically published {} animal definitions and {} building tiers at generation {}",
                    animals.byAnimalType().size(),
                    buildings.byKey().size(),
                    generation);
        } catch (RuntimeException exception) {
            logRejected(
                    currentAnimals,
                    currentBuildings,
                    exception);
        }
    }

    private static Map<ResourceLocation, JsonElement> loadStrict(
            ResourceManager manager,
            String directory
    ) {
        FileToIdConverter converter =
                FileToIdConverter.json(directory);
        LinkedHashMap<ResourceLocation, JsonElement> result =
                new LinkedHashMap<>();
        converter.listMatchingResources(manager).entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> readResource(
                        converter,
                        entry.getKey(),
                        entry.getValue(),
                        result));
        return Map.copyOf(result);
    }

    private static void readResource(
            FileToIdConverter converter,
            ResourceLocation fileId,
            Resource resource,
            Map<ResourceLocation, JsonElement> output
    ) {
        ResourceLocation dataId =
                converter.fileToId(fileId);
        try (Reader reader = resource.openAsReader()) {
            JsonElement parsed = GsonHelper.fromJson(
                    GSON, reader, JsonElement.class);
            if (parsed == null) {
                throw new JsonParseException(
                        "JSON root is null");
            }
            if (output.put(dataId, parsed) != null) {
                throw new IllegalStateException(
                        "Duplicate data resource " + dataId);
            }
        } catch (IOException | JsonParseException exception) {
            throw new IllegalArgumentException(
                    "Could not parse data resource "
                            + dataId + " from " + fileId,
                    exception);
        }
    }

    private static void logRejected(
            FarmAnimalDefinitions.Snapshot animals,
            AnimalBuildingTierDefinitions.Snapshot buildings,
            RuntimeException exception
    ) {
        StardewCraft.LOGGER.error(
                "[ANIMAL_DATA] Combined reload rejected; keeping animal generation {} and building generation {}",
                animals.generation(),
                buildings.generation(),
                exception);
    }

    record Prepared(
            Map<ResourceLocation, JsonElement> animals,
            Map<ResourceLocation, JsonElement> buildings,
            RuntimeException failure
    ) {
    }
}
