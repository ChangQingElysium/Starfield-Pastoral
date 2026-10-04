package com.stardew.craft.mixin;

import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Normalize saved/network/copied quality stacks before they enter inventory comparisons.
 * Vanilla component equality and hashing stay strict; names, flower colors and all other data survive.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackQualityComponentsMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V",
            at = @At("RETURN"))
    private void stardewcraft$normalizeQualityComponents(CallbackInfo ci) {
        QualityHelper.normalizeQualityComponents((ItemStack) (Object) this);
    }
}
