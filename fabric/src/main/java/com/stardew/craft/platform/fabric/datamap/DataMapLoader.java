package com.stardew.craft.platform.fabric.datamap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decode during reload; resolve tags only after vanilla binds the new tag sets. */
public final class DataMapLoader implements PreparableReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("stardewcraft/fabric-data-maps");
    private final RegistryAccess registries;
    private final List<DataMapType<?, ?>> types;
    private Map<DataMapType<?, ?>, List<File<?, ?>>> prepared;

    public DataMapLoader(RegistryAccess registries) {
        this(registries, DataMaps.types());
        DataMaps.seal();
    }

    DataMapLoader(RegistryAccess registries, List<DataMapType<?, ?>> types) {
        this.registries = registries;
        this.types = List.copyOf(types);
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
            ProfilerFiller preparationProfiler, ProfilerFiller applyProfiler, Executor background, Executor game) {
        return CompletableFuture.supplyAsync(() -> load(manager), background)
                .thenCompose(barrier::wait).thenAcceptAsync(result -> prepared = result, game);
    }

    private Map<DataMapType<?, ?>, List<File<?, ?>>> load(ResourceManager manager) {
        Map<DataMapType<?, ?>, List<File<?, ?>>> result = new IdentityHashMap<>();
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        for (var type : types) {
            var id = fileLocation(type);
            List<File<?, ?>> files = new ArrayList<>();
            for (var resource : manager.getResourceStack(id)) {
                try (Reader reader = resource.openAsReader()) {
                    files.add(read(type, JsonParser.parseReader(reader), ops));
                } catch (Exception failure) {
                    if (failure instanceof UnsupportedOperationException unsupported) throw unsupported;
                    LOGGER.error("Could not read data map {} from pack {}", id, resource.sourcePackId(), failure);
                }
            }
            // Empty lists deliberately clear data when a map's last file disappears.
            result.put(type, files);
        }
        return result;
    }

    public static ResourceLocation fileLocation(DataMapType<?, ?> type) {
        var registry = type.registryKey().location();
        String folder = (registry.getNamespace().equals("minecraft") ? "" : registry.getNamespace() + "/") + registry.getPath();
        return ResourceLocation.fromNamespaceAndPath(type.id().getNamespace(), "data_maps/" + folder + "/" + type.id().getPath() + ".json");
    }

    /** Called on the server thread at updateRegistryTags RETURN, including initial world loading. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void applyAfterTags() {
        if (prepared == null) throw new IllegalStateException("Data maps were not prepared before tags were bound");
        Map<HolderOwner<?>, Map<DataMapType<?, ?>, Map<?, ?>>> snapshots = new IdentityHashMap<>();
        prepared.forEach((type, files) -> {
            Registry registry = registries.registryOrThrow(type.registryKey());
            snapshots.computeIfAbsent(registry.holderOwner(), ignored -> new IdentityHashMap<>())
                    .put(type, build(registry, type, (List) files));
        });
        DataMaps.publishServer(snapshots);
        prepared = null;
    }

    static <R, T> File<R, T> read(DataMapType<R, T> type, JsonElement input, DynamicOps<JsonElement> ops) {
        JsonObject json = input.getAsJsonObject();
        rejectConditions(json);
        JsonObject values = json.getAsJsonObject("values");
        if (values == null) throw new IllegalArgumentException("Data map values are required");
        List<Value<R, T>> additions = new ArrayList<>();
        values.entrySet().forEach(entry -> {
            JsonElement raw = entry.getValue();
            if (raw.isJsonObject()) rejectConditions(raw.getAsJsonObject());
            T value;
            boolean replace = false;
            // NeoForge tries the wrapper codec first and the direct codec second.
            var wrapper = raw.isJsonObject() ? raw.getAsJsonObject() : null;
            var decodedWrapper = wrapper != null && wrapper.has("value")
                    ? type.codec().parse(ops, wrapper.get("value")).result() : Optional.<T>empty();
            if (decodedWrapper.isPresent()) {
                value = decodedWrapper.get();
                replace = bool(wrapper, "replace");
            } else {
                value = type.codec().parse(ops, raw).getOrThrow();
            }
            additions.add(new Value<>(source(type, entry.getKey()), value, replace));
        });
        List<Removal<R, T>> removals = new ArrayList<>();
        if (json.has("remove")) {
            JsonElement remove = json.get("remove");
            if (remove.isJsonObject()) {
                if (!(type instanceof AdvancedDataMapType<R, T, ?> advanced)) throw new IllegalArgumentException("Object removals require an advanced map");
                remove.getAsJsonObject().entrySet().forEach(entry -> removals.add(new Removal<>(source(type, entry.getKey()),
                        Optional.of(advanced.remover().parse(ops, entry.getValue()).getOrThrow()))));
            } else {
                for (JsonElement entry : remove.getAsJsonArray()) {
                    if (entry.isJsonPrimitive()) {
                        removals.add(new Removal<>(source(type, entry.getAsString()), Optional.empty()));
                    } else {
                        if (!(type instanceof AdvancedDataMapType<R, T, ?> advanced)) throw new IllegalArgumentException("Object removals require an advanced map");
                        var removal = entry.getAsJsonObject();
                        Optional<DataMapValueRemover<R, T>> remover = removal.has("remover")
                                ? Optional.of(advanced.remover().parse(ops, removal.get("remover")).getOrThrow()) : Optional.empty();
                        removals.add(new Removal<>(source(type, removal.get("key").getAsString()), remover));
                    }
                }
            }
        }
        return new File<>(bool(json, "replace"), List.copyOf(additions), List.copyOf(removals));
    }

    private static boolean bool(JsonObject json, String key) {
        if (!json.has(key)) return false;
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException(key + " must be boolean");
        return json.get(key).getAsBoolean();
    }

    private static void rejectConditions(JsonObject json) {
        if (json.has("neoforge:conditions") || json.has("fabric:load_conditions")) {
            throw new UnsupportedOperationException("Conditional data map entries need an explicit condition adapter; refusing unconditional attachment");
        }
    }

    private static <R> Either<TagKey<R>, ResourceKey<R>> source(DataMapType<R, ?> type, String text) {
        return text.startsWith("#") ? Either.left(TagKey.create(type.registryKey(), ResourceLocation.parse(text.substring(1))))
                : Either.right(ResourceKey.create(type.registryKey(), ResourceLocation.parse(text)));
    }

    static <R, T> Map<ResourceKey<R>, T> build(Registry<R> registry, DataMapType<R, T> type, List<File<R, T>> files) {
        Map<ResourceKey<R>, Resolved<R, T>> result = new LinkedHashMap<>();
        DataMapValueMerger<R, T> merger = type instanceof AdvancedDataMapType<R, T, ?> advanced
                ? advanced.merger() : DataMapValueMerger.defaultMerger();
        for (var file : files) {
            if (file.replace()) result.clear();
            for (var entry : file.values()) resolve(registry, entry.source(), true, holder -> {
                var key = holder.unwrapKey().orElseThrow();
                var previous = result.get(key);
                T value = previous == null || entry.replace() ? entry.value()
                        : merger.merge(registry, previous.source(), previous.value(), entry.source(), entry.value());
                result.put(key, new Resolved<>(entry.source(), value));
            });
            for (var removal : file.removals()) resolve(registry, removal.source(), false, holder -> {
                var key = holder.unwrapKey().orElseThrow();
                var previous = result.get(key);
                if (previous == null) return;
                Optional<T> remainder = removal.remover().isEmpty() ? Optional.empty()
                        : removal.remover().get().remove(previous.value(), registry, previous.source(), holder.value());
                if (remainder.isEmpty()) result.remove(key);
                else result.put(key, new Resolved<>(previous.source(), remainder.get()));
            });
        }
        Map<ResourceKey<R>, T> values = new LinkedHashMap<>();
        result.forEach((key, value) -> values.put(key, value.value()));
        return Map.copyOf(values);
    }

    private static <R> void resolve(Registry<R> registry, Either<TagKey<R>, ResourceKey<R>> source,
            boolean required, Consumer<Holder<R>> action) {
        source.map(tag -> {
            registry.getTagOrEmpty(tag).forEach(action);
            return null;
        }, key -> {
            var holder = registry.getHolder(key);
            if (holder.isPresent()) action.accept(holder.get());
            else if (required) LOGGER.error("Unknown registry object {} in data map for {}", key.location(), registry.key().location());
            return null;
        });
    }

    record File<R, T>(boolean replace, List<Value<R, T>> values, List<Removal<R, T>> removals) {}
    record Value<R, T>(Either<TagKey<R>, ResourceKey<R>> source, T value, boolean replace) {}
    record Removal<R, T>(Either<TagKey<R>, ResourceKey<R>> source, Optional<DataMapValueRemover<R, T>> remover) {}
    private record Resolved<R, T>(Either<TagKey<R>, ResourceKey<R>> source, T value) {}
}
