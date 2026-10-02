package com.stardew.craft.port.net.minecraft.world.item.component;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.stardew.craft.port.net.minecraft.world.entity.EquipmentSlotGroup;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * 1.21 {@code minecraft:attribute_modifiers}; stored as the vanilla 1.20.1 {@code AttributeModifiers} list
 * ({@code AttributeName, Name, Amount, Operation, UUID, Slot}) plus the HideFlags MODIFIERS bit.
 * A slot group spanning several slots is written as one entry per slot; {@link EquipmentSlotGroup#ANY}
 * omits {@code Slot}, which 1.20.1 applies to every slot.
 */
public record ItemAttributeModifiers(List<Entry> modifiers, boolean showInTooltip) {
    public static final ItemAttributeModifiers EMPTY = new ItemAttributeModifiers(List.of(), true);

    public ItemAttributeModifiers {
        modifiers = List.copyOf(modifiers);
    }

    public ItemAttributeModifiers withTooltip(boolean showInTooltip) {
        return new ItemAttributeModifiers(modifiers, showInTooltip);
    }

    public ItemAttributeModifiers withModifierAdded(Holder<Attribute> attribute, AttributeModifier modifier,
            EquipmentSlotGroup slot) {
        ImmutableList.Builder<Entry> builder = ImmutableList.builderWithExpectedSize(modifiers.size() + 1);
        for (Entry entry : modifiers) {
            if (!entry.matches(attribute, modifier.getId())) {
                builder.add(entry);
            }
        }
        builder.add(new Entry(attribute, modifier, slot));
        return new ItemAttributeModifiers(builder.build(), showInTooltip);
    }

    public void forEach(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        for (Entry entry : modifiers) {
            if (entry.slot().test(slot)) {
                action.accept(entry.attribute(), entry.modifier());
            }
        }
    }

    public double compute(double base, EquipmentSlot slot) {
        double value = base;
        for (Entry entry : modifiers) {
            if (entry.slot().test(slot)) {
                double amount = entry.modifier().getAmount();
                value += switch (entry.modifier().getOperation()) {
                    case ADDITION -> amount;
                    case MULTIPLY_BASE -> amount * base;
                    case MULTIPLY_TOTAL -> amount * value;
                };
            }
        }
        return value;
    }

    /** PORT(1.20.1): the per-slot multimap that {@code Item#getDefaultAttributeModifiers(EquipmentSlot)} returns. */
    public Multimap<Attribute, AttributeModifier> portModifiers(EquipmentSlot slot) {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        forEach(slot, (attribute, modifier) -> builder.put(attribute.value(), modifier));
        return builder.build();
    }

    /** PORT(1.20.1): vanilla 1.20.1 {@code AttributeModifiers} list layout. */
    public ListTag toTag() {
        ListTag list = new ListTag();
        for (Entry entry : modifiers) {
            ResourceLocation id = entry.attribute().unwrapKey().map(key -> key.location())
                    .orElseGet(() -> BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value()));
            if (id == null) {
                continue;
            }
            List<EquipmentSlot> slots = entry.slot() == EquipmentSlotGroup.ANY ? List.of() : entry.slot().portSlots();
            if (entry.slot() == EquipmentSlotGroup.ANY) {
                list.add(entryTag(id, entry.modifier(), null));
            }
            for (EquipmentSlot slot : slots) {
                list.add(entryTag(id, entry.modifier(), slot));
            }
        }
        return list;
    }

    private static CompoundTag entryTag(ResourceLocation attribute, AttributeModifier modifier, EquipmentSlot slot) {
        CompoundTag tag = modifier.save();
        tag.putString("AttributeName", attribute.toString());
        if (slot != null) {
            tag.putString("Slot", slot.getName());
        }
        return tag;
    }

    /** PORT(1.20.1): inverse of {@link #toTag()}; unknown attributes and malformed entries are skipped like vanilla. */
    public static ItemAttributeModifiers fromTag(ListTag list, boolean showInTooltip) {
        ImmutableList.Builder<Entry> builder = ImmutableList.builder();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("AttributeName"));
            if (id == null) {
                continue;
            }
            var attribute = BuiltInRegistries.ATTRIBUTE.getHolder(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ATTRIBUTE, id));
            if (attribute.isEmpty()) {
                continue;
            }
            AttributeModifier modifier = AttributeModifier.load(tag);
            if (modifier == null) {
                continue;
            }
            EquipmentSlotGroup slot = tag.contains("Slot", Tag.TAG_STRING)
                    ? EquipmentSlotGroup.bySlot(EquipmentSlot.byName(tag.getString("Slot")))
                    : EquipmentSlotGroup.ANY;
            builder.add(new Entry(attribute.get(), modifier, slot));
        }
        return new ItemAttributeModifiers(builder.build(), showInTooltip);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final ImmutableList.Builder<Entry> entries = ImmutableList.builder();

        Builder() {
        }

        public Builder add(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
            entries.add(new Entry(attribute, modifier, slot));
            return this;
        }

        /** PORT(1.20.1): attributes are plain registry objects ({@code Attributes.X}) on 1.20.1. */
        public Builder add(Attribute attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
            return add(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute), modifier, slot);
        }

        public ItemAttributeModifiers build() {
            return new ItemAttributeModifiers(entries.build(), true);
        }
    }

    public record Entry(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
        public boolean matches(Holder<Attribute> attribute, UUID id) {
            return sameAttribute(this.attribute, attribute) && modifier.getId().equals(id);
        }

        private static boolean sameAttribute(Holder<Attribute> left, Holder<Attribute> right) {
            return left == right || left.value() == right.value();
        }
    }
}
