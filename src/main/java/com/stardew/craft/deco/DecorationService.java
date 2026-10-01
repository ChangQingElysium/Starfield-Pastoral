package com.stardew.craft.deco;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.FlooringBlock;
import com.stardew.craft.block.utility.LegacyWallpaperBlock;
import com.stardew.craft.block.utility.WallpaperBlock;
import com.stardew.craft.blockentity.DecorBlockEntity;
import com.stardew.craft.network.payload.OpenDecorationScreenPayload;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DecorationService {
    private static final int MAX_CONNECTED_BLOCKS = 8192;

    private DecorationService() {
    }

    @SuppressWarnings("null")
    public static void openSelection(ServerPlayer player, BlockPos pos, DecorationType type) {
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        if (!isTargetBlock(state.getBlock(), type)) {
            return;
        }

        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        List<OpenDecorationScreenPayload.DecorationOption> options = new ArrayList<>();
        for (DecorationStyle style : DecorationStyleRegistry.getStyles(type)) {
            boolean unlocked = data.isDecorationUnlocked(type, style.id());
            options.add(new OpenDecorationScreenPayload.DecorationOption(
                style.id(),
                style.texture().toString(),
                style.texWidth(),
                style.texHeight(),
                style.sourceX(),
                style.sourceY(),
                style.sourceWidth(),
                style.sourceHeight(),
                unlocked,
                style.unlockHintKey(),
                style.sortOrder()
            ));
        }

        options.sort(Comparator
            .comparing((OpenDecorationScreenPayload.DecorationOption o) -> !o.unlocked())
            .thenComparingInt(OpenDecorationScreenPayload.DecorationOption::sortOrder));

        String currentStyle = DecorationStyleRegistry.getDefaultStyleId(type);
        int currentSegment = -1;
        if (type == DecorationType.WALLPAPER && state.getBlock() instanceof WallpaperBlock wallpaper) {
            currentStyle = wallpaper.getStyleId();
        } else if (level.getBlockEntity(pos) instanceof DecorBlockEntity decorBe) {
            currentStyle = decorBe.getStyleId();
            if (type == DecorationType.WALLPAPER) {
                currentSegment = decorBe.getSegmentOverride();
            }
        } else if (type == DecorationType.WALLPAPER && state.hasProperty(LegacyWallpaperBlock.STYLE)) {
            currentStyle = WallpaperStyles.fromLegacyVisualIndex(state.getValue(LegacyWallpaperBlock.STYLE));
        }

        PacketDistributor.sendToPlayer(player,
            new OpenDecorationScreenPayload(type.name(), pos, currentStyle, options, currentSegment));
    }

    @SuppressWarnings("null")
    public static int applyToConnected(Level level, BlockPos start, DecorationType type, String styleId, int segment) {
        if (!isTargetBlock(level.getBlockState(start).getBlock(), type)) {
            return 0;
        }

        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);

        int changed = 0;
        while (!queue.isEmpty() && changed < MAX_CONNECTED_BLOCKS) {
            BlockPos pos = queue.poll();
            if (!visited.add(pos)) {
                continue;
            }
            if (!isTargetBlock(level.getBlockState(pos).getBlock(), type)) {
                continue;
            }

            if (applyAt(level, pos, type, styleId, segment)) {
                changed++;
            }

            queue.add(pos.north());
            queue.add(pos.south());
            queue.add(pos.east());
            queue.add(pos.west());
            queue.add(pos.above());
            queue.add(pos.below());
        }

        return changed;
    }

    /** Apply a style to all matching blocks within an AABB region (inclusive). */
    @SuppressWarnings("null")
    public static int applyToRegion(Level level, BlockPos cornerA, BlockPos cornerB, DecorationType type,
                                    String styleId, int segment) {
        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        int changed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (!isTargetBlock(level.getBlockState(cursor).getBlock(), type)) {
                        continue;
                    }
                    if (applyAt(level, cursor.immutable(), type, styleId, segment)) {
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    private static boolean isTargetBlock(Block block, DecorationType type) {
        return type == DecorationType.WALLPAPER
            ? block instanceof WallpaperBlock || block == ModBlocks.WALLPAPER_BLOCK.get()
            : block == ModBlocks.FLOORING_BLOCK.get();
    }

    private static boolean applyAt(Level level, BlockPos pos, DecorationType type, String styleId, int segment) {
        if (type == DecorationType.WALLPAPER) {
            return applyWallpaper(level, pos, styleId, segment);
        }
        return applyFlooring(level, pos, styleId);
    }

    private static boolean applyWallpaper(Level level, BlockPos pos, String styleId, int requestedSegment) {
        BlockState current = level.getBlockState(pos);
        if (!isTargetBlock(current.getBlock(), DecorationType.WALLPAPER)) {
            return false;
        }

        int preservedSegment = current.hasProperty(WallpaperBlock.SEGMENT)
            ? current.getValue(WallpaperBlock.SEGMENT)
            : 0;
        int segment = requestedSegment >= 0 ? requestedSegment : preservedSegment;
        WallpaperBlock target = ModBlocks.getWallpaperStyleBlock(styleId).get();
        BlockState updated = target.defaultBlockState().setValue(WallpaperBlock.SEGMENT, segment);
        if (updated != current) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
        return true;
    }

    private static boolean applyFlooring(Level level, BlockPos pos, String styleId) {
        BlockState current = level.getBlockState(pos);
        if (!current.is(ModBlocks.FLOORING_BLOCK.get())) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        DecorBlockEntity decor;
        if (be instanceof DecorBlockEntity existing) {
            decor = existing;
        } else if (current.getBlock() instanceof EntityBlock entityBlock
            && entityBlock.newBlockEntity(pos, current) instanceof DecorBlockEntity created) {
            level.setBlockEntity(created);
            decor = created;
        } else {
            return false;
        }

        decor.setStyleId(styleId);
        int visual = DecorationStyleRegistry.getVisualIndex(DecorationType.FLOORING, styleId);
        int part = Math.floorMod(pos.getZ(), 2) * 2 + Math.floorMod(pos.getX(), 2);
        BlockState updated = current.setValue(FlooringBlock.STYLE, visual).setValue(FlooringBlock.PART, part);
        if (updated != current) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
        return true;
    }
}
