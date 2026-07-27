package com.stardew.craft.festival;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.festival.StardewFestivalMapOverlay;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.world.StardewMapSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class FestivalMapOverlayRegistry {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, FestivalMapOverlayDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, StardewFestivalMapOverlay>
            ADDON_DEFINITIONS = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, StardewFestivalMapOverlay>
            DATA_DEFINITIONS = Map.of();
    private static final OrderedExtensionRegistry<StardewFestivalMapOverlay>
            ADDON_OVERLAYS = new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "festival/map_overlay"));

    static {
        register(new FestivalMapOverlayDefinition(
            "Town-EggFestival",
            "Town",
            new BlockPos(-38, 63, -23),
            "",
            "data/stardewcraft/structures/festivals/egg_festival_town.schem",
            new BlockPos(-38, 63, -23),
            new BlockPos(67, 68, 52),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Town-Fair",
            "Town",
            new BlockPos(-41, 49, -47),
            "",
            "data/stardewcraft/structures/festivals/stardew_valley_fair_town.schem",
            new BlockPos(-41, 49, -47),
            new BlockPos(80, 77, 57),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Town-Halloween",
            "Town",
            new BlockPos(-36, 63, -77),
            "",
            "data/stardewcraft/structures/festivals/spirit_eve_town.schem",
            new BlockPos(-36, 63, -77),
            new BlockPos(74, 71, 22),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Town-Christmas",
            "Town",
            new BlockPos(-31, 64, -26),
            "",
            "data/stardewcraft/structures/festivals/winter_star_town.schem",
            new BlockPos(-31, 64, -26),
            new BlockPos(41, 78, 25),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Forest-FlowerFestival",
            "Forest",
            new BlockPos(-270, 59, 90),
            "",
            "data/stardewcraft/structures/festivals/huawujie.schem",
            new BlockPos(-270, 59, 90),
            new BlockPos(-172, 74, 138),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Beach-Luau",
            "Beach",
            new BlockPos(30, 59, 88),
            "",
            "data/stardewcraft/structures/festivals/luau_beach.schem",
            new BlockPos(30, 59, 88),
            new BlockPos(90, 63, 130),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Beach-Jellies",
            "Beach",
            new BlockPos(20, 59, 95),
            "",
            "data/stardewcraft/structures/festivals/moonlight_jellies_beach.schem",
            new BlockPos(20, 59, 95),
            new BlockPos(92, 63, 166),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "DesertFestival",
            "Desert",
            new BlockPos(-273, 62, -228),
            "",
            "data/stardewcraft/structures/festivals/desert_festival.schem",
            new BlockPos(-273, 62, -228),
            new BlockPos(-181, 77, -135),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Forest-TroutDerby",
            "Forest",
            new BlockPos(-149, 64, 82),
            "",
            "data/stardewcraft/structures/festivals/trout_derby_forest.schem",
            new BlockPos(-149, 64, 82),
            new BlockPos(-136, 67, 88),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Beach-SquidFest",
            "Beach",
            new BlockPos(26, 59, 96),
            "",
            "data/stardewcraft/structures/festivals/squid_fest_beach.schem",
            new BlockPos(26, 59, 96),
            new BlockPos(43, 64, 101),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "BeachNightMarket",
            "Beach",
            new BlockPos(13, 40, 127),
            "",
            "data/stardewcraft/structures/festivals/night_market_beach.schem",
            new BlockPos(13, 40, 127),
            new BlockPos(127, 78, 180),
            List.of(),
            true,
            true,
            true
        ));
        register(new FestivalMapOverlayDefinition(
            "Forest-IceFestival",
            "Forest",
            new BlockPos(-192, 63, -2),
            "",
            "data/stardewcraft/structures/festivals/festival_of_ice_forest.schem",
            new BlockPos(-192, 63, -2),
            new BlockPos(-32, 73, 83),
            List.of(),
            true,
            true,
            true,
            new FestivalMapOverlayDefinition.TreeClearance(8, 48, 0)
        ));
    }

    private FestivalMapOverlayRegistry() {
    }

    public static void register(FestivalMapOverlayDefinition definition) {
        if (definition == null) {
            return;
        }
        DEFINITIONS.put(normalize(definition.overlayId()), definition);
    }

    public static synchronized void registerAddon(
            StardewFestivalMapOverlay overlay
    ) {
        if (overlay == null) {
            throw new NullPointerException("overlay");
        }
        if (ADDON_DEFINITIONS.containsKey(overlay.id())
                || DATA_DEFINITIONS.containsKey(overlay.id())) {
            throw new IllegalStateException(
                    "Festival map overlay already registered: "
                            + overlay.id());
        }
        ADDON_DEFINITIONS.put(overlay.id(), overlay);
        ADDON_OVERLAYS.register(overlay.id(), 0, overlay);
    }

    public static synchronized Optional<StardewFestivalMapOverlay> findAddon(
            ResourceLocation id
    ) {
        if (id == null) {
            return Optional.empty();
        }
        StardewFestivalMapOverlay javaOverlay =
                ADDON_DEFINITIONS.get(id);
        return Optional.ofNullable(javaOverlay != null
                ? javaOverlay : DATA_DEFINITIONS.get(id));
    }

    public static List<StardewFestivalMapOverlay> addonOverlays() {
        List<StardewFestivalMapOverlay> javaOverlays =
                ADDON_OVERLAYS.entries().stream()
                .map(OrderedExtensionRegistry.Entry::extension)
                .toList();
        ArrayList<StardewFestivalMapOverlay> combined =
                new ArrayList<>(javaOverlays);
        DATA_DEFINITIONS.entrySet().stream()
                .filter(entry -> !ADDON_DEFINITIONS.containsKey(
                        entry.getKey()))
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .map(Map.Entry::getValue)
                .forEach(combined::add);
        return List.copyOf(combined);
    }

    public static Optional<FestivalMapOverlayDefinition> get(String overlayId) {
        if (overlayId == null || overlayId.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(
                normalize(overlayId));
        StardewFestivalMapOverlay addon = id == null
                ? null : findAddon(id).orElse(null);
        if (addon != null) {
            return resolveAddon(addon, null);
        }
        return Optional.ofNullable(
                DEFINITIONS.get(normalize(overlayId)));
    }

    public static Optional<FestivalMapOverlayDefinition> get(
            ServerLevel level,
            String overlayId
    ) {
        if (overlayId == null || overlayId.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(
                normalize(overlayId));
        StardewFestivalMapOverlay addon = id == null
                ? null : findAddon(id).orElse(null);
        if (addon != null) {
            return resolveAddon(
                    addon,
                    level == null
                            ? null : level.dimension().location());
        }
        return Optional.ofNullable(
                DEFINITIONS.get(normalize(overlayId)));
    }

    public static boolean isRegistered(String overlayId) {
        return get(overlayId).isPresent();
    }

    public static Collection<FestivalMapOverlayDefinition> all() {
        return java.util.List.copyOf(DEFINITIONS.values());
    }

    private static String normalize(String overlayId) {
        return overlayId.toLowerCase(Locale.ROOT);
    }

    private static Optional<FestivalMapOverlayDefinition> resolveAddon(
            StardewFestivalMapOverlay addon,
            ResourceLocation expectedDimension
    ) {
        return StardewMapSlots.worldAnchor(addon.originAnchor())
                .filter(slot -> expectedDimension == null
                        || expectedDimension.equals(slot.dimension()))
                .map(slot -> {
                    BlockPos origin = BlockPos.containing(
                            slot.position());
                    return new FestivalMapOverlayDefinition(
                            addon.id().toString(),
                            addon.locationId().toString(),
                            origin,
                            resourcePath(addon.baseSchematic()),
                            resourcePath(addon.festivalSchematic()),
                            origin.offset(addon.boundsMinOffset()),
                            origin.offset(addon.boundsMaxOffset()),
                            addon.safePositionOffsets().stream()
                                    .map(origin::offset)
                                    .toList(),
                            addon.requiresBlackFade(),
                            addon.cleanupDroppedItems(),
                            addon.cleanupTaggedEntities(),
                            new FestivalMapOverlayDefinition.TreeClearance(
                                    addon.treeClearance()
                                            .horizontalRadius(),
                                    addon.treeClearance().up(),
                                    addon.treeClearance().down()));
                });
    }

    private static String resourcePath(ResourceLocation id) {
        return id == null ? "" : "data/" + id.getNamespace()
                + "/structures/" + id.getPath();
    }

    static synchronized boolean publishData(
            Map<ResourceLocation, StardewFestivalMapOverlay> overlays
    ) {
        List<ResourceLocation> conflicts = overlays.keySet().stream()
                .filter(ADDON_DEFINITIONS::containsKey)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        if (!conflicts.isEmpty()) {
            StardewCraft.LOGGER.error(
                    "[FestivalMapOverlays] Data definitions conflict with "
                            + "Java registrations: {}", conflicts);
            return false;
        }
        DATA_DEFINITIONS = Map.copyOf(overlays);
        return true;
    }

    static Map<ResourceLocation, StardewFestivalMapOverlay> dataSnapshot() {
        return DATA_DEFINITIONS;
    }

    static StardewFestivalMapOverlay decodeData(
            ResourceLocation id,
            JsonElement raw
    ) {
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    "overlay definition must be an object");
        }
        JsonObject object = raw.getAsJsonObject();
        ResourceLocation locationId = readId(
                object, "location", id.getNamespace(), true);
        ResourceLocation originAnchor = readId(
                object, "origin_anchor", id.getNamespace(), true);
        ResourceLocation baseSchematic = object.has("base_schematic")
                && !object.get("base_schematic").isJsonNull()
                ? readId(object, "base_schematic",
                        id.getNamespace(), true)
                : null;
        ResourceLocation festivalSchematic = readId(
                object, "festival_schematic", id.getNamespace(), true);
        BlockPos boundsMin = readBlockPos(
                object.get("bounds_min_offset"),
                "bounds_min_offset");
        BlockPos boundsMax = readBlockPos(
                object.get("bounds_max_offset"),
                "bounds_max_offset");
        List<BlockPos> safePositions = readBlockPosList(
                object.get("safe_position_offsets"),
                "safe_position_offsets");
        boolean blackFade = readBoolean(
                object, "requires_black_fade", true);
        boolean cleanupItems = readBoolean(
                object, "cleanup_dropped_items", true);
        boolean cleanupEntities = readBoolean(
                object, "cleanup_tagged_entities", true);
        StardewFestivalMapOverlay.TreeClearance treeClearance =
                readTreeClearance(object.get("tree_clearance"));
        return new StardewFestivalMapOverlay(
                id, locationId, originAnchor, baseSchematic,
                festivalSchematic, boundsMin, boundsMax,
                safePositions, blackFade, cleanupItems,
                cleanupEntities, treeClearance);
    }

    private static StardewFestivalMapOverlay.TreeClearance
    readTreeClearance(JsonElement raw) {
        if (raw == null || raw.isJsonNull()) {
            return StardewFestivalMapOverlay.TreeClearance.NONE;
        }
        if (!raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    "tree_clearance must be an object");
        }
        JsonObject object = raw.getAsJsonObject();
        return new StardewFestivalMapOverlay.TreeClearance(
                readNonNegativeInt(
                        object, "horizontal_radius", 0),
                readNonNegativeInt(object, "up", 0),
                readNonNegativeInt(object, "down", 0));
    }

    private static List<BlockPos> readBlockPosList(
            JsonElement raw,
            String field
    ) {
        if (raw == null || raw.isJsonNull()) {
            return List.of();
        }
        if (!raw.isJsonArray()) {
            throw new IllegalArgumentException(
                    field + " must be an array");
        }
        ArrayList<BlockPos> result = new ArrayList<>();
        JsonArray values = raw.getAsJsonArray();
        for (int index = 0; index < values.size(); index++) {
            result.add(readBlockPos(
                    values.get(index), field + "[" + index + "]"));
        }
        return List.copyOf(result);
    }

    private static BlockPos readBlockPos(
            JsonElement raw,
            String field
    ) {
        if (raw == null || !raw.isJsonArray()) {
            throw new IllegalArgumentException(
                    field + " must be a three-integer array");
        }
        JsonArray values = raw.getAsJsonArray();
        if (values.size() != 3) {
            throw new IllegalArgumentException(
                    field + " must contain exactly three integers");
        }
        return new BlockPos(
                readInteger(values.get(0), field + "[0]"),
                readInteger(values.get(1), field + "[1]"),
                readInteger(values.get(2), field + "[2]"));
    }

    private static int readInteger(JsonElement raw, String field) {
        if (raw == null || !raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    field + " must be an integer");
        }
        double value = raw.getAsDouble();
        if (!Double.isFinite(value)
                || Math.rint(value) != value
                || value < Integer.MIN_VALUE
                || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    field + " must be a 32-bit integer");
        }
        return (int) value;
    }

    private static int readNonNegativeInt(
            JsonObject object,
            String field,
            int fallback
    ) {
        if (!object.has(field)) {
            return fallback;
        }
        int value = readInteger(object.get(field), field);
        if (value < 0) {
            throw new IllegalArgumentException(
                    field + " must be non-negative");
        }
        return value;
    }

    private static boolean readBoolean(
            JsonObject object,
            String field,
            boolean fallback
    ) {
        if (!object.has(field)) {
            return fallback;
        }
        JsonElement raw = object.get(field);
        if (!raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(
                    field + " must be a boolean");
        }
        return raw.getAsBoolean();
    }

    private static ResourceLocation readId(
            JsonObject object,
            String field,
            String defaultNamespace,
            boolean required
    ) {
        if (!object.has(field)) {
            if (required) {
                throw new IllegalArgumentException(
                        "missing " + field);
            }
            return null;
        }
        JsonElement raw = object.get(field);
        if (!raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    field + " must be a resource ID string");
        }
        String value = raw.getAsString().trim();
        ResourceLocation result = ResourceLocation.tryParse(
                value.indexOf(':') >= 0
                        ? value
                        : defaultNamespace + ":" + value);
        if (result == null) {
            throw new IllegalArgumentException(
                    "invalid " + field + " ID: " + value);
        }
        return result;
    }

    public static final class ReloadListener
            extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "festival_map_overlays");
        }

        @Override
        protected void apply(
                Map<ResourceLocation, JsonElement> objects,
                ResourceManager manager,
                ProfilerFiller profiler
        ) {
            LinkedHashMap<ResourceLocation, StardewFestivalMapOverlay>
                    candidate = new LinkedHashMap<>();
            List<String> errors = new ArrayList<>();
            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        try {
                            candidate.put(entry.getKey(), decodeData(
                                    entry.getKey(), entry.getValue()));
                        } catch (RuntimeException exception) {
                            errors.add(entry.getKey() + ": "
                                    + exception.getMessage());
                        }
                    });
            if (!errors.isEmpty()) {
                errors.forEach(error -> StardewCraft.LOGGER.error(
                        "[FestivalMapOverlays] {}", error));
                StardewCraft.LOGGER.error(
                        "[FestivalMapOverlays] Rejected reload; keeping {} "
                                + "data overlays",
                        DATA_DEFINITIONS.size());
                return;
            }
            if (publishData(candidate)) {
                StardewCraft.LOGGER.info(
                        "[FestivalMapOverlays] Applied {} data overlays",
                        candidate.size());
            } else {
                StardewCraft.LOGGER.error(
                        "[FestivalMapOverlays] Rejected reload; keeping {} "
                                + "data overlays",
                        DATA_DEFINITIONS.size());
            }
        }
    }
}
