package com.stardew.craft.port;

import com.stardew.craft.port.net.minecraft.core.component.DataComponentType;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;

/**
 * 1.21 {@code ItemStack} data component accessors ({@code get/set/has/remove/update/getOrDefault}) for
 * 1.20.1, where stacks only carry NBT. Semantics follow 1.21: {@code set} with {@code null} removes,
 * {@code set}/{@code remove} return the previous value, {@code update} returns the stored value.
 * Every value returned is a fresh snapshot; mutating it does not change the stack.
 *
 * <p>Writes to the shared {@link ItemStack#EMPTY} instance are ignored so the singleton is never tagged.
 */
public final class PortItemData {
    private PortItemData() {
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T get(ItemStack stack, DataComponentType<? extends T> type) {
        return ((DataComponentType<T>) type).portRead(stack);
    }

    public static <T> T getOrDefault(ItemStack stack, DataComponentType<? extends T> type, T defaultValue) {
        T value = get(stack, type);
        return value != null ? value : defaultValue;
    }

    public static boolean has(ItemStack stack, DataComponentType<?> type) {
        return type.portRead(stack) != null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T set(ItemStack stack, DataComponentType<? super T> type, @Nullable T value) {
        DataComponentType<T> typed = (DataComponentType<T>) type;
        T previous = typed.portRead(stack);
        if (value == null) {
            typed.portClear(stack);
        } else {
            typed.portWrite(stack, value);
        }
        return previous;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T remove(ItemStack stack, DataComponentType<? extends T> type) {
        DataComponentType<T> typed = (DataComponentType<T>) type;
        T previous = typed.portRead(stack);
        if (previous != null) {
            typed.portClear(stack);
        }
        return previous;
    }

    @Nullable
    public static <T> T update(ItemStack stack, DataComponentType<T> type, T defaultValue, UnaryOperator<T> updater) {
        T current = getOrDefault(stack, type, defaultValue);
        T updated = updater.apply(current);
        set(stack, type, updated);
        return updated;
    }

    @Nullable
    public static <T, U> T update(ItemStack stack, DataComponentType<T> type, T defaultValue, U argument,
            BiFunction<T, U, T> updater) {
        return update(stack, type, defaultValue, current -> updater.apply(current, argument));
    }

    /** Copies one component from {@code source} to {@code target} (1.21 {@code copyFrom}). */
    public static <T> void copyFrom(ItemStack target, DataComponentType<T> type, ItemStack source) {
        set(target, type, get(source, type));
    }
}
