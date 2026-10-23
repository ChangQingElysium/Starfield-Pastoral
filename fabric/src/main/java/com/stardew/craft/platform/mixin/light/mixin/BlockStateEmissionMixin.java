package com.stardew.craft.platform.mixin.light.mixin;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stardew.craft.platform.fabric.light.LightEmissionHooks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateEmissionMixin {
    @Shadow protected abstract BlockState asState();

    @ModifyReturnValue(method = "getLightEmission", at = @At("RETURN"), require = 1)
    private int stardewcraft$stateBasedLight(int original) {
        return LightEmissionHooks.staticEmission(asState(), original);
    }
}
