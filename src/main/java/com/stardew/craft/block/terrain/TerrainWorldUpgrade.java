package com.stardew.craft.block.terrain;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

/** One-time saved-terrain repair, run on chunk NBT before Minecraft resolves block IDs. */
public final class TerrainWorldUpgrade {
    public static final String VERSION_KEY = "stardewcraft:terrain_variants_version";
    public static final int VERSION = 1;
    private static final Codec<PalettedContainer<BlockState>> STATES = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
    private TerrainWorldUpgrade() {}

    /** Stable, per-position weighted selection for bulk placement; player placement keeps its existing RNG. */
    public static BlockState varied(BlockState state, long seed, BlockPos pos) {
        var property = TerrainVariants.property(state);
        if (property == null || property == TerrainVariants.ASPHALT || property == TerrainVariants.SAND || property == TerrainVariants.PAVING || property == TerrainVariants.CLIFF || state.getValue(property) != 0) return state;
        long hash = seed ^ pos.asLong() ^ 0x9E3779B97F4A7C15L;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        int roll = (int) Long.remainderUnsigned(hash, 100);
        return state.setValue(property, property == TerrainVariants.GRASS
                ? TerrainVariantWeights.grass(roll) : TerrainVariantWeights.dirt(roll));
    }

    public static boolean upgrade(CompoundTag chunk, long seed) {
        if (chunk.getInt(VERSION_KEY) >= VERSION) return false;
        int baseX = chunk.getInt("xPos") << 4, baseZ = chunk.getInt("zPos") << 4;
        var sections = chunk.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < sections.size(); i++) {
            var section = sections.getCompound(i);
            if (!section.contains("block_states", Tag.TAG_COMPOUND)) continue;
            var raw = section.getCompound("block_states").copy();
            var palette = raw.getList("palette", Tag.TAG_COMPOUND);
            boolean eligible = false;
            for (int p = 0; p < palette.size(); p++) {
                var entry = palette.getCompound(p);
                String name = entry.getString("Name");
                // Retirement data only: old blocks are no longer registered, rendered, spawned or usable.
                if (name.equals("stardewcraft:artifact_spot_dirt")) {
                    entry.putString("Name", "stardewcraft:dirt"); entry.remove("Properties");
                } else if (name.equals("stardewcraft:desert_artifact_spot") || name.equals("stardewcraft:beach_artifact_spot")) {
                    entry.putString("Name", "minecraft:sand"); entry.remove("Properties");
                }
                eligible |= !name.equals(entry.getString("Name"))
                        || name.equals("stardewcraft:dirt") || name.equals("stardewcraft:grass_block");
            }
            if (!eligible) continue;
            var states = com.stardew.craft.port.PortDataResults.getOrThrow(STATES.parse(NbtOps.INSTANCE, raw)
                    .promotePartial(error -> com.stardew.craft.StardewCraft.LOGGER.warn("[Terrain upgrade] {}", error)));
            int baseY = section.getByte("Y") << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                var current = states.get(x, y, z);
                var next = varied(current, seed, new BlockPos(baseX + x, baseY + y, baseZ + z));
                if (next != current) states.set(x, y, z, next);
            }
            section.put("block_states", com.stardew.craft.port.PortDataResults.getOrThrow(STATES.encodeStart(NbtOps.INSTANCE, states)));
        }
        chunk.putInt(VERSION_KEY, VERSION);
        chunk.putBoolean("shouldSave", true);
        return true;
    }
}
