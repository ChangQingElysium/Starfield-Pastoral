package com.stardew.craft.mixin;

import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): network half of {@link ItemStackQualityComponentsMixin}. 1.21.1 decodes network stacks through
 * the {@code ItemStack} master constructor (normalized at its RETURN); 1.20.1 {@code readItem} builds the stack
 * first and then applies the share tag, so the same normalization runs on the finished stack.
 */
@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufQualityComponentsMixin {
    @Inject(method = "readItem()Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void stardewcraft$normalizeQualityComponents(CallbackInfoReturnable<ItemStack> cir) {
        QualityHelper.normalizeQualityComponents(cir.getReturnValue());
    }
}
