package com.stardew.craft.monster;

import com.google.gson.*;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishpond.service.FishPondQualifiedItemService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original independent table rolls, including negative Debris resource IDs. No placeholder loot. */
public final class MonsterSourceLoot {
    private static volatile Map<String, Table> tables = bundled();
    private MonsterSourceLoot() {}
    public record Entry(String item, double chance) {}
    public record Table(boolean mineMonster, List<Entry> drops) {}
    private static Map<String, Table> bundled() {
        try (var stream = MonsterSourceLoot.class.getResourceAsStream("/data/stardewcraft/monster_loot/source_tables.json")) {
            if (stream == null) throw new IllegalStateException("Missing source monster loot");
            return decode(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public static Map<String, Table> decode(JsonObject json) {
        var result = new LinkedHashMap<String, Table>();
        for (var table : json.entrySet()) {
            var entries = new ArrayList<Entry>();
            for (var element : table.getValue().getAsJsonObject().getAsJsonArray("drops")) {
                var entry = element.getAsJsonObject();
                double chance = entry.get("chance").getAsDouble();
                String item = entry.get("item").getAsString();
                if (!Double.isFinite(chance) || chance < 0 || chance > 1 || item.isBlank()) throw new IllegalArgumentException("Invalid source loot: " + table.getKey());
                entries.add(new Entry(item, chance));
            }
            result.put(table.getKey(), new Table(table.getValue().getAsJsonObject().get("mine_monster").getAsBoolean(), List.copyOf(entries)));
        }
        if (!result.containsKey("Green Slime")) throw new IllegalArgumentException("Missing Green Slime loot");
        return Map.copyOf(result);
    }
    public static List<String> roll(String source, RandomSource random) {
        var items = new ArrayList<String>();
        for (var entry : tables.getOrDefault(source, new Table(false, List.of())).drops()) if (random.nextDouble() < entry.chance()) items.add(entry.item());
        return items;
    }
    public static ItemStack item(String sourceId, int count) {
        return FishPondQualifiedItemService.createItemStack(sourceId.contains(":") || sourceId.startsWith("(") ? sourceId : "(O)" + sourceId, count);
    }
    public static List<ItemStack> materialize(List<String> ids, RandomSource random) {
        var result = new ArrayList<ItemStack>();
        for (String id : ids) {
            int count = 1;
            if (id.startsWith("-")) {
                id = switch (id) { case "-4" -> "382"; case "-6" -> "384"; default -> id.substring(1); };
                count = 1 + random.nextInt(3);
            }
            ItemStack stack = item(id, count);
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }
    public static final class Reload extends SimpleJsonResourceReloadListener {
        public Reload() { super(new Gson(), "monster_loot"); }
        @Override protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
            try {
                var id = new ResourceLocation(StardewCraft.MODID,"source_tables");
                tables = decode(Objects.requireNonNull(resources.get(id), "Missing source_tables").getAsJsonObject());
            } catch (RuntimeException failure) { StardewCraft.LOGGER.error("Monster loot reload rejected; retaining previous table", failure); }
        }
    }
}
