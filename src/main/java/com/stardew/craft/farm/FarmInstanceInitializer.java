package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.tree.WildTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家个人农场实例的初始化器。
 * 放置 schematic → 设置生物群系 → 放置图腾柱 → 温室 → 概率化碎片 → 出口实体。
 * <p>
 * 碎片生成规则（概率制，per-block）：
 *   黄土方块 (YELLOW_DIRT) 上: 树木 / 石头 / 杂草 / 牧草 / 树苗
 *   草方块 (GRASS_BLOCK) 上: 树木 / 牧草 / 杂草（无石头、无树苗）
 *   森林农场额外: 桃花心木树（草方块上，极低概率）
 */
@SuppressWarnings("null")
public class FarmInstanceInitializer {

    // ── 概率配置（千分比） ──
    private static final int TREE_PROB = 19;     // 1.9%
    private static final int STONE_PROB = 86;    // 8.6%（仅黄土）
    private static final int WEED_PROB = 95;     // 9.5%
    private static final int GRASS_PROB = 75;    // 7.5%
    private static final int SAPLING_PROB = 4;   // 0.4%（仅黄土）
    private static final int MAHOGANY_PROB = 3;  // 0.3%（仅森林农场，草方块）

    private static final int CLEAR_RADIUS = 5;
    private static final int TREE_MIN_DIST = 4;
    private static final int SCAN_Y_MAX = 100;
    private static final int SCAN_Y_MIN = -64;

    /**
     * 初始化指定玩家的农场实例。
     */
    public static boolean initializeFarm(ServerLevel level, FarmInstance farm) {
        if (farm.isInitialized()) {
            StardewCraft.LOGGER.warn("[FARM_INIT] Farm for {} already initialized", farm.getOwnerName());
            return true;
        }

        FarmType.FarmLayout layout = farm.getFarmType().getLayout();
        if (layout == null) {
            StardewCraft.LOGGER.error("[FARM_INIT] No layout data for farm type {}", farm.getFarmType().getId());
            return false;
        }

        BlockPos origin = farm.getOrigin();
        StardewCraft.LOGGER.info("[FARM_INIT] Initializing {} farm for {} at origin {}",
                farm.getFarmType().getId(), farm.getOwnerName(), origin);

        // 1. 预加载区块
        preloadFarmChunks(level, farm);

        // 2. 放置 schematic（地形）
        placeSchematic(level, farm);

        // 2.5 在 schematic 底面正下方铺一层基岩，防止掉出世界
        placeBedrockFloor(level, farm, layout);

        // 3. 设置生物群系（非 default 的农场类型）
        if (layout.biomeId() != null) {
            setFarmBiome(level, farm, layout.biomeId());
        }

        // 4. 放置农场图腾柱（朝西）
        placeFarmTotemPole(level, farm);

        // 5. 放置温室（门口朝西，CW90 旋转）
        com.stardew.craft.greenhouse.GreenhouseManager.get(level).ensurePlacedForPlayer(level, farm.getOwnerUUID());

        // 6. 概率化生成自然碎片
        spawnNaturalDebris(level, farm);

        // 7. 放置 3 个出口交互实体
        spawnExitPortals(level, farm, layout);

        // 7.5 放置农场洞穴系统（室外墙 + 室外传送方块 + 室内结构）
        placeFarmCaveSystem(level, farm, layout);

        // 8. 河边农场特殊：送熏鱼机
        if (farm.getFarmType() == FarmType.RIVERLAND) {
            giveStarterItem(level, farm, ModBlocks.FISH_SMOKER.get().asItem());
        }

        farm.markInitialized();
        FarmInstanceRegistry.get().setDirty();
        StardewCraft.LOGGER.info("[FARM_INIT] Farm initialization complete for {}", farm.getOwnerName());
        return true;
    }

    // ══════════════════════════════════════════
    //  Schematic 放置
    // ══════════════════════════════════════════

    private static void placeSchematic(ServerLevel level, FarmInstance farm) {
        String path = farm.getFarmType().getSchematicPath();
        BlockPos origin = farm.getOrigin();
        boolean result = com.stardew.craft.mining.StructureLoader.loadAndPlaceWithResult(level, path, origin);
        if (!result) {
            StardewCraft.LOGGER.error("[FARM_INIT] Failed to place schematic {} at {}", path, origin);
        } else {
            StardewCraft.LOGGER.info("[FARM_INIT] Placed schematic {} at {}", path, origin);
        }
    }

    /**
     * 在农场 schematic 底面正下方铺一整层基岩，防止玩家掉出世界。
     * Y = origin.getY() - 1，覆盖 schemWidth × schemLength 的完整区域。
     */
    private static void placeBedrockFloor(ServerLevel level, FarmInstance farm, FarmType.FarmLayout layout) {
        BlockPos origin = farm.getOrigin();
        int bedrockY = origin.getY() - 1;
        int startX = origin.getX();
        int startZ = origin.getZ();
        int endX = startX + layout.schemWidth();
        int endZ = startZ + layout.schemLength();
        net.minecraft.world.level.block.state.BlockState bedrock = net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();

        for (int x = startX; x < endX; x++) {
            for (int z = startZ; z < endZ; z++) {
                level.setBlock(new BlockPos(x, bedrockY, z), bedrock, 2);
            }
        }
        StardewCraft.LOGGER.info("[FARM_INIT] Bedrock floor placed at Y={} ({} x {} blocks)",
                bedrockY, layout.schemWidth(), layout.schemLength());
    }

    // ══════════════════════════════════════════
    //  生物群系设置
    // ══════════════════════════════════════════

    private static void setFarmBiome(ServerLevel level, FarmInstance farm, String biomeId) {
        ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, biomeId));
        Holder<Biome> biomeHolder;
        try {
            biomeHolder = level.registryAccess()
                    .registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(biomeKey);
        } catch (Exception e) {
            StardewCraft.LOGGER.error("[FARM_INIT] Biome {} not found", biomeId);
            return;
        }

        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        int minCX = min.getX() >> 4, maxCX = max.getX() >> 4;
        int minCZ = min.getZ() >> 4, maxCZ = max.getZ() >> 4;

        int modified = 0;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                boolean changed = false;
                for (int si = 0; si < chunk.getSections().length; si++) {
                    LevelChunkSection section = chunk.getSections()[si];
                    if (section == null) continue;
                    @SuppressWarnings("unchecked")
                    net.minecraft.world.level.chunk.PalettedContainer<Holder<Biome>> biomes =
                            (net.minecraft.world.level.chunk.PalettedContainer<Holder<Biome>>)
                                    (Object) section.getBiomes();
                    for (int bx = 0; bx < 4; bx++) {
                        for (int by = 0; by < 4; by++) {
                            for (int bz = 0; bz < 4; bz++) {
                                biomes.set(bx, by, bz, biomeHolder);
                                changed = true;
                            }
                        }
                    }
                }
                if (changed) {
                    chunk.setUnsaved(true);
                    modified++;
                }
            }
        }
        StardewCraft.LOGGER.info("[FARM_INIT] Set biome {} for {} chunks", biomeId, modified);
    }

    // ══════════════════════════════════════════
    //  自然碎片
    // ══════════════════════════════════════════

    private static void spawnNaturalDebris(ServerLevel level, FarmInstance farm) {
        RandomSource random = level.getRandom();
        BlockPos boundsMin = farm.getFarmBoundsMin();
        BlockPos boundsMax = farm.getFarmBoundsMax();
        BlockPos spawnPos = farm.getSpawnPoint();
        BlockPos greenhousePos = farm.getGreenhousePos();
        boolean isForest = farm.getFarmType() == FarmType.FOREST;

        Block yellowDirt = ModBlocks.YELLOW_DIRT.get();
        Block grassBlock = Blocks.GRASS_BLOCK;
        int season = StardewTimeManager.get().getCurrentSeason();

        List<BlockPos> treeTrunks = new ArrayList<>();
        int trees = 0, stones = 0, weeds = 0, grass = 0, saplings = 0, mahogany = 0;

        for (int x = boundsMin.getX(); x <= boundsMax.getX(); x++) {
            for (int z = boundsMin.getZ(); z <= boundsMax.getZ(); z++) {
                if (isNearProtected(x, z, spawnPos, greenhousePos)) continue;

                // 从上往下扫描找表面
                BlockPos placePos = null;
                Block surfaceBlock = null;
                for (int y = SCAN_Y_MAX; y >= SCAN_Y_MIN; y--) {
                    BlockPos groundPos = new BlockPos(x, y, z);
                    BlockState groundState = level.getBlockState(groundPos);
                    if (groundState.isAir()) continue;
                    Block ground = groundState.getBlock();
                    if (ground == yellowDirt || ground == grassBlock) {
                        BlockPos above = groundPos.above();
                        if (level.getBlockState(above).isAir() && level.canSeeSky(above)) {
                            placePos = above;
                            surfaceBlock = ground;
                        }
                    }
                    break;
                }
                if (placePos == null) continue;

                boolean onGrass = (surfaceBlock == grassBlock);
                int roll = random.nextInt(1000);
                int cumulative = 0;

                // ── 树木（黄土+草方块都可生成） ──
                cumulative += TREE_PROB;
                if (roll < cumulative) {
                    if (!tooCloseToAny(placePos, treeTrunks, TREE_MIN_DIST)) {
                        WildTrees.Def[] treeDefs = {WildTrees.OAK, WildTrees.MAPLE, WildTrees.PINE};
                        int[] weights = {3, 3, 4};
                        WildTrees.Def chosen = pickWeighted(random, treeDefs, weights);
                        if (com.stardew.craft.tree.prefab.PrefabTreeManager.tryPlaceRandomVariant(level, placePos, chosen)) {
                            treeTrunks.add(placePos);
                            trees++;
                        }
                    }
                    continue;
                }

                // ── 森林农场桃花心木（仅草方块，极低概率） ──
                if (isForest && onGrass) {
                    cumulative += MAHOGANY_PROB;
                    if (roll < cumulative) {
                        if (!tooCloseToAny(placePos, treeTrunks, TREE_MIN_DIST)) {
                            if (com.stardew.craft.tree.prefab.PrefabTreeManager.tryPlaceRandomVariant(level, placePos, WildTrees.MAHOGANY)) {
                                treeTrunks.add(placePos);
                                mahogany++;
                            }
                        }
                        continue;
                    }
                }

                // ── 石头（仅黄土） ──
                if (!onGrass) {
                    cumulative += STONE_PROB;
                    if (roll < cumulative) {
                        Block[] stoneBlocks = {ModBlocks.EARTH_SHALE.get(), ModBlocks.MOSSY_SANDSTONE.get()};
                        level.setBlock(placePos, stoneBlocks[random.nextInt(stoneBlocks.length)].defaultBlockState(), 3);
                        stones++;
                        continue;
                    }
                }

                // ── 杂草（黄土+草方块） ──
                cumulative += WEED_PROB;
                if (roll < cumulative) {
                    int variant = random.nextInt(3);
                    BlockState state = ModBlocks.WILD_WEEDS.get().defaultBlockState()
                            .setValue(WildWeedsBlock.SEASON, Math.max(0, Math.min(3, season)))
                            .setValue(WildWeedsBlock.VARIANT, variant);
                    level.setBlock(placePos, state, 3);
                    weeds++;
                    continue;
                }

                // ── 牧草（黄土+草方块） ──
                cumulative += GRASS_PROB;
                if (roll < cumulative) {
                    BlockState state = ModBlocks.PASTURE_GRASS.get().defaultBlockState()
                            .setValue(PastureGrassBlock.VARIANT, random.nextInt(3));
                    level.setBlock(placePos, state, 3);
                    grass++;
                    continue;
                }

                // ── 树苗（仅黄土） ──
                if (!onGrass) {
                    cumulative += SAPLING_PROB;
                    if (roll < cumulative) {
                        WildTrees.Def[] treeDefs = {WildTrees.OAK, WildTrees.MAPLE, WildTrees.PINE};
                        WildTrees.Def def = treeDefs[random.nextInt(treeDefs.length)];
                        Block sapling = random.nextBoolean() ? def.sapling0().get() : def.sapling1().get();
                        level.setBlock(placePos, sapling.defaultBlockState(), 3);
                        saplings++;
                    }
                }
            }
        }

        StardewCraft.LOGGER.info("[FARM_INIT] Debris: trees={}, mahogany={}, stones={}, weeds={}, grass={}, saplings={}",
                trees, mahogany, stones, weeds, grass, saplings);
    }

    private static boolean isNearProtected(int x, int z, BlockPos spawn, BlockPos greenhouse) {
        if (Math.abs(x - spawn.getX()) <= CLEAR_RADIUS && Math.abs(z - spawn.getZ()) <= CLEAR_RADIUS) return true;
        if (x >= greenhouse.getX() - 2 && x <= greenhouse.getX() + 19
                && z >= greenhouse.getZ() - 2 && z <= greenhouse.getZ() + 19) return true;
        return false;
    }

    private static boolean tooCloseToAny(BlockPos pos, List<BlockPos> existing, int minDist) {
        for (BlockPos e : existing) {
            if (Math.abs(pos.getX() - e.getX()) <= minDist && Math.abs(pos.getZ() - e.getZ()) <= minDist)
                return true;
        }
        return false;
    }

    private static <T> T pickWeighted(RandomSource random, T[] items, int[] weights) {
        int total = 0;
        for (int w : weights) total += w;
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < items.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return items[i];
        }
        return items[items.length - 1];
    }

    // ══════════════════════════════════════════
    //  图腾柱（朝西）
    // ══════════════════════════════════════════

    private static void placeFarmTotemPole(ServerLevel level, FarmInstance farm) {
        BlockPos totemPos = farm.getFarmTotemPos();
        level.getChunk(totemPos.getX() >> 4, totemPos.getZ() >> 4);

        Block block = com.stardew.craft.block.ModBlocks.TOTEM_POLE_FARM.get();
        BlockState mainState = block.defaultBlockState()
                .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                .setValue(MapDecorStaticBlock.FACING, Direction.WEST)
                .setValue(com.stardew.craft.block.utility.totem.TotemPoleBlock.ACTIVATED, true);
        level.setBlock(totemPos, mainState, 3);
        block.setPlacedBy(level, totemPos, mainState, null, net.minecraft.world.item.ItemStack.EMPTY);

        if (level.getBlockEntity(totemPos) instanceof com.stardew.craft.blockentity.TotemPoleBlockEntity pole) {
            String poleName = farm.getFarmName();
            com.stardew.craft.totem.TotemPoleTracker tracker = com.stardew.craft.totem.TotemPoleTracker.get(level);
            int poleId = tracker.allocateId();
            tracker.register(poleId, new com.stardew.craft.totem.TotemPoleTracker.PoleEntry(
                    totemPos, poleName, com.stardew.craft.block.utility.totem.TotemType.FARM, false));
            pole.initSystemPole(level, poleId, poleName);
        }
    }

    // ══════════════════════════════════════════
    //  出口交互实体
    // ══════════════════════════════════════════

    private static void spawnExitPortals(ServerLevel level, FarmInstance farm, FarmType.FarmLayout layout) {
        BlockPos origin = farm.getOrigin();

        spawnExitEntityRegion(level, origin, layout.entrySouth(),
                "sdv_portal_target:farm_exit_south", "sdv_portal_marker:farm_exit");
        spawnExitEntityRegion(level, origin, layout.entryEast(),
                "sdv_portal_target:farm_exit_east", "sdv_portal_marker:farm_exit");
        spawnExitEntityRegion(level, origin, layout.entryWest(),
                "sdv_portal_target:farm_exit_west", "sdv_portal_marker:farm_exit");

        StardewCraft.LOGGER.info("[FARM_INIT] Spawned exit portals for farm of {}", farm.getOwnerName());
    }

    private static void spawnExitEntityRegion(ServerLevel level, BlockPos origin,
                                               FarmType.EntryData entry,
                                               String targetTag, String markerTag) {
        BlockPos min = origin.offset(entry.exitMin());
        BlockPos max = origin.offset(entry.exitMax());

        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int maxY = Math.max(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());

        // 提取 targetId（去除前缀）
        String targetId = targetTag;
        if (targetTag.startsWith("sdv_portal_target:")) {
            targetId = targetTag.substring("sdv_portal_target:".length());
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    level.setBlock(pos, com.stardew.craft.block.ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(),
                            net.minecraft.world.level.block.Block.UPDATE_ALL);
                    if (level.getBlockEntity(pos) instanceof com.stardew.craft.blockentity.PortalTriggerBlockEntity be) {
                        be.configure(targetId, markerTag);
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════
    //  农场洞穴系统（外墙 + 传送方块 + 室内）
    // ══════════════════════════════════════════

    /**
     * 老存档兼容：若某农场未放置过洞穴系统（cavePlaced=false），则首次进服时补放。
     * 仅由 {@link com.stardew.craft.player.PlayerDataEventHandler} 在玩家登录并完成离线追赶后调用。
     *
     * @return true 表示本次执行了补放
     */
    public static boolean backfillFarmCaveIfMissing(ServerLevel level, FarmInstance farm) {
        if (farm == null || !farm.isInitialized()) return false;
        FarmType.FarmLayout layout = farm.getFarmType().getLayout();
        if (layout == null) return false;
        com.stardew.craft.interior.PlayerInteriorAllocator alloc =
                com.stardew.craft.interior.PlayerInteriorAllocator.get(level);
        if (alloc.isCavePlaced(farm.getOwnerUUID())) return false;

        StardewCraft.LOGGER.info("[FARM_INIT] Backfilling farm cave for owner={} ({})",
                farm.getOwnerUUID(), farm.getOwnerName());
        placeFarmCaveSystem(level, farm, layout);

        // 若 owner 已选择过 MUSHROOMS（老存档 choice 可能已在 Step 1 存下），补放蘑菇盆
        if (farm.getCaveChoice() == FarmCaveChoice.MUSHROOMS) {
            BlockPos caveOrigin = alloc.getCaveOrigin(farm.getOwnerUUID());
            net.minecraft.world.level.block.Block box = com.stardew.craft.block.ModBlocks.MUSHROOM_BOX.get();
            for (BlockPos off : com.stardew.craft.manager.FarmCaveDailyService.MUSHROOM_BOX_OFFSETS) {
                BlockPos p = caveOrigin.offset(off);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, box.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                }
            }
        }
        return true;
    }

    private static void placeFarmCaveSystem(ServerLevel level, FarmInstance farm, FarmType.FarmLayout layout) {
        BlockPos origin = farm.getOrigin();

        // 1. 清空区域（仅 FOREST）
        FarmType.CaveRegion clear = layout.caveClearBox();
        if (clear != null) {
            fillRegion(level, origin, clear, Blocks.AIR.defaultBlockState());
        }

        // 2. 黑色混凝土墙（STANDARD/FOREST）
        FarmType.CaveRegion blackWall = layout.caveBlackWall();
        if (blackWall != null) {
            fillRegion(level, origin, blackWall, Blocks.BLACK_CONCRETE.defaultBlockState());
        }

        // 3. 外部传送方块
        FarmType.CaveRegion portalWall = layout.cavePortalWall();
        if (portalWall != null) {
            BlockPos absMin = origin.offset(portalWall.min());
            BlockPos absMax = origin.offset(portalWall.max());
            com.stardew.craft.interior.InteriorSubspaceManager.spawnFarmCaveOutdoorPortalArea(level, absMin, absMax);
        }

        // 4. 室内：为 owner 分配洞穴 origin + 放置 schem + 室内出口传送
        com.stardew.craft.interior.PlayerInteriorAllocator alloc =
                com.stardew.craft.interior.PlayerInteriorAllocator.get(level);
        alloc.ensureCaveLoaded(level, farm.getOwnerUUID());

        StardewCraft.LOGGER.info("[FARM_INIT] Farm cave system placed for {}", farm.getOwnerName());
    }

    /**
     * 在 (origin + region.min)~(origin + region.max) 的立方体区域填充 state。min/max 均包含。
     */
    private static void fillRegion(ServerLevel level, BlockPos origin, FarmType.CaveRegion region, BlockState state) {
        BlockPos min = origin.offset(region.min());
        BlockPos max = origin.offset(region.max());
        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int maxY = Math.max(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), state, 3);
                }
            }
        }
    }

    // ══════════════════════════════════════════
    //  辅助
    // ══════════════════════════════════════════

    private static void preloadFarmChunks(ServerLevel level, FarmInstance farm) {        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        int minCX = min.getX() >> 4, maxCX = max.getX() >> 4;
        int minCZ = min.getZ() >> 4, maxCZ = max.getZ() >> 4;
        int count = 0;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                level.getChunk(cx, cz);
                count++;
            }
        }
        StardewCraft.LOGGER.info("[FARM_INIT] Pre-loaded {} chunks", count);
    }

    /**
     * 给玩家发放开局物品（如河边农场的熏鱼机）。
     */
    private static void giveStarterItem(ServerLevel level, FarmInstance farm,
                                         net.minecraft.world.item.Item item) {
        var player = level.getServer().getPlayerList().getPlayer(farm.getOwnerUUID());
        if (player != null) {
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            StardewCraft.LOGGER.info("[FARM_INIT] Gave {} to {}", item, farm.getOwnerName());
        }
    }
}
