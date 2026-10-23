package com.stardew.craft.platform.mixin.extension.mixin;


import com.stardew.craft.platform.fabric.extension.ItemDurabilityProvider;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemDurabilityMixin {
    @Inject(method = "isDamageableItem", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$itemDurability(CallbackInfoReturnable<Boolean> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!stack.isEmpty() && stack.getItem() instanceof ItemDurabilityProvider provider) callback.setReturnValue(provider.isDamageable(stack));
    }
}
