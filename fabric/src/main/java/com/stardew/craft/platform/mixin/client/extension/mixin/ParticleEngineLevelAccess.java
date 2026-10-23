package com.stardew.craft.platform.mixin.client.extension.mixin;


import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ParticleEngine.class)
public interface ParticleEngineLevelAccess {
    @Accessor("level") ClientLevel stardewcraft$level();
}
