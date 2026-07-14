package com.stardew.craft.block.utility;

import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.data.VanillaObjectCatalog;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.mining.MiningPlayerData;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 垃圾桶战利品表 — 对齐原版 Data/GarbageCans.json + TryGetGarbageItem() 逻辑。
 * <p>
 * 掉落优先级：BeforeAll → 位置特定 → AfterAll。
 * 每个阶段内命中第一个符合条件的条目后即停止。
 */
@SuppressWarnings("null")
public final class GarbageCanLootTable {

    private static final float DEFAULT_BASE_CHANCE = 0.2f;

    private GarbageCanLootTable() {}

    /** User-captured Minecraft positions for the four named vanilla Town garbage cans. */
    @Nullable
    public static String namedCanId(BlockPos pos) {
        if (pos == null) return null;
        if (pos.equals(new BlockPos(40, 64, 2))) return "Evelyn";
        if (pos.equals(new BlockPos(33, 64, 12))) return "Saloon";
        if (pos.equals(new BlockPos(112, 64, 27))) return "Blacksmith";
        if (pos.equals(new BlockPos(134, 64, 44))) return "Museum";
        return null;
    }

    /**
     * 结果记录：包含物品和是否 Mega/DoubleMega 标识。
     */
    public record Result(ItemStack item, boolean isMegaSuccess, boolean isDoubleMegaSuccess) {}

    /**
     * 尝试为给定 canId 计算今日掉落。
     *
     * @param canId            垃圾桶标识（位置 hash 或命名 ID）
     * @param dailyLuck        玩家日运
     * @param trashCansChecked 玩家累计翻垃圾桶次数
     * @param daySeed          当日确定性种子
     * @param player           服务端玩家，用于获取进度相关数据
     * @return 掉落结果，若无掉落则返回 null
     */
    @Nullable
    public static Result tryGetItem(String canId, double dailyLuck, int trashCansChecked,
                                    long daySeed, ServerPlayer player) {
        float baseChance = DEFAULT_BASE_CHANCE;
        baseChance += (float) dailyLuck;
        baseChance += BookPowerEffects.getTrashCanChanceBonus(PlayerDataManager.getPlayerData(player));

        Random rng = createGarbageRandom(canId, daySeed);

        boolean baseChancePassed = rng.nextDouble() < baseChance;

        // ---- BeforeAll ----
        Result beforeAll = evaluateBeforeAll(rng, baseChancePassed, trashCansChecked);
        if (beforeAll != null) return beforeAll;

        // ---- Named can items (Data/GarbageCans.json) ----
        Result locationSpecific = evaluateLocationSpecific(
                canId, rng, baseChancePassed, dailyLuck, daySeed);
        if (locationSpecific != null) return locationSpecific;

        // ---- AfterAll ----
        return evaluateAfterAll(rng, baseChancePassed, trashCansChecked, player);
    }

    @Nullable
    private static Result evaluateLocationSpecific(String canId, Random rng, boolean baseChancePassed,
                                                   double dailyLuck, long daySeed) {
        if (!baseChancePassed) return null;
        double specialChance = 0.2 + dailyLuck;
        return switch (canId) {
            case "Evelyn" -> rng.nextDouble() < specialChance
                    ? result(ModItems.COOKING_DISHES.get("cookie").get(), 1)
                    : null;
            case "Blacksmith" -> {
                if (rng.nextDouble() >= specialChance) yield null;
                Item[] ores = {
                        ModItems.EARTH_COPPER_ORE.get(),
                        ModItems.EARTH_IRON_ORE.get(),
                        ModItems.EARTH_GOLD_ORE.get()
                };
                yield result(ores[rng.nextInt(ores.length)], 1 + rng.nextInt(4));
            }
            case "Museum" -> {
                Random geodeRoll = createSyncedDayRandom(daySeed, "garbage_museum_535");
                if (geodeRoll.nextDouble() >= specialChance) yield null;
                Random omniRoll = createSyncedDayRandom(daySeed, "garbage_museum_749");
                yield omniRoll.nextDouble() < 0.05
                        ? result(ModItems.OMNI_GEODE.get(), 1)
                        : result(ModItems.GEODE.get(), 1);
            }
            case "Saloon" -> {
                Random dishRoll = createSyncedDayRandom(daySeed, "garbage_saloon_dish");
                if (dishRoll.nextDouble() >= specialChance) yield null;
                ItemStack dish = dishOfTheDay(daySeed);
                yield dish.isEmpty() ? null : new Result(dish, false, false);
            }
            default -> null;
        };
    }

    private static Result result(Item item, int count) {
        return new Result(new ItemStack(item, count), false, false);
    }

    /** Game1.UpdateDishOfTheDay: random object 194..239, excluding the fixed Saloon stock. */
    private static ItemStack dishOfTheDay(long daySeed) {
        Random rng = createSyncedDayRandom(daySeed, "dish_of_the_day");
        for (int attempt = 0; attempt < 100; attempt++) {
            int objectId = 194 + rng.nextInt(46);
            if (objectId == 196 || objectId == 206 || objectId == 216
                    || objectId == 224 || objectId == 217) {
                continue;
            }
            ItemStack stack = VanillaObjectCatalog.stackFor(
                    VanillaObjectCatalog.entryByKey(Integer.toString(objectId)));
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    // ==================== BeforeAll ====================

    @Nullable
    private static Result evaluateBeforeAll(Random rng, boolean baseChancePassed, int trashCansChecked) {
        // Garbage Hat: 20+ 次, 0.2% 概率, DoubleMega — 我们没有此物品，跳过
        // Trash Catalogue: 50+ 次, 0.2% 概率, DoubleMega — 我们没有此物品，跳过
        // Qi Bean: 需要特殊订单规则 DROP_QI_BEANS, 25% 概率 — 我们没有此物品，跳过
        return null;
    }

    // ==================== AfterAll ====================

    @Nullable
    private static Result evaluateAfterAll(Random rng, boolean baseChancePassed,
                                           int trashCansChecked, ServerPlayer player) {
        // MegaSuccess: 20+ 次, 1% 概率
        if (trashCansChecked >= 20 && rng.nextDouble() < 0.01) {
            List<Item> megaPool = buildMegaSuccessPool(player, rng);
            if (!megaPool.isEmpty()) {
                Item item = megaPool.get(rng.nextInt(megaPool.size()));
                return new Result(new ItemStack(item), true, false);
            }
        }

        // Fallback: 需要通过 baseChance
        if (!baseChancePassed) return null;

        List<Item> fallbackPool = buildFallbackPool(player, rng);
        if (!fallbackPool.isEmpty()) {
            Item item = fallbackPool.get(rng.nextInt(fallbackPool.size()));
            return new Result(new ItemStack(item), false, false);
        }

        return null;
    }

    // ==================== 物品池 ====================

    /**
     * AfterAll MegaSuccess 随机池。
     * <p>原版: Green Algae, Bread, Field Snack, Acorn, Maple Seed, Pine Cone, RANDOM_BASE_SEASON_ITEM
     * <p>Field Snack 不存在于 mod，跳过
     */
    private static List<Item> buildMegaSuccessPool(ServerPlayer player, Random rng) {
        List<Item> pool = new ArrayList<>();
        pool.add(ModItems.GREEN_ALGAE.get());
        pool.add(ModItems.COOKING_DISHES.get("bread").get());
        // Field Snack — 不存在于 mod，跳过
        pool.add(ModItems.ACORN.get());
        pool.add(ModItems.MAPLE_SEED.get());
        pool.add(ModItems.PINE_CONE.get());
        // RANDOM_BASE_SEASON_ITEM: 用当前季节的物品替代
        Item seasonItem = getRandomItemFromSeason(player, rng);
        if (seasonItem != null) {
            pool.add(seasonItem);
        }
        return pool;
    }

    /**
     * AfterAll Fallback 随机池。
     * <p>原版通用: Green Algae, Bread, Acorn, Maple Seed, Pine Cone,
     *          RANDOM_BASE_SEASON_ITEM, Trash, Joja Cola, Broken Glasses, Broken CD, Soggy Newspaper
     */
    private static List<Item> buildFallbackPool(ServerPlayer player, Random rng) {
        List<Item> pool = new ArrayList<>();
        // 通用好物品
        pool.add(ModItems.GREEN_ALGAE.get());
        pool.add(ModItems.COOKING_DISHES.get("bread").get());
        pool.add(ModItems.ACORN.get());
        pool.add(ModItems.MAPLE_SEED.get());
        pool.add(ModItems.PINE_CONE.get());
        // RANDOM_BASE_SEASON_ITEM
        Item seasonItem = getRandomItemFromSeason(player, rng);
        if (seasonItem != null) {
            pool.add(seasonItem);
        }
        // 垃圾（保留，但占比降低了）
        pool.add(ModItems.TRASH.get());
        pool.add(ModItems.JOJA_COLA.get());
        pool.add(ModItems.BROKEN_GLASSES.get());
        pool.add(ModItems.BROKEN_CD.get());
        pool.add(ModItems.SOGGY_NEWSPAPER.get());
        return pool;
    }

    // ==================== RANDOM_BASE_SEASON_ITEM ====================

    /**
     * 对齐 Utility.getRandomItemFromSeason()。
     * <p>
     * 基础池 + 矿洞深度解锁 + 沙漠解锁 + 熔炉解锁 + 季节鱼/作物。
     * 不存在于 mod 的物品已跳过（标注 SDV 物品ID）。
     */
    @Nullable
    private static Item getRandomItemFromSeason(ServerPlayer player, Random rng) {
        List<Item> possibleItems = new ArrayList<>();

        // ---- 基础池 (SDV: 68,66,78,80,86,152,167,153,420) ----
        possibleItems.add(ModItems.TOPAZ.get());           // 68
        possibleItems.add(ModItems.AMETHYST.get());        // 66
        possibleItems.add(vanillaItem("cave_carrot"));   // 78
        possibleItems.add(ModItems.QUARTZ.get());          // 80
        possibleItems.add(ModItems.EARTH_CRYSTAL.get());   // 86
        possibleItems.add(ModItems.SEAWEED.get());         // 152
        possibleItems.add(ModItems.JOJA_COLA.get());       // 167
        possibleItems.add(ModItems.GREEN_ALGAE.get());     // 153
        possibleItems.add(ModItems.RED_MUSHROOM.get());    // 420

        // ---- 矿洞深度 > 40 (SDV: 62,70,72,84,422) ----
        MiningPlayerData miningData = MiningDataManager.getPlayerData(player);
        int maxMineFloor = miningData != null ? miningData.getMaxFloorReached() : 0;

        if (maxMineFloor > 40) {
            possibleItems.add(ModItems.AQUAMARINE.get());      // 62
            possibleItems.add(ModItems.JADE.get());            // 70
            possibleItems.add(ModItems.DIAMOND.get());         // 72
            possibleItems.add(ModItems.FROZEN_TEAR.get());     // 84
            possibleItems.add(ModItems.PURPLE_MUSHROOM.get()); // 422
        }

        // ---- 矿洞深度 > 80 (SDV: 64,60,82) ----
        if (maxMineFloor > 80) {
            possibleItems.add(ModItems.RUBY.get());        // 64
            possibleItems.add(ModItems.EMERALD.get());     // 60
            possibleItems.add(ModItems.FIRE_QUARTZ.get()); // 82
        }

        // ---- 沙漠解锁 ccVault (SDV: 88,90,164,165) ----
        PlayerStardewData pData = PlayerDataManager.getPlayerData(player);
        if (pData != null && pData.hasMailFlag("ccVault")) {
            possibleItems.add(vanillaItem("coconut"));        // 88
            possibleItems.add(vanillaItem("cactus_fruit"));   // 90
            possibleItems.add(ModItems.SANDFISH.get());        // 164
            possibleItems.add(ModItems.SCORPION_CARP.get());   // 165
        }

        // ---- 熔炉解锁 (SDV: 334,335,336,338) ----
        if (pData != null && pData.isRecipeUnlocked("furnace")) {
            possibleItems.add(ModItems.COPPER_BAR.get());      // 334
            possibleItems.add(ModItems.IRON_BAR.get());        // 335
            possibleItems.add(ModItems.GOLD_BAR.get());        // 336
            possibleItems.add(ModItems.REFINED_QUARTZ.get());  // 338
        }
        // Quartz Globe (339) — 不存在于 mod

        // ---- 季节物品 ----
        int season = StardewTimeManager.get().getCurrentSeason();
        switch (season) {
            case 0 -> { // Spring
                possibleItems.add(vanillaItem("wild_horseradish")); // 16
                possibleItems.add(vanillaItem("daffodil"));         // 18
                possibleItems.add(vanillaItem("leek"));             // 20
                possibleItems.add(vanillaItem("dandelion"));        // 22
                possibleItems.add(ModItems.ANCHOVY.get());         // 129
                possibleItems.add(ModItems.SARDINE.get());         // 131
                possibleItems.add(ModItems.BREAM.get());           // 132
                possibleItems.add(ModItems.LARGEMOUTH_BASS.get()); // 136
                possibleItems.add(ModItems.SMALLMOUTH_BASS.get()); // 137
                possibleItems.add(ModItems.CARP.get());            // 142
                possibleItems.add(ModItems.CATFISH.get());         // 143
                possibleItems.add(ModItems.SUNFISH.get());         // 145
                possibleItems.add(ModItems.HERRING.get());         // 147
                possibleItems.add(ModItems.EEL.get());             // 148
                possibleItems.add(ModItems.SEAWEED.get());         // 152
                possibleItems.add(ModItems.JOJA_COLA.get());       // 167
                possibleItems.add(ModItems.FLOUNDER.get());        // 267
            }
            case 1 -> { // Summer
                possibleItems.add(ModItems.PUFFERFISH.get());      // 128
                possibleItems.add(ModItems.TUNA.get());            // 130
                possibleItems.add(ModItems.BREAM.get());           // 132
                possibleItems.add(ModItems.LARGEMOUTH_BASS.get()); // 136
                possibleItems.add(ModItems.RAINBOW_TROUT.get());   // 138
                possibleItems.add(ModItems.CARP.get());            // 142
                possibleItems.add(ModItems.PIKE.get());            // 144
                possibleItems.add(ModItems.SUNFISH.get());         // 145
                possibleItems.add(ModItems.RED_SNAPPER.get());     // 146
                possibleItems.add(ModItems.OCTOPUS.get());         // 149
                possibleItems.add(ModItems.RED_MULLET.get());      // 150
                possibleItems.add(ModItems.SUPER_CUCUMBER.get());  // 155
                possibleItems.add(vanillaItem("spice_berry"));     // 396
                possibleItems.add(ModItems.GRAPE.get());           // 398
                possibleItems.add(vanillaItem("sweet_pea"));       // 402
                possibleItems.add(ModItems.FLOUNDER.get());        // 267
            }
            case 2 -> { // Fall
                possibleItems.add(ModItems.COMMON_MUSHROOM.get()); // 404
                possibleItems.add(vanillaItem("wild_plum"));       // 406
                possibleItems.add(vanillaItem("hazelnut"));        // 408
                possibleItems.add(vanillaItem("blackberry"));      // 410
                possibleItems.add(ModItems.ANCHOVY.get());         // 129
                possibleItems.add(ModItems.SARDINE.get());         // 131
                possibleItems.add(ModItems.BREAM.get());           // 132
                possibleItems.add(ModItems.LARGEMOUTH_BASS.get()); // 136
                possibleItems.add(ModItems.SMALLMOUTH_BASS.get()); // 137
                possibleItems.add(ModItems.SALMON.get());          // 139
                possibleItems.add(ModItems.WALLEYE.get());         // 140
                possibleItems.add(ModItems.CARP.get());            // 142
                possibleItems.add(ModItems.CATFISH.get());         // 143
                possibleItems.add(ModItems.EEL.get());             // 148
                possibleItems.add(ModItems.RED_MULLET.get());      // 150
                possibleItems.add(ModItems.SEA_CUCUMBER.get());    // 154
                possibleItems.add(ModItems.SUPER_CUCUMBER.get());  // 155
                possibleItems.add(ModItems.MIDNIGHT_CARP.get());   // 269
                possibleItems.add(ModItems.GRAPE.get());           // SDV Fall crop
            }
            case 3 -> { // Winter
                possibleItems.add(vanillaItem("winter_root"));     // 412
                possibleItems.add(vanillaItem("crystal_fruit"));   // 414
                possibleItems.add(vanillaItem("snow_yam"));        // 416
                possibleItems.add(vanillaItem("crocus"));          // 418
                possibleItems.add(ModItems.TUNA.get());            // 130
                possibleItems.add(ModItems.SARDINE.get());         // 131
                possibleItems.add(ModItems.BREAM.get());           // 132
                possibleItems.add(ModItems.LARGEMOUTH_BASS.get()); // 136
                possibleItems.add(ModItems.WALLEYE.get());         // 140
                possibleItems.add(ModItems.PERCH.get());           // 141
                possibleItems.add(ModItems.PIKE.get());            // 144
                possibleItems.add(ModItems.RED_SNAPPER.get());     // 146
                possibleItems.add(ModItems.HERRING.get());         // 147
                possibleItems.add(ModItems.RED_MULLET.get());      // 150
                possibleItems.add(ModItems.SQUID.get());           // 151
                possibleItems.add(ModItems.SEA_CUCUMBER.get());    // 154
                possibleItems.add(ModItems.MIDNIGHT_CARP.get());   // 269
            }
        }

        if (possibleItems.isEmpty()) return null;
        return possibleItems.get(rng.nextInt(possibleItems.size()));
    }

    // ==================== Helpers ====================

    /**
     * 从 VanillaCategoryItems 获取物品。
     */
    private static Item vanillaItem(String id) {
        return ModItems.VANILLA_CATEGORY_ITEMS.get(id).get();
    }

    // ==================== RNG ====================

    /**
     * 创建确定性垃圾桶 RNG。对齐原版 CreateDaySaveRandom(777 + hash(id))。
     */
    private static Random createGarbageRandom(String canId, long daySeed) {
        long seed = daySeed + 777L + deterministicHash(canId);
        Random rng = new Random(seed);
        // 原版 prewarm: 两轮随机预热
        int prewarm = rng.nextInt(100);
        for (int i = 0; i < prewarm; i++) rng.nextDouble();
        prewarm = rng.nextInt(100);
        for (int i = 0; i < prewarm; i++) rng.nextDouble();
        return rng;
    }

    private static Random createSyncedDayRandom(long daySeed, String key) {
        return new Random(daySeed ^ deterministicHash(key));
    }

    /**
     * 确定性字符串哈希（对齐 .NET GetDeterministicHashCode）。
     */
    private static int deterministicHash(String s) {
        int hash1 = 5381;
        int hash2 = hash1;
        for (int i = 0; i < s.length(); i += 2) {
            hash1 = ((hash1 << 5) + hash1) ^ s.charAt(i);
            if (i + 1 < s.length()) {
                hash2 = ((hash2 << 5) + hash2) ^ s.charAt(i + 1);
            }
        }
        return hash1 + (hash2 * 1566083941);
    }
}
