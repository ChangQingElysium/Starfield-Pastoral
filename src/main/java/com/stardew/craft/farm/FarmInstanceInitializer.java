package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.api.v1.farm.StardewFarmInitializationSteps;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutMigrations;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家个人农场实例的初始化器。
 * 放置 schematic → 设置生物群系 → 放置图腾柱 → 温室 → 原版地图密度的碎片 → 出口实体。
 * <p>
 * 初始生态以原版每张农场 TMX 的 Paths 层作为统计样本：按粗粒度区域提取树种、
 * 杂物和牧草的密度及构成，再根据 Minecraft 地图中该区域实际存在的泥土、草地、
 * 深色草地与沙地重新计算数量并生成不规则生态簇。不会逐格放大原版点阵。
 */
@SuppressWarnings("null")
public class FarmInstanceInitializer {

    private static final int CLEAR_RADIUS = 5;
    private static final int EXIT_CURTAIN_HEIGHT = 6;
    private static final Set<UUID> PREPARING_FARMS =
            ConcurrentHashMap.newKeySet();
    private static final ResourceLocation TOTEM_ORIENTATION_STEP =
            new ResourceLocation(StardewCraft.MODID, "farm_totem_south");
    private static final ResourceLocation TOTEM_BUSHES_STEP =
            new ResourceLocation(StardewCraft.MODID, "farm_totem_bushes");
    private static final ResourceLocation PATHS_ECOLOGY_STEP =
            new ResourceLocation(StardewCraft.MODID, "farm_paths_ecology");
    private static final int PATHS_ECOLOGY_VERSION = 4;
    private static final ResourceLocation WILDERNESS_BOWL_SITE_STEP =
            new ResourceLocation(StardewCraft.MODID, "wilderness_bowl_site_west");
    private static final ResourceLocation EXIT_PROTECTION_STEP =
            new ResourceLocation(StardewCraft.MODID, "farm_exit_protection");
    private static final ResourceLocation LIGHTING_REBUILD_STEP =
            new ResourceLocation(StardewCraft.MODID, "farm_lighting_rebuild");
    private static final int LIGHTING_REBUILD_VERSION = 1;
    private static final Set<Heightmap.Types> FARM_HEIGHTMAPS = EnumSet.of(
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR,
            Heightmap.Types.WORLD_SURFACE);
    private static boolean bootstrapped;

    /** Registers retryable repairs which must also apply to already initialized farms. */
    public static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        StardewFarmInitializationSteps.register(
                TOTEM_ORIENTATION_STEP, 1, 110,
                StardewFarmInitializationSteps.FailurePolicy.CONTINUE,
                context -> ensureFarmTotemFacesSouth(
                        context.level(), context.farm().ownerUuid()));
        StardewFarmInitializationSteps.register(
                TOTEM_BUSHES_STEP, 1, 109,
                StardewFarmInitializationSteps.FailurePolicy.CONTINUE,
                context -> ensureFarmTotemBushes(
                        context.level(), context.farm().ownerUuid()));
        StardewFarmInitializationSteps.register(
                PATHS_ECOLOGY_STEP, PATHS_ECOLOGY_VERSION, 108,
                StardewFarmInitializationSteps.FailurePolicy.CONTINUE,
                context -> ensureFarmPathsEcology(
                        context.level(), context.farm().ownerUuid()));
        StardewFarmInitializationSteps.register(
                WILDERNESS_BOWL_SITE_STEP, 1, 107,
                StardewFarmInitializationSteps.FailurePolicy.CONTINUE,
                context -> ensureWildernessPetBowlSite(
                        context.level(), context.farm().ownerUuid()));
        StardewFarmInitializationSteps.register(
                EXIT_PROTECTION_STEP, 1, 105,
                StardewFarmInitializationSteps.FailurePolicy.CONTINUE,
                context -> ensureExitProtection(
                        context.level(), context.farm().ownerUuid()));
        StardewFarmLayoutMigrations.register(
                StardewFarmLayoutRegistry.builtinId(FarmType.FOUR_CORNERS),
                2,
                StardewFarmLayoutMigrations.FailurePolicy.STOP,
                StardewFarmLayoutMigrations.SnapshotPolicy.ADOPT_CURRENT_REGISTRATION,
                FarmInstanceInitializer::migrateFourCornersSouthExit);
    }

    /**
     * 初始化指定玩家的农场实例。
     */
    public static boolean initializeFarm(ServerLevel level, FarmInstance farm) {
        long startedAt = System.nanoTime();
        if (farm.isInitialized()) {
            StardewCraft.LOGGER.warn("[FARM_INIT] Farm for {} already initialized", farm.getOwnerName());
            StardewFarmLayoutMigrations.runPending(level, farm.getOwnerUUID());
            StardewFarmInitializationSteps.runPending(level, farm.getOwnerUUID());
            return true;
        }

        StardewFarmLayout layout = farm.getFarmLayout();
        if (layout == null) {
            StardewCraft.LOGGER.error(
                    "[FARM_INIT] No layout data for farm type {}",
                    farm.getFarmLayoutId());
            return false;
        }

        BlockPos origin = farm.getOrigin();
        StardewCraft.LOGGER.info("[FARM_INIT] Initializing {} farm for {} at origin {}",
                farm.getFarmLayoutId(), farm.getOwnerName(), origin);

        // 1. 批量放置 schematic（内部会一次性加载所需区块）
        if (!placeSchematic(level, farm)) return false;

        // 2.5 在 schematic 底面正下方铺一层基岩，防止掉出世界
        placeBedrockFloor(level, farm, layout);

        // 3. 设置生物群系（非 default 的农场类型）
        if (layout.biomeId() != null) {
            setFarmBiome(level, farm, layout.biomeId());
        }

        // 4. 放置农场图腾柱（朝南）
        placeFarmTotemPole(level, farm);

        // 5. 放置温室（门口朝南）
        com.stardew.craft.greenhouse.GreenhouseManager.get(level).ensurePlacedForPlayer(level, farm.getOwnerUUID());

        // 6. 按原版各区域的 Paths 统计规律，在实际地表上生成不规则生态簇。
        FarmInitialEcology.populate(level, farm, false);
        farm.markInitializationStepComplete(PATHS_ECOLOGY_STEP, PATHS_ECOLOGY_VERSION);
        FarmOreDailyService.seedNewFarm(level, farm);

        // 7. 放置 3 个出口交互实体
        spawnExitPortals(level, farm, layout);

        // 7.5 放置农场洞穴系统（室外墙 + 室外传送方块 + 室内结构）
        placeFarmCaveSystem(level, farm, layout);

        // 8. 河边农场特殊：送熏鱼机
        if (farm.getFarmLayoutId().equals(
                StardewFarmLayoutRegistry.builtinId(FarmType.RIVERLAND))) {
            giveStarterItem(level, farm, ModBlocks.FISH_SMOKER.get().asItem());
        }

        farm.markInitialized();
        FarmInstanceRegistry.get().setDirty();
        StardewFarmLayoutMigrations.runPending(level, farm.getOwnerUUID());
        StardewFarmInitializationSteps.runPending(level, farm.getOwnerUUID());
        StardewCraft.LOGGER.info(
                "[FARM_INIT] Farm initialization complete for {} in {} ms; lighting stabilization pending",
                farm.getOwnerName(),
                (System.nanoTime() - startedAt) / 1_000_000L);
        return true;
    }

    /**
     * Builds a farm and completes only after every farm chunk has finished its
     * queued sky/block-light work. Callers must wait for this future before
     * moving a player to the farm.
     */
    public static CompletableFuture<Boolean> prepareFarmForTeleport(
            ServerLevel level,
            FarmInstance farm
    ) {
        PREPARING_FARMS.add(farm.getOwnerUUID());
        final boolean initialized;
        try {
            initialized = initializeFarm(level, farm);
        } catch (RuntimeException exception) {
            PREPARING_FARMS.remove(farm.getOwnerUUID());
            StardewCraft.LOGGER.error(
                    "[FARM_INIT] Farm preparation crashed for {}",
                    farm.getOwnerName(), exception);
            return CompletableFuture.completedFuture(false);
        }
        if (!initialized) {
            PREPARING_FARMS.remove(farm.getOwnerUUID());
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Void> lightingReady = needsLightingRebuild(farm)
                ? rebuildFarmLighting(level, farm)
                : waitForPendingFarmLighting(level, farm);
        return lightingReady
                .thenComposeAsync(ignored -> waitOneServerTick(
                        level.getServer()), level.getServer())
                .handleAsync((ignored, failure) -> {
                    PREPARING_FARMS.remove(farm.getOwnerUUID());
                    if (failure != null) {
                        StardewCraft.LOGGER.error(
                                "[FARM_INIT] Lighting stabilization failed for {}",
                                farm.getOwnerName(), failure);
                        return false;
                    }
                    farm.markInitializationStepComplete(
                            LIGHTING_REBUILD_STEP,
                            LIGHTING_REBUILD_VERSION);
                    FarmInstanceRegistry.get().setDirty();
                    StardewCraft.LOGGER.info(
                            "[FARM_INIT] Farm and lighting ready for {}",
                            farm.getOwnerName());
                    return true;
                }, level.getServer());
    }

    public static boolean isPreparing(FarmInstance farm) {
        return farm != null && PREPARING_FARMS.contains(
                farm.getOwnerUUID());
    }

    public static boolean tryBeginPreparation(FarmInstance farm) {
        return farm != null && PREPARING_FARMS.add(
                farm.getOwnerUUID());
    }

    public static boolean needsLightingRebuild(FarmInstance farm) {
        return farm != null && farm.getInitializationStepVersion(
                LIGHTING_REBUILD_STEP) < LIGHTING_REBUILD_VERSION;
    }

    /**
     * Rebuilds the data which a normal chunk-generation LIGHT step would have
     * produced. Farm schematics are written into already-generated void chunks,
     * so merely waiting for queued block checks is not enough: their sky-source
     * columns can still describe the old empty chunk and yield an all-black map.
     */
    private static CompletableFuture<Void> rebuildFarmLighting(
            ServerLevel level,
            FarmInstance farm
    ) {
        var engine = level.getChunkSource().getLightEngine();
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        Set<ChunkPos> positions = FarmChunkManager.chunkPositionsForBounds(
                min, max);
        TemporaryChunkLeaseTracker.Lease lease =
                FarmChunkManager.get().acquireTemporaryChunks(
                        level, positions);
        try {
            ArrayList<LevelChunk> chunks = new ArrayList<>(positions.size());
            for (ChunkPos position : positions) {
                LevelChunk chunk = level.getChunk(position.x, position.z);
                // Bulk map placement updates blocks in a previously lit void
                // chunk. Re-prime both occlusion inputs before propagating sky
                // and block sources through the new terrain.
                Heightmap.primeHeightmaps(chunk, FARM_HEIGHTMAPS);
                chunk.initializeLightSources();
                chunk.setLightCorrect(false);
                chunks.add(chunk);
            }

            long startedAt = System.nanoTime();
            ArrayList<CompletableFuture<?>> relight = new ArrayList<>(
                    chunks.size());
            for (LevelChunk chunk : chunks) {
                relight.add(engine.lightChunk(chunk, false));
            }
            engine.tryScheduleUpdate();
            return CompletableFuture.allOf(
                            relight.toArray(CompletableFuture[]::new))
                    .thenComposeAsync(ignored -> waitForPendingChunks(
                            level, chunks), level.getServer())
                    .thenRunAsync(() -> {
                        resendLightingToTrackingPlayers(level, chunks);
                        StardewCraft.LOGGER.info(
                                "[FARM_INIT] Rebuilt lighting for {} chunks of {} in {} ms",
                                chunks.size(), farm.getOwnerName(),
                                (System.nanoTime() - startedAt) / 1_000_000L);
                    }, level.getServer())
                    .whenCompleteAsync(
                            (ignored, failure) -> lease.close(),
                            level.getServer());
        } catch (RuntimeException exception) {
            lease.close();
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static CompletableFuture<Void> waitForPendingFarmLighting(
            ServerLevel level,
            FarmInstance farm
    ) {
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        ArrayList<LevelChunk> chunks = new ArrayList<>();
        for (int chunkX = min.getX() >> 4;
             chunkX <= max.getX() >> 4; chunkX++) {
            for (int chunkZ = min.getZ() >> 4;
                 chunkZ <= max.getZ() >> 4; chunkZ++) {
                chunks.add(level.getChunk(chunkX, chunkZ));
            }
        }
        return waitForPendingChunks(level, chunks);
    }

    private static CompletableFuture<Void> waitForPendingChunks(
            ServerLevel level,
            List<LevelChunk> chunks
    ) {
        var engine = level.getChunkSource().getLightEngine();
        ArrayList<CompletableFuture<?>> pending = new ArrayList<>(
                chunks.size());
        for (LevelChunk chunk : chunks) {
            ChunkPos position = chunk.getPos();
            pending.add(com.stardew.craft.port.PortLevels.waitForPendingTasks(engine,
                    position.x, position.z));
        }
        engine.tryScheduleUpdate();
        return CompletableFuture.allOf(
                pending.toArray(CompletableFuture[]::new));
    }

    private static void resendLightingToTrackingPlayers(
            ServerLevel level,
            List<LevelChunk> chunks
    ) {
        var engine = level.getChunkSource().getLightEngine();
        var chunkMap = level.getChunkSource().chunkMap;
        for (LevelChunk chunk : chunks) {
            var packet = new net.minecraft.network.protocol.game.ClientboundLightUpdatePacket(
                    chunk.getPos(), engine, null, null);
            for (var player : chunkMap.getPlayers(
                    chunk.getPos(), false)) {
                player.connection.send(packet);
            }
        }
    }

    private static CompletableFuture<Void> waitOneServerTick(
            net.minecraft.server.MinecraftServer server
    ) {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        server.tell(new net.minecraft.server.TickTask(
                server.getTickCount() + 1,
                () -> ready.complete(null)));
        return ready;
    }

    // ══════════════════════════════════════════
    //  Schematic 放置
    // ══════════════════════════════════════════

    private static boolean placeSchematic(ServerLevel level, FarmInstance farm) {
        ResourceLocation path = farm.getFarmLayout().schematic();
        BlockPos origin = farm.getOrigin();
        boolean result = com.stardew.craft.mining.StructureLoader
                .loadAndPlaceFarmSchematicWithResult(
                level, path, origin);
        if (!result) {
            StardewCraft.LOGGER.error("[FARM_INIT] Failed to place schematic {} at {}", path, origin);
        } else {
            StardewCraft.LOGGER.info("[FARM_INIT] Placed schematic {} at {}", path, origin);
        }
        return result;
    }

    /**
     * 在农场 schematic 底面正下方铺一整层基岩，防止玩家掉出世界。
     * Y = origin.getY() - 1，覆盖 schemWidth × schemLength 的完整区域。
     */
    private static void placeBedrockFloor(
            ServerLevel level,
            FarmInstance farm,
            StardewFarmLayout layout
    ) {
        BlockPos origin = farm.getOrigin();
        int bedrockY = origin.getY() - 1;
        int startX = origin.getX();
        int startZ = origin.getZ();
        int endX = startX + layout.width();
        int endZ = startZ + layout.length();
        net.minecraft.world.level.block.state.BlockState bedrock = net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();

        int minChunkX = startX >> 4;
        int maxChunkX = (endX - 1) >> 4;
        int minChunkZ = startZ >> 4;
        int maxChunkZ = (endZ - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                int chunkStartX = Math.max(startX, chunkX << 4);
                int chunkEndX = Math.min(endX, (chunkX + 1) << 4);
                int chunkStartZ = Math.max(startZ, chunkZ << 4);
                int chunkEndZ = Math.min(endZ, (chunkZ + 1) << 4);
                for (int x = chunkStartX; x < chunkEndX; x++) {
                    for (int z = chunkStartZ; z < chunkEndZ; z++) {
                        BlockPos pos = new BlockPos(x, bedrockY, z);
                        chunk.setBlockState(pos, bedrock, false);
                    }
                }
            }
        }
        StardewCraft.LOGGER.info("[FARM_INIT] Bedrock floor placed at Y={} ({} x {} blocks)",
                bedrockY, layout.width(), layout.length());
    }

    // ══════════════════════════════════════════
    //  生物群系设置
    // ══════════════════════════════════════════

    private static void setFarmBiome(ServerLevel level, FarmInstance farm, String biomeId) {
        ResourceLocation biomeLocation = biomeId.indexOf(':') >= 0
                ? ResourceLocation.tryParse(biomeId)
                : ResourceLocation.tryBuild(
                        StardewCraft.MODID, biomeId);
        if (biomeLocation == null) {
            StardewCraft.LOGGER.error(
                    "[FARM_INIT] Invalid biome ID {}", biomeId);
            return;
        }
        ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME,
                biomeLocation);
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

    static boolean isNearProtected(FarmInstance farm, int x, int z, int margin) {
        BlockPos spawn = farm.getSpawnPoint();
        BlockPos greenhouse = farm.getGreenhousePos();
        if (Math.abs(x - spawn.getX()) <= CLEAR_RADIUS + margin
                && Math.abs(z - spawn.getZ()) <= CLEAR_RADIUS + margin) return true;
        if (x >= greenhouse.getX() - 2 - margin && x <= greenhouse.getX() + 19 + margin
                && z >= greenhouse.getZ() - 2 - margin && z <= greenhouse.getZ() + 19 + margin) return true;
        if (nearPoint(x, z, farm.getFarmTotemPos(), 2 + margin)
                || nearPoint(x, z, com.stardew.craft.pet.PetHomes.authoredBowl(farm), 2 + margin)) {
            return true;
        }
        StardewFarmLayout layout = farm.getFarmLayout();
        BlockPos origin = farm.getOrigin();
        if (nearEntry(x, z, origin, layout.entrySouth(), 3 + margin)
                || nearEntry(x, z, origin, layout.entryEast(), 3 + margin)
                || nearEntry(x, z, origin, layout.entryWest(), 3 + margin)) {
            return true;
        }
        StardewFarmLayout.Region cave = layout.cavePortalWall();
        if (cave != null && nearRegion(x, z, origin.offset(cave.min()),
                origin.offset(cave.max()), 3 + margin)) return true;
        return false;
    }

    private static boolean nearPoint(int x, int z, BlockPos point, int radius) {
        return Math.abs(x - point.getX()) <= radius && Math.abs(z - point.getZ()) <= radius;
    }

    private static boolean nearEntry(int x, int z, BlockPos origin,
                                     StardewFarmLayout.Entry entry, int margin) {
        return nearRegion(x, z, origin.offset(entry.exitMin()),
                origin.offset(entry.exitMax()), margin);
    }

    private static boolean nearRegion(int x, int z, BlockPos first, BlockPos second, int margin) {
        return x >= Math.min(first.getX(), second.getX()) - margin
                && x <= Math.max(first.getX(), second.getX()) + margin
                && z >= Math.min(first.getZ(), second.getZ()) - margin
                && z <= Math.max(first.getZ(), second.getZ()) + margin;
    }

    // ══════════════════════════════════════════
    //  图腾柱（朝南）
    // ══════════════════════════════════════════

    private static void placeFarmTotemPole(ServerLevel level, FarmInstance farm) {
        BlockPos totemPos = farm.getFarmTotemPos();
        level.getChunk(totemPos.getX() >> 4, totemPos.getZ() >> 4);

        Block block = com.stardew.craft.block.ModBlocks.TOTEM_POLE_FARM.get();
        BlockState mainState = block.defaultBlockState()
                .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                .setValue(MapDecorStaticBlock.FACING, Direction.SOUTH)
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

        placeFarmTotemBushes(level, farm);
    }

    private static void ensureFarmTotemFacesSouth(ServerLevel level, java.util.UUID owner) {
        FarmInstance farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
        if (farm == null) return;
        BlockPos mainPos = farm.getFarmTotemPos();
        Block block = ModBlocks.TOTEM_POLE_FARM.get();
        MapDecorStaticBlock decor = (MapDecorStaticBlock) block;
        java.util.ArrayList<BlockPos> parts = new java.util.ArrayList<>();
        for (BlockPos candidate : BlockPos.betweenClosed(
                mainPos.offset(-1, -1, -1), mainPos.offset(1, 3, 1))) {
            BlockState state = level.getBlockState(candidate);
            if (!state.is(block)) continue;
            if ((candidate.equals(mainPos)
                    && state.getValue(MapDecorStaticBlock.PART)
                            == MapDecorStaticBlock.Part.MAIN)
                    || (state.getValue(MapDecorStaticBlock.PART)
                            == MapDecorStaticBlock.Part.EXTENSION
                    && mainPos.equals(decor.findMainPos(level, candidate, state)))) {
                parts.add(candidate.immutable());
            }
        }
        for (BlockPos part : parts) {
            BlockState state = level.getBlockState(part);
            if (state.is(block)
                    && state.getValue(MapDecorStaticBlock.FACING) != Direction.SOUTH) {
                level.setBlock(part, state.setValue(
                        MapDecorStaticBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
            }
        }
    }

    private static void ensureFarmTotemBushes(ServerLevel level, java.util.UUID owner) {
        FarmInstance farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
        if (farm != null) {
            placeFarmTotemBushes(level, farm);
        }
    }

    private static void placeFarmTotemBushes(ServerLevel level, FarmInstance farm) {
        BlockState bush = ModBlocks.SMALL_BUSH.get().defaultBlockState();
        BlockPos totem = farm.getFarmTotemPos();
        level.setBlock(totem.west(), bush, Block.UPDATE_ALL);
        level.setBlock(totem.east(), bush, Block.UPDATE_ALL);
    }

    private static void ensureFarmPathsEcology(ServerLevel level, java.util.UUID owner) {
        FarmInstance farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
        if (farm != null && farm.isInitialized()) {
            FarmInitialEcology.populate(level, farm, true);
        }
    }

    /** Moves only the untouched authored Wilderness bowl; player-moved bowls stay where they are. */
    private static void ensureWildernessPetBowlSite(ServerLevel level, java.util.UUID owner) {
        FarmInstance farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
        if (farm == null || !farm.isInitialized() || !farm.getFarmLayoutId().equals(
                StardewFarmLayoutRegistry.builtinId(FarmType.WILDERNESS))) return;

        BlockPos oldPosition = farm.getOrigin().offset(178, 25, 70);
        BlockPos newPosition = oldPosition.west();
        if (!level.hasChunksAt(oldPosition.offset(-1, -1, -1), oldPosition.offset(3, 2, 3))) {
            throw new IllegalStateException("Wilderness pet bowl chunks are not loaded");
        }
        if (!(level.getBlockState(oldPosition).getBlock()
                instanceof com.stardew.craft.pet.PetBowlBlock)) return;
        if (level.getBlockState(newPosition).getBlock()
                instanceof com.stardew.craft.pet.PetBowlBlock) return;

        var record = com.stardew.craft.pet.PetBowlBuildings.ensure(level, oldPosition);
        if (record == null || !record.farmId().equals(farm.getInstanceId())) {
            throw new IllegalStateException("Could not register the authored Wilderness pet bowl");
        }
        var transfer = com.stardew.craft.building.runtime.BuildingTransfer.move(
                level, record, newPosition, record.facing());
        var data = com.stardew.craft.building.runtime.BuildingWorldData.get(level.getServer());
        if (data.beginTransfer(transfer)
                != com.stardew.craft.building.runtime.BuildingWorldData.Result.SUCCESS) {
            throw new IllegalStateException("Could not reserve the corrected Wilderness pet bowl site");
        }
        com.stardew.craft.building.runtime.BuildingLifecycleService.completeTransfer(level, transfer);
    }

    private static void migrateFourCornersSouthExit(
            StardewFarmLayoutMigrations.Context context
    ) {
        ServerLevel level = context.level();
        BlockPos origin = context.farm().origin();
        BlockPos oldMin = origin.offset(148, 31, 237);
        BlockPos oldMax = origin.offset(150, 33, 237);
        for (BlockPos cursor : BlockPos.betweenClosed(oldMin, oldMax)) {
            if (level.getBlockState(cursor).is(ModBlocks.PORTAL_TRIGGER.get())) {
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }

        StardewFarmLayout current = StardewFarmLayoutRegistry.find(
                        StardewFarmLayoutRegistry.builtinId(FarmType.FOUR_CORNERS))
                .orElseThrow(() -> new IllegalStateException("Four Corners layout is unavailable"));
        spawnExitEntityRegion(level, origin, current.entryEast(),
                "sdv_portal_target:farm_exit_east", "sdv_portal_marker:farm_exit");
        placeExitBarrierWall(level, origin, current.entryEast());
    }

    // ══════════════════════════════════════════
    //  出口交互实体
    // ══════════════════════════════════════════

    private static void spawnExitPortals(
            ServerLevel level,
            FarmInstance farm,
            StardewFarmLayout layout
    ) {
        BlockPos origin = farm.getOrigin();

        spawnExitEntityRegion(level, origin, layout.entrySouth(),
                "sdv_portal_target:farm_exit_south", "sdv_portal_marker:farm_exit");
        placeExitBarrierWall(level, origin, layout.entrySouth());
        spawnExitEntityRegion(level, origin, layout.entryEast(),
                "sdv_portal_target:farm_exit_east", "sdv_portal_marker:farm_exit");
        placeExitBarrierWall(level, origin, layout.entryEast());
        spawnExitEntityRegion(level, origin, layout.entryWest(),
                "sdv_portal_target:farm_exit_west", "sdv_portal_marker:farm_exit");
        placeExitBarrierWall(level, origin, layout.entryWest());

        StardewCraft.LOGGER.info("[FARM_INIT] Spawned exit portals for farm of {}", farm.getOwnerName());
    }

    private static void spawnExitEntityRegion(ServerLevel level, BlockPos origin,
                                               StardewFarmLayout.Entry entry,
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

    /**
     * Places the authored protection curtain behind an exit. Solid scenery is
     * retained; barriers only occupy open or fluid cells, so the boundary
     * closes every route around the exit without cutting visible terrain away.
     */
    private static void placeExitBarrierWall(ServerLevel level, BlockPos origin,
                                             StardewFarmLayout.Entry entry) {
        BlockPos min = origin.offset(entry.barrierMin());
        BlockPos max = origin.offset(entry.barrierMax());
        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int maxY = Math.max(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());
        int fallbackGroundY = origin.getY()
                + entry.teleportOffset().getY() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int terrainY = Integer.MIN_VALUE;
                int fluidTopY = Integer.MIN_VALUE;
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.getFluidState().isEmpty()) {
                        fluidTopY = y;
                    }
                    if (isExitTerrainSupport(level, cursor, state)) {
                        terrainY = y;
                    }
                }
                if (terrainY == Integer.MIN_VALUE) {
                    terrainY = fallbackGroundY;
                }
                int curtainMinY = Math.max(minY, terrainY + 1);
                int curtainMaxY = Math.min(maxY, Math.max(
                        terrainY + EXIT_CURTAIN_HEIGHT,
                        fluidTopY == Integer.MIN_VALUE
                                ? Integer.MIN_VALUE : fluidTopY + 2));
                for (int y = curtainMinY; y <= curtainMaxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir()
                            && existing.getFluidState().isEmpty()
                            && !existing.is(Blocks.BARRIER)) {
                        continue;
                    }
                    BlockState barrier = Blocks.BARRIER.defaultBlockState();
                    if (barrier.hasProperty(BlockStateProperties.WATERLOGGED)
                            && existing.getFluidState().is(Fluids.WATER)) {
                        barrier = barrier.setValue(
                                BlockStateProperties.WATERLOGGED, true);
                    }
                    if (!existing.equals(barrier)) {
                        level.setBlock(pos, barrier,
                                Block.UPDATE_CLIENTS
                                        | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    private static boolean isExitTerrainSupport(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        return state.getFluidState().isEmpty()
                && !state.is(BlockTags.LOGS)
                && !state.is(BlockTags.LEAVES)
                && !state.is(BlockTags.SAPLINGS)
                && !state.is(BlockTags.FLOWERS)
                && state.isCollisionShapeFullBlock(level, pos);
    }

    /** Repairs widened exit curtains in already initialized farms. */
    private static void ensureExitProtection(
            ServerLevel level,
            java.util.UUID ownerUuid
    ) {
        FarmInstance farm = FarmInstanceRegistry.get(level.getServer())
                .getFarm(ownerUuid);
        if (farm == null || !farm.isInitialized()) return;

        StardewFarmLayout saved = farm.getFarmLayout();
        if (saved == null) return;
        StardewFarmLayout layout = StardewFarmLayoutRegistry
                .find(farm.getFarmLayoutId())
                .filter(current -> current.width() == saved.width()
                        && current.height() == saved.height()
                        && current.length() == saved.length())
                .orElse(saved);
        BlockPos origin = farm.getOrigin();
        placeExitBarrierWall(level, origin, layout.entrySouth());
        placeExitBarrierWall(level, origin, layout.entryEast());
        placeExitBarrierWall(level, origin, layout.entryWest());
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
        StardewFarmLayout saved = farm.getFarmLayout();
        if (saved == null) return false;
        // Existing farms retain their creation snapshot.  Use the current
        // authored geometry when dimensions still match so old snapshots that
        // predate the cave portal fields can receive the repaired doorway.
        StardewFarmLayout layout = StardewFarmLayoutRegistry
                .find(farm.getFarmLayoutId())
                .filter(current -> current.width() == saved.width()
                        && current.height() == saved.height()
                        && current.length() == saved.length())
                .orElse(saved);
        // Repair the door independently of old cavePlaced flags, without clearing farm contents.
        var portal=layout.cavePortalWall();
        if(portal!=null)com.stardew.craft.interior.InteriorSubspaceManager.spawnFarmCaveOutdoorPortalArea(
                level,farm.getOrigin().offset(portal.min()),farm.getOrigin().offset(portal.max()));
        com.stardew.craft.interior.FarmCaveRuntime.request(level,farm);
        return true;
    }

    private static void placeFarmCaveSystem(
            ServerLevel level,
            FarmInstance farm,
            StardewFarmLayout layout
    ) {
        BlockPos origin = farm.getOrigin();

        // 1. 清空区域（仅 FOREST）
        StardewFarmLayout.Region clear = layout.caveClearBox();
        if (clear != null) {
            fillRegion(level, origin, clear, Blocks.AIR.defaultBlockState());
        }

        // 2. 黑色混凝土墙（STANDARD/FOREST）
        StardewFarmLayout.Region blackWall = layout.caveBlackWall();
        if (blackWall != null) {
            fillRegion(level, origin, blackWall, Blocks.BLACK_CONCRETE.defaultBlockState());
        }

        // 3. 外部传送方块
        StardewFarmLayout.Region portalWall = layout.cavePortalWall();
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
    private static void fillRegion(
            ServerLevel level,
            BlockPos origin,
            StardewFarmLayout.Region region,
            BlockState state
    ) {
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
