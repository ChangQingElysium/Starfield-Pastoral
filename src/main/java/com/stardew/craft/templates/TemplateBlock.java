package com.stardew.craft.templates;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.extensions.IForgeBlock;

/** Marker shared by simple and vanilla-derived material template blocks. */
public interface TemplateBlock extends IForgeBlock {
    TemplateShape templateShape();

    // PORT(1.20.1): Forge has no hasDynamicLightEmission hook; getLightEmission below honours it instead.
    default boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    @Override
    default int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        // PORT(1.20.1): ChunkAccess#findBlocks pre-filters sections with BlockPos.ZERO; MinecraftForge skips that probe for
        // blocks with dynamic light emission, so report "may emit" for the probe position.
        if (pos == BlockPos.ZERO && hasDynamicLightEmission(state)) return 15;
        // The lighting worker cannot read server BlockEntities. MinecraftForge stores and syncs this cache with the chunk.
        var lights = com.stardew.craft.port.PortAuxLight.getAuxLightManager(level, pos);
        return lights == null ? 0 : lights.getLightAt(pos);
    }
}
