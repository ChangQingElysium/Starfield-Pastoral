package com.stardew.craft.platform.fabric.datamap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

/** Named registry keys and each type's network codec; never unstable numeric registry IDs. */
final class DataMapSnapshot {
    static final int MAX_BYTES = 16 * 1024 * 1024;

    private DataMapSnapshot() {}

    static byte[] encode(RegistryAccess registries, List<DataMapType<?, ?>> types) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        JsonArray maps = new JsonArray();
        for (var type : types) if (type.networkCodec() != null) maps.add(encodeMap(registries, type, ops));
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("version", 1);
        snapshot.add("maps", maps);
        byte[] result = snapshot.toString().getBytes(StandardCharsets.UTF_8);
        if (result.length > MAX_BYTES) throw new IllegalArgumentException("Data map snapshot exceeds 16 MiB");
        return result;
    }

    private static <R, T> JsonObject encodeMap(RegistryAccess registries, DataMapType<R, T> type,
            DynamicOps<com.google.gson.JsonElement> ops) {
        var registry = registries.registryOrThrow(type.registryKey());
        JsonObject result = new JsonObject();
        result.addProperty("registry", type.registryKey().location().toString());
        result.addProperty("id", type.id().toString());
        result.addProperty("mandatory", type.mandatorySync());
        JsonObject values = new JsonObject();
        DataMaps.getServerDataMap(registry, type).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(key -> key.location().toString())))
                .forEach(entry -> values.add(entry.getKey().location().toString(),
                        type.networkCodec().encodeStart(ops, entry.getValue()).getOrThrow()));
        result.add("values", values);
        return result;
    }

    static Map<HolderOwner<?>, Map<DataMapType<?, ?>, Map<?, ?>>> decode(
            byte[] bytes, RegistryAccess registries, List<DataMapType<?, ?>> types) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid data map snapshot size");
        var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (json.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unknown data map snapshot version");
        Map<DataMaps.Key, DataMapType<?, ?>> known = new LinkedHashMap<>();
        Map<HolderOwner<?>, Map<DataMapType<?, ?>, Map<?, ?>>> result = new IdentityHashMap<>();
        for (var type : types) if (type.networkCodec() != null) {
            known.put(new DataMaps.Key(type.registryKey().location(), type.id()), type);
            result.computeIfAbsent(registries.registryOrThrow(type.registryKey()).holderOwner(), ignored -> new IdentityHashMap<>())
                    .put(type, Map.of());
        }
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var seen = new HashSet<DataMaps.Key>();
        for (var raw : json.getAsJsonArray("maps")) {
            var map = raw.getAsJsonObject();
            var key = new DataMaps.Key(net.minecraft.resources.ResourceLocation.parse(map.get("registry").getAsString()),
                    net.minecraft.resources.ResourceLocation.parse(map.get("id").getAsString()));
            if (!seen.add(key)) throw new IllegalArgumentException("Repeated data map in snapshot: " + key);
            var type = known.get(key);
            if (type == null) {
                if (map.get("mandatory").getAsBoolean()) throw new IllegalArgumentException("Client does not know mandatory data map: " + key);
                continue;
            }
            decodeMap(registries, type, map.getAsJsonObject("values"), ops, result);
        }
        return result;
    }

    private static <R, T> void decodeMap(RegistryAccess registries, DataMapType<R, T> type, JsonObject input,
            DynamicOps<com.google.gson.JsonElement> ops, Map<HolderOwner<?>, Map<DataMapType<?, ?>, Map<?, ?>>> output) {
        Registry<R> registry = registries.registryOrThrow(type.registryKey());
        Map<ResourceKey<R>, T> values = new LinkedHashMap<>();
        input.entrySet().forEach(entry -> {
            ResourceKey<R> key = ResourceKey.create(type.registryKey(), net.minecraft.resources.ResourceLocation.parse(entry.getKey()));
            if (registry.getHolder(key).isEmpty()) throw new IllegalArgumentException("Unknown client registry object: " + key);
            values.put(key, type.networkCodec().parse(ops, entry.getValue()).getOrThrow());
        });
        output.get(registry.holderOwner()).put(type, Map.copyOf(values));
    }
}
