package com.stardew.craft.gingerisland;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** VolcanoDungeon.performToolAction/CoolLava: watering makes shared, walkable surface stones.
 * Ordinary-world lava and the fifth floor's water refill pool are unaffected.
 */
public final class VolcanoCooling {
    private static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME,
            new ResourceLocation("stardewcraft", "volcano_cooling"));
    private VolcanoCooling() {}

    public static boolean canCool(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.LAVA) && isCoolingFloor(level, pos)
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static boolean isCoolingFloor(Level level, BlockPos pos) {
        var locations = com.stardew.craft.api.v1.world.StardewLocations.hierarchy(level.dimension().location(), pos);
        if (locations.isEmpty()) return level.getBiome(pos).is(BIOMES);
        // Caldera only produces steam; the fifth floor is the watering-can refill room.
        var dungeon = locations.stream().filter(com.stardew.craft.mining.IslandStoneRewards::isVolcano).findFirst();
        if (dungeon.isEmpty()) return false;
        var floor = dungeon.get().property(IslandContext.VOLCANO_FLOOR);
        if (floor.isEmpty()) return true;
        try {
            int index = Integer.parseInt(floor.get());
            return index >= 0 && index <= 9 && index != 5;
        } catch (NumberFormatException invalid) { return false; }
    }

    public static boolean cool(Level level, BlockPos pos) {
        if (!canCool(level, pos)) return false;
        if (level instanceof ServerLevel server) {
            var stones = (CooledLavaBlock) GingerIslandBlocks.get("ginger_volcano_cooled_lava");
            // Flag 3 sends the authoritative state to visitors and updates all four edge masks.
            if (!server.setBlock(pos, stones.connected(server, pos), 3)) return false;
            server.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                    5, .25, .12, .25, .015);
            server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, .7F, 1.3F);
        }
        return true;
    }
}
