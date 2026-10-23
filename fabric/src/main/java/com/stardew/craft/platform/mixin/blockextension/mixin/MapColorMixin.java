package com.stardew.craft.platform.mixin.blockextension.mixin;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stardew.craft.platform.fabric.blockextension.BlockBehaviors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class MapColorMixin {
    @Shadow protected abstract BlockState asState();
    @ModifyReturnValue(method = "getMapColor", at = @At("RETURN"), require = 1)
    private MapColor stardewcraft$contextualMapColor(MapColor original, BlockGetter view, BlockPos pos) {
        return BlockBehaviors.resolveMapColor(asState(), view, pos, original);
    }
}
