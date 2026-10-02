package com.stardew.craft.port;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.DeferredRegister;

/**
 * PORT(1.20.1): 1.21.1 enchantments are data-driven ({@code data/stardewcraft/enchantment/*.json}); 1.20.1 only has
 * the built-in, code-registered enchantment registry. Each mod enchantment is registered here under the same id
 * ({@code stardewcraft:<name>}) and built from the same JSON definitions, including data-pack overrides, so the
 * {@code ResourceKey<Enchantment>} constants in {@code StardewEnchantments} resolve through
 * {@code registryAccess().registryOrThrow/lookupOrThrow(Registries.ENCHANTMENT)} exactly as in 1.21.1.
 * <p>
 * Rules reproduced from 1.21.1 {@code Enchantment}:
 * <ul>
 *   <li>{@code supported_items} → {@link Enchantment#canEnchant} (anvil, {@code /enchant}); {@code primary_items}
 *   (absent = supported items) → {@link Enchantment#canApplyAtEnchantingTable}.</li>
 *   <li>{@code exclusive_set} → {@code checkCompatibility}; 1.20.1 {@code isCompatibleWith} checks both directions
 *   like 1.21 {@code Enchantment.areCompatible}.</li>
 *   <li>{@code weight} → the exact 1..1024 weighted-selection value via {@code PortEnchantmentWeightMixin}.
 *   Standard weights retain their corresponding 1.20 rarity for Forge callers.</li>
 *   <li>{@code min_cost}/{@code max_cost} → {@code base + per_level_above_first * (level - 1)}.</li>
 *   <li>{@code anvil_cost} → anvil per-level cost via {@code PortAnvilEnchantmentCostMixin} (1.20.1 derives it from
 *   rarity; 1.21 uses {@code anvil_cost}, halved with a minimum of 1 for books in both versions).</li>
 *   <li>Vanilla 1.21 enchantment tags replace 1.20.1's flags: {@code #minecraft:treasure} → treasure-only,
 *   {@code #minecraft:curse} → curse, {@code #minecraft:tradeable} → tradeable, {@code #minecraft:in_enchanting_table}
 *   or {@code #minecraft:on_random_loot} → discoverable.</li>
 *   <li>1.21 creative tabs list a book for every registered enchantment, so the creative-tab category filter is
 *   bypassed.</li>
 * </ul>
 * The source mod's JSONs carry no effect components; non-empty {@code effects} are explicitly rejected.
 * Reloads retain all 17 registered identities and publish one validated snapshot, then sync remote clients.
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
    public static final TagKey<Enchantment> DOUBLE_TRADE_PRICE = vanillaTag("double_trade_price");
    private static final ThreadLocal<TagKey<Enchantment>> SELECTION_POOL = new ThreadLocal<>();

    private static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(Registries.ENCHANTMENT, PortBootstrap.NAMESPACE);
    private static final Map<String, EnchantmentCategory> CATEGORIES = new LinkedHashMap<>();
    // Publish a complete reload at once. Registered enchantment identities never change.
    private static volatile Map<String, DataEnchantment> definitions = Map.of();
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
        MinecraftForge.EVENT_BUS.register(PortEnchantments.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> PortClientHandlers::register);
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

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
                    ProfilerFiller preparationProfiler, ProfilerFiller reloadProfiler,
                    Executor backgroundExecutor, Executor gameExecutor) {
                return CompletableFuture.supplyAsync(() -> readDefinitions(manager), backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenAcceptAsync(PortEnchantments::applyDefinitions, gameExecutor);
            }

            @Override
            public String getName() { return "PortEnchantments"; }
        });
    }

    static Map<String, JsonObject> readDefinitions(ResourceManager manager) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (String name : NAMES) {
            ResourceLocation file = new ResourceLocation(PortBootstrap.NAMESPACE, "enchantment/" + name + ".json");
            try (Reader reader = manager.getResourceOrThrow(file).openAsReader()) {
                result.put(name, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot reload enchantment " + file, exception);
            }
        }
        return result;
    }

    /** Shared by the resource reload and network decoder; reject invalid/incomplete snapshots atomically. */
    public static void applyDefinitions(Map<String, JsonObject> json) {
        if (!json.keySet().equals(Set.copyOf(NAMES))) {
            throw new IllegalArgumentException("Enchantment snapshot must contain exactly the registered mod ids");
        }
        Map<String, DataEnchantment> next = new LinkedHashMap<>();
        for (String name : NAMES) next.put(name, new DataEnchantment(name, json.get(name).deepCopy()));
        definitions = Map.copyOf(next);
    }

    public static Map<String, JsonObject> snapshot() {
        Map<String, DataEnchantment> current = definitions;
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (String name : NAMES) {
            DataEnchantment definition = current.get(name);
            if (definition == null) definition = load(name);
            result.put(name, definition.json.deepCopy());
        }
        return result;
    }

    static void resetClientDefinitions() { definitions = Map.of(); }

    public record SyncMessage(Map<String, JsonObject> definitions) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(NAMES.size());
            for (String name : NAMES) {
                buf.writeUtf(name);
                buf.writeUtf(definitions.get(name).toString(), 32767);
            }
        }

        static SyncMessage decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count != NAMES.size()) throw new IllegalArgumentException("Invalid enchantment snapshot size " + count);
            Map<String, JsonObject> json = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String name = buf.readUtf(64);
                if (!NAMES.contains(name) || json.containsKey(name)) throw new IllegalArgumentException("Invalid enchantment id " + name);
                json.put(name, JsonParser.parseString(buf.readUtf(32767)).getAsJsonObject());
            }
            return new SyncMessage(Map.copyOf(json));
        }
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        List<ServerPlayer> players = event.getPlayer() == null ? event.getPlayerList().getPlayers() : List.of(event.getPlayer());
        if (players.isEmpty()) return;
        SyncMessage message = new SyncMessage(snapshot());
        for (ServerPlayer player : players) {
            if (player.connection.connection.isMemoryConnection()) continue;
            PortNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
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
        // EnchantmentInstance's constructor uses the exact JSON weight through a dedicated hook.
        return Enchantment.Rarity.COMMON;
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

    private static synchronized EnchantmentCategory category(String name, ItemSet supported) {
        // 1.20.1 requires a category; it only feeds Forge's default enchanting-table/creative-tab checks, both of
        // which DataEnchantment overrides. Give it the supported-items predicate so other mods reading it agree.
        return CATEGORIES.computeIfAbsent(name, key -> EnchantmentCategory.create(
                "STARDEWCRAFT_" + key.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT),
                item -> {
                    DataEnchantment current = definitions.get(name);
                    return (current == null ? supported : current.supportedItems).test().test(item.builtInRegistryHolder());
                }));
    }

    public static int anvilCost(Enchantment enchantment, int vanilla) {
        return enchantment instanceof DataEnchantment data ? data.current().anvilCost : vanilla;
    }

    public static int weight(Enchantment enchantment, int vanilla) {
        return enchantment instanceof DataEnchantment data ? data.current().weight : vanilla;
    }

    public static <T> T fromRandomLoot(Supplier<T> action) {
        TagKey<Enchantment> previous = SELECTION_POOL.get();
        SELECTION_POOL.set(ON_RANDOM_LOOT);
        try { return action.get(); }
        finally {
            if (previous == null) SELECTION_POOL.remove(); else SELECTION_POOL.set(previous);
        }
    }

    public static boolean inSelectionPool(Enchantment enchantment, boolean vanilla) {
        TagKey<Enchantment> pool = SELECTION_POOL.get();
        return enchantment instanceof DataEnchantment data ? data.inTag(pool == null ? IN_ENCHANTING_TABLE : pool) : vanilla;
    }

    public static boolean inRandomLoot(Enchantment enchantment, boolean vanilla) {
        return enchantment instanceof DataEnchantment data ? data.inTag(ON_RANDOM_LOOT) : vanilla;
    }

    public static boolean doubleTradePrice(Enchantment enchantment, boolean vanilla) {
        return enchantment instanceof DataEnchantment data ? data.inTag(DOUBLE_TRADE_PRICE) : vanilla;
    }

    public static final class DataEnchantment extends Enchantment {
        private final String name;
        private final JsonObject json;
        private final int weight;
        private final EquipmentSlot[] activeSlots;
        private final Component description;
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
            super(rarityForWeight(json.get("weight").getAsInt()), category(name, supported), slots(json.getAsJsonArray("slots")));
            this.name = name;
            this.json = json;
            this.weight = json.get("weight").getAsInt();
            if (weight < 1 || weight > 1024) throw new IllegalArgumentException("Enchantment weight must be in 1..1024");
            this.activeSlots = slots(json.getAsJsonArray("slots"));
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
            if (maxLevel < 1 || maxLevel > 255 || anvilCost < 0) throw new IllegalArgumentException("Invalid enchantment level/anvil cost");
            this.exclusiveSet = parseEnchantmentSet(json.get("exclusive_set"));
            this.description = Component.Serializer.fromJson(json.get("description"));
            if (this.description == null) throw new IllegalArgumentException("Missing enchantment description");
            JsonElement descriptionJson = json.get("description");
            this.descriptionId = descriptionJson.isJsonObject() && descriptionJson.getAsJsonObject().has("translate")
                    ? descriptionJson.getAsJsonObject().get("translate").getAsString() : "enchantment." + PortBootstrap.NAMESPACE + "." + name;
        }

        private DataEnchantment current() { return definitions.getOrDefault(name, this); }

        @Override
        public Rarity getRarity() { return rarityForWeight(current().weight); }

        @Override
        public Map<EquipmentSlot, ItemStack> getSlotItems(LivingEntity entity) {
            Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : current().activeSlots) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (!stack.isEmpty()) items.put(slot, stack);
            }
            return items;
        }

        @Override
        public Component getFullname(int level) {
            var text = ComponentUtils.mergeStyles(current().description.copy(),
                    Style.EMPTY.withColor(isCurse() ? ChatFormatting.RED : ChatFormatting.GRAY));
            if (level != 1 || getMaxLevel() != 1) text.append(" ").append(Component.translatable("enchantment.level." + level));
            return text;
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
            return current().maxLevel;
        }

        @Override
        public int getMinCost(int level) {
            DataEnchantment data = current();
            return data.minCostBase + data.minCostPerLevel * (level - 1);
        }

        @Override
        public int getMaxCost(int level) {
            DataEnchantment data = current();
            return data.maxCostBase + data.maxCostPerLevel * (level - 1);
        }

        @Override
        protected String getOrCreateDescriptionId() {
            return current().descriptionId;
        }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            // 1.21 Enchantment.areCompatible: !a.equals(b) && !a.exclusiveSet.contains(b) && !b.exclusiveSet.contains(a);
            // 1.20.1 isCompatibleWith runs this from both sides.
            return this != other && !current().exclusiveSet.test(BuiltInRegistries.ENCHANTMENT.wrapAsHolder(other));
        }

        /** 1.21 {@code isSupportedItem} / {@code canEnchant}. */
        @Override
        public boolean canEnchant(ItemStack stack) {
            return current().supportedItems.contains(stack);
        }

        /** 1.21 {@code isPrimaryItem}. */
        @Override
        public boolean canApplyAtEnchantingTable(ItemStack stack) {
            DataEnchantment data = current();
            return data.supportedItems.contains(stack) && (data.primaryItems == null || data.primaryItems.contains(stack));
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
