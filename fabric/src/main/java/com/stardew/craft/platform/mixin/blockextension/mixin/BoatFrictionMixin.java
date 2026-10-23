package com.stardew.craft.platform.mixin.blockextension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.blockextension.BlockBehaviors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Boat.class)
abstract class BoatFrictionMixin {
    @WrapOperation(method = "getGroundFriction", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"), require = 1)
    private float stardewcraft$contactFriction(Block block, Operation<Float> original,
            @Local BlockState state, @Local BlockPos.MutableBlockPos pos) {
        var boat = (Boat) (Object) this;
        return block instanceof com.stardew.craft.platform.fabric.blockextension.BlockBehaviorProvider provider
                ? provider.getFriction(state, boat.level(), pos, boat) : original.call(block);
    }
}
