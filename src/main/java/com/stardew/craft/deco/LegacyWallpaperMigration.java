package com.stardew.craft.deco;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.LegacyWallpaperBlock;
import com.stardew.craft.block.utility.WallpaperBlock;
import com.stardew.craft.blockentity.DecorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.List;

/** Lazily replaces legacy style-property wallpapers when their chunk is loaded. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class LegacyWallpaperMigration {
    private LegacyWallpaperMigration() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        level.getServer().tell(new net.minecraft.server.TickTask(
            level.getServer().getTickCount() + 1,
            () -> {
                LevelChunk loaded = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (loaded != null) {
                    migrateChunk(level, loaded);
                }
            }
        ));
    }

    private static void migrateChunk(ServerLevel level, LevelChunk chunk) {
        List<BlockPos> legacyPositions = new ArrayList<>();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()
                || !section.getStates().maybeHas(state -> state.is(ModBlocks.WALLPAPER_BLOCK.get()))) {
                continue;
            }

            int minY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (section.getBlockState(x, y, z).is(ModBlocks.WALLPAPER_BLOCK.get())) {
                            legacyPositions.add(new BlockPos(minX + x, minY + y, minZ + z));
                        }
                    }
                }
            }
        }

        boolean changed = false;
        for (BlockPos pos : legacyPositions) {
            changed |= migrateAt(level, pos);
        }
        if (changed) {
            chunk.setUnsaved(true);
        }
    }

    public static boolean migrateAt(ServerLevel level, BlockPos pos) {
        BlockState legacyState = level.getBlockState(pos);
        if (!legacyState.is(ModBlocks.WALLPAPER_BLOCK.get())) {
            return false;
        }

        String styleId = null;
        if (level.getBlockEntity(pos) instanceof DecorBlockEntity decor && WallpaperStyles.contains(decor.getStyleId())) {
            styleId = decor.getStyleId();
        }
        if (styleId == null) {
            styleId = WallpaperStyles.fromLegacyVisualIndex(legacyState.getValue(LegacyWallpaperBlock.STYLE));
        }

        int segment = legacyState.getValue(LegacyWallpaperBlock.SEGMENT);
        WallpaperBlock target = ModBlocks.getWallpaperStyleBlock(styleId).get();
        BlockState migrated = target.defaultBlockState().setValue(WallpaperBlock.SEGMENT, segment);
        return level.setBlock(pos, migrated, Block.UPDATE_CLIENTS);
    }
}
