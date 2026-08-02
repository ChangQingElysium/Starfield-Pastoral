package com.stardew.craft.forge;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.ForgeEnchantmentGuard;
import com.stardew.craft.combat.StardewWeaponSpeedRules;
import com.stardew.craft.combat.WeaponForgeData;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.equipment.CombinedRingData;
import com.stardew.craft.item.equipment.StardewRingItem;
import com.stardew.craft.item.tool.FishingRodItem;
import com.stardew.craft.item.tool.HoeItem;
import com.stardew.craft.item.tool.PanItem;
import com.stardew.craft.item.tool.ScytheItem;
import com.stardew.craft.item.tool.StardewAxeItem;
import com.stardew.craft.item.tool.StardewPickaxeItem;
import com.stardew.craft.item.tool.WateringCanItem;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class ForgeRuleService {
    public static final int CRAFT_TIME_MS = 1600;
    public static final int PRISMATIC_FORGE_COST = 20;
    public static final int DIAMOND_FORGE_COST = 10;
    public static final int APPEARANCE_FORGE_COST = 10;
    public static final int MAX_WEAPON_FORGE_LEVELS = 3;
    private static final String TAG_TIMES_ENCHANTED = StardewCraft.MODID + ":TimesEnchanted";

    private static final String GEM_EMERALD = StardewCraft.MODID + ":emerald";
    private static final String GEM_AQUAMARINE = StardewCraft.MODID + ":aquamarine";
    private static final String GEM_RUBY = StardewCraft.MODID + ":ruby";
    private static final String GEM_AMETHYST = StardewCraft.MODID + ":amethyst";
    private static final String GEM_TOPAZ = StardewCraft.MODID + ":topaz";
    private static final String GEM_JADE = StardewCraft.MODID + ":jade";
    private static final float BASE_ATTACK_RANGE = 3.0f;

    private static final List<String> DIAMOND_RANDOM_FORGE_ORDER = List.of(
            GEM_EMERALD,
            GEM_AQUAMARINE,
            GEM_RUBY,
            GEM_AMETHYST,
            GEM_TOPAZ,
            GEM_JADE
    );

    private static final List<ResourceKey<Enchantment>> PRISMATIC_ENCHANTMENTS = List.of(
            StardewEnchantments.CRUSADER,
            StardewEnchantments.HAYMAKER,
            StardewEnchantments.POWERFUL,
            StardewEnchantments.EXPANSIVE,
            StardewEnchantments.SHAVING,
            StardewEnchantments.BOTTOMLESS,
            StardewEnchantments.GENEROUS,
            StardewEnchantments.ARCHAEOLOGIST,
            StardewEnchantments.MASTER,
            StardewEnchantments.AUTO_HOOK,
            StardewEnchantments.PRESERVING,
            StardewEnchantments.EFFICIENT,
            StardewEnchantments.SWIFT,
            StardewEnchantments.FISHER
    );

    private ForgeRuleService() {
    }

    public enum CraftState {
        MISSING_INGREDIENTS(0, "stardewcraft.forge.description"),
        MISSING_SHARDS(1, "stardewcraft.forge.not_enough_shards"),
        VALID(2, "stardewcraft.forge.valid"),
        INVALID_RECIPE(3, "stardewcraft.forge.wrong_order"),
        NO_ROOM(4, "stardewcraft.forge.no_room");

        private final int id;
        private final String translationKey;

        CraftState(int id, String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        public int id() {
            return id;
        }

        public String translationKey() {
            return translationKey;
        }

        public static CraftState byId(int id) {
            for (CraftState state : values()) {
                if (state.id == id) {
                    return state;
                }
            }
            return MISSING_INGREDIENTS;
        }
    }

    public record Preview(CraftState state, int cost, ItemStack result) {
    }

    public record UnforgeResult(ItemStack result, List<ItemStack> returnedItems, int cinderShardRefund) {
    }

    public static Preview preview(ItemStack left, ItemStack right, Player player) {
        if (left.isEmpty() || right.isEmpty()) {
            return new Preview(CraftState.MISSING_INGREDIENTS, 0, ItemStack.EMPTY);
        }
        if (!isValidCraft(left, right)) {
            return new Preview(CraftState.INVALID_RECIPE, 0, ItemStack.EMPTY);
        }

        int cost = getForgeCost(left, right);
        ItemStack result = craftPreview(left, right, player);
        if (player != null && countCinderShards(player.getInventory()) < cost) {
            return new Preview(CraftState.MISSING_SHARDS, cost, result);
        }
        if (player != null && !canFitCraftedItem(player, result)) {
            return new Preview(CraftState.NO_ROOM, cost, result);
        }
        return new Preview(CraftState.VALID, cost, result);
    }

    public static boolean isValidCraft(ItemStack left, ItemStack right) {
        return isPrismaticCraft(left, right)
                || isGemForgeCraft(left, right)
                || isGalaxySoulCraft(left, right)
            || isDragonToothCraft(left, right)
            || isWeaponAppearanceCraft(left, right)
            || isRingCombinationCraft(left, right);
    }

    public static int getForgeCost(ItemStack left, ItemStack right) {
        if (isPrismaticShard(right)) {
            return PRISMATIC_FORGE_COST;
        }
        if (isGalaxySoul(right)) {
            return PRISMATIC_FORGE_COST;
        }
        if (isDragonTooth(right)) {
            return 10;
        }
        if (isRingCombinationCraft(left, right)) {
            return 20;
        }
        if (isDiamond(right)) {
            return DIAMOND_FORGE_COST;
        }
        if (isOrdinaryGem(right)) {
            return getForgeCostAtLevel(totalGemForgeLevels(left));
        }
        if (isWeaponAppearanceCraft(left, right)) {
            return APPEARANCE_FORGE_COST;
        }
        return 1;
    }

    public static ItemStack craftPreview(ItemStack left, ItemStack right) {
        return craftPreview(left, right, null);
    }

    private static ItemStack craftPreview(ItemStack left, ItemStack right, Player player) {
        if (!isValidCraft(left, right)) {
            return ItemStack.EMPTY;
        }
        if (isDiamond(right)) {
            return craftDiamondPreview(left);
        }
        if (isGemForgeCraft(left, right)) {
            return craftGemForge(left, right, RandomSource.create(0L));
        }
        if (isGalaxySoulCraft(left, right)) {
            return craftGalaxySoulForge(left);
        }
        if (isDragonToothCraft(left, right)) {
            return craftDragonToothForge(left, RandomSource.create(0L));
        }
        if (isWeaponAppearanceCraft(left, right)) {
            return craftWeaponAppearance(left, right);
        }
        if (isRingCombinationCraft(left, right)) {
            return craftRingCombination(left, right);
        }
        ItemStack result = left.copyWithCount(1);
        if (player instanceof ServerPlayer serverPlayer) {
            ResourceKey<Enchantment> enchantment = selectPrismaticEnchantment(left, prismaticRandom(serverPlayer));
            if (enchantment != null) {
                try (AutoCloseable ignored = ForgeEnchantmentGuard.beginForgeTransaction()) {
                    applyPrismaticEnchantment(result, enchantment, serverPlayer);
                } catch (Exception exception) {
                    StardewCraft.LOGGER.warn("Failed to preview Mini-Forge enchantment {}", enchantment.location(), exception);
                }
            }
        }
        refreshWeaponAttributeModifiers(result);
        return result;
    }

    private static ItemStack craftDiamondPreview(ItemStack left) {
        ItemStack result = left.copyWithCount(1);
        WeaponForgeData.State oldState = WeaponForgeData.read(result);
        WeaponForgeData.write(result, new WeaponForgeData.State(
                oldState.gemForges(),
                oldState.prismaticEnchantment(),
                oldState.previousEnchantments(),
                oldState.galaxySoulLevel(),
                oldState.appearanceWeaponId(),
                oldState.dragonToothEnchantment(),
                true));
        refreshWeaponAttributeModifiers(result);
        return result;
    }

    public static boolean canCraftForReal(ItemStack left, ItemStack right, ServerPlayer player) {
        Preview preview = preview(left, right, player);
        return preview.state() == CraftState.VALID && !preview.result().isEmpty();
    }

    public static ItemStack craftForReal(ItemStack left, ItemStack right, ServerPlayer player) {
        if (!canCraftForReal(left, right, player)) {
            return ItemStack.EMPTY;
        }
        if (isGemForgeCraft(left, right)) {
            return craftGemForge(left, right, player.getRandom());
        }
        if (isGalaxySoulCraft(left, right)) {
            return craftGalaxySoulForge(left);
        }
        if (isDragonToothCraft(left, right)) {
            return craftDragonToothForge(left, player.getRandom());
        }
        if (isWeaponAppearanceCraft(left, right)) {
            return craftWeaponAppearance(left, right);
        }
        if (isRingCombinationCraft(left, right)) {
            return craftRingCombination(left, right);
        }

        ResourceKey<Enchantment> enchantment = selectPrismaticEnchantment(left, prismaticRandom(player));
        if (enchantment == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = left.copyWithCount(1);
        try (AutoCloseable ignored = ForgeEnchantmentGuard.beginForgeTransaction()) {
            applyPrismaticEnchantment(result, enchantment, player);
            incrementTimesEnchanted(player);
        } catch (Exception exception) {
            StardewCraft.LOGGER.warn("Failed to apply Mini-Forge enchantment {}", enchantment.location(), exception);
            return ItemStack.EMPTY;
        }
        return result;
    }

    public static boolean isValidUnforge(ItemStack left, ItemStack right) {
        if (!right.isEmpty()) {
            return false;
        }
        return isValidUnforgeTarget(left);
    }

    public static boolean isValidUnforgeTarget(ItemStack left) {
        if (CombinedRingData.isCombinedRing(left)) {
            return true;
        }
        if (!isWeaponForgeTarget(left)) {
            return false;
        }
        WeaponForgeData.State state = WeaponForgeData.read(left);
        return totalLevels(state.gemForges()) > 0
                || !state.appearanceWeaponId().isEmpty()
            || !state.dragonToothEnchantment().isEmpty()
            || state.diamondForge();
    }

    public static UnforgeResult unforgeForReal(ItemStack left, ItemStack right) {
        if (!isValidUnforge(left, right)) {
            return new UnforgeResult(ItemStack.EMPTY, List.of(), 0);
        }

        if (CombinedRingData.isCombinedRing(left)) {
            return new UnforgeResult(ItemStack.EMPTY, CombinedRingData.split(left), 10);
        }

        ItemStack result = left.copyWithCount(1);
        WeaponForgeData.State oldState = WeaponForgeData.read(result);
        int refund = getUnforgeCinderShardRefund(oldState);
        ItemStack returnedAppearance = appearanceStack(oldState.appearanceWeaponId());

        WeaponForgeData.write(result, new WeaponForgeData.State(
                List.of(),
                oldState.prismaticEnchantment(),
                oldState.previousEnchantments(),
                oldState.galaxySoulLevel(),
                "",
                "",
                false));
        refreshWeaponAttributeModifiers(result);
        return new UnforgeResult(result, returnedAppearance.isEmpty() ? List.of() : List.of(returnedAppearance), refund);
    }

    public static boolean isValidLeftIngredient(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return isForgeableTool(stack) || stack.getItem() instanceof StardewRingItem || CombinedRingData.isCombinedRing(stack);
    }

    public static boolean isValidRightIngredient(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item == ModItems.PRISMATIC_SHARD.get()
                || item == ModItems.EMERALD.get()
                || item == ModItems.AQUAMARINE.get()
                || item == ModItems.RUBY.get()
                || item == ModItems.AMETHYST.get()
                || item == ModItems.TOPAZ.get()
                || item == ModItems.JADE.get()
                || item == ModItems.DIAMOND.get()
                || item == ModItems.GALAXY_SOUL.get()
                || item == ModItems.DRAGON_TOOTH.get()
                || item instanceof StardewRingItem
                || item instanceof IStardewWeapon;
    }

    public static int countCinderShards(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.CINDER_SHARD.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean consumeCinderShards(Inventory inventory, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (countCinderShards(inventory) < amount) {
            return false;
        }

        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(ModItems.CINDER_SHARD.get())) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            remaining -= taken;
        }
        inventory.setChanged();
        return true;
    }

    private static int getForgeCostAtLevel(int level) {
        return 10 + level * 5;
    }

    private static boolean isPrismaticCraft(ItemStack left, ItemStack right) {
        return isPrismaticShard(right) && !availablePrismaticEnchantments(left).isEmpty();
    }

    private static boolean isGemForgeCraft(ItemStack left, ItemStack right) {
        return isWeaponForgeTarget(left)
                && (isOrdinaryGem(right) || isDiamond(right))
                && totalGemForgeLevels(left) < MAX_WEAPON_FORGE_LEVELS;
    }

    private static boolean isGalaxySoulCraft(ItemStack left, ItemStack right) {
        return isGalaxySoul(right)
                && isGalaxyWeapon(left)
                && WeaponForgeData.read(left).galaxySoulLevel() < 3;
    }

    private static boolean isDragonToothCraft(ItemStack left, ItemStack right) {
        if (!isDragonTooth(right) || !isWeaponForgeTarget(left)) {
            return false;
        }
        if (!(left.getItem() instanceof IStardewWeapon weapon) || weapon.getWeaponData() == null) {
            return false;
        }
        return true;
    }

    private static boolean isWeaponAppearanceCraft(ItemStack left, ItemStack right) {
        if (!isWeaponForgeTarget(left) || !isWeaponForgeTarget(right)) {
            return false;
        }
        if (!(left.getItem() instanceof IStardewWeapon leftWeapon)
                || !(right.getItem() instanceof IStardewWeapon rightWeapon)) {
            return false;
        }
        if (leftWeapon.getWeaponData() == null
                || rightWeapon.getWeaponData() == null
                || leftWeapon.getWeaponData().getWeaponType() != rightWeapon.getWeaponData().getWeaponType()) {
            return false;
        }
        String appearanceWeaponId = BuiltInRegistries.ITEM.getKey(right.getItem()).toString();
        WeaponForgeData.State state = WeaponForgeData.read(left);
        if (state.appearanceWeaponId().isEmpty()) {
            return !ItemStack.isSameItem(left, right);
        }
        return !state.appearanceWeaponId().equals(appearanceWeaponId);
    }

    private static boolean isRingCombinationCraft(ItemStack left, ItemStack right) {
        return CombinedRingData.isCombinableRing(left) && CombinedRingData.isCombinableRing(right);
    }

    private static boolean isPrismaticShard(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.PRISMATIC_SHARD.get());
    }

    private static boolean isGalaxySoul(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.GALAXY_SOUL.get());
    }

    private static boolean isDragonTooth(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.DRAGON_TOOTH.get());
    }

    private static boolean isWeaponForgeTarget(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof IStardewWeapon && !(item instanceof ScytheItem);
    }

    private static boolean isOrdinaryGem(ItemStack stack) {
        return ordinaryGemId(stack) != null;
    }

    private static boolean isDiamond(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.DIAMOND.get());
    }

    private static boolean isGalaxyWeapon(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ModItems.GALAXY_SWORD.get())
                || stack.is(ModItems.GALAXY_DAGGER.get())
                || stack.is(ModItems.GALAXY_HAMMER.get()));
    }

    private static Item infinityWeaponFor(ItemStack stack) {
        if (stack.is(ModItems.GALAXY_SWORD.get())) {
            return ModItems.INFINITY_BLADE.get();
        }
        if (stack.is(ModItems.GALAXY_DAGGER.get())) {
            return ModItems.INFINITY_DAGGER.get();
        }
        if (stack.is(ModItems.GALAXY_HAMMER.get())) {
            return ModItems.INFINITY_GAVEL.get();
        }
        return null;
    }

    private static String ordinaryGemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(ModItems.EMERALD.get())) {
            return GEM_EMERALD;
        }
        if (stack.is(ModItems.AQUAMARINE.get())) {
            return GEM_AQUAMARINE;
        }
        if (stack.is(ModItems.RUBY.get())) {
            return GEM_RUBY;
        }
        if (stack.is(ModItems.AMETHYST.get())) {
            return GEM_AMETHYST;
        }
        if (stack.is(ModItems.TOPAZ.get())) {
            return GEM_TOPAZ;
        }
        if (stack.is(ModItems.JADE.get())) {
            return GEM_JADE;
        }
        return null;
    }

    private static int totalGemForgeLevels(ItemStack stack) {
        return totalLevels(WeaponForgeData.read(stack).gemForges());
    }

    private static ItemStack craftGemForge(ItemStack left, ItemStack right, RandomSource random) {
        ItemStack result = left.copyWithCount(1);
        WeaponForgeData.State oldState = WeaponForgeData.read(result);
        List<WeaponForgeData.GemForge> gemForges = new ArrayList<>(oldState.gemForges());
        int remaining = MAX_WEAPON_FORGE_LEVELS - totalLevels(gemForges);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        String gemId = ordinaryGemId(right);
        if (gemId != null) {
            addGemForgeLevel(gemForges, gemId);
        } else if (isDiamond(right)) {
            List<String> candidates = new ArrayList<>();
            for (String candidate : DIAMOND_RANDOM_FORGE_ORDER) {
                if (!hasGemForge(gemForges, candidate)) {
                    candidates.add(candidate);
                }
            }
            for (int i = 0; i < remaining && !candidates.isEmpty(); i++) {
                int index = random.nextInt(candidates.size());
                addGemForgeLevel(gemForges, candidates.remove(index));
            }
        }

        WeaponForgeData.write(result, new WeaponForgeData.State(
                gemForges,
                oldState.prismaticEnchantment(),
                oldState.previousEnchantments(),
                oldState.galaxySoulLevel(),
                oldState.appearanceWeaponId(),
                oldState.dragonToothEnchantment(),
                oldState.diamondForge() || isDiamond(right)));
        refreshWeaponAttributeModifiers(result);
        return result;
    }

    private static ItemStack craftGalaxySoulForge(ItemStack left) {
        WeaponForgeData.State oldState = WeaponForgeData.read(left);
        int newLevel = oldState.galaxySoulLevel() + 1;
        ItemStack result = left.copyWithCount(1);
        String appearanceWeaponId = oldState.appearanceWeaponId();

        if (newLevel >= 3) {
            Item transformedItem = infinityWeaponFor(left);
            if (transformedItem == null) {
                return ItemStack.EMPTY;
            }
            result = transformedItem.getDefaultInstance();
            copyComponent(left, result, DataComponents.ENCHANTMENTS);
            copyComponent(left, result, DataComponents.CUSTOM_NAME);
            copyComponent(left, result, DataComponents.LORE);
            newLevel = 0;
            appearanceWeaponId = "";
        }

        WeaponForgeData.write(result, new WeaponForgeData.State(
                oldState.gemForges(),
                oldState.prismaticEnchantment(),
                oldState.previousEnchantments(),
                newLevel,
                appearanceWeaponId,
                oldState.dragonToothEnchantment(),
                oldState.diamondForge()));
        refreshWeaponAttributeModifiers(result);
        return result;
    }

    private static ItemStack craftDragonToothForge(ItemStack left, RandomSource random) {
        ItemStack result = left.copyWithCount(1);
        WeaponForgeData.State oldState = WeaponForgeData.read(result);
        String enchantment = selectDragonToothEnchantment(result, oldState.dragonToothEnchantment(), random);
        if (enchantment.isEmpty()) {
            return ItemStack.EMPTY;
        }

        WeaponForgeData.write(result, new WeaponForgeData.State(
                oldState.gemForges(),
                oldState.prismaticEnchantment(),
                oldState.previousEnchantments(),
                oldState.galaxySoulLevel(),
                oldState.appearanceWeaponId(),
                enchantment,
                oldState.diamondForge()));
        refreshWeaponAttributeModifiers(result);
        return result;
    }

    private static ItemStack craftWeaponAppearance(ItemStack left, ItemStack right) {
        ItemStack result = left.copyWithCount(1);
        WeaponForgeData.State oldState = WeaponForgeData.read(result);
        String appearanceWeaponId = BuiltInRegistries.ITEM.getKey(right.getItem()).toString();
        WeaponForgeData.write(result, new WeaponForgeData.State(
                oldState.gemForges(),
                oldState.prismaticEnchantment(),
                oldState.previousEnchantments(),
                oldState.galaxySoulLevel(),
                appearanceWeaponId,
                oldState.dragonToothEnchantment(),
                oldState.diamondForge()));
        refreshWeaponAttributeModifiers(result);
        return result;
    }

    private static ItemStack craftRingCombination(ItemStack left, ItemStack right) {
        return CombinedRingData.create(left, right);
    }

    private static int getUnforgeCinderShardRefund(WeaponForgeData.State state) {
        int totalCost = 0;
        int forgeLevel = 0;
        for (WeaponForgeData.GemForge forge : state.gemForges()) {
            int levels = Math.max(0, forge.level());
            for (int i = 0; i < levels; i++) {
                totalCost += getForgeCostAtLevel(forgeLevel);
                forgeLevel++;
            }
        }
        if (!state.appearanceWeaponId().isEmpty()) {
            totalCost += APPEARANCE_FORGE_COST;
        }
        if (!state.dragonToothEnchantment().isEmpty()) {
            totalCost += 10;
        }
        if (state.diamondForge()) {
            totalCost += DIAMOND_FORGE_COST;
        }
        return totalCost / 2;
    }

    private static ItemStack appearanceStack(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(location)
                .map(item -> new ItemStack(item, 1))
                .orElse(ItemStack.EMPTY);
    }

    private static <T> void copyComponent(ItemStack source, ItemStack target, DataComponentType<T> type) {
        T value = source.get(type);
        if (value != null) {
            target.set(type, value);
        }
    }

    private static int totalLevels(List<WeaponForgeData.GemForge> gemForges) {
        int total = 0;
        for (WeaponForgeData.GemForge forge : gemForges) {
            total += Math.max(0, forge.level());
        }
        return total;
    }

    private static boolean hasGemForge(List<WeaponForgeData.GemForge> gemForges, String gemId) {
        for (WeaponForgeData.GemForge forge : gemForges) {
            if (gemId.equals(forge.itemId())) {
                return true;
            }
        }
        return false;
    }

    private static void addGemForgeLevel(List<WeaponForgeData.GemForge> gemForges, String gemId) {
        for (int index = 0; index < gemForges.size(); index++) {
            WeaponForgeData.GemForge forge = gemForges.get(index);
            if (gemId.equals(forge.itemId())) {
                gemForges.set(index, new WeaponForgeData.GemForge(gemId, forge.level() + 1));
                return;
            }
        }
        gemForges.add(new WeaponForgeData.GemForge(gemId, 1));
    }

    private static void refreshWeaponAttributeModifiers(ItemStack stack) {
        if (!(stack.getItem() instanceof IStardewWeapon)) {
            return;
        }
        WeaponStats stats = WeaponStats.fromItemStack(stack);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String modifierId = "weapon." + itemId.getPath();
        float avgDamage = (stats.getMinDamage() + stats.getMaxDamage()) / 2.0f - 1.0f;
        float attackSpeed = (float) StardewWeaponSpeedRules.attacksPerSecondFromRawSpeed(
                stats.getWeaponType(),
                stats.getRawSpeed(),
                stats.getWeaponSpeedMultiplier()
        ) - 4.0F;
        float attackRangeBonus = stats.getWeaponType().getAttackRange() - BASE_ATTACK_RANGE;

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, modifierId + ".attack_damage"),
                                avgDamage,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, modifierId + ".attack_speed"),
                                attackSpeed,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, modifierId + ".attack_range"),
                                attackRangeBonus,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build()
                .withTooltip(false));
    }

    private static String selectDragonToothEnchantment(ItemStack stack, String previous, RandomSource random) {
        if (!(stack.getItem() instanceof IStardewWeapon weapon) || weapon.getWeaponData() == null) {
            return "";
        }
        List<String> previousKinds = WeaponForgeData.dragonToothBonuses(previous).stream()
                .map(WeaponForgeData.DragonToothBonus::kind)
                .toList();
        List<WeaponForgeData.DragonToothBonus> selected = List.of();
        for (int attempt = 0; attempt < 20; attempt++) {
            selected = rollDragonToothBonuses(weapon, random);
            if (!matchesPreviousDragonToothKinds(selected, previousKinds)) {
                break;
            }
        }
        return WeaponForgeData.encodeDragonToothBonuses(selected);
    }

    private static List<WeaponForgeData.DragonToothBonus> rollDragonToothBonuses(IStardewWeapon weapon, RandomSource random) {
        List<WeaponForgeData.DragonToothBonus> bonuses = new ArrayList<>();
        int weaponLevel = Math.max(1, weapon.getWeaponData().getLevel());

        if (random.nextFloat() < 0.125f && weaponLevel <= 10) {
            bonuses.add(new WeaponForgeData.DragonToothBonus("defense",
                    Math.max(1, Math.min(2, random.nextInt(weaponLevel + 1) / 2 + 1))));
        } else if (random.nextFloat() < 0.125f) {
            bonuses.add(new WeaponForgeData.DragonToothBonus("lightweight", random.nextInt(5) + 1));
        } else if (random.nextFloat() < 0.125f) {
            bonuses.add(new WeaponForgeData.DragonToothBonus("slime_gatherer", 1));
        }

        switch (random.nextInt(5)) {
            case 0 -> bonuses.add(new WeaponForgeData.DragonToothBonus("attack",
                    Math.max(1, Math.min(5, random.nextInt(weaponLevel + 1) / 2 + 1))));
            case 1 -> bonuses.add(new WeaponForgeData.DragonToothBonus("crit",
                    Math.max(1, Math.min(3, random.nextInt(Math.max(1, weaponLevel)) / 3))));
            case 2 -> {
                int speedCap = Math.max(1, 4 - weapon.getWeaponData().getRawSpeed());
                bonuses.add(new WeaponForgeData.DragonToothBonus("speed",
                        Math.max(1, Math.min(speedCap, random.nextInt(Math.max(1, weaponLevel))))));
            }
            case 3 -> bonuses.add(new WeaponForgeData.DragonToothBonus("slime_slayer", 1));
            case 4 -> bonuses.add(new WeaponForgeData.DragonToothBonus("crit_power",
                    Math.max(1, Math.min(3, random.nextInt(Math.max(1, weaponLevel)) / 3))));
            default -> {
            }
        }
        return bonuses;
    }

    private static boolean matchesPreviousDragonToothKinds(List<WeaponForgeData.DragonToothBonus> bonuses, List<String> previousKinds) {
        if (previousKinds.isEmpty()) {
            return false;
        }
        for (WeaponForgeData.DragonToothBonus bonus : bonuses) {
            if (previousKinds.contains(bonus.kind())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isForgeableTool(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof IStardewWeapon && !(item instanceof ScytheItem)
                || item instanceof StardewAxeItem
                || item instanceof StardewPickaxeItem
                || item instanceof HoeItem
                || item instanceof WateringCanItem
                || item instanceof FishingRodItem
                || item instanceof PanItem;
    }

    private static boolean canFitCraftedItem(Player player, ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(carried, result)
                && carried.getCount() + result.getCount() <= carried.getMaxStackSize();
    }

    private static ResourceKey<Enchantment> selectPrismaticEnchantment(ItemStack baseStack, RandomSource random) {
        List<ResourceKey<Enchantment>> candidates = availablePrismaticEnchantments(baseStack);
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static RandomSource prismaticRandom(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        long timesEnchanted = data.getLong(TAG_TIMES_ENCHANTED);
        long worldSeed = player.serverLevel().getSeed();
        long uuidSeed = player.getUUID().getMostSignificantBits() ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 21);
        long seed = mixSeed(timesEnchanted, worldSeed, uuidSeed);
        return RandomSource.create(seed);
    }

    private static void incrementTimesEnchanted(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.putLong(TAG_TIMES_ENCHANTED, data.getLong(TAG_TIMES_ENCHANTED) + 1L);
    }

    private static long mixSeed(long timesEnchanted, long worldSeed, long playerSeed) {
        long seed = 0x9E3779B97F4A7C15L;
        seed ^= timesEnchanted + 0xBF58476D1CE4E5B9L + (seed << 6) + (seed >>> 2);
        seed ^= worldSeed + 0x94D049BB133111EBL + (seed << 6) + (seed >>> 2);
        seed ^= playerSeed + 0xD1B54A32D192ED03L + (seed << 6) + (seed >>> 2);
        return seed;
    }

    private static List<ResourceKey<Enchantment>> availablePrismaticEnchantments(ItemStack stack) {
        List<ResourceKey<Enchantment>> candidates = new ArrayList<>();
        for (ResourceKey<Enchantment> enchantment : PRISMATIC_ENCHANTMENTS) {
            if (canApplyPrismaticEnchantment(stack, enchantment) && !StardewEnchantments.has(stack, enchantment)) {
                candidates.add(enchantment);
            }
        }

        WeaponForgeData.State state = WeaponForgeData.read(stack);
        for (String previous : state.previousEnchantments()) {
            if (candidates.size() <= 1) {
                break;
            }
            candidates.removeIf(enchantment -> matchesStoredEnchantmentName(enchantment, previous));
        }
        return candidates;
    }

    private static boolean canApplyPrismaticEnchantment(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        Item item = stack.getItem();
        if (enchantment == StardewEnchantments.ARTFUL
                || enchantment == StardewEnchantments.BUG_KILLER
                || enchantment == StardewEnchantments.VAMPIRIC
                || enchantment == StardewEnchantments.CRUSADER
                || enchantment == StardewEnchantments.HAYMAKER) {
            return item instanceof IStardewWeapon && !(item instanceof ScytheItem);
        }
        if (enchantment == StardewEnchantments.POWERFUL) {
            return item instanceof StardewPickaxeItem || item instanceof StardewAxeItem;
        }
        if (enchantment == StardewEnchantments.EFFICIENT) {
            return item instanceof StardewPickaxeItem
                    || item instanceof StardewAxeItem
                    || item instanceof HoeItem
                    || item instanceof WateringCanItem;
        }
        if (enchantment == StardewEnchantments.SWIFT) {
            return item instanceof StardewPickaxeItem || item instanceof StardewAxeItem || item instanceof HoeItem;
        }
        if (enchantment == StardewEnchantments.EXPANSIVE) {
            return item instanceof HoeItem hoe && hoe.getTier().getMaxChargeLevel() == 4
                    || item instanceof WateringCanItem can && can.getTier().getMaxChargeLevel() == 4
                    || item instanceof PanItem pan && pan.getUpgradeLevel() == 4;
        }
        if (enchantment == StardewEnchantments.BOTTOMLESS) {
            return item instanceof WateringCanItem;
        }
        if (enchantment == StardewEnchantments.SHAVING) {
            return item instanceof StardewAxeItem;
        }
        if (enchantment == StardewEnchantments.ARCHAEOLOGIST || enchantment == StardewEnchantments.GENEROUS) {
            return item instanceof HoeItem;
        }
        if (enchantment == StardewEnchantments.MASTER
                || enchantment == StardewEnchantments.AUTO_HOOK
                || enchantment == StardewEnchantments.PRESERVING) {
            return item instanceof FishingRodItem;
        }
        if (enchantment == StardewEnchantments.FISHER) {
            return item instanceof PanItem;
        }
        return false;
    }

    private static void applyPrismaticEnchantment(ItemStack stack, ResourceKey<Enchantment> enchantment, ServerPlayer player) {
        ItemEnchantments currentEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(currentEnchantments);
        var lookup = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (ResourceKey<Enchantment> prismatic : PRISMATIC_ENCHANTMENTS) {
            lookup.get(prismatic).ifPresent(holder -> mutable.set(holder, 0));
        }
        Holder.Reference<Enchantment> holder = lookup.getOrThrow(enchantment);
        mutable.set(holder, 1);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());

        WeaponForgeData.State oldState = WeaponForgeData.read(stack);
        List<String> previous = new ArrayList<>();
        previous.add(enchantment.location().getPath());
        for (String stored : oldState.previousEnchantments()) {
            if (previous.size() >= 2) {
                break;
            }
            if (!matchesStoredEnchantmentName(enchantment, stored)) {
                previous.add(stored);
            }
        }
        WeaponForgeData.write(stack, new WeaponForgeData.State(
                oldState.gemForges(),
                enchantment.location().toString(),
                previous,
                oldState.galaxySoulLevel(),
                oldState.appearanceWeaponId(),
                oldState.dragonToothEnchantment(),
                oldState.diamondForge()));
    }

    private static boolean matchesStoredEnchantmentName(ResourceKey<Enchantment> enchantment, String stored) {
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        ResourceLocation location = enchantment.location();
        String path = location.getPath();
        return stored.equals(path)
                || stored.equals(location.toString())
                || stored.equalsIgnoreCase(toOriginalName(path));
    }

    private static String toOriginalName(String path) {
        StringBuilder builder = new StringBuilder();
        boolean capitalize = true;
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            if (character == '_') {
                capitalize = true;
                continue;
            }
            builder.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = false;
        }
        return builder.toString();
    }
}
