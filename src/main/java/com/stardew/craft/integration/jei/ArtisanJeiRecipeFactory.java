package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.machine.StardewMachineTypeRegistry;
import com.stardew.craft.api.v1.internal.machine.StardewMachineRecipeDisplayRegistry;
import com.stardew.craft.api.v1.machine.StardewMachineRecipeDisplay;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.item.artisan.FlavoredArtisanOutputResolver;
import com.stardew.craft.item.artisan.PreserveType;
import com.stardew.craft.item.artisan.PreservesItem;
import com.stardew.craft.item.artisan.SeedMakerOutputResolver;
import com.stardew.craft.item.artisan.SmokedOutputResolver;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.item.catalog.StardewItemDisplayStacks;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Converts the runtime artisan recipe snapshot into complete JEI lookup recipes. */
public final class ArtisanJeiRecipeFactory {
    private static final ResourceLocation PRESERVES_JAR = id("preserves_jar");
    private static final ResourceLocation FISH_SMOKER = id("fish_smoker");

    private ArtisanJeiRecipeFactory() {
    }

    public static List<ArtisanJeiRecipe> build(MachineJeiRegistry.Machine machine) {
        ItemStack machineStack = itemStack(machine.itemId(), 1);
        if (machineStack.isEmpty()) {
            StardewCraft.LOGGER.warn("Skipping JEI machine {} because item {} is missing",
                    machine.id(), machine.itemId());
            return List.of();
        }

        List<ArtisanRecipeDataManager.Recipe> definitions =
                ArtisanRecipeDataManager.getRecipes(machine.id().toString());
        List<ArtisanJeiRecipe> result = new ArrayList<>();
        Set<Item> claimedInputs = new HashSet<>();

        for (ArtisanRecipeDataManager.Recipe definition : definitions) {
            if (isRoeDefinition(machine, definition)) {
                continue;
            }
            for (Item inputItem : candidateItems(definition)) {
                if (inputItem == Items.AIR || claimedInputs.contains(inputItem)) {
                    continue;
                }
                ArtisanJeiRecipe recipe = buildForItem(machine, definition, inputItem);
                if (recipe != null) {
                    result.add(recipe);
                    claimedInputs.add(inputItem);
                }
            }
        }

        if (PRESERVES_JAR.equals(machine.id())) {
            result.addAll(buildRoeRecipes(machine, definitions));
        }
        for (StardewMachineRecipeDisplay display
                : StardewMachineRecipeDisplayRegistry.displays(machine.id())) {
            result.add(fromAddonDisplay(machine, display));
        }
        return List.copyOf(result);
    }

    private static ArtisanJeiRecipe fromAddonDisplay(
            MachineJeiRegistry.Machine machine,
            StardewMachineRecipeDisplay display
    ) {
        List<ArtisanJeiRecipe.Input> inputs = display.inputs().stream()
                .map(input -> new ArtisanJeiRecipe.Input(
                        input.stacks(), input.count(), input.auxiliary()))
                .toList();
        List<ArtisanJeiRecipe.Output> outputs = display.outputs().stream()
                .map(output -> new ArtisanJeiRecipe.Output(
                        output.stacks(),
                        output.minCount(),
                        output.maxCount(),
                        output.chance()))
                .toList();
        return new ArtisanJeiRecipe(
                display.id(),
                machine,
                inputs,
                outputs,
                display.minutes(),
                display.keepInputQuality(),
                display.outputQuality()
        );
    }

    private static ArtisanJeiRecipe buildForItem(
            MachineJeiRegistry.Machine machine,
            ArtisanRecipeDataManager.Recipe definition,
            Item inputItem
    ) {
        List<ItemStack> inputStacks = displayStacks(inputItem, definition.consumeCount());
        if (inputStacks.isEmpty()) {
            return null;
        }

        List<ArtisanJeiRecipe.Input> inputs = new ArrayList<>();
        inputs.add(new ArtisanJeiRecipe.Input(inputStacks, definition.consumeCount(), false));
        if (FISH_SMOKER.equals(machine.id())) {
            inputs.add(new ArtisanJeiRecipe.Input(List.of(new ItemStack(Items.COAL)), 1, true));
        } else {
            for (var auxiliary : StardewMachineTypeRegistry.auxiliaryInputs(machine.id())) {
                if (!BuiltInRegistries.ITEM.containsKey(auxiliary.itemId())) {
                    continue;
                }
                Item auxiliaryItem = BuiltInRegistries.ITEM.get(auxiliary.itemId());
                List<ItemStack> auxiliaryStacks =
                        displayStacks(auxiliaryItem, auxiliary.count());
                if (!auxiliaryStacks.isEmpty()) {
                    inputs.add(new ArtisanJeiRecipe.Input(
                            auxiliaryStacks, auxiliary.count(), true));
                }
            }
        }

        List<ArtisanJeiRecipe.Output> outputs = machine.producesItem()
                ? resolveOutputs(definition, inputItem, inputStacks)
                : List.of();
        if (machine.producesItem() && outputs.isEmpty()) {
            StardewCraft.LOGGER.warn("Skipping JEI artisan recipe {} for {}: output could not be resolved",
                    definition.id(), inputItem);
            return null;
        }

        ResourceLocation inputId = BuiltInRegistries.ITEM.getKey(inputItem);
        ResourceLocation displayId = ResourceLocation.fromNamespaceAndPath(
                definition.id().getNamespace(), definition.id().getPath() + "/" + inputId.getPath());
        return new ArtisanJeiRecipe(displayId, machine, inputs, outputs, definition.minutes(),
                definition.keepInputQuality(), definition.outputQuality());
    }

    private static List<ArtisanJeiRecipe.Output> resolveOutputs(
            ArtisanRecipeDataManager.Recipe definition,
            Item inputItem,
            List<ItemStack> inputStacks
    ) {
        return switch (definition.outputMode()) {
            case FIXED -> fixedOutputs(definition, inputStacks);
            case COPY_INPUT -> List.of(new ArtisanJeiRecipe.Output(
                    withCount(inputStacks, Math.max(1, definition.outputCount())),
                    Math.max(1, definition.outputCount()), Math.max(1, definition.outputCount()), 1.0D));
            case SMOKED -> smokedOutputs(definition, inputItem, inputStacks);
            case SEEDMAKER -> seedMakerOutputs(definition, inputItem);
        };
    }

    private static List<ArtisanJeiRecipe.Output> fixedOutputs(
            ArtisanRecipeDataManager.Recipe definition,
            List<ItemStack> inputStacks
    ) {
        if (definition.outputId() == null) {
            return List.of();
        }
        int count = Math.max(1, definition.outputCount());

        if (definition.preserveType() != null) {
            List<ItemStack> flavored = new ArrayList<>();
            List<ItemStack> sources = keepsPreserveQuality(definition.preserveType())
                    ? inputStacks
                    : List.of(inputStacks.getFirst());
            for (ItemStack source : sources) {
                ItemStack output = itemStack(definition.outputId(), count);
                if (!output.isEmpty()) {
                    FlavoredArtisanOutputResolver.apply(definition.preserveType(), source, output);
                    flavored.add(output);
                }
            }
            return flavored.isEmpty() ? List.of() : List.of(
                    new ArtisanJeiRecipe.Output(flavored, count, count, 1.0D));
        }

        ItemStack baseOutput = itemStack(definition.outputId(), count);
        if (baseOutput.isEmpty()) {
            return List.of();
        }
        List<ItemStack> outputs = new ArrayList<>();
        if (definition.keepInputQuality()) {
            for (ItemStack input : inputStacks) {
                ItemStack output = baseOutput.copy();
                QualityHelper.setQuality(output, QualityHelper.getQuality(input));
                outputs.add(output);
            }
        } else {
            if (definition.outputQuality() >= 0) {
                QualityHelper.setQuality(baseOutput, definition.outputQuality());
            }
            outputs.add(baseOutput);
        }
        return List.of(new ArtisanJeiRecipe.Output(outputs, count, count, 1.0D));
    }

    private static List<ArtisanJeiRecipe.Output> smokedOutputs(
            ArtisanRecipeDataManager.Recipe definition,
            Item inputItem,
            List<ItemStack> inputStacks
    ) {
        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack input : inputStacks) {
            ItemStack output = SmokedOutputResolver.resolve(input);
            if (output.isEmpty()) {
                continue;
            }
            output.setCount(Math.max(1, definition.outputCount()));
            if (definition.keepInputQuality()) {
                QualityHelper.setQuality(output, QualityHelper.getQuality(input));
            } else if (definition.outputQuality() >= 0) {
                QualityHelper.setQuality(output, definition.outputQuality());
            }
            outputs.add(output);
        }
        int count = Math.max(1, definition.outputCount());
        return List.of(new ArtisanJeiRecipe.Output(outputs, count, count, 1.0D));
    }

    private static List<ArtisanJeiRecipe.Output> seedMakerOutputs(
            ArtisanRecipeDataManager.Recipe definition,
            Item inputItem
    ) {
        ArtisanRecipeDataManager.SeedMakerRule rule = definition.seedMakerRule();
        Item seedItem = SeedMakerOutputResolver.resolve(inputItem);
        if (rule == null || seedItem == null) {
            return List.of();
        }

        double ancientChance = rule.ancientChance();
        double mixedChance = (1.0D - ancientChance) * rule.mixedChance();
        double seedChance = (1.0D - ancientChance) * (1.0D - rule.mixedChance());
        return List.of(
                randomOutput(seedItem, rule.seedMin(), rule.seedMax(), seedChance),
                randomOutput(ModItems.MIXED_SEEDS.get(), rule.mixedMin(), rule.mixedMax(), mixedChance),
                randomOutput(ModItems.ANCIENT_FRUIT_SEEDS.get(), 1, 1, ancientChance)
        );
    }

    private static ArtisanJeiRecipe.Output randomOutput(
            Item item, int minCount, int maxCount, double chance
    ) {
        ItemStack stack = new ItemStack(item, Math.max(1, minCount));
        return new ArtisanJeiRecipe.Output(List.of(stack), minCount, maxCount, chance);
    }

    private static List<ArtisanJeiRecipe> buildRoeRecipes(
            MachineJeiRegistry.Machine machine,
            List<ArtisanRecipeDataManager.Recipe> definitions
    ) {
        ArtisanRecipeDataManager.Recipe aged = findPreserveRecipe(definitions, PreserveType.AGED_ROE);
        ArtisanRecipeDataManager.Recipe caviar = findPreserveRecipe(definitions, PreserveType.CAVIAR);
        if (aged == null || caviar == null) {
            return List.of();
        }

        List<ArtisanJeiRecipe> result = new ArrayList<>();
        for (ItemStack roe : StardewItemDisplayStacks.preserveVariants()) {
            if (roe.getItem() != ModItems.ROE.get()) {
                continue;
            }
            ResourceLocation sourceId = PreservesItem.getSourceItemId(roe);
            if (sourceId == null || !BuiltInRegistries.ITEM.containsKey(sourceId)) {
                continue;
            }
            boolean sturgeon = "sturgeon".equals(sourceId.getPath());
            ArtisanRecipeDataManager.Recipe definition = sturgeon ? caviar : aged;
            ItemStack source = new ItemStack(BuiltInRegistries.ITEM.get(sourceId));
            ItemStack output = itemStack(definition.outputId(), Math.max(1, definition.outputCount()));
            PreservesItem.createFlavored(definition.preserveType(), source, output);

            ResourceLocation displayId = ResourceLocation.fromNamespaceAndPath(
                    definition.id().getNamespace(), definition.id().getPath() + "/roe/" + sourceId.getPath());
            result.add(new ArtisanJeiRecipe(
                    displayId,
                    machine,
                    List.of(new ArtisanJeiRecipe.Input(List.of(roe), 1, false)),
                    List.of(new ArtisanJeiRecipe.Output(List.of(output), output.getCount(), output.getCount(), 1.0D)),
                    definition.minutes(),
                    definition.keepInputQuality(),
                    definition.outputQuality()
            ));
        }
        return result;
    }

    private static ArtisanRecipeDataManager.Recipe findPreserveRecipe(
            List<ArtisanRecipeDataManager.Recipe> definitions, PreserveType type
    ) {
        return definitions.stream().filter(recipe -> recipe.preserveType() == type).findFirst().orElse(null);
    }

    private static boolean isRoeDefinition(
            MachineJeiRegistry.Machine machine, ArtisanRecipeDataManager.Recipe definition
    ) {
        return PRESERVES_JAR.equals(machine.id())
                && definition.inputId() != null
                && definition.inputId().equals(BuiltInRegistries.ITEM.getKey(ModItems.ROE.get()));
    }

    private static List<Item> candidateItems(ArtisanRecipeDataManager.Recipe definition) {
        Set<Item> result = new LinkedHashSet<>();
        if (definition.inputId() != null && BuiltInRegistries.ITEM.containsKey(definition.inputId())) {
            result.add(BuiltInRegistries.ITEM.get(definition.inputId()));
        }
        if (definition.inputTag() != null) {
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(definition.inputTag())) {
                result.add(holder.value());
            }
        }
        if (definition.inputMode() != null
                && definition.inputMode() != ArtisanRecipeDataManager.InputMode.DEFAULT) {
            result.addAll(StardewItemCatalog.itemsForDynamicInput(definition.inputMode()));
        }
        return List.copyOf(result);
    }

    private static List<ItemStack> displayStacks(Item item, int count) {
        List<ItemStack> variants = StardewItemDisplayStacks.stacksForItem(item);
        if (variants.isEmpty()) {
            variants = List.of(new ItemStack(item));
        }
        return withCount(variants, Math.max(1, count));
    }

    private static List<ItemStack> withCount(List<ItemStack> stacks, int count) {
        return stacks.stream().map(stack -> {
            ItemStack copy = stack.copy();
            copy.setCount(Math.max(1, count));
            return copy;
        }).toList();
    }

    private static boolean keepsPreserveQuality(PreserveType type) {
        return type == PreserveType.DRIED_FRUIT || type == PreserveType.DRIED_MUSHROOMS;
    }

    private static ItemStack itemStack(ResourceLocation id, int count) {
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
    }
}
