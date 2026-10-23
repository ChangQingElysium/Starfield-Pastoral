package com.stardew.craft.festival;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.ParkedVehicleBlockEntity;
import com.stardew.craft.core.ModDimensions;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Upgrade the festival's retired block-built bus without replacing its surrounding map or saved backup. */
public final class DesertFestivalBusLayout {
    public static final BlockPos BUS_ANCHOR = new BlockPos(-224, 64, -180);
    public static final BlockPos LEGACY_ORIGIN = new BlockPos(-227, 64, -183);
    public static final String LEGACY_SCHEMATIC_PATH =
            "data/stardewcraft/structures/festivals/desert_festival_legacy_bus.schem";
    private static final int REPLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private DesertFestivalBusLayout() {}

    private static final class Layout {
        private static final SchematicSnapshot LEGACY = SchematicSnapshot.load(LEGACY_SCHEMATIC_PATH);
        private static final Map<BlockPos, BlockState> CURRENT = currentBus();

        private static Map<BlockPos, BlockState> currentBus() {
            var definition = FestivalMapOverlayRegistry.get("DesertFestival").orElseThrow();
            var snapshot = SchematicSnapshot.load(definition.festivalSchematicPath());
            var cells = new LinkedHashMap<BlockPos, BlockState>();
            for (int y = 0; y < snapshot.height(); y++) for (int z = 0; z < snapshot.length(); z++)
                for (int x = 0; x < snapshot.width(); x++) {
                    var state = snapshot.block(x, y, z);
                    if (state.is(ModBlocks.BUS.get())) cells.put(definition.origin().offset(x, y, z), state);
                }
            return Map.copyOf(cells);
        }
    }

    /** Idempotent, loaded-chunk-only recovery for an already open festival or an old restored map. */
    public static boolean repair(ServerLevel level) {
        if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY
                || Layout.LEGACY.isEmpty() || Layout.CURRENT.isEmpty()) return false;
        var old = Layout.LEGACY;
        // Do not force the desert to load just because the festival is open.
        for (BlockPos pos : BlockPos.betweenClosed(LEGACY_ORIGIN,
                LEGACY_ORIGIN.offset(old.width() - 1, old.height() - 1, old.length() - 1)))
            if (!level.hasChunkAt(pos)) return false;
        for (var entry : Layout.CURRENT.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos))
                return false;
            var state = level.getBlockState(pos);
            var blockEntity = level.getBlockEntity(pos);
            if (state.equals(entry.getValue())) {
                if (blockEntity != null && !(blockEntity instanceof ParkedVehicleBlockEntity)) return false;
            } else if (!(state.isAir() && blockEntity == null) && !isOwnedLegacy(level, pos)) {
                // A player or add-on changed a necessary cell. Preserve it and the old bus atomically.
                return false;
            }
        }
        MapDecorStaticBlock.runWithDropsSuppressed(() -> {
            for (BlockPos cursor : BlockPos.betweenClosed(LEGACY_ORIGIN,
                    LEGACY_ORIGIN.offset(old.width() - 1, old.height() - 1, old.length() - 1))) {
                BlockPos pos = cursor.immutable();
                if (isOwnedLegacy(level, pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), REPLACE_FLAGS);
            }
            Layout.CURRENT.forEach((pos, state) -> {
                if (!level.getBlockState(pos).equals(state)) level.setBlock(pos, state, REPLACE_FLAGS);
            });
            if (level.getBlockEntity(BUS_ANCHOR) == null) {
                level.setBlockEntity(new ParkedVehicleBlockEntity(BUS_ANCHOR, level.getBlockState(BUS_ANCHOR)));
                var state = level.getBlockState(BUS_ANCHOR);
                level.sendBlockUpdated(BUS_ANCHOR, state, state, Block.UPDATE_CLIENTS);
            }
        });
        return true;
    }

    private static boolean isOwnedLegacy(ServerLevel level, BlockPos pos) {
        BlockPos local = pos.subtract(LEGACY_ORIGIN);
        var snapshot = Layout.LEGACY;
        if (local.getX() < 0 || local.getY() < 0 || local.getZ() < 0
                || local.getX() >= snapshot.width() || local.getY() >= snapshot.height() || local.getZ() >= snapshot.length())
            return false;
        var expected = snapshot.block(local.getX(), local.getY(), local.getZ());
        if (expected.isAir() || !level.getBlockState(pos).equals(expected)) return false;
        var actualBlockEntity = level.getBlockEntity(pos);
        var raw = snapshot.blockEntity(local.getX(), local.getY(), local.getZ());
        if (raw == null || raw.isEmpty()) return actualBlockEntity == null;
        if (actualBlockEntity == null) return false;
        CompoundTag normalized = raw.contains("Data", Tag.TAG_COMPOUND) ? raw.getCompound("Data").copy() : raw.copy();
        if (!normalized.contains("id", Tag.TAG_STRING)) normalized.putString("id", raw.getString("Id"));
        normalized.putInt("x", pos.getX()); normalized.putInt("y", pos.getY()); normalized.putInt("z", pos.getZ());
        var expectedBlockEntity = BlockEntity.loadStatic(pos, expected, normalized, level.registryAccess());
        return expectedBlockEntity != null && actualBlockEntity.saveWithFullMetadata(level.registryAccess())
                .equals(expectedBlockEntity.saveWithFullMetadata(level.registryAccess()));
    }
}
