package com.stardew.craft.dimension;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.desert.DesertConstants;
import com.stardew.craft.world.MutantBugLairArea;
import com.stardew.craft.world.WitchArea;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces {@code minecraft:the_void} and {@code minecraft:plains} biomes
 * with {@code stardewcraft:stardew_default} when chunks load in the
 * Stardew Valley dimension.
 *
 * <p>Prebuilt region files (.mca) were saved with void/plains biome data.
 * This handler transparently patches them at load time so that
 * precipitation works and fishing falls back to the trash pool.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class StardewBiomePatcher {

    private static final String MANIFEST_RESOURCE = "pregen/stardew_valley/region_manifest.txt";
    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$", Pattern.CASE_INSENSITIVE);
    private static final int CHUNKS_PER_TICK = 12;
    private static final boolean ENABLE_EAGER_PREGEN_MIGRATION = Boolean.getBoolean("stardewcraft.eagerPregenBiomeMigration");

    private static final ResourceKey<Biome> STARDEW_DEFAULT_KEY =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("stardewcraft", "stardew_default"));

    private static final ResourceKey<Biome> CALICO_DESERT_KEY =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("stardewcraft", "calico_desert"));

    private static final ResourceKey<Biome> MUTANT_BUG_LAIR_KEY =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("stardewcraft", "mutant_bug_lair"));

    private static final ResourceKey<Biome> WITCH_SWAMP_KEY =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("stardewcraft", "witch_swamp"));

    // pregen 主地图内嵌沙漠包围盒（世界坐标）
    private static final int DESERT_MIN_X = DesertConstants.DESERT_BBOX_MIN_X;
    private static final int DESERT_MAX_X = DesertConstants.DESERT_BBOX_MAX_X;
    private static final int DESERT_MIN_Z = DesertConstants.DESERT_BBOX_MIN_Z;
    private static final int DESERT_MAX_Z = DesertConstants.DESERT_BBOX_MAX_Z;

    private static boolean pregenMigrationScheduled = false;

    private StardewBiomePatcher() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(ModDimensions.STARDEW_VALLEY)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        patchChunk(serverLevel, chunk);
    }

    public static boolean patchChunk(ServerLevel serverLevel, LevelChunk chunk) {
        // 判断此 chunk 是否与沙漠区域重叠
        boolean isDesertChunk = isDesertChunk(chunk.getPos());

        Holder<Biome> targetBiome;
        if (isDesertChunk) {
            targetBiome = serverLevel.registryAccess()
                    .registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(CALICO_DESERT_KEY);
        } else {
            targetBiome = serverLevel.registryAccess()
                    .registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(STARDEW_DEFAULT_KEY);
        }
        Holder<Biome> mutantBugLairBiome = serverLevel.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(MUTANT_BUG_LAIR_KEY);
        Holder<Biome> witchSwampBiome = serverLevel.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(WITCH_SWAMP_KEY);

        boolean modified = false;

        LevelChunkSection[] sections = chunk.getSections();
        for (int sIdx = 0; sIdx < sections.length; sIdx++) {
            LevelChunkSection section = sections[sIdx];
            if (section == null) continue;

            PalettedContainerRO<Holder<Biome>> biomesRO = section.getBiomes();
            if (!(biomesRO instanceof PalettedContainer<Holder<Biome>> biomes)) continue;

            int sectionMinY = chunk.getSectionYFromSectionIndex(sIdx) << 4;

            // Biome container is 4×4×4 (one biome per 4-block cube)
            for (int bx = 0; bx < 4; bx++) {
                for (int by = 0; by < 4; by++) {
                    for (int bz = 0; bz < 4; bz++) {
                        Holder<Biome> current = biomes.get(bx, by, bz);
                        int cellMinX = chunk.getPos().getMinBlockX() + bx * 4;
                        int cellMinY = sectionMinY + by * 4;
                        int cellMinZ = chunk.getPos().getMinBlockZ() + bz * 4;
                        boolean inMutantBugLair = MutantBugLairArea.biomeCellIntersects(cellMinX, cellMinY, cellMinZ);
                        boolean inWitchSwamp = WitchArea.swampBiomeCellIntersects(cellMinX, cellMinY, cellMinZ);
                        Holder<Biome> desired = inMutantBugLair
                                ? mutantBugLairBiome
                                : inWitchSwamp ? witchSwampBiome : targetBiome;
                        if ((inMutantBugLair && !current.is(MUTANT_BUG_LAIR_KEY))
                                || (inWitchSwamp && !current.is(WITCH_SWAMP_KEY))
                                || (!inMutantBugLair && !inWitchSwamp && shouldReplace(current, isDesertChunk))) {
                            biomes.getAndSetUnchecked(bx, by, bz, desired);
                            modified = true;
                        }
                    }
                }
            }
        }

        if (modified) {
            chunk.setUnsaved(true);
        }
        return modified;
    }

    public static void schedulePregenBiomeMigration(ServerLevel level, String reason) {
        if (!level.dimension().equals(ModDimensions.STARDEW_VALLEY)) return;
        if (!ENABLE_EAGER_PREGEN_MIGRATION) {
            StardewCraft.LOGGER.info("[VALLEY_BIOME] Skipping eager pregen biome migration ({}); chunks will patch on load", reason);
            return;
        }
        if (pregenMigrationScheduled) {
            StardewCraft.LOGGER.info("[VALLEY_BIOME] Pregen biome migration already scheduled; skipping duplicate request ({})", reason);
            return;
        }

        Queue<ChunkPos> chunks = new ArrayDeque<>();
        for (RegionCoordinate region : readManifestRegionCoordinates()) {
            int baseChunkX = region.regionX() * 32;
            int baseChunkZ = region.regionZ() * 32;
            for (int chunkOffsetZ = 0; chunkOffsetZ < 32; chunkOffsetZ++) {
                for (int chunkOffsetX = 0; chunkOffsetX < 32; chunkOffsetX++) {
                    chunks.add(new ChunkPos(baseChunkX + chunkOffsetX, baseChunkZ + chunkOffsetZ));
                }
            }
        }

        if (chunks.isEmpty()) {
            StardewCraft.LOGGER.warn("[VALLEY_BIOME] No pregen manifest regions found; biome migration not scheduled ({})", reason);
            return;
        }

        pregenMigrationScheduled = true;
        MinecraftServer server = level.getServer();
        StardewCraft.LOGGER.info("[VALLEY_BIOME] Scheduling pregen biome migration for {} chunks ({})", chunks.size(), reason);
        server.tell(new TickTask(server.getTickCount() + 1,
                () -> runPregenBiomeMigrationBatch(server, reason, chunks, 0, 0)));
    }

    private static void runPregenBiomeMigrationBatch(
            MinecraftServer server,
            String reason,
            Queue<ChunkPos> chunks,
            int scannedChunks,
            int modifiedChunks
    ) {
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            pregenMigrationScheduled = false;
            StardewCraft.LOGGER.warn("[VALLEY_BIOME] Stardew level missing; biome migration stopped ({})", reason);
            return;
        }

        int batchScanned = 0;
        int batchModified = 0;
        try {
            while (batchScanned < CHUNKS_PER_TICK && !chunks.isEmpty()) {
                ChunkPos chunkPos = chunks.remove();
                LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
                if (patchChunk(level, chunk)) {
                    batchModified++;
                }
                batchScanned++;
            }
        } catch (Exception e) {
            pregenMigrationScheduled = false;
            StardewCraft.LOGGER.error("[VALLEY_BIOME] Pregen biome migration failed ({})", reason, e);
            return;
        }

        int totalScanned = scannedChunks + batchScanned;
        int totalModified = modifiedChunks + batchModified;
        if (!chunks.isEmpty()) {
            server.tell(new TickTask(server.getTickCount() + 1,
                    () -> runPregenBiomeMigrationBatch(server, reason, chunks, totalScanned, totalModified)));
            return;
        }

        level.getChunkSource().save(false);
        pregenMigrationScheduled = false;
        StardewCraft.LOGGER.info("[VALLEY_BIOME] Completed pregen biome migration ({}): scanned {} chunks, modified {} chunks",
                reason, totalScanned, totalModified);
    }

    private static List<RegionCoordinate> readManifestRegionCoordinates() {
        List<RegionCoordinate> regions = new ArrayList<>();
        try (InputStream input = StardewBiomePatcher.class.getClassLoader().getResourceAsStream(MANIFEST_RESOURCE)) {
            if (input == null) {
                return regions;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    Matcher matcher = REGION_FILE_PATTERN.matcher(trimmed);
                    if (!matcher.matches()) {
                        continue;
                    }
                    regions.add(new RegionCoordinate(
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2))));
                }
            }
        } catch (Exception e) {
            StardewCraft.LOGGER.warn("[VALLEY_BIOME] Failed reading pregen biome migration manifest: {}", e.getMessage());
        }
        return regions;
    }

    private record RegionCoordinate(int regionX, int regionZ) {}

    /**
     * Returns true if this chunk's block range overlaps the desert schem bounding box.
     */
    private static boolean isDesertChunk(ChunkPos pos) {
        int chunkMinX = pos.getMinBlockX();
        int chunkMaxX = pos.getMaxBlockX();
        int chunkMinZ = pos.getMinBlockZ();
        int chunkMaxZ = pos.getMaxBlockZ();
        return chunkMaxX >= DESERT_MIN_X && chunkMinX <= DESERT_MAX_X
                && chunkMaxZ >= DESERT_MIN_Z && chunkMinZ <= DESERT_MAX_Z;
    }

    /**
     * Returns true for biomes that should be replaced with the target biome.
     * <ul>
     *   <li>非沙漠区域：替换所有 vanilla (minecraft:*) 生物群系为 stardew_default。</li>
     *   <li>沙漠区域：替换所有 vanilla 以及 stardewcraft:stardew_default（之前被
     *       误覆写的 chunk 也要纠正）为 calico_desert，但保留已是 calico_desert 的格子。</li>
     * </ul>
     */
    private static boolean shouldReplace(Holder<Biome> biome, boolean isDesertChunk) {
        return biome.unwrapKey().map(key -> {
            String ns = key.location().getNamespace();
            String path = key.location().getPath();
            if ("minecraft".equals(ns)) return true;
            if (isDesertChunk && "stardewcraft".equals(ns) && "stardew_default".equals(path)) {
                return true;
            }
            return false;
        }).orElse(false);
    }
}
