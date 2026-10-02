package com.stardew.craft.port.net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.stardew.craft.port.PortItemData;
import com.stardew.craft.port.net.minecraft.core.component.DataComponentType;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;

/**
 * 1.21 {@code minecraft:custom_data}: an immutable snapshot of a compound tag.
 * On 1.20.1 the stack's custom data is its root tag minus the vanilla-reserved keys
 * (see {@code PortItemNbt.RESERVED_ROOT_KEYS}).
 */
public final class CustomData {
    public static final CustomData EMPTY = new CustomData(new CompoundTag());
    public static final Codec<CustomData> CODEC = CompoundTag.CODEC.xmap(CustomData::new, data -> data.tag);

    private final CompoundTag tag;

    private CustomData(CompoundTag tag) {
        this.tag = tag;
    }

    public static CustomData of(CompoundTag tag) {
        return new CustomData(tag.copy());
    }

    public static Predicate<ItemStack> itemMatcher(DataComponentType<CustomData> type, CompoundTag expected) {
        return stack -> PortItemData.getOrDefault(stack, type, EMPTY).matchedBy(expected);
    }

    public boolean matchedBy(CompoundTag expected) {
        return NbtUtils.compareNbt(expected, tag, true);
    }

    public static void update(DataComponentType<CustomData> type, ItemStack stack, Consumer<CompoundTag> updater) {
        CustomData updated = PortItemData.getOrDefault(stack, type, EMPTY).update(updater);
        if (updated.tag.isEmpty()) {
            PortItemData.remove(stack, type);
        } else {
            PortItemData.set(stack, type, updated);
        }
    }

    public static void set(DataComponentType<CustomData> type, ItemStack stack, CompoundTag tag) {
        if (!tag.isEmpty()) {
            PortItemData.set(stack, type, of(tag));
        } else {
            PortItemData.remove(stack, type);
        }
    }

    public CustomData update(Consumer<CompoundTag> updater) {
        CompoundTag copy = tag.copy();
        updater.accept(copy);
        return new CustomData(copy);
    }

    public int size() {
        return tag.size();
    }

    public boolean isEmpty() {
        return tag.isEmpty();
    }

    public boolean contains(String key) {
        return tag.contains(key);
    }

    public CompoundTag copyTag() {
        return tag.copy();
    }

    /** @deprecated mirrors 1.21: the returned tag must not be mutated. */
    @Deprecated
    public CompoundTag getUnsafe() {
        return tag;
    }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof CustomData data && tag.equals(data.tag);
    }

    @Override
    public int hashCode() {
        return tag.hashCode();
    }

    @Override
    public String toString() {
        return tag.toString();
    }
}
