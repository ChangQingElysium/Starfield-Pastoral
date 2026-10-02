package com.stardew.craft.port.net.minecraft.world.item.enchantment;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * 1.21 {@code minecraft:enchantments} / {@code minecraft:stored_enchantments}; stored as the vanilla 1.20.1
 * {@code Enchantments}/{@code StoredEnchantments} list ({@code id, lvl}) plus the matching HideFlags bit.
 *
 * <p>Holders are canonical: a registered enchantment resolves to its built-in registry reference, and an id
 * that is not registered on 1.20.1 resolves to one shared stand-alone reference (key only), so the id is
 * preserved on round trips and key-based lookups keep working.
 */
public final class ItemEnchantments {
    public static final ItemEnchantments EMPTY = new ItemEnchantments(new Object2IntLinkedOpenHashMap<>(), true);
    private static final int MAX_LEVEL = 255;
    private static final Map<ResourceKey<Enchantment>, Holder.Reference<Enchantment>> UNBOUND = new ConcurrentHashMap<>();

    final Object2IntLinkedOpenHashMap<Holder<Enchantment>> enchantments;
    final boolean showInTooltip;

    ItemEnchantments(Object2IntLinkedOpenHashMap<Holder<Enchantment>> enchantments, boolean showInTooltip) {
        this.enchantments = enchantments;
        this.showInTooltip = showInTooltip;
    }

    /** PORT(1.20.1): canonical holder for an enchantment id (see class doc). */
    public static Holder<Enchantment> portHolder(ResourceLocation id) {
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
        var registered = BuiltInRegistries.ENCHANTMENT.getHolder(key);
        if (registered.isPresent()) {
            return registered.get();
        }
        return UNBOUND.computeIfAbsent(key,
                missing -> Holder.Reference.createStandAlone(BuiltInRegistries.ENCHANTMENT.holderOwner(), missing));
    }

    /** PORT(1.20.1): canonical holder for a 1.20.1 enchantment object. */
    public static Holder<Enchantment> portHolder(Enchantment enchantment) {
        return BuiltInRegistries.ENCHANTMENT.wrapAsHolder(enchantment);
    }

    static Holder<Enchantment> canonical(Holder<Enchantment> holder) {
        return holder.unwrapKey().map(key -> portHolder(key.location())).orElse(holder);
    }

    public int getLevel(Holder<Enchantment> enchantment) {
        return enchantments.getInt(canonical(enchantment));
    }

    public Set<Holder<Enchantment>> keySet() {
        return java.util.Collections.unmodifiableSet(enchantments.keySet());
    }

    public Set<Object2IntMap.Entry<Holder<Enchantment>>> entrySet() {
        return java.util.Collections.unmodifiableSet(enchantments.object2IntEntrySet());
    }

    public int size() {
        return enchantments.size();
    }

    public boolean isEmpty() {
        return enchantments.isEmpty();
    }

    public boolean showInTooltip() {
        return showInTooltip;
    }

    public ItemEnchantments withTooltip(boolean showInTooltip) {
        return new ItemEnchantments(enchantments, showInTooltip);
    }

    /** PORT(1.20.1): vanilla enchantment list layout ({@code id} string, {@code lvl} short). */
    public ListTag toTag() {
        ListTag list = new ListTag();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.object2IntEntrySet()) {
            ResourceLocation id = entry.getKey().unwrapKey().map(ResourceKey::location)
                    .orElseGet(() -> BuiltInRegistries.ENCHANTMENT.getKey(entry.getKey().value()));
            if (id != null) {
                list.add(EnchantmentHelper.storeEnchantment(id, entry.getIntValue()));
            }
        }
        return list;
    }

    /** PORT(1.20.1): inverse of {@link #toTag()}. */
    public static ItemEnchantments fromTag(ListTag list, boolean showInTooltip) {
        Object2IntLinkedOpenHashMap<Holder<Enchantment>> map = new Object2IntLinkedOpenHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            int level = EnchantmentHelper.getEnchantmentLevel(entry);
            if (id != null && level > 0) {
                map.put(portHolder(id), Math.min(level, MAX_LEVEL));
            }
        }
        return new ItemEnchantments(map, showInTooltip);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ItemEnchantments that
                && showInTooltip == that.showInTooltip && enchantments.equals(that.enchantments);
    }

    @Override
    public int hashCode() {
        return enchantments.hashCode() * 31 + (showInTooltip ? 1 : 0);
    }

    @Override
    public String toString() {
        return "ItemEnchantments{enchantments=" + enchantments + ", showInTooltip=" + showInTooltip + "}";
    }

    public static class Mutable {
        private final Object2IntLinkedOpenHashMap<Holder<Enchantment>> enchantments = new Object2IntLinkedOpenHashMap<>();
        private final boolean showInTooltip;

        public Mutable(ItemEnchantments enchantments) {
            this.enchantments.putAll(enchantments.enchantments);
            this.showInTooltip = enchantments.showInTooltip;
        }

        public void set(Holder<Enchantment> enchantment, int level) {
            Holder<Enchantment> key = canonical(enchantment);
            if (level <= 0) {
                enchantments.removeInt(key);
            } else {
                enchantments.put(key, Math.min(level, MAX_LEVEL));
            }
        }

        public void upgrade(Holder<Enchantment> enchantment, int level) {
            if (level > 0) {
                Holder<Enchantment> key = canonical(enchantment);
                enchantments.merge(key, Math.min(level, MAX_LEVEL), Integer::max);
            }
        }

        public void removeIf(Predicate<Holder<Enchantment>> predicate) {
            enchantments.keySet().removeIf(predicate);
        }

        public int getLevel(Holder<Enchantment> enchantment) {
            return enchantments.getOrDefault(canonical(enchantment), 0);
        }

        public Set<Holder<Enchantment>> keySet() {
            return enchantments.keySet();
        }

        public ItemEnchantments toImmutable() {
            return new ItemEnchantments(new Object2IntLinkedOpenHashMap<>(enchantments), showInTooltip);
        }
    }
}
