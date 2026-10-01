package com.stardew.craft.specialorder;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

public final class SpecialOrderBoardInstaller extends SavedData {
    private static final String DATA_NAME = "stardew_special_order_board";
    private static final int SITE_VERSION = 2;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    public static final BlockPos BOARD_POS = new BlockPos(57, 64, 46);
    public static final BlockPos TICKET_BOX_POS = BOARD_POS.west(2);

    private int placedVersion = 0;

    public SpecialOrderBoardInstaller() {
    }

    public static SpecialOrderBoardInstaller get(ServerLevel anyLevelInServer) {
        ServerLevel overworld = anyLevelInServer.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) {
            return new SpecialOrderBoardInstaller();
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

        StardewCraft.LOGGER.info("[SPECIAL_ORDERS] Installing special orders board (version {} -> {})", placedVersion, SITE_VERSION);
        if (placeSite(stardewLevel, BOARD_POS)) {
            placedVersion = SITE_VERSION;
            setDirty();
        } else {
            StardewCraft.LOGGER.warn("[SPECIAL_ORDERS] Board site is obstructed; retaining its installation version for retry");
        }
    }

    /** Installs or upgrades the complete 3x2 board and its adjacent 1x2 collection box atomically. */
    public static boolean placeSite(ServerLevel level, BlockPos boardPos) {
        var previous = new java.util.LinkedHashMap<BlockPos, BlockState>();
        BlockPos ticketPos = boardPos.west(2);
        for (int dx = -2; dx <= 1; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                BlockPos pos = boardPos.offset(dx, dy, 0);
                var block = (MapDecorStaticBlock) (dx == -2 ? ModBlocks.PRIZE_TICKET_BOX.get() : ModBlocks.SPECIAL_ORDERS_BOARD.get());
                BlockPos anchor = dx == -2 ? ticketPos : boardPos;
                if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
                BlockState here = level.getBlockState(pos);
                boolean owned = here.is(block) && (pos.equals(anchor)
                        ? here.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.MAIN
                        : here.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.EXTENSION
                            && anchor.equals(block.findMainPos(level, pos, here)));
                if (level.getBlockEntity(pos) != null || (!here.canBeReplaced() && !owned)) return false;
                previous.put(pos, here);
            }
        }
        for (BlockPos anchor : java.util.List.of(boardPos, ticketPos)) {
            var block = (MapDecorStaticBlock) (anchor.equals(boardPos) ? ModBlocks.SPECIAL_ORDERS_BOARD.get() : ModBlocks.PRIZE_TICKET_BOX.get());
            BlockState state = block.defaultBlockState().setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                    .setValue(MapDecorStaticBlock.FACING, Direction.SOUTH);
            if ((!level.getBlockState(anchor).equals(state) && !level.setBlock(anchor, state, FLAGS))
                    || !block.placeExtensions(level, anchor, state)) {
                MapDecorStaticBlock.runWithDropsSuppressed(() -> previous.forEach((pos, before) -> level.setBlock(pos, before, FLAGS)));
                return false;
            }
        }
        return true;
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("PlacedVersion", placedVersion);
        return tag;
    }

    private static SpecialOrderBoardInstaller load(CompoundTag tag, HolderLookup.Provider provider) {
        SpecialOrderBoardInstaller installer = new SpecialOrderBoardInstaller();
        installer.placedVersion = tag.getInt("PlacedVersion");
        return installer;
    }

    public static com.stardew.craft.port.PortSavedData.Factory<SpecialOrderBoardInstaller> factory() {
        return new com.stardew.craft.port.PortSavedData.Factory<>(SpecialOrderBoardInstaller::new, SpecialOrderBoardInstaller::load);
    }
}
