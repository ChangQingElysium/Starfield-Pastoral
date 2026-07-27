package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmCaveDailyHandlers;
import com.stardew.craft.api.v1.internal.farm.StardewFarmCaveDailyRegistry;
import com.stardew.craft.api.v1.internal.farm.StardewFarmSnapshots;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.MushroomBoxBlockEntity;
import com.stardew.craft.farm.FarmCaveChoice;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.interior.PlayerInteriorAllocator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;
import java.util.UUID;

/**
 * 农场洞穴每日结算：
 * <ul>
 *   <li>{@link FarmCaveChoice#FRUIT_BATS}：清除旧的水果 forage → while rng&lt;0.66 在洞内随机 tile 生成水果 forage。</li>
 *   <li>{@link FarmCaveChoice#MUSHROOMS}：6 个蘑菇盆按 SDV {@code (BC)128} 概率滚动产菇。</li>
 * </ul>
 *
 * <p>仅处理有在线成员的农场洞穴（owner 或 member 任一在线即可）。
 */
public final class FarmCaveDailyService {

    private FarmCaveDailyService() {}

    /** Fruit bats 水果池（对齐 SDV FarmCave.DayUpdate 的 5 分支） */
    private record FruitEntry(DeferredBlock<Block> block) {}

    // 5 个候选水果 forage 方块；最后一个是「苹果 10% + 其他 5 种随机」的合并条目（特殊处理）
    private static final FruitEntry FRUIT_SALMONBERRY = new FruitEntry(ModBlocks.FORAGE_SALMONBERRY);
    private static final FruitEntry FRUIT_SPICE_BERRY = new FruitEntry(ModBlocks.FORAGE_SPICE_BERRY);
    private static final FruitEntry FRUIT_WILD_PLUM = new FruitEntry(ModBlocks.FORAGE_WILD_PLUM);
    private static final FruitEntry FRUIT_BLACKBERRY = new FruitEntry(ModBlocks.FORAGE_BLACKBERRY);

    private static final List<FruitEntry> FRUITS_CASE4 = List.of(
            new FruitEntry(ModBlocks.FORAGE_APRICOT),       // 634
            new FruitEntry(ModBlocks.FORAGE_ORANGE),        // 635
            new FruitEntry(ModBlocks.FORAGE_PEACH),         // 636
            new FruitEntry(ModBlocks.FORAGE_POMEGRANATE),   // 637
            new FruitEntry(ModBlocks.FORAGE_MANGO)          // 638
    );
    private static final FruitEntry FRUIT_APPLE = new FruitEntry(ModBlocks.FORAGE_APPLE); // 613, 10% inside case 4

    /** 蘑菇盆 6 个 tile（schem local → 洞穴 origin 的偏移） */
    public static final List<BlockPos> MUSHROOM_BOX_OFFSETS = List.of(
            new BlockPos(3, 1, 3), new BlockPos(3, 1, 5), new BlockPos(3, 1, 7),
            new BlockPos(5, 1, 3), new BlockPos(5, 1, 5), new BlockPos(5, 1, 7)
    );

    // 蘑菇产出 item id（对齐 SDV Content/Data/Machines.json (BC)128）
    private static final ResourceLocation PURPLE_MUSHROOM = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "purple_mushroom");
    private static final ResourceLocation CHANTERELLE     = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "chanterelle");
    private static final ResourceLocation MOREL           = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "morel");
    private static final ResourceLocation RED_MUSHROOM    = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "red_mushroom");
    private static final ResourceLocation COMMON_MUSHROOM = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "common_mushroom");

    // ── 入口 ──

    public static void onNewDay(ServerLevel level) {
        FarmInstanceRegistry reg = FarmInstanceRegistry.get();
        PlayerInteriorAllocator alloc = PlayerInteriorAllocator.get(level);
        RandomSource rng = level.getRandom();
        java.util.Set<UUID> processedOwners = new java.util.HashSet<>();

        int fruitCount = 0;
        int mushroomCount = 0;

        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
            UUID ownerUUID = reg.getOwnerForPlayer(sp.getUUID());
            if (ownerUUID == null || !processedOwners.add(ownerUUID)) continue;

            FarmInstance farm = reg.getFarm(ownerUUID);
            if (farm == null) continue;
            if (!alloc.isCavePlaced(ownerUUID)) continue;

            FarmCaveChoice choice = farm.getCaveChoice();
            BlockPos caveOrigin = alloc.getCaveOrigin(ownerUUID);
            StardewFarmCaveDailyHandlers.Context addonContext =
                    new StardewFarmCaveDailyHandlers.Context(
                            level, StardewFarmSnapshots.from(farm), caveOrigin, rng);
            if (StardewFarmCaveDailyRegistry.runHandlers(addonContext)
                    == StardewFarmCaveDailyHandlers.Result.SKIP_DEFAULT) {
                continue;
            }
            if (choice == FarmCaveChoice.NONE) continue;

            if (choice == FarmCaveChoice.FRUIT_BATS) {
                fruitCount += processFruitBats(level, farm, caveOrigin, rng);
            } else if (choice == FarmCaveChoice.MUSHROOMS) {
                mushroomCount += processMushrooms(level, caveOrigin, rng);
            }
        }

        if (fruitCount > 0 || mushroomCount > 0) {
            StardewCraft.LOGGER.info("[FARM-CAVE] Daily result: fruits={}, mushrooms={}", fruitCount, mushroomCount);
        }
    }

    // ── Fruit Bats ──

    private static int processFruitBats(
            ServerLevel level,
            FarmInstance farm,
            BlockPos caveOrigin,
            RandomSource rng
    ) {
        // SDV FarmCave.DayUpdate: 不清旧水果，直接累积 → 玩家拾取前一直存在。
        int placed = 0;
        while (rng.nextDouble() < 0.66D) {
            // SDV: x ∈ Next(1, W-1)  → W=9 → [1,7] (7 values)
            //      z ∈ Next(1, H-4)  → H=10 → [1,5] (5 values, exclusive upper)
            int lx = 1 + rng.nextInt(InteriorSubspaceManager.FARM_CAVE_SCHEM_W - 2);
            int lz = 1 + rng.nextInt(InteriorSubspaceManager.FARM_CAVE_SCHEM_L - 5);
            BlockPos place = caveOrigin.offset(lx, 1, lz);
            BlockPos below = place.below();

            if (!level.getBlockState(place).isAir()) continue;
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;

            Block block = StardewFarmCaveDailyRegistry.resolveFruit(
                    new StardewFarmCaveDailyHandlers.FruitContext(
                            level,
                            StardewFarmSnapshots.from(farm),
                            caveOrigin,
                            rng,
                            pickFruitBlock(rng)
                    ));
            level.setBlock(place, block.defaultBlockState(), Block.UPDATE_ALL);
            placed++;
        }
        return placed;
    }

    private static Block pickFruitBlock(RandomSource rng) {
        int branch = rng.nextInt(5);
        return switch (branch) {
            case 0 -> FRUIT_SALMONBERRY.block().get();
            case 1 -> FRUIT_SPICE_BERRY.block().get();
            case 2 -> FRUIT_WILD_PLUM.block().get();
            case 3 -> FRUIT_BLACKBERRY.block().get();
            default -> {
                // case 4: 10% 苹果 else 5 选 1（apricot/orange/peach/pomegranate/mango）
                if (rng.nextDouble() < 0.10D) {
                    yield FRUIT_APPLE.block().get();
                } else {
                    yield FRUITS_CASE4.get(rng.nextInt(FRUITS_CASE4.size())).block().get();
                }
            }
        };
    }

    // ── Mushrooms ──

    private static int processMushrooms(ServerLevel level, BlockPos caveOrigin, RandomSource rng) {
        int produced = 0;
        for (BlockPos off : MUSHROOM_BOX_OFFSETS) {
            BlockPos p = caveOrigin.offset(off);
            if (!(level.getBlockEntity(p) instanceof MushroomBoxBlockEntity box)) continue;
            if (box.isReady()) continue;

            ResourceLocation product = rollMushroom(rng);
            box.setProductIfEmpty(product);
            produced++;
        }
        return produced;
    }

    /**
     * 对齐 SDV {@code (BC)128} MachineData OutputRules：
     * <pre>
     *  RANDOM 0.025 -> Purple
     *  RANDOM 0.075 -> Chanterelle
     *  RANDOM 0.09  -> Morel
     *  RANDOM 0.15  -> Red
     *  default      -> Common
     * </pre>
     */
    private static ResourceLocation rollMushroom(RandomSource rng) {
        double r = rng.nextDouble();
        if (r < 0.025D) return PURPLE_MUSHROOM;
        if (r < 0.025D + 0.075D) return CHANTERELLE;       // 0.100
        if (r < 0.025D + 0.075D + 0.090D) return MOREL;    // 0.190
        if (r < 0.025D + 0.075D + 0.090D + 0.150D) return RED_MUSHROOM; // 0.340
        return COMMON_MUSHROOM;
    }
}
