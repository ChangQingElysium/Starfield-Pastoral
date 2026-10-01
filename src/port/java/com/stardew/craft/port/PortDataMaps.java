package com.stardew.craft.port;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.port.net.neoforged.neoforge.registries.datamaps.AdvancedDataMapType;
import com.stardew.craft.port.net.neoforged.neoforge.registries.datamaps.DataMapType;
import com.stardew.craft.port.net.neoforged.neoforge.registries.datamaps.DataMapValueMerger;
import com.stardew.craft.port.net.neoforged.neoforge.registries.datamaps.DataMapValueRemover;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * PORT(1.20.1): NeoForge data maps on Forge. Files keep NeoForge's location and format
 * ({@code data/<ns>/data_maps/<registry>/<path>.json} with {@code replace}/{@code values}/{@code remove},
 * tags, per-value {@code replace} and conditions). They are read by a server reload listener, resolved against
 * tags once the server's tags are bound ({@link TagsUpdatedEvent}), and synced types are sent to remote clients.
 * {@link #getData(Holder, DataMapType)} replaces NeoForge's {@code Holder#getData(DataMapType)}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class PortDataMaps {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String PATH = "data_maps";
    private static final Map<ResourceLocation, Map<ResourceLocation, DataMapType<?, ?>>> TYPES = new LinkedHashMap<>();
    private static volatile Map<DataMapType<?, ?>, Map<ResourceKey<?>, Object>> data = Map.of();
    @Nullable
    private static volatile Map<DataMapType<?, ?>, List<DataFile>> pending;

    private PortDataMaps() {}

    public static synchronized void register(DataMapType<?, ?> type) {
        Map<ResourceLocation, DataMapType<?, ?>> forRegistry = TYPES.computeIfAbsent(type.registryKey().location(), ignored -> new LinkedHashMap<>());
        if (forRegistry.putIfAbsent(type.id(), type) != null) {
            throw new IllegalArgumentException("Tried to register data map type with ID " + type.id() + " to registry "
                    + type.registryKey().location() + " twice");
        }
    }

    private static synchronized List<DataMapType<?, ?>> types() {
        List<DataMapType<?, ?>> list = new ArrayList<>();
        TYPES.values().forEach(map -> list.addAll(map.values()));
        return list;
    }

    @Nullable
    private static synchronized DataMapType<?, ?> type(ResourceLocation registry, ResourceLocation id) {
        Map<ResourceLocation, DataMapType<?, ?>> map = TYPES.get(registry);
        return map == null ? null : map.get(id);
    }

    @Nullable
    public static <R, T> T getData(Holder<R> holder, DataMapType<R, T> type) {
        Optional<ResourceKey<R>> key = holder.unwrapKey();
        if (key.isEmpty()) return null;
        Map<ResourceKey<?>, Object> values = data.get(type);
        return values == null ? null : (T) values.get(key.get());
    }

    public static <R, T> Map<ResourceKey<R>, T> getDataMap(DataMapType<R, T> type) {
        Map<ResourceKey<?>, Object> values = data.get(type);
        return values == null ? Map.of() : (Map) Collections.unmodifiableMap(values);
    }

    // ---- loading -------------------------------------------------------------------------------------------

    private record DataEntry(Either<TagKey<?>, ResourceKey<?>> key, Object value, boolean replace) {}

    private record Removal(Either<TagKey<?>, ResourceKey<?>> key, Optional<DataMapValueRemover<?, ?>> remover) {}

    private record DataFile(boolean replace, List<DataEntry> values, List<Removal> removals) {}

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        RegistryAccess access = event.getRegistryAccess();
        ICondition.IContext context = event.getConditionContext();
        event.addListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager, ProfilerFiller preparationsProfiler,
                    ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
                return CompletableFuture.supplyAsync(() -> load(manager, access, context), backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenAcceptAsync(result -> pending = result, gameExecutor);
            }

            @Override
            public String getName() {
                return "PortDataMaps";
            }
        });
    }

    @SubscribeEvent
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        Map<DataMapType<?, ?>, List<DataFile>> files = pending;
        if (files == null) return;
        pending = null;
        Map<DataMapType<?, ?>, Map<ResourceKey<?>, Object>> built = new IdentityHashMap<>();
        files.forEach((type, list) -> {
            Registry registry = event.getRegistryAccess().registryOrThrow((ResourceKey) type.registryKey());
            built.put(type, build(registry, (DataMapType) type, list));
        });
        data = built;
    }

    private static String folder(ResourceLocation registryId) {
        return (registryId.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? "" : registryId.getNamespace() + "/") + registryId.getPath();
    }

    private static Map<DataMapType<?, ?>, List<DataFile>> load(ResourceManager manager, RegistryAccess access, ICondition.IContext context) {
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        Map<DataMapType<?, ?>, List<DataFile>> result = new IdentityHashMap<>();
        for (DataMapType<?, ?> type : types()) {
            ResourceLocation file = new ResourceLocation(type.id().getNamespace(),
                    PATH + "/" + folder(type.registryKey().location()) + "/" + type.id().getPath() + ".json");
            List<DataFile> files = new ArrayList<>();
            for (Resource resource : manager.getResourceStack(file)) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (!conditionsMet(json, context)) continue;
                    files.add(readFile(ops, type, json, context));
                } catch (Exception exception) {
                    LOGGER.error("Could not read data map of type {} for registry {}", type.id(), type.registryKey().location(), exception);
                }
            }
            if (!files.isEmpty()) result.put(type, files);
        }
        return result;
    }

    private static boolean conditionsMet(JsonObject json, ICondition.IContext context) {
        for (String member : new String[] {"neoforge:conditions", "forge:conditions"}) {
            if (!json.has(member)) continue;
            JsonArray conditions = json.getAsJsonArray(member).deepCopy();
            renameConditionTypes(conditions);
            if (!CraftingHelper.processConditions(conditions, context)) return false;
        }
        return true;
    }

    /** NeoForge condition ids ({@code neoforge:mod_loaded} ...) map to Forge's {@code forge:} ids. */
    private static void renameConditionTypes(JsonElement element) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(PortDataMaps::renameConditionTypes);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") && object.get("type").getAsString().startsWith("neoforge:")) {
                object.addProperty("type", "forge:" + object.get("type").getAsString().substring("neoforge:".length()));
            }
            object.entrySet().forEach(entry -> renameConditionTypes(entry.getValue()));
        }
    }

    private static Either<TagKey<?>, ResourceKey<?>> parseKey(DataMapType<?, ?> type, String raw) {
        if (raw.startsWith("#")) return Either.left(TagKey.create((ResourceKey) type.registryKey(), new ResourceLocation(raw.substring(1))));
        return Either.right(ResourceKey.create((ResourceKey) type.registryKey(), new ResourceLocation(raw)));
    }

    private static <T> T decode(Codec<T> codec, DynamicOps<JsonElement> ops, JsonElement json) {
        return codec.parse(ops, json).getOrThrow(false, error -> {});
    }

    private static DataFile readFile(DynamicOps<JsonElement> ops, DataMapType<?, ?> type, JsonObject json, ICondition.IContext context) {
        boolean replace = json.has("replace") && json.get("replace").getAsBoolean();
        List<DataEntry> values = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("values").entrySet()) {
            JsonElement element = entry.getValue();
            if (element.isJsonObject() && (element.getAsJsonObject().has("neoforge:conditions") || element.getAsJsonObject().has("forge:conditions"))) {
                if (!conditionsMet(element.getAsJsonObject(), context)) continue;
                JsonObject copy = element.getAsJsonObject().deepCopy();
                copy.remove("neoforge:conditions");
                copy.remove("forge:conditions");
                element = copy;
            }
            values.add(readEntry(ops, type, parseKey(type, entry.getKey()), element));
        }
        List<Removal> removals = new ArrayList<>();
        JsonElement remove = json.get("remove");
        if (remove != null) {
            if (remove.isJsonArray()) {
                for (JsonElement element : remove.getAsJsonArray()) {
                    if (element.isJsonPrimitive()) {
                        removals.add(new Removal(parseKey(type, element.getAsString()), Optional.empty()));
                    } else {
                        JsonObject object = element.getAsJsonObject();
                        Optional<DataMapValueRemover<?, ?>> remover = Optional.empty();
                        if (object.has("remover") && type instanceof AdvancedDataMapType<?, ?, ?> advanced) {
                            remover = Optional.of(decode((Codec<DataMapValueRemover<?, ?>>) (Codec) advanced.remover(), ops, object.get("remover")));
                        }
                        removals.add(new Removal(parseKey(type, object.get("key").getAsString()), remover));
                    }
                }
            } else if (type instanceof AdvancedDataMapType<?, ?, ?> advanced) {
                for (Map.Entry<String, JsonElement> entry : remove.getAsJsonObject().entrySet()) {
                    removals.add(new Removal(parseKey(type, entry.getKey()),
                            Optional.of(decode((Codec<DataMapValueRemover<?, ?>>) (Codec) advanced.remover(), ops, entry.getValue()))));
                }
            } else {
                throw new IllegalArgumentException("'remove' must be a list for data map " + type.id());
            }
        }
        return new DataFile(replace, values, removals);
    }

    /** Mirrors NeoForge's {@code DataMapEntry} codec: {@code {"value": v, "replace": b}} or the bare value. */
    private static DataEntry readEntry(DynamicOps<JsonElement> ops, DataMapType<?, ?> type, Either<TagKey<?>, ResourceKey<?>> key, JsonElement element) {
        Codec<Object> codec = (Codec<Object>) type.codec();
        if (element.isJsonObject() && element.getAsJsonObject().has("value")) {
            JsonObject object = element.getAsJsonObject();
            var wrapped = codec.parse(ops, object.get("value")).result();
            if (wrapped.isPresent()) {
                return new DataEntry(key, wrapped.get(), object.has("replace") && object.get("replace").getAsBoolean());
            }
        }
        return new DataEntry(key, decode(codec, ops, element), false);
    }

    private record WithSource(Object value, Either<TagKey<?>, ResourceKey<?>> source) {}

    private static <R, T> Map<ResourceKey<?>, Object> build(Registry<R> registry, DataMapType<R, T> type, List<DataFile> files) {
        Map<ResourceKey<?>, WithSource> result = new IdentityHashMap<>();
        DataMapValueMerger<R, T> merger = type instanceof AdvancedDataMapType<R, T, ?> advanced ? advanced.merger() : DataMapValueMerger.defaultMerger();
        for (DataFile file : files) {
            if (file.replace()) result.clear();
            for (DataEntry entry : file.values()) {
                resolve(registry, entry.key(), true, holder -> {
                    ResourceKey<R> key = holder.unwrapKey().orElseThrow();
                    WithSource old = result.get(key);
                    if (old == null || entry.replace()) {
                        result.put(key, new WithSource(entry.value(), entry.key()));
                    } else {
                        result.put(key, new WithSource(merger.merge(registry, (Either) old.source(), (T) old.value(),
                                (Either) entry.key(), (T) entry.value()), entry.key()));
                    }
                });
            }
            for (Removal removal : file.removals()) {
                resolve(registry, removal.key(), false, holder -> {
                    ResourceKey<R> key = holder.unwrapKey().orElseThrow();
                    if (removal.remover().isEmpty()) {
                        result.remove(key);
                        return;
                    }
                    WithSource old = result.get(key);
                    if (old == null) return;
                    Optional<T> newValue = ((DataMapValueRemover<R, T>) removal.remover().get())
                            .remove((T) old.value(), registry, (Either) old.source(), holder.value());
                    if (newValue.isEmpty()) result.remove(key);
                    else result.put(key, new WithSource(newValue.get(), old.source()));
                });
            }
        }
        Map<ResourceKey<?>, Object> map = new IdentityHashMap<>();
        result.forEach((key, value) -> map.put(key, value.value()));
        return map;
    }

    private static <R> void resolve(Registry<R> registry, Either<TagKey<?>, ResourceKey<?>> key, boolean required, Consumer<Holder<R>> consumer) {
        if (key.left().isPresent()) {
            registry.getTagOrEmpty((TagKey<R>) key.left().get()).forEach(consumer);
        } else {
            ResourceKey<R> id = (ResourceKey<R>) key.right().orElseThrow();
            Optional<Holder.Reference<R>> holder = registry.getHolder(id);
            if (holder.isPresent()) consumer.accept(holder.get());
            else if (required) LOGGER.error("Object with ID {} specified in data map for registry {} doesn't exist", id.location(), registry.key().location());
        }
    }

    // ---- client sync ---------------------------------------------------------------------------------------

    /** Synced data maps as {@code (registry, type id) -> [(entry id, network-codec JSON)]}. */
    public record SyncMessage(Map<ResourceLocation, Map<ResourceLocation, Map<ResourceLocation, String>>> maps) {
        void encode(FriendlyByteBuf buf) {
            buf.writeMap(maps, FriendlyByteBuf::writeResourceLocation,
                    (b1, types) -> b1.writeMap(types, FriendlyByteBuf::writeResourceLocation,
                            (b2, entries) -> b2.writeMap(entries, FriendlyByteBuf::writeResourceLocation,
                                    (b3, json) -> b3.writeUtf(json, Integer.MAX_VALUE / 4))));
        }

        static SyncMessage decode(FriendlyByteBuf buf) {
            return new SyncMessage(buf.readMap(FriendlyByteBuf::readResourceLocation,
                    b1 -> b1.readMap(FriendlyByteBuf::readResourceLocation,
                            b2 -> b2.readMap(FriendlyByteBuf::readResourceLocation, b3 -> b3.readUtf(Integer.MAX_VALUE / 4)))));
        }
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        List<ServerPlayer> players = event.getPlayer() != null ? List.of(event.getPlayer()) : event.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, event.getPlayerList().getServer().registryAccess());
        Map<ResourceLocation, Map<ResourceLocation, Map<ResourceLocation, String>>> maps = new HashMap<>();
        for (DataMapType<?, ?> type : types()) {
            Codec<Object> codec = (Codec<Object>) type.networkCodec();
            if (codec == null) continue;
            Map<ResourceLocation, String> entries = new HashMap<>();
            getDataMap((DataMapType<Object, Object>) type).forEach((key, value) -> codec.encodeStart(ops, value)
                    .resultOrPartial(error -> LOGGER.error("Failed to encode data map {} value for {}: {}", type.id(), key.location(), error))
                    .ifPresent(json -> entries.put(key.location(), json.toString())));
            maps.computeIfAbsent(type.registryKey().location(), ignored -> new HashMap<>()).put(type.id(), entries);
        }
        SyncMessage message = new SyncMessage(maps);
        for (ServerPlayer player : players) {
            if (player.connection.connection.isMemoryConnection()) continue;
            PortNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    static void applySync(SyncMessage message, RegistryAccess access) {
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        Map<DataMapType<?, ?>, Map<ResourceKey<?>, Object>> updated = new IdentityHashMap<>(data);
        message.maps().forEach((registry, types) -> types.forEach((id, entries) -> {
            DataMapType<?, ?> type = type(registry, id);
            if (type == null || type.networkCodec() == null) return;
            Map<ResourceKey<?>, Object> values = new IdentityHashMap<>();
            entries.forEach((entry, json) -> ((Codec<Object>) type.networkCodec()).parse(ops, JsonParser.parseString(json))
                    .resultOrPartial(error -> LOGGER.error("Failed to decode synced data map {} value for {}: {}", id, entry, error))
                    .ifPresent(value -> values.put(ResourceKey.create((ResourceKey) type.registryKey(), entry), value)));
            updated.put(type, values);
        }));
        data = updated;
    }
}
