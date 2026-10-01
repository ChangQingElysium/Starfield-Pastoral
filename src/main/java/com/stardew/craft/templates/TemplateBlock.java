package com.stardew.craft.templates;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import com.stardew.craft.port.net.neoforged.neoforge.common.extensions.IBlockExtension;

/** Marker shared by simple and vanilla-derived material template blocks. */
public interface TemplateBlock extends IBlockExtension {
    TemplateShape templateShape();

    @Override
    default boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    @Override
    default int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        // The lighting worker cannot read server BlockEntities. MinecraftForge stores and syncs this cache with the chunk.
        var lights = level.getAuxLightManager(pos);
        return lights == null ? 0 : lights.getLightAt(pos);
    }
}
