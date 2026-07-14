package com.stardew.craft.mining;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mastery.TallMasteryBlock;
import com.stardew.craft.block.mine.CalicoStatueBlock;
import com.stardew.craft.api.v1.mining.StardewMineMonsterContext;
import com.stardew.craft.api.v1.mining.StardewMineMonsterProviders;
import com.stardew.craft.api.v1.mining.StardewMineThemeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * 矿井楼层生成器
 * 
 * 按照 Implementation Plan + MINING_REGISTRATION_CHECKLIST.md：
 * - 每层中心 (0, 64, floor*100)
 * - 尺寸：20×20 到 50×50（随机）
 * - 高度：6层（Y=64到Y=69）
 * - 外壳：mine_barrier 封印石
 * - 内部频次：主石头(80-90%) >> 装饰石头(5-15%) > 原版方块(1-5%)
 * - 装饰石头按用途选择：
 *   - banded_marble: 冰段洞窟装饰、宝箱房地面
 *   - limestone: 土段洞窟、靠近木桶/矿车区域
 *   - mossy_sandstone: 土段过渡、隐藏房间的潮湿角落
 *   - cracked_slate: 三段通用，适合边缘/墙面阴影区
 *   - scoria: 熔岩段洞窟、火山气息装饰
 *   - salt_rock: 冰段、特殊洞窟标志材
 */
@SuppressWarnings({"null", "unused"})
public class MineFloorGenerator {

    private static final int GENERATION_VERSION = 20;
    private static final double ORE_RATE_MULTIPLIER = 0.15;
    // A类矿（直接采集）在洞窟表面的概率
    private static final double SURFACE_MINERAL_RATE = 0.012;
    // B类矿（宝石矿石节点）生成概率倍率
    private static final double GEM_NODE_RATE_MULTIPLIER = 0.14;
    // B类矿更偏向洞窟表面
    private static final double GEM_SURFACE_BIAS = 0.7;

    // Perlin 噪声频率 — 控制地质带宽度（越小越宽）
    private static final double PERLIN_FREQ = 0.08;
    
    // 房间尺寸范围
    private static final int MIN_SIZE = 80;
    private static final int MAX_SIZE = 120;
    
    // 房间高度（35格，安全区 65-67 处于上半部，洞穴主体向下展开）
    private static final int FLOOR_HEIGHT = 35;
    private static final int FLOOR_Y_START = 48;
    private static final int FLOOR_Y_END = FLOOR_Y_START + FLOOR_HEIGHT - 1; // 48-82
    
    // 中心安全区半径（3×3）
    private static final int SAFE_ZONE_RADIUS = 1;
    // 洞窟与安全区的缓冲距离（避免擦边）
    private static final int SAFE_ZONE_BUFFER = 2;
    private static final int SAFE_ZONE_Y_START = 65;
    private static final int SAFE_ZONE_Y_END = 67;

    // 洞窟与地板/天花板的最小间距（至少留 1-2 层）
    private static final int CAVE_VERTICAL_PADDING = 2;
    private static final String LADDER_HIGHLIGHT_TAG = "stardewcraft_mine_ladder_highlight";

    // ── 缓存 DeferredHolder.get() 结果，避免热路径上重复解引用 ──
    private static Block EARTH_SHALE, DARK_EARTH_SHALE;
    private static Block FROST_GNEISS, DARK_FROST_GNEISS;
    private static Block LAVA_BASALT, DARK_LAVA_BASALT;
    private static Block DESERT_BEDROCK, DARK_DESERT_BEDROCK;
    private static Block SULFUR_ROCK, WEATHERED_STONE;
    private static Block BANDED_MARBLE, LIMESTONE, MOSSY_SANDSTONE;
    private static Block CRACKED_SLATE, SCORIA, SALT_ROCK;
    private static Block MINE_BARRIER;
    private static boolean blocksCached = false;

    private static void ensureBlocksCached() {
        if (blocksCached) return;
        EARTH_SHALE      = ModBlocks.EARTH_SHALE.get();
        DARK_EARTH_SHALE  = ModBlocks.DARK_EARTH_SHALE.get();
        FROST_GNEISS     = ModBlocks.FROST_GNEISS.get();
        DARK_FROST_GNEISS = ModBlocks.DARK_FROST_GNEISS.get();
        LAVA_BASALT      = ModBlocks.LAVA_BASALT.get();
        DARK_LAVA_BASALT  = ModBlocks.DARK_LAVA_BASALT.get();
        DESERT_BEDROCK   = ModBlocks.DESERT_BEDROCK.get();
        DARK_DESERT_BEDROCK = ModBlocks.DARK_DESERT_BEDROCK.get();
        SULFUR_ROCK      = ModBlocks.SULFUR_ROCK.get();
        WEATHERED_STONE  = ModBlocks.WEATHERED_STONE.get();
        BANDED_MARBLE    = ModBlocks.BANDED_MARBLE.get();
        LIMESTONE        = ModBlocks.LIMESTONE.get();
        MOSSY_SANDSTONE  = ModBlocks.MOSSY_SANDSTONE.get();
        CRACKED_SLATE    = ModBlocks.CRACKED_SLATE.get();
        SCORIA           = ModBlocks.SCORIA.get();
        SALT_ROCK        = ModBlocks.SALT_ROCK.get();
        MINE_BARRIER     = ModBlocks.MINE_BARRIER.get();
        blocksCached = true;
    }
    
    /**
     * 生成指定楼层
     */
    public static void generateFloor(ServerLevel level, int floorNumber) {
        ensureBlocksCached();
        RandomSource random = level.getRandom();

        // Floor 121: 骷髅矿入口大厅（schem 放置，无标准地形）
        if (floorNumber == 121) {
            generateSkullCavernLobby(level);
            return;
        }

        clearLegacyLadderHighlightDisplays(level, floorNumber);

        // 每天只生成一次：同一天内进入不刷新
        MineFloorDataManager manager = MineFloorDataManager.get(level);
        MineFloorData existingData = manager.getFloorData(floorNumber);
        if (!manager.needsGeneration(floorNumber)
            && existingData != null
            && existingData.getGenerationVersion() == GENERATION_VERSION) {
            StardewCraft.LOGGER.info("[MINE] Floor {} already generated today, skipping refresh.", floorNumber);
            return;
        }
        clearExistingFloorMobs(level, floorNumber);
        manager.clearFloorData(floorNumber);
        
        // 1. 确定房间尺寸（20-50随机）
        int size = MIN_SIZE + random.nextInt(MAX_SIZE - MIN_SIZE + 1);
        
        // 2. 确定主题
        FloorTheme theme = getThemeForFloor(floorNumber);
        boolean isDark = random.nextDouble() < (theme == FloorTheme.SKULL_CAVERN ? 0.30 : 0.15); // 骷髅矿30% dark，常规15%
        
    // 3. 计算中心坐标
    int centerX = 0;
    // 第0层特殊处理：Z=0；其他层：Z=floor*100+14
    int centerZ = (floorNumber == 0) ? 0 : (floorNumber * MiningCoordinates.FLOOR_SPACING + 14);
        
        StardewCraft.LOGGER.info("[MINE] Generating floor {} at center ({}, {}) with size {}x{}, theme: {}, dark: {}", 
            floorNumber, centerX, centerZ, size, size, theme, isDark);
        
        // 4. 生成外壳（mine_barrier）
        generateShell(level, centerX, centerZ, size);
        
        // 5. 填充主石头 + 装饰石头 + 原版方块（Perlin 噪声地质带分布）
        fillInterior(level, random, centerX, centerZ, size, theme, isDark, floorNumber);

    // 5.1 装饰石头斑块（类似原版花岗岩/闪长岩）
    generateDecorPatches(level, random, centerX, centerZ, size, theme);
        
    // 6. 生成洞窟（P0-2 多形状 Carver）
    generateCaves(level, random, centerX, centerZ, size, theme);

    // 6.05 清理悬空方块 — 移除被洞穴挖空后孤立/半悬空的石头
    cleanupFloatingBlocks(level, centerX, centerZ, size);

    // 6.1 微地形 — 台阶/平台/坑洞/碎石/悬挂物（P0-3）
    generateMicroTerrain(level, random, centerX, centerZ, size, theme, isDark);

    // 6.2 洞窟内壁分化 — 墙面/地面/天花板装饰（P0-4）
    decorateCaveInterior(level, random, centerX, centerZ, size, theme, isDark);

    // 6.3 主题环境特色（P1 — 水洼/冰面/熔岩池等）
    generateThemeFeatures(level, random, centerX, centerZ, size, theme, isDark, floorNumber);

    // 6.35 可钓鱼地下水池（每层 0-1 个，5×5~8×8，2格深）
    generateFishingPool(level, random, centerX, centerZ, size, theme, floorNumber);

    // 6.4 P2 特殊房间（蘑菇房/矿石密集区/骨骸房）
    generateSpecialRoom(level, random, centerX, centerZ, size, theme, isDark, floorNumber);

    // 6.45 P2 环境装饰（骨头/轨道/篝火）
    generateEnvironmentDecor(level, random, centerX, centerZ, size, theme, isDark, floorNumber);

    // 6.5 生成光照（P0 智能间距 + 墙壁偏好）
    generateLighting(level, random, centerX, centerZ, size, theme, isDark);

    // 6.6 生成木桶（SDV BreakableContainer，洞窟地面散落）
    if (floorNumber > 0 && (floorNumber % 5 != 0 || floorNumber >= 121)) {
        generateBarrels(level, random, centerX, centerZ, size, floorNumber);
    }

    // 7. 生成矿石（避免被洞窟挖空）
    generateOres(level, random, centerX, centerZ, size, floorNumber, theme);
        
        // 8. 生成中心安全区（清空玩家出生点周围）
        generateSafeZone(level, centerX, centerZ, floorNumber);
        // 注意：楼梯不再预放置，改为挖掘石头时动态生成（见 MiningBlockBreakHandler）

        // 9. 生成A类矿（洞窟表面）与B类矿（宝石矿石节点）
        generateSurfaceMinerals(level, random, centerX, centerZ, size, floorNumber);
        generateGemOreNodes(level, random, centerX, centerZ, size, floorNumber);
        
    // 10. 统计可计数石头数量并初始化楼层数据
        int stonesLeft = countStones(level, centerX, centerZ, size);
        MineFloorData floorData = manager.getOrCreateFloorData(floorNumber, stonesLeft);
        floorData.setStonesLeft(stonesLeft);
    floorData.setGenerationVersion(GENERATION_VERSION);
        manager.setFloorData(floorNumber, floorData);

    // 记录当日已生成
    manager.markGenerated(floorNumber);

        // 11. 预放置怪物（所有类型，不依赖原生刷怪）
        spawnMonsters(level, random, centerX, centerZ, size, floorNumber, isDark);
        
        StardewCraft.LOGGER.info("[MINE] Floor {} generation complete, stonesLeft: {}", floorNumber, stonesLeft);
    }

    private static void clearExistingFloorMobs(ServerLevel level, int floorNumber) {
        BlockPos center = MiningCoordinates.getFloorCenter(floorNumber);
        int halfSize = MAX_SIZE / 2 + 8;
        AABB floorBounds = new AABB(
                center.getX() - halfSize, FLOOR_Y_START - 4, center.getZ() - halfSize,
                center.getX() + halfSize, FLOOR_Y_END + 8, center.getZ() + halfSize);

        int removed = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, floorBounds,
                entity -> entity.getTags().stream().anyMatch(tag -> tag.startsWith("sd_mob_")))) {
            mob.discard();
            removed++;
        }
        com.stardew.craft.event.MineMonsterSpawnHandler.invalidateFloorMobCount(floorNumber);
        if (removed > 0) {
            StardewCraft.LOGGER.info("[MINE] Cleared {} persisted monsters before regenerating floor {}",
                    removed, floorNumber);
        }
    }

    private static void clearLegacyLadderHighlightDisplays(ServerLevel level, int floorNumber) {
        BlockPos center = MiningCoordinates.getFloorCenter(floorNumber);
        int halfSize = MAX_SIZE / 2 + 8;
        AABB floorBounds = new AABB(
                center.getX() - halfSize, FLOOR_Y_START - 4, center.getZ() - halfSize,
                center.getX() + halfSize, FLOOR_Y_END + 8, center.getZ() + halfSize);
        String floorTag = LADDER_HIGHLIGHT_TAG + "_" + floorNumber;
        int removed = 0;
        for (Display.BlockDisplay marker : level.getEntitiesOfClass(Display.BlockDisplay.class, floorBounds,
                entity -> entity.getTags().contains(LADDER_HIGHLIGHT_TAG)
                        && entity.getTags().contains(floorTag))) {
            marker.discard();
            removed++;
        }
        if (removed > 0) {
            StardewCraft.LOGGER.info("[MINE] Removed {} legacy ladder highlight markers on floor {}", removed, floorNumber);
        }
    }

    /**
     * 强制刷新楼层光照 — 向客户端重新发送所有光源方块的更新包
     * <p>
     * 解决生成楼层时光照引擎尚未完成传播、客户端收到的 chunk 数据里光照不正确的问题。
     * 应在玩家传送到新楼层后延迟几 tick 调用，此时玩家已在追踪对应 chunk。
     */
    public static void forceClientLightRefresh(ServerLevel level, int floorNumber) {
        BlockPos center = MiningCoordinates.getFloorCenter(floorNumber);
        int halfSize = MAX_SIZE / 2 + 2;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        int count = 0;
        for (int x = center.getX() - halfSize; x <= center.getX() + halfSize; x++) {
            for (int z = center.getZ() - halfSize; z <= center.getZ() + halfSize; z++) {
                for (int y = FLOOR_Y_START; y <= FLOOR_Y_END; y++) {
                    mpos.set(x, y, z);
                    BlockState state = level.getBlockState(mpos);
                    if (state.getLightEmission(level, mpos) > 0) {
                        level.getLightEngine().checkBlock(mpos);
                        level.getChunkSource().blockChanged(mpos);
                        count++;
                    }
                }
            }
        }
        StardewCraft.LOGGER.debug("[MINE] forceClientLightRefresh floor {} — re-sent {} light sources", floorNumber, count);
    }

    /**
     * 统计楼层中可计数的石头数量
     */
    private static int countStones(ServerLevel level, int centerX, int centerZ, int size) {
        int halfSize = size / 2;
        int count = 0;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        
        for (int x = centerX - halfSize + 1; x < centerX + halfSize; x++) {
            for (int z = centerZ - halfSize + 1; z < centerZ + halfSize; z++) {
                for (int y = FLOOR_Y_START; y <= FLOOR_Y_END; y++) {
                    Block block = level.getBlockState(mpos.set(x, y, z)).getBlock();
                    
                    // 检查是否是可计数的主石头
                    if (isCountableStone(block)) {
                        count++;
                    }
                }
            }
        }
        
        return count;
    }
    
    /**
     * 检查方块是否是可计数的主石头
     */
    private static boolean isCountableStone(Block block) {
        // 主石头（6种）+ 装饰石头也计数
        return block == EARTH_SHALE || block == FROST_GNEISS || block == LAVA_BASALT
            || block == BANDED_MARBLE || block == LIMESTONE || block == MOSSY_SANDSTONE
            || block == CRACKED_SLATE || block == SCORIA || block == SALT_ROCK;
    }

    /**
     * 生成光照 — 智能墙壁/地面放置 + 间距控制
     *
     * 策略：
     *   1. 安全区固定4个火把（保留）
     *   2. 洞窟内：优先在墙壁旁的地面放置光源
     *   3. 最小间距 6 格，避免过密或过疏
     *   4. Dark层光照密度降至 1/3 + 使用 Soul 变体
     */
    @SuppressWarnings("null")
    private static void generateLighting(ServerLevel level, RandomSource random, int centerX, int centerZ, int size, FloorTheme theme, boolean isDark) {
        int halfSize = size / 2;

        // 安全区固定火把（四角）
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                BlockPos torchPos = new BlockPos(
                        centerX + dx * (SAFE_ZONE_RADIUS + 1),
                        SAFE_ZONE_Y_START,
                        centerZ + dz * (SAFE_ZONE_RADIUS + 1));
                if (level.getBlockState(torchPos).isAir()) {
                    BlockState safeTorch = (theme == FloorTheme.SKULL_CAVERN)
                        ? Blocks.SOUL_TORCH.defaultBlockState()
                        : Blocks.TORCH.defaultBlockState();
                    level.setBlock(torchPos, safeTorch, 3);
                }
            }
        }

        // 光源方块选择
        BlockState groundLight, ceilingLight;
        if (isDark) {
            groundLight = Blocks.SOUL_LANTERN.defaultBlockState();
            ceilingLight = Blocks.SOUL_LANTERN.defaultBlockState();
        } else if (theme == FloorTheme.SKULL_CAVERN) {
            // 骷髅矿：Soul Lantern 地面 + Magma Block 天花板自然光源
            groundLight = Blocks.SOUL_LANTERN.defaultBlockState();
            ceilingLight = Blocks.SHROOMLIGHT.defaultBlockState();
        } else {
            switch (theme) {
                case LAVA:
                    groundLight = Blocks.LANTERN.defaultBlockState();
                    ceilingLight = Blocks.SHROOMLIGHT.defaultBlockState();
                    break;
                case FROST:
                    groundLight = Blocks.LANTERN.defaultBlockState();
                    ceilingLight = Blocks.SEA_LANTERN.defaultBlockState();
                    break;
                default:
                    groundLight = Blocks.TORCH.defaultBlockState();
                    ceilingLight = Blocks.LANTERN.defaultBlockState();
                    break;
            }
        }

        // 最小间距
        int spacing = isDark ? 14 : (theme == FloorTheme.SKULL_CAVERN ? 10 : 7);
        // 用网格+随机偏移保证均匀分布
        for (int gx = centerX - halfSize + 4; gx < centerX + halfSize - 3; gx += spacing) {
            for (int gz = centerZ - halfSize + 4; gz < centerZ + halfSize - 3; gz += spacing) {
                // 随机偏移 ±2
                int x = gx + random.nextInt(5) - 2;
                int z = gz + random.nextInt(5) - 2;

                if (Math.abs(x - centerX) >= halfSize - 2 || Math.abs(z - centerZ) >= halfSize - 2) continue;
                if (Math.abs(x - centerX) <= SAFE_ZONE_RADIUS + SAFE_ZONE_BUFFER
                    && Math.abs(z - centerZ) <= SAFE_ZONE_RADIUS + SAFE_ZONE_BUFFER) continue;

                // 优先放地面光源
                boolean placed = false;
                for (int y = FLOOR_Y_END - 1; y >= FLOOR_Y_START + 1; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockPos below = pos.below();
                    if (level.getBlockState(pos).isAir() && !level.getBlockState(below).isAir()) {
                        // 偏好墙壁旁（至少一个水平邻居是实心）
                        boolean nearWall = false;
                        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                            if (!level.getBlockState(pos.relative(dir)).isAir()) {
                                nearWall = true;
                                break;
                            }
                        }
                        if (nearWall || random.nextFloat() < 0.3f) {
                            level.setBlock(pos, groundLight, 3);
                            placed = true;
                        }
                        break;
                    }
                }

                // 若地面没放成功，尝试天花板
                if (!placed && random.nextFloat() < 0.4f) {
                    for (int y = FLOOR_Y_START + 2; y <= FLOOR_Y_END - 1; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockPos above = pos.above();
                        if (level.getBlockState(pos).isAir() && !level.getBlockState(above).isAir()
                            && !level.getBlockState(above).is(ModBlocks.MINE_BARRIER.get())) {
                            level.setBlock(pos, ceilingLight, 3);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * 预放置怪物 — 按 SDV 原版瀑布概率（cascading probability）生成怪物
     * MineMonsterSpawnHandler 会在 EntityJoinLevel 事件中注入 SDV 属性和 tag
     *
     * 数量已按面积比例缩减：SDV 40×30 房间约 8-15 只；我们 80-120 格房间按 area/900
     */
    private static void spawnMonsters(ServerLevel level, RandomSource random,
                                      int centerX, int centerZ, int size, int floorNumber,
                                      boolean isDark) {
        if (floorNumber <= 0) return; // 大厅不刷怪
        if (floorNumber % 5 == 0) return; // boss 层 / 电梯层不刷普通怪

        int area = size * size;
        int baseCount = Math.max(6, area / (floorNumber > 120 ? 500 : 600)); // 骷髅矿怪物密度更高
        int maxCount = floorNumber > 120 ? 35 : 30; // 骷髅矿上限 35
        int totalCount = Math.min(baseCount + random.nextInt(baseCount / 2 + 1), maxCount);
        if (hasMonsterMuskPlayer(level, floorNumber)) {
            totalCount = Math.min(maxCount * 2, totalCount * 2);
        }
        if (floorNumber > 120) {
            totalCount = com.stardew.craft.festival.desert.DesertFestivalMineService.adjustMonsterCountForCalicoStatues(level, totalCount);
        }

        boolean ghostSpawned = false; // SDV ghostAdded 标记：每层最多 1 只 Ghost

        int halfSize = size / 2;
        int spawned = 0;
        int maxAttempts = totalCount * 30;

        for (int attempt = 0; attempt < maxAttempts && spawned < totalCount; attempt++) {
            int x = centerX - halfSize + 4 + random.nextInt(Math.max(1, size - 8));
            int z = centerZ - halfSize + 4 + random.nextInt(Math.max(1, size - 8));

            // 避开安全区
            if (Math.abs(x - centerX) <= SAFE_ZONE_RADIUS + SAFE_ZONE_BUFFER + 2
                    && Math.abs(z - centerZ) <= SAFE_ZONE_RADIUS + SAFE_ZONE_BUFFER + 2) continue;

            // SDV 瀑布概率选择怪物类型
            // 骷髅矿：isDark 时必出 Carbon Ghost，飞行怪须远离出生点
            double distFromCenter = Math.sqrt((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ));
            EntityType<?> type = pickMonsterForFloor(floorNumber, random, isDark, distFromCenter);
            if (floorNumber > 120) {
                type = com.stardew.craft.festival.desert.DesertFestivalMineService.applyCalicoStatueInvasion(level, random, type);
            }

            // Ghost 限制：每层最多 1 只（匹配 SDV ghostAdded 标记）
            if (type == EntityType.HUSK) {
                if (ghostSpawned) {
                    type = EntityType.SLIME; // 回退为史莱姆
                } else {
                    ghostSpawned = true;
                }
            }

            // 飞行怪在空气中生成，地面怪在地面生成
            boolean isFlying = type == EntityType.PHANTOM || type == EntityType.BLAZE;

            if (isFlying) {
                int y = FLOOR_Y_START + 3 + random.nextInt(Math.max(1, FLOOR_HEIGHT - 6));
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.getBlockState(pos).isAir()) continue;

                net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) type.create(level);
                if (mob != null) {
                    mob.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
                    mob.setPersistenceRequired();
                    level.addFreshEntity(mob);
                    spawned++;
                }
            } else {
                // 地面怪：从顶部向下找地面
                for (int y = FLOOR_Y_END - 1; y >= FLOOR_Y_START + 1; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockPos below = pos.below();
                    if (level.getBlockState(pos).isAir() && !level.getBlockState(below).isAir()
                            && level.getBlockState(pos.above()).isAir()) {
                        net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) type.create(level);
                        if (mob != null) {
                            mob.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
                            mob.setPersistenceRequired();
                            level.addFreshEntity(mob);
                            spawned++;
                        }
                        break;
                    }
                }
            }
        }

        if (spawned > 0) {
            StardewCraft.LOGGER.info("[MINE] Spawned {} monsters on floor {} (target={})", 
                    spawned, floorNumber, totalCount);
        } else {
            StardewCraft.LOGGER.warn("[MINE] FAILED to spawn any monsters on floor {} (target={}, attempts={})", 
                    floorNumber, totalCount, maxAttempts);
        }
    }

    private static boolean hasMonsterMuskPlayer(ServerLevel level, int floorNumber) {
        for (ServerPlayer player : level.players()) {
            if (com.stardew.craft.event.MineMonsterSpawnHandler.inferFloor(player) == floorNumber
                    && player.hasEffect(com.stardew.craft.effect.ModMobEffects.MONSTER_MUSK)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 SDV 原版 MineShaft.getMonsterForThisLevel() 的瀑布概率选择怪物。
     * 每次 random 调用都是独立 roll，与原版行为一致。
     *
     * MC 实体映射：
     * - Slime       → Green Slime / Frost Jelly / Sludge
     * - Spider      → Bug（SDV 装甲飞虫）
     * - Silverfish  → Rock Crab / Duggy / Lava Crab
     * - Endermite   → Grub / Dust Spirit
     * - Cave Spider → Fly（SDV 苍蝇）
     * - Phantom     → Bat / Frost Bat / Lava Bat
     * - Zombie      → Rock Golem / Metal Head
     * - Husk        → Ghost（缓慢飘浮型，每层限 1 只）
     * - Skeleton    → Skeleton
     * - Wither Skeleton → Shadow Brute
     * - Stray       → Shadow Shaman（远程魔法型）
     * - Blaze       → Squid Kid
     */
    private static EntityType<?> pickMonsterForFloor(int floor, RandomSource random,
                                                      boolean isDark, double distFromSpawn) {
        EntityType<?> provided = StardewMineMonsterProviders.select(
                new StardewMineMonsterContext(floor, isDark, distFromSpawn, random));
        if (provided != null) return provided;

        // ── 土段 Earth (1-40) — SDV getMineArea() == 0/10 ──
        if (floor <= 40) {
            // 25% Bug（原版全土段出现，非清怪层）
            if (random.nextFloat() < 0.25f) {
                return EntityType.SPIDER;
            }
            if (floor <= 15) {
                // 早期：15% Rock Crab → 其余 Green Slime
                if (random.nextFloat() < 0.15f) return EntityType.SILVERFISH;
                return EntityType.SLIME;
            }
            if (floor <= 30) {
                // 中期：Rock Crab + Fly + Green Slime + Grub
                if (random.nextFloat() < 0.15f) return EntityType.SILVERFISH;  // Rock Crab
                if (random.nextFloat() < 0.05f) return EntityType.CAVE_SPIDER; // Fly
                if (random.nextFloat() < 0.45f) return EntityType.SLIME;       // Green Slime
                return EntityType.ENDERMITE;                                     // Grub
            }
            // 31-40：Rock Golem 主力，少量 Bat
            if (random.nextFloat() < 0.10f) return EntityType.PHANTOM;
            return EntityType.ZOMBIE;
        }

        // ── 冰段 Frost (41-79) — SDV getMineArea() == 40 ──
        if (floor <= 79) {
            // 70-79：Skeleton 占绝对主力（75%）
            if (floor >= 70 && random.nextFloat() < 0.75f) {
                return EntityType.SKELETON;
            }
            // 30% Dust Spirit
            if (random.nextFloat() < 0.30f) return EntityType.ENDERMITE;
            // 30% Frost Bat
            if (random.nextFloat() < 0.30f) return EntityType.PHANTOM;
            // Ghost（>50层，稀有，每层限1只由 spawnMonsters 控制）
            if (floor > 50 && random.nextFloat() < 0.15f) return EntityType.HUSK;
            // 剩余：Frost Jelly
            return EntityType.SLIME;
        }

        // ── 熔岩段 Lava (80-120) — SDV getMineArea() == 80 ──
        if (floor <= 120) {
            // 15% Sludge
            if (random.nextFloat() < 0.15f) return EntityType.SLIME;
            // 15% Metal Head
            if (random.nextFloat() < 0.15f) return EntityType.ZOMBIE;
            // 25% Shadow Brute
            if (random.nextFloat() < 0.25f) return EntityType.WITHER_SKELETON;
            // 25% Shadow Shaman
            if (random.nextFloat() < 0.25f) return EntityType.STRAY;
            // 25% Lava Crab
            if (random.nextFloat() < 0.25f) return EntityType.SILVERFISH;
            // 20% Squid Kid（≥90层）
            if (floor >= 90 && random.nextFloat() < 0.20f) return EntityType.BLAZE;
            // 回退：Lava Bat
            return EntityType.PHANTOM;
        }

        // ── 骷髅矿洞 Skull Cavern (121+) — 按 SDV 权重表选取 ──
        // 映射：Mummy=DROWNED, Serpent/RoyalSerpent=VEX, DinoMonster=HOGLIN, BigSlime=MAGMA_CUBE,
        //      IridiumBat=PHANTOM, IridiumCrab=SILVERFISH, CarbonGhost=HUSK,
        //      ShadowBrute=WITHER_SKELETON, ShadowShaman=STRAY, Bug=SPIDER, GreenSlime=SLIME
        return pickSkullCavernMonster(random, floor, isDark, distFromSpawn);
    }

    /** 骷髅矿权重表 — 总权重 1000，累积选取。 */
    private static EntityType<?> pickSkullCavernMonster(RandomSource random, int floor, boolean isDark, double distFromSpawn) {
        int total = 0;
        int[] weights = new int[12];
        EntityType<?>[] types = new EntityType<?>[12];
        int n = 0;

        // Mummy（Drowned）- SDV 主力
        types[n] = EntityType.DROWNED; weights[n] = 220; n++;
        // Serpent（Vex）- 飞行威胁，需离出生点有距离
        if (distFromSpawn > 6.0) { types[n] = EntityType.VEX; weights[n] = 200; n++; }
        // Big Slime（MagmaCube）- 骷髅矿回退核心
        types[n] = EntityType.MAGMA_CUBE; weights[n] = 130; n++;
        // Green Slime（低权重备选）
        types[n] = EntityType.SLIME; weights[n] = 90; n++;
        // Bug（低权重）
        types[n] = EntityType.SPIDER; weights[n] = 70; n++;
        // Iridium Bat - 高威胁飞行
        types[n] = EntityType.PHANTOM; weights[n] = 110; n++;
        // Iridium Crab（≥146）
        if (floor >= 146) { types[n] = EntityType.SILVERFISH; weights[n] = 80; n++; }
        // DinoMonster / Pepper Rex（≥146 低概率）
        if (floor >= 146 && distFromSpawn > 8.0) { types[n] = EntityType.HOGLIN; weights[n] = 40; n++; }
        // Carbon Ghost（dark 层优先）
        if (isDark) { types[n] = EntityType.HUSK; weights[n] = 70; n++; }
        // Shadow Brute（低概率随机插入，维持紧张感）
        types[n] = EntityType.WITHER_SKELETON; weights[n] = 30; n++;
        // Shadow Shaman
        types[n] = EntityType.STRAY; weights[n] = 20; n++;
        // 普通骷髅（极少数，配合 Skeleton Mage 映射）
        types[n] = EntityType.SKELETON; weights[n] = 20; n++;

        for (int i = 0; i < n; i++) total += weights[i];
        int pick = random.nextInt(total);
        int acc = 0;
        for (int i = 0; i < n; i++) {
            acc += weights[i];
            if (pick < acc) return types[i];
        }
        return EntityType.MAGMA_CUBE;
    }
    
    /**
     * 生成外壳（mine_barrier包裹整个房间）
     */
    @SuppressWarnings("null")
    private static void generateShell(ServerLevel level, int centerX, int centerZ, int size) {
        int halfSize = size / 2;
        BlockState barrierState = ModBlocks.MINE_BARRIER.get().defaultBlockState();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        
        // 遍历整个房间范围
        for (int x = centerX - halfSize; x <= centerX + halfSize; x++) {
            for (int z = centerZ - halfSize; z <= centerZ + halfSize; z++) {
                // 底层（Y=63，比地板低1格）
                level.setBlock(mpos.set(x, FLOOR_Y_START - 1, z), barrierState, 2);
                
                // 顶层（Y=70，比天花板高1格）
                level.setBlock(mpos.set(x, FLOOR_Y_END + 1, z), barrierState, 2);
                
                // 四周墙壁（只在边缘）
                if (x == centerX - halfSize || x == centerX + halfSize || 
                    z == centerZ - halfSize || z == centerZ + halfSize) {
                    for (int y = FLOOR_Y_START; y <= FLOOR_Y_END; y++) {
                        level.setBlock(mpos.set(x, y, z), barrierState, 2);
                    }
                }
            }
        }
    }
    
    /**
     * 填充内部区域 — MC 原版风格：主石头为基底 + 散布斑块
     * 
     * 模拟 MC 原版 granite/diorite/andesite 的分布方式：
     * 1. 先用主石头（earth_shale/frost_gneiss/lava_basalt）填满整个房间
     * 2. 然后叠加散布的装饰石斑块（类似 MC 的 OreFeature 椭球体分布）
     * 3. Dark 层额外叠加 dark 变体斑块
     * 
     * 这种方式避免了 Perlin 噪声带的条纹感和悬空方块问题。
     */
    @SuppressWarnings("null")
    private static void fillInterior(ServerLevel level, RandomSource random, 
                                     int centerX, int centerZ, int size, 
                                     FloorTheme theme, boolean isDark, int floorNumber) {
        int halfSize = size / 2;
        Block mainStone = getMainStone(theme, false);
        BlockState mainStoneState = mainStone.defaultBlockState();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        
        // Phase 1: 用主石头填满整个房间
        for (int x = centerX - halfSize + 1; x < centerX + halfSize; x++) {
            for (int z = centerZ - halfSize + 1; z < centerZ + halfSize; z++) {
                for (int y = FLOOR_Y_START; y <= FLOOR_Y_END; y++) {
                    int dx = Math.abs(x - centerX);
                    int dz = Math.abs(z - centerZ);
                    if (dx <= SAFE_ZONE_RADIUS && dz <= SAFE_ZONE_RADIUS
                        && y >= SAFE_ZONE_Y_START && y <= SAFE_ZONE_Y_END) {
                        continue;
                    }
                    level.setBlock(mpos.set(x, y, z), mainStoneState, 2);
                }
            }
        }
        
        // Phase 2: MC 风格散布斑块 — 每种装饰石独立生成若干椭球体
        // MC 原版每 chunk(16×16) 生成约 2 个 granite/diorite/andesite 斑块，每个 ~33 块
        // 我们的房间是 80-120 格宽，面积约 25-56 个 chunk，按比例缩放
        int area = (size - 2) * (size - 2);
        int chunksEquivalent = area / 256; // 每 chunk = 16×16 = 256
        
        // 装饰石 A（结构性，如 limestone/banded_marble/scoria）— 每 chunk 约 2 个斑块
        int decorACount = Math.max(4, chunksEquivalent * 2);
        Block[] decorABlocks = getDecorABlocks(theme);
        for (int i = 0; i < decorACount; i++) {
            int bx = centerX - halfSize + 2 + random.nextInt(Math.max(1, size - 4));
            int bz = centerZ - halfSize + 2 + random.nextInt(Math.max(1, size - 4));
            int by = FLOOR_Y_START + 1 + random.nextInt(Math.max(1, FLOOR_HEIGHT - 2));
            if (isNearSafeZone(bx, bz, centerX, centerZ)) continue;
            Block block = decorABlocks[random.nextInt(decorABlocks.length)];
            generateStoneBlob(level, random, bx, by, bz, 20 + random.nextInt(14), // 20-33 块
                    block, centerX, centerZ, halfSize);
        }
        
        // 装饰石 B（环境性，如 mossy_sandstone/salt_rock）— 每 chunk 约 1.5 个斑块
        Block[] decorBBlocks = getDecorBBlocks(theme);
        if (decorBBlocks.length > 0) {
            int decorBCount = Math.max(3, (int)(chunksEquivalent * 1.5));
            for (int i = 0; i < decorBCount; i++) {
                int bx = centerX - halfSize + 2 + random.nextInt(Math.max(1, size - 4));
                int bz = centerZ - halfSize + 2 + random.nextInt(Math.max(1, size - 4));
                int by = FLOOR_Y_START + 1 + random.nextInt(Math.max(1, FLOOR_HEIGHT - 2));
                if (isNearSafeZone(bx, bz, centerX, centerZ)) continue;
                Block block = decorBBlocks[random.nextInt(decorBBlocks.length)];
                generateStoneBlob(level, random, bx, by, bz, 15 + random.nextInt(19), // 15-33 块
                        block, centerX, centerZ, halfSize);
            }
        }
        
        // 原版方块点缀（andesite/dirt/ice 等）— 每 chunk 约 1 个斑块，尺寸较小
        int vanillaCount = Math.max(2, chunksEquivalent);
        for (int i = 0; i < vanillaCount; i++) {
            int bx = centerX - halfSize + 2 + random.nextInt(Math.max(1, size - 4));
            int bz = centerZ - halfSize + 2 + random.nextInt(Math.max(1, size - 4));
            int by = FLOOR_Y_START + 1 + random.nextInt(Math.max(1, FLOOR_HEIGHT - 2));
            if (isNearSafeZone(bx, bz, centerX, centerZ)) continue;
            Block block = getVanillaBlock(theme, random);
            generateStoneBlob(level, random, bx, by, bz, 10 + random.nextInt(14), // 10-23 块
                    block, centerX, centerZ, halfSize);
        }
        
        // Dark 层额外覆盖：用 dark 变体替换部分主石头区域
        if (isDark) {
            Block darkStone = getMainStone(theme, true);
            int darkCount = Math.max(4, chunksEquivalent * 2);
            for (int i = 0; i < darkCount; i++) {
                int bx = centerX - halfSize + 2 + random.nextInt(Math.max(1, size - 4));
                int bz = centerZ - halfSize + 2 + random.nextInt(Math.max(1, size - 4));
                int by = FLOOR_Y_START + 1 + random.nextInt(Math.max(1, FLOOR_HEIGHT - 2));
                if (isNearSafeZone(bx, bz, centerX, centerZ)) continue;
                generateStoneBlob(level, random, bx, by, bz, 25 + random.nextInt(20), // 25-44 块
                        darkStone, centerX, centerZ, halfSize);
            }
        }
    }
    
    /**
     * MC OreFeature 风格的椭球体斑块生成 — 纺锤形（spindled ellipsoid）
     * 
     * 算法复刻自 MC OreFeature.place()：
     * 1. 在起始点附近取两个端点 A、B，间距 = size/8
     * 2. 沿 A→B 插值，每步计算 sin(π*t) 纺锤半径
     * 3. 在半径内替换主石头为目标方块
     */
    @SuppressWarnings("null")
    private static void generateStoneBlob(ServerLevel level, RandomSource random,
                                          int startX, int startY, int startZ, int size,
                                          Block block, int centerX, int centerZ, int halfSize) {
        // MC: 两个端点沿随机方向分开 size/8 距离
        float span = size / 8.0f;
        float angle = random.nextFloat() * (float)Math.PI;
        float angleV = (random.nextFloat() - 0.5f) * 0.4f; // 垂直倾斜
        
        double ax = startX + Math.cos(angle) * span;
        double az = startZ + Math.sin(angle) * span;
        double ay = startY + Math.sin(angleV) * span;
        double bx = startX - Math.cos(angle) * span;
        double bz = startZ - Math.sin(angle) * span;
        double by = startY - Math.sin(angleV) * span;
        
        BlockState blockState = block.defaultBlockState();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        
        for (int i = 0; i < size; i++) {
            float t = (float)i / (float)size;
            // 插值位置
            double cx = ax + (bx - ax) * t;
            double cy = ay + (by - ay) * t;
            double cz = az + (bz - az) * t;
            // 纺锤半径：sin(π*t) 在中间最大，两端为 0
            double baseRadius = Math.sin(Math.PI * t) * (size / 16.0) + 0.5;
            double rx = baseRadius * (0.85 + random.nextFloat() * 0.3);
            double ry = baseRadius * 0.6; // Y 方向压扁
            double rz = baseRadius * (0.85 + random.nextFloat() * 0.3);
            
            int minX = (int)Math.floor(cx - rx);
            int maxX = (int)Math.ceil(cx + rx);
            int minY = (int)Math.floor(cy - ry);
            int maxY = (int)Math.ceil(cy + ry);
            int minZ = (int)Math.floor(cz - rz);
            int maxZ = (int)Math.ceil(cz + rz);
            
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        double dx = (x - cx) / rx;
                        double dy = (y - cy) / ry;
                        double dz = (z - cz) / rz;
                        if (dx * dx + dy * dy + dz * dz > 1.0) continue;
                        
                        // 边界检查
                        if (Math.abs(x - centerX) >= halfSize || Math.abs(z - centerZ) >= halfSize) continue;
                        if (y < FLOOR_Y_START || y > FLOOR_Y_END) continue;
                        // 跳过安全区
                        if (Math.abs(x - centerX) <= SAFE_ZONE_RADIUS
                            && Math.abs(z - centerZ) <= SAFE_ZONE_RADIUS
                            && y >= SAFE_ZONE_Y_START && y <= SAFE_ZONE_Y_END) continue;
                        
                        BlockPos pos = mpos.set(x, y, z);
                        BlockState current = level.getBlockState(mpos);
                        // 只替换主石头类方块（不替换 barrier、安全区方块等）
                        if (isMainStone(current)) {
                            level.setBlock(mpos, blockState, 2);
                        }
                    }
                }
            }
        }
    }
    
    /** 装饰石 A 方块数组（按主题） */
    private static Block[] getDecorABlocks(FloorTheme theme) {
        List<Block> blocks = blocks(themeDefinition(theme), StardewMineThemeDefinition::decorA);
        return blocks.isEmpty() ? new Block[]{ModBlocks.CRACKED_SLATE.get()} : blocks.toArray(Block[]::new);
    }
    
    /** 装饰石 B 方块数组（按主题） */
    private static Block[] getDecorBBlocks(FloorTheme theme) {
        List<Block> blocks = blocks(themeDefinition(theme), StardewMineThemeDefinition::decorB);
        return blocks.toArray(Block[]::new);
    }
    
    /**
     * 清理悬空方块 — 洞穴挖空后可能产生 1-2 格的孤立石头碎片
     * 
     * 规则：如果一个非空气方块的 6 个邻居中有 5 个以上是空气，则移除它。
     * 跳过 barrier 和安全区方块。多轮扫描直到无变化。
     */
    @SuppressWarnings("null")
    private static void cleanupFloatingBlocks(ServerLevel level, int centerX, int centerZ, int size) {
        int halfSize = size / 2;
        boolean changed = true;
        int passes = 0;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        BlockState airState = Blocks.AIR.defaultBlockState();
        Block barrierBlock = ModBlocks.MINE_BARRIER.get();
        
        while (changed && passes < 3) {
            changed = false;
            passes++;
            
            for (int x = centerX - halfSize + 2; x < centerX + halfSize - 1; x++) {
                for (int z = centerZ - halfSize + 2; z < centerZ + halfSize - 1; z++) {
                    for (int y = FLOOR_Y_START + 1; y <= FLOOR_Y_END - 1; y++) {
                        mpos.set(x, y, z);
                        BlockState state = level.getBlockState(mpos);
                        if (state.isAir()) continue;
                        if (state.is(barrierBlock)) continue;
                        // 跳过安全区
                        if (Math.abs(x - centerX) <= SAFE_ZONE_RADIUS + 1
                            && Math.abs(z - centerZ) <= SAFE_ZONE_RADIUS + 1
                            && y >= SAFE_ZONE_Y_START - 1 && y <= SAFE_ZONE_Y_END + 1) continue;
                        
                        // 统计空气邻居数
                        int airNeighbors = 0;
                        if (level.getBlockState(neighborPos.set(x + 1, y, z)).isAir()) airNeighbors++;
                        if (level.getBlockState(neighborPos.set(x - 1, y, z)).isAir()) airNeighbors++;
                        if (level.getBlockState(neighborPos.set(x, y + 1, z)).isAir()) airNeighbors++;
                        if (level.getBlockState(neighborPos.set(x, y - 1, z)).isAir()) airNeighbors++;
                        if (level.getBlockState(neighborPos.set(x, y, z + 1)).isAir()) airNeighbors++;
                        if (level.getBlockState(neighborPos.set(x, y, z - 1)).isAir()) airNeighbors++;
                        
                        // 5+ 面暴露在空气中 = 悬空碎片，移除
                        if (airNeighbors >= 5) {
                            level.setBlock(mpos, airState, 2);
                            changed = true;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 生成矿石（精确按照 MINING_IMPLEMENTATION_PLAN.md 的概率）
     * 
     * 核心概率：
     * - 主矿（铜/铁/金）：0.17% 每个石头
     * - 铱矿：0.03% (80-99层) → 0.05% (100-119层)
     * - 煤矿：0.06% 全层通用
     * 
     * 跨段规则：
     * - 铜矿：1-39正常(0.17%)，40-119减半(0.085%)
     * - 铁矿：40-79正常(0.17%)，80-119减半(0.085%)
     * - 金矿：80-119正常(0.17%)
     * - 铱矿：80-99低(0.03%)，100-119高(0.05%)
     */
    private static void generateOres(ServerLevel level, RandomSource random,
                                     int centerX, int centerZ, int size,
                                     int floorNumber, FloorTheme theme) {
        int halfSize = size / 2;
        int totalPlaced = 0;

        // 使用矿脉生成（原版风格）
        int veinCount = getOreVeinCount(floorNumber, size);
        if (veinCount <= 0) {
            return;
        }

        for (int i = 0; i < veinCount; i++) {
            int x = centerX + random.nextInt(size - 10) - (size - 10) / 2;
            int z = centerZ + random.nextInt(size - 10) - (size - 10) / 2;
            int y = FLOOR_Y_START + random.nextInt(FLOOR_HEIGHT);

            if (Math.abs(x - centerX) <= SAFE_ZONE_RADIUS && Math.abs(z - centerZ) <= SAFE_ZONE_RADIUS) {
                continue;
            }

            String oreKey = pickOreKeyForFloor(random, floorNumber);

            if (theme == FloorTheme.SKULL_CAVERN
                    && com.stardew.craft.festival.desert.DesertFestivalMineService.shouldUseCalicoEggStone(level, floorNumber, random)) {
                Block calicoStone = com.stardew.craft.festival.desert.DesertFestivalMineService.pickCalicoEggStone(random);
                int veinSize = getOreVeinSize(random, "calico_egg_stone", floorNumber);
                totalPlaced += generateOreVein(level, random, x, y, z, veinSize, calicoStone, centerX, centerZ, halfSize);
                continue;
            }

            Block oreBlock = getOreBlock(theme, oreKey);
            if (oreBlock == null) {
                continue;
            }

            int veinSize = getOreVeinSize(random, oreKey, floorNumber);

            totalPlaced += generateOreVein(level, random, x, y, z, veinSize, oreBlock, centerX, centerZ, halfSize);
        }

        // 兜底：如果本层没有生成任何矿石，强制放置一小簇主矿
        if (totalPlaced == 0) {
            String primaryOreKey = getPrimaryOreKeyForFloor(floorNumber);
            Block primaryOre = getOreBlock(theme, primaryOreKey);
            if (primaryOre != null) {
                placeFallbackOreCluster(level, centerX, centerZ, size, primaryOre, halfSize);
            }
        }
    }

    /**
     * 生成装饰石头斑块（类似原版石头变种分布）
     */
    private static void generateDecorPatches(ServerLevel level, RandomSource random,
                                              int centerX, int centerZ, int size, FloorTheme theme) {
        int halfSize = size / 2;
        int patchCount = 3 + random.nextInt(4); // 3-6 个斑块（配合 Perlin 带状分布，作为浓缩点缀）

        for (int i = 0; i < patchCount; i++) {
            int patchSize = 4 + random.nextInt(6); // 4-9（缩小斑块尺寸）
            int x = centerX + random.nextInt(size - 10) - (size - 10) / 2;
            int z = centerZ + random.nextInt(size - 10) - (size - 10) / 2;
            int y = FLOOR_Y_START + random.nextInt(FLOOR_HEIGHT);

            if (Math.abs(x - centerX) <= SAFE_ZONE_RADIUS && Math.abs(z - centerZ) <= SAFE_ZONE_RADIUS) {
                continue;
            }

            Block decor = getDecorativeStone(theme, random);
            generateBlob(level, random, x, y, z, patchSize, decor, centerX, centerZ, halfSize);
        }
    }

    private static void generateBlob(ServerLevel level, RandomSource random,
                                     int startX, int startY, int startZ, int size,
                                     Block block, int centerX, int centerZ, int halfSize) {
        double x = startX;
        double y = startY;
        double z = startZ;

        for (int i = 0; i < size; i++) {
            double radius = 1.5 + random.nextFloat() * 1.5;
            replaceMainStoneSphere(level, x, y, z, radius, block, centerX, centerZ, halfSize);

            x += random.nextInt(3) - 1;
            y += random.nextInt(3) - 1;
            z += random.nextInt(3) - 1;
        }
    }

    private static int getOreVeinCount(int floor, int size) {
        double stoneEstimate = size * size * FLOOR_HEIGHT * 0.8; // 估算每层石头数量（体积）
        double totalProbability = getTotalOreProbabilityForFloor(floor);
        double expectedOreBlocks = stoneEstimate * totalProbability;
        double averageVeinSize = 4.5 + getThemeProgress(floor) * 1.0;

        int count = (int)Math.round(expectedOreBlocks / averageVeinSize);
        return Math.max(6, count);
    }

    private static double getTotalOreProbabilityForFloor(int floor) {
        if (floor >= 1 && floor <= 39) {
            double depthFactor = getThemeDepthFactor(floor);
            return (0.0164 + 0.0060) * depthFactor * ORE_RATE_MULTIPLIER; // copper + coal
        }
        if (floor >= 40 && floor <= 79) {
            double depthFactor = getThemeDepthFactor(floor);
            return (0.0140 + 0.0090 + 0.0066) * depthFactor * ORE_RATE_MULTIPLIER; // iron + copper + coal
        }
        if (floor >= 80 && floor <= 119) {
            double depthFactor = getThemeDepthFactor(floor);
            double iridium = (floor >= 100) ? 0.004 : 0.0024;
            return (iridium + 0.0136 + 0.0084 + 0.0084 + 0.0078) * depthFactor * ORE_RATE_MULTIPLIER; // iridium + gold + iron + copper + coal
        }
        // ── 骷髅矿洞 (121+)：接近原版，先提升出矿密度，再让铱矿随深度变得更常见 ──
        if (floor > 120) {
            return getSkullCavernOreChance(floor) * ORE_RATE_MULTIPLIER;
        }
        return 0.0;
    }

    private static String pickOreKeyForFloor(RandomSource random, int floor) {
        double progress = getThemeProgress(floor);
        double commonFactor = 1.0 + progress * 0.5;
        double rareFactor = 1.0 + progress * 1.4;
        double cumulative = 0.0;

        // 基于 MINING_IMPLEMENTATION_PLAN.md 概率 * 4（作为权重），并随深度提升
        if (floor >= 1 && floor <= 39) {
            double copper = 0.0164 * commonFactor * ORE_RATE_MULTIPLIER;
            double coal = 0.0060 * commonFactor * ORE_RATE_MULTIPLIER;
            double total = copper + coal;
            double roll = random.nextDouble() * total;
            cumulative += copper;
            if (roll < cumulative) return "copper";
            return "coal";
        }

        if (floor >= 40 && floor <= 79) {
            double iron = 0.0140 * rareFactor * ORE_RATE_MULTIPLIER;
            double copper = 0.0090 * commonFactor * ORE_RATE_MULTIPLIER;
            double coal = 0.0066 * commonFactor * ORE_RATE_MULTIPLIER;
            double total = iron + copper + coal;
            double roll = random.nextDouble() * total;
            cumulative += iron;
            if (roll < cumulative) return "iron";
            cumulative += copper;
            if (roll < cumulative) return "copper";
            return "coal";
        }

        if (floor >= 80 && floor <= 119) {
            double iridium = (floor >= 100) ? 0.004 : 0.0024;
            double gold = 0.0136;
            double iron = 0.0084;
            double copper = 0.0084;
            double coal = 0.0078;
            iridium *= rareFactor * ORE_RATE_MULTIPLIER;
            gold *= rareFactor * ORE_RATE_MULTIPLIER;
            iron *= commonFactor * ORE_RATE_MULTIPLIER;
            copper *= commonFactor * ORE_RATE_MULTIPLIER;
            coal *= commonFactor * ORE_RATE_MULTIPLIER;
            double total = iridium + gold + iron + copper + coal;
            double roll = random.nextDouble() * total;
            cumulative += iridium;
            if (roll < cumulative) return "iridium";
            cumulative += gold;
            if (roll < cumulative) return "gold";
            cumulative += iron;
            if (roll < cumulative) return "iron";
            cumulative += copper;
            if (roll < cumulative) return "copper";
            return "coal";
        }

        // ── 骷髅矿洞 Skull Cavern (121+) — 贴近原版的逐层升级节奏 ──
        if (floor > 120) {
            double iridiumChance = getSkullCavernIridiumChance(floor);
            double goldChance = getSkullCavernGoldChance(floor);
            double ironChance = getSkullCavernIronChance(floor);

            if (random.nextDouble() < iridiumChance) return "iridium";
            if (random.nextDouble() < goldChance) return "gold";
            if (random.nextDouble() < ironChance) return "iron";
            return "coal";
        }

        return "coal";
    }

    private static double getSkullCavernOreChance(int floor) {
        int skullLevel = floor - 120;
        double chanceForOre = 0.02 + skullLevel * 0.0005;
        if (floor >= 130) {
            chanceForOre += 0.01 * ((Math.min(100, skullLevel) - 10) / 10.0);
        }
        return chanceForOre;
    }

    private static double getSkullCavernIridiumChance(int floor) {
        int skullLevel = floor - 120;
        double iridiumBoost = 0.0;
        if (floor >= 130) {
            iridiumBoost += 0.001 * ((skullLevel - 10) / 10.0);
        }
        iridiumBoost = Math.min(iridiumBoost, 0.004);
        if (skullLevel > 100) {
            iridiumBoost += skullLevel / 1000000.0;
        }

        double vanillaChance = Math.min(100, skullLevel) * (0.0003 + iridiumBoost);
        double floorBonus = 0.003 + Math.min(skullLevel, 80) * 0.00005;
        return Math.min(0.32, vanillaChance * 1.35 + floorBonus);
    }

    private static double getSkullCavernGoldChance(int floor) {
        int skullLevel = floor - 120;
        return 0.01 + (floor - Math.min(150, skullLevel)) * 0.0005;
    }

    private static double getSkullCavernIronChance(int floor) {
        int skullLevel = floor - 120;
        return Math.min(0.5, 0.1 + (floor - Math.min(200, skullLevel)) * 0.005);
    }

    private static double getThemeDepthFactor(int floor) {
        double progress = getThemeProgress(floor);
        if (floor >= 1 && floor <= 39) {
            return 1.0 + progress * 0.7; // 1.0 -> 1.7
        }
        if (floor >= 40 && floor <= 79) {
            return 1.0 + progress * 0.9; // 1.0 -> 1.9
        }
        if (floor >= 80 && floor <= 119) {
            return 1.0 + progress * 1.1; // 1.0 -> 2.1
        }
        if (floor > 120) {
            // 骷髅矿：每层 +1% 矿石产出，100 层后封顶为 2.0x
            int skullLevel = floor - 120;
            return 1.0 + Math.min(skullLevel, 100) * 0.01;
        }
        return 1.0;
    }

    private static double getThemeProgress(int floor) {
        if (floor >= 1 && floor <= 39) {
            return (floor - 1) / 38.0;
        }
        if (floor >= 40 && floor <= 79) {
            return (floor - 40) / 39.0;
        }
        if (floor >= 80 && floor <= 119) {
            return (floor - 80) / 39.0;
        }
        if (floor > 120) {
            // 骷髅矿无限深，progress 以 100 层为满进度，之后 clamp 在 1.0
            return Math.min(1.0, (floor - 120) / 100.0);
        }
        return 0.0;
    }

    private static int getOreVeinSize(RandomSource random, String oreKey, int floor) {
        double progress = getThemeProgress(floor);
        int commonBonus = (int)Math.floor(progress * 2.0); // 0-2
        int rareBonus = (int)Math.floor(progress * 1.0);   // 0-1
        return switch (oreKey) {
            case "copper" -> 4 + random.nextInt(4) + commonBonus; // 4-9
            case "iron" -> 4 + random.nextInt(3) + commonBonus; // 4-8
            case "gold" -> 3 + random.nextInt(3) + rareBonus; // 3-6
            case "iridium" -> 2 + random.nextInt(3) + rareBonus; // 2-5
            case "coal" -> 3 + random.nextInt(4) + commonBonus; // 3-8
            default -> 3 + random.nextInt(3) + commonBonus; // 3-7
        };
    }

    private static int generateOreVein(ServerLevel level, RandomSource random,
                                        int startX, int startY, int startZ, int size,
                                        Block oreBlock, int centerX, int centerZ, int halfSize) {
        double angle = random.nextDouble() * Math.PI;
        double spread = size / 8.0;

        double ax = startX + Math.sin(angle) * spread;
        double bx = startX - Math.sin(angle) * spread;
        double az = startZ + Math.cos(angle) * spread;
        double bz = startZ - Math.cos(angle) * spread;
        double ay = startY + random.nextInt(3) - 1;
        double by = startY + random.nextInt(3) - 1;

        double radiusBase = random.nextDouble() * size / 16.0;

        int placed = 0;
        for (int i = 0; i <= size; i++) {
            double t = (double)i / size;
            double x = lerp(t, ax, bx);
            double y = lerp(t, ay, by);
            double z = lerp(t, az, bz);

            double scale = (Math.sin(Math.PI * t) + 1.0) * 0.5; // 0..1
            double radius = (scale * radiusBase + 1.0);

            double rx = radius * (0.85 + random.nextDouble() * 0.3);
            double ry = radius * 0.6;
            double rz = radius * (0.85 + random.nextDouble() * 0.3);

            placed += replaceOreEllipsoid(level, x, y, z, rx, ry, rz, oreBlock, centerX, centerZ, halfSize);
        }

        return placed;
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    @SuppressWarnings("null")
    private static int replaceOreEllipsoid(ServerLevel level,
                                                  double centerX, double centerY, double centerZ,
                                                  double radiusX, double radiusY, double radiusZ,
                                                  Block replacement,
                                                  int roomCenterX, int roomCenterZ, int halfSize) {
        if (radiusX <= 0.0 || radiusY <= 0.0 || radiusZ <= 0.0) {
            return 0;
        }

        int placed = 0;

        int minX = (int)Math.floor(centerX - radiusX - 1.0);
        int maxX = (int)Math.floor(centerX + radiusX + 1.0);
        int minY = Math.max(FLOOR_Y_START, (int)Math.floor(centerY - radiusY - 1.0));
        int maxY = Math.min(FLOOR_Y_END, (int)Math.floor(centerY + radiusY + 1.0));
        int minZ = (int)Math.floor(centerZ - radiusZ - 1.0);
        int maxZ = (int)Math.floor(centerZ + radiusZ + 1.0);

        for (int x = minX; x <= maxX; x++) {
            if (Math.abs(x - roomCenterX) >= halfSize - 1) continue;
            double dx = (x + 0.5 - centerX) / radiusX;

            for (int z = minZ; z <= maxZ; z++) {
                if (Math.abs(z - roomCenterZ) >= halfSize - 1) continue;
                double dz = (z + 0.5 - centerZ) / radiusZ;

                for (int y = minY; y <= maxY; y++) {
                    double dy = (y + 0.5 - centerY) / radiusY;
                    double distanceSq = dx * dx + dy * dy + dz * dz;

                    if (distanceSq < 1.0) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState currentState = level.getBlockState(pos);
                        if (isOreReplaceable(currentState)) {
                            level.setBlock(pos, replacement.defaultBlockState(), 2);
                            placed++;
                        }
                    }
                }
            }
        }

        return placed;
    }

    private static boolean isOreReplaceable(BlockState state) {
        Block block = state.getBlock();
        return isMainStone(state)
            || block == ModBlocks.BANDED_MARBLE.get()
            || block == ModBlocks.LIMESTONE.get()
            || block == ModBlocks.MOSSY_SANDSTONE.get()
            || block == ModBlocks.CRACKED_SLATE.get()
            || block == ModBlocks.SCORIA.get()
            || block == ModBlocks.SALT_ROCK.get()
            || block == Blocks.ANDESITE
            || block == Blocks.DIRT
                        || block == Blocks.BLUE_ICE
            || block == Blocks.PACKED_ICE
            || block == Blocks.PRISMARINE_BRICKS
            || block == Blocks.MAGMA_BLOCK
            || block == Blocks.NETHERRACK
            || block == Blocks.SANDSTONE
            || block == Blocks.RED_SANDSTONE;
    }

    private static String getPrimaryOreKeyForFloor(int floor) {
        if (floor > 180) return "iridium";
        if (floor > 120) return "gold";
        if (floor >= 80) return "gold";
        if (floor >= 40) return "iron";
        return "copper";
    }

    @SuppressWarnings("null")
    private static void placeFallbackOreCluster(ServerLevel level,
                                                int centerX, int centerZ, int size,
                                                Block oreBlock, int halfSize) {
        int startX = centerX + 3;
        int startZ = centerZ + 3;
        int placed = 0;

        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                int x = startX + dx;
                int z = startZ + dz;
                if (Math.abs(x - centerX) >= halfSize - 1 || Math.abs(z - centerZ) >= halfSize - 1) {
                    continue;
                }
                for (int y = FLOOR_Y_START; y <= FLOOR_Y_END; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState currentState = level.getBlockState(pos);
                    if (isOreReplaceable(currentState)) {
                        level.setBlock(pos, oreBlock.defaultBlockState(), 2);
                        placed++;
                        if (placed >= 6) {
                            return;
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("null")
    private static void replaceMainStoneSphere(ServerLevel level, double centerX, double centerY, double centerZ,
                                               double radius, Block replacement,
                                               int roomCenterX, int roomCenterZ, int halfSize) {
        int minX = (int)Math.floor(centerX - radius - 1.0);
        int maxX = (int)Math.floor(centerX + radius + 1.0);
        int minY = Math.max(FLOOR_Y_START, (int)Math.floor(centerY - radius - 1.0));
        int maxY = Math.min(FLOOR_Y_END, (int)Math.floor(centerY + radius + 1.0));
        int minZ = (int)Math.floor(centerZ - radius - 1.0);
        int maxZ = (int)Math.floor(centerZ + radius + 1.0);

        for (int x = minX; x <= maxX; x++) {
            if (Math.abs(x - roomCenterX) >= halfSize - 1) continue;
            double dx = (x + 0.5 - centerX) / radius;

            for (int z = minZ; z <= maxZ; z++) {
                if (Math.abs(z - roomCenterZ) >= halfSize - 1) continue;
                double dz = (z + 0.5 - centerZ) / radius;

                for (int y = minY; y <= maxY; y++) {
                    double dy = (y + 0.5 - centerY) / radius;
                    double distanceSq = dx * dx + dy * dy * 1.2 + dz * dz;

                    if (distanceSq < 1.0) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState currentState = level.getBlockState(pos);
                        if (isMainStone(currentState)) {
                            level.setBlock(pos, replacement.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 检查是否是主石头方块
     */
    private static boolean isMainStone(BlockState state) {
        Block block = state.getBlock();
        return block == EARTH_SHALE || block == DARK_EARTH_SHALE
            || block == FROST_GNEISS || block == DARK_FROST_GNEISS
            || block == LAVA_BASALT || block == DARK_LAVA_BASALT
            || block == DESERT_BEDROCK || block == DARK_DESERT_BEDROCK;
    }

    private static boolean isDecorStone(BlockState state) {
        Block block = state.getBlock();
        return block == BANDED_MARBLE || block == LIMESTONE
            || block == MOSSY_SANDSTONE || block == CRACKED_SLATE
            || block == SCORIA || block == SALT_ROCK
            || block == SULFUR_ROCK || block == WEATHERED_STONE;
    }

    private static boolean isStoneForMineral(BlockState state) {
        return isMainStone(state) || isDecorStone(state);
    }

    /** 检查是否是原版点缀石头（fillInterior 中的 getVanillaBlock 结果） */
    private static boolean isVanillaStone(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.ANDESITE || block == Blocks.DIRT
                || block == Blocks.BLUE_ICE || block == Blocks.PACKED_ICE || block == Blocks.PRISMARINE_BRICKS
                || block == Blocks.MAGMA_BLOCK || block == Blocks.NETHERRACK
                || block == Blocks.SANDSTONE || block == Blocks.RED_SANDSTONE;
    }

    private static List<BlockPos> collectStonePositions(ServerLevel level, int centerX, int centerZ, int size) {
        int halfSize = size / 2;
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int x = centerX - halfSize + 1; x < centerX + halfSize; x++) {
            for (int z = centerZ - halfSize + 1; z < centerZ + halfSize; z++) {
                for (int y = FLOOR_Y_START; y <= FLOOR_Y_END; y++) {
                    if (isInSafeZone(x, y, z, centerX, centerZ)) {
                        continue;
                    }
                    mpos.set(x, y, z);
                    if (isStoneForMineral(level.getBlockState(mpos))) {
                        positions.add(mpos.immutable());
                    }
                }
            }
        }
        return positions;
    }

    @SuppressWarnings("null")
    private static List<BlockPos> collectSurfaceStonePositions(ServerLevel level, int centerX, int centerZ, int size) {
        int halfSize = size / 2;
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int x = centerX - halfSize + 1; x < centerX + halfSize; x++) {
            for (int z = centerZ - halfSize + 1; z < centerZ + halfSize; z++) {
                for (int y = FLOOR_Y_START; y <= FLOOR_Y_END - 1; y++) {
                    if (isInSafeZone(x, y, z, centerX, centerZ)) continue;

                    mpos.set(x, y, z);
                    BlockState stoneState = level.getBlockState(mpos);
                    mpos.set(x, y + 1, z);
                    BlockState airState = level.getBlockState(mpos);

                    if (isStoneForMineral(stoneState) && airState.isAir()) {
                        positions.add(mpos.immutable()); // Store the AIR position where the mineral will go
                    }
                }
            }
        }
        return positions;
    }

    @SuppressWarnings("null")
    
    private static void generateLevelExit(ServerLevel level, RandomSource random, int centerX, int centerZ, int size, int floorNumber, MineFloorData data) {
        List<BlockPos> surfaceAirPositions = collectSurfaceStonePositions(level, centerX, centerZ, size);
        if (surfaceAirPositions.isEmpty()) return;
        
        BlockPos bestPos = surfaceAirPositions.get(random.nextInt(surfaceAirPositions.size()));
        double maxDist = 0;
        
        for (int i = 0; i < 20; i++) {
            BlockPos pos = surfaceAirPositions.get(random.nextInt(surfaceAirPositions.size()));
            double dist = pos.distToCenterSqr(centerX, pos.getY(), centerZ);
            if (dist > maxDist) {
                maxDist = dist;
                bestPos = pos;
            }
        }
        
        BlockPos ladderPos = bestPos.below();
        // SDV: 骷髅矿（floor > 120）非必杀层 20% 概率生成竖井
        boolean isShaft = floorNumber > 120
                && !data.isMonsterArea()
                && random.nextDouble() < 0.2;
        net.minecraft.world.level.block.state.BlockState ladderState =
                ModBlocks.MINE_LADDER.get().defaultBlockState()
                        .setValue(com.stardew.craft.block.mine.MineLadderBlock.SHAFT, isShaft);
        level.setBlock(ladderPos, ladderState, 2);
        data.setLadderPos(ladderPos);
        data.setLadderFound(true);

        spawnLadderHighlightDisplay(level, centerX, centerZ, size, floorNumber, ladderPos);
    }

    private static void spawnLadderHighlightDisplay(ServerLevel level, int centerX, int centerZ, int size, int floorNumber, BlockPos ladderPos) {
        int halfSize = size / 2;
        AABB scanBox = new AABB(
            centerX - halfSize, FLOOR_Y_START - 2, centerZ - halfSize,
            centerX + halfSize + 1, FLOOR_Y_END + 3, centerZ + halfSize + 1
        );

        String floorTag = LADDER_HIGHLIGHT_TAG + "_" + floorNumber;
        for (Display.BlockDisplay existing : level.getEntitiesOfClass(Display.BlockDisplay.class, scanBox,
            e -> e.getTags().contains(LADDER_HIGHLIGHT_TAG) && e.getTags().contains(floorTag))) {
            existing.discard();
        }

        Display.BlockDisplay marker = EntityType.BLOCK_DISPLAY.create(level);
        if (marker == null) {
            return;
        }

        marker.setPos(ladderPos.getX() + 0.5D, ladderPos.getY(), ladderPos.getZ() + 0.5D);
        marker.setGlowingTag(true);
        marker.setInvisible(true);
        marker.setNoGravity(true);
        marker.setInvulnerable(true);
        marker.setSilent(true);
        marker.addTag(LADDER_HIGHLIGHT_TAG);
        marker.addTag(floorTag);
        marker.addTag("stardewcraft_mine_ladder_highlight_anchor");
        level.addFreshEntity(marker);
    }

    @SuppressWarnings("null")
    private static void generateSurfaceMinerals(ServerLevel level, RandomSource random, int centerX, int centerZ, int size, int floorNumber) {
        List<BlockPos> surfaceAirPositions = collectSurfaceStonePositions(level, centerX, centerZ, size);
        if (surfaceAirPositions.isEmpty()) {
            return;
        }

        int targetCount = (int)Math.round(surfaceAirPositions.size() * SURFACE_MINERAL_RATE);
        int attempts = Math.max(10, targetCount * 4);
        int placed = 0;

        while (placed < targetCount && attempts-- > 0) {
            BlockPos pos = surfaceAirPositions.get(random.nextInt(surfaceAirPositions.size()));
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) continue;

            Block mineral = pickSurfaceMineralBlock(random, floorNumber);
            if (mineral != null) {
                level.setBlock(pos, mineral.defaultBlockState(), 2);
                placed++;
            }
        }
    }

    private static Block pickSurfaceMineralBlock(RandomSource random, int floorNumber) {
        float quartzWeight = 0.6f;
        float roll = random.nextFloat();
        // 骷髅矿洞 (121+)：A类矿石混合 — Fire Quartz 40%, Earth Crystal 30%, Frozen Tear 30%
        if (floorNumber > 120) {
            if (roll < quartzWeight) return ModBlocks.QUARTZ.get();
            float gemRoll = random.nextFloat();
            if (gemRoll < 0.4f) return ModBlocks.FIRE_QUARTZ.get();
            if (gemRoll < 0.7f) return ModBlocks.EARTH_CRYSTAL.get();
            return ModBlocks.FROZEN_TEAR.get();
        }
        if (floorNumber >= 80) {
            return roll < quartzWeight ? ModBlocks.QUARTZ.get() : ModBlocks.FIRE_QUARTZ.get();
        }
        if (floorNumber >= 40) {
            return roll < quartzWeight ? ModBlocks.QUARTZ.get() : ModBlocks.FROZEN_TEAR.get();
        }
        if (floorNumber >= 1) {
            return roll < quartzWeight ? ModBlocks.QUARTZ.get() : ModBlocks.EARTH_CRYSTAL.get();
        }
        return ModBlocks.QUARTZ.get();
    }

    @SuppressWarnings("null")
    private static void generateGemOreNodes(ServerLevel level, RandomSource random,
                                            int centerX, int centerZ, int size, int floorNumber) {
        List<BlockPos> stonePositions = collectStonePositions(level, centerX, centerZ, size);
        if (stonePositions.isEmpty()) {
            return;
        }

        List<BlockPos> surfaceStones = collectSurfaceStonePositions(level, centerX, centerZ, size);
        double baseRate = 0.003 * GEM_NODE_RATE_MULTIPLIER;
        // 骷髅矿：略高于常规层，但不再把铱矿团块当作常态供给
        if (floorNumber > 120) {
            int skullLevel = floorNumber - 120;
            baseRate *= 1.15 + Math.min(skullLevel * 0.003, 0.35);
        }
        int targetCount = (int)Math.round(stonePositions.size() * baseRate);
        if (targetCount <= 0) {
            targetCount = random.nextFloat() < 0.35f ? 1 : 0;
        }

        int attempts = Math.max(10, targetCount * 4);
        int placed = 0;
        while (placed < targetCount && attempts-- > 0) {
            boolean preferSurface = !surfaceStones.isEmpty() && random.nextDouble() < GEM_SURFACE_BIAS;
            BlockPos pos = (preferSurface ? surfaceStones : stonePositions).get(
                    random.nextInt((preferSurface ? surfaceStones : stonePositions).size()));
            @SuppressWarnings("null")
            BlockState state = level.getBlockState(pos);
            if (!isStoneForMineral(state)) {
                continue;
            }

            Block gemOre = pickGemOreBlockForFloor(random, floorNumber);
            if (gemOre != null) {
                level.setBlock(pos, gemOre.defaultBlockState(), 2);
                placed++;
            }
        }
    }

    private static Block pickGemOreBlockForFloor(RandomSource random, int floorNumber) {
        List<Block> candidates = new ArrayList<>();
        candidates.add(ModBlocks.AMETHYST_ORE.get());
        candidates.add(ModBlocks.TOPAZ_ORE.get());

        if (floorNumber >= 40) {
            candidates.add(ModBlocks.AQUAMARINE_ORE.get());
            candidates.add(ModBlocks.JADE_ORE.get());
        }

        if (floorNumber >= 80) {
            candidates.add(ModBlocks.RUBY_ORE.get());
            candidates.add(ModBlocks.EMERALD_ORE.get());
        }

        if (floorNumber >= 100) {
            candidates.add(ModBlocks.DIAMOND_ORE.get());
        }

        // 骷髅矿洞 B 类矿：仍然偏高端，但不再额外堆叠大块铱矿节点
        if (floorNumber > 120) {
            candidates.add(ModBlocks.RUBY_ORE.get());
            candidates.add(ModBlocks.EMERALD_ORE.get());
            for (int i = 0; i < 2; i++) candidates.add(ModBlocks.DIAMOND_ORE.get());
        }

        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }
    
    /**
     * 根据楼层和概率选择矿石类型
     * 
     * 概率表（按 MINING_IMPLEMENTATION_PLAN.md）：
     * - 铜矿：1-39层(0.17%), 40-119层(0.085%)
     * - 铁矿：40-79层(0.17%), 80-119层(0.085%)
     * - 金矿：80-119层(0.17%)
     * - 铱矿：80-99层(0.03%), 100-119层(0.05%)
     * - 煤矿：1-119层(0.06%)
     * 
     * @param roll 0-100之间的随机数
     */
    
    /**
     * 生成洞窟 — 真正还原 MC 原版 CaveWorldCarver + CanyonWorldCarver 规模
     *
     * MC 原版中，一个 chunk(16×16) 会被 17×17=289 个周围 chunk 的洞穴影响。
     * 平均每个 chunk 有 ~1 个 cave origin，所以一个 100×100 的区域
     * 实际上有 ~30-50 条隧道穿过。
     *
     * 本方法模拟这个规模：
     *   1. Cave origins 数量与面积成正比（每 16×16 约 1-2 个）
     *   2. 隧道长度 80-120 步（MC 原版 84-112）
     *   3. Room 半径可达 10+（MC 原版 2.5-8.5）
     *   4. 追加 Canyon 峡谷雕刻器（宽度随高度变化的裂隙）
     *   5. 追加大型噪声洞穴（类似 MC 1.18+ cheese cave）
     */
    private static void generateCaves(ServerLevel level, RandomSource random,
                                      int centerX, int centerZ, int size,
                                      FloorTheme theme) {
        int halfSize = size / 2;
        List<Block> decorBlocks = getCaveDecorationBlocks(theme);

        // ═══════════ Phase 1: MC 原版 CaveWorldCarver 隧道系统 ═══════════

        // 骷髅矿调整：隧道更密、更粗、更多分叉 → 更开阔混乱的洞窟
        boolean isSkull = theme == FloorTheme.SKULL_CAVERN;
        float densityMul = isSkull ? 1.5f : 1.0f;
        float thicknessMul = isSkull ? 1.3f : 1.0f;

        // 起源数量：每 16×16 区域 ~1.2 个起源（MC 原版密度）
        int chunksInArea = (size / 16) * (size / 16);
        int caveOrigins = Math.max(6, (int)(chunksInArea * 1.2 * densityMul));
        // 上限避免极端卡顿，但允许足够多
        caveOrigins = Math.min(caveOrigins, 75);

        StardewCraft.LOGGER.debug("[MINE] Generating {} cave origins for {}x{} floor", caveOrigins, size, size);

        for (int origin = 0; origin < caveOrigins; origin++) {
            // 随机起始位置
            double startX = centerX + (random.nextInt(size - 10) - (size - 10) / 2);
            double startZ = centerZ + (random.nextInt(size - 10) - (size - 10) / 2);
            double startY = FLOOR_Y_START + 2 + random.nextInt(Math.max(1, FLOOR_HEIGHT - 4));

            if (isNearSafeZone((int) startX, (int) startZ, centerX, centerZ)) continue;

            // MC 原版 horizontalRadiusMultiplier (0.7-1.5) 和 verticalRadiusMultiplier (0.6-1.2)
            double hRadiusMul = 0.7 + random.nextDouble() * 0.8;
            double vRadiusMul = 0.6 + random.nextDouble() * 0.6;

            int tunnelCount = 1;

            // 25% 概率先造大球形 Room（参照 MC createRoom）
            if (random.nextInt(4) == 0) {
                float roomRadius = 1.0F + random.nextFloat() * 6.0F;
                double yScale = 0.5 + random.nextDouble() * 0.5;
                double hRadius = 1.5 + roomRadius; // MC: 1.5 + sin(π/2) * f = 1.5 + f
                double vRadius = hRadius * yScale;
                carveEllipsoid(level, startX, startY, startZ,
                              hRadius * hRadiusMul, vRadius * vRadiusMul, hRadius * hRadiusMul,
                              decorBlocks, random, centerX, centerZ, halfSize);
                tunnelCount += random.nextInt(4);
            }
            // 骷髅矿：每个起源额外多 1-2 条隧道（模拟 fork×1.5）
            if (isSkull) {
                tunnelCount += 1 + random.nextInt(2);
            }

            // 从起源延伸隧道
            for (int t = 0; t < tunnelCount; t++) {
                float yaw = random.nextFloat() * (float)(Math.PI * 2);
                float pitch = (random.nextFloat() - 0.5F) / 4.0F;
                float thickness = getCarverThickness(random) * thicknessMul;
                // MC 原版步数：112 - random(28) = 84-112
                int maxSteps = 80 + random.nextInt(40); // 80-119 步
                createVanillaTunnel(level, random, centerX, centerZ, halfSize, decorBlocks,
                                   startX, startY, startZ, thickness, yaw, pitch,
                                   0, maxSteps, hRadiusMul, vRadiusMul);
            }
        }

        // ═══════════ Phase 2: Canyon 峡谷（参照 MC CanyonWorldCarver）═══════════
        // MC 中每 ~50 chunks 生成 1 条峡谷，我们的 36 chunks 空间生成 1 条
        // 骷髅矿 85% 概率（更多峡谷）
        float canyonChance = isSkull ? 0.85f : 0.7f;
        if (random.nextFloat() < canyonChance) {
            createCanyon(level, random, centerX, centerZ, halfSize, decorBlocks);
        }

        // ═══════════ Phase 3: 大型洞穴房间（cheese cave 风格）═══════════
        // 用多个重叠大椭球创造大型开阔空间
        // 骷髅矿 2-4 个（更宽敞）
        int cheeseCaveCount = isSkull ? (2 + random.nextInt(3)) : (1 + random.nextInt(3));
        for (int i = 0; i < cheeseCaveCount; i++) {
            createLargeCavern(level, random, centerX, centerZ, halfSize, decorBlocks);
        }
    }

    /**
     * MC 原版隧道厚度计算 — 参照 CaveWorldCarver.getThickness()
     * 基础值 random*2 + random (0-3)，10% 概率乘以额外因子 (可达 ~9)
     */
    private static float getCarverThickness(RandomSource random) {
        float f = random.nextFloat() * 2.0F + random.nextFloat();
        if (random.nextInt(10) == 0) {
            f *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        }
        return f;
    }

    /**
     * MC 原版隧道雕刻 — 完整参照 CaveWorldCarver.createTunnel()
     *
     * 关键特性：
     * - sin 曲线半径（纺锤形：细→粗→细）
     * - pitch 强阻尼（×0.7 或 ×0.92）
     * - 中点分叉（yaw ± π/2）— Y形分支
     * - 25% 步进跳过（创造自然空隙）
     * - horizontalRadiusMultiplier / verticalRadiusMultiplier（MC config 参数）
     */
    private static void createVanillaTunnel(ServerLevel level, RandomSource random,
                                            int roomCenterX, int roomCenterZ, int halfSize,
                                            List<Block> decorBlocks,
                                            double x, double y, double z,
                                            float thickness, float yaw, float pitch,
                                            int startStep, int totalSteps,
                                            double hRadiusMul, double vRadiusMul) {
        int branchPoint = startStep + (totalSteps - startStep) / 4
                        + random.nextInt(Math.max(1, (totalSteps - startStep) / 2));
        boolean gentlePitch = random.nextInt(6) == 0;
        float yawChange = 0.0F;
        float pitchChange = 0.0F;

        for (int step = startStep; step < totalSteps; step++) {
            // 纺锤形半径：1.5 + sin(π * step/total) * thickness（MC 原版完全一致）
            double hRadius = 1.5 + Math.sin(Math.PI * step / (double) totalSteps) * thickness;
            double vRadius = hRadius; // 基础垂直半径 = 水平半径

            // 应用 MC config 中的 radius multipliers
            hRadius *= hRadiusMul;
            vRadius *= vRadiusMul;

            // 方向更新（MC 原版完全一致的顺序和系数）
            float cosPitch = (float) Math.cos(pitch);
            x += Math.cos(yaw) * cosPitch;
            y += Math.sin(pitch);
            z += Math.sin(yaw) * cosPitch;

            // pitch 阻尼（MC 原版: flag ? 0.92F : 0.7F）
            pitch *= gentlePitch ? 0.92F : 0.7F;
            pitch += pitchChange * 0.1F;
            yaw += yawChange * 0.1F;

            // 随机扰动累积（MC 原版完全一致）
            pitchChange *= 0.9F;
            yawChange *= 0.75F;
            pitchChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yawChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;

            // 中点 Y 形分叉（MC 原版完全一致）
            if (step == branchPoint && thickness > 1.0F) {
                createVanillaTunnel(level, random, roomCenterX, roomCenterZ, halfSize, decorBlocks,
                                   x, y, z,
                                   random.nextFloat() * 0.5F + 0.5F,
                                   yaw - (float)(Math.PI / 2), pitch / 3.0F,
                                   step, totalSteps, hRadiusMul, vRadiusMul);
                createVanillaTunnel(level, random, roomCenterX, roomCenterZ, halfSize, decorBlocks,
                                   x, y, z,
                                   random.nextFloat() * 0.5F + 0.5F,
                                   yaw + (float)(Math.PI / 2), pitch / 3.0F,
                                   step, totalSteps, hRadiusMul, vRadiusMul);
                return;
            }

            // 边界检查（保留 3 格不挖穿外壳）
            if (Math.abs(x - roomCenterX) >= halfSize - 3 || Math.abs(z - roomCenterZ) >= halfSize - 3) {
                break;
            }

            // 安全区跳过
            if (isNearSafeZone((int) x, (int) z, roomCenterX, roomCenterZ)) {
                continue;
            }

            // 25% 步进跳过（MC 原版: nextInt(4) != 0 才雕刻）
            if (random.nextInt(4) != 0) {
                carveEllipsoid(level, x, y, z, hRadius, vRadius, hRadius,
                              decorBlocks, random, roomCenterX, roomCenterZ, halfSize);
            }
        }
    }

    /**
     * Canyon 峡谷雕刻器 — 参照 MC CanyonWorldCarver.doCarve()
     *
     * 与普通隧道的区别：
     * - 更长（100-160 步）
     * - 宽度随高度变化（initWidthFactors 产生锯齿状墙壁）
     * - 水平/垂直比例更极端（深而窄的裂隙）
     * - yaw 变化系数用 0.05 而非 0.1（更直）
     */
    private static void createCanyon(ServerLevel level, RandomSource random,
                                     int centerX, int centerZ, int halfSize,
                                     List<Block> decorBlocks) {
        double startX = centerX + (random.nextInt(halfSize) - halfSize / 2);
        double startZ = centerZ + (random.nextInt(halfSize) - halfSize / 2);
        double startY = FLOOR_Y_START + 4 + random.nextInt(Math.max(1, FLOOR_HEIGHT - 8));

        if (isNearSafeZone((int) startX, (int) startZ, centerX, centerZ)) return;

        float thickness = 1.0F + random.nextFloat() * 3.0F; // 1-4（比隧道更粗）
        float yaw = random.nextFloat() * (float)(Math.PI * 2);
        float pitch = (random.nextFloat() - 0.5F) * 0.1F; // 几乎水平
        double verticalRatio = 2.0 + random.nextDouble() * 2.0; // 垂直拉伸 2-4×（深裂缝）
        int totalSteps = 100 + random.nextInt(60); // 100-159 步

        // 生成随高度变化的宽度因子（MC: initWidthFactors）
        int heightRange = FLOOR_HEIGHT;
        float[] widthFactors = new float[heightRange];
        float currentFactor = 1.0F;
        for (int j = 0; j < heightRange; j++) {
            if (j == 0 || random.nextInt(3) == 0) {
                currentFactor = 1.0F + random.nextFloat() * random.nextFloat();
            }
            widthFactors[j] = currentFactor * currentFactor;
        }

        float yawChange = 0.0F;
        float pitchChange = 0.0F;
        double x = startX, y = startY, z = startZ;

        for (int step = 0; step < totalSteps; step++) {
            // 纺锤形半径
            double baseRadius = 1.5 + Math.sin(Math.PI * step / (double) totalSteps) * thickness;
            // 60% 面积随机缩放（MC: horizontalRadiusFactor）
            double hRadius = baseRadius * (0.6 + random.nextDouble() * 0.8);
            // 垂直方向拉伸，再加中段膨胀
            float progress = (float) step / totalSteps;
            float centerFactor = 1.0F - Math.abs(0.5F - progress) * 2.0F;
            double vRadius = baseRadius * verticalRatio * (0.7 + 0.6 * centerFactor)
                           * (0.75 + random.nextDouble() * 0.25);

            float cosPitch = (float) Math.cos(pitch);
            x += Math.cos(yaw) * cosPitch;
            y += Math.sin(pitch);
            z += Math.sin(yaw) * cosPitch;

            // Canyon 用 0.7 阻尼，0.05 系数（更直更平稳）
            pitch *= 0.7F;
            pitch += pitchChange * 0.05F;
            yaw += yawChange * 0.05F;

            pitchChange *= 0.8F;
            yawChange *= 0.5F;
            pitchChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yawChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;

            if (Math.abs(x - centerX) >= halfSize - 3 || Math.abs(z - centerZ) >= halfSize - 3) break;
            if (isNearSafeZone((int) x, (int) z, centerX, centerZ)) continue;

            if (random.nextInt(4) != 0) {
                carveCanyonEllipsoid(level, x, y, z, hRadius, vRadius,
                                    widthFactors, decorBlocks, random, centerX, centerZ, halfSize);
            }
        }
    }

    /**
     * Canyon 专用椭球雕刻 — 使用 widthFactors 产生锯齿状墙壁
     * 参照 MC CanyonWorldCarver.shouldSkip()
     */
    @SuppressWarnings("null")
    private static void carveCanyonEllipsoid(ServerLevel level, double cx, double cy, double cz,
                                              double rx, double ry,
                                              float[] widthFactors,
                                              List<Block> decorBlocks, RandomSource random,
                                              int roomCenterX, int roomCenterZ, int halfSize) {
        int minX = (int) Math.floor(cx - rx - 1);
        int maxX = (int) Math.ceil(cx + rx + 1);
        int minY = Math.max(FLOOR_Y_START + 1, (int) Math.floor(cy - ry - 1));
        int maxY = Math.min(FLOOR_Y_END - 1, (int) Math.ceil(cy + ry + 1));
        int minZ = (int) Math.floor(cz - rx - 1);
        int maxZ = (int) Math.ceil(cz + rx + 1);

        for (int x = minX; x <= maxX; x++) {
            if (Math.abs(x - roomCenterX) >= halfSize - 2) continue;
            double dx = (x + 0.5 - cx) / rx;
            for (int z = minZ; z <= maxZ; z++) {
                if (Math.abs(z - roomCenterZ) >= halfSize - 2) continue;
                double dz = (z + 0.5 - cz) / rx;

                for (int y = maxY; y >= minY; y--) {
                    double dy = (y + 0.5 - cy) / ry;
                    // Canyon shouldSkip: (dx² + dz²) * widthFactor[y] + dy²/6 >= 1.0
                    int yIndex = Math.min(Math.max(y - FLOOR_Y_START, 0), widthFactors.length - 1);
                    double distSq = (dx * dx + dz * dz) * widthFactors[yIndex] + dy * dy / 6.0;
                    if (distSq >= 1.0) continue;

                    if (isInSafeZone(x, y, z, roomCenterX, roomCenterZ)) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir() || st.getBlock() == ModBlocks.MINE_BARRIER.get()) continue;

                    if (distSq > 0.8 && random.nextFloat() < 0.10f) {
                        Block decor = decorBlocks.get(random.nextInt(decorBlocks.size()));
                        level.setBlock(pos, decor.defaultBlockState(), 2);
                    } else {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /**
     * 大型洞穴 — 类似 MC 1.18+ cheese cave
     *
     * 用 5-12 个大型重叠椭球创造一个开阔的不规则空间，
     * 中心区域完全掏空，边缘参差不齐。
     */
    private static void createLargeCavern(ServerLevel level, RandomSource random,
                                          int centerX, int centerZ, int halfSize,
                                          List<Block> decorBlocks) {
        // 洞穴中心
        double cx = centerX + (random.nextInt(halfSize) - halfSize / 2);
        double cz = centerZ + (random.nextInt(halfSize) - halfSize / 2);
        double cy = FLOOR_Y_START + 3 + random.nextInt(Math.max(1, FLOOR_HEIGHT - 6));

        if (isNearSafeZone((int) cx, (int) cz, centerX, centerZ)) return;
        // 距离安全区至少 10 格
        if (Math.abs(cx - centerX) < 10 && Math.abs(cz - centerZ) < 10) return;

        int blobCount = 5 + random.nextInt(8); // 5-12 个椭球
        double spreadRadius = 8 + random.nextDouble() * 12; // 散布半径 8-20

        for (int i = 0; i < blobCount; i++) {
            // 椭球位置：以洞穴中心为原点，随机偏移
            double bx = cx + (random.nextDouble() - 0.5) * spreadRadius * 2;
            double bz = cz + (random.nextDouble() - 0.5) * spreadRadius * 2;
            double by = cy + (random.nextDouble() - 0.5) * 6;

            // 椭球半径：4-12（大型）
            double baseRadius = 4 + random.nextDouble() * 8;
            double stretchX = 0.6 + random.nextDouble() * 0.8; // 0.6-1.4
            double stretchY = 0.3 + random.nextDouble() * 0.5; // 0.3-0.8（水平偏平）
            double stretchZ = 0.6 + random.nextDouble() * 0.8;

            carveEllipsoid(level, bx, by, bz,
                          baseRadius * stretchX, baseRadius * stretchY, baseRadius * stretchZ,
                          decorBlocks, random, centerX, centerZ, halfSize);
        }
    }

    /** 安全区附近判定（用于洞窟起点跳过） */
    private static boolean isNearSafeZone(int x, int z, int centerX, int centerZ) {
        return Math.abs(x - centerX) <= SAFE_ZONE_RADIUS + SAFE_ZONE_BUFFER
            && Math.abs(z - centerZ) <= SAFE_ZONE_RADIUS + SAFE_ZONE_BUFFER;
    }

    /**
     * 雕刻椭球形空间 — 参照 MC WorldCarver.carveEllipsoid()
     *
     * 与 MC 的差异：
     * - floorLevel = -0.85 (MC 的 floorLevel 从 config 随机采样，约 -0.7~-1.0)
     * - 使用 CAVE_AIR 可以考虑，但目前洞穴用普通 AIR
     * - 边界保护留 2 格给 mine_barrier
     */
    @SuppressWarnings("null")
    private static void carveEllipsoid(ServerLevel level, double cx, double cy, double cz,
                                       double rx, double ry, double rz,
                                       List<Block> decorBlocks, RandomSource random,
                                       int roomCenterX, int roomCenterZ, int halfSize) {
        int minX = (int) Math.floor(cx - rx - 1);
        int maxX = (int) Math.ceil(cx + rx + 1);
        int minY = Math.max(FLOOR_Y_START + 1, (int) Math.floor(cy - ry - 1));
        int maxY = Math.min(FLOOR_Y_END - 1, (int) Math.ceil(cy + ry + 1));
        int minZ = (int) Math.floor(cz - rz - 1);
        int maxZ = (int) Math.ceil(cz + rz + 1);

        // floorLevel: 低于此高度不雕刻（-0.85 比之前的 -0.7 更宽松）
        double floorLevel = -0.85;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        BlockState airState = Blocks.AIR.defaultBlockState();
        Block barrierBlock = ModBlocks.MINE_BARRIER.get();

        for (int x = minX; x <= maxX; x++) {
            if (Math.abs(x - roomCenterX) >= halfSize - 2) continue;
            double ddx = (x + 0.5 - cx) / rx;
            for (int z = minZ; z <= maxZ; z++) {
                if (Math.abs(z - roomCenterZ) >= halfSize - 2) continue;
                double ddz = (z + 0.5 - cz) / rz;
                if (ddx * ddx + ddz * ddz >= 1.0) continue;

                for (int y = maxY; y >= minY; y--) {
                    double ddy = (y + 0.5 - cy) / ry;
                    if (ddy <= floorLevel) continue;
                    double distSq = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (distSq >= 1.0) continue;

                    if (isInSafeZone(x, y, z, roomCenterX, roomCenterZ)) continue;

                    mpos.set(x, y, z);
                    BlockState st = level.getBlockState(mpos);
                    if (st.isAir() || st.getBlock() == barrierBlock) continue;

                    // 边缘装饰（12% 概率）
                    if (distSq > 0.75 && random.nextFloat() < 0.12f) {
                        Block decor = decorBlocks.get(random.nextInt(decorBlocks.size()));
                        level.setBlock(mpos, decor.defaultBlockState(), 2);
                    } else {
                        level.setBlock(mpos, airState, 2);
                    }
                }
            }
        }
    }

    // ======================== P0-3: 微地形 — 台阶/平台/坑洞/碎石 ========================

    /**
     * 生成微地形 — 在洞窟空间中添加高低差
     *
     * 内容：
     *   1. 抬升平台（Raised Platform）— 3×3~6×6 区域升高 1 格 + Stairs 坡道
     *   2. 下沉坑洞（Sunken Pit）— 2×2~5×5 区域降低 1 格 + Stairs 内坡
     *   3. 地面碎石散布 — 洞窟地面 5-10% 放置 Slab (bottomHalf)
     *   4. 天花板悬挂物 — POINTED_DRIPSTONE / CHAIN
     *   5. 石柱 — 大房间中从地到顶的支柱
     */
    @SuppressWarnings("null")
    private static void generateMicroTerrain(ServerLevel level, RandomSource random,
                                              int centerX, int centerZ, int size,
                                              FloorTheme theme, boolean isDark) {
        int halfSize = size / 2;
        Block mainStone = getMainStone(theme, isDark);
        Block slab = getThemeSlab(theme, isDark);
        Block stairs = getThemeStairs(theme, isDark);

        // ── 1. 抬升平台 ──
        int platformCount = 2 + random.nextInt(3); // 2-4 个
        for (int p = 0; p < platformCount; p++) {
            int pw = 3 + random.nextInt(4); // 3-6
            int pd = 3 + random.nextInt(4);
            int px = centerX + random.nextInt(size - 20) - (size - 20) / 2;
            int pz = centerZ + random.nextInt(size - 20) - (size - 20) / 2;

            // 在此位置找洞窟地面
            int groundY = findCaveGroundY(level, px, pz, centerX, centerZ, halfSize);
            if (groundY < 0) continue;

            // 填充平台主体
            for (int dx = -pw / 2; dx <= pw / 2; dx++) {
                for (int dz = -pd / 2; dz <= pd / 2; dz++) {
                    int x = px + dx;
                    int z = pz + dz;
                    if (Math.abs(x - centerX) >= halfSize - 2 || Math.abs(z - centerZ) >= halfSize - 2) continue;
                    if (isInSafeZone(x, groundY, z, centerX, centerZ)) continue;

                    BlockPos pos = new BlockPos(x, groundY, z);
                    BlockPos above = pos.above();
                    // 只在洞窟地面（下方实心、当前空气）放置
                    if (!level.getBlockState(pos).isAir()) continue;
                    if (level.getBlockState(pos.below()).isAir()) continue;

                    // 边缘用 Stairs，内部用实心石
                    boolean isEdge = Math.abs(dx) == pw / 2 || Math.abs(dz) == pd / 2;
                    if (isEdge) {
                        // 楼梯朝外
                        Direction facing = getEdgeFacing(dx, dz, pw, pd);
                        if (facing != null && stairs instanceof StairBlock stairBlock) {
                            level.setBlock(pos, stairBlock.defaultBlockState()
                                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                                .setValue(BlockStateProperties.HALF, Half.BOTTOM), 2);
                        } else {
                            level.setBlock(pos, slab.defaultBlockState()
                                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), 2);
                        }
                    } else {
                        level.setBlock(pos, mainStone.defaultBlockState(), 2);
                        // 顶部加半砖微调（50%概率）
                        if (random.nextFloat() < 0.5f && level.getBlockState(above).isAir()) {
                            level.setBlock(above, slab.defaultBlockState()
                                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), 2);
                        }
                    }
                }
            }
        }

        // ── 2. 下沉坑洞 ──
        int pitCount = 1 + random.nextInt(3); // 1-3 个
        for (int p = 0; p < pitCount; p++) {
            int pw = 2 + random.nextInt(4); // 2-5
            int pd = 2 + random.nextInt(4);
            int px = centerX + random.nextInt(size - 20) - (size - 20) / 2;
            int pz = centerZ + random.nextInt(size - 20) - (size - 20) / 2;

            int groundY = findCaveGroundY(level, px, pz, centerX, centerZ, halfSize);
            if (groundY < 0 || groundY - 1 < FLOOR_Y_START + 1) continue;

            for (int dx = -pw / 2; dx <= pw / 2; dx++) {
                for (int dz = -pd / 2; dz <= pd / 2; dz++) {
                    int x = px + dx;
                    int z = pz + dz;
                    if (Math.abs(x - centerX) >= halfSize - 2 || Math.abs(z - centerZ) >= halfSize - 2) continue;
                    if (isInSafeZone(x, groundY, z, centerX, centerZ)) continue;

                    BlockPos floorPos = new BlockPos(x, groundY - 1, z); // 地面下方
                    BlockPos airPos = new BlockPos(x, groundY, z);
                    // 只挖实心地面下的石头
                    if (level.getBlockState(airPos).isAir() && !level.getBlockState(floorPos).isAir()
                        && floorPos.getY() >= FLOOR_Y_START + 1) {
                        // 边缘挖一层并放内坡 Stairs
                        boolean isEdge = Math.abs(dx) == pw / 2 || Math.abs(dz) == pd / 2;
                        if (isEdge) {
                            // 保留边缘不挖
                        } else {
                            // 挖掉一格，下方露出的就是坑底
                            level.setBlock(floorPos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }

        // ── 3. 地面碎石散布（Slab） ── 降低密度避免遍地碎石
        for (int x = centerX - halfSize + 2; x < centerX + halfSize - 1; x++) {
            for (int z = centerZ - halfSize + 2; z < centerZ + halfSize - 1; z++) {
                if (random.nextFloat() >= 0.025f) continue; // 2.5% 密度（原 7%）
                if (isNearSafeZone(x, z, centerX, centerZ)) continue;

                // 找洞窟地面
                for (int y = FLOOR_Y_END - 1; y >= FLOOR_Y_START + 1; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockPos below = pos.below();
                    if (level.getBlockState(pos).isAir() && !level.getBlockState(below).isAir()
                        && level.getBlockState(pos.above()).isAir()) {
                        level.setBlock(pos, slab.defaultBlockState()
                            .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), 2);
                        break;
                    }
                }
            }
        }

        // ── 4. 天花板悬挂物 ── 降低密度，修正朝向
        for (int x = centerX - halfSize + 2; x < centerX + halfSize - 1; x++) {
            for (int z = centerZ - halfSize + 2; z < centerZ + halfSize - 1; z++) {
                if (random.nextFloat() >= 0.006f) continue; // 0.6% 密度（原 2%）
                if (isNearSafeZone(x, z, centerX, centerZ)) continue;

                // 找天花板（从顶部向下，上方实心、当前空气）
                for (int y = FLOOR_Y_END - 1; y >= FLOOR_Y_START + 3; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockPos above = pos.above();
                    if (level.getBlockState(pos).isAir() && !level.getBlockState(above).isAir()
                        && !level.getBlockState(above).is(ModBlocks.MINE_BARRIER.get())) {
                        // 只悬挂 1 格（不堆叠，避免视觉杂乱）
                        Block hangBlock;
                        BlockState hangState;
                        switch (theme) {
                            case LAVA:
                                hangBlock = Blocks.CHAIN;
                                hangState = hangBlock.defaultBlockState();
                                break;
                            case FROST:
                                // 冰锥朝下
                                hangState = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);
                                break;
                            default:
                                // 石锥朝下
                                hangState = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);
                                break;
                        }
                        level.setBlock(pos, hangState, 2);
                        break; // 一列只找一个天花板
                    }
                }
            }
        }
    }

    /** 查找某位置的洞窟地面Y（空气下方第一个实心方块上方） */
    private static int findCaveGroundY(ServerLevel level, int x, int z, int centerX, int centerZ, int halfSize) {
        if (Math.abs(x - centerX) >= halfSize - 2 || Math.abs(z - centerZ) >= halfSize - 2) return -1;
        for (int y = FLOOR_Y_END - 1; y >= FLOOR_Y_START + 1; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                return y;
            }
        }
        return -1;
    }

    /** 根据平台边缘偏移返回楼梯朝向（朝外） */
    private static Direction getEdgeFacing(int dx, int dz, int pw, int pd) {
        if (dx == pw / 2) return Direction.EAST;
        if (dx == -pw / 2) return Direction.WEST;
        if (dz == pd / 2) return Direction.SOUTH;
        if (dz == -pd / 2) return Direction.NORTH;
        return null;
    }

    /** 获取主题对应的 Slab */
    private static Block getThemeSlab(FloorTheme theme, boolean isDark) {
        switch (theme) {
            case EARTH: return isDark ? ModBlocks.DARK_EARTH_SHALE_SLAB.get() : ModBlocks.EARTH_SHALE_SLAB.get();
            case FROST: return isDark ? ModBlocks.DARK_FROST_GNEISS_SLAB.get() : ModBlocks.FROST_GNEISS_SLAB.get();
            case LAVA:  return isDark ? ModBlocks.DARK_LAVA_BASALT_SLAB.get() : ModBlocks.LAVA_BASALT_SLAB.get();
            default: return ModBlocks.EARTH_SHALE_SLAB.get();
        }
    }

    /** 获取主题对应的 Stairs */
    private static Block getThemeStairs(FloorTheme theme, boolean isDark) {
        switch (theme) {
            case EARTH: return isDark ? ModBlocks.DARK_EARTH_SHALE_STAIRS.get() : ModBlocks.EARTH_SHALE_STAIRS.get();
            case FROST: return isDark ? ModBlocks.DARK_FROST_GNEISS_STAIRS.get() : ModBlocks.FROST_GNEISS_STAIRS.get();
            case LAVA:  return isDark ? ModBlocks.DARK_LAVA_BASALT_STAIRS.get() : ModBlocks.LAVA_BASALT_STAIRS.get();
            default: return ModBlocks.EARTH_SHALE_STAIRS.get();
        }
    }

    // ======================== P0-4: 洞窟内壁分化 — 墙面/地面/天花板装饰 ========================

    /**
     * 洞窟内壁后处理 — 扫描空气方块的邻居，按 FLOOR/CEILING/WALL 分类并装饰
     *
     * 地面处理：
     *   Earth: 6% MOSSY_SANDSTONE 替换 + 3% MOSS_CARPET 上放
     *   Frost: 5% SNOW 上放 + 3% PACKED_ICE 替换
     *   Lava:  5% MAGMA_BLOCK 替换 + 3% NETHERRACK 替换
     *
     * 天花板处理：
     *   Earth: 3% HANGING_ROOTS 悬挂 + 2% POINTED_DRIPSTONE(down)
     *   Frost: 3% POINTED_DRIPSTONE(down) + 2% PACKED_ICE 替换
     *   Lava:  3% CHAIN 悬挂 + 2% SHROOMLIGHT 嵌入
     *
     * 墙面处理：
     *   Earth: 4% CRACKED_SLATE 替换 + 2% GLOW_LICHEN 贴面
     *   Frost: 3% SALT_ROCK 替换 + 3% BLUE_ICE 替换
     *   Lava:  4% SCORIA 替换 + 2% NETHERRACK 替换
     *   Dark:  额外 2% SCULK_VEIN
     */
    @SuppressWarnings("null")
    private static void decorateCaveInterior(ServerLevel level, RandomSource random,
                                              int centerX, int centerZ, int size,
                                              FloorTheme theme, boolean isDark) {
        int halfSize = size / 2;

        for (int x = centerX - halfSize + 2; x < centerX + halfSize - 1; x++) {
            for (int z = centerZ - halfSize + 2; z < centerZ + halfSize - 1; z++) {
                for (int y = FLOOR_Y_START + 1; y <= FLOOR_Y_END - 1; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) continue;
                    if (isInSafeZone(x, y, z, centerX, centerZ)) continue;

                    // 检查下方 — FLOOR（仅石头类方块，避免替换 slab/stairs 等微地形）
                    BlockPos below = pos.below();
                    BlockState belowState = level.getBlockState(below);
                    if (isMainStone(belowState) || isDecorStone(belowState) || isVanillaStone(belowState)) {
                        decorateFloorSurface(level, random, below, pos, theme);
                    }

                    // 检查上方 — CEILING（仅石头类方块，避免替换悬挂装饰）
                    BlockPos above = pos.above();
                    BlockState aboveState = level.getBlockState(above);
                    if (isMainStone(aboveState) || isDecorStone(aboveState) || isVanillaStone(aboveState)) {
                        decorateCeilingSurface(level, random, above, pos, theme);
                    }

                    // 检查四个水平邻居 — WALL
                    for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                        BlockPos neighbor = pos.relative(dir);
                        BlockState neighborState = level.getBlockState(neighbor);
                        if (!neighborState.isAir() && neighborState.getBlock() != ModBlocks.MINE_BARRIER.get()
                            && (isMainStone(neighborState) || isDecorStone(neighborState))) {
                            decorateWallSurface(level, random, neighbor, pos, theme, isDark);
                            break; // 每个空气格只处理一次墙面
                        }
                    }
                }
            }
        }
    }

    // ======================== P1: 主题环境特色 ========================

    /**
     * 主题环境特色分发 — 根据 Earth/Frost/Lava 生成不同的环境装饰
     */
    @SuppressWarnings("null")
    private static void generateThemeFeatures(ServerLevel level, RandomSource random,
                                               int centerX, int centerZ, int size,
                                               FloorTheme theme, boolean isDark, int floorNumber) {
        switch (theme) {
            case EARTH:
                generateEarthFeatures(level, random, centerX, centerZ, size, isDark, floorNumber);
                break;
            case FROST:
                generateFrostFeatures(level, random, centerX, centerZ, size, isDark);
                break;
            case LAVA:
                generateLavaFeatures(level, random, centerX, centerZ, size, isDark);
                break;
            case SKULL_CAVERN:
                generateSkullCavernFeatures(level, random, centerX, centerZ, size, isDark, floorNumber);
                break;
            default:
                break;
        }
    }

    // ──────── P1-1: Earth段环境特色（1-39层）────────

    /**
     * Earth 段特色：
     * - 小水洼（非Dark层，0-2个，2×2~4×4）
     * - 碎石堆（大房间角落，每层 1-3 堆）
     * - 蜘蛛网（洞窟角落，至少两面墙交汇处）
     */
    @SuppressWarnings("null")
    private static void generateEarthFeatures(ServerLevel level, RandomSource random,
                                               int centerX, int centerZ, int size,
                                               boolean isDark, int floorNumber) {
        int halfSize = size / 2;

        // ── 1. 小水洼（仅非Dark层）──
        if (!isDark) {
            int poolCount = random.nextInt(3); // 0-2 个
            for (int p = 0; p < poolCount; p++) {
                int pw = 2 + random.nextInt(3); // 2-4
                int pd = 2 + random.nextInt(3);
                int px = centerX + random.nextInt(size - 24) - (size - 24) / 2;
                int pz = centerZ + random.nextInt(size - 24) - (size - 24) / 2;
                if (isNearSafeZone(px, pz, centerX, centerZ)) continue;

                int groundY = findCaveGroundY(level, px, pz, centerX, centerZ, halfSize);
                if (groundY < 0 || groundY - 1 <= FLOOR_Y_START) continue;

                int placed = 0;
                for (int dx = -pw / 2; dx <= pw / 2; dx++) {
                    for (int dz = -pd / 2; dz <= pd / 2; dz++) {
                        int x = px + dx;
                        int z = pz + dz;
                        if (Math.abs(x - centerX) >= halfSize - 3 || Math.abs(z - centerZ) >= halfSize - 3) continue;
                        if (isNearSafeZone(x, z, centerX, centerZ)) continue;

                        BlockPos waterPos = new BlockPos(x, groundY, z);
                        BlockPos belowWater = waterPos.below();
                        // 只在洞窟地面的空气处放水
                        if (level.getBlockState(waterPos).isAir()
                            && !level.getBlockState(belowWater).isAir()) {
                            level.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 2);
                            // 水洼周围地面变苔藓砂岩
                            if (isMainStone(level.getBlockState(belowWater)) && random.nextFloat() < 0.5f) {
                                level.setBlock(belowWater, ModBlocks.MOSSY_SANDSTONE.get().defaultBlockState(), 2);
                            }
                            placed++;
                        }
                    }
                }
                if (placed > 0) {
                    StardewCraft.LOGGER.debug("[MINE] Earth: placed water pool {}x at ({}, {})", placed, px, pz);
                }
            }
        }

        // ── 2. 碎石堆（大房间角落）──
        int rubbleCount = 1 + random.nextInt(3); // 1-3 堆
        for (int r = 0; r < rubbleCount; r++) {
            int rx = centerX + random.nextInt(size - 20) - (size - 20) / 2;
            int rz = centerZ + random.nextInt(size - 20) - (size - 20) / 2;
            if (isNearSafeZone(rx, rz, centerX, centerZ)) continue;

            int groundY = findCaveGroundY(level, rx, rz, centerX, centerZ, halfSize);
            if (groundY < 0) continue;

            // 底层 2×2 石头，上方 1-2 个碎石/半砖
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    int x = rx + dx;
                    int z = rz + dz;
                    if (Math.abs(x - centerX) >= halfSize - 3 || Math.abs(z - centerZ) >= halfSize - 3) continue;
                    BlockPos pos = new BlockPos(x, groundY, z);
                    if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                        level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 2);
                        // 上方概率放半砖
                        if (random.nextFloat() < 0.4f) {
                            level.setBlock(pos.above(), Blocks.COBBLESTONE_SLAB.defaultBlockState()
                                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), 2);
                        }
                    }
                }
            }
        }

        // ── 3. 蜘蛛网（洞窟角落，至少两面水平邻居是石头）──
        int webCount = 3 + random.nextInt(6); // 3-8 个
        int webPlaced = 0;
        for (int attempt = 0; attempt < webCount * 20 && webPlaced < webCount; attempt++) {
            int x = centerX - halfSize + 3 + random.nextInt(size - 6);
            int z = centerZ - halfSize + 3 + random.nextInt(size - 6);
            int y = FLOOR_Y_START + 2 + random.nextInt(FLOOR_HEIGHT - 4);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) continue;
            if (isInSafeZone(x, y, z, centerX, centerZ)) continue;

            // 至少两个水平邻居是实心石头
            int solidNeighbors = 0;
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                BlockState ns = level.getBlockState(pos.relative(dir));
                if (isMainStone(ns) || isDecorStone(ns)) solidNeighbors++;
            }
            if (solidNeighbors >= 2) {
                level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 2);
                webPlaced++;
            }
        }
    }

    // ──────── P1-2: Frost段环境特色（40-79层）────────

    /**
     * Frost 段特色：
     * - 冰冻水洼（水面覆 ICE，0-2个）
     * - 地面石笋 POINTED_DRIPSTONE(UP)（洞窟地面 2% 概率）
     * - 冰柱（大房间中 PACKED_ICE 柱，1-2根，1×1×3~5）
     */
    @SuppressWarnings("null")
    private static void generateFrostFeatures(ServerLevel level, RandomSource random,
                                               int centerX, int centerZ, int size, boolean isDark) {
        int halfSize = size / 2;

        // ── 1. 冰冻水洼（下沉 1 格 + 密封 + 冰覆盖）──
        if (!isDark) {
            int poolCount = random.nextInt(3); // 0-2
            for (int p = 0; p < poolCount; p++) {
                int pw = 2 + random.nextInt(3);
                int pd = 2 + random.nextInt(3);
                int px = centerX + random.nextInt(size - 24) - (size - 24) / 2;
                int pz = centerZ + random.nextInt(size - 24) - (size - 24) / 2;
                if (isNearSafeZone(px, pz, centerX, centerZ)) continue;

                int groundY = findCaveGroundY(level, px, pz, centerX, centerZ, halfSize);
                if (groundY < 0 || groundY - 2 <= FLOOR_Y_START) continue;

                // 先密封底部和四壁
                for (int dx = -pw / 2 - 1; dx <= pw / 2 + 1; dx++) {
                    for (int dz = -pd / 2 - 1; dz <= pd / 2 + 1; dz++) {
                        int x = px + dx;
                        int z = pz + dz;
                        if (Math.abs(x - centerX) >= halfSize - 3 || Math.abs(z - centerZ) >= halfSize - 3) continue;
                        boolean isEdge = Math.abs(dx) > pw / 2 || Math.abs(dz) > pd / 2;
                        // 底部密封
                        BlockPos bottom = new BlockPos(x, groundY - 2, z);
                        BlockState bs = level.getBlockState(bottom);
                        if (bs.isAir() || !bs.getFluidState().isEmpty()) {
                            level.setBlock(bottom, Blocks.PACKED_ICE.defaultBlockState(), 2);
                        }
                        // 外壁密封
                        if (isEdge) {
                            BlockPos wall = new BlockPos(x, groundY - 1, z);
                            BlockState ws = level.getBlockState(wall);
                            if (ws.isAir() || !ws.getFluidState().isEmpty()) {
                                level.setBlock(wall, Blocks.PACKED_ICE.defaultBlockState(), 2);
                            }
                        }
                    }
                }
                // 填水 + 冰盖
                for (int dx = -pw / 2; dx <= pw / 2; dx++) {
                    for (int dz = -pd / 2; dz <= pd / 2; dz++) {
                        int x = px + dx;
                        int z = pz + dz;
                        if (Math.abs(x - centerX) >= halfSize - 3 || Math.abs(z - centerZ) >= halfSize - 3) continue;
                        if (isNearSafeZone(x, z, centerX, centerZ)) continue;

                        // 下沉 1 格放水
                        BlockPos waterPos = new BlockPos(x, groundY - 1, z);
                        level.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 2);
                        // 水面放冰
                        BlockPos icePos = new BlockPos(x, groundY, z);
                        level.setBlock(icePos, Blocks.ICE.defaultBlockState(), 2);
                    }
                }
                // 密封检查：每个水方块的邻居若空气则补上
                for (int dx = -pw / 2; dx <= pw / 2; dx++) {
                    for (int dz = -pd / 2; dz <= pd / 2; dz++) {
                        int x = px + dx;
                        int z = pz + dz;
                        BlockPos waterPos = new BlockPos(x, groundY - 1, z);
                        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH,
                                                             Direction.EAST, Direction.WEST, Direction.DOWN}) {
                            BlockPos neighbor = waterPos.relative(dir);
                            if (level.getBlockState(neighbor).isAir()) {
                                level.setBlock(neighbor, Blocks.PACKED_ICE.defaultBlockState(), 2);
                            }
                        }
                    }
                }
            }
        }

        // ── 2. 地面石笋（POINTED_DRIPSTONE UP）── 稀疏散布
        for (int x = centerX - halfSize + 3; x < centerX + halfSize - 2; x++) {
            for (int z = centerZ - halfSize + 3; z < centerZ + halfSize - 2; z++) {
                if (random.nextFloat() >= 0.008f) continue; // 0.8% 密度
                if (isNearSafeZone(x, z, centerX, centerZ)) continue;

                int groundY = findCaveGroundY(level, x, z, centerX, centerZ, halfSize);
                if (groundY < 0) continue;

                BlockPos pos = new BlockPos(x, groundY, z);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                    level.setBlock(pos, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP), 2);
                }
            }
        }

        // ── 3. 冰柱（PACKED_ICE 柱，1-2根，1×1×3~5 高）──
        int pillarCount = 1 + random.nextInt(2);
        for (int p = 0; p < pillarCount; p++) {
            int px = centerX + random.nextInt(size - 20) - (size - 20) / 2;
            int pz = centerZ + random.nextInt(size - 20) - (size - 20) / 2;
            if (isNearSafeZone(px, pz, centerX, centerZ)) continue;

            int groundY = findCaveGroundY(level, px, pz, centerX, centerZ, halfSize);
            if (groundY < 0) continue;

            int height = 3 + random.nextInt(3); // 3-5 高
            for (int h = 0; h < height; h++) {
                BlockPos pos = new BlockPos(px, groundY + h, pz);
                if (pos.getY() >= FLOOR_Y_END - 1) break;
                BlockState current = level.getBlockState(pos);
                if (current.isAir() || isMainStone(current)) {
                    level.setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), 2);
                }
            }
        }
    }

    // ──────── P1-3: Lava段环境特色（80-119层）────────

    /**
     * Lava 段特色：
     * - 熔岩池（下沉坑底放 LAVA，1-3 个，周围 MAGMA_BLOCK）
     * - 岩浆裂缝（1格宽、5-12格长直线 LAVA 沟）
     * - 黑曜石柱（OBSIDIAN 柱，1-2根，1×1×3~6）
     */
    @SuppressWarnings("null")
    private static void generateLavaFeatures(ServerLevel level, RandomSource random,
                                              int centerX, int centerZ, int size, boolean isDark) {
        int halfSize = size / 2;

        // ── 1. 熔岩池 ──
        int poolCount = 1 + random.nextInt(3); // 1-3 个
        for (int p = 0; p < poolCount; p++) {
            int pw = 2 + random.nextInt(4); // 2-5
            int pd = 2 + random.nextInt(4);
            int px = centerX + random.nextInt(size - 30) - (size - 30) / 2;
            int pz = centerZ + random.nextInt(size - 30) - (size - 30) / 2;

            // 安全约束：距中心 > 15 格
            if (Math.abs(px - centerX) < 15 && Math.abs(pz - centerZ) < 15) continue;

            int groundY = findCaveGroundY(level, px, pz, centerX, centerZ, halfSize);
            if (groundY < 0 || groundY - 1 <= FLOOR_Y_START) continue;

            int placed = 0;
            for (int dx = -pw / 2; dx <= pw / 2; dx++) {
                for (int dz = -pd / 2; dz <= pd / 2; dz++) {
                    int x = px + dx;
                    int z = pz + dz;
                    if (Math.abs(x - centerX) >= halfSize - 3 || Math.abs(z - centerZ) >= halfSize - 3) continue;

                    BlockPos lavaPos = new BlockPos(x, groundY, z);
                    BlockPos belowLava = lavaPos.below();
                    if (level.getBlockState(lavaPos).isAir()
                        && !level.getBlockState(belowLava).isAir()) {
                        level.setBlock(lavaPos, Blocks.LAVA.defaultBlockState(), 2);
                        // 池底替换为 MAGMA_BLOCK
                        if (isMainStone(level.getBlockState(belowLava))) {
                            level.setBlock(belowLava, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                        }
                        placed++;
                    }

                    // 池边（外围一圈）放 MAGMA_BLOCK
                    boolean isEdge = Math.abs(dx) == pw / 2 || Math.abs(dz) == pd / 2;
                    if (isEdge) {
                        BlockPos edgeFloor = new BlockPos(x, groundY - 1, z);
                        if (isMainStone(level.getBlockState(edgeFloor))) {
                            level.setBlock(edgeFloor, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                        }
                    }
                }
            }
            if (placed > 0) {
                StardewCraft.LOGGER.debug("[MINE] Lava: placed lava pool {}x at ({}, {})", placed, px, pz);
            }
        }

        // ── 2. 岩浆裂缝（1格宽，5-12格长）──
        int crackCount = random.nextInt(3); // 0-2 条
        for (int c = 0; c < crackCount; c++) {
            int cx = centerX + random.nextInt(size - 30) - (size - 30) / 2;
            int cz = centerZ + random.nextInt(size - 30) - (size - 30) / 2;
            if (Math.abs(cx - centerX) < 15 && Math.abs(cz - centerZ) < 15) continue;

            int length = 5 + random.nextInt(8); // 5-12
            boolean horizontal = random.nextBoolean(); // true=沿X, false=沿Z

            int groundY = findCaveGroundY(level, cx, cz, centerX, centerZ, halfSize);
            if (groundY < 0 || groundY - 1 <= FLOOR_Y_START) continue;

            for (int i = 0; i < length; i++) {
                int x = horizontal ? cx + i : cx;
                int z = horizontal ? cz : cz + i;
                if (Math.abs(x - centerX) >= halfSize - 3 || Math.abs(z - centerZ) >= halfSize - 3) break;
                if (isNearSafeZone(x, z, centerX, centerZ)) break;

                // 找该列的地面
                int gy = findCaveGroundY(level, x, z, centerX, centerZ, halfSize);
                if (gy < 0 || gy - 1 <= FLOOR_Y_START) continue;

                BlockPos lavaPos = new BlockPos(x, gy, z);
                BlockPos belowPos = lavaPos.below();
                if (level.getBlockState(lavaPos).isAir() && !level.getBlockState(belowPos).isAir()) {
                    level.setBlock(lavaPos, Blocks.LAVA.defaultBlockState(), 2);
                }
            }
        }

        // ── 3. 黑曜石柱（1-2根，1×1×3~6）──
        int pillarCount = 1 + random.nextInt(2);
        for (int p = 0; p < pillarCount; p++) {
            int px = centerX + random.nextInt(size - 20) - (size - 20) / 2;
            int pz = centerZ + random.nextInt(size - 20) - (size - 20) / 2;
            if (isNearSafeZone(px, pz, centerX, centerZ)) continue;

            int groundY = findCaveGroundY(level, px, pz, centerX, centerZ, halfSize);
            if (groundY < 0) continue;

            int height = 3 + random.nextInt(4); // 3-6 高
            for (int h = 0; h < height; h++) {
                BlockPos pos = new BlockPos(px, groundY + h, pz);
                if (pos.getY() >= FLOOR_Y_END - 1) break;
                BlockState current = level.getBlockState(pos);
                if (current.isAir() || isMainStone(current)) {
                    level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 2);
                }
            }
        }
    }

    // ──────── P1-4: 骷髅矿洞环境特色（121+层）────────

    /**
     * 骷髅矿洞专属地形特色 — 远古沙漠地下遗迹 + 极端自然环境
     *
     * 特色 1: 熔岩裂隙网络（地面裂缝，内部流淌熔岩）
     * 特色 2: 沙岩柱林（地面到天花板的粗大石柱）
     * 特色 3: 骨骸化石层（墙壁中嵌入巨大骨骼化石）
     */
    @SuppressWarnings("null")
    private static void generateSkullCavernFeatures(ServerLevel level, RandomSource random,
                                                     int centerX, int centerZ, int size,
                                                     boolean isDark, int floorNumber) {
        int halfSize = size / 2;

        // ── 1. 熔岩裂隙网络（每层 1-3 条）──
        int fissureCount = 1 + random.nextInt(3);
        for (int f = 0; f < fissureCount; f++) {
            int startX = centerX + random.nextInt(size - 30) - (size - 30) / 2;
            int startZ = centerZ + random.nextInt(size - 30) - (size - 30) / 2;
            if (Math.abs(startX - centerX) < 15 && Math.abs(startZ - centerZ) < 15) continue;

            int steps = 15 + random.nextInt(16); // 15-30 步
            double angle = random.nextDouble() * Math.PI * 2;
            double cx = startX, cz = startZ;

            for (int s = 0; s < steps; s++) {
                int x = (int) cx;
                int z = (int) cz;
                if (Math.abs(x - centerX) >= halfSize - 3 || Math.abs(z - centerZ) >= halfSize - 3) break;
                if (isNearSafeZone(x, z, centerX, centerZ)) { cx += Math.cos(angle); cz += Math.sin(angle); continue; }

                int groundY = findCaveGroundY(level, x, z, centerX, centerZ, halfSize);
                if (groundY < 0 || groundY - 2 <= FLOOR_Y_START) { cx += Math.cos(angle); cz += Math.sin(angle); continue; }

                // 挖 1-2 格深的沟槽，底部放 MAGMA_BLOCK，上方放 LAVA
                int depth = 1 + random.nextInt(2);
                for (int d = 0; d < depth; d++) {
                    BlockPos digPos = new BlockPos(x, groundY - d, z);
                    if (level.getBlockState(digPos).getBlock() != ModBlocks.MINE_BARRIER.get()) {
                        if (d == depth - 1) {
                            level.setBlock(digPos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                        } else {
                            level.setBlock(digPos, Blocks.LAVA.defaultBlockState(), 2);
                        }
                    }
                }

                // 裂隙边缘用 sulfur_rock 装饰
                int fissureWidth = 1 + (random.nextInt(3) == 0 ? 1 : 0); // 大部分 1 格宽，偶尔 2 格
                if (fissureWidth > 1) {
                    int sideX = x + (random.nextBoolean() ? 1 : -1);
                    BlockPos sidePos = new BlockPos(sideX, groundY, z);
                    if (level.getBlockState(sidePos).isAir() || isMainStone(level.getBlockState(sidePos))) {
                        level.setBlock(sidePos, ModBlocks.SULFUR_ROCK.get().defaultBlockState(), 2);
                    }
                }

                // 转向 ±45°
                angle += (random.nextDouble() - 0.5) * Math.PI / 2;
                cx += Math.cos(angle);
                cz += Math.sin(angle);
            }
        }

        // ── 2. 沙岩柱林（每层 2-5 根）──
        int pillarCount = 2 + random.nextInt(4);
        for (int p = 0; p < pillarCount; p++) {
            int px = centerX + random.nextInt(size - 24) - (size - 24) / 2;
            int pz = centerZ + random.nextInt(size - 24) - (size - 24) / 2;
            if (isNearSafeZone(px, pz, centerX, centerZ)) continue;

            int groundY = findCaveGroundY(level, px, pz, centerX, centerZ, halfSize);
            if (groundY < 0) continue;

            // 找天花板高度
            int ceilingY = -1;
            for (int y = groundY + 1; y <= FLOOR_Y_END; y++) {
                if (!level.getBlockState(new BlockPos(px, y, pz)).isAir()) {
                    ceilingY = y;
                    break;
                }
            }
            if (ceilingY < 0 || ceilingY - groundY < 4) continue; // 至少 4 格高

            int pillarRadius = 1 + (random.nextInt(3) == 0 ? 1 : 0); // 大部分半径 1（3x3），偶尔 2（5x5）
            boolean broken = random.nextFloat() < 0.30f; // 30% 断裂柱
            int topY = broken ? groundY + (ceilingY - groundY) / 2 : ceilingY;

            for (int dy = 0; dy <= topY - groundY; dy++) {
                int y = groundY + dy;
                Block pillarBlock;
                if (dy <= 2) {
                    pillarBlock = Blocks.SANDSTONE; // 基座
                } else {
                    // 骷髅矿柱身：sulfur_rock（黄绿斑驳，沙漠火山感）
                    pillarBlock = ModBlocks.SULFUR_ROCK.get();
                }

                for (int dx = -pillarRadius; dx <= pillarRadius; dx++) {
                    for (int dz = -pillarRadius; dz <= pillarRadius; dz++) {
                        if (dx * dx + dz * dz > pillarRadius * pillarRadius + 1) continue;
                        BlockPos pos = new BlockPos(px + dx, y, pz + dz);
                        if (pos.getY() >= FLOOR_Y_END) break;
                        BlockState current = level.getBlockState(pos);
                        if (current.isAir() || isMainStone(current) || isDecorStone(current)) {
                            level.setBlock(pos, pillarBlock.defaultBlockState(), 2);
                        }
                    }
                }
            }

            // 断裂柱顶部散落碎石
            if (broken) {
                for (int i = 0; i < 3; i++) {
                    int sx = px + random.nextInt(3) - 1;
                    int sz = pz + random.nextInt(3) - 1;
                    BlockPos scatterPos = new BlockPos(sx, groundY + (topY - groundY) + 1, sz);
                    if (level.getBlockState(scatterPos).isAir()) {
                        level.setBlock(scatterPos, Blocks.SANDSTONE.defaultBlockState(), 2);
                    }
                }
            }
        }

        // ── 3. 骨骸化石层（每层 0-2 组）──
        int fossilCount = random.nextInt(3); // 0-2
        for (int f = 0; f < fossilCount; f++) {
            int fx = centerX + random.nextInt(size - 20) - (size - 20) / 2;
            int fz = centerZ + random.nextInt(size - 20) - (size - 20) / 2;
            if (isNearSafeZone(fx, fz, centerX, centerZ)) continue;

            int fy = FLOOR_Y_START + 3 + random.nextInt(Math.max(1, FLOOR_HEIGHT - 8));
            int boneCount = 3 + random.nextInt(5); // 3-7 块骨头

            // 弧形排列模拟肋骨/脊椎
            double startAngle = random.nextDouble() * Math.PI * 2;
            double angleStep = Math.PI / boneCount * 0.8;
            for (int b = 0; b < boneCount; b++) {
                double a = startAngle + b * angleStep;
                int bx = fx + (int)(Math.cos(a) * (2 + b * 0.5));
                int bz = fz + (int)(Math.sin(a) * (2 + b * 0.5));
                int by = fy + (b % 2 == 0 ? 0 : 1);

                BlockPos bonePos = new BlockPos(bx, by, bz);
                BlockState current = level.getBlockState(bonePos);
                if (isMainStone(current) || isDecorStone(current)) {
                    // Bone Block 带方向属性
                    BlockState boneState = Blocks.BONE_BLOCK.defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z);
                    level.setBlock(bonePos, boneState, 2);
                }
            }

            // 化石旁放置 SOUL_LANTERN（幽蓝灯光）
            BlockPos lanternPos = new BlockPos(fx, fy - 1, fz);
            if (level.getBlockState(lanternPos).isAir()) {
                BlockPos belowLantern = lanternPos.below();
                if (!level.getBlockState(belowLantern).isAir()) {
                    level.setBlock(lanternPos, Blocks.SOUL_LANTERN.defaultBlockState(), 2);
                }
            }
        }

        // ── 4. 悬空岩桥（Suspended Rock Bridge）——大型洞穴内横跨的窄石桥 ──
        if (random.nextFloat() < 0.60f) { // 60% 概率
            generateSuspendedBridge(level, random, centerX, centerZ, halfSize);
        }

        // ── 5. 沙瀑（Sand Fall）——天花板裂缝落沙效果 ──
        int sandFallCount = random.nextInt(3); // 0-2
        for (int sf = 0; sf < sandFallCount; sf++) {
            generateSandFall(level, random, centerX, centerZ, halfSize);
        }

        // ── 6. 毒气区域（Toxic Mist Zone）——每层 3-5 个 ──
        int toxicZoneCount = 3 + random.nextInt(3);
        for (int tz = 0; tz < toxicZoneCount; tz++) {
            generateToxicMistZone(level, random, centerX, centerZ, halfSize);
        }

        // ── 7. 流沙池（Quicksand Pools）——每层 5-8 个小池子 ──
        int quicksandCount = 5 + random.nextInt(4);
        for (int q = 0; q < quicksandCount; q++) {
            generateQuicksandPool(level, random, centerX, centerZ, halfSize);
        }
    }

    // ======================== 骷髅矿特殊地形方法 ========================

    /**
     * 悬空岩桥 — 在大型洞穴空间内横跨一座窄石桥（宽1-2格，长10-20格）。
     * 桥面使用砂岩台阶/石砖，桥下是深渊/熔岩，两端连接洞壁。
     * 营造"悬崖栈道"氛围，也是跨越大洞穴的捷径。
     */
    @SuppressWarnings("null")
    private static void generateSuspendedBridge(ServerLevel level, RandomSource random,
                                                  int centerX, int centerZ, int halfSize) {
        // 在洞穴空间中寻找两个对向的石壁 → 连一座桥
        int bridgeY = FLOOR_Y_START + 3 + random.nextInt(2); // 桥面高度
        boolean eastWest = random.nextBoolean(); // 桥的方向

        // 从洞穴中心偏移一段距离，寻找合适的起点
        int offset = 5 + random.nextInt(halfSize / 3);
        int sx, sz, ex, ez;
        if (eastWest) {
            sz = ez = centerZ + random.nextInt(halfSize / 2) - halfSize / 4;
            sx = centerX - offset;
            ex = centerX + offset;
        } else {
            sx = ex = centerX + random.nextInt(halfSize / 2) - halfSize / 4;
            sz = centerZ - offset;
            ez = centerZ + offset;
        }

        // 验证两端是实心石壁，中间大部分是空气
        if (isNearSafeZone(sx, sz, centerX, centerZ)) return;
        if (isNearSafeZone(ex, ez, centerX, centerZ)) return;
        if (Math.abs(sx - centerX) >= halfSize - 3 || Math.abs(sz - centerZ) >= halfSize - 3) return;
        if (Math.abs(ex - centerX) >= halfSize - 3 || Math.abs(ez - centerZ) >= halfSize - 3) return;

        // 检查起止点是否有实心方块可以"锚定"
        BlockPos startAnchor = new BlockPos(sx, bridgeY, sz);
        BlockPos endAnchor = new BlockPos(ex, bridgeY, ez);
        if (level.getBlockState(startAnchor).isAir() && level.getBlockState(startAnchor.below()).isAir()) return;
        if (level.getBlockState(endAnchor).isAir() && level.getBlockState(endAnchor.below()).isAir()) return;

        // 统计中间空气占比
        int airCount = 0;
        int totalCount = 0;
        int steps = (int) Math.sqrt((ex - sx) * (ex - sx) + (ez - sz) * (ez - sz));
        if (steps < 6) return; // 桥太短没意义

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int mx = (int) (sx + (ex - sx) * t);
            int mz = (int) (sz + (ez - sz) * t);
            if (level.getBlockState(new BlockPos(mx, bridgeY, mz)).isAir()) airCount++;
            totalCount++;
        }
        if (totalCount == 0 || (float) airCount / totalCount < 0.5f) return; // 中间不够空旷

        // 铺设桥面——全部用 unstable_rock（踩上 1.5s 后碎裂）
        Block bridgeBlock = ModBlocks.UNSTABLE_ROCK.get();
        int bridgeWidth = 1 + random.nextInt(2); // 1-2 格宽

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int mx = (int) (sx + (ex - sx) * t);
            int mz = (int) (sz + (ez - sz) * t);

            for (int w = -(bridgeWidth / 2); w <= bridgeWidth / 2; w++) {
                int bx = eastWest ? mx : mx + w;
                int bz = eastWest ? mz + w : mz;
                BlockPos bridgePos = new BlockPos(bx, bridgeY, bz);
                BlockState current = level.getBlockState(bridgePos);
                if (current.getBlock() == ModBlocks.MINE_BARRIER.get()) continue;

                // 清空桥面上方
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos above = bridgePos.above(dy);
                    BlockState aboveState = level.getBlockState(above);
                    if (!aboveState.isAir() && aboveState.getBlock() != ModBlocks.MINE_BARRIER.get()) {
                        level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
                    }
                }

                // 放置不稳定岩桥面
                level.setBlock(bridgePos, bridgeBlock.defaultBlockState(), 2);
            }
        }

        // 桥头两端放置火把/灯笼
        for (BlockPos anchor : new BlockPos[]{startAnchor, endAnchor}) {
            BlockPos torchPos = anchor.above();
            if (level.getBlockState(torchPos).isAir()) {
                level.setBlock(torchPos, Blocks.SOUL_LANTERN.defaultBlockState(), 2);
            }
        }

        StardewCraft.LOGGER.debug("[MINE] Generated suspended bridge from ({},{}) to ({},{})", sx, sz, ex, ez);
    }

    /**
     * 沙瀑 — 从天花板裂缝下落的沙柱。
     * 天花板开一条 1-2 格宽的裂缝，下方堆积 2-4 层沙子/红沙。
     * 纯装饰性地形，营造"沙漠地下"氛围。
     */
    @SuppressWarnings("null")
    private static void generateSandFall(ServerLevel level, RandomSource random,
                                          int centerX, int centerZ, int halfSize) {
        // 随机选择一个位置
        int fx = centerX + random.nextInt(halfSize) - halfSize / 2;
        int fz = centerZ + random.nextInt(halfSize) - halfSize / 2;
        if (isNearSafeZone(fx, fz, centerX, centerZ)) return;
        if (Math.abs(fx - centerX) >= halfSize - 4 || Math.abs(fz - centerZ) >= halfSize - 4) return;

        // 裂缝方向（东西或南北）
        boolean nsDir = random.nextBoolean();
        int length = 3 + random.nextInt(5); // 3-7 格长
        Block sandBlock = random.nextBoolean() ? Blocks.SAND : Blocks.RED_SAND;

        for (int i = 0; i < length; i++) {
            int sx = nsDir ? fx : fx + i;
            int sz = nsDir ? fz + i : fz;

            if (Math.abs(sx - centerX) >= halfSize - 3 || Math.abs(sz - centerZ) >= halfSize - 3) break;

            // 从天花板向下找到空气开始的位置
            int ceilingY = -1;
            for (int y = FLOOR_Y_END; y >= FLOOR_Y_START + 2; y--) {
                BlockPos pos = new BlockPos(sx, y, sz);
                if (!level.getBlockState(pos).isAir()) {
                    ceilingY = y;
                    break;
                }
            }
            if (ceilingY < 0) continue;

            // 天花板打开裂缝（1-2 格）
            for (int dy = 0; dy < 1 + random.nextInt(2); dy++) {
                BlockPos crackPos = new BlockPos(sx, ceilingY - dy, sz);
                BlockState crackState = level.getBlockState(crackPos);
                if (crackState.getBlock() != ModBlocks.MINE_BARRIER.get()) {
                    level.setBlock(crackPos, Blocks.AIR.defaultBlockState(), 2);
                }
            }

            // 地面堆积沙子（找到地面）
            for (int y = FLOOR_Y_START + 1; y < FLOOR_Y_END; y++) {
                BlockPos groundPos = new BlockPos(sx, y, sz);
                if (level.getBlockState(groundPos).isAir() && !level.getBlockState(groundPos.below()).isAir()) {
                    // 堆 1-3 格高的沙
                    int pileHeight = 1 + random.nextInt(3);
                    for (int ph = 0; ph < pileHeight; ph++) {
                        BlockPos sandPos = groundPos.above(ph);
                        if (level.getBlockState(sandPos).isAir()) {
                            level.setBlock(sandPos, sandBlock.defaultBlockState(), 2);
                        }
                    }
                    // 周围扩散 1 格（自然感）
                    if (random.nextFloat() < 0.5f) {
                        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                            BlockPos spread = groundPos.relative(dir);
                            if (level.getBlockState(spread).isAir() && !level.getBlockState(spread.below()).isAir()
                                    && random.nextFloat() < 0.4f) {
                                level.setBlock(spread, sandBlock.defaultBlockState(), 2);
                            }
                        }
                    }
                    break;
                }
            }

            // 在天花板与地面之间放置沙砾粒子方块（用 falling_dust 的视觉效果模拟）
            // 中间放置少量沙砾（空中漂浮的碎屑感）
            if (random.nextFloat() < 0.3f) {
                int midY = (ceilingY + FLOOR_Y_START) / 2;
                BlockPos midPos = new BlockPos(sx, midY, sz);
                if (level.getBlockState(midPos).isAir()) {
                    // 用蜘蛛网模拟落沙的"漫天飞尘"视觉
                    level.setBlock(midPos, Blocks.COBWEB.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * 毒气区域 — 一小片被有毒蒸气笼罩的区域。
     * 用大量蜘蛛网 + 绿色混凝土粉末 + 凋零玫瑰模拟毒雾效果。
     * 踩入区域会被凋零玫瑰施加 Wither 效果（MC 原版机制）。
     */
    @SuppressWarnings("null")
    private static void generateToxicMistZone(ServerLevel level, RandomSource random,
                                                int centerX, int centerZ, int halfSize) {
        int zoneR = 4 + random.nextInt(4); // 半径 4-7
        int zx = centerX + random.nextInt(halfSize / 2) - halfSize / 4;
        int zz = centerZ + random.nextInt(halfSize / 2) - halfSize / 4;
        if (isNearSafeZone(zx, zz, centerX, centerZ)) return;
        if (Math.abs(zx - centerX) + zoneR >= halfSize - 3) return;
        if (Math.abs(zz - centerZ) + zoneR >= halfSize - 3) return;

        for (int dx = -zoneR; dx <= zoneR; dx++) {
            for (int dz = -zoneR; dz <= zoneR; dz++) {
                if (dx * dx + dz * dz > zoneR * zoneR) continue;

                int px = zx + dx;
                int pz = zz + dz;

                // 安全区内绝不放置毒气孢子
                if (isNearSafeZone(px, pz, centerX, centerZ)) continue;

                // 找地面
                for (int y = FLOOR_Y_END - 1; y >= FLOOR_Y_START + 1; y--) {
                    BlockPos pos = new BlockPos(px, y, pz);
                    if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                        // 地面替换为 toxic_spore_block（接触/上方释放毒气，进入中毒）
                        BlockPos groundPos = pos.below();
                        BlockState groundState = level.getBlockState(groundPos);
                        if (groundState.getBlock() != ModBlocks.MINE_BARRIER.get()
                                && random.nextFloat() < 0.75f) {
                            level.setBlock(groundPos,
                                    ModBlocks.TOXIC_SPORE_BLOCK.get().defaultBlockState(), 2);
                        }
                        break;
                    }
                }
            }
        }

        // 区域中心放一个灵魂灯笼（提示"这里有东西"）
        if (isNearSafeZone(zx, zz, centerX, centerZ)) return;
        for (int y = FLOOR_Y_END - 1; y >= FLOOR_Y_START + 1; y--) {
            BlockPos center = new BlockPos(zx, y, zz);
            if (level.getBlockState(center).isAir() && !level.getBlockState(center.below()).isAir()) {
                level.setBlock(center, Blocks.SOUL_LANTERN.defaultBlockState(), 2);
                break;
            }
        }

        StardewCraft.LOGGER.debug("[MINE] Generated toxic mist zone at ({}, {}), radius={}", zx, zz, zoneR);
    }

    /**
     * 流沙池 — 地面一小片下沉区域，踩上即下陷+减速。
     * 3-5 格直径的圆形凹陷，地面方块替换为 QUICKSAND；四周散一些沙子做自然过渡。
     */
    @SuppressWarnings("null")
    private static void generateQuicksandPool(ServerLevel level, RandomSource random,
                                                 int centerX, int centerZ, int halfSize) {
        int r = 1 + random.nextInt(3); // 半径 1-3（直径 3-7）
        int qx = centerX + random.nextInt(halfSize * 2 - 8) - (halfSize - 4);
        int qz = centerZ + random.nextInt(halfSize * 2 - 8) - (halfSize - 4);
        if (isNearSafeZone(qx, qz, centerX, centerZ)) return;
        if (Math.abs(qx - centerX) + r >= halfSize - 3) return;
        if (Math.abs(qz - centerZ) + r >= halfSize - 3) return;

        BlockState quicksandState = ModBlocks.QUICKSAND.get().defaultBlockState();
        BlockState sandState = Blocks.SAND.defaultBlockState();

        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > (r + 1) * (r + 1)) continue;
                int px = qx + dx;
                int pz = qz + dz;

                // 找地面
                for (int y = FLOOR_Y_END - 1; y >= FLOOR_Y_START + 1; y--) {
                    BlockPos pos = new BlockPos(px, y, pz);
                    if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                        BlockPos groundPos = pos.below();
                        BlockState groundState = level.getBlockState(groundPos);
                        if (groundState.getBlock() == ModBlocks.MINE_BARRIER.get()) break;

                        if (distSq <= r * r) {
                            // 核心：直接替换地面为流沙
                            level.setBlock(groundPos, quicksandState, 2);
                        } else if (random.nextFloat() < 0.5f) {
                            // 外圈：铺一层沙做过渡
                            level.setBlock(groundPos, sandState, 2);
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * 远古武器室 — 封闭石室，中央石基上放置一件随机高级武器。
     * SDV 中骷髅矿偶有远古宝物房间，这是我们的 MC 版本。
     * 房间 7×7，石砖墙壁，中央有盔甲架展示武器，四角有灵魂灯笼。
     */
    @SuppressWarnings("null")
    private static void generateAncientWeaponRoom(ServerLevel level, RandomSource random,
                                                     int centerX, int centerZ, int halfSize, int floorNumber) {
        int roomW = 7;
        int roomD = 7;
        int roomH = 5;

        // 选择位置
        int rx = centerX + random.nextInt(halfSize / 2) - halfSize / 4;
        int rz = centerZ + random.nextInt(halfSize / 2) - halfSize / 4;
        if (isNearSafeZone(rx, rz, centerX, centerZ)) return;
        if (Math.abs(rx - centerX) + roomW / 2 >= halfSize - 3) return;
        if (Math.abs(rz - centerZ) + roomD / 2 >= halfSize - 3) return;

        int baseY = FLOOR_Y_START + 2;

        Block wallBlock = Blocks.DEEPSLATE_BRICKS;
        Block floorBlock = Blocks.DEEPSLATE_TILES;
        Block pillarBlock = Blocks.CHISELED_DEEPSLATE;

        // 构建房间
        for (int dx = -roomW / 2; dx <= roomW / 2; dx++) {
            for (int dz = -roomD / 2; dz <= roomD / 2; dz++) {
                boolean isWall = Math.abs(dx) == roomW / 2 || Math.abs(dz) == roomD / 2;
                boolean isCorner = Math.abs(dx) == roomW / 2 && Math.abs(dz) == roomD / 2;

                for (int dy = 0; dy < roomH; dy++) {
                    BlockPos pos = new BlockPos(rx + dx, baseY + dy, rz + dz);
                    BlockState current = level.getBlockState(pos);
                    if (current.getBlock() == ModBlocks.MINE_BARRIER.get()) continue;

                    if (dy == 0) {
                        // 地板
                        level.setBlock(pos, floorBlock.defaultBlockState(), 2);
                    } else if (dy == roomH - 1) {
                        // 天花板
                        level.setBlock(pos, wallBlock.defaultBlockState(), 2);
                    } else if (isCorner) {
                        // 四角柱子
                        level.setBlock(pos, pillarBlock.defaultBlockState(), 2);
                    } else if (isWall) {
                        // 墙壁
                        level.setBlock(pos, wallBlock.defaultBlockState(), 2);
                    } else {
                        // 内部空气
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 开一个门（随机一面墙的中间）
        int doorSide = random.nextInt(4);
        int doorX = rx, doorZ = rz;
        switch (doorSide) {
            case 0 -> doorX = rx - roomW / 2; // 西
            case 1 -> doorX = rx + roomW / 2; // 东
            case 2 -> doorZ = rz - roomD / 2; // 北
            case 3 -> doorZ = rz + roomD / 2; // 南
        }
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos doorPos = new BlockPos(doorX, baseY + dy, doorZ);
            level.setBlock(doorPos, Blocks.AIR.defaultBlockState(), 2);
        }

        // 中央石基（2格高的深板岩雕刻块 + 盔甲架）
        BlockPos pedestalBase = new BlockPos(rx, baseY + 1, rz);
        level.setBlock(pedestalBase, pillarBlock.defaultBlockState(), 2);

        // 在石基上放置盔甲架，手持随机武器
        BlockPos armorStandPos = new BlockPos(rx, baseY + 2, rz);
        level.setBlock(armorStandPos, Blocks.AIR.defaultBlockState(), 2);

        net.minecraft.world.entity.decoration.ArmorStand armorStand =
                new net.minecraft.world.entity.decoration.ArmorStand(level, rx + 0.5, baseY + 2, rz + 0.5);
        armorStand.setNoGravity(true);
        armorStand.setInvulnerable(true);
        armorStand.setShowArms(true);
        armorStand.setSilent(true);

        // 随机选择展示武器
        net.minecraft.world.item.ItemStack weapon = pickAncientWeapon(random, floorNumber);
        armorStand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);

        level.addFreshEntity(armorStand);

        // 四角灵魂灯笼
        int[][] corners = {{-roomW/2 + 1, -roomD/2 + 1}, {roomW/2 - 1, -roomD/2 + 1},
                           {-roomW/2 + 1, roomD/2 - 1}, {roomW/2 - 1, roomD/2 - 1}};
        for (int[] c : corners) {
            BlockPos lanternPos = new BlockPos(rx + c[0], baseY + 1, rz + c[1]);
            if (level.getBlockState(lanternPos).isAir()) {
                level.setBlock(lanternPos, Blocks.SOUL_LANTERN.defaultBlockState(), 2);
            }
        }

        StardewCraft.LOGGER.info("[MINE] Generated ancient weapon room at ({}, {}), floor {}", rx, rz, floorNumber);
    }

    /** 选择远古武器室的展示武器 */
    private static net.minecraft.world.item.ItemStack pickAncientWeapon(RandomSource random, int floorNumber) {
        // 深层有更好的武器
        if (floorNumber >= 160 && random.nextFloat() < 0.3f) {
            return new net.minecraft.world.item.ItemStack(com.stardew.craft.item.ModItems.LAVA_KATANA.get());
        }
        float roll = random.nextFloat();
        if (roll < 0.4f) {
            return new net.minecraft.world.item.ItemStack(com.stardew.craft.item.ModItems.OBSIDIAN_EDGE.get());
        } else if (roll < 0.7f) {
            return new net.minecraft.world.item.ItemStack(com.stardew.craft.item.ModItems.BONE_SWORD.get());
        } else {
            return new net.minecraft.world.item.ItemStack(com.stardew.craft.item.ModItems.OBSIDIAN_EDGE.get());
        }
    }

    // ======================== 可钓鱼地下水池 ========================

    /**
     * 生成一个下沉式椭圆水池（5×5~8×8，3格深），供玩家在矿洞内钓鱼。
     * 每层最多 1 个，概率 40%。Lava 段水池改用熔岩（可钓 Lava Eel）。
     *
     * 改进要点（解决水到处乱流）：
     * 1. 向下挖坑（下沉式），而非在地面上堆墙
     * 2. 四壁+底部用 2 格厚实心石密封
     * 3. 扫描密封壳周围，填补任何被洞穴挖穿的空洞
     * 4. 椭圆形池塘边缘 + 台阶过渡，更自然
     */
    @SuppressWarnings("null")
    private static void generateFishingPool(ServerLevel level, RandomSource random,
                                             int centerX, int centerZ, int size,
                                             FloorTheme theme, int floorNumber) {
        if (floorNumber == 0) return;
        if (random.nextFloat() >= 0.40f) return; // 40% 概率

        int halfSize = size / 2;
        int radiusX = 3 + random.nextInt(2); // 水面半径 3-4（直径 7-9）
        int radiusZ = 3 + random.nextInt(2);

        int maxAttempts = 30;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int px = centerX + random.nextInt(size - 30) - (size - 30) / 2;
            int pz = centerZ + random.nextInt(size - 30) - (size - 30) / 2;

            if (Math.abs(px - centerX) < 12 && Math.abs(pz - centerZ) < 12) continue;

            int groundY = findCaveGroundY(level, px, pz, centerX, centerZ, halfSize);
            if (groundY < 0 || groundY - 5 <= FLOOR_Y_START) continue;
            if (!level.getBlockState(new BlockPos(px, groundY + 1, pz)).isAir()) continue;
            if (Math.abs(px - centerX) + radiusX + 4 >= halfSize - 2) continue;
            if (Math.abs(pz - centerZ) + radiusZ + 4 >= halfSize - 2) continue;

            boolean isLava = (theme == FloorTheme.LAVA);
            BlockState fluid = isLava ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState();
            Block sealStone = getMainStone(theme, false);

            // ── 生成不规则噪声偏移表（每个角度不同的半径扰动） ──
            // 让池塘边缘看起来像自然形成的水洼而不是完美椭圆
            double[] edgeNoise = new double[64];
            for (int i = 0; i < 64; i++) {
                edgeNoise[i] = 0.7 + random.nextDouble() * 0.6; // 0.7-1.3 倍半径扰动
            }

            // ── 第一步：确定每个格子的角色（水/浅水/岸边/密封） ──
            // 渐进深度：中心最深(3格)，边缘浅(1格)
            int sealThickness = 2;
            int scanR = Math.max(radiusX, radiusZ) + sealThickness + 2;

            // 先构建密封壳
            for (int dx = -scanR; dx <= scanR; dx++) {
                for (int dz = -scanR; dz <= scanR; dz++) {
                    int x = px + dx;
                    int z = pz + dz;
                    double noiseFactor = sampleEdgeNoise(edgeNoise, dx, dz);
                    double normDist = getEllipDist(dx, dz, radiusX, radiusZ) / (noiseFactor * noiseFactor);
                    boolean insidePool = normDist < 1.0;
                    boolean inSealZone = getEllipDist(dx, dz, radiusX + sealThickness, radiusZ + sealThickness) < 1.0;

                    if (!insidePool && inSealZone) {
                        // 密封壳区域
                        for (int dy = -4; dy <= 0; dy++) {
                            int y = groundY + dy;
                            if (y <= FLOOR_Y_START || y >= FLOOR_Y_END) continue;
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState current = level.getBlockState(pos);
                            if (current.isAir() || !current.getFluidState().isEmpty()) {
                                level.setBlock(pos, sealStone.defaultBlockState(), 2);
                            }
                        }
                    } else if (insidePool) {
                        // 池底密封
                        int depth = getPoolDepth(normDist);
                        for (int dy = -depth - sealThickness; dy < -depth; dy++) {
                            int y = groundY + dy;
                            if (y <= FLOOR_Y_START) continue;
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState current = level.getBlockState(pos);
                            if (current.isAir() || !current.getFluidState().isEmpty()) {
                                level.setBlock(pos, sealStone.defaultBlockState(), 2);
                            }
                        }
                    }
                }
            }

            // ── 第二步：挖坑 + 填水（渐进深度） ──
            int placed = 0;
            for (int dx = -radiusX - 1; dx <= radiusX + 1; dx++) {
                for (int dz = -radiusZ - 1; dz <= radiusZ + 1; dz++) {
                    int x = px + dx;
                    int z = pz + dz;
                    double noiseFactor = sampleEdgeNoise(edgeNoise, dx, dz);
                    double normDist = getEllipDist(dx, dz, radiusX, radiusZ) / (noiseFactor * noiseFactor);
                    if (normDist >= 1.0) continue;
                    if (isNearSafeZone(x, z, centerX, centerZ)) continue;

                    // 渐进深度：中心 3 格深，边缘 1 格深
                    int depth = getPoolDepth(normDist);

                    for (int dy = -depth; dy <= 0; dy++) {
                        int y = groundY + dy;
                        if (y <= FLOOR_Y_START) continue;
                        level.setBlock(new BlockPos(x, y, z), fluid, 2);
                    }
                    placed++;

                    // 清空水面上方
                    for (int above = 1; above <= 2; above++) {
                        BlockPos abovePos = new BlockPos(x, groundY + above, z);
                        BlockState aboveSt = level.getBlockState(abovePos);
                        if (!aboveSt.isAir() && aboveSt.getFluidState().isEmpty()
                            && aboveSt.getBlock() != ModBlocks.MINE_BARRIER.get()) {
                            level.setBlock(abovePos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }

            // ── 第三步：自然岸边过渡 ──
            for (int dx = -(radiusX + 3); dx <= radiusX + 3; dx++) {
                for (int dz = -(radiusZ + 3); dz <= radiusZ + 3; dz++) {
                    int x = px + dx;
                    int z = pz + dz;
                    double noiseFactor = sampleEdgeNoise(edgeNoise, dx, dz);
                    double normDist = getEllipDist(dx, dz, radiusX, radiusZ) / (noiseFactor * noiseFactor);

                    // 岸边过渡区（紧邻水面外围 1-2 格）
                    if (normDist >= 1.0 && normDist < 1.8) {
                        BlockPos groundPos = new BlockPos(x, groundY, z);
                        BlockState gs = level.getBlockState(groundPos);
                        if (gs.isAir() || gs.getBlock() == ModBlocks.MINE_BARRIER.get()) continue;

                        // 岸边地面替换为主题性方块
                        if (isLava) {
                            // 熔岩池岸边：岩浆块 + 黑曜石碎片
                            if (normDist < 1.3) {
                                level.setBlock(groundPos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                            } else if (random.nextFloat() < 0.4f) {
                                level.setBlock(groundPos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                            }
                        } else if (theme == FloorTheme.FROST) {
                            // 冰池岸边：浮冰 + 雪
                            if (normDist < 1.3) {
                                level.setBlock(groundPos, Blocks.PACKED_ICE.defaultBlockState(), 2);
                            }
                            // 岸边上方偶尔放雪
                            BlockPos aboveGround = groundPos.above();
                            if (level.getBlockState(aboveGround).isAir() && random.nextFloat() < 0.3f) {
                                level.setBlock(aboveGround, Blocks.SNOW.defaultBlockState(), 2);
                            }
                        } else {
                            // 土段水池岸边：苔藓砂岩 + 苔藓地毯 + 碎石台阶
                            if (normDist < 1.3) {
                                level.setBlock(groundPos, ModBlocks.MOSSY_SANDSTONE.get().defaultBlockState(), 2);
                            } else if (random.nextFloat() < 0.3f) {
                                level.setBlock(groundPos, ModBlocks.MOSSY_SANDSTONE.get().defaultBlockState(), 2);
                            }
                            // 岸边上方放苔藓地毯/小植物
                            BlockPos aboveGround = groundPos.above();
                            if (level.getBlockState(aboveGround).isAir()) {
                                float plantRoll = random.nextFloat();
                                if (plantRoll < 0.15f) {
                                    level.setBlock(aboveGround, Blocks.MOSS_CARPET.defaultBlockState(), 2);
                                } else if (plantRoll < 0.22f) {
                                    level.setBlock(aboveGround, Blocks.BROWN_MUSHROOM.defaultBlockState(), 2);
                                }
                            }
                        }

                        // 边缘有概率放半砖形成坡面（所有主题通用）
                        if (normDist >= 1.0 && normDist < 1.2 && random.nextFloat() < 0.35f) {
                            BlockPos slabPos = new BlockPos(x, groundY + 1, z);
                            if (level.getBlockState(slabPos).isAir()) {
                                // 用主题对应的台阶
                                BlockState slab = getThemeSlab(theme);
                                if (slab != null) {
                                    level.setBlock(slabPos, slab, 2);
                                }
                            }
                        }
                    }
                }
            }

            // ── 第四步：最终密封检查 ──
            for (int dx = -(radiusX + 1); dx <= radiusX + 1; dx++) {
                for (int dz = -(radiusZ + 1); dz <= radiusZ + 1; dz++) {
                    int x = px + dx;
                    int z = pz + dz;
                    double noiseFactor = sampleEdgeNoise(edgeNoise, dx, dz);
                    double normDist = getEllipDist(dx, dz, radiusX, radiusZ) / (noiseFactor * noiseFactor);
                    if (normDist >= 1.0) continue;

                    int depth = getPoolDepth(normDist);
                    for (int dy = -depth; dy <= 0; dy++) {
                        BlockPos waterPos = new BlockPos(x, groundY + dy, z);
                        for (Direction dir : Direction.values()) {
                            BlockPos neighbor = waterPos.relative(dir);
                            BlockState ns = level.getBlockState(neighbor);
                            if (ns.isAir()) {
                                if (dir == Direction.UP && dy == 0) continue;
                                level.setBlock(neighbor, sealStone.defaultBlockState(), 2);
                            }
                        }
                    }
                }
            }

            if (placed > 0) {
                StardewCraft.LOGGER.debug("[MINE] Placed fishing pool ({}x, {}) at ({}, {}, {})",
                    placed, isLava ? "lava" : "water", px, groundY, pz);
            }
            break;
        }
    }

    /** 椭圆距离 (归一化，< 1.0 = 在内) */
    private static double getEllipDist(int dx, int dz, int rx, int rz) {
        return (double)(dx * dx) / ((rx + 0.5) * (rx + 0.5))
             + (double)(dz * dz) / ((rz + 0.5) * (rz + 0.5));
    }

    /** 渐进深度：中心 3 格，中间 2 格，边缘 1 格 */
    private static int getPoolDepth(double normDist) {
        if (normDist < 0.25) return 3;       // 中心区
        if (normDist < 0.6) return 2;        // 中间区
        return 1;                             // 边缘浅水区
    }

    /** 采样不规则边缘噪声（按角度索引） */
    private static double sampleEdgeNoise(double[] noise, int dx, int dz) {
        double angle = Math.atan2(dz, dx);
        // 映射 [-π, π] → [0, 63]
        int idx = (int)((angle + Math.PI) / (2 * Math.PI) * 63.99);
        idx = Math.max(0, Math.min(63, idx));
        // 平滑插值相邻两个采样点
        int next = (idx + 1) % 64;
        double frac = ((angle + Math.PI) / (2 * Math.PI) * 63.99) - idx;
        return noise[idx] * (1 - frac) + noise[next] * frac;
    }

    /** 主题台阶方块（用于水池边缘坡面） */
    @SuppressWarnings("null")
    private static BlockState getThemeSlab(FloorTheme theme) {
        switch (theme) {
            case EARTH:
                return Blocks.COBBLESTONE_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            case FROST:
                return Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            case LAVA:
                return Blocks.BLACKSTONE_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            default:
                return null;
        }
    }

    // ======================== P2-1: 特殊房间 ========================

    /**
     * 每层有概率生成一个特殊洞穴室：
     * - 蘑菇房（Earth+Frost, 8%）
     * - 矿石密集区（全段, 10%）
     * - 骨骸房（Lava 80+层, 5%）
     */
    @SuppressWarnings("null")
    private static void generateSpecialRoom(ServerLevel level, RandomSource random,
                                             int centerX, int centerZ, int size,
                                             FloorTheme theme, boolean isDark, int floorNumber) {
        if (floorNumber == 0 || floorNumber % 5 == 0) return; // boss 层不生成

        int halfSize = size / 2;
        float roll = random.nextFloat();

        if (theme == FloorTheme.SKULL_CAVERN) {
            float iridiumRoomChance = getSkullCavernIridiumRoomChance(floorNumber);
            // 骷髅矿专属特殊房间
            if (roll < iridiumRoomChance) {
                // 铱矿密集洞：深层后才稳定出现
                generateIridiumTreasureRoom(level, random, centerX, centerZ, halfSize, floorNumber);
            } else if (roll < iridiumRoomChance + 0.08f) {
                // 岩浆湖洞穴（8%）
                generateLavaChamber(level, random, centerX, centerZ, halfSize, floorNumber);
            } else if (roll < iridiumRoomChance + 0.11f && floorNumber >= 146) {
                // Dino 巢穴（3%，≥146层）
                generateDinoNest(level, random, centerX, centerZ, halfSize);
            } else if (roll < iridiumRoomChance + 0.16f) {
                // 远古武器室（5%）——封闭石室，中央武器展示架
                generateAncientWeaponRoom(level, random, centerX, centerZ, halfSize, floorNumber);
            }
            return;
        }

        if (roll < 0.10f) {
            // 矿石密集区（10%） — 全段
            generateRichVeinChamber(level, random, centerX, centerZ, halfSize, theme, floorNumber);
        } else if (roll < 0.18f && (theme == FloorTheme.EARTH || theme == FloorTheme.FROST)) {
            // 蘑菇房（8%） — Earth + Frost
            generateMushroomRoom(level, random, centerX, centerZ, halfSize, theme);
        } else if (roll < 0.23f && theme == FloorTheme.LAVA) {
            // 骨骸房（5%） — Lava
            generateBoneChamber(level, random, centerX, centerZ, halfSize);
        }
    }

    /** 蘑菇房：中型方形房间，地面 MYCELIUM + 蘑菇方块 */
    @SuppressWarnings("null")
    private static void generateMushroomRoom(ServerLevel level, RandomSource random,
                                              int centerX, int centerZ, int halfSize,
                                              FloorTheme theme) {
        int roomW = 8 + random.nextInt(5); // 8-12
        int roomD = 8 + random.nextInt(5);
        int roomH = 5 + random.nextInt(3); // 5-7

        int rx = centerX + random.nextInt(halfSize) - halfSize / 2;
        int rz = centerZ + random.nextInt(halfSize) - halfSize / 2;
        if (isNearSafeZone(rx, rz, centerX, centerZ)) return;
        if (Math.abs(rx - centerX) + roomW / 2 >= halfSize - 3) return;
        if (Math.abs(rz - centerZ) + roomD / 2 >= halfSize - 3) return;

        int baseY = FLOOR_Y_START + 2;

        // 挖空房间
        for (int dx = -roomW / 2; dx <= roomW / 2; dx++) {
            for (int dz = -roomD / 2; dz <= roomD / 2; dz++) {
                for (int dy = 0; dy < roomH; dy++) {
                    BlockPos pos = new BlockPos(rx + dx, baseY + dy, rz + dz);
                    BlockState current = level.getBlockState(pos);
                    if (!current.isAir() && current.getBlock() != ModBlocks.MINE_BARRIER.get()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                // 地面替换为菌丝
                BlockPos floorPos = new BlockPos(rx + dx, baseY - 1, rz + dz);
                if (!level.getBlockState(floorPos).isAir()
                    && level.getBlockState(floorPos).getBlock() != ModBlocks.MINE_BARRIER.get()) {
                    level.setBlock(floorPos, Blocks.MYCELIUM.defaultBlockState(), 2);
                }
            }
        }

        // 可选蘑菇方块列表（项目自有蘑菇 forage blocks）
        Block[] mushroomBlocks = {
            ModBlocks.FORAGE_COMMON_MUSHROOM.get(),
            ModBlocks.FORAGE_RED_MUSHROOM.get(),
            ModBlocks.FORAGE_PURPLE_MUSHROOM.get(),
            ModBlocks.FORAGE_MOREL.get(),
            ModBlocks.FORAGE_CHANTERELLE.get(),
            ModBlocks.FORAGE_MAGMA_CAP.get()
        };

        // 地面蘑菇植物（4-8 个）
        int plantCount = 4 + random.nextInt(5);
        for (int p = 0; p < plantCount; p++) {
            int px = rx - roomW / 2 + 1 + random.nextInt(roomW - 2);
            int pz = rz - roomD / 2 + 1 + random.nextInt(roomD - 2);
            BlockPos pos = new BlockPos(px, baseY, pz);
            if (level.getBlockState(pos).isAir()
                && level.getBlockState(pos.below()).is(Blocks.MYCELIUM)) {
                Block plant = mushroomBlocks[random.nextInt(mushroomBlocks.length)];
                level.setBlock(pos, plant.defaultBlockState(), 2);
            }
        }

        // 天花板嵌入 SHROOMLIGHT（2 个）
        for (int s = 0; s < 2; s++) {
            int sx = rx - roomW / 2 + 2 + random.nextInt(roomW - 4);
            int sz = rz - roomD / 2 + 2 + random.nextInt(roomD - 4);
            BlockPos ceilPos = new BlockPos(sx, baseY + roomH, sz);
            if (!level.getBlockState(ceilPos).isAir()) {
                level.setBlock(ceilPos, Blocks.SHROOMLIGHT.defaultBlockState(), 3);
            }
        }

        StardewCraft.LOGGER.debug("[MINE] Generated mushroom room at ({}, {})", rx, rz);
    }

    /** 矿石密集区：小型洞穴，墙壁 30-50% 替换为该楼层主矿石 */
    @SuppressWarnings("null")
    private static void generateRichVeinChamber(ServerLevel level, RandomSource random,
                                                 int centerX, int centerZ, int halfSize,
                                                 FloorTheme theme, int floorNumber) {
        int roomSize = 6 + random.nextInt(3); // 6-8
        int rx = centerX + random.nextInt(halfSize) - halfSize / 2;
        int rz = centerZ + random.nextInt(halfSize) - halfSize / 2;
        if (isNearSafeZone(rx, rz, centerX, centerZ)) return;
        if (Math.abs(rx - centerX) + roomSize / 2 >= halfSize - 3) return;
        if (Math.abs(rz - centerZ) + roomSize / 2 >= halfSize - 3) return;

        int baseY = FLOOR_Y_START + 2;
        int roomH = 4 + random.nextInt(3); // 4-6

        // 用多椭球挖出不规则空间
        int blobCount = 3 + random.nextInt(3);
        for (int b = 0; b < blobCount; b++) {
            int bx = rx + random.nextInt(5) - 2;
            int bz = rz + random.nextInt(5) - 2;
            int by = baseY + random.nextInt(2);
            int radius = 3 + random.nextInt(2);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -radius / 2; dy <= radius / 2; dy++) {
                        double dist = (dx * dx + dz * dz) / (double)(radius * radius)
                                    + (dy * dy) / (double)((radius / 2 + 1) * (radius / 2 + 1));
                        if (dist > 1.0) continue;
                        BlockPos pos = new BlockPos(bx + dx, by + dy, bz + dz);
                        if (pos.getY() <= FLOOR_Y_START || pos.getY() >= FLOOR_Y_END) continue;
                        if (Math.abs(pos.getX() - centerX) >= halfSize - 2) continue;
                        if (Math.abs(pos.getZ() - centerZ) >= halfSize - 2) continue;
                        BlockState current = level.getBlockState(pos);
                        if (!current.isAir() && current.getBlock() != ModBlocks.MINE_BARRIER.get()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }

        // 扫描空气方块的邻居，将 30-50% 的石头墙替换为矿石
        String oreKey = pickPrimaryOreForFloor(floorNumber);
        Block oreBlock = getOreBlock(theme, oreKey);
        float oreChance = 0.30f + random.nextFloat() * 0.20f; // 30-50%

        for (int dx = -(roomSize / 2 + 2); dx <= roomSize / 2 + 2; dx++) {
            for (int dz = -(roomSize / 2 + 2); dz <= roomSize / 2 + 2; dz++) {
                for (int dy = 0; dy < roomH + 2; dy++) {
                    BlockPos pos = new BlockPos(rx + dx, baseY + dy, rz + dz);
                    if (!level.getBlockState(pos).isAir()) continue;

                    // 检查六个邻居
                    for (Direction dir : Direction.values()) {
                        BlockPos neighbor = pos.relative(dir);
                        BlockState ns = level.getBlockState(neighbor);
                        if ((isMainStone(ns) || isDecorStone(ns)) && random.nextFloat() < oreChance) {
                            level.setBlock(neighbor, oreBlock.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }

        // 内壁点缀紫水晶簇
        for (int i = 0; i < 4 + random.nextInt(4); i++) {
            int ax = rx - roomSize / 2 + random.nextInt(roomSize);
            int az = rz - roomSize / 2 + random.nextInt(roomSize);
            int ay = baseY + random.nextInt(roomH);
            BlockPos pos = new BlockPos(ax, ay, az);
            if (level.getBlockState(pos).isAir()) {
                // 找一个有石头邻居的面
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = pos.relative(dir);
                    if (isMainStone(level.getBlockState(neighbor))) {
                        level.setBlock(pos, Blocks.AMETHYST_CLUSTER.defaultBlockState(), 2);
                        break;
                    }
                }
            }
        }

        StardewCraft.LOGGER.debug("[MINE] Generated rich vein chamber ({}) at ({}, {})", oreKey, rx, rz);
    }

    /** 骨骸房：Lava 段，地面散布 BONE_BLOCK + SKELETON_SKULL + SOUL_LANTERN */
    @SuppressWarnings("null")
    private static void generateBoneChamber(ServerLevel level, RandomSource random,
                                             int centerX, int centerZ, int halfSize) {
        int roomW = 8 + random.nextInt(5); // 8-12
        int roomD = 8 + random.nextInt(5);
        int roomH = 5 + random.nextInt(3);

        int rx = centerX + random.nextInt(halfSize) - halfSize / 2;
        int rz = centerZ + random.nextInt(halfSize) - halfSize / 2;
        if (isNearSafeZone(rx, rz, centerX, centerZ)) return;
        if (Math.abs(rx - centerX) + roomW / 2 >= halfSize - 3) return;
        if (Math.abs(rz - centerZ) + roomD / 2 >= halfSize - 3) return;

        int baseY = FLOOR_Y_START + 2;

        // 挖空房间
        for (int dx = -roomW / 2; dx <= roomW / 2; dx++) {
            for (int dz = -roomD / 2; dz <= roomD / 2; dz++) {
                for (int dy = 0; dy < roomH; dy++) {
                    BlockPos pos = new BlockPos(rx + dx, baseY + dy, rz + dz);
                    BlockState current = level.getBlockState(pos);
                    if (!current.isAir() && current.getBlock() != ModBlocks.MINE_BARRIER.get()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 地面散布 BONE_BLOCK（4-8 个）
        int boneCount = 4 + random.nextInt(5);
        for (int b = 0; b < boneCount; b++) {
            int bx = rx - roomW / 2 + 1 + random.nextInt(roomW - 2);
            int bz = rz - roomD / 2 + 1 + random.nextInt(roomD - 2);
            BlockPos pos = new BlockPos(bx, baseY, bz);
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.BONE_BLOCK.defaultBlockState(), 2);
            }
        }

        // 头颅（1-2 个）
        for (int s = 0; s < 1 + random.nextInt(2); s++) {
            int sx = rx - roomW / 2 + 2 + random.nextInt(roomW - 4);
            int sz = rz - roomD / 2 + 2 + random.nextInt(roomD - 4);
            BlockPos pos = new BlockPos(sx, baseY, sz);
            if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                level.setBlock(pos, Blocks.SKELETON_SKULL.defaultBlockState(), 2);
            }
        }

        // SOUL_LANTERN 照明（2-3 个）
        for (int l = 0; l < 2 + random.nextInt(2); l++) {
            int lx = rx - roomW / 2 + 1 + random.nextInt(roomW - 2);
            int lz = rz - roomD / 2 + 1 + random.nextInt(roomD - 2);
            BlockPos pos = new BlockPos(lx, baseY, lz);
            if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                level.setBlock(pos, Blocks.SOUL_LANTERN.defaultBlockState(), 3);
            }
        }

        StardewCraft.LOGGER.debug("[MINE] Generated bone chamber at ({}, {})", rx, rz);
    }

    // ──────── 骷髅矿专属特殊房间 ────────

    private static float getSkullCavernIridiumRoomChance(int floorNumber) {
        int skullLevel = floorNumber - 120;
        if (skullLevel < 30) {
            return 0.0f;
        }
        return (float)Math.min(0.035, 0.01 + (skullLevel - 30) * 0.00025);
    }

    /** 铱矿密集洞：小型洞穴，墙壁少量高密铱矿石 */
    @SuppressWarnings("null")
    private static void generateIridiumTreasureRoom(ServerLevel level, RandomSource random,
                                                      int centerX, int centerZ, int halfSize, int floorNumber) {
        int roomW = 6 + random.nextInt(4); // 6-9
        int roomD = 6 + random.nextInt(4);
        int roomH = 4 + random.nextInt(3); // 4-6

        int rx = centerX + random.nextInt(halfSize) - halfSize / 2;
        int rz = centerZ + random.nextInt(halfSize) - halfSize / 2;
        if (isNearSafeZone(rx, rz, centerX, centerZ)) return;
        if (Math.abs(rx - centerX) + roomW / 2 >= halfSize - 3) return;
        if (Math.abs(rz - centerZ) + roomD / 2 >= halfSize - 3) return;

        int baseY = FLOOR_Y_START + 2;
        Block iridiumOre = ModBlocks.DESERT_IRIDIUM_ORE.get();
        Block mainStone = LAVA_BASALT;

        // 挖空房间，墙壁替换为铱矿 + 主石头混合
        for (int dx = -roomW / 2; dx <= roomW / 2; dx++) {
            for (int dz = -roomD / 2; dz <= roomD / 2; dz++) {
                boolean isWall = Math.abs(dx) >= roomW / 2 - 1 || Math.abs(dz) >= roomD / 2 - 1;
                for (int dy = 0; dy < roomH; dy++) {
                    BlockPos pos = new BlockPos(rx + dx, baseY + dy, rz + dz);
                    BlockState current = level.getBlockState(pos);
                    if (current.getBlock() == ModBlocks.MINE_BARRIER.get()) continue;

                    if (isWall && dy > 0 && dy < roomH - 1) {
                        // 墙壁保留“深层奖励”感，但不再是半个房间都刷铱矿
                        level.setBlock(pos, random.nextFloat() < 0.32f ? iridiumOre.defaultBlockState() : mainStone.defaultBlockState(), 2);
                    } else if (!isWall) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 地面放几颗铱矿碎块
        for (int i = 0; i < 2 + random.nextInt(2); i++) {
            int ix = rx - roomW / 2 + 2 + random.nextInt(Math.max(1, roomW - 4));
            int iz = rz - roomD / 2 + 2 + random.nextInt(Math.max(1, roomD - 4));
            BlockPos orePos = new BlockPos(ix, baseY, iz);
            if (level.getBlockState(orePos).isAir() && !level.getBlockState(orePos.below()).isAir()) {
                level.setBlock(orePos, iridiumOre.defaultBlockState(), 2);
            }
        }

        // SOUL_LANTERN 照明
        BlockPos lightPos = new BlockPos(rx, baseY, rz);
        if (level.getBlockState(lightPos).isAir()) {
            level.setBlock(lightPos, Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }

        StardewCraft.LOGGER.debug("[MINE] Generated iridium treasure room at ({}, {})", rx, rz);
    }

    /** 岩浆湖洞穴：大型开阔洞穴，中央是巨大熔岩湖，周围有矿石环 */
    @SuppressWarnings("null")
    private static void generateLavaChamber(ServerLevel level, RandomSource random,
                                             int centerX, int centerZ, int halfSize, int floorNumber) {
        int roomR = 8 + random.nextInt(5); // 半径 8-12
        int roomH = 6 + random.nextInt(3); // 6-8

        int rx = centerX + random.nextInt(halfSize / 2) - halfSize / 4;
        int rz = centerZ + random.nextInt(halfSize / 2) - halfSize / 4;
        if (isNearSafeZone(rx, rz, centerX, centerZ)) return;
        if (Math.abs(rx - centerX) + roomR >= halfSize - 3) return;
        if (Math.abs(rz - centerZ) + roomR >= halfSize - 3) return;

        int baseY = FLOOR_Y_START + 2;
        int lavaR = roomR - 3; // 熔岩湖比房间小 3 格

        // 挖空椭圆房间
        for (int dx = -roomR; dx <= roomR; dx++) {
            for (int dz = -roomR; dz <= roomR; dz++) {
                if (dx * dx + dz * dz > roomR * roomR) continue;
                for (int dy = 0; dy < roomH; dy++) {
                    BlockPos pos = new BlockPos(rx + dx, baseY + dy, rz + dz);
                    BlockState current = level.getBlockState(pos);
                    if (current.getBlock() != ModBlocks.MINE_BARRIER.get()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                // 中央熔岩湖
                if (dx * dx + dz * dz <= lavaR * lavaR) {
                    BlockPos lavaPos = new BlockPos(rx + dx, baseY, rz + dz);
                    level.setBlock(lavaPos, Blocks.LAVA.defaultBlockState(), 2);
                    // 熔岩底部
                    BlockPos belowPos = lavaPos.below();
                    if (level.getBlockState(belowPos).getBlock() != ModBlocks.MINE_BARRIER.get()) {
                        level.setBlock(belowPos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                    }
                } else if (dx * dx + dz * dz > (lavaR + 1) * (lavaR + 1)) {
                    // 外围矿石环
                    if (random.nextFloat() < 0.25f) {
                        BlockPos orePos = new BlockPos(rx + dx, baseY + 1 + random.nextInt(2), rz + dz);
                        if (level.getBlockState(orePos).isAir()) {
                            String oreKey = random.nextFloat() < 0.4f ? "iridium" : "gold";
                            level.setBlock(orePos, getOreBlock(FloorTheme.SKULL_CAVERN, oreKey).defaultBlockState(), 2);
                        }
                    }
                }
            }
        }

        StardewCraft.LOGGER.debug("[MINE] Generated lava chamber at ({}, {})", rx, rz);
    }

    /** Dino 巢穴：菌丝地面 + 骨头装饰 */
    @SuppressWarnings("null")
    private static void generateDinoNest(ServerLevel level, RandomSource random,
                                          int centerX, int centerZ, int halfSize) {
        int roomW = 8 + random.nextInt(4); // 8-11
        int roomD = 8 + random.nextInt(4);
        int roomH = 5 + random.nextInt(3);

        int rx = centerX + random.nextInt(halfSize) - halfSize / 2;
        int rz = centerZ + random.nextInt(halfSize) - halfSize / 2;
        if (isNearSafeZone(rx, rz, centerX, centerZ)) return;
        if (Math.abs(rx - centerX) + roomW / 2 >= halfSize - 3) return;
        if (Math.abs(rz - centerZ) + roomD / 2 >= halfSize - 3) return;

        int baseY = FLOOR_Y_START + 2;

        // 挖空房间
        for (int dx = -roomW / 2; dx <= roomW / 2; dx++) {
            for (int dz = -roomD / 2; dz <= roomD / 2; dz++) {
                for (int dy = 0; dy < roomH; dy++) {
                    BlockPos pos = new BlockPos(rx + dx, baseY + dy, rz + dz);
                    BlockState current = level.getBlockState(pos);
                    if (!current.isAir() && current.getBlock() != ModBlocks.MINE_BARRIER.get()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                // 地面替换为菌丝
                BlockPos floorPos = new BlockPos(rx + dx, baseY - 1, rz + dz);
                if (!level.getBlockState(floorPos).isAir()
                    && level.getBlockState(floorPos).getBlock() != ModBlocks.MINE_BARRIER.get()) {
                    level.setBlock(floorPos, Blocks.MYCELIUM.defaultBlockState(), 2);
                }
            }
        }

        // 骨头装饰
        for (int b = 0; b < 4 + random.nextInt(4); b++) {
            int bx = rx - roomW / 2 + 1 + random.nextInt(Math.max(1, roomW - 2));
            int bz = rz - roomD / 2 + 1 + random.nextInt(Math.max(1, roomD - 2));
            BlockPos pos = new BlockPos(bx, baseY, bz);
            if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                level.setBlock(pos, Blocks.BONE_BLOCK.defaultBlockState(), 2);
            }
        }

        // SOUL_LANTERN 照明（2个）
        for (int l = 0; l < 2; l++) {
            int lx = rx + (l == 0 ? -roomW / 3 : roomW / 3);
            int lz = rz + random.nextInt(3) - 1;
            BlockPos pos = new BlockPos(lx, baseY, lz);
            if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                level.setBlock(pos, Blocks.SOUL_LANTERN.defaultBlockState(), 3);
            }
        }

        StardewCraft.LOGGER.debug("[MINE] Generated dino nest at ({}, {})", rx, rz);
    }

    /** 根据楼层返回该层主要矿石类型 */
    private static String pickPrimaryOreForFloor(int floor) {
        if (floor >= 80) return "gold";
        if (floor >= 40) return "iron";
        return "copper";
    }

    // ======================== P2-2: 环境装饰 ========================

    /**
     * 环境装饰散布（木桶之后）：
     * - 骨头碎片（Lava + Deep Earth 30+层）
     * - 矿车轨道残片（Earth 20+层）
     * - 小型篝火（每 3 层 15% 概率）
     */
    @SuppressWarnings("null")
    private static void generateEnvironmentDecor(ServerLevel level, RandomSource random,
                                                  int centerX, int centerZ, int size,
                                                  FloorTheme theme, boolean isDark, int floorNumber) {
        if (floorNumber == 0) return;
        int halfSize = size / 2;

        // ── 1. 骨头碎片（Lava段 或 Earth 30+层）──
        if (theme == FloorTheme.LAVA || (theme == FloorTheme.EARTH && floorNumber >= 30)) {
            int boneCount = random.nextInt(4); // 0-3
            for (int b = 0; b < boneCount; b++) {
                int bx = centerX + random.nextInt(size - 20) - (size - 20) / 2;
                int bz = centerZ + random.nextInt(size - 20) - (size - 20) / 2;
                if (isNearSafeZone(bx, bz, centerX, centerZ)) continue;

                int groundY = findCaveGroundY(level, bx, bz, centerX, centerZ, halfSize);
                if (groundY < 0) continue;

                BlockPos pos = new BlockPos(bx, groundY, bz);
                if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                    level.setBlock(pos, Blocks.BONE_BLOCK.defaultBlockState(), 2);
                }
            }
        }

        // ── 2. 矿车轨道残片（Earth 20+层，每层最多 1 条）──
        if (theme == FloorTheme.EARTH && floorNumber >= 20 && random.nextFloat() < 0.3f) {
            int tx = centerX + random.nextInt(size - 24) - (size - 24) / 2;
            int tz = centerZ + random.nextInt(size - 24) - (size - 24) / 2;
            if (!isNearSafeZone(tx, tz, centerX, centerZ)) {
                int groundY = findCaveGroundY(level, tx, tz, centerX, centerZ, halfSize);
                if (groundY >= 0) {
                    int length = 3 + random.nextInt(4); // 3-6 格
                    boolean horizontal = random.nextBoolean();
                    for (int i = 0; i < length; i++) {
                        int x = horizontal ? tx + i : tx;
                        int z = horizontal ? tz : tz + i;
                        if (Math.abs(x - centerX) >= halfSize - 3 || Math.abs(z - centerZ) >= halfSize - 3) break;

                        int gy = findCaveGroundY(level, x, z, centerX, centerZ, halfSize);
                        if (gy < 0) break;

                        BlockPos railPos = new BlockPos(x, gy, z);
                        if (level.getBlockState(railPos).isAir() && !level.getBlockState(railPos.below()).isAir()) {
                            level.setBlock(railPos, Blocks.RAIL.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }

        // ── 3. 小型篝火（每 3 层 15% 概率）──
        if (floorNumber % 3 == 0 && random.nextFloat() < 0.15f) {
            int maxAttempts = 15;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                int cx = centerX + random.nextInt(size - 20) - (size - 20) / 2;
                int cz = centerZ + random.nextInt(size - 20) - (size - 20) / 2;
                if (isNearSafeZone(cx, cz, centerX, centerZ)) continue;

                int groundY = findCaveGroundY(level, cx, cz, centerX, centerZ, halfSize);
                if (groundY < 0) continue;

                BlockPos campPos = new BlockPos(cx, groundY, cz);
                BlockPos above1 = campPos.above();
                BlockPos above2 = campPos.above(2);
                // 需要 3 格垂直空间
                if (!level.getBlockState(campPos).isAir()) continue;
                if (!level.getBlockState(above1).isAir()) continue;
                if (!level.getBlockState(above2).isAir()) continue;
                if (level.getBlockState(campPos.below()).isAir()) continue;

                // 放置篝火
                Block campfire = (theme == FloorTheme.LAVA) ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE;
                level.setBlock(campPos, campfire.defaultBlockState(), 2);

                StardewCraft.LOGGER.debug("[MINE] Placed campfire at ({}, {}, {})", cx, groundY, cz);
                break;
            }
        }
    }

    /** 地面装饰 — stone 是地面石头，air 是上方空气 */
    @SuppressWarnings("null")
    private static void decorateFloorSurface(ServerLevel level, RandomSource random,
                                              BlockPos stone, BlockPos air, FloorTheme theme) {
        float roll = random.nextFloat();
        switch (theme) {
            case EARTH:
                if (roll < 0.06f) {
                    level.setBlock(stone, ModBlocks.MOSSY_SANDSTONE.get().defaultBlockState(), 2);
                } else if (roll < 0.09f && level.getBlockState(air).isAir()) {
                    level.setBlock(air, Blocks.MOSS_CARPET.defaultBlockState(), 2);
                }
                break;
            case FROST:
                if (roll < 0.05f && level.getBlockState(air).isAir()) {
                    level.setBlock(air, Blocks.SNOW.defaultBlockState(), 2);
                } else if (roll < 0.08f) {
                    level.setBlock(stone, Blocks.PACKED_ICE.defaultBlockState(), 2);
                }
                break;
            case LAVA:
                if (roll < 0.05f) {
                    level.setBlock(stone, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                } else if (roll < 0.08f) {
                    level.setBlock(stone, Blocks.NETHERRACK.defaultBlockState(), 2);
                }
                break;
            default:
                break;
        }
    }

    /** 天花板装饰 — stone 是天花板石头，air 是下方空气 */
    @SuppressWarnings("null")
    private static void decorateCeilingSurface(ServerLevel level, RandomSource random,
                                                BlockPos stone, BlockPos air, FloorTheme theme) {
        float roll = random.nextFloat();
        switch (theme) {
            case EARTH:
                if (roll < 0.03f && level.getBlockState(air).isAir()) {
                    level.setBlock(air, Blocks.HANGING_ROOTS.defaultBlockState(), 2);
                } else if (roll < 0.05f && level.getBlockState(air).isAir()) {
                    level.setBlock(air, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN), 2);
                }
                break;
            case FROST:
                if (roll < 0.03f && level.getBlockState(air).isAir()) {
                    level.setBlock(air, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN), 2);
                } else if (roll < 0.05f) {
                    level.setBlock(stone, Blocks.PACKED_ICE.defaultBlockState(), 2);
                }
                break;
            case LAVA:
                if (roll < 0.03f && level.getBlockState(air).isAir()) {
                    level.setBlock(air, Blocks.CHAIN.defaultBlockState(), 2);
                } else if (roll < 0.05f) {
                    level.setBlock(stone, Blocks.SHROOMLIGHT.defaultBlockState(), 3);
                }
                break;
            default:
                break;
        }
    }

    /** 墙面装饰 — stone 是墙面石头，air 是侧面空气 */
    @SuppressWarnings("null")
    private static void decorateWallSurface(ServerLevel level, RandomSource random,
                                             BlockPos stone, BlockPos air, FloorTheme theme, boolean isDark) {
        float roll = random.nextFloat();

        // 计算从 air 指向 stone 的方向（MultifaceBlock 贴面方向）
        Direction faceDir = Direction.getNearest(
                stone.getX() - air.getX(),
                stone.getY() - air.getY(),
                stone.getZ() - air.getZ());

        // Dark层额外暗黑纹路
        if (isDark && roll < 0.02f && level.getBlockState(air).isAir()) {
            level.setBlock(air, Blocks.SCULK_VEIN.defaultBlockState()
                    .setValue(MultifaceBlock.getFaceProperty(faceDir), true), 2);
            return;
        }

        switch (theme) {
            case EARTH:
                if (roll < 0.04f) {
                    level.setBlock(stone, ModBlocks.CRACKED_SLATE.get().defaultBlockState(), 2);
                } else if (roll < 0.06f && level.getBlockState(air).isAir()) {
                    level.setBlock(air, Blocks.GLOW_LICHEN.defaultBlockState()
                            .setValue(MultifaceBlock.getFaceProperty(faceDir), true), 2);
                }
                break;
            case FROST:
                if (roll < 0.03f) {
                    level.setBlock(stone, ModBlocks.SALT_ROCK.get().defaultBlockState(), 2);
                } else if (roll < 0.06f) {
                    level.setBlock(stone, Blocks.BLUE_ICE.defaultBlockState(), 2);
                }
                break;
            case LAVA:
                if (roll < 0.04f) {
                    level.setBlock(stone, ModBlocks.SCORIA.get().defaultBlockState(), 2);
                } else if (roll < 0.06f) {
                    level.setBlock(stone, Blocks.NETHERRACK.defaultBlockState(), 2);
                }
                break;
            default:
                break;
        }
    }

    private static boolean isInSafeZone(int x, int y, int z, int centerX, int centerZ) {
        return Math.abs(x - centerX) <= SAFE_ZONE_RADIUS + 1 && Math.abs(z - centerZ) <= SAFE_ZONE_RADIUS + 1 && y >= FLOOR_Y_START && y <= SAFE_ZONE_Y_END + 2;
    }

    // ─────────────────────────────────────────────────────────────
    //  Floor 121 骷髅矿入口大厅（skullkeyentrance.schem）
    // ─────────────────────────────────────────────────────────────
    /** skem 大小 13(X) × 6(Y) × 7(Z) */
    public static final BlockPos SKULL_CAVERN_LOBBY_ORIGIN = new BlockPos(-6, 64, 24211);
    /** 玩家进入位置 = origin + (3, 1, 3) */
    public static final BlockPos SKULL_CAVERN_LOBBY_SPAWN =
            SKULL_CAVERN_LOBBY_ORIGIN.offset(3, 1, 3);
    private static final String SKULL_CAVERN_LOBBY_SCHEM = "data/stardewcraft/structures/skullkeyentrance.schem";
    private static final String SKULL_CAVERN_EXIT_MARKER = "sdv_portal_marker:skull_cavern_exit";
    private static final String SKULL_CAVERN_EXIT_TARGET = "sdv_portal_target:skull_cavern_exit";

    /**
     * 生成 floor 121 骷髅矿入口大厅：
     * - 放置 skullkeyentrance.schem
     * - 在 origin + (1, 1~2, 2~3) 放置 1×2×2 传送触发方块（返回沙漠）
     */
    @SuppressWarnings("null")
    private static void generateSkullCavernLobby(ServerLevel level) {
        MineFloorDataManager manager = MineFloorDataManager.get(level);
        if (!manager.needsGeneration(121)) {
            StardewCraft.LOGGER.info("[SKULL] Lobby (floor 121) already generated today, skipping");
            return;
        }
        manager.clearFloorData(121);

        StardewCraft.LOGGER.info("[SKULL] Generating skull cavern lobby at {}", SKULL_CAVERN_LOBBY_ORIGIN);

        // 1. 放置 schem
        StructureLoader.loadAndPlaceWithResult(level, SKULL_CAVERN_LOBBY_SCHEM, SKULL_CAVERN_LOBBY_ORIGIN);

        // 2. 放置 1×2×2 portal trigger：X=origin+1, Y=origin+1..+2, Z=origin+2..+3
        BlockPos portalBase = SKULL_CAVERN_LOBBY_ORIGIN.offset(1, 1, 2);
        com.stardew.craft.interior.InteriorSubspaceManager.placePortalTriggerArea(
                level, portalBase, 2 /*height*/, 1 /*xBlocks*/, 2 /*zBlocks*/,
                SKULL_CAVERN_EXIT_MARKER, SKULL_CAVERN_EXIT_TARGET);

        // 3. 标记生成完成
        manager.markGenerated(121);

        StardewCraft.LOGGER.info("[SKULL] Skull cavern lobby generated");
    }
    
    /**
     * 生成中心安全区
     * 
     * 结构：
     * - 3×3×3 空气区域（Y=65,66,67三层，X和Z各±1）
     * - 玩家背后（北侧 Z-1）：1×3×1 封印石墙（Y=65..67）
     * - 封印石墙南侧：3个 mine_exit（每格一个）
     * - 玩家面向南（朝向exit）
     */
    @SuppressWarnings("null")
    private static void generateSafeZone(ServerLevel level, int centerX, int centerZ, int floorNumber) {
        // 1. 清空3×3×3区域（Y=65,66,67三层，X和Z各±1）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = SAFE_ZONE_Y_START; y <= SAFE_ZONE_Y_END; y++) {
                    level.setBlock(new BlockPos(centerX + dx, y, centerZ + dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        
        // 2. 玩家背后（北侧 Z-1）放置1×3×1封印石墙
        Block barrier = ModBlocks.MINE_BARRIER.get();
        int wallZ = centerZ - 1; // 北侧
        for (int y = SAFE_ZONE_Y_START; y <= SAFE_ZONE_Y_END; y++) {
            level.setBlock(new BlockPos(centerX, y, wallZ), barrier.defaultBlockState(), 2);
        }
        
        // 3. 在封印石墙南侧（玩家面向方向）放置3个mine_exit（每格一个）
        // exit依附在北侧墙上，FACING=SOUTH表示面朝南（墙在exit的北侧）
        Block exit = ModBlocks.MINE_EXIT.get();
        for (int y = SAFE_ZONE_Y_START; y <= SAFE_ZONE_Y_END; y++) {
            BlockPos exitPos = new BlockPos(centerX, y, centerZ);
            @SuppressWarnings("null")
            BlockState exitState = exit.defaultBlockState()
                .setValue(com.stardew.craft.block.mine.MineExitBlock.FACING, Direction.SOUTH); // 面朝南，依附北墙
            level.setBlock(exitPos, exitState, 2);
        }

        // 4. 电梯（每 5 层放一个，0层由结构放置；骷髅矿无电梯，只能靠楼梯下）
        if (floorNumber > 0 && floorNumber <= 120 && floorNumber % 5 == 0) {
            Block elevator = ModBlocks.ELEVATOR.get();
            BlockPos elevatorPos = new BlockPos(centerX + 1, SAFE_ZONE_Y_START, wallZ); // 封印石旁边一格，地面高度
            @SuppressWarnings("null")
            BlockState elevatorState = elevator.defaultBlockState()
                .setValue(com.stardew.craft.block.mine.ElevatorBlock.FACING, Direction.SOUTH);
            level.setBlock(elevatorPos, elevatorState, 2);
        }
        
    StardewCraft.LOGGER.info("[MINE] Generated safe zone (3×3×3) with 3 exits at center ({}, {}, {})", centerX, SAFE_ZONE_Y_START, centerZ);

        // 5. 宝箱层：在安全区南侧（玩家面前）放置矿井宝箱
        if (MineChestLootTable.isChestFloor(floorNumber)) {
            placeMineChests(level, centerX, centerZ, floorNumber);
        }
    }

    /**
     * 在宝箱楼层放置一个或多个矿井宝箱。
     * <p>
     * - 普通楼层：1 个宝箱，位于 (centerX, Y, centerZ+1)
     * - 骷髅矿井宝藏室 220：1 个（主位）
     * - 骷髅矿井宝藏室 320：2 个（主位 +1X / 主位 -1X）
     * - 骷髅矿井宝藏室 420：3 个（主位 / 主位 -2X / 主位 +2X）
     * 对齐 SDV MineShaft::addLevelChests 的 chestSpot 偏移。
     */
    @SuppressWarnings("null")
    private static void placeMineChests(ServerLevel level, int centerX, int centerZ, int floorNumber) {
        Block chestBlock = com.stardew.craft.block.ModBlocks.MINE_CHEST.get();
        int chestZ = centerZ + 1;
        int[] xOffsets;
        if (MineChestLootTable.isSkullCavernTreasureFloor(floorNumber)) {
            xOffsets = switch (floorNumber) {
                case 220 -> new int[] { 0 };
                case 320 -> new int[] { -1, 1 };
                case 420 -> new int[] { -2, 0, 2 };
                default -> new int[] { 0 };
            };
        } else {
            xOffsets = new int[] { 0 };
        }
        for (int dx : xOffsets) {
            BlockPos chestPos = new BlockPos(centerX + dx, SAFE_ZONE_Y_START, chestZ);
            // 清空宝箱位及其上下各一格，避免挨着矿墙无法打开
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlock(chestPos.above(dy), Blocks.AIR.defaultBlockState(), 2);
            }
            // 宝箱下方铺一块固体方块，防止掉落 / 确保视觉一致
            level.setBlock(chestPos.below(),
                    getMainStone(getThemeForFloor(floorNumber), false).defaultBlockState(), 2);
            BlockState chestState = chestBlock.defaultBlockState()
                    .setValue(com.stardew.craft.block.mine.MineChestBlock.FACING, Direction.NORTH);
            level.setBlock(chestPos, chestState, 2);
            StardewCraft.LOGGER.info("[MINE] Placed mine chest on floor {} at {}", floorNumber, chestPos);
        }
    }
    
    /**
     * 根据楼层确定主题
     */
    private static FloorTheme getThemeForFloor(int floor) {
        StardewMineThemeDefinition definition = MineThemeData.forFloor(floor);
        if (definition != null) {
            try {
                return FloorTheme.valueOf(definition.mechanicId().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                StardewCraft.LOGGER.error("[MINE] Unknown terrain mechanic {} for floor {}",
                        definition.mechanicId(), floor);
            }
        }
        return FloorTheme.EARTH;
    }
    
    /**
     * 获取主题对应的主石头
     */
    private static Block getMainStone(FloorTheme theme, boolean isDark) {
        StardewMineThemeDefinition definition = themeDefinition(theme);
        if (definition != null) {
            return block(isDark ? definition.darkStone() : definition.mainStone(), ModBlocks.EARTH_SHALE.get());
        }
        return ModBlocks.EARTH_SHALE.get();
    }
    
    /**
     * 获取装饰石头（按主题和用途选择）
     * 按照 MINING_REGISTRATION_CHECKLIST.md 的用途说明：
     * - banded_marble: 冰段洞窟装饰、宝箱房地面
     * - limestone: 土段洞窟、靠近木桶/矿车区域
     * - mossy_sandstone: 土段过渡、隐藏房间的潮湿角落
     * - cracked_slate: 三段通用，适合边缘/墙面阴影区
     * - scoria: 熔岩段洞窟、火山气息装饰
     * - salt_rock: 冰段、特殊洞窟标志材
     */
    private static Block getDecorativeStone(FloorTheme theme, RandomSource random) {
        List<Block> blocks = blocks(themeDefinition(theme), StardewMineThemeDefinition::decorativeStones);
        return blocks.isEmpty() ? ModBlocks.CRACKED_SLATE.get() : blocks.get(random.nextInt(blocks.size()));
    }
    
    /**
     * 获取原版方块点缀（按主题选择，占比最低）
     */
    private static Block getVanillaBlock(FloorTheme theme, RandomSource random) {
        List<Block> blocks = blocks(themeDefinition(theme), StardewMineThemeDefinition::vanillaAccents);
        return blocks.isEmpty() ? Blocks.STONE : blocks.get(random.nextInt(blocks.size()));
    }
    
    /**
     * 获取洞窟装饰石头（用于洞窟边缘装饰）
     */
    private static List<Block> getCaveDecorationBlocks(FloorTheme theme) {
        List<Block> result = blocks(themeDefinition(theme), StardewMineThemeDefinition::caveDecorations);
        return result.isEmpty() ? List.of(ModBlocks.CRACKED_SLATE.get()) : result;
    }
    
    /**
     * 获取矿石方块（按主题选择）
     */
    private static Block getOreBlock(FloorTheme theme, String oreType) {
        StardewMineThemeDefinition definition = themeDefinition(theme);
        if (definition != null) {
            ResourceLocation id = definition.ores().getOrDefault(oreType, definition.ores().get("copper"));
            return block(id, ModBlocks.EARTH_COPPER_ORE.get());
        }
        return ModBlocks.EARTH_COPPER_ORE.get();
    }

    private static StardewMineThemeDefinition themeDefinition(FloorTheme theme) {
        return MineThemeData.forMechanic(theme.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static List<Block> blocks(
            StardewMineThemeDefinition definition,
            java.util.function.Function<StardewMineThemeDefinition, List<ResourceLocation>> selector
    ) {
        if (definition == null) return List.of();
        return selector.apply(definition).stream()
                .filter(BuiltInRegistries.BLOCK::containsKey)
                .map(BuiltInRegistries.BLOCK::get)
                .toList();
    }

    private static Block block(ResourceLocation id, Block fallback) {
        return id != null && BuiltInRegistries.BLOCK.containsKey(id)
                ? BuiltInRegistries.BLOCK.get(id)
                : fallback;
    }
    
    // ======================== barrel generation ========================

    /**
     * 在洞窟空间中生成木桶（SDV BreakableContainer）
     *
     * 生成规则（仿 SDV MineShaft.populateLevel）：
     * - 基础数量：0-4 个
     * - 仅在洞窟地面（脚下是实体方块、当前是空气、上方也是空气）放置
     * - 避开中心安全区
     * - 普通矿井不在 boss 层（floorNumber % 5 == 0）生成，骷髅矿洞按原版仍可生成
     * - 沙漠节骷髅矿洞中，Calico Statue 会替换一个符合条件的木桶候选点
     */
    @SuppressWarnings("null")
    private static void generateBarrels(ServerLevel level, RandomSource random,
                                        int centerX, int centerZ, int size, int floorNumber) {
        int halfSize = size / 2;
        // SDV 原版: mineRandom.Next(5) + (int)(AverageDailyLuck * 20)
        // SDV 房间约 40×30=1200 格；我们房间 80~120 边长，面积 6400~14400 格
        // 按面积比例放大：base = SDV_base * (size*size) / 1200
        // SDV base 平均 2 → 我们 base = 2 * area/1200，再加随机浮动
        int area = size * size;
        int scaledBase = Math.max(3, area / 600);         // 80×80→10, 100×100→16, 120×120→24
        int barrelCount = scaledBase + random.nextInt(scaledBase / 2 + 1); // 10~15, 16~24, 24~36

        int placed = 0;
        int maxAttempts = barrelCount * 40; // 防止无限循环
        boolean shouldPlaceCalicoStatue = shouldPlaceCalicoStatueOnThisFloor(level, random, floorNumber);
        boolean placedCalicoStatue = false;

        for (int attempt = 0; attempt < maxAttempts && placed < barrelCount; attempt++) {
            int x = centerX - halfSize + 2 + random.nextInt(size - 4);
            int z = centerZ - halfSize + 2 + random.nextInt(size - 4);

            // 避开中心安全区
            if (Math.abs(x - centerX) <= SAFE_ZONE_RADIUS + SAFE_ZONE_BUFFER + 1
                && Math.abs(z - centerZ) <= SAFE_ZONE_RADIUS + SAFE_ZONE_BUFFER + 1) {
                continue;
            }

            // 从洞窟高度向下扫描找到地面
            for (int y = FLOOR_Y_END - 1; y >= FLOOR_Y_START + 1; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockPos below = pos.below();
                BlockPos above = pos.above();

                // 需要：当前是空气、下方是实心石材（非装饰块）、上方是空气
                BlockState belowState = level.getBlockState(below);
                if (level.getBlockState(pos).isAir()
                    && !belowState.isAir()
                    && belowState.isSolidRender(level, below)
                    && level.getBlockState(above).isAir()) {

                    if (shouldPlaceCalicoStatue && canPlaceCalicoStatue(level, pos)) {
                        placeCalicoStatue(level, pos, floorNumber);
                        shouldPlaceCalicoStatue = false;
                        placedCalicoStatue = true;
                    } else {
                        level.setBlock(pos, com.stardew.craft.block.ModBlocks.MINE_BARREL.get().defaultBlockState(), 2);
                    }
                    placed++;
                    break;
                }
            }
        }

        if (placed > 0) {
            StardewCraft.LOGGER.debug("[MINE] Placed {} barrels in room at ({}, {})", placed, centerX, centerZ);
        }
        if (placedCalicoStatue) {
            StardewCraft.LOGGER.debug("[MINE] Replaced one barrel candidate with Calico Statue on floor {}", floorNumber);
        }
    }

    private static boolean shouldPlaceCalicoStatueOnThisFloor(ServerLevel level, RandomSource random, int floorNumber) {
        return floorNumber >= 121
            && com.stardew.craft.festival.desert.DesertFestivalMineService.isActive()
            && random.nextBoolean();
    }

    private static boolean canPlaceCalicoStatue(ServerLevel level, BlockPos pos) {
        BlockPos backdrop = pos.north();
        return level.getBlockState(pos).isAir()
            && level.getBlockState(pos.above()).isAir()
            && level.getBlockState(pos.below()).isSolidRender(level, pos.below())
            && level.getBlockState(backdrop).isSolidRender(level, backdrop);
    }

    private static void placeCalicoStatue(ServerLevel level, BlockPos pos, int floorNumber) {
        BlockState statueState = ModBlocks.CALICO_STATUE.get().defaultBlockState()
            .setValue(CalicoStatueBlock.FACING, Direction.SOUTH)
            .setValue(CalicoStatueBlock.PART, TallMasteryBlock.Part.MAIN)
            .setValue(CalicoStatueBlock.ACTIVATED, false);
        level.setBlock(pos, statueState, 2);
        level.setBlock(pos.above(), statueState.setValue(CalicoStatueBlock.PART, TallMasteryBlock.Part.EXTENSION), 2);
        StardewCraft.LOGGER.debug("[MINE] Placed Calico Statue on floor {} at {}", floorNumber, pos);
    }

    /**
     * 楼层主题枚举
     */
    public enum FloorTheme {
        EARTH,          // 1-39层：土段
        FROST,          // 40-79层：冰段
        LAVA,           // 80-119层：熔岩段
        SUMMIT,         // 120层：顶峰
        SKULL_CAVERN    // 121+层：骷髅矿洞
    }

    /**
     * 洞窟形状枚举 — P0-2 多形状 Carver
     */
    enum CaveShape {
        TUNNEL,           // 虫蚀球链隧道
        RECTANGULAR_ROOM, // 方形/矩形空间（带圆角）
        L_CORRIDOR,       // L形走廊
        RAVINE,           // 窄缝裂隙（高而窄）
        IRREGULAR_CAVE    // 不规则洞穴（多椭球叠加）
    }

    // ======================== Perlin Noise ========================

    /**
     * 简易 2D Perlin 噪声实现（用于地质带分布）
     * 值域约 [-1, 1]
     */
    private static final class PerlinNoise {
        // 预计算梯度向量（8方向）
        private static final double[][] GRAD2 = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
        };

        static double noise2D(double x, double y, long seed) {
            // 网格坐标
            int xi = (int) Math.floor(x);
            int yi = (int) Math.floor(y);
            // 小数部分
            double xf = x - xi;
            double yf = y - yi;
            // Fade 曲线 (6t^5 - 15t^4 + 10t^3)
            double u = fade(xf);
            double v = fade(yf);
            // 四角梯度
            double n00 = grad(hash(xi, yi, seed), xf, yf);
            double n10 = grad(hash(xi + 1, yi, seed), xf - 1, yf);
            double n01 = grad(hash(xi, yi + 1, seed), xf, yf - 1);
            double n11 = grad(hash(xi + 1, yi + 1, seed), xf - 1, yf - 1);
            // 双线性插值
            double nx0 = n00 + u * (n10 - n00);
            double nx1 = n01 + u * (n11 - n01);
            return nx0 + v * (nx1 - nx0);
        }

        private static double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        private static double grad(int hash, double x, double y) {
            double[] g = GRAD2[hash & 7];
            return g[0] * x + g[1] * y;
        }

        private static int hash(int x, int y, long seed) {
            long h = seed;
            h ^= x * 0x9E3779B97F4A7C15L;
            h ^= y * 0x6C62272E07BB0142L;
            h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
            h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
            return (int) ((h ^ (h >>> 31)) & 0x7FFFFFFF);
        }
    }
}
