package com.stardew.craft.mixin;

import com.stardew.craft.port.PortItemNbt;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.21 getHoverName: custom_name, then item_name, then the Item's ordinary name. */
@Mixin(ItemStack.class)
public abstract class PortItemStackBaseNameMixin {
    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void stardewcraft$independentBaseName(CallbackInfoReturnable<Component> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.hasCustomHoverName()) return;
        Component name = PortItemNbt.itemName(stack);
        if (name != null) callback.setReturnValue(name);
    }
}
