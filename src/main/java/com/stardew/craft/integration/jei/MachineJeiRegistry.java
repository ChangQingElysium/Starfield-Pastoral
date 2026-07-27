package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.machine.StardewMachineTypeRegistry;
import com.stardew.craft.api.v1.machine.StardewMachineType;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stable JEI descriptors for artisan machines. Categories must exist before server data sync. */
public final class MachineJeiRegistry {
    public enum Layout {
        STANDARD(64),
        AUXILIARY_INPUT(72),
        RANDOM_OUTPUT(84);

        private final int height;

        Layout(int height) {
            this.height = height;
        }

        public int height() {
            return height;
        }
    }

    public record Machine(
            ResourceLocation id,
            ResourceLocation itemId,
            RecipeType<ArtisanJeiRecipe> recipeType,
            Layout layout,
            boolean producesItem
    ) {
        public String translationKey() {
            return StardewMachineTypeRegistry.translationKey(id);
        }
    }

    private static final List<Machine> MACHINES = List.of(
            machine("charcoal_kiln", Layout.STANDARD, true),
            machine("cheese_press", Layout.STANDARD, true),
            machine("crystalarium", Layout.STANDARD, true),
            machine("dehydrator", Layout.STANDARD, true),
            machine("fish_smoker", Layout.AUXILIARY_INPUT, true),
            machine("furnace", Layout.STANDARD, true),
            machine("incubator", Layout.STANDARD, false),
            machine("keg", Layout.STANDARD, true),
            machine("loom", Layout.STANDARD, true),
            machine("mayonnaise_machine", Layout.STANDARD, true),
            machine("oil_maker", Layout.STANDARD, true),
            machine("preserves_jar", Layout.STANDARD, true),
            machine("seed_maker", Layout.RANDOM_OUTPUT, true)
    );

    private static volatile Catalog catalog;

    private MachineJeiRegistry() {
    }

    public static synchronized List<Machine> all() {
        return catalog().machines();
    }

    private static synchronized Catalog catalog() {
        Catalog current = catalog;
        if (current == null) {
            List<Machine> combined = new java.util.ArrayList<>(MACHINES);
            for (StardewMachineType definition
                    : StardewMachineTypeRegistry.freezeAndGetDefinitions()) {
                combined.add(fromAddonDefinition(definition));
            }
            List<Machine> machines = List.copyOf(combined);
            current = new Catalog(machines, buildIndex(machines));
            catalog = current;
        }
        return current;
    }

    public static Optional<Machine> find(ResourceLocation id) {
        return Optional.ofNullable(catalog().byId().get(id));
    }

    private static Machine machine(String path, Layout layout, boolean producesItem) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
        RecipeType<ArtisanJeiRecipe> recipeType = RecipeType.create(
                StardewCraft.MODID, "machine/" + path, ArtisanJeiRecipe.class);
        return new Machine(id, id, recipeType, layout, producesItem);
    }

    private static Machine fromAddonDefinition(StardewMachineType definition) {
        Layout layout = switch (definition.layout()) {
            case STANDARD -> Layout.STANDARD;
            case AUXILIARY_INPUT -> Layout.AUXILIARY_INPUT;
            case RANDOM_OUTPUT -> Layout.RANDOM_OUTPUT;
        };
        RecipeType<ArtisanJeiRecipe> recipeType = RecipeType.create(
                definition.id().getNamespace(),
                "machine/" + definition.id().getPath(),
                ArtisanJeiRecipe.class
        );
        return new Machine(
                definition.id(),
                definition.itemId(),
                recipeType,
                layout,
                definition.producesItem()
        );
    }

    private static Map<ResourceLocation, Machine> buildIndex(List<Machine> machines) {
        Map<ResourceLocation, Machine> result = new LinkedHashMap<>();
        for (Machine machine : machines) {
            Machine duplicate = result.put(machine.id(), machine);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate JEI machine descriptor: " + machine.id());
            }
        }
        return Map.copyOf(result);
    }

    private record Catalog(
            List<Machine> machines,
            Map<ResourceLocation, Machine> byId
    ) {
    }
}
