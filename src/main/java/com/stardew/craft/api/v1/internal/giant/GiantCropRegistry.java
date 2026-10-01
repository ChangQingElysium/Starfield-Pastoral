package com.stardew.craft.api.v1.internal.giant;

import com.stardew.craft.api.v1.agriculture.StardewGiantCrops.*;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.giant.GiantCropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Immutable produce index; registration never scans the world. */
public final class GiantCropRegistry {
    private static final Map<ResourceLocation, Definition> ADDONS = new HashMap<>();
    private static volatile Map<ResourceLocation, List<Definition>> byProduce;
    private GiantCropRegistry() {}

    public static synchronized void register(Definition definition) {
        if (definition.id().getNamespace().equals("stardewcraft")) throw new IllegalArgumentException("Reserved giant crop namespace");
        if (ADDONS.putIfAbsent(definition.id(), definition) != null) throw new IllegalStateException("Duplicate giant crop " + definition.id());
        byProduce = null;
    }

    public static List<Definition> definitions() {
        return index().values().stream().flatMap(Collection::stream).sorted(Comparator.comparing(d -> d.id().toString())).toList();
    }
    public static List<Definition> forProduce(ResourceLocation produce) { return index().getOrDefault(produce, List.of()); }

    private static Map<ResourceLocation, List<Definition>> index() {
        var current = byProduce;
        if (current != null) return current;
        synchronized (GiantCropRegistry.class) {
            if (byProduce != null) return byProduce;
            var all = new ArrayList<>(ADDONS.values());
            core(all, "cauliflower", (GiantCropBlock) ModBlocks.GIANT_CAULIFLOWER.get());
            core(all, "melon", (GiantCropBlock) ModBlocks.GIANT_MELON.get());
            core(all, "pumpkin", (GiantCropBlock) ModBlocks.GIANT_PUMPKIN.get());
            core(all, "powder_melon", (GiantCropBlock) ModBlocks.GIANT_POWDERMELON.get());
            core(all, "qi_fruit", (GiantCropBlock) ModBlocks.GIANT_QI_FRUIT.get());
            all.sort(Comparator.comparing(d -> d.id().toString()));
            var grouped = new HashMap<ResourceLocation, List<Definition>>();
            for (var definition : all) grouped.computeIfAbsent(definition.produce(), k -> new ArrayList<>()).add(definition);
            grouped.replaceAll((k, v) -> List.copyOf(v));
            return byProduce = Map.copyOf(grouped);
        }
    }

    private static void core(List<Definition> definitions, String produce, GiantCropBlock block) {
        definitions.add(new Definition(BuiltInRegistries.BLOCK.getKey(block), new ResourceLocation("stardewcraft", produce),
                3, 3, block.footprintHeight(), .01, true, (context, crops) -> {
            var cells = new ArrayList<Cell>();
            for (int y = 0; y < context.definition().height(); y++) for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++)
                cells.add(new Cell(new BlockPos(x, y, z), block.defaultBlockState().setValue(GiantCropBlock.PART,
                        x == 1 && y == 0 && z == 1 ? GiantCropBlock.Part.MAIN : GiantCropBlock.Part.EXTENSION)));
            return cells;
        }));
    }
}
