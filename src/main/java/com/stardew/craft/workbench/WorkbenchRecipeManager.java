package com.stardew.craft.workbench;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * Builds and caches the recipe list for wood and stone workbenches.
 * Recipes are rebuilt when tags are synced (TagsUpdatedEvent on both sides).
 */
public final class WorkbenchRecipeManager {

    private static final Map<WorkbenchType, List<WorkbenchEntry>> CACHE = new EnumMap<>(WorkbenchType.class);

    private WorkbenchRecipeManager() {}

    public static void invalidate() {
        CACHE.clear();
    }

    public static List<WorkbenchEntry> getRecipes(WorkbenchType type) {
        return CACHE.computeIfAbsent(type, WorkbenchRecipeManager::build);
    }

    public static WorkbenchEntry findEntry(WorkbenchType type, ResourceLocation itemId) {
        for (WorkbenchEntry e : getRecipes(type)) {
            if (e.itemId().equals(itemId)) return e;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────
    private static List<WorkbenchEntry> build(WorkbenchType type) {
        return switch (type) {
            case WOOD  -> buildWood();
            case STONE -> buildStone();
            case TEMPLATE -> TemplateWorkbenchRecipes.build();
        };
    }

    // =====================================================================
    // WOOD WORKBENCH — tag-driven
    // =====================================================================
    private static List<WorkbenchEntry> buildWood() {
        List<WorkbenchEntry> list = new ArrayList<>();

        // Logs (including stripped)
            addFromTag(list, "minecraft:logs", "logs", 5, 2, null);

        // Wood blocks (6-face bark): oak_wood, birch_wood, etc.
            addHardcoded(list, "wood", 5, 2,
            "minecraft:oak_wood", "minecraft:spruce_wood", "minecraft:birch_wood",
            "minecraft:jungle_wood", "minecraft:acacia_wood", "minecraft:dark_oak_wood",
            "minecraft:mangrove_wood", "minecraft:cherry_wood",
            "minecraft:stripped_oak_wood", "minecraft:stripped_spruce_wood",
            "minecraft:stripped_birch_wood", "minecraft:stripped_jungle_wood",
            "minecraft:stripped_acacia_wood", "minecraft:stripped_dark_oak_wood",
            "minecraft:stripped_mangrove_wood", "minecraft:stripped_cherry_wood"
        );

        // Planks
            addFromTag(list, "minecraft:planks", "planks", 2, 2, null);

        // Wooden slabs
            addFromTag(list, "minecraft:wooden_slabs", "slabs", 1, 2, null);

        // Wooden stairs
            addFromTag(list, "minecraft:wooden_stairs", "stairs", 3, 2, null);

        // Wooden fences
            addFromTag(list, "minecraft:wooden_fences", "fences", 1, 2, null);

        // Fence gates
            addFromTag(list, "minecraft:fence_gates", "fence_gates", 2, 2, null);

        // Wooden doors
            addFromTag(list, "minecraft:wooden_doors", "doors", 4, 2, null);

        // Wooden trapdoors
            addFromTag(list, "minecraft:wooden_trapdoors", "trapdoors", 3, 2, null);

        // Wooden buttons
        addFromTag(list, "minecraft:wooden_buttons", "buttons", 1, 2, null);

        // Wooden pressure plates
        addFromTag(list, "minecraft:wooden_pressure_plates", "pressure_plates", 1, 1, null);

        deduplicate(list);
        sort(list);
        return list;
    }

    // =====================================================================
    // STONE WORKBENCH — mostly hardcoded
    // =====================================================================
    private static List<WorkbenchEntry> buildStone() {
        List<WorkbenchEntry> list = new ArrayList<>();

        // T1 — cost 1
        addHardcoded(list, "basic", 1, 1,
            "minecraft:cobblestone", "minecraft:mossy_cobblestone"
        );

        // T2 — cost 2
           addHardcoded(list, "standard", 2, 2,
            "minecraft:stone",
            "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
            "minecraft:tuff",
            "minecraft:sandstone", "minecraft:red_sandstone",
            "minecraft:mud_bricks"
        );

        // T3 — cost 3
           addHardcoded(list, "refined", 3, 2,
            "minecraft:polished_andesite", "minecraft:polished_diorite", "minecraft:polished_granite",
            "minecraft:polished_tuff",
            "minecraft:smooth_stone",
            "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks",
            "minecraft:deepslate", "minecraft:polished_deepslate",
            "minecraft:smooth_sandstone", "minecraft:smooth_red_sandstone",
            "minecraft:cut_sandstone", "minecraft:cut_red_sandstone",
            "minecraft:bricks"
        );

        // T4 — cost 4
        addHardcoded(list, "advanced", 4, 1,
            "minecraft:chiseled_stone_bricks",
            "minecraft:deepslate_bricks", "minecraft:deepslate_tiles"
        );

        // T5 — cost 5
        addHardcoded(list, "ornate", 5, 1,
            "minecraft:chiseled_deepslate"
        );

        // Stone slabs — scan tag, filter stone-type
        addStoneSlab(list);

        // Stone stairs — scan tag, filter stone-type
        addStoneStairs(list);

        // Walls
            addFromTag(list, "minecraft:walls", "walls", 2, 2, null);

        // Stone buttons
        addHardcoded(list, "buttons", 1, 2,
            "minecraft:stone_button", "minecraft:polished_blackstone_button"
        );

        // Stone pressure plates
        addHardcoded(list, "pressure_plates", 1, 1,
            "minecraft:stone_pressure_plate",
            "minecraft:polished_blackstone_pressure_plate",
            "minecraft:heavy_weighted_pressure_plate",
            "minecraft:light_weighted_pressure_plate"
        );

        deduplicate(list);
        sort(list);
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private static void addFromTag(List<WorkbenchEntry> list, String tagId,
                                    String category, int cost, int outputCount,
                                    Set<String> exclude) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(tagId));
        BuiltInRegistries.ITEM.stream()
            .filter(item -> item != Items.AIR)
            .filter(item -> {
                try { return BuiltInRegistries.ITEM.wrapAsHolder(item).is(tag); }
                catch (Exception e) { return false; }
            })
            .forEach(item -> {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (exclude != null && exclude.contains(id.toString())) return;
                list.add(new WorkbenchEntry(id, category, cost, outputCount, id.getNamespace()));
            });
    }

    private static void addHardcoded(List<WorkbenchEntry> list, String category, int cost, int outputCount, String... itemIds) {
        for (String id : itemIds) {
            ResourceLocation rl = new ResourceLocation(id);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != null && item != Items.AIR) {
                list.add(new WorkbenchEntry(rl, category, cost, outputCount, rl.getNamespace()));
            }
        }
    }

    /** Stone slabs: items in #minecraft:slabs that are NOT wooden */
    private static void addStoneSlab(List<WorkbenchEntry> list) {
        TagKey<Item> slabTag = TagKey.create(Registries.ITEM, new ResourceLocation("minecraft:slabs"));
        TagKey<Item> woodenSlabTag = TagKey.create(Registries.ITEM, new ResourceLocation("minecraft:wooden_slabs"));
        BuiltInRegistries.ITEM.stream()
            .filter(item -> item != Items.AIR)
            .filter(item -> {
                try {
                    return BuiltInRegistries.ITEM.wrapAsHolder(item).is(slabTag)
                        && !BuiltInRegistries.ITEM.wrapAsHolder(item).is(woodenSlabTag);
                } catch (Exception e) { return false; }
            })
            .forEach(item -> {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                list.add(new WorkbenchEntry(id, "slabs", 1, 2, id.getNamespace()));
            });
    }

    /** Stone stairs: items in #minecraft:stairs that are NOT wooden */
    private static void addStoneStairs(List<WorkbenchEntry> list) {
        TagKey<Item> stairTag = TagKey.create(Registries.ITEM, new ResourceLocation("minecraft:stairs"));
        TagKey<Item> woodenStairTag = TagKey.create(Registries.ITEM, new ResourceLocation("minecraft:wooden_stairs"));
        BuiltInRegistries.ITEM.stream()
            .filter(item -> item != Items.AIR)
            .filter(item -> {
                try {
                    return BuiltInRegistries.ITEM.wrapAsHolder(item).is(stairTag)
                        && !BuiltInRegistries.ITEM.wrapAsHolder(item).is(woodenStairTag);
                } catch (Exception e) { return false; }
            })
            .forEach(item -> {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                list.add(new WorkbenchEntry(id, "stairs", 2, 2, id.getNamespace()));
            });
    }

    /** Remove duplicates (keep first occurrence) */
    private static void deduplicate(List<WorkbenchEntry> list) {
        Set<ResourceLocation> seen = new HashSet<>();
        list.removeIf(e -> !seen.add(e.itemId()));
    }

    /** Sort: minecraft first → stardewcraft → other mods alphabetically */
    private static void sort(List<WorkbenchEntry> list) {
        list.sort(Comparator.<WorkbenchEntry, Integer>comparing(e -> {
            if ("minecraft".equals(e.namespace())) return 0;
            if (StardewCraft.MODID.equals(e.namespace())) return 1;
            return 2;
        }).thenComparing(e -> categoryOrder(e.category()))
          .thenComparing(e -> e.itemId().toString()));
    }

    private static int categoryOrder(String cat) {
        return switch (cat) {
            case "logs" -> 0;
            case "wood" -> 1;
            case "planks" -> 2;
            case "slabs" -> 3;
            case "stairs" -> 4;
            case "fences" -> 5;
            case "fence_gates" -> 6;
            case "doors" -> 7;
            case "trapdoors" -> 8;
            case "buttons" -> 9;
            case "pressure_plates" -> 10;
            case "basic" -> 0;
            case "standard" -> 1;
            case "refined" -> 2;
            case "advanced" -> 3;
            case "ornate" -> 4;
            case "walls" -> 5;
            default -> 50;
        };
    }

}
