package com.stardew.craft.port.net.minecraft.core.component;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;

/**
 * 1.21 item data component type, backed by the 1.20.1 ItemStack NBT.
 *
 * <p>Each type knows how to read, write and clear its value in the vanilla 1.20.1 tag layout (see
 * {@link DataComponents}). Stack access goes through {@code com.stardew.craft.port.PortItemData}, which
 * mirrors {@code ItemStack#get/set/has/remove/update/getOrDefault}. The mod registers no custom component
 * types, so there is no registry or builder.
 */
public final class DataComponentType<T> {
    /** NBT mapping of one component. */
    public interface NbtAccessor<T> {
        @Nullable
        T read(ItemStack stack);

        void write(ItemStack stack, T value);

        void clear(ItemStack stack);
    }

    private final String name;
    private final NbtAccessor<T> accessor;

    DataComponentType(String name, NbtAccessor<T> accessor) {
        this.name = Objects.requireNonNull(name);
        this.accessor = Objects.requireNonNull(accessor);
    }

    /** Component id path, e.g. {@code custom_data}. */
    public String portName() {
        return name;
    }

    @Nullable
    public T portRead(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        return accessor.read(stack);
    }

    public void portWrite(ItemStack stack, T value) {
        accessor.write(stack, Objects.requireNonNull(value, "component value"));
    }

    public void portClear(ItemStack stack) {
        accessor.clear(stack);
    }

    @Override
    public String toString() {
        return "minecraft:" + name;
    }
}
