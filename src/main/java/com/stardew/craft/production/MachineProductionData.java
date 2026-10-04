package com.stardew.craft.production;

import com.google.gson.*;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-authoritative production tuning. Running jobs retain their saved output and deadline. */
public final class MachineProductionData {
    private static final Gson GSON = new Gson();
    private static final AtomicDefinitionStore<Profile> STORE = new AtomicDefinitionStore<>();
    private static final List<String> BUILTINS = List.of("tapper", "worm_bin", "deluxe_worm_bin", "bee_house",
        "solar_panel", "lightning_rod", "geode_crusher", "heavy_furnace", "cask");
    private static volatile String cachedJson = "{}";
    private static final Profile EMPTY = new Profile(1, null, Map.of(), Map.of(), 0);
    private MachineProductionData() {}

    public record Cycle(ResourceLocation output, int minCount, int maxCount, int duration, boolean mornings) {
        public ItemStack createOutput(RandomSource random) {
            return output == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(output),
                minCount == maxCount ? minCount : minCount + random.nextInt(maxCount - minCount + 1));
        }
        /** Mornings use Utility.CalculateMinutesUntilMorning on the 1600-minute machine day. */
        public int rawMinutes(long now) {
            return mornings
                    ? com.stardew.craft.blockentity.TimedProductionBlockEntity.minutesUntilMorning(now, duration)
                    : duration;
        }
        public long deadline(long now, String machine) {
            return now + minutes(machine, rawMinutes(now));
        }
    }
    public record Profile(double multiplier, Integer minutes, Map<String, Cycle> cycles,
                          Map<ResourceLocation, Float> agingRates, int coalPerBatch) {}

    public static Profile profile(String machine) {
        ensureLoaded();
        return STORE.snapshot().definitions().getOrDefault(id(machine), EMPTY);
    }
    public static Cycle cycle(String machine, String key) { return profile(machine).cycles().get(key); }
    public static int minutes(String machine, int proposed) {
        Profile p = profile(machine);
        double adjusted = (p.minutes() == null ? proposed : p.minutes()) * p.multiplier();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(proposed == 0 && p.minutes() == null ? 0 : 1, Math.ceil(adjusted)));
    }
    public static Float agingRate(ResourceLocation item) { return profile("cask").agingRates().get(item); }
    public static ResourceLocation id(String value) {
        return ResourceLocation.parse(value.contains(":") ? value : StardewCraft.MODID + ":" + value);
    }
    private static synchronized void ensureLoaded() {
        if (STORE.snapshot().version() == 0) reload(Map.of());
    }

    /** Also used by headless contract tests; commits all definitions together or retains the previous snapshot. */
    public static synchronized boolean reload(Map<ResourceLocation, JsonElement> overrides) {
        Map<ResourceLocation, JsonObject> sources = new LinkedHashMap<>();
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        try {
            for (String name : BUILTINS) {
                try (var stream = MachineProductionData.class.getResourceAsStream("/data/stardewcraft/production/" + name + ".json")) {
                    if (stream == null) throw new IllegalArgumentException("Missing bundled production profile " + name);
                    sources.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load bundled production defaults", error);
        }
        Map<ResourceLocation, Profile> definitions = new LinkedHashMap<>();
        Map<ResourceLocation, String> canonical = new LinkedHashMap<>();
        Set<ResourceLocation> ids = new LinkedHashSet<>(sources.keySet());
        ids.addAll(overrides.keySet());
        for (ResourceLocation id : ids) {
            try {
                JsonObject merged = sources.getOrDefault(id, new JsonObject()).deepCopy();
                if (overrides.containsKey(id)) merge(merged, overrides.get(id).getAsJsonObject());
                Profile profile = parse(merged);
                if (id.equals(id("cask")) && profile.minutes() != null)
                    throw new IllegalArgumentException("Cask uses aging_rates and time_multiplier, not minutes");
                for (var entry : profile.cycles().entrySet()) {
                    if ((id.equals(id("tapper")) || id.equals(id("worm_bin")) || id.equals(id("deluxe_worm_bin"))
                            || id.equals(id("solar_panel")) || id.equals(id("lightning_rod"))) && entry.getValue().output() == null)
                        throw new IllegalArgumentException("Missing cycle output: " + entry.getKey());
                    if ((id.equals(id("bee_house")) || id.equals(id("geode_crusher"))) && entry.getValue().output() != null)
                        throw new IllegalArgumentException("This machine resolves its output elsewhere; configure only duration here");
                    if (id.equals(id("geode_crusher")) && entry.getValue().mornings())
                        throw new IllegalArgumentException("Geode crusher duration uses minutes");
                }
                definitions.put(id, profile);
                canonical.put(id, GSON.toJson(merged));
            } catch (RuntimeException error) {
                diagnostics.add(DefinitionDiagnostic.error(id, id, error.getMessage()));
            }
        }
        var result = STORE.applyLocal(definitions, canonical, diagnostics);
        if (!result.accepted()) StardewCraft.LOGGER.error("Production reload rejected; previous settings retained: {}", diagnostics);
        if (result.accepted()) {
            JsonObject json = new JsonObject();
            canonical.forEach((key, value) -> json.add(key.toString(), JsonParser.parseString(value)));
            cachedJson = GSON.toJson(json);
        }
        return result.accepted();
    }

    public static String getCachedJson() { ensureLoaded(); return cachedJson; }
    public static void applyFromJson(String json) {
        try {
            Map<ResourceLocation, JsonElement> values = new LinkedHashMap<>();
            JsonParser.parseString(json).getAsJsonObject().entrySet().forEach(e -> values.put(id(e.getKey()), e.getValue()));
            MachineProductionData.reload(values);
        } catch (RuntimeException error) {
            StardewCraft.LOGGER.error("Invalid production sync; previous settings retained", error);
        }
    }

    // Merge named cycles/rates, but replace each duration atomically (minutes and mornings are exclusive).
    private static void merge(JsonObject target, JsonObject override) {
        for (var entry : override.entrySet()) {
            String key = entry.getKey();
            if ((key.equals("cycles") || key.equals("aging_rates")) && target.has(key)) {
                JsonObject map = target.getAsJsonObject(key);
                for (var value : entry.getValue().getAsJsonObject().entrySet()) {
                    if (key.equals("cycles") && map.has(value.getKey())) {
                        JsonObject cycle = map.getAsJsonObject(value.getKey());
                        value.getValue().getAsJsonObject().entrySet().forEach(field -> cycle.add(field.getKey(), field.getValue()));
                    } else map.add(value.getKey(), value.getValue());
                }
            } else target.add(key, entry.getValue());
        }
    }
    private static Profile parse(JsonObject root) {
        keys(root, Set.of("time_multiplier", "minutes", "cycles", "aging_rates", "coal_per_batch"));
        double multiplier = root.has("time_multiplier") ? number(root.get("time_multiplier"), 0.000001, 1000000) : 1;
        Integer minutes = root.has("minutes") ? integer(root.get("minutes"), 1, Integer.MAX_VALUE) : null;
        int coal = root.has("coal_per_batch") ? integer(root.get("coal_per_batch"), 0, 64) : 0;
        Map<String, Cycle> cycles = new LinkedHashMap<>();
        if (root.has("cycles")) for (var entry : root.getAsJsonObject("cycles").entrySet()) {
            JsonObject c = entry.getValue().getAsJsonObject();
            keys(c, Set.of("output", "min_count", "max_count", "duration"));
            ResourceLocation output = c.has("output") ? ResourceLocation.parse(c.get("output").getAsString()) : null;
            if (output != null && (!BuiltInRegistries.ITEM.containsKey(output) || BuiltInRegistries.ITEM.get(output) == net.minecraft.world.item.Items.AIR))
                throw new IllegalArgumentException("Unknown output " + output);
            int min = c.has("min_count") ? integer(c.get("min_count"), 1, 999) : 1;
            int max = c.has("max_count") ? integer(c.get("max_count"), min, 999) : min;
            JsonObject duration = c.getAsJsonObject("duration");
            keys(duration, Set.of("minutes", "mornings"));
            if (duration.size() != 1) throw new IllegalArgumentException("Duration needs exactly one of minutes or mornings");
            boolean mornings = duration.has("mornings");
            int time = integer(duration.get(mornings ? "mornings" : "minutes"), 1, mornings ? 1000000 : Integer.MAX_VALUE);
            cycles.put(entry.getKey(), new Cycle(output, min, max, time, mornings));
        }
        Map<ResourceLocation, Float> rates = new LinkedHashMap<>();
        if (root.has("aging_rates")) for (var entry : root.getAsJsonObject("aging_rates").entrySet()) {
            ResourceLocation item = ResourceLocation.parse(entry.getKey());
            if (!BuiltInRegistries.ITEM.containsKey(item)) throw new IllegalArgumentException("Unknown aging item " + item);
            rates.put(item, (float) number(entry.getValue(), 0.000001, 1000000));
        }
        return new Profile(multiplier, minutes, Map.copyOf(cycles), Map.copyOf(rates), coal);
    }
    private static void keys(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown production field " + key);
    }
    private static double number(JsonElement value, double min, double max) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected a number");
        double n = value.getAsDouble();
        if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException("Production number outside " + min + ".." + max);
        return n;
    }
    private static int integer(JsonElement value, int min, int max) {
        double n = number(value, min, max);
        if (n != Math.rint(n)) throw new IllegalArgumentException("Expected a whole number");
        return (int) n;
    }
    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() { super(GSON, "production"); }
        @Override protected Map<ResourceLocation, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonElement> values = new LinkedHashMap<>();
            var converter = net.minecraft.resources.FileToIdConverter.json("production");
            converter.listMatchingResources(manager).forEach((path, resource) -> {
                ResourceLocation id = converter.fileToId(path);
                try (var reader = resource.openAsReader()) {
                    values.put(id, JsonParser.parseReader(reader));
                } catch (Exception error) {
                    StardewCraft.LOGGER.error("Invalid production JSON {}", path, error);
                    values.put(id, JsonNull.INSTANCE); // Keep the error in the candidate so the entire reload is rejected.
                }
            });
            return values;
        }
        @Override protected void apply(Map<ResourceLocation, JsonElement> values, ResourceManager manager, ProfilerFiller profiler) {
            MachineProductionData.reload(values);
        }
    }
}
