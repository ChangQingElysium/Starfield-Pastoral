package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
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
            return "stardewcraft.jei.machine." + id.getPath();
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

    private static final Map<ResourceLocation, Machine> BY_ID = buildIndex();

    private MachineJeiRegistry() {
    }

    public static List<Machine> all() {
        return MACHINES;
    }

    public static Optional<Machine> find(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    private static Machine machine(String path, Layout layout, boolean producesItem) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
        RecipeType<ArtisanJeiRecipe> recipeType = RecipeType.create(
                StardewCraft.MODID, "machine/" + path, ArtisanJeiRecipe.class);
        return new Machine(id, id, recipeType, layout, producesItem);
    }

    private static Map<ResourceLocation, Machine> buildIndex() {
        Map<ResourceLocation, Machine> result = new LinkedHashMap<>();
        for (Machine machine : MACHINES) {
            Machine duplicate = result.put(machine.id(), machine);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate JEI machine descriptor: " + machine.id());
            }
        }
        return Map.copyOf(result);
    }
}
