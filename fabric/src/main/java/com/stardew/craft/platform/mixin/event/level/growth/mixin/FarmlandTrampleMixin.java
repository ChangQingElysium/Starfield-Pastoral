package com.stardew.craft.platform.mixin.event.level.growth.mixin;


import com.stardew.craft.platform.fabric.event.level.growth.GrowthEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

@Mixin(net.minecraft.world.level.block.FarmBlock.class)
abstract class FarmlandTrampleMixin {
    @WrapOperation(method = "fallOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/FarmBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"), require = 1)
    private void stardew$beforeTrample(Entity entity, BlockState state, Level level, BlockPos pos, Operation<Void> original,
            Level methodLevel, BlockState methodState, BlockPos methodPos, Entity methodEntity, float distance) {
        if (GrowthEvents.trample(level, pos, distance, entity)) original.call(entity, state, level, pos);
    }
}
