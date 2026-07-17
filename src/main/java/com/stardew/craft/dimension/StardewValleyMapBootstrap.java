package com.stardew.craft.dimension;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 星露谷主地图引导器：
 * - 每个存档只在首次初始化时铺一次图
 * - 采用分帧放置，避免世界加载阶段阻塞主线程
 */
@SuppressWarnings({"null", "unused"})
public final class StardewValleyMapBootstrap {

    private StardewValleyMapBootstrap() {}

    private static final String[] SCHEMATIC_RESOURCE_CANDIDATES = new String[] {
        "data/stardewcraft/structures/stardew_valley/main.schem",
        "data/stardewcraft/structures/mine/main.schem"
    };

    private static final int MAP_ORIGIN_Y = 64;
    private static final int BLOCK_BUDGET_PER_TICK = 120000;
    private static final int BLOCK_BUDGET_BLOCKING = 400000;
    private static final long TICK_TIME_BUDGET_NANOS = 10_000_000L;
    private static final int PROGRESS_LOG_INTERVAL_TICKS = 100;
    private static final int MAP_PIPELINE_VERSION = 3;
    private static final int PLACE_FLAGS = 2 | 16;

    private static final Map<Level, PlacementJob> ACTIVE_JOBS = new HashMap<>();
    private static volatile LoadedSchematic CACHED_SCHEMATIC;

    public static void preGenerateAllChunksOnServerStart(ServerLevel level, long timeBudgetMillis) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }

        LoadedSchematic loaded = loadMainSchematic();
        if (loaded == null) {
            return;
        }

        Bounds bounds = boundsForCenter(loaded.schematic().width(), loaded.schematic().height(), loaded.schematic().length());
        MapSavedData data = MapSavedData.get(level);
        prepareDataForSchematic(data, loaded, bounds);

        if (data.isAllMapChunksGenerated()) {
            data.markBuildFinished(loaded.sha256(), bounds, loaded.schematic().nonAirMask());
            data.setDirty();
            return;
        }

        int minChunkX = Math.floorDiv(bounds.x, 16);
        int maxChunkX = Math.floorDiv(bounds.x + bounds.width - 1, 16);
        int minChunkZ = Math.floorDiv(bounds.z, 16);
        int maxChunkZ = Math.floorDiv(bounds.z + bounds.length - 1, 16);

        long deadline = System.nanoTime() + Math.max(1L, timeBudgetMillis) * 1_000_000L;
        int scanned = 0;
        int generated = 0;

        StardewCraft.LOGGER.info("[VALLEY_MAP] Startup pre-generation begin: chunkX=[{},{}], chunkZ=[{},{}], budget={}ms",
            minChunkX, maxChunkX, minChunkZ, maxChunkZ, timeBudgetMillis);

        outer:
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                scanned++;
                if (System.nanoTime() >= deadline) {
                    break outer;
                }

                long key = ChunkPos.asLong(cx, cz);
                if (data.isChunkGenerated(key)) {
                    continue;
                }

                level.getChunk(cx, cz);
                ensureChunkGenerated(level, cx, cz);
                generated++;

                if ((scanned & 63) == 0) {
                    StardewCraft.LOGGER.info("[VALLEY_MAP] Startup pre-generation progress: scanned={}, generated={}", scanned, generated);
                }
            }
        }

        if (data.isAllMapChunksGenerated()) {
            data.markBuildFinished(loaded.sha256(), bounds, loaded.schematic().nonAirMask());
            data.setDirty();
            StardewCraft.LOGGER.info("[VALLEY_MAP] Startup pre-generation completed: generated={}", generated);
        } else {
            StardewCraft.LOGGER.warn("[VALLEY_MAP] Startup pre-generation partial: scanned={}, generated={}, remaining chunks will generate on demand",
                scanned, generated);
        }
    }

    public static void preGenerateAllChunksBlocking(ServerLevel level) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }

        LoadedSchematic loaded = loadMainSchematic();
        if (loaded == null) {
            return;
        }

        Bounds bounds = boundsForCenter(loaded.schematic().width(), loaded.schematic().height(), loaded.schematic().length());
        MapSavedData data = MapSavedData.get(level);
        prepareDataForSchematic(data, loaded, bounds);

        if (data.isAllMapChunksGenerated()) {
            data.markBuildFinished(loaded.sha256(), bounds, loaded.schematic().nonAirMask());
            data.setDirty();
            return;
        }

        int minChunkX = Math.floorDiv(bounds.x, 16);
        int maxChunkX = Math.floorDiv(bounds.x + bounds.width - 1, 16);
        int minChunkZ = Math.floorDiv(bounds.z, 16);
        int maxChunkZ = Math.floorDiv(bounds.z + bounds.length - 1, 16);

        int generated = 0;
        StardewCraft.LOGGER.info("[VALLEY_MAP] Blocking full pre-generation begin: chunkX=[{},{}], chunkZ=[{},{}]",
            minChunkX, maxChunkX, minChunkZ, maxChunkZ);

        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                long key = ChunkPos.asLong(cx, cz);
                if (data.isChunkGenerated(key)) {
                    continue;
                }
                level.getChunk(cx, cz);
                ensureChunkGenerated(level, cx, cz);
                generated++;
            }
        }

        data.markBuildFinished(loaded.sha256(), bounds, loaded.schematic().nonAirMask());
        data.setDirty();
        StardewCraft.LOGGER.info("[VALLEY_MAP] Blocking full pre-generation completed: generated={} chunks", generated);
    }

    public static void markPrebuiltInstalled(ServerLevel level, int prebuiltVersion) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        MapSavedData data = MapSavedData.get(level);
        if (data.markPrebuiltInstalled(prebuiltVersion)) {
            data.setDirty();
            StardewCraft.LOGGER.info("[VALLEY_MAP] Marked prebuilt regions ready. version={}", prebuiltVersion);
        }
    }

    public static void ensureGenerated(ServerLevel level) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }

        MapSavedData data = MapSavedData.get(level);
        if (data.hasAppliedMap()) {
            return;
        }

        if (ACTIVE_JOBS.containsKey(level)) {
            return;
        }

        LoadedSchematic loaded = loadMainSchematic();
        if (loaded == null) {
            return;
        }

        Bounds newBounds = boundsForCenter(loaded.schematic().width(), loaded.schematic().height(), loaded.schematic().length());
        boolean shouldClearOld = false;
        Bounds oldBounds = shouldClearOld ? data.appliedBounds() : null;
        long[] oldNonAirMask = shouldClearOld ? data.nonAirMask() : new long[0];

        PlacementJob job = new PlacementJob(level, loaded.schematic(), loaded.sha256(), oldBounds, oldNonAirMask, newBounds, shouldClearOld);
        ACTIVE_JOBS.put(level, job);

        data.markBuildStarted(loaded.sha256(), newBounds, loaded.schematic().nonAirMask());
        data.setDirty();

        StardewCraft.LOGGER.info("[VALLEY_MAP] Build scheduled. source={}, hash={}, size={}x{}x{}, nonAir={}",
            loaded.resourcePath(), loaded.sha256(), loaded.schematic().width(), loaded.schematic().height(), loaded.schematic().length(),
            loaded.schematic().nonAirIndexes().length);
    }

    public static void tick(ServerLevel level) {
        PlacementJob job = ACTIVE_JOBS.get(level);
        if (job == null) {
            return;
        }

        PlacementResult result = job.placeNext(BLOCK_BUDGET_PER_TICK, TICK_TIME_BUDGET_NANOS);
        if (result.finished()) {
            ACTIVE_JOBS.remove(level);

            MapSavedData data = MapSavedData.get(level);
            data.markBuildFinished(job.targetHash, job.newBounds, job.schematic.nonAirMask());
            data.setDirty();

            StardewCraft.LOGGER.info("[VALLEY_MAP] Build completed. hash={}, totalWrites={}", job.targetHash, result.totalWrites());
            return;
        }

        if (result.shouldLogProgress()) {
            StardewCraft.LOGGER.info("[VALLEY_MAP] Progress: phase={}, {:.2f}% (cursor={}/{})",
                result.phase(), result.progressPercent(), result.cursor(), result.total());
        }
    }

    public static boolean isGenerationComplete(ServerLevel level) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return true;
        }
        return MapSavedData.get(level).hasAppliedMap();
    }

    public static boolean isGenerationInProgress(ServerLevel level) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return false;
        }
        return ACTIVE_JOBS.containsKey(level);
    }

    public static void ensureSpawnAreaGenerated(ServerLevel level, BlockPos center, int radiusChunks) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }

        LoadedSchematic loaded = loadMainSchematic();
        if (loaded == null) {
            return;
        }
        MapSavedData data = MapSavedData.get(level);
        prepareDataForSchematic(data, loaded, boundsForCenter(loaded.schematic().width(), loaded.schematic().height(), loaded.schematic().length()));

        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                ensureChunkGenerated(level, centerChunkX + dx, centerChunkZ + dz);
            }
        }
    }

    public static void ensureChunkGenerated(ServerLevel level, int chunkX, int chunkZ) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }

        LoadedSchematic loaded = loadMainSchematic();
        if (loaded == null) {
            return;
        }

        Bounds bounds = boundsForCenter(loaded.schematic().width(), loaded.schematic().height(), loaded.schematic().length());
        if (!chunkIntersects(bounds, chunkX, chunkZ)) {
            return;
        }

        MapSavedData data = MapSavedData.get(level);
        prepareDataForSchematic(data, loaded, bounds);

        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        if (data.isChunkGenerated(chunkKey)) {
            return;
        }

        generateSingleChunk(level, loaded.schematic(), bounds, chunkX, chunkZ);
        data.markChunkGenerated(chunkKey);
        data.setDirty();

        if (data.isAllMapChunksGenerated()) {
            data.markBuildFinished(loaded.sha256(), bounds, loaded.schematic().nonAirMask());
            data.setDirty();
            StardewCraft.LOGGER.info("[VALLEY_MAP] All map chunks generated. hash={}", loaded.sha256());
        }
    }

    private static void prepareDataForSchematic(MapSavedData data, LoadedSchematic loaded, Bounds bounds) {
        if (!data.matchesSchema(loaded.sha256(), bounds, MAP_PIPELINE_VERSION)) {
            data.resetForSchema(loaded.sha256(), bounds, loaded.schematic().nonAirMask(), MAP_PIPELINE_VERSION);
            data.setDirty();
        }
    }

    private static boolean chunkIntersects(Bounds bounds, int chunkX, int chunkZ) {
        int chunkMinX = chunkX << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkZ << 4;
        int chunkMaxZ = chunkMinZ + 15;

        int mapMinX = bounds.x;
        int mapMaxX = bounds.x + bounds.width - 1;
        int mapMinZ = bounds.z;
        int mapMaxZ = bounds.z + bounds.length - 1;

        return chunkMaxX >= mapMinX && chunkMinX <= mapMaxX && chunkMaxZ >= mapMinZ && chunkMinZ <= mapMaxZ;
    }

    private static void generateSingleChunk(ServerLevel level, SchematicData schematic, Bounds bounds, int chunkX, int chunkZ) {
        int chunkMinX = chunkX << 4;
        int chunkMinZ = chunkZ << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = 0; y < schematic.height; y++) {
            int worldY = bounds.y + y;
            if (worldY < level.getMinBuildHeight() || worldY >= level.getMaxBuildHeight()) {
                continue;
            }

            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = chunkMinZ + localZ;
                int schemZ = worldZ - bounds.z;
                if (schemZ < 0 || schemZ >= schematic.length) {
                    continue;
                }

                for (int localX = 0; localX < 16; localX++) {
                    int worldX = chunkMinX + localX;
                    int schemX = worldX - bounds.x;
                    if (schemX < 0 || schemX >= schematic.width) {
                        continue;
                    }

                    int blockIndex = schemX + schematic.width * (schemZ + schematic.length * y);
                    int paletteIndex = schematic.paletteIndexes[blockIndex];
                    if (paletteIndex < 0 || paletteIndex >= schematic.palette.length) {
                        continue;
                    }

                    BlockState target = schematic.palette[paletteIndex];
                    if (target.isAir()) {
                        continue;
                    }

                    pos.set(worldX, worldY, worldZ);
                    level.setBlock(pos, target, PLACE_FLAGS);
                }
            }
        }
    }

    public static double getGenerationProgressPercent(ServerLevel level) {
        if (isGenerationComplete(level)) {
            return 100.0;
        }
        PlacementJob job = ACTIVE_JOBS.get(level);
        if (job == null) {
            return 0.0;
        }
        return job.progressPercent();
    }

    public static void ensureGeneratedBlocking(ServerLevel level) {
        if (isGenerationComplete(level)) {
            return;
        }

        ensureGenerated(level);
        PlacementJob job = ACTIVE_JOBS.get(level);
        if (job == null) {
            return;
        }

        PlacementResult result = null;
        int guard = 0;
        while (guard++ < 100000) {
            result = job.placeNext(BLOCK_BUDGET_BLOCKING, Long.MAX_VALUE);
            if (result.finished()) {
                break;
            }
        }

        if (result == null || !result.finished()) {
            StardewCraft.LOGGER.error("[VALLEY_MAP] Blocking generation did not finish within safety guard");
            return;
        }

        ACTIVE_JOBS.remove(level);
        MapSavedData data = MapSavedData.get(level);
        data.markBuildFinished(job.targetHash, job.newBounds, job.schematic.nonAirMask());
        data.setDirty();
        StardewCraft.LOGGER.info("[VALLEY_MAP] Build completed (blocking). hash={}, totalWrites={}", job.targetHash, result.totalWrites());
    }

    private static LoadedSchematic loadMainSchematic() {
        LoadedSchematic cached = CACHED_SCHEMATIC;
        if (cached != null) {
            return cached;
        }

        for (String candidate : SCHEMATIC_RESOURCE_CANDIDATES) {
            try {
                byte[] bytes = readResourceBytes(candidate);
                if (bytes == null) {
                    continue;
                }

                String hash = sha256Hex(bytes);
                CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
                if (root == null) {
                    StardewCraft.LOGGER.error("[VALLEY_MAP] Failed to read schem NBT: {}", candidate);
                    continue;
                }

                SchematicData data = parseSchematic(root);
                if (data == null) {
                    StardewCraft.LOGGER.error("[VALLEY_MAP] Invalid schem content: {}", candidate);
                    continue;
                }

                LoadedSchematic loaded = new LoadedSchematic(candidate, hash, data);
                CACHED_SCHEMATIC = loaded;
                return loaded;
            } catch (Exception e) {
                StardewCraft.LOGGER.error("[VALLEY_MAP] Failed loading schem {}: {}", candidate, e.getMessage());
            }
        }

        StardewCraft.LOGGER.error("[VALLEY_MAP] main.schem not found. expected one of {}", Arrays.toString(SCHEMATIC_RESOURCE_CANDIDATES));
        return null;
    }

    private static byte[] readResourceBytes(String resourcePath) throws IOException {
        InputStream stream = StardewValleyMapBootstrap.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            return null;
        }
        try (InputStream in = stream) {
            return in.readAllBytes();
        }
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static SchematicData parseSchematic(CompoundTag root) {
        int width = Short.toUnsignedInt(root.getShort("Width"));
        int height = Short.toUnsignedInt(root.getShort("Height"));
        int length = Short.toUnsignedInt(root.getShort("Length"));
        if (width <= 0 || height <= 0 || length <= 0) {
            return null;
        }

        CompoundTag paletteTag = root.getCompound("Palette");
        if (paletteTag.isEmpty()) {
            return null;
        }

        BlockState[] palette = decodePalette(paletteTag);
        if (palette.length == 0) {
            return null;
        }

        byte[] blockDataRaw = root.getByteArray("BlockData");
        int totalBlocks = width * height * length;
        int[] paletteIndexes = decodeVarIntArray(blockDataRaw, totalBlocks);
        if (paletteIndexes.length != totalBlocks) {
            StardewCraft.LOGGER.error("[VALLEY_MAP] BlockData length mismatch: expected={}, actual={}", totalBlocks, paletteIndexes.length);
            return null;
        }

        NonAirPlacement nonAir = collectNonAirPlacement(palette, paletteIndexes, width, height, length);
        long[] nonAirMask = buildMaskFromIndexes(nonAir.indexes(), totalBlocks);

        return new SchematicData(width, height, length, palette, paletteIndexes, nonAir.indexes(), nonAir.paletteIndexes(), nonAirMask);
    }

    private static NonAirPlacement collectNonAirPlacement(BlockState[] palette, int[] paletteIndexes,
                                                          int width, int height, int length) {
        List<Integer> nonAirIndexList = new ArrayList<>();
        List<Integer> nonAirPaletteList = new ArrayList<>();
        for (int i = 0; i < paletteIndexes.length; i++) {
            int paletteIndex = paletteIndexes[i];
            if (paletteIndex < 0 || paletteIndex >= palette.length) {
                continue;
            }
            if (!palette[paletteIndex].isAir()) {
                nonAirIndexList.add(i);
                nonAirPaletteList.add(paletteIndex);
            }
        }

        int count = nonAirIndexList.size();
        int[] indexes = new int[count];
        int[] nonAirPaletteIndexes = new int[count];
        for (int i = 0; i < count; i++) {
            indexes[i] = nonAirIndexList.get(i);
            nonAirPaletteIndexes[i] = nonAirPaletteList.get(i);
        }

        return reorderPlacementByTopLayer(indexes, nonAirPaletteIndexes, width, height, length);
    }

    private static NonAirPlacement reorderPlacementByTopLayer(int[] indexes, int[] paletteIndexes,
                                                              int width, int height, int length) {
        if (indexes.length <= 1) {
            return new NonAirPlacement(indexes, paletteIndexes);
        }

        int layerArea = width * length;
        if (layerArea <= 0) {
            return new NonAirPlacement(indexes, paletteIndexes);
        }

        int[] layerCounts = new int[Math.max(1, height)];
        for (int index : indexes) {
            int y = Math.max(0, Math.min(height - 1, index / layerArea));
            layerCounts[y]++;
        }

        int[] writeOffsets = new int[layerCounts.length];
        int cursor = 0;
        for (int y = layerCounts.length - 1; y >= 0; y--) {
            writeOffsets[y] = cursor;
            cursor += layerCounts[y];
        }

        int[] orderedIndexes = new int[indexes.length];
        int[] orderedPaletteIndexes = new int[paletteIndexes.length];
        for (int i = 0; i < indexes.length; i++) {
            int y = Math.max(0, Math.min(height - 1, indexes[i] / layerArea));
            int writeAt = writeOffsets[y]++;
            orderedIndexes[writeAt] = indexes[i];
            orderedPaletteIndexes[writeAt] = paletteIndexes[i];
        }

        return new NonAirPlacement(orderedIndexes, orderedPaletteIndexes);
    }

    private static long[] buildMaskFromIndexes(int[] indexes, int totalBits) {
        if (totalBits <= 0) {
            return new long[0];
        }
        long[] mask = new long[(totalBits + 63) / 64];
        for (int index : indexes) {
            if (index < 0 || index >= totalBits) {
                continue;
            }
            int word = index >>> 6;
            int bit = index & 63;
            mask[word] |= (1L << bit);
        }
        return mask;
    }

    private static int[] maskToIndexes(long[] mask, int totalBits) {
        if (mask == null || mask.length == 0 || totalBits <= 0) {
            return new int[0];
        }
        List<Integer> out = new ArrayList<>();
        int words = Math.min(mask.length, (totalBits + 63) / 64);
        for (int word = 0; word < words; word++) {
            long bits = mask[word];
            while (bits != 0L) {
                int bit = Long.numberOfTrailingZeros(bits);
                int index = (word << 6) + bit;
                if (index < totalBits) {
                    out.add(index);
                }
                bits &= (bits - 1);
            }
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < out.size(); i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    private static BlockState[] decodePalette(CompoundTag paletteTag) {
        Set<String> keys = paletteTag.getAllKeys();
        int maxId = -1;
        for (String key : keys) {
            maxId = Math.max(maxId, paletteTag.getInt(key));
        }
        if (maxId < 0) {
            return new BlockState[0];
        }

        BlockState[] palette = new BlockState[maxId + 1];
        for (String blockStateString : keys) {
            int id = paletteTag.getInt(blockStateString);
            if (id < 0 || id >= palette.length) {
                continue;
            }
            palette[id] = parseBlockState(blockStateString);
        }

        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == null) {
                palette[i] = Blocks.AIR.defaultBlockState();
            }
        }
        return palette;
    }

    private static BlockState parseBlockState(String raw) {
        String blockId = raw;
        String props = null;
        int split = raw.indexOf('[');
        if (split >= 0 && raw.endsWith("]")) {
            blockId = raw.substring(0, split);
            props = raw.substring(split + 1, raw.length() - 1);
        }

        Block block = BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.parse(blockId)).orElse(Blocks.AIR);
        BlockState state = block.defaultBlockState();
        if (props == null || props.isEmpty()) {
            return state;
        }

        String[] parts = props.split(",");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0 || eq >= part.length() - 1) {
                continue;
            }
            String key = part.substring(0, eq);
            String value = part.substring(eq + 1);

            Property<?> property = block.getStateDefinition().getProperty(key);
            if (property == null) {
                continue;
            }
            state = applyProperty(state, property, value);
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, Property<?> property, String value) {
        Object parsed = property.getValue(value).orElse(null);
        if (!(parsed instanceof Comparable<?> comparable)) {
            return state;
        }
        return state.setValue((Property) property, (Comparable) comparable);
    }

    private static int[] decodeVarIntArray(byte[] bytes, int expectedCount) {
        int[] out = new int[expectedCount];
        int outIndex = 0;
        int cursor = 0;

        while (cursor < bytes.length && outIndex < expectedCount) {
            int value = 0;
            int bitOffset = 0;
            int current;
            do {
                if (cursor >= bytes.length || bitOffset > 35) {
                    return Arrays.copyOf(out, outIndex);
                }
                current = bytes[cursor++] & 0xFF;
                value |= (current & 0x7F) << bitOffset;
                bitOffset += 7;
            } while ((current & 0x80) != 0);

            out[outIndex++] = value;
        }

        if (outIndex == expectedCount) {
            return out;
        }
        return Arrays.copyOf(out, outIndex);
    }

    private static Bounds boundsForCenter(int width, int height, int length) {
        int originX = -Math.floorDiv(width, 2);
        int originZ = -Math.floorDiv(length, 2);
        return new Bounds(originX, MAP_ORIGIN_Y, originZ, width, height, length);
    }

    private enum Phase {
        CLEAR_OLD,
        PLACE_NEW,
        FINISHED
    }

    private record SchematicData(int width, int height, int length, BlockState[] palette, int[] paletteIndexes,
                                 int[] nonAirIndexes, int[] nonAirPaletteIndexes, long[] nonAirMask) {
    }

    private record NonAirPlacement(int[] indexes, int[] paletteIndexes) {
    }

    private record LoadedSchematic(String resourcePath, String sha256, SchematicData schematic) {
    }

    private record Bounds(int x, int y, int z, int width, int height, int length) {
    }

    private record PlacementResult(boolean finished, String phase, int cursor, int total, int totalWrites, boolean shouldLogProgress,
                                   double progressPercent) {
    }

    private static final class PlacementJob {
        private final ServerLevel level;
        private final SchematicData schematic;
        private final String targetHash;
        private final Bounds oldBounds;
        private final int[] oldNonAirIndexes;
        private final boolean legacyFullClear;
        private final Bounds newBounds;
        private final boolean shouldClearOld;
        private final boolean skipReadCompare;

        private Phase phase;
        private int cursor;
        private int total;
        private int writes;
        private long lastLogTick;
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        private PlacementJob(ServerLevel level, SchematicData schematic, String targetHash,
                             Bounds oldBounds, long[] oldNonAirMask, Bounds newBounds, boolean shouldClearOld) {
            this.level = level;
            this.schematic = schematic;
            this.targetHash = targetHash;
            this.oldBounds = oldBounds;
            this.newBounds = newBounds;
            this.shouldClearOld = shouldClearOld;

            int oldTotal = oldBounds == null ? 0 : oldBounds.width * oldBounds.height * oldBounds.length;
            this.oldNonAirIndexes = oldBounds == null ? new int[0] : maskToIndexes(oldNonAirMask, oldTotal);
            this.legacyFullClear = shouldClearOld && oldBounds != null && this.oldNonAirIndexes.length == 0;

            this.phase = shouldClearOld ? Phase.CLEAR_OLD : Phase.PLACE_NEW;
            this.skipReadCompare = !shouldClearOld;
            this.cursor = 0;
            this.total = shouldClearOld
                ? (legacyFullClear
                    ? oldTotal
                    : this.oldNonAirIndexes.length)
                : schematic.nonAirIndexes.length;
            this.writes = 0;
            this.lastLogTick = level.getGameTime();
        }

        private PlacementResult placeNext(int budget, long maxNanos) {
            int processed = 0;
            long startNanos = System.nanoTime();

            while (processed < budget && phase != Phase.FINISHED) {
                if (cursor >= total) {
                    advancePhase();
                    continue;
                }

                if (maxNanos != Long.MAX_VALUE && (processed & 255) == 0) {
                    if (System.nanoTime() - startNanos >= maxNanos) {
                        break;
                    }
                }

                if (phase == Phase.CLEAR_OLD && oldBounds != null) {
                    int blockIndex = legacyFullClear ? cursor : oldNonAirIndexes[cursor];
                    setMutablePos(blockIndex, oldBounds, mutablePos);
                    if (level.isInWorldBounds(mutablePos)) {
                        BlockState current = level.getBlockState(mutablePos);
                        if (!current.isAir()) {
                            level.setBlock(mutablePos, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
                            writes++;
                        }
                    }
                } else if (phase == Phase.PLACE_NEW) {
                    int blockIndex = schematic.nonAirIndexes[cursor];
                    setMutablePos(blockIndex, newBounds, mutablePos);
                    if (level.isInWorldBounds(mutablePos)) {
                        int paletteIndex = schematic.nonAirPaletteIndexes[cursor];
                        if (paletteIndex >= 0 && paletteIndex < schematic.palette.length) {
                            BlockState target = schematic.palette[paletteIndex];
                            if (skipReadCompare) {
                                level.setBlock(mutablePos, target, PLACE_FLAGS);
                                writes++;
                            } else {
                                BlockState current = level.getBlockState(mutablePos);
                                if (current != target) {
                                    level.setBlock(mutablePos, target, PLACE_FLAGS);
                                    writes++;
                                }
                            }
                        }
                    }
                }

                cursor++;
                processed++;
            }

            long now = level.getGameTime();
            boolean shouldLog = now - lastLogTick >= PROGRESS_LOG_INTERVAL_TICKS;
            if (shouldLog) {
                lastLogTick = now;
            }

            return new PlacementResult(
                phase == Phase.FINISHED,
                phase.name(),
                cursor,
                total,
                writes,
                shouldLog,
                total <= 0 ? 100.0 : (cursor * 100.0 / total)
            );
        }

        private static void setMutablePos(int index, Bounds bounds, BlockPos.MutableBlockPos outPos) {
            int x = index % bounds.width;
            int rem = index / bounds.width;
            int z = rem % bounds.length;
            int y = rem / bounds.length;
            outPos.set(bounds.x + x, bounds.y + y, bounds.z + z);
        }

        private double progressPercent() {
            if (total <= 0) {
                return 100.0;
            }
            return cursor * 100.0 / total;
        }

        private void advancePhase() {
            if (phase == Phase.CLEAR_OLD) {
                phase = Phase.PLACE_NEW;
                cursor = 0;
                total = schematic.nonAirIndexes.length;
                StardewCraft.LOGGER.info("[VALLEY_MAP] Old map cleared (legacyFullClear={}), begin placing new map (nonAir={})...",
                    legacyFullClear, schematic.nonAirIndexes.length);
                return;
            }
            phase = Phase.FINISHED;
        }

        private static BlockPos indexToPos(int index, Bounds bounds) {
            int x = index % bounds.width;
            int rem = index / bounds.width;
            int z = rem % bounds.length;
            int y = rem / bounds.length;
            return new BlockPos(bounds.x + x, bounds.y + y, bounds.z + z);
        }
    }

    static final class MapSavedData extends SavedData {
        private static final String NAME = "stardew_valley_map_state";

        private boolean applied;
        private String appliedHash;
        private String pendingHash;

        private int originX;
        private int originY;
        private int originZ;
        private int width;
        private int height;
        private int length;
        private long[] nonAirMask = new long[0];
        private long[] generatedChunksArray = new long[0];
        private final Set<Long> generatedChunks = ConcurrentHashMap.newKeySet();
        private int totalMapChunks;

        static MapSavedData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MapSavedData::new, MapSavedData::load),
                NAME
            );
        }

        static MapSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
            MapSavedData d = new MapSavedData();
            d.applied = tag.getBoolean("applied");
            d.appliedHash = tag.getString("appliedHash");
            d.pendingHash = tag.getString("pendingHash");
            d.originX = tag.getInt("originX");
            d.originY = tag.getInt("originY");
            d.originZ = tag.getInt("originZ");
            d.width = tag.getInt("width");
            d.height = tag.getInt("height");
            d.length = tag.getInt("length");
            d.nonAirMask = tag.getLongArray("nonAirMask");
            d.generatedChunksArray = tag.getLongArray("generatedChunks");
            d.totalMapChunks = tag.getInt("totalMapChunks");
            for (long chunkKey : d.generatedChunksArray) {
                d.generatedChunks.add(chunkKey);
            }
            d.pipelineVersion = tag.getInt("pipelineVersion");
            d.prebuiltVersion = tag.getInt("prebuiltVersion");
            return d;
        }

        @Override
        public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
            tag.putBoolean("applied", applied);
            tag.putString("appliedHash", appliedHash == null ? "" : appliedHash);
            tag.putString("pendingHash", pendingHash == null ? "" : pendingHash);
            tag.putInt("originX", originX);
            tag.putInt("originY", originY);
            tag.putInt("originZ", originZ);
            tag.putInt("width", width);
            tag.putInt("height", height);
            tag.putInt("length", length);
            tag.putLongArray("nonAirMask", nonAirMask == null ? new long[0] : nonAirMask);
            if (generatedChunksArray == null || generatedChunksArray.length != generatedChunks.size()) {
                generatedChunksArray = new long[generatedChunks.size()];
                int i = 0;
                for (long key : generatedChunks) {
                    generatedChunksArray[i++] = key;
                }
            }
            tag.putLongArray("generatedChunks", generatedChunksArray);
            tag.putInt("totalMapChunks", totalMapChunks);
            tag.putInt("pipelineVersion", pipelineVersion);
            tag.putInt("prebuiltVersion", prebuiltVersion);
            return tag;
        }

        private int pipelineVersion = 0;
        private int prebuiltVersion = 0;

        boolean isAppliedForHash(String hash) {
            return applied && hash != null && hash.equals(appliedHash) && pipelineVersion == MAP_PIPELINE_VERSION;
        }

        boolean hasAppliedMap() {
            return applied && (prebuiltVersion > 0 || (width > 0 && height > 0 && length > 0));
        }

        boolean markPrebuiltInstalled(int version) {
            int normalizedVersion = Math.max(1, version);
            if (applied && prebuiltVersion == normalizedVersion) {
                return false;
            }
            this.applied = true;
            this.appliedHash = "pregen:" + normalizedVersion;
            this.pendingHash = "";
            this.originX = 0;
            this.originY = 0;
            this.originZ = 0;
            this.width = 0;
            this.height = 0;
            this.length = 0;
            this.nonAirMask = new long[0];
            this.generatedChunks.clear();
            this.generatedChunksArray = new long[0];
            this.totalMapChunks = 0;
            this.pipelineVersion = MAP_PIPELINE_VERSION;
            this.prebuiltVersion = normalizedVersion;
            return true;
        }

        boolean matchesSchema(String hash, Bounds bounds, int version) {
            if (hash == null || bounds == null) {
                return false;
            }
            return version == pipelineVersion
                && hash.equals(appliedHash)
                && originX == bounds.x
                && originY == bounds.y
                && originZ == bounds.z
                && width == bounds.width
                && height == bounds.height
                && length == bounds.length;
        }

        void resetForSchema(String hash, Bounds bounds, long[] newMask, int version) {
            this.applied = false;
            this.appliedHash = hash;
            this.pendingHash = hash;
            this.originX = bounds.x;
            this.originY = bounds.y;
            this.originZ = bounds.z;
            this.width = bounds.width;
            this.height = bounds.height;
            this.length = bounds.length;
            this.nonAirMask = newMask == null ? new long[0] : newMask;
            this.pipelineVersion = version;
            this.prebuiltVersion = 0;
            this.generatedChunks.clear();
            this.generatedChunksArray = new long[0];
            this.totalMapChunks = computeTotalMapChunks(bounds);
        }

        boolean isChunkGenerated(long key) {
            return generatedChunks.contains(key);
        }

        void markChunkGenerated(long key) {
            if (generatedChunks.add(key)) {
                generatedChunksArray = null;
            }
        }

        boolean isAllMapChunksGenerated() {
            return totalMapChunks > 0 && generatedChunks.size() >= totalMapChunks;
        }

        private static int computeTotalMapChunks(Bounds bounds) {
            int minChunkX = Math.floorDiv(bounds.x, 16);
            int maxChunkX = Math.floorDiv(bounds.x + bounds.width - 1, 16);
            int minChunkZ = Math.floorDiv(bounds.z, 16);
            int maxChunkZ = Math.floorDiv(bounds.z + bounds.length - 1, 16);
            int chunkWidth = maxChunkX - minChunkX + 1;
            int chunkLength = maxChunkZ - minChunkZ + 1;
            return Math.max(0, chunkWidth * chunkLength);
        }

        Bounds appliedBounds() {
            return new Bounds(originX, originY, originZ, width, height, length);
        }

        long[] nonAirMask() {
            return nonAirMask == null ? new long[0] : nonAirMask;
        }

        void markBuildStarted(String hash, Bounds bounds, long[] newMask) {
            this.applied = false;
            this.pendingHash = hash;
            this.originX = bounds.x;
            this.originY = bounds.y;
            this.originZ = bounds.z;
            this.width = bounds.width;
            this.height = bounds.height;
            this.length = bounds.length;
            this.nonAirMask = newMask == null ? new long[0] : newMask;
            this.pipelineVersion = MAP_PIPELINE_VERSION;
            this.prebuiltVersion = 0;
        }

        void markBuildFinished(String hash, Bounds bounds, long[] newMask) {
            this.applied = true;
            this.appliedHash = hash;
            this.pendingHash = "";
            this.originX = bounds.x;
            this.originY = bounds.y;
            this.originZ = bounds.z;
            this.width = bounds.width;
            this.height = bounds.height;
            this.length = bounds.length;
            this.nonAirMask = newMask == null ? new long[0] : newMask;
            this.pipelineVersion = MAP_PIPELINE_VERSION;
            this.prebuiltVersion = 0;
        }
    }
}
