package com.stardew.craft.port;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/**
 * PORT(1.20.1): 1.21.1 enchantments are data-driven ({@code data/stardewcraft/enchantment/*.json}); 1.20.1 only has
 * the built-in, code-registered enchantment registry. Each mod enchantment is registered here under the same id
 * ({@code stardewcraft:<name>}) and built from the very same JSON file shipped in the mod jar, so the
 * {@code ResourceKey<Enchantment>} constants in {@code StardewEnchantments} resolve through
 * {@code registryAccess().registryOrThrow/lookupOrThrow(Registries.ENCHANTMENT)} exactly as in 1.21.1.
 * <p>
 * Rules reproduced from 1.21.1 {@code Enchantment}:
 * <ul>
 *   <li>{@code supported_items} → {@link Enchantment#canEnchant} (anvil, {@code /enchant}); {@code primary_items}
 *   (absent = supported items) → {@link Enchantment#canApplyAtEnchantingTable}.</li>
 *   <li>{@code exclusive_set} → {@code checkCompatibility}; 1.20.1 {@code isCompatibleWith} checks both directions
 *   like 1.21 {@code Enchantment.areCompatible}.</li>
 *   <li>{@code weight} → {@link Enchantment.Rarity} by weight (10/5/2/1; any other weight is rejected).</li>
 *   <li>{@code min_cost}/{@code max_cost} → {@code base + per_level_above_first * (level - 1)}.</li>
 *   <li>{@code anvil_cost} → anvil per-level cost via {@code PortAnvilEnchantmentCostMixin} (1.20.1 derives it from
 *   rarity; 1.21 uses {@code anvil_cost}, halved with a minimum of 1 for books in both versions).</li>
 *   <li>Vanilla 1.21 enchantment tags replace 1.20.1's flags: {@code #minecraft:treasure} → treasure-only,
 *   {@code #minecraft:curse} → curse, {@code #minecraft:tradeable} → tradeable, {@code #minecraft:in_enchanting_table}
 *   or {@code #minecraft:on_random_loot} → discoverable.</li>
 *   <li>1.21 creative tabs list a book for every registered enchantment, so the creative-tab category filter is
 *   bypassed.</li>
 * </ul>
 * The JSONs carry no effect components; any {@code effects} entry is rejected at registration.
 */
public final class PortEnchantments {
    /** Registration order = 1.21 data-driven registry order (resource locations sorted). */
    private static final List<String> NAMES = List.of(
            "archaeologist", "artful", "auto_hook", "bottomless", "bug_killer", "crusader", "efficient",
            "expansive", "fisher", "generous", "haymaker", "master", "powerful", "preserving", "shaving",
            "swift", "vampiric");

    public static final TagKey<Enchantment> TREASURE = vanillaTag("treasure");
    public static final TagKey<Enchantment> CURSE = vanillaTag("curse");
    public static final TagKey<Enchantment> TRADEABLE = vanillaTag("tradeable");
    public static final TagKey<Enchantment> IN_ENCHANTING_TABLE = vanillaTag("in_enchanting_table");
    public static final TagKey<Enchantment> ON_RANDOM_LOOT = vanillaTag("on_random_loot");

    private static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(Registries.ENCHANTMENT, PortBootstrap.NAMESPACE);
    private static final Map<String, EnchantmentCategory> CATEGORIES = new LinkedHashMap<>();
    private static boolean registered;

    static {
        for (String name : NAMES) {
            ENCHANTMENTS.register(name, () -> load(name));
        }
    }

    private PortEnchantments() {}

    public static synchronized void register(IEventBus modBus) {
        if (registered) return;
        registered = true;
        ENCHANTMENTS.register(modBus);
    }

    private static TagKey<Enchantment> vanillaTag(String path) {
        return TagKey.create(Registries.ENCHANTMENT, new ResourceLocation("minecraft", path));
    }

    private static DataEnchantment load(String name) {
        String path = "/data/" + PortBootstrap.NAMESPACE + "/enchantment/" + name + ".json";
        try (InputStream in = PortEnchantments.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing enchantment definition " + path);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new DataEnchantment(name, json);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read enchantment definition " + path, e);
        }
    }

    /** Item set from a 1.21 {@code HolderSet<Item>} JSON value: {@code "#tag"}, {@code "id"} or {@code ["id", ...]}. */
    private record ItemSet(String key, Predicate<Holder<Item>> test) {
        static ItemSet parse(JsonElement element) {
            if (element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value.startsWith("#")) {
                    TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(value.substring(1)));
                    return new ItemSet(value, holder -> holder.is(tag));
                }
                ResourceLocation id = new ResourceLocation(value);
                return new ItemSet(value, holder -> holder.is(id));
            }
            List<ResourceLocation> ids = new ArrayList<>();
            for (JsonElement entry : element.getAsJsonArray()) ids.add(new ResourceLocation(entry.getAsString()));
            return new ItemSet(ids.toString(), holder -> ids.stream().anyMatch(holder::is));
        }

        boolean contains(ItemStack stack) {
            return test.test(stack.getItem().builtInRegistryHolder());
        }
    }

    /** Enchantment set from a 1.21 {@code HolderSet<Enchantment>} JSON value. */
    private static Predicate<Holder<Enchantment>> parseEnchantmentSet(JsonElement element) {
        if (element == null) return holder -> false;
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (value.startsWith("#")) {
                TagKey<Enchantment> tag = TagKey.create(Registries.ENCHANTMENT, new ResourceLocation(value.substring(1)));
                return holder -> holder.is(tag);
            }
            ResourceLocation id = new ResourceLocation(value);
            return holder -> holder.is(id);
        }
        List<ResourceLocation> ids = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) ids.add(new ResourceLocation(entry.getAsString()));
        return holder -> ids.stream().anyMatch(holder::is);
    }

    private static Enchantment.Rarity rarityForWeight(int weight) {
        for (Enchantment.Rarity rarity : Enchantment.Rarity.values()) {
            if (rarity.getWeight() == weight) return rarity;
        }
        throw new UnsupportedOperationException("PORT(1.20.1): enchantment weight " + weight
                + " has no 1.20.1 Rarity (10/5/2/1); enchanting-table weighting would need a dedicated override");
    }

    /** Anvil per-level cost as the 1.20.1 rarity switch in {@code AnvilMenu#createResult} computes it. */
    static Enchantment.Rarity rarityForAnvilCost(int anvilCost) {
        return switch (anvilCost) {
            case 1 -> Enchantment.Rarity.COMMON;
            case 2 -> Enchantment.Rarity.UNCOMMON;
            case 4 -> Enchantment.Rarity.RARE;
            case 8 -> Enchantment.Rarity.VERY_RARE;
            default -> throw new UnsupportedOperationException("PORT(1.20.1): anvil_cost " + anvilCost
                    + " is not expressible through the 1.20.1 anvil rarity switch (1/2/4/8)");
        };
    }

    private static EquipmentSlot[] slots(JsonArray groups) {
        Set<EquipmentSlot> slots = EnumSet.noneOf(EquipmentSlot.class);
        for (JsonElement group : groups) {
            switch (group.getAsString()) {
                case "any" -> slots.addAll(EnumSet.allOf(EquipmentSlot.class));
                case "mainhand" -> slots.add(EquipmentSlot.MAINHAND);
                case "offhand" -> slots.add(EquipmentSlot.OFFHAND);
                case "hand" -> { slots.add(EquipmentSlot.MAINHAND); slots.add(EquipmentSlot.OFFHAND); }
                case "feet" -> slots.add(EquipmentSlot.FEET);
                case "legs" -> slots.add(EquipmentSlot.LEGS);
                case "chest" -> slots.add(EquipmentSlot.CHEST);
                case "head" -> slots.add(EquipmentSlot.HEAD);
                case "armor" -> slots.addAll(EnumSet.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD));
                // 1.21 BODY (horse/wolf armour) has no 1.20.1 equipment slot.
                case "body" -> { }
                default -> throw new IllegalArgumentException("Unknown equipment slot group " + group);
            }
        }
        return slots.toArray(EquipmentSlot[]::new);
    }

    private static synchronized EnchantmentCategory category(ItemSet supported) {
        // 1.20.1 requires a category; it only feeds Forge's default enchanting-table/creative-tab checks, both of
        // which DataEnchantment overrides. Give it the supported-items predicate so other mods reading it agree.
        return CATEGORIES.computeIfAbsent(supported.key(), key -> EnchantmentCategory.create(
                "STARDEWCRAFT_" + key.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT),
                item -> supported.test().test(item.builtInRegistryHolder())));
    }

    /** Rarity whose 1.20.1 anvil cost equals the 1.21 {@code anvil_cost}; {@code null} for non-mod enchantments. */
    public static Enchantment.Rarity anvilRarity(Enchantment enchantment) {
        return enchantment instanceof DataEnchantment data ? rarityForAnvilCost(data.anvilCost) : null;
    }

    public static final class DataEnchantment extends Enchantment {
        private final int maxLevel;
        private final int minCostBase;
        private final int minCostPerLevel;
        private final int maxCostBase;
        private final int maxCostPerLevel;
        final int anvilCost;
        private final ItemSet supportedItems;
        private final ItemSet primaryItems;
        private final Predicate<Holder<Enchantment>> exclusiveSet;

        private DataEnchantment(String name, JsonObject json) {
            this(name, json, ItemSet.parse(json.get("supported_items")));
        }

        private DataEnchantment(String name, JsonObject json, ItemSet supported) {
            super(rarityForWeight(json.get("weight").getAsInt()), category(supported), slots(json.getAsJsonArray("slots")));
            if (json.has("effects") && !json.getAsJsonObject("effects").entrySet().isEmpty()) {
                throw new UnsupportedOperationException("PORT(1.20.1): enchantment effect components are not ported ("
                        + PortBootstrap.NAMESPACE + ":" + name + ")");
            }
            this.supportedItems = supported;
            this.primaryItems = json.has("primary_items") ? ItemSet.parse(json.get("primary_items")) : null;
            this.maxLevel = json.get("max_level").getAsInt();
            JsonObject minCost = json.getAsJsonObject("min_cost");
            JsonObject maxCost = json.getAsJsonObject("max_cost");
            this.minCostBase = minCost.get("base").getAsInt();
            this.minCostPerLevel = minCost.get("per_level_above_first").getAsInt();
            this.maxCostBase = maxCost.get("base").getAsInt();
            this.maxCostPerLevel = maxCost.get("per_level_above_first").getAsInt();
            this.anvilCost = json.get("anvil_cost").getAsInt();
            rarityForAnvilCost(this.anvilCost); // validate now, not on first anvil use
            this.exclusiveSet = parseEnchantmentSet(json.get("exclusive_set"));
            JsonObject description = json.getAsJsonObject("description");
            if (description == null || !description.has("translate") || description.size() != 1) {
                throw new UnsupportedOperationException("PORT(1.20.1): only plain translatable enchantment descriptions are ported");
            }
            this.descriptionId = description.get("translate").getAsString();
        }

        private Holder<Enchantment> holder() {
            return BuiltInRegistries.ENCHANTMENT.wrapAsHolder(this);
        }

        private boolean inTag(TagKey<Enchantment> tag) {
            return holder().is(tag);
        }

        @Override
        public int getMinLevel() {
            return 1;
        }

        @Override
        public int getMaxLevel() {
            return maxLevel;
        }

        @Override
        public int getMinCost(int level) {
            return minCostBase + minCostPerLevel * (level - 1);
        }

        @Override
        public int getMaxCost(int level) {
            return maxCostBase + maxCostPerLevel * (level - 1);
        }

        @Override
        protected String getOrCreateDescriptionId() {
            return descriptionId;
        }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            // 1.21 Enchantment.areCompatible: !a.equals(b) && !a.exclusiveSet.contains(b) && !b.exclusiveSet.contains(a);
            // 1.20.1 isCompatibleWith runs this from both sides.
            return this != other && !exclusiveSet.test(BuiltInRegistries.ENCHANTMENT.wrapAsHolder(other));
        }

        /** 1.21 {@code isSupportedItem} / {@code canEnchant}. */
        @Override
        public boolean canEnchant(ItemStack stack) {
            return supportedItems.contains(stack);
        }

        /** 1.21 {@code isPrimaryItem}. */
        @Override
        public boolean canApplyAtEnchantingTable(ItemStack stack) {
            return supportedItems.contains(stack) && (primaryItems == null || primaryItems.contains(stack));
        }

        @Override
        public boolean isTreasureOnly() {
            return inTag(TREASURE);
        }

        @Override
        public boolean isCurse() {
            return inTag(CURSE);
        }

        @Override
        public boolean isTradeable() {
            return inTag(TRADEABLE);
        }

        @Override
        public boolean isDiscoverable() {
            return inTag(IN_ENCHANTING_TABLE) || inTag(ON_RANDOM_LOOT);
        }

        @Override
        public boolean allowedInCreativeTab(Item book, Set<EnchantmentCategory> allowedCategories) {
            return isAllowedOnBooks();
        }
    }
}
