package com.stardew.craft.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.FenceGateBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PORT(1.20.1): NeoForge 1.21 exposes {@code FenceGateBlock#openSound/closeSound}; 1.20.1 keeps them private. */
@Mixin(FenceGateBlock.class)
public interface PortFenceGateBlockAccessor {
    @Accessor("openSound")
    SoundEvent stardewcraft$getOpenSound();

    @Accessor("closeSound")
    SoundEvent stardewcraft$getCloseSound();
}
