package com.stardew.craft.platform.mixin.blockextension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.blockextension.BlockBehaviors;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemEntity.class)
abstract class ItemEntityFrictionMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"), require = 1)
    private float stardewcraft$materialFriction(Block block, Operation<Float> original) {
        var entity = (ItemEntity) (Object) this;
        return BlockBehaviors.resolveFriction(block, entity, entity.getBlockPosBelowThatAffectsMyMovement(), original.call(block));
    }
}
