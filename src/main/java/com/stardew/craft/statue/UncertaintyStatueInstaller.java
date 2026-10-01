package com.stardew.craft.statue;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

public final class UncertaintyStatueInstaller extends SavedData {
    private static final String DATA_NAME = "stardew_uncertainty_statue";
    private static final int SITE_VERSION = 3;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final BlockPos STATUE_POS = new BlockPos(4, 51, 57);

    private int placedVersion = 0;

    public UncertaintyStatueInstaller() {
    }

    public static UncertaintyStatueInstaller get(ServerLevel anyLevelInServer) {
        ServerLevel overworld = anyLevelInServer.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) {
            return new UncertaintyStatueInstaller();
        }
        return overworld.getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(factory()), com.stardew.craft.port.PortSavedData.constructor(factory()), DATA_NAME);
    }

    public void resetForMigration() {
        placedVersion = 0;
        setDirty();
    }

    public void ensurePlaced(ServerLevel stardewLevel) {
        if (placedVersion >= SITE_VERSION) {
            return;
        }
        if (!ModDimensions.STARDEW_VALLEY.equals(stardewLevel.dimension())) {
            return;
        }

        StardewCraft.LOGGER.info("[UNCERTAINTY_STATUE] Installing statue (version {} -> {})", placedVersion, SITE_VERSION);
        if (placedVersion == 0) clearRequestedStrip(stardewLevel);
        if (!placeStatue(stardewLevel)) {
            StardewCraft.LOGGER.warn("[UNCERTAINTY_STATUE] New footprint is obstructed; migration will retry on next installation check");
            return;
        }
        // v1 extended along X; v2 extended north. Keep any cell owned by the rotated statue.
        MapDecorStaticBlock.runWithDropsSuppressed(() -> {
            for (BlockPos oldColumn : new BlockPos[]{STATUE_POS.west(), STATUE_POS.east(), STATUE_POS.north()}) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos oldCell = oldColumn.above(dy);
                    BlockState oldState = stardewLevel.getBlockState(oldCell);
                    if (oldState.is(ModBlocks.UNCERTAINTY_STATUE.get())
                            && oldState.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.EXTENSION
                            && !STATUE_POS.equals(((MapDecorStaticBlock) ModBlocks.UNCERTAINTY_STATUE.get())
                                .findMainPos(stardewLevel, oldCell, oldState))) {
                        stardewLevel.removeBlock(oldCell, false);
                    }
                }
            }
        });
        placedVersion = SITE_VERSION;
        setDirty();
    }

    private static void clearRequestedStrip(ServerLevel level) {
        for (int x = 3; x <= 6; x++) {
            level.setBlock(new BlockPos(x, 51, 57), Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    private static boolean placeStatue(ServerLevel level) {
        BlockState state = ModBlocks.UNCERTAINTY_STATUE.get().defaultBlockState()
            .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
            .setValue(MapDecorStaticBlock.FACING, Direction.SOUTH);
        level.setBlock(STATUE_POS, state, FLAGS);
        return ((MapDecorStaticBlock) ModBlocks.UNCERTAINTY_STATUE.get()).placeExtensions(level, STATUE_POS, state);
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("PlacedVersion", placedVersion);
        return tag;
    }

    private static UncertaintyStatueInstaller load(CompoundTag tag, HolderLookup.Provider provider) {
        UncertaintyStatueInstaller installer = new UncertaintyStatueInstaller();
        installer.placedVersion = tag.getInt("PlacedVersion");
        return installer;
    }

    public static com.stardew.craft.port.PortSavedData.Factory<UncertaintyStatueInstaller> factory() {
        return new com.stardew.craft.port.PortSavedData.Factory<>(UncertaintyStatueInstaller::new, UncertaintyStatueInstaller::load);
    }
}
