package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.ResourceClumpBlock;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.List;

public final class CoalForestClumpSpawnService {
    private static final String INIT_DATA_ID = "stardewcraft_coal_forest_clumps_init";
    private static final int CLEAR_MAX_Y = CoalForestArea.MAX_Y + 8;
    private static final List<BlockPos> LARGE_STUMP_POSITIONS = List.of(
            new BlockPos(-205, 68, 5),
            new BlockPos(-235, 68, 9),
            new BlockPos(-231, 68, 7),
            new BlockPos(-237, 68, 5),
            new BlockPos(-212, 68, 35),
            new BlockPos(-223, 68, 35));

    private CoalForestClumpSpawnService() {
    }

    public static void onNewDay(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        ensureRegionChunksLoaded(level);
        clearExisting(level);

        RandomSource random = level.getRandom();
        int spawnedLargeStumps = 0;
        for (BlockPos pos : LARGE_STUMP_POSITIONS) {
            if (tryPlaceAt(level, random, ModBlocks.LARGE_STUMP.get(), pos)) {
                spawnedLargeStumps++;
            } else {
                StardewCraft.LOGGER.warn("[SECRET_WOODS] Failed to place large stump at {}", pos);
            }
        }

        StardewCraft.LOGGER.info("[SECRET_WOODS] Daily stump respawn: largeStump={}/{}",
                spawnedLargeStumps, LARGE_STUMP_POSITIONS.size());
    }

    public static void ensureInitialSpawn(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        CoalForestClumpInitData data = level.getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(CoalForestClumpInitData.factory()), com.stardew.craft.port.PortSavedData.constructor(CoalForestClumpInitData.factory()), INIT_DATA_ID);
        if (data.initialized()) {
            return;
        }

        onNewDay(level);
        data.setInitialized(true);
    }

    private static boolean tryPlaceAt(ServerLevel level, RandomSource random, Block block, BlockPos mainPos) {
        if (!(block instanceof ResourceClumpBlock clump)) {
            return false;
        }

        if (!CoalForestArea.containsGround(mainPos)) {
            return false;
        }

        if (!level.getBlockState(mainPos.below()).isFaceSturdy(level, mainPos.below(), Direction.UP)) {
            return false;
        }

        if (!level.getBlockState(mainPos).canBeReplaced()) {
            return false;
        }

        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos supportPos = mainPos.offset(dx, -1, dz);
                BlockPos lowerPos = mainPos.offset(dx, 0, dz);
                BlockPos upperPos = lowerPos.above();

                if (!CoalForestArea.containsColumn(lowerPos)) {
                    return false;
                }
                if (!level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP)) {
                    return false;
                }
                if (!level.getBlockState(lowerPos).canBeReplaced()) {
                    return false;
                }
                if (!level.getBlockState(upperPos).canBeReplaced()) {
                    return false;
                }
            }
        }

        BlockState state = clump.defaultBlockState()
                .setValue(com.stardew.craft.block.decor.MapDecorStaticBlock.PART,
                        com.stardew.craft.block.decor.MapDecorStaticBlock.Part.MAIN)
                .setValue(com.stardew.craft.block.decor.MapDecorStaticBlock.FACING, facing);
        level.setBlock(mainPos, state, Block.UPDATE_ALL);
        clump.setPlacedBy(level, mainPos, state, null, ItemStack.EMPTY);
        return true;
    }

    private static void clearExisting(ServerLevel level) {
        for (int x = CoalForestArea.MIN_X; x <= CoalForestArea.MAX_X; x++) {
            for (int z = CoalForestArea.MIN_Z; z <= CoalForestArea.MAX_Z; z++) {
                for (int y = CoalForestArea.MIN_Y; y <= CLEAR_MAX_Y; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof ResourceClumpBlock)) {
                        continue;
                    }
                    if (!state.hasProperty(com.stardew.craft.block.decor.MapDecorStaticBlock.PART)
                            || state.getValue(com.stardew.craft.block.decor.MapDecorStaticBlock.PART)
                            != com.stardew.craft.block.decor.MapDecorStaticBlock.Part.MAIN) {
                        continue;
                    }
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void ensureRegionChunksLoaded(ServerLevel level) {
        int minChunkX = CoalForestArea.MIN_X >> 4;
        int maxChunkX = CoalForestArea.MAX_X >> 4;
        int minChunkZ = CoalForestArea.MIN_Z >> 4;
        int maxChunkZ = CoalForestArea.MAX_Z >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static final class CoalForestClumpInitData extends SavedData {
        private boolean initialized;

        private CoalForestClumpInitData(boolean initialized) {
            this.initialized = initialized;
        }

        static com.stardew.craft.port.PortSavedData.Factory<CoalForestClumpInitData> factory() {
            return new com.stardew.craft.port.PortSavedData.Factory<>(
                    () -> new CoalForestClumpInitData(false),
                    CoalForestClumpInitData::load);
        }

        static CoalForestClumpInitData load(CompoundTag tag, HolderLookup.Provider registries) {
            return new CoalForestClumpInitData(tag.getBoolean("Initialized"));
        }

        boolean initialized() {
            return initialized;
        }

        void setInitialized(boolean value) {
            if (initialized != value) {
                initialized = value;
                setDirty();
            }
        }

        @Override
        public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
            tag.putBoolean("Initialized", initialized);
            return tag;
        }
    }

}