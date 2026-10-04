package com.stardew.craft.fishing.data;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.query.*;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.loot.LootEffects;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Replaceable base treasure query; namespaced treasure_pools remain compatible additions/replacements. */
public class TreasureLootManager extends SimplePreparableReloadListener<TreasureLootManager.TreasureData> {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(TreasureLootManager.class);
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE = ResourceLocation.parse("stardewcraft:fishing/fishing_treasure.json");
    private volatile TreasureData data = new TreasureData();
    public static class TreasureLootEntry {
        public String item;
        public int minCount = 1, maxCount = 1, weight = 100, minFishingLevel = 0;
    }
    public static class TreasureData {
        public List<TreasureLootEntry> commonLoot = new ArrayList<>(), rareLoot = new ArrayList<>(),
                goldenLoot = new ArrayList<>(), fallbackLoot = new ArrayList<>();
        public double rollChanceStart = 1, rollChanceDecayNormal = .4, rollChanceDecayGolden = .6,
                rareChance = .15, goldenPoolChance = .5;
        public JsonElement query;
        private transient StardewItemQuery compiled;
    }
    @Override protected TreasureData prepare(@Nonnull ResourceManager manager, @Nonnull ProfilerFiller profiler) {
        try (var reader = manager.getResourceOrThrow(FILE).openAsReader()) {
            return decode(JsonParser.parseReader(reader));
        } catch (Exception ex) {
            LOGGER.error("[Fishing treasure] Rejected base reload; keeping last valid definition", ex);
            return data;
        }
    }
    @Override protected void apply(@Nonnull TreasureData prepared, @Nonnull ResourceManager manager, @Nonnull ProfilerFiller profiler) {
        data = prepared;
    }
    public void initializeWithDefaults() { loadFromBundledData(); }
    public void loadFromBundledData() {
        try (var in = TreasureLootManager.class.getResourceAsStream("/data/stardewcraft/fishing/fishing_treasure.json")) {
            if (in == null) throw new IOException("Missing bundled fishing treasure definition");
            try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) { data = decode(JsonParser.parseReader(reader)); }
        } catch (Exception ex) { LOGGER.error("[Fishing treasure] Keeping last valid base definition", ex); }
    }
    private static TreasureData decode(JsonElement json) {
        var value = GSON.fromJson(json, TreasureData.class);
        if (value.query != null) value.compiled = StardewItemQueries.CODEC.parse(JsonOps.INSTANCE, value.query).getOrThrow();
        else {
            for (var pool : List.of(value.commonLoot, value.rareLoot, value.goldenLoot, value.fallbackLoot))
                for (var entry : pool) {
                    if (ResourceLocation.tryParse(entry.item) == null || entry.weight < 1 || entry.minCount < 1
                            || entry.maxCount < entry.minCount || entry.maxCount > 4096)
                        throw new IllegalArgumentException("Invalid legacy fishing treasure entry");
                }
            if (!Double.isFinite(value.rollChanceStart) || value.rollChanceStart < 0 || value.rollChanceStart > 1
                    || !(value.rollChanceDecayNormal >= 0 && value.rollChanceDecayNormal < 1)
                    || !(value.rollChanceDecayGolden >= 0 && value.rollChanceDecayGolden < 1)
                    || !(value.rareChance >= 0 && value.rareChance <= 1)
                    || !(value.goldenPoolChance >= 0 && value.goldenPoolChance <= 1))
                throw new IllegalArgumentException("Invalid legacy fishing treasure probabilities");
        }
        return value;
    }
    public List<ItemStack> generateTreasure(int fishingLevel, boolean golden, RandomSource random) {
        return generateTreasure(fishingLevel, golden, random, 5, 0);
    }
    public List<ItemStack> generateTreasure(int fishingLevel, boolean golden, RandomSource random, int distance, double dailyLuck) {
        return generateTreasure(fishingLevel, golden, random, distance, dailyLuck, null);
    }
    public List<ItemStack> generateTreasure(int fishingLevel, boolean golden, RandomSource random,
            int distance, double dailyLuck, @Nullable ServerPlayer player) {
        if (FishingTreasurePoolData.replacesBase(player, golden)) return new ArrayList<>();
        var current = data;
        var result = new ArrayList<ItemStack>();
        if (current.compiled != null) {
            var server = player == null ? net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() : player.getServer();
            if (server == null) throw new IllegalStateException("Fishing loot requires a server query context");
            var effects = new ArrayList<StardewAction>();
            int dist = Math.clamp(distance, 0, 5);
            var parameters = Map.of("fishing_level", (double) fishingLevel, "water_distance", (double) dist,
                    "daily_luck", dailyLuck, "golden", golden ? 1d : 0d,
                    "equipment_luck", (1 + dailyLuck) * dist / 5,
                    "mystery_box_multiplier", BookPowerEffects.applyMysteryBoxChance(
                            player == null ? null : PlayerDataManager.getPlayerData(player), 1));
            var context = new StardewItemQueryContext(player == null ? server.overworld() : player.serverLevel(),
                    player, random::nextLong, parameters, effects::add);
            StardewItemQueries.resolve(current.compiled, context)
                    .resultOrPartial(error -> LOGGER.error("[Fishing treasure] {}", error)).ifPresent(result::addAll);
            if (!result.isEmpty()) LootEffects.commit(player, effects);
        } else {
            // The legacy JSON arrays are now actually consumed; no Java fallback content is inserted.
            double chance = current.rollChanceStart;
            for (int roll = 0; roll < 256 && random.nextDouble() < chance; roll++) {
                chance *= golden ? current.rollChanceDecayGolden : current.rollChanceDecayNormal;
                var pool = golden && random.nextDouble() < current.goldenPoolChance ? current.goldenLoot
                        : random.nextDouble() < current.rareChance ? current.rareLoot : current.commonLoot;
                selectLegacy(result, pool, fishingLevel, random);
            }
            if (result.isEmpty()) selectLegacy(result, current.fallbackLoot, fishingLevel, random);
        }
        if (com.stardew.craft.festival.desert.DesertFestivalWillyFishingService.shouldForceGoldenBobberTreasure(player)) {
            result.clear(); result.add(new ItemStack(com.stardew.craft.item.ModItems.GOLDEN_BOBBER.get()));
        }
        return result;
    }
    private static void selectLegacy(List<ItemStack> out, List<TreasureLootEntry> pool, int level, RandomSource random) {
        var entries = pool.stream().filter(e -> e.minFishingLevel <= level).toList();
        long weight = entries.stream().mapToLong(e -> e.weight).sum();
        if (weight == 0) return;
        long selected = Math.floorMod(random.nextLong(), weight);
        for (var entry : entries) {
            if ((selected -= entry.weight) < 0) {
                var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.item));
                var stack = new ItemStack(item, entry.minCount + random.nextInt(entry.maxCount - entry.minCount + 1));
                if (!stack.isEmpty()) out.add(stack);
                break;
            }
        }
    }
}
