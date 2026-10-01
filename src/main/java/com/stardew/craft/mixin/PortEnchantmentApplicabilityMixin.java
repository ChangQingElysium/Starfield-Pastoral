package com.stardew.craft.mixin;

import com.stardew.craft.port.PortVanillaEnchantmentRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): 1.21.1 vanilla enchantment applicability for StardewCraft items (see
 * {@link PortVanillaEnchantmentRules}). {@code canEnchant} is the 1.20.1 equivalent of 1.21
 * {@code ItemStack#supportsEnchantment} (anvil, {@code /enchant}, {@code enchant_randomly}) and
 * {@code canApplyAtEnchantingTable(ItemStack)} of {@code ItemStack#isPrimaryItemFor} (enchanting table,
 * {@code enchant_with_levels}). Vanilla subclasses that override {@code canEnchant} with their own shortcuts are
 * covered by {@link PortEnchantmentSubclassApplicabilityMixin}. Other items/enchantments fall through unchanged.
 */
@Mixin(Enchantment.class)
public abstract class PortEnchantmentApplicabilityMixin {
    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$supportedItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Boolean supported = PortVanillaEnchantmentRules.supports((Enchantment) (Object) this, stack);
        if (supported != null) cir.setReturnValue(supported);
    }

    @Inject(method = "canApplyAtEnchantingTable(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void stardewcraft$primaryItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Boolean primary = PortVanillaEnchantmentRules.isPrimaryItemFor((Enchantment) (Object) this, stack);
        if (primary != null) cir.setReturnValue(primary);
    }
}
