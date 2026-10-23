package com.stardew.craft.platform.mixin.event.entity.living.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.entity.living.ItemUseFinishHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class ItemUseFinishMixin {
    @WrapOperation(method = "completeUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack stardewcraft$finish(ItemStack stack, Level level, LivingEntity user, Operation<ItemStack> original) {
        return ItemUseFinishHooks.finish(user, stack, user.getUseItemRemainingTicks(), () -> original.call(stack, level, user));
    }
}
