package com.stardew.craft.platform.mixin.item.transfer.mixin;


import com.stardew.craft.platform.fabric.item.transfer.XoroshiroRandomState;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(XoroshiroRandomSource.class)
public interface XoroshiroRandomStateMixin extends XoroshiroRandomState {
    @Override @Accessor("randomNumberGenerator") Xoroshiro128PlusPlus stardewcraft$generator();
    @Override @Accessor("randomNumberGenerator") void stardewcraft$generator(Xoroshiro128PlusPlus value);
}
