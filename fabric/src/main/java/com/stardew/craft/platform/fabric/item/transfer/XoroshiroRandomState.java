package com.stardew.craft.platform.fabric.item.transfer;

import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;

/** Updating the bit generator preserves the native source's existing Gaussian cache. */
public interface XoroshiroRandomState {
    Xoroshiro128PlusPlus stardewcraft$generator();
    void stardewcraft$generator(Xoroshiro128PlusPlus value);
}
