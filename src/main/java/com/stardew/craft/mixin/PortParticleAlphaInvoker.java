package com.stardew.craft.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * PORT(1.20.1): access to the protected {@code Particle#setAlpha} for the ported coloured
 * {@code minecraft:entity_effect} provider (1.21.1 {@code SpellParticle.MobEffectProvider} sets the option's alpha after
 * construction).
 */
@Mixin(Particle.class)
public interface PortParticleAlphaInvoker {
    @Invoker("setAlpha")
    void stardewcraft$setAlpha(float alpha);
}
