// Upstream-SHA256: c2da2e0f36a7bbabe607cd8a157ec784b092bff36240d00baafe1989ad606ca7
package com.stardew.craft.mixin;

import com.stardew.craft.integration.ae2.Ae2FacadeExclusions;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.items.parts.FacadeItem", remap = false)
public abstract class Ae2FacadeItemMixin {
    // AE2 methods keep their external names; unique selectors avoid named Minecraft descriptors.
    // The typed callback descriptors are remapped normally in the production classfile.
    @Inject(method = "createFacadeForItem",
            at = @At("HEAD"), cancellable = true)
    private void stardewcraft$excludeBlocks(ItemStack stack, boolean returnItem,
                                          CallbackInfoReturnable<ItemStack> callback) {
        if (Ae2FacadeExclusions.isExcluded(stack)) callback.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "createFacadeForItemUnchecked",
            at = @At("HEAD"), cancellable = true)
    private void stardewcraft$excludeUncheckedBlocks(ItemStack stack, CallbackInfoReturnable<ItemStack> callback) {
        if (Ae2FacadeExclusions.isExcluded(stack)) callback.setReturnValue(ItemStack.EMPTY);
    }
}
