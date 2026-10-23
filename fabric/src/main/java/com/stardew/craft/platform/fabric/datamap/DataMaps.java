package com.stardew.craft.platform.fabric.datamap;

import com.stardew.craft.platform.mixin.datamap.mixin.HolderOwnerAccessor;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Values belong to the actual holder owner, not a process-wide registry-key fallback. */
public final class DataMaps {
    private static final Map<Key, DataMapType<?, ?>> TYPES = new LinkedHashMap<>();
    // Built-in registries are static and are shared by both logical sides of an
    // integrated server. Client network projections must never replace the
    // authoritative values (which may include unsynced maps or private fields).
    private static final Map<HolderOwner<?>, Map<DataMapType<?, ?>, Map<?, ?>>> SERVER_VALUES = new WeakHashMap<>();
    private static final Map<HolderOwner<?>, Map<DataMapType<?, ?>, Map<?, ?>>> CLIENT_VALUES = new WeakHashMap<>();
    private static boolean sealed;

    private DataMaps() {}

    static synchronized void register(DataMapType<?, ?> type) {
        Objects.requireNonNull(type, "type");
        if (sealed) throw new IllegalStateException("Data map type registration is closed");
        if (type.networkCodec() != null && !BuiltInRegistries.REGISTRY.containsKey(type.registryKey().location())) {
            throw new UnsupportedOperationException("Early configuration data map sync currently supports built-in registries only: " + type.registryKey().location());
        }
        Key key = new Key(type.registryKey().location(), type.id());
        if (TYPES.putIfAbsent(key, type) != null) throw new IllegalArgumentException("Duplicate data map: " + key);
    }

    public static synchronized List<DataMapType<?, ?>> types() { return List.copyOf(TYPES.values()); }
    static synchronized void seal() { sealed = true; }
    public static synchronized @Nullable DataMapType<?, ?> type(ResourceLocation registry, ResourceLocation id) {
        return TYPES.get(new Key(registry, id));
    }

    public static <R, T> @Nullable T get(Holder<R> holder, DataMapType<R, T> type) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(type, "type");
        if (holder instanceof Holder.Direct<R>) return null;
        if (!(holder instanceof HolderOwnerAccessor accessor)) {
            throw new IllegalStateException("Holder owner mixin is missing for " + holder.getClass().getName());
        }
        var key = holder.unwrapKey().orElse(null);
        return key == null ? null : get(accessor.stardewcraft$dataMapOwner(), type, key);
    }

    @SuppressWarnings("unchecked")
    static synchronized <R, T> @Nullable T get(HolderOwner<?> owner, DataMapType<R, T> type, ResourceKey<R> key) {
        return (T) visible(owner).getOrDefault(type, Map.of()).get(key);
    }

    @SuppressWarnings("unchecked")
    public static synchronized <R, T> Map<ResourceKey<R>, T> getDataMap(Registry<R> registry, DataMapType<R, T> type) {
        return (Map<ResourceKey<R>, T>) visible(registry.holderOwner()).getOrDefault(type, Map.of());
    }

    @SuppressWarnings("unchecked")
    static synchronized <R, T> Map<ResourceKey<R>, T> getServerDataMap(Registry<R> registry, DataMapType<R, T> type) {
        return (Map<ResourceKey<R>, T>) SERVER_VALUES.getOrDefault(registry.holderOwner(), Map.of()).getOrDefault(type, Map.of());
    }

    private static Map<DataMapType<?, ?>, Map<?, ?>> visible(HolderOwner<?> owner) {
        // The integrated client's seven maps use the same full network codec.
        // Prefer authority for a shared owner instead of exposing a lossy client
        // projection to the server thread through this holder-only API.
        // This is not thread-based selection of two distinct codec views: an
        // integrated client also sees authority while that shared owner is live.
        return SERVER_VALUES.getOrDefault(owner, CLIENT_VALUES.getOrDefault(owner, Map.of()));
    }

    static synchronized void publishServer(Map<HolderOwner<?>, Map<DataMapType<?, ?>, Map<?, ?>>> snapshots) {
        publishTo(SERVER_VALUES, snapshots);
    }

    static synchronized void publishClient(Map<HolderOwner<?>, Map<DataMapType<?, ?>, Map<?, ?>>> snapshots) {
        publishTo(CLIENT_VALUES, snapshots);
    }

    private static void publishTo(Map<HolderOwner<?>, Map<DataMapType<?, ?>, Map<?, ?>>> destination,
            Map<HolderOwner<?>, Map<DataMapType<?, ?>, Map<?, ?>>> snapshots) {
        snapshots.forEach((owner, maps) -> {
            Map<DataMapType<?, ?>, Map<?, ?>> immutable = new IdentityHashMap<>();
            maps.forEach((type, values) -> immutable.put(type, Map.copyOf(values)));
            destination.put(owner, Collections.unmodifiableMap(immutable));
        });
    }

    public static synchronized void clearClient(HolderOwner<?> owner) { CLIENT_VALUES.remove(owner); }
    public static synchronized void clearServer(HolderOwner<?> owner) { SERVER_VALUES.remove(owner); }

    public record Key(ResourceLocation registry, ResourceLocation id) {}
}
