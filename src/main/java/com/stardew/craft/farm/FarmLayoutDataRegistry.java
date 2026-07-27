package com.stardew.craft.farm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutAttachment;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutConfigField;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutRegistration;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Atomic reload catalog for {@code data/<namespace>/farm_layouts/*.json}. */
public final class FarmLayoutDataRegistry {
    public static final int CURRENT_FORMAT = 1;
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();
    private static volatile ReloadReport lastReport =
            new ReloadReport(0, 0, false, List.of());

    private FarmLayoutDataRegistry() {
    }

    public static ReloadReport lastReport() {
        return lastReport;
    }

    static ReloadReport reload(
            Map<ResourceLocation, JsonElement> objects,
            @Nullable ResourceManager manager
    ) {
        LinkedHashMap<ResourceLocation, StardewFarmLayoutRegistration>
                candidate = new LinkedHashMap<>();
        ArrayList<String> errors = new ArrayList<>();
        objects.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(
                                ResourceLocation::toString)))
                .forEach(entry -> {
                    try {
                        candidate.put(
                                entry.getKey(),
                                decode(entry.getKey(),
                                        entry.getValue(),
                                        manager));
                    } catch (RuntimeException exception) {
                        errors.add(entry.getKey() + ": "
                                + exception.getMessage());
                    }
                });
        if (errors.isEmpty()) {
            try {
                StardewFarmLayoutRegistry.publishData(candidate);
            } catch (RuntimeException exception) {
                errors.add(exception.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            errors.forEach(error -> StardewCraft.LOGGER.error(
                    "[FarmLayouts] {}", error));
            StardewFarmLayoutRegistry.DataSnapshot active =
                    StardewFarmLayoutRegistry.dataSnapshot();
            lastReport = new ReloadReport(
                    active.revision(),
                    active.registrations().size(),
                    true,
                    errors);
            StardewCraft.LOGGER.error(
                    "[FarmLayouts] Rejected reload; keeping {} data layouts",
                    lastReport.activeCount());
            return lastReport;
        }
        StardewFarmLayoutRegistry.DataSnapshot active =
                StardewFarmLayoutRegistry.dataSnapshot();
        lastReport = new ReloadReport(
                active.revision(),
                active.registrations().size(),
                false,
                List.of());
        StardewCraft.LOGGER.info(
                "[FarmLayouts] Applied snapshot v{} ({} data layouts)",
                lastReport.revision(),
                lastReport.activeCount());
        return lastReport;
    }

    static StardewFarmLayoutRegistration decode(
            ResourceLocation id,
            JsonElement raw,
            @Nullable ResourceManager resourceManager
    ) {
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    "farm layout definition must be an object");
        }
        JsonObject object = raw.getAsJsonObject();
        rejectUnknownFields(object, "farm layout", Set.of(
                "format", "version", "selectable",
                "display_name", "description", "icon", "schematic",
                "origin_y", "size", "spawn", "greenhouse", "totem",
                "entries", "biome", "forage", "cave",
                "configuration", "attachments"));
        int format = readInt(object.get("format"), "format", true);
        if (format != CURRENT_FORMAT) {
            throw new IllegalArgumentException(
                    "unsupported format " + format
                            + " (expected " + CURRENT_FORMAT + ")");
        }
        int version = readInt(
                object.get("version"), "version", true);
        if (version < 1) {
            throw new IllegalArgumentException(
                    "version must be at least 1");
        }
        boolean selectable = readBoolean(
                object.get("selectable"), "selectable", true);
        Component displayName = readComponent(
                object.get("display_name"), "display_name");
        Component description = readComponent(
                object.get("description"), "description");
        ResourceLocation icon = readId(
                object.get("icon"), "icon", id.getNamespace());
        ResourceLocation schematic = readId(
                object.get("schematic"), "schematic",
                id.getNamespace());
        validateSchematicReference(schematic, resourceManager);

        int originY = readInt(
                object.get("origin_y"), "origin_y", true);
        BlockPos size = readPos(object.get("size"), "size");
        Point spawn = readPoint(
                object.get("spawn"), "spawn", true);
        BlockPos greenhouse = readPos(
                object.get("greenhouse"), "greenhouse");
        BlockPos totem = readPos(
                object.get("totem"), "totem");

        JsonObject entries = readObject(
                object.get("entries"), "entries");
        rejectUnknownFields(entries, "entries",
                Set.of("south", "east", "west"));
        StardewFarmLayout.Entry south = readEntry(
                entries.get("south"), "entries.south");
        StardewFarmLayout.Entry east = readEntry(
                entries.get("east"), "entries.east");
        StardewFarmLayout.Entry west = readEntry(
                entries.get("west"), "entries.west");

        String biome = object.has("biome")
                ? readId(object.get("biome"), "biome",
                        id.getNamespace()).toString()
                : null;
        BlockPos forageMin = null;
        BlockPos forageMax = null;
        if (object.has("forage")) {
            JsonObject forage = readObject(
                    object.get("forage"), "forage");
            rejectUnknownFields(forage, "forage",
                    Set.of("min", "max"));
            forageMin = readPos(
                    forage.get("min"), "forage.min");
            forageMax = readPos(
                    forage.get("max"), "forage.max");
        }

        StardewFarmLayout.Region caveBlack = null;
        StardewFarmLayout.Region cavePortal = null;
        StardewFarmLayout.Region caveClear = null;
        BlockPos caveExit = null;
        float caveExitYaw = 0.0F;
        if (object.has("cave")) {
            JsonObject cave = readObject(
                    object.get("cave"), "cave");
            rejectUnknownFields(cave, "cave", Set.of(
                    "black_wall", "portal_wall",
                    "clear_box", "exit"));
            if (cave.size() == 0) {
                throw new IllegalArgumentException(
                        "cave must declare at least one field");
            }
            if (cave.has("black_wall")) {
                caveBlack = readRegion(
                        cave.get("black_wall"), "cave.black_wall");
            }
            if (cave.has("portal_wall")) {
                cavePortal = readRegion(
                        cave.get("portal_wall"), "cave.portal_wall");
            }
            if (cave.has("clear_box")) {
                caveClear = readRegion(
                        cave.get("clear_box"), "cave.clear_box");
            }
            Point exit = readPoint(
                    cave.get("exit"), "cave.exit", false);
            if (exit != null) {
                caveExit = exit.offset();
                caveExitYaw = exit.yaw();
            }
        }

        StardewFarmLayout layout = new StardewFarmLayout(
                id,
                selectable,
                displayName,
                description,
                icon,
                schematic,
                originY,
                size.getX(),
                size.getY(),
                size.getZ(),
                spawn.offset(),
                spawn.yaw(),
                greenhouse,
                totem,
                south,
                east,
                west,
                biome,
                forageMin,
                forageMax,
                caveBlack,
                cavePortal,
                caveClear,
                caveExit,
                caveExitYaw);
        return new StardewFarmLayoutRegistration(
                layout,
                version,
                readConfiguration(
                        object.get("configuration"),
                        id.getNamespace()),
                readAttachments(
                        object.get("attachments"),
                        id.getNamespace()));
    }

    private static List<StardewFarmLayoutConfigField> readConfiguration(
            JsonElement raw,
            String defaultNamespace
    ) {
        if (raw == null || raw.isJsonNull()) {
            return List.of();
        }
        JsonArray values = readArray(raw, "configuration");
        ArrayList<StardewFarmLayoutConfigField> fields =
                new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String path = "configuration[" + index + "]";
            JsonObject field = readObject(values.get(index), path);
            ResourceLocation id = readId(
                    field.get("id"), path + ".id",
                    defaultNamespace);
            Component label = readComponent(
                    field.get("label"), path + ".label");
            Component description = field.has("description")
                    ? readComponent(
                            field.get("description"),
                            path + ".description")
                    : Component.empty();
            String rawType = readString(
                    field.get("type"), path + ".type");
            StardewFarmLayoutConfigField.Type type;
            try {
                type = StardewFarmLayoutConfigField.Type.valueOf(
                        rawType.trim().toUpperCase(
                                java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        path + ".type must be boolean, integer or choice");
            }
            Set<String> allowedFields = switch (type) {
                case BOOLEAN -> Set.of(
                        "id", "type", "label",
                        "description", "default");
                case INTEGER -> Set.of(
                        "id", "type", "label", "description",
                        "default", "minimum", "maximum");
                case CHOICE -> Set.of(
                        "id", "type", "label", "description",
                        "default", "choices");
            };
            rejectUnknownFields(field, path, allowedFields);
            StardewFarmLayoutConfigField decoded = switch (type) {
                case BOOLEAN -> StardewFarmLayoutConfigField.bool(
                        id, label, description,
                        readBoolean(
                                field.get("default"),
                                path + ".default", true));
                case INTEGER -> StardewFarmLayoutConfigField.integer(
                        id, label, description,
                        readInt(
                                field.get("default"),
                                path + ".default", true),
                        readInt(
                                field.get("minimum"),
                                path + ".minimum", true),
                        readInt(
                                field.get("maximum"),
                                path + ".maximum", true));
                case CHOICE -> {
                    JsonArray choices = readArray(
                            field.get("choices"),
                            path + ".choices");
                    ArrayList<String> decodedChoices =
                            new ArrayList<>();
                    for (int choiceIndex = 0;
                         choiceIndex < choices.size();
                         choiceIndex++) {
                        decodedChoices.add(readString(
                                choices.get(choiceIndex),
                                path + ".choices["
                                        + choiceIndex + "]"));
                    }
                    yield StardewFarmLayoutConfigField.choice(
                            id, label, description,
                            readString(
                                    field.get("default"),
                                    path + ".default"),
                            decodedChoices);
                }
            };
            fields.add(decoded);
        }
        return List.copyOf(fields);
    }

    private static List<StardewFarmLayoutAttachment> readAttachments(
            JsonElement raw,
            String defaultNamespace
    ) {
        if (raw == null || raw.isJsonNull()) {
            return List.of();
        }
        JsonArray values = readArray(raw, "attachments");
        ArrayList<StardewFarmLayoutAttachment> attachments =
                new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String path = "attachments[" + index + "]";
            JsonObject attachment = readObject(
                    values.get(index), path);
            rejectUnknownFields(attachment, path,
                    Set.of("id", "offset", "yaw", "tags"));
            ResourceLocation id = readId(
                    attachment.get("id"), path + ".id",
                    defaultNamespace);
            BlockPos offset = readPos(
                    attachment.get("offset"), path + ".offset");
            float yaw = attachment.has("yaw")
                    ? readFloat(
                            attachment.get("yaw"),
                            path + ".yaw")
                    : 0.0F;
            Set<ResourceLocation> tags = readIds(
                    attachment.get("tags"),
                    path + ".tags",
                    defaultNamespace);
            attachments.add(new StardewFarmLayoutAttachment(
                    id, offset, yaw, tags));
        }
        return List.copyOf(attachments);
    }

    private static StardewFarmLayout.Entry readEntry(
            JsonElement raw,
            String path
    ) {
        JsonObject entry = readObject(raw, path);
        rejectUnknownFields(entry, path,
                Set.of("teleport", "yaw", "exit_min", "exit_max"));
        return new StardewFarmLayout.Entry(
                readPos(entry.get("teleport"), path + ".teleport"),
                entry.has("yaw")
                        ? readFloat(entry.get("yaw"), path + ".yaw")
                        : 0.0F,
                readPos(entry.get("exit_min"), path + ".exit_min"),
                readPos(entry.get("exit_max"), path + ".exit_max"));
    }

    private static StardewFarmLayout.Region readRegion(
            JsonElement raw,
            String path
    ) {
        JsonObject region = readObject(raw, path);
        rejectUnknownFields(region, path, Set.of("min", "max"));
        return new StardewFarmLayout.Region(
                readPos(region.get("min"), path + ".min"),
                readPos(region.get("max"), path + ".max"));
    }

    private static Point readPoint(
            JsonElement raw,
            String path,
            boolean required
    ) {
        if ((raw == null || raw.isJsonNull()) && !required) {
            return null;
        }
        JsonObject point = readObject(raw, path);
        rejectUnknownFields(point, path, Set.of("offset", "yaw"));
        return new Point(
                readPos(point.get("offset"), path + ".offset"),
                point.has("yaw")
                        ? readFloat(point.get("yaw"), path + ".yaw")
                        : 0.0F);
    }

    private static Component readComponent(
            JsonElement raw,
            String field
    ) {
        if (raw == null || raw.isJsonNull()) {
            throw new IllegalArgumentException("missing " + field);
        }
        try {
            return ComponentSerialization.CODEC
                    .parse(JsonOps.INSTANCE, raw)
                    .getOrThrow(IllegalArgumentException::new);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "invalid " + field + ": "
                            + exception.getMessage(), exception);
        }
    }

    private static void validateSchematicReference(
            ResourceLocation schematic,
            @Nullable ResourceManager manager
    ) {
        String path = schematic.getPath();
        if (!path.endsWith(".schem") && !path.endsWith(".nbt")) {
            throw new IllegalArgumentException(
                    "schematic must end with .schem or .nbt");
        }
        if (manager == null) {
            return;
        }
        ResourceLocation resource = ResourceLocation.fromNamespaceAndPath(
                schematic.getNamespace(),
                "structures/" + schematic.getPath());
        if (manager.getResource(resource).isEmpty()) {
            throw new IllegalArgumentException(
                    "missing structure resource data/"
                            + resource.getNamespace() + "/"
                            + resource.getPath());
        }
    }

    private static JsonObject readObject(
            JsonElement raw,
            String field
    ) {
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    field + " must be an object");
        }
        return raw.getAsJsonObject();
    }

    private static void rejectUnknownFields(
            JsonObject object,
            String path,
            Set<String> allowed
    ) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException(
                        "unknown " + path + " field: " + field);
            }
        }
    }

    private static JsonArray readArray(
            JsonElement raw,
            String field
    ) {
        if (raw == null || !raw.isJsonArray()) {
            throw new IllegalArgumentException(
                    field + " must be an array");
        }
        return raw.getAsJsonArray();
    }

    private static BlockPos readPos(
            JsonElement raw,
            String field
    ) {
        JsonArray values = readArray(raw, field);
        if (values.size() != 3) {
            throw new IllegalArgumentException(
                    field + " must contain exactly three integers");
        }
        return new BlockPos(
                readInt(values.get(0), field + "[0]", true),
                readInt(values.get(1), field + "[1]", true),
                readInt(values.get(2), field + "[2]", true));
    }

    private static Set<ResourceLocation> readIds(
            JsonElement raw,
            String field,
            String defaultNamespace
    ) {
        if (raw == null || raw.isJsonNull()) {
            return Set.of();
        }
        JsonArray values = readArray(raw, field);
        LinkedHashSet<ResourceLocation> ids =
                new LinkedHashSet<>();
        for (int index = 0; index < values.size(); index++) {
            ids.add(readId(
                    values.get(index),
                    field + "[" + index + "]",
                    defaultNamespace));
        }
        return Set.copyOf(ids);
    }

    private static ResourceLocation readId(
            JsonElement raw,
            String field,
            String defaultNamespace
    ) {
        String value = readString(raw, field).trim();
        ResourceLocation id = ResourceLocation.tryParse(
                value.indexOf(':') >= 0
                        ? value
                        : defaultNamespace + ":" + value);
        if (id == null) {
            throw new IllegalArgumentException(
                    "invalid " + field + " ID: " + value);
        }
        return id;
    }

    private static String readString(
            JsonElement raw,
            String field
    ) {
        if (raw == null || !raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    field + " must be a string");
        }
        return raw.getAsString();
    }

    private static boolean readBoolean(
            JsonElement raw,
            String field,
            boolean required
    ) {
        if (raw == null || raw.isJsonNull()) {
            if (!required) {
                return false;
            }
            throw new IllegalArgumentException("missing " + field);
        }
        if (!raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(
                    field + " must be a boolean");
        }
        return raw.getAsBoolean();
    }

    private static int readInt(
            JsonElement raw,
            String field,
            boolean required
    ) {
        if (raw == null || raw.isJsonNull()) {
            if (!required) {
                return 0;
            }
            throw new IllegalArgumentException("missing " + field);
        }
        if (!raw.isJsonPrimitive()
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

    private static float readFloat(
            JsonElement raw,
            String field
    ) {
        if (raw == null || !raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    field + " must be a finite number");
        }
        float value = raw.getAsFloat();
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(
                    field + " must be a finite number");
        }
        return value;
    }

    public static final class ReloadListener
            extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "farm_layouts");
        }

        @Override
        protected void apply(
                Map<ResourceLocation, JsonElement> objects,
                ResourceManager manager,
                ProfilerFiller profiler
        ) {
            FarmLayoutDataRegistry.reload(objects, manager);
        }
    }

    public record ReloadReport(
            long revision,
            int activeCount,
            boolean rejected,
            List<String> errors
    ) {
        public ReloadReport {
            if (revision < 0 || activeCount < 0) {
                throw new IllegalArgumentException(
                        "Invalid farm layout reload report");
            }
            errors = List.copyOf(errors);
        }
    }

    private record Point(BlockPos offset, float yaw) {
    }
}
