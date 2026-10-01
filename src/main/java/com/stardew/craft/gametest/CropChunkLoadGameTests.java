package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.CropVisualStageSync;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.manager.CropGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_crop_chunk_load")
@PrefixGameTestTemplate(false)
public final class CropChunkLoadGameTests {
    @GameTest(templateNamespace = "stardewcraft_crop_chunk_load", template = "ring_utilities", timeoutTicks = 160,
            batch = "crop_load_unload")
    public static void unloadedAndReplacedChunksDoNotApplyOldRequests(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        level.setBlock(pos.below(), ModBlocks.FARMLAND.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(pos, ModBlocks.PARSNIP_CROP.get().defaultBlockState()
                .setValue(StardewCropBlock.GROWTH_STAGE, 0), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        var manager = CropGrowthManager.get(level);
        manager.addCrop(level, pos);
        manager.getState(level, pos).phase = 2;
        var chunk = level.getChunkAt(pos);
        CropVisualStageSync.loaded(new ChunkEvent.Load(chunk, false));
        CropVisualStageSync.unloaded(new ChunkEvent.Unload(chunk));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(chunk.getBlockState(pos).getValue(StardewCropBlock.GROWTH_STAGE) == 0,
                    "Unloaded chunk retained its pending visual write");
            var obsolete = new LevelChunk(level, chunk.getPos());
            CropVisualStageSync.loaded(new ChunkEvent.Load(obsolete, false));
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(chunk.getBlockState(pos).getValue(StardewCropBlock.GROWTH_STAGE) == 0,
                        "Obsolete chunk request wrote into a different chunk at the same coordinates");
                CropVisualStageSync.loaded(new ChunkEvent.Load(chunk, false));
                CropVisualStageSync.unloaded(new ChunkEvent.Unload(obsolete));
                helper.succeedWhen(() -> helper.assertTrue(
                        chunk.getBlockState(pos).getValue(StardewCropBlock.GROWTH_STAGE) == 3,
                        "Reloaded chunk failed to synchronize after an obsolete unload event"));
            });
        });
    }

    @GameTest(templateNamespace = "stardewcraft_crop_chunk_load", template = "ring_utilities", timeoutTicks = 160)
    public static void boundaryCropsOnlySyncAfterLoadCallback(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos west = new BlockPos((base.getX() & ~15) + 15, base.getY(), base.getZ());
        BlockPos east = west.east();
        for (BlockPos pos : new BlockPos[]{west, east}) {
            level.setBlock(pos.below(), ModBlocks.FARMLAND.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(pos, ModBlocks.PARSNIP_CROP.get().defaultBlockState()
                    .setValue(StardewCropBlock.GROWTH_STAGE, 0), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            var manager = CropGrowthManager.get(level);
            manager.addCrop(level, pos);
            manager.getState(level, pos).phase = 2;
        }
        for (BlockPos pos : new BlockPos[]{west, east}) {
            var chunk = level.getChunkAt(pos);
            CropVisualStageSync.loaded(new ChunkEvent.Load(chunk, false));
            helper.assertTrue(chunk.getBlockState(pos).getValue(StardewCropBlock.GROWTH_STAGE) == 0,
                    "Crop was changed inside the chunk load callback, which can deadlock neighbor loading");
        }
        helper.succeedWhen(() -> {
            for (BlockPos pos : new BlockPos[]{west, east}) {
                helper.assertTrue(level.getBlockState(pos).getValue(StardewCropBlock.GROWTH_STAGE) == 3,
                        "Loaded boundary crop did not restore its saved visual stage");
            }
        });
    }
}
