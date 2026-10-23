package com.stardew.craft.platform.fabric.particle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;

/** Neo's optional source position is local metadata, deliberately absent from both native codecs. */
public final class BlockParticleSources {
    private BlockParticleSources() {}

    public interface Source {
        BlockPos stardewcraft$sourcePos();
        void stardewcraft$sourcePos(BlockPos pos);
    }

    public static BlockParticleOption withSource(BlockParticleOption option, BlockPos pos) {
        ((Source) option).stardewcraft$sourcePos(pos == null ? null : pos.immutable());
        return option;
    }

    public static BlockPos get(BlockParticleOption option) {
        return ((Source) option).stardewcraft$sourcePos();
    }
}
