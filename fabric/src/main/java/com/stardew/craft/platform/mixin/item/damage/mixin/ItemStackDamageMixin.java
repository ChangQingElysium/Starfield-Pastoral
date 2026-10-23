package com.stardew.craft.platform.mixin.item.damage.mixin;


import com.stardew.craft.platform.fabric.item.damage.ItemDamageHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
abstract class ItemStackDamageMixin {
    @Inject(method = "canBeHurtBy", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$itemDamageVeto(DamageSource source, CallbackInfoReturnable<Boolean> result) {
        var stack = (ItemStack) (Object) this;
        if (!ItemDamageHooks.allowsDamage(stack.getItem(), stack, source)) result.setReturnValue(false);
    }
}
