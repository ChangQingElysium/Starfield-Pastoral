package com.stardew.craft.world.interaction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.mapinteraction.StardewMapInteractionAction;
import com.stardew.craft.api.v1.mapinteraction.StardewMapInteractionActions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reloadable data-pack registry for data/&lt;namespace&gt;/map_interactions/*.json. */
public final class MapInteractionRegistry {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();
    private static volatile Catalog catalog = Catalog.empty();
    private static volatile ReloadReport lastReport =
            new ReloadReport(0, 0, false, List.of());

    private MapInteractionRegistry() {
    }

    public static List<MapInteractionDefinition> all() {
        return catalog.ordered();
    }

    public static List<MapInteractionDefinition> at(
            ResourceLocation dimension,
            BlockPos pos
    ) {
        if (dimension == null || pos == null) {
            return List.of();
        }
        long chunk = new ChunkPos(pos).toLong();
        List<MapInteractionDefinition> exact =
                catalog.chunkIndex().getOrDefault(
                        new ChunkKey(dimension, chunk), List.of());
        List<MapInteractionDefinition> wildcard =
                catalog.chunkIndex().getOrDefault(
                        new ChunkKey(null, chunk), List.of());
        if (wildcard.isEmpty()) {
            return exact;
        }
        if (exact.isEmpty()) {
            return wildcard;
        }
        LinkedHashSet<MapInteractionDefinition> combined =
                new LinkedHashSet<>(exact);
        combined.addAll(wildcard);
        return combined.stream()
                .sorted(definitionOrder())
                .toList();
    }

    public static ReloadReport lastReport() {
        return lastReport;
    }

    static synchronized void publish(
            Map<ResourceLocation, MapInteractionDefinition> definitions
    ) {
        List<MapInteractionDefinition> ordered = definitions.values()
                .stream()
                .sorted(definitionOrder())
                .toList();
        LinkedHashMap<ChunkKey,
                LinkedHashSet<MapInteractionDefinition>> mutableIndex =
                new LinkedHashMap<>();
        for (MapInteractionDefinition definition : ordered) {
            for (MapInteractionDefinition.Box box :
                    definition.boxes()) {
                int minChunkX = box.min().getX() >> 4;
                int maxChunkX = box.max().getX() >> 4;
                int minChunkZ = box.min().getZ() >> 4;
                int maxChunkZ = box.max().getZ() >> 4;
                for (int chunkX = minChunkX;
                     chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ;
                         chunkZ <= maxChunkZ; chunkZ++) {
                        ChunkKey key = new ChunkKey(
                                definition.dimension(),
                                ChunkPos.asLong(chunkX, chunkZ));
                        mutableIndex.computeIfAbsent(
                                        key,
                                        ignored ->
                                                new LinkedHashSet<>())
                                .add(definition);
                    }
                }
            }
        }
        LinkedHashMap<ChunkKey, List<MapInteractionDefinition>>
                chunkIndex = new LinkedHashMap<>();
        mutableIndex.forEach((key, values) ->
                chunkIndex.put(key, List.copyOf(values)));
        catalog = new Catalog(
                catalog.revision() + 1,
                Map.copyOf(definitions),
                ordered,
                Map.copyOf(chunkIndex));
    }

    public static final class ReloadListener
            extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "map_interactions");
        }

        @Override
        protected void apply(
                Map<ResourceLocation, JsonElement> objects,
                ResourceManager manager,
                ProfilerFiller profiler
        ) {
            ReloadReport report = applyObjects(objects);
            report.errors().forEach(error -> StardewCraft.LOGGER.error(
                    "[Map interactions] {}", error));
            if (report.partial()) {
                StardewCraft.LOGGER.warn(
                        "[Map interactions] Applied snapshot v{} with {} retained/ignored invalid definition(s) ({} active)",
                        report.revision(), report.errors().size(),
                        report.definitionCount());
            } else {
                StardewCraft.LOGGER.info(
                        "[Map interactions] Applied snapshot v{} ({} definitions)",
                        report.revision(), report.definitionCount());
            }
        }
    }

    static synchronized ReloadReport applyObjects(
            Map<ResourceLocation, JsonElement> objects
    ) {
        Map<ResourceLocation, MapInteractionDefinition> previous =
                catalog.byId();
        LinkedHashMap<ResourceLocation, MapInteractionDefinition>
                candidate = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        objects.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(
                                ResourceLocation::toString)))
                .forEach(entry -> {
                    try {
                        candidate.put(
                                entry.getKey(),
                                decode(entry.getKey(), entry.getValue()));
                    } catch (RuntimeException exception) {
                        errors.add(entry.getKey() + ": "
                                + exception.getMessage());
                        MapInteractionDefinition retained =
                                previous.get(entry.getKey());
                        if (retained != null) {
                            candidate.put(entry.getKey(), retained);
                        }
                    }
                });
        publish(candidate);
        lastReport = new ReloadReport(
                catalog.revision(),
                candidate.size(),
                !errors.isEmpty(),
                List.copyOf(errors));
        return lastReport;
    }

    static MapInteractionDefinition decode(
            ResourceLocation id,
            JsonElement raw
    ) {
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    "definition must be a JSON object");
        }
        JsonObject root = raw.getAsJsonObject();
        rejectUnknown(root, Set.of(
                "format", "priority", "trigger", "branches", "hint",
                "source"));
        int format = readInt(root, "format", 1);
        if (format != 1) {
            throw new IllegalArgumentException(
                    "unsupported format " + format);
        }
        int priority = readInt(root, "priority", 0);
        if (priority < -100000 || priority > 100000) {
            throw new IllegalArgumentException(
                    "priority is outside [-100000, 100000]");
        }

        JsonObject trigger = requiredObject(root, "trigger");
        rejectUnknown(trigger, Set.of(
                "dimension", "location", "positions", "boxes",
                "blocks", "block_tags", "hand"));
        String hand = readString(trigger, "hand", "main_hand");
        if (!"main_hand".equals(hand)) {
            throw new IllegalArgumentException(
                    "v1 only supports trigger.hand main_hand");
        }
        ResourceLocation dimension = optionalId(
                trigger, "dimension");
        ResourceLocation location = optionalId(
                trigger, "location");
        List<MapInteractionDefinition.Box> boxes =
                readBoxes(trigger);
        if (boxes.isEmpty()) {
            throw new IllegalArgumentException(
                    "trigger needs at least one position or box");
        }
        Set<ResourceLocation> blocks =
                readIds(trigger.get("blocks"), "trigger.blocks");
        Set<ResourceLocation> blockTags =
                readIds(trigger.get("block_tags"),
                        "trigger.block_tags");

        JsonArray rawBranches = requiredArray(root, "branches");
        if (rawBranches.isEmpty() || rawBranches.size() > 64) {
            throw new IllegalArgumentException(
                    "branches must contain 1..64 entries");
        }
        List<MapInteractionDefinition.Branch> branches =
                new ArrayList<>();
        Set<String> branchIds = new LinkedHashSet<>();
        for (int i = 0; i < rawBranches.size(); i++) {
            MapInteractionDefinition.Branch branch =
                    decodeBranch(rawBranches.get(i), i);
            if (!branchIds.add(branch.id())) {
                throw new IllegalArgumentException(
                        "duplicate branch id " + branch.id());
            }
            branches.add(branch);
        }

        MapInteractionDefinition.Source source = root.has("source")
                ? decodeSource(requiredObject(root, "source"))
                : MapInteractionDefinition.Source.empty();
        MapInteractionDefinition.Hint hint = readHint(root);
        return new MapInteractionDefinition(
                id, priority, dimension, location, boxes,
                blocks, blockTags, branches, hint, source);
    }

    private static MapInteractionDefinition.Hint readHint(
            JsonObject root
    ) {
        String raw = readString(root, "hint", "auto");
        return switch (raw) {
            case "auto" -> MapInteractionDefinition.Hint.AUTO;
            case "read" -> MapInteractionDefinition.Hint.READ;
            case "none" -> MapInteractionDefinition.Hint.NONE;
            default -> throw new IllegalArgumentException(
                    "hint must be auto, read or none");
        };
    }

    private static MapInteractionDefinition.Branch decodeBranch(
            JsonElement raw,
            int index
    ) {
        if (!raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    "branches[" + index + "] must be an object");
        }
        JsonObject object = raw.getAsJsonObject();
        rejectUnknown(object, Set.of(
                "id", "conditions", "effects", "messages", "action"));
        String id = readString(object, "id", "branch_" + index);

        List<StardewCondition> conditions = new ArrayList<>();
        if (object.has("conditions")) {
            JsonArray values = requiredArray(object, "conditions");
            if (values.size() > 16) {
                throw new IllegalArgumentException(
                        "branch " + id + " has more than 16 conditions");
            }
            for (JsonElement value : values) {
                StardewCondition condition = StardewConditions.CODEC
                        .parse(JsonOps.INSTANCE, value)
                        .getOrThrow(message ->
                                new IllegalArgumentException(
                                        "branch " + id
                                                + " condition: "
                                                + message));
                conditions.add(condition);
            }
        }

        List<StardewAction> effects = new ArrayList<>();
        if (object.has("effects")) {
            JsonArray values = requiredArray(object, "effects");
            if (values.size() > 16) {
                throw new IllegalArgumentException(
                        "branch " + id + " has more than 16 effects");
            }
            for (JsonElement value : values) {
                StardewAction effect = StardewActions.CODEC
                        .parse(JsonOps.INSTANCE, value)
                        .getOrThrow(message ->
                                new IllegalArgumentException(
                                        "branch " + id
                                                + " effect: "
                                                + message));
                effects.add(effect);
            }
        }

        List<MapInteractionDefinition.Message> messages =
                new ArrayList<>();
        if (object.has("messages")) {
            JsonArray values = requiredArray(object, "messages");
            if (values.size() > 32) {
                throw new IllegalArgumentException(
                        "branch " + id + " has more than 32 messages");
            }
            for (JsonElement value : values) {
                messages.add(decodeMessage(value, id));
            }
        }

        StardewMapInteractionAction action = object.has("action")
                ? decodeAction(requiredObject(object, "action"), id)
                : null;
        if (action == null && effects.isEmpty() && messages.isEmpty()) {
            throw new IllegalArgumentException(
                    "branch " + id
                            + " has neither effects, messages nor action");
        }
        return new MapInteractionDefinition.Branch(
                id, conditions, effects, messages, action);
    }

    private static MapInteractionDefinition.Message decodeMessage(
            JsonElement raw,
            String branchId
    ) {
        if (!raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    "branch " + branchId
                            + " message must be an object");
        }
        JsonObject object = raw.getAsJsonObject();
        rejectUnknown(object, Set.of(
                "translate", "fallback", "literal"));
        String literal = optionalString(object, "literal");
        String translate = optionalString(object, "translate");
        String fallback = optionalString(object, "fallback");
        if ((literal == null) == (translate == null)) {
            throw new IllegalArgumentException(
                    "branch " + branchId
                            + " message needs exactly one of literal or translate");
        }
        if (literal != null && fallback != null) {
            throw new IllegalArgumentException(
                    "literal message cannot declare fallback");
        }
        return new MapInteractionDefinition.Message(
                translate, fallback, literal);
    }

    private static StardewMapInteractionAction decodeAction(
            JsonObject object,
            String branchId
    ) {
        rejectUnknown(object, Set.of("type", "data"));
        ResourceLocation type = requiredId(object, "type");
        JsonElement data = object.has("data")
                ? object.get("data") : new JsonObject();
        return StardewMapInteractionActions.decode(type, data)
                .getOrThrow(message -> new IllegalArgumentException(
                        "branch " + branchId + " action: " + message));
    }

    private static MapInteractionDefinition.Source decodeSource(
            JsonObject object
    ) {
        rejectUnknown(object, Set.of(
                "vanilla_version", "map", "tile_action", "code"));
        String vanillaVersion =
                readString(object, "vanilla_version", "");
        String map = readString(object, "map", "");
        String tileAction = readString(object, "tile_action", "");
        String code = readString(object, "code", "");
        return new MapInteractionDefinition.Source(
                vanillaVersion, map, tileAction, code);
    }

    private static List<MapInteractionDefinition.Box> readBoxes(
            JsonObject trigger
    ) {
        List<MapInteractionDefinition.Box> result =
                new ArrayList<>();
        long totalVolume = 0L;
        if (trigger.has("positions")) {
            JsonArray positions = requiredArray(trigger, "positions");
            if (positions.size() > 64) {
                throw new IllegalArgumentException(
                        "trigger has more than 64 positions");
            }
            for (JsonElement raw : positions) {
                BlockPos pos = readPosition(raw, "position");
                result.add(new MapInteractionDefinition.Box(pos, pos));
                totalVolume++;
            }
        }
        if (trigger.has("boxes")) {
            JsonArray boxes = requiredArray(trigger, "boxes");
            if (boxes.size() > 64) {
                throw new IllegalArgumentException(
                        "trigger has more than 64 boxes");
            }
            for (JsonElement raw : boxes) {
                if (!raw.isJsonObject()) {
                    throw new IllegalArgumentException(
                            "trigger.boxes entries must be objects");
                }
                JsonObject box = raw.getAsJsonObject();
                rejectUnknown(box, Set.of("min", "max"));
                BlockPos a = readPosition(box.get("min"), "box.min");
                BlockPos b = readPosition(box.get("max"), "box.max");
                BlockPos min = new BlockPos(
                        Math.min(a.getX(), b.getX()),
                        Math.min(a.getY(), b.getY()),
                        Math.min(a.getZ(), b.getZ()));
                BlockPos max = new BlockPos(
                        Math.max(a.getX(), b.getX()),
                        Math.max(a.getY(), b.getY()),
                        Math.max(a.getZ(), b.getZ()));
                long sizeX = axisSize(
                        min.getX(), max.getX(), "box x");
                long sizeY = axisSize(
                        min.getY(), max.getY(), "box y");
                long sizeZ = axisSize(
                        min.getZ(), max.getZ(), "box z");
                long volume = Math.multiplyExact(
                        Math.multiplyExact(sizeX, sizeY), sizeZ);
                if (volume > 4096L) {
                    throw new IllegalArgumentException(
                            "interaction box volume exceeds 4096");
                }
                totalVolume += volume;
                result.add(new MapInteractionDefinition.Box(min, max));
            }
        }
        if (result.size() > 64) {
            throw new IllegalArgumentException(
                    "trigger has more than 64 positions/boxes");
        }
        if (totalVolume > 32768L) {
            throw new IllegalArgumentException(
                    "trigger covers more than 32768 block positions");
        }
        return result;
    }

    private static Comparator<MapInteractionDefinition>
    definitionOrder() {
        return Comparator
                .comparingInt(MapInteractionDefinition::priority)
                .reversed()
                .thenComparing(value -> value.id().toString());
    }

    private static BlockPos readPosition(
            JsonElement raw,
            String field
    ) {
        if (raw == null || !raw.isJsonArray()
                || raw.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException(
                    field + " must be a three-integer array");
        }
        JsonArray values = raw.getAsJsonArray();
        int x = readExactInt(values.get(0), field + "[0]");
        int y = readExactInt(values.get(1), field + "[1]");
        int z = readExactInt(values.get(2), field + "[2]");
        if (Math.abs((long) x) > 30_000_000L
                || Math.abs((long) z) > 30_000_000L) {
            throw new IllegalArgumentException(
                    field + " is outside supported world bounds");
        }
        return new BlockPos(x, y, z);
    }

    private static long axisSize(
            int min,
            int max,
            String field
    ) {
        long size = (long) max - (long) min + 1L;
        if (size <= 0L || size > 4096L) {
            throw new IllegalArgumentException(
                    field + " span exceeds 4096");
        }
        return size;
    }

    private static Set<ResourceLocation> readIds(
            JsonElement raw,
            String field
    ) {
        if (raw == null) {
            return Set.of();
        }
        if (!raw.isJsonArray()) {
            throw new IllegalArgumentException(
                    field + " must be an array");
        }
        LinkedHashSet<ResourceLocation> result =
                new LinkedHashSet<>();
        for (JsonElement value : raw.getAsJsonArray()) {
            if (!value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(
                        field + " entries must be string IDs");
            }
            ResourceLocation id =
                    ResourceLocation.tryParse(value.getAsString());
            if (id == null) {
                throw new IllegalArgumentException(
                        field + " contains invalid ID " + value);
            }
            result.add(id);
        }
        return result;
    }

    private static JsonObject requiredObject(
            JsonObject root,
            String field
    ) {
        JsonElement raw = root.get(field);
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    field + " must be an object");
        }
        return raw.getAsJsonObject();
    }

    private static JsonArray requiredArray(
            JsonObject root,
            String field
    ) {
        JsonElement raw = root.get(field);
        if (raw == null || !raw.isJsonArray()) {
            throw new IllegalArgumentException(
                    field + " must be an array");
        }
        return raw.getAsJsonArray();
    }

    private static int readInt(
            JsonObject root,
            String field,
            int fallback
    ) {
        return root.has(field)
                ? readExactInt(root.get(field), field) : fallback;
    }

    private static int readExactInt(
            JsonElement raw,
            String field
    ) {
        if (raw == null
                || !raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    field + " must be an integer");
        }
        try {
            return new BigDecimal(raw.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " must be an integer");
        }
    }

    private static String readString(
            JsonObject root,
            String field,
            String fallback
    ) {
        String value = optionalString(root, field);
        return value == null ? fallback : value;
    }

    private static String optionalString(
            JsonObject root,
            String field
    ) {
        if (!root.has(field)) {
            return null;
        }
        JsonElement raw = root.get(field);
        if (!raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    field + " must be a string");
        }
        return raw.getAsString();
    }

    private static ResourceLocation requiredId(
            JsonObject root,
            String field
    ) {
        ResourceLocation id = optionalId(root, field);
        if (id == null) {
            throw new IllegalArgumentException(
                    field + " is required and must be a namespaced ID");
        }
        return id;
    }

    private static ResourceLocation optionalId(
            JsonObject root,
            String field
    ) {
        String value = optionalString(root, field);
        if (value == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || value.indexOf(':') < 1) {
            throw new IllegalArgumentException(
                    field + " must be a namespaced ID");
        }
        return id;
    }

    private static void rejectUnknown(
            JsonObject object,
            Set<String> allowed
    ) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(
                        "unknown field " + key);
            }
        }
    }

    private record Catalog(
            long revision,
            Map<ResourceLocation, MapInteractionDefinition> byId,
            List<MapInteractionDefinition> ordered,
            Map<ChunkKey, List<MapInteractionDefinition>> chunkIndex
    ) {
        private static Catalog empty() {
            return new Catalog(0L, Map.of(), List.of(), Map.of());
        }
    }

    private record ChunkKey(
            ResourceLocation dimension,
            long chunk
    ) {
    }

    public record ReloadReport(
            long revision,
            int definitionCount,
            boolean partial,
            List<String> errors
    ) {
    }
}
