package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.PortPlantSupport;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** PORT(1.20.1): NeoForge 1.21.1 rabbits raid carrots on any FarmBlock ({@code instanceof FarmBlock}). */
@Mixin(targets = "net.minecraft.world.entity.animal.Rabbit$RaidGardenGoal")
public abstract class PortRabbitRaidFarmlandMixin {
    @WrapOperation(method = "isValidTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private boolean stardewcraft$port121Farmland(BlockState state, Block expected, Operation<Boolean> original) {
        return expected == Blocks.FARMLAND ? PortPlantSupport.farmland(state) : original.call(state, expected);
    }
}
