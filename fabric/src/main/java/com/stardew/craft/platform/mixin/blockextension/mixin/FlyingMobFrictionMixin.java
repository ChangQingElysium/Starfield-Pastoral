package com.stardew.craft.platform.mixin.blockextension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.blockextension.BlockBehaviors;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FlyingMob.class)
abstract class FlyingMobFrictionMixin {
    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"), require = 2)
    private float stardewcraft$materialFriction(Block block, Operation<Float> original) {
        var entity = (FlyingMob) (Object) this;
        return BlockBehaviors.resolveFriction(block, entity, entity.getBlockPosBelowThatAffectsMyMovement(), original.call(block));
    }
}
