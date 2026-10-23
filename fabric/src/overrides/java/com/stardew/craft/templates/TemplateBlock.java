// Upstream-SHA256: 33304b763b2fa561ab9c07e8310320e5c7242419bb6d261312b40d753412fe2a
package com.stardew.craft.templates;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import com.stardew.craft.platform.fabric.light.LightEmissionProvider;

/** Marker shared by simple and vanilla-derived material template blocks. */
public interface TemplateBlock extends LightEmissionProvider, com.stardew.craft.platform.fabric.blockextension.BlockBehaviorProvider {
    TemplateShape templateShape();

    @Override
    default boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    @Override
    default int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        // Lighting workers read the chunk cache or an immutable render-region snapshot, never live block entities.
        var lights = com.stardew.craft.platform.fabric.light.AuxiliaryLights.getManager(level, pos);
        return lights == null ? 0 : lights.getLightAt(pos);
    }
}
