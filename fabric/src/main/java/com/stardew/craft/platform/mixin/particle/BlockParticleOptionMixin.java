package com.stardew.craft.platform.mixin.particle;

import com.stardew.craft.platform.fabric.particle.BlockParticleSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockParticleOption.class)
public abstract class BlockParticleOptionMixin implements BlockParticleSources.Source {
    @Unique private BlockPos stardewcraft$particleSource;

    @Override public BlockPos stardewcraft$sourcePos() { return stardewcraft$particleSource; }
    @Override public void stardewcraft$sourcePos(BlockPos pos) { stardewcraft$particleSource = pos; }
}
