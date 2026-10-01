package com.stardew.craft.dimension;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.MiningCoordinates;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkEvent;

/**
 * 矿井维度群系补丁器 — 根据区块 Z 坐标推算楼层号，
 * 将 the_void 替换为对应的矿井群系（mines_20/60/100），
 * 使钓鱼系统能正确识别群系标签并返回对应鱼种。
 *
 * 楼层→群系映射（对应 SDV 矿井分段）：
 *   1-39层（Earth） → stardewcraft:mines_20
 *   40-79层（Frost） → stardewcraft:mines_60
 *   80-119层（Lava） → stardewcraft:mines_100
 *   0层/其他          → stardewcraft:mines_20（默认）
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class MineBiomePatcher {

    private static final ResourceKey<Biome> MINES_20_KEY =
            ResourceKey.create(Registries.BIOME, new ResourceLocation("stardewcraft", "mines_20"));
    private static final ResourceKey<Biome> MINES_60_KEY =
            ResourceKey.create(Registries.BIOME, new ResourceLocation("stardewcraft", "mines_60"));
    private static final ResourceKey<Biome> MINES_100_KEY =
            ResourceKey.create(Registries.BIOME, new ResourceLocation("stardewcraft", "mines_100"));

    private MineBiomePatcher() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(ModMiningDimensions.STARDEW_MINING)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        // 用区块中心 Z 坐标推算所属楼层
        int chunkCenterZ = (chunk.getPos().getMinBlockZ() + chunk.getPos().getMaxBlockZ()) / 2;
        int floor = estimateFloorFromZ(chunkCenterZ);
        patchChunk(serverLevel, chunk, floor);
    }

    /** Also runs after template placement, correcting previously saved void/legacy biome palettes. */
    public static void patchChunk(ServerLevel serverLevel, LevelChunk chunk, int floor) {
        Holder<Biome> targetBiome = getBiomeForFloor(serverLevel, floor);

        boolean modified = false;
        LevelChunkSection[] sections = chunk.getSections();
        for (LevelChunkSection section : sections) {
            if (section == null) continue;

            PalettedContainerRO<Holder<Biome>> biomesRO = section.getBiomes();
            if (!(biomesRO instanceof PalettedContainer<Holder<Biome>> biomes)) continue;

            for (int bx = 0; bx < 4; bx++) {
                for (int by = 0; by < 4; by++) {
                    for (int bz = 0; bz < 4; bz++) {
                        Holder<Biome> current = biomes.get(bx, by, bz);
                        if (!current.is(targetBiome.unwrapKey().orElseThrow())) {
                            biomes.getAndSetUnchecked(bx, by, bz, targetBiome);
                            modified = true;
                        }
                    }
                }
            }
        }

        if (modified) {
            chunk.setUnsaved(true);
        }
    }

    /**
     * 从 Z 坐标推算楼层号。
     * 每层中心 Z = floor × FLOOR_SPACING + 14，房间半径最大 60。
     * 0 层中心 Z = 0。
     */
    private static int estimateFloorFromZ(int z) {
        return com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(new net.minecraft.core.BlockPos(0,66,z));
    }

    private static Holder<Biome> getBiomeForFloor(ServerLevel level, int floor) {
        ResourceKey<Biome> key;
        if (floor > 120 || floor == com.stardew.craft.mining.SkullCavernRuntime.LOBBY) {
            key=ResourceKey.create(Registries.BIOME,new ResourceLocation("stardewcraft","skull_cavern"));
        } else if (floor >= 80) {
            key = MINES_100_KEY;
        } else if (floor >= 40) {
            key = MINES_60_KEY;
        } else {
            key = MINES_20_KEY;
        }
        return level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
    }
}
