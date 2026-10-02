package com.stardew.craft.port.net.minecraft.world.item.alchemy;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

/**
 * 1.21 {@code minecraft:potion_contents}; stored as the vanilla 1.20.1 {@code Potion},
 * {@code CustomPotionColor} and {@code CustomPotionEffects} root keys.
 */
public record PotionContents(Optional<Holder<Potion>> potion, Optional<Integer> customColor,
        List<MobEffectInstance> customEffects) {
    public static final PotionContents EMPTY = new PotionContents(Optional.empty(), Optional.empty(), List.of());

    public PotionContents {
        customEffects = List.copyOf(customEffects);
    }

    public PotionContents(Holder<Potion> potion) {
        this(Optional.of(potion), Optional.empty(), List.of());
    }

    public static ItemStack createItemStack(Item item, Holder<Potion> potion) {
        ItemStack stack = new ItemStack(item);
        PortItemData.set(stack, DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }

    public boolean is(Holder<Potion> potion) {
        return this.potion.isPresent() && this.potion.get().value() == potion.value();
    }

    public PotionContents withPotion(Holder<Potion> potion) {
        return new PotionContents(Optional.of(potion), customColor, customEffects);
    }

    public PotionContents withEffectAdded(MobEffectInstance effect) {
        List<MobEffectInstance> effects = new ArrayList<>(customEffects);
        effects.add(effect);
        return new PotionContents(potion, customColor, effects);
    }

    public Iterable<MobEffectInstance> getAllEffects() {
        List<MobEffectInstance> effects = new ArrayList<>();
        potion.ifPresent(holder -> effects.addAll(holder.value().getEffects()));
        effects.addAll(customEffects);
        return effects;
    }

    public boolean hasEffects() {
        return !customEffects.isEmpty() || potion.map(holder -> !holder.value().getEffects().isEmpty()).orElse(false);
    }

    public int getColor() {
        if (customColor.isPresent()) {
            return customColor.get();
        }
        List<MobEffectInstance> effects = new ArrayList<>();
        getAllEffects().forEach(effects::add);
        return effects.isEmpty() ? -13083194 : PotionUtils.getColor(effects);
    }

    /** PORT(1.20.1): write into the stack's root tag in the vanilla layout. */
    public void portWrite(ItemStack stack) {
        portClear(stack);
        potion.ifPresent(holder -> {
            var id = holder.unwrapKey().map(key -> key.location())
                    .orElseGet(() -> BuiltInRegistries.POTION.getKey(holder.value()));
            stack.getOrCreateTag().putString(PotionUtils.TAG_POTION, id.toString());
        });
        customColor.ifPresent(color -> stack.getOrCreateTag().putInt(PotionUtils.TAG_CUSTOM_POTION_COLOR, color));
        if (!customEffects.isEmpty()) {
            ListTag list = new ListTag();
            for (MobEffectInstance effect : customEffects) {
                list.add(effect.save(new CompoundTag()));
            }
            stack.getOrCreateTag().put(PotionUtils.TAG_CUSTOM_POTION_EFFECTS, list);
        }
    }

    /** PORT(1.20.1): read from the vanilla layout; {@code null} when the stack carries no potion data. */
    public static PotionContents portRead(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !(tag.contains(PotionUtils.TAG_POTION, Tag.TAG_STRING)
                || tag.contains(PotionUtils.TAG_CUSTOM_POTION_COLOR, Tag.TAG_ANY_NUMERIC)
                || tag.contains(PotionUtils.TAG_CUSTOM_POTION_EFFECTS, Tag.TAG_LIST))) {
            return null;
        }
        Optional<Holder<Potion>> potion = Optional.empty();
        if (tag.contains(PotionUtils.TAG_POTION, Tag.TAG_STRING)) {
            Potion value = PotionUtils.getPotion(tag);
            if (value != Potions.EMPTY) {
                potion = Optional.of(BuiltInRegistries.POTION.wrapAsHolder(value));
            }
        }
        Optional<Integer> color = tag.contains(PotionUtils.TAG_CUSTOM_POTION_COLOR, Tag.TAG_ANY_NUMERIC)
                ? Optional.of(tag.getInt(PotionUtils.TAG_CUSTOM_POTION_COLOR)) : Optional.empty();
        return new PotionContents(potion, color, PotionUtils.getCustomEffects(tag));
    }

    /** PORT(1.20.1): remove every potion key. */
    public static void portClear(ItemStack stack) {
        stack.removeTagKey(PotionUtils.TAG_POTION);
        stack.removeTagKey(PotionUtils.TAG_CUSTOM_POTION_COLOR);
        stack.removeTagKey(PotionUtils.TAG_CUSTOM_POTION_EFFECTS);
    }
}
