package com.stardew.craft.port;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * PORT(1.20.1): 1.21.1 decides which items a vanilla enchantment applies to from data — the enchantment's
 * {@code supported_items} / {@code primary_items} item sets (the {@code #minecraft:enchantable/*} tags, which the
 * mod feeds through {@code #minecraft:swords/axes/pickaxes/hoes}) and its {@code exclusive_set} enchantment tag.
 * 1.20.1 decides it with {@code EnchantmentCategory} class checks plus per-class shortcuts (any damageable item
 * takes Unbreaking, any {@code AxeItem} takes Sharpness, any {@code FishingRodItem} takes Lure, ...).
 * <p>
 * For StardewCraft items only (item id namespace {@code stardewcraft}) and the vanilla enchantments listed in the
 * generated {@link PortVanillaEnchantmentTable}, this class answers with the 1.21.1 rules, evaluated against the
 * live item/enchantment tags (so datapacks keep working as in 1.21.1):
 * <ul>
 *   <li>{@link #supports} = 1.21 {@code ItemStack#supportsEnchantment} / {@code Enchantment#isSupportedItem}:
 *   anvil, {@code /enchant}, {@code enchant_randomly}; wired into {@code Enchantment#canEnchant}.</li>
 *   <li>{@link #isPrimaryItemFor} = 1.21 {@code ItemStack#isPrimaryItemFor}: enchanting table and
 *   {@code enchant_with_levels}; wired into {@code Enchantment#canApplyAtEnchantingTable(ItemStack)}.</li>
 *   <li>{@link #areCompatible} = 1.21 {@code Enchantment#areCompatible} on the exclusive-set tags, used where
 *   1.20.1 checks compatibility for a known target item (anvil, {@code /enchant}, enchanting-table selection).</li>
 * </ul>
 * Vanilla items, other mods' items and non-vanilla enchantments keep their 1.20.1 behaviour (every method returns
 * {@code null} / the original result for them). The mod's own data enchantments are handled by
 * {@link PortEnchantments.DataEnchantment}.
 */
public final class PortVanillaEnchantmentRules {
    private record Rule(Predicate<Holder<Item>> supported, @Nullable Predicate<Holder<Item>> primary,
                        @Nullable TagKey<Enchantment> exclusiveSet) {}

    private PortVanillaEnchantmentRules() {}

    private static final class Lazy {
        static final Map<Enchantment, Rule> RULES = build();

        private static Map<Enchantment, Rule> build() {
            Map<Enchantment, Rule> rules = new IdentityHashMap<>();
            for (PortVanillaEnchantmentTable.Entry entry : PortVanillaEnchantmentTable.ENTRIES) {
                Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(new ResourceLocation(entry.id()));
                if (enchantment == null) {
                    throw new IllegalStateException("PORT(1.20.1): vanilla enchantment " + entry.id() + " is not registered");
                }
                TagKey<Enchantment> exclusive = null;
                if (entry.exclusiveSet() != null) {
                    if (!entry.exclusiveSet().startsWith("#")) {
                        throw new IllegalStateException("PORT(1.20.1): non-tag exclusive_set " + entry.exclusiveSet());
                    }
                    exclusive = TagKey.create(Registries.ENCHANTMENT, new ResourceLocation(entry.exclusiveSet().substring(1)));
                }
                rules.put(enchantment, new Rule(itemSet(entry.supportedItems()),
                        entry.primaryItems() == null ? null : itemSet(entry.primaryItems()), exclusive));
            }
            return rules;
        }

        private static Predicate<Holder<Item>> itemSet(String value) {
            if (value.startsWith("#")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(value.substring(1)));
                return holder -> holder.is(tag);
            }
            ResourceLocation id = new ResourceLocation(value);
            return holder -> holder.is(id);
        }
    }

    /** Whether the 1.21.1 rules apply to this stack (a StardewCraft item). */
    public static boolean isModItem(ItemStack stack) {
        return !stack.isEmpty()
                && PortBootstrap.NAMESPACE.equals(stack.getItem().builtInRegistryHolder().key().location().getNamespace());
    }

    @Nullable
    private static Rule rule(Enchantment enchantment) {
        return Lazy.RULES.get(enchantment);
    }

    /** 1.21 {@code supportsEnchantment} for a StardewCraft item; {@code null} = keep the 1.20.1 answer. */
    @Nullable
    public static Boolean supports(Enchantment enchantment, ItemStack stack) {
        if (!isModItem(stack)) return null;
        Rule rule = rule(enchantment);
        return rule == null ? null : rule.supported().test(stack.getItem().builtInRegistryHolder());
    }

    /** 1.21 {@code isPrimaryItemFor} for a StardewCraft item; {@code null} = keep the 1.20.1 answer. */
    @Nullable
    public static Boolean isPrimaryItemFor(Enchantment enchantment, ItemStack stack) {
        if (!isModItem(stack)) return null;
        Rule rule = rule(enchantment);
        if (rule == null) return null;
        Holder<Item> item = stack.getItem().builtInRegistryHolder();
        return rule.supported().test(item) && (rule.primary() == null || rule.primary().test(item));
    }

    /**
     * 1.21 {@code Enchantment.areCompatible(a, b)} when the target is a StardewCraft item and both enchantments are
     * ported vanilla ones; otherwise the 1.20.1 {@code a.isCompatibleWith(b)}.
     */
    public static boolean areCompatible(ItemStack target, Enchantment a, Enchantment b) {
        if (isModItem(target)) {
            Rule ruleA = rule(a);
            Rule ruleB = rule(b);
            if (ruleA != null && ruleB != null) {
                return a != b && !inSet(ruleA, b) && !inSet(ruleB, a);
            }
        }
        return a.isCompatibleWith(b);
    }

    private static boolean inSet(Rule rule, Enchantment other) {
        return rule.exclusiveSet() != null && BuiltInRegistries.ENCHANTMENT.wrapAsHolder(other).is(rule.exclusiveSet());
    }

    /** {@code EnchantmentHelper#isEnchantmentCompatible} with {@link #areCompatible}. */
    public static boolean isEnchantmentCompatible(ItemStack target, Collection<Enchantment> existing, Enchantment added) {
        for (Enchantment enchantment : existing) {
            if (!areCompatible(target, enchantment, added)) return false;
        }
        return true;
    }

    /** {@code EnchantmentHelper#filterCompatibleEnchantments} with {@link #areCompatible}. */
    public static void filterCompatibleEnchantments(ItemStack target, List<EnchantmentInstance> candidates, EnchantmentInstance chosen) {
        candidates.removeIf(candidate -> !areCompatible(target, chosen.enchantment, candidate.enchantment));
    }
}
