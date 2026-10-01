package com.stardew.craft.port;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 1.21 ItemStack (de)serialization entry points on top of the 1.20.1 NBT format
 * ({@code {id, Count, tag, ForgeCaps}}). The registry provider parameter is kept so call sites stay
 * unchanged; 1.20.1 item NBT does not need it.
 */
public final class PortItemStacks {
    private PortItemStacks() {
    }

    /** 1.21 {@code ItemStack.parse(provider, tag)}: empty when the tag does not describe a valid stack. */
    public static Optional<ItemStack> parse(HolderLookup.Provider registries, Tag tag) {
        if (!(tag instanceof CompoundTag compound) || compound.isEmpty()) {
            return Optional.empty();
        }
        ItemStack stack = ItemStack.of(compound);
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
    }

    /** 1.21 {@code ItemStack.parseOptional(provider, tag)}: an empty tag or invalid stack yields EMPTY. */
    public static ItemStack parseOptional(HolderLookup.Provider registries, CompoundTag tag) {
        return tag.isEmpty() ? ItemStack.EMPTY : ItemStack.of(tag);
    }

    /** 1.21 {@code stack.save(provider)}: like 1.21 it rejects empty stacks. */
    public static Tag save(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        }
        return stack.save(new CompoundTag());
    }

    /** 1.21 {@code stack.save(provider, prefix)}: writes into {@code prefix}. */
    public static Tag save(ItemStack stack, HolderLookup.Provider registries, Tag prefix) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        }
        return stack.save(prefix instanceof CompoundTag compound ? compound : new CompoundTag());
    }

    /** 1.21 {@code stack.saveOptional(provider)}: EMPTY becomes an empty compound. */
    public static Tag saveOptional(ItemStack stack, HolderLookup.Provider registries) {
        return stack.isEmpty() ? new CompoundTag() : stack.save(new CompoundTag());
    }

    /** 1.21 {@code stack.consume(amount, entity)}: shrink unless the entity has infinite materials (creative). */
    public static void consume(ItemStack stack, int amount, @Nullable LivingEntity entity) {
        if (!(entity instanceof Player player && player.getAbilities().instabuild)) {
            stack.shrink(amount);
        }
    }

    /**
     * 1.21 {@code stack.forEachModifier(slot, action)}: the stack's effective modifiers for the slot (its
     * {@code AttributeModifiers} tag, else the item defaults, after Forge's ItemAttributeModifierEvent).
     * PORT(1.20.1): the attribute is passed as the plain {@link Attribute}, matching the 1.20.1 type of
     * {@code Attributes.X}, so call sites comparing against {@code Attributes.X} keep their meaning.
     */
    public static void forEachModifier(ItemStack stack, EquipmentSlot slot, BiConsumer<Attribute, AttributeModifier> action) {
        for (Map.Entry<Attribute, AttributeModifier> entry : stack.getAttributeModifiers(slot).entries()) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 1.21 {@code stack.enchant(holder, level)}: raises the enchantment to at least {@code level}
     * (1.21 upgrade semantics). Ids not registered on 1.20.1 are kept as stand-alone holders, see
     * {@link com.stardew.craft.port.net.minecraft.world.item.enchantment.ItemEnchantments}.
     */
    public static void enchant(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        var type = com.stardew.craft.port.net.minecraft.core.component.DataComponents.ENCHANTMENTS;
        var current = PortItemData.getOrDefault(stack, type,
                com.stardew.craft.port.net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        var mutable = new com.stardew.craft.port.net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(current);
        mutable.upgrade(enchantment, level);
        PortItemData.set(stack, type, mutable.toImmutable());
    }

    /**
     * 1.21 {@code target.applyComponents(source.getComponentsPatch())}: every component the source customises
     * replaces the target's. In 1.20.1 the customised data is the stack tag, one root key per component
     * ({@code PortItemNbt}), so each root key of the source tag replaces the target's (defaults the target got at
     * construction stay unless overridden). 1.21 "removed component" patch entries have no 1.20.1 counterpart.
     */
    public static void applyComponentsPatch(ItemStack target, ItemStack source) {
        net.minecraft.nbt.CompoundTag patch = source.getTag();
        if (target.isEmpty() || patch == null || patch.isEmpty()) {
            return;
        }
        net.minecraft.nbt.CompoundTag tag = target.getOrCreateTag();
        for (String key : patch.getAllKeys()) {
            net.minecraft.nbt.Tag value = patch.get(key);
            if (value != null) {
                tag.put(key, value.copy());
            }
        }
    }
}
