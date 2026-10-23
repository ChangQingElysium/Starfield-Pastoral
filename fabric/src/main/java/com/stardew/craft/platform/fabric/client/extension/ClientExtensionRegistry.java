package com.stardew.craft.platform.fabric.client.extension;

import java.lang.reflect.InvocationTargetException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
public final class ClientExtensionRegistry {
    private final Map<Item, IClientItemExtensions> items = new IdentityHashMap<>();
    private final Map<Block, IClientBlockExtensions> blocks = new IdentityHashMap<>();
    public IClientItemExtensions item(Item item) { return items.getOrDefault(item, IClientItemExtensions.DEFAULT); }
    public IClientBlockExtensions block(Block block) { return blocks.getOrDefault(block, IClientBlockExtensions.DEFAULT); }
    public boolean isItemRegistered(Item item) { return items.containsKey(item); }
    public boolean isBlockRegistered(Block block) { return blocks.containsKey(block); }
    public void registerItem(IClientItemExtensions extension, Item... targets) { register(items, extension, targets); }
    public void registerBlock(IClientBlockExtensions extension, Block... targets) { register(blocks, extension, targets); }
    private static <T, E> void register(Map<T, E> registry, E extension, T[] targets) {
        Objects.requireNonNull(extension, "extensions");
        if (targets.length == 0) throw new IllegalArgumentException("At least one extension target is required");
        var seen = new IdentityHashMap<T, Boolean>();
        for (T target : targets) {
            Objects.requireNonNull(target, "extension target");
            if (registry.containsKey(target) || seen.put(target, true) != null)
                throw new IllegalStateException("Duplicate client extensions for " + target);
        }
        for (T target : targets) registry.put(target, extension);
    }

    public void readLegacyItems(Iterable<Item> items) {
        items.forEach(item -> readLegacy(item, IClientItemExtensions.class, extension -> registerItem(extension, item)));
    }
    public void readLegacyBlocks(Iterable<Block> blocks) {
        blocks.forEach(block -> readLegacy(block, IClientBlockExtensions.class, extension -> registerBlock(extension, block)));
    }
    private static <E> void readLegacy(Object target, Class<E> extensionType, Consumer<E> register) {
        final java.lang.reflect.Method method;
        try { method = target.getClass().getMethod("initializeClient", Consumer.class); }
        catch (NoSuchMethodException absent) { return; }
        try {
            method.invoke(target, (Consumer<Object>) extension -> {
                if (!extensionType.isInstance(extension)) throw new IllegalArgumentException("Invalid " + extensionType.getSimpleName()
                        + " from " + target.getClass().getName() + ".initializeClient");
                register.accept(extensionType.cast(extension));
            });
        } catch (IllegalAccessException | InvocationTargetException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
            throw new IllegalStateException("Client initializeClient failed for " + target.getClass().getName(), cause);
        }
    }
}
