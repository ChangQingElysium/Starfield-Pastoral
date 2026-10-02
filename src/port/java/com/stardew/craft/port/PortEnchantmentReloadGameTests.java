package com.stardew.craft.port;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.combat.ForgeEnchantmentGuard;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Headless tests of the actual resource-read, registered identity, anvil and wire paths. */
@GameTestHolder("stardewcraft")
@PrefixGameTestTemplate(false)
public final class PortEnchantmentReloadGameTests {
    private static final ResourceLocation FILE = new ResourceLocation("stardewcraft", "enchantment/artful.json");
    private static Enchantment artful() {
        return BuiltInRegistries.ENCHANTMENT.get(new ResourceLocation("stardewcraft", "artful"));
    }

    private static JsonObject override(Map<String, JsonObject> originals) {
        JsonObject json = originals.get("artful").deepCopy();
        json.addProperty("supported_items", "minecraft:diamond_sword");
        json.addProperty("primary_items", "minecraft:diamond_sword");
        json.addProperty("weight", 7);
        json.addProperty("max_level", 3);
        json.addProperty("anvil_cost", 6);
        json.add("slots", JsonParser.parseString("[\"offhand\"]"));
        json.add("description", JsonParser.parseString("{\"text\":\"reload-probe\",\"color\":\"gold\"}"));
        json.getAsJsonObject("min_cost").addProperty("base", 3);
        json.getAsJsonObject("min_cost").addProperty("per_level_above_first", 2);
        json.getAsJsonObject("max_cost").addProperty("base", 9);
        json.getAsJsonObject("max_cost").addProperty("per_level_above_first", 4);
        json.addProperty("exclusive_set", "stardewcraft:crusader");
        return json;
    }

    private static ResourceManager overlay(ResourceManager base, JsonObject json) {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        return new ResourceManager() {
            @Override public Set<String> getNamespaces() { return base.getNamespaces(); }
            @Override public Optional<Resource> getResource(ResourceLocation id) {
                return FILE.equals(id) ? Optional.of(new Resource(base.getResource(FILE).orElseThrow().source(),
                        () -> new ByteArrayInputStream(bytes))) : base.getResource(id);
            }
            @Override public List<Resource> getResourceStack(ResourceLocation id) { return base.getResourceStack(id); }
            @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
                return base.listResources(path, filter);
            }
            @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
                return base.listResourceStacks(path, filter);
            }
            @Override public Stream<PackResources> listPacks() { return base.listPacks(); }
        };
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void enchantmentResourceReloadUpdatesAllExistingConsumers(GameTestHelper helper) throws Exception {
        Map<String, JsonObject> original = PortEnchantments.snapshot();
        Enchantment identity = artful();
        try {
            ResourceManager base = helper.getLevel().getServer().getResourceManager();
            PortEnchantments.applyDefinitions(PortEnchantments.readDefinitions(overlay(base, override(original))));
            helper.assertTrue(artful() == identity, "Reload replaced a registered enchantment identity");
            helper.assertTrue(identity.getMaxLevel() == 3 && identity.getMinCost(3) == 7 && identity.getMaxCost(3) == 17,
                    "Reload left old level/cost values");
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            helper.assertTrue(identity.canEnchant(sword) && identity.canApplyAtEnchantingTable(sword)
                    && !identity.canEnchant(new ItemStack(Items.DIAMOND_AXE)), "Reload left stale item predicates");
            helper.assertTrue(identity.category.canEnchant(Items.DIAMOND_SWORD)
                    && !identity.category.canEnchant(Items.DIAMOND_AXE), "Public Forge category is stale");
            Enchantment crusader = BuiltInRegistries.ENCHANTMENT.get(new ResourceLocation("stardewcraft", "crusader"));
            helper.assertTrue(!identity.isCompatibleWith(crusader), "Exclusive set is stale");
            helper.assertTrue(new EnchantmentInstance(identity, 1).getWeight().asInt() == 7, "Arbitrary JSON weight was quantized");
            helper.assertTrue(identity.getFullname(1).getString().startsWith("reload-probe")
                    && identity.getFullname(1).getStyle().getColor().getValue() == ChatFormatting.GOLD.getColor(),
                    "Description or explicit style was lost");
            ArmorStand stand = new ArmorStand(helper.getLevel(), 0, 0, 0);
            stand.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
            stand.setItemSlot(EquipmentSlot.OFFHAND, sword.copy());
            helper.assertTrue(identity.getSlotItems(stand).keySet().equals(Set.of(EquipmentSlot.OFFHAND)), "Slot group is stale");
            var player = helper.makeMockPlayer();
            player.getAbilities().instabuild = false;
            AnvilMenu menu = new AnvilMenu(0, player.getInventory());
            menu.getSlot(AnvilMenu.INPUT_SLOT).set(sword.copy());
            menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(identity, 1)));
            menu.createResult();
            helper.assertTrue(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(), "Reload bypassed the existing forge-enchantment guard");
            // The source mod deliberately forbids mod-enchantment books outside its forge transaction.
            // Enter that existing transaction only for the native anvil-cost probe.
            try (var transaction = ForgeEnchantmentGuard.beginForgeTransaction()) {
                menu.createResult();
                helper.assertTrue(!menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty() && menu.getCost() == 3,
                        "Anvil did not use cost 6 / book divisor 2: " + menu.getCost());
            }
            PortEnchantments.applyDefinitions(PortEnchantments.readDefinitions(base));
            helper.assertTrue(identity.getMaxLevel() == original.get("artful").get("max_level").getAsInt(),
                    "Removing a data-pack override did not restore bundled data");
        } finally {
            PortEnchantments.applyDefinitions(original);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void enchantmentSyncRoundTripAndInvalidReloadAreAtomic(GameTestHelper helper) {
        Map<String, JsonObject> original = PortEnchantments.snapshot();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            Map<String, JsonObject> changed = PortEnchantments.snapshot();
            changed.put("artful", override(original));
            new PortEnchantments.SyncMessage(changed).encode(buffer);
            PortEnchantments.applyDefinitions(PortEnchantments.SyncMessage.decode(buffer).definitions());
            helper.assertTrue(artful().getMaxLevel() == 3 && PortEnchantments.weight(artful(), 1) == 7,
                    "Synced definitions differ from server definitions");
            Map<String, JsonObject> invalid = PortEnchantments.snapshot();
            invalid.get("vampiric").addProperty("weight", 0);
            invalid.get("artful").addProperty("max_level", 2);
            boolean rejected = false;
            try { PortEnchantments.applyDefinitions(invalid); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected && artful().getMaxLevel() == 3, "Invalid reload published a partial snapshot");
        } finally {
            buffer.release();
            PortEnchantments.applyDefinitions(original);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void dataPackTableLootAndTradeTagsRemainIndependent(GameTestHelper helper) {
        Map<String, JsonObject> original = PortEnchantments.snapshot();
        Map<TagKey<Enchantment>, List<Holder<Enchantment>>> originalTags = new LinkedHashMap<>();
        BuiltInRegistries.ENCHANTMENT.getTags().forEach(pair -> originalTags.put(pair.getFirst(), pair.getSecond().stream().toList()));
        Holder<Enchantment> holder = BuiltInRegistries.ENCHANTMENT.wrapAsHolder(artful());
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        try {
            Map<String, JsonObject> changed = PortEnchantments.snapshot();
            changed.put("artful", override(original));
            PortEnchantments.applyDefinitions(changed);
            Map<TagKey<Enchantment>, List<Holder<Enchantment>>> tags = new LinkedHashMap<>(originalTags);
            tags.put(PortEnchantments.IN_ENCHANTING_TABLE, List.of(holder));
            tags.put(PortEnchantments.ON_RANDOM_LOOT, List.of());
            tags.put(PortEnchantments.TREASURE, List.of(holder));
            tags.put(PortEnchantments.DOUBLE_TRADE_PRICE, List.of());
            BuiltInRegistries.ENCHANTMENT.bindTags(tags);
            helper.assertTrue(offered(sword), "A data-pack table tag was incorrectly blocked by the old treasure flag");
            helper.assertTrue(!PortEnchantments.fromRandomLoot(() -> offered(sword)), "Table-only enchantment leaked into random loot");
            helper.assertTrue(!PortEnchantments.doubleTradePrice(artful(), true), "Trade price incorrectly follows treasure");
            tags.put(PortEnchantments.IN_ENCHANTING_TABLE, List.of());
            tags.put(PortEnchantments.ON_RANDOM_LOOT, List.of(holder));
            tags.put(PortEnchantments.TREASURE, List.of());
            tags.put(PortEnchantments.DOUBLE_TRADE_PRICE, List.of(holder));
            BuiltInRegistries.ENCHANTMENT.bindTags(tags);
            helper.assertTrue(!offered(sword) && PortEnchantments.fromRandomLoot(() -> offered(sword)),
                    "Loot-only enchantment leaked into the table, or was filtered from loot");
            helper.assertTrue(PortEnchantments.doubleTradePrice(artful(), false), "Independent trade-price tag was ignored");
            try { PortEnchantments.fromRandomLoot(() -> { throw new IllegalStateException("pool probe"); }); }
            catch (IllegalStateException expected) { }
            helper.assertTrue(!offered(sword), "Exceptional loot selection leaked its pool into subsequent calls");
        } finally {
            BuiltInRegistries.ENCHANTMENT.bindTags(originalTags);
            PortEnchantments.applyDefinitions(original);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void dataPackExclusiveTagsRejectMixedModVanillaPairs(GameTestHelper helper) {
        Map<String, JsonObject> original = PortEnchantments.snapshot();
        Map<TagKey<Enchantment>, List<Holder<Enchantment>>> originalTags = new LinkedHashMap<>();
        BuiltInRegistries.ENCHANTMENT.getTags().forEach(pair -> originalTags.put(pair.getFirst(), pair.getSecond().stream().toList()));
        Enchantment mod = artful();
        Enchantment sharpness = BuiltInRegistries.ENCHANTMENT.get(new ResourceLocation("minecraft", "sharpness"));
        ItemStack target = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("stardewcraft", "rusty_sword")));
        TagKey<Enchantment> damage = TagKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                new ResourceLocation("minecraft", "exclusive_set/damage"));
        try {
            Map<String, JsonObject> changed = PortEnchantments.snapshot();
            changed.get("artful").add("exclusive_set", JsonParser.parseString("[]"));
            PortEnchantments.applyDefinitions(changed);
            helper.assertTrue(PortVanillaEnchantmentRules.areCompatible(target, sharpness, mod),
                    "Mixed pair is incompatible before the one-sided tag override");
            Map<TagKey<Enchantment>, List<Holder<Enchantment>>> tags = new LinkedHashMap<>(originalTags);
            List<Holder<Enchantment>> members = new ArrayList<>(tags.getOrDefault(damage, List.of()));
            Holder<Enchantment> holder = BuiltInRegistries.ENCHANTMENT.wrapAsHolder(mod);
            if (!members.contains(holder)) members.add(holder);
            tags.put(damage, members);
            BuiltInRegistries.ENCHANTMENT.bindTags(tags);
            helper.assertTrue(!PortVanillaEnchantmentRules.areCompatible(target, sharpness, mod)
                    && !PortVanillaEnchantmentRules.areCompatible(target, mod, sharpness),
                    "Vanilla exclusive tag ignored its mod-enchantment member in one direction");
            helper.assertTrue(sharpness.isCompatibleWith(mod)
                    && PortVanillaEnchantmentRules.areCompatible(new ItemStack(Items.DIAMOND_SWORD), sharpness, mod),
                    "The mod-item bridge changed the native Forge/vanilla-item fallback");
            List<EnchantmentInstance> candidates = new ArrayList<>(List.of(new EnchantmentInstance(mod, 1)));
            PortVanillaEnchantmentRules.filterCompatibleEnchantments(target, candidates, new EnchantmentInstance(sharpness, 1));
            helper.assertTrue(candidates.isEmpty(), "Enchantment selection retained a one-sided exclusive candidate");
            target.enchant(mod, 1);
            var player = helper.makeMockPlayer();
            player.getAbilities().instabuild = false;
            AnvilMenu menu = new AnvilMenu(0, player.getInventory());
            menu.getSlot(AnvilMenu.INPUT_SLOT).set(target);
            menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(sharpness, 1)));
            menu.createResult();
            helper.assertTrue(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                    "Native anvil accepted a mixed pair rejected by the vanilla exclusive tag");
        } finally {
            BuiltInRegistries.ENCHANTMENT.bindTags(originalTags);
            PortEnchantments.applyDefinitions(original);
        }
        helper.succeed();
    }

    private static boolean offered(ItemStack stack) {
        return EnchantmentHelper.getAvailableEnchantmentResults(7, stack, false).stream()
                .anyMatch(candidate -> candidate.enchantment == artful());
    }
}
