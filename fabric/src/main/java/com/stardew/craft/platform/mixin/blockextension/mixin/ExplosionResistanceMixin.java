package com.stardew.craft.platform.mixin.blockextension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.blockextension.BlockBehaviors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExplosionDamageCalculator.class)
abstract class ExplosionResistanceMixin {
    @WrapOperation(method = "getBlockExplosionResistance", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;getExplosionResistance()F"), require = 1)
    private float stardewcraft$contextualResistance(Block block, Operation<Float> original, Explosion explosion,
            BlockGetter view, BlockPos pos, BlockState state, FluidState fluid) {
        // Native Optional/air handling, fluid max and entity-specific calculator overrides remain outside this call.
        return BlockBehaviors.resolveExplosionResistance(state, view, pos, explosion, original.call(block));
    }
}
