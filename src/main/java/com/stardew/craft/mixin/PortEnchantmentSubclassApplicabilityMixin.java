package com.stardew.craft.mixin;

import com.stardew.craft.port.PortVanillaEnchantmentRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.BindingCurseEnchantment;
import net.minecraft.world.item.enchantment.DamageEnchantment;
import net.minecraft.world.item.enchantment.DigDurabilityEnchantment;
import net.minecraft.world.item.enchantment.DiggingEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ThornsEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): the vanilla 1.20.1 enchantment classes that override {@code canEnchant} and answer before reaching
 * {@code Enchantment#canEnchant} (Unbreaking: any damageable item; Sharpness/Smite/Bane: any {@code AxeItem};
 * Efficiency: any {@code ShearsItem}; Thorns: any {@code ArmorItem}; Curse of Binding: never shields). For
 * StardewCraft items answer with the 1.21.1 {@code supported_items} instead, like
 * {@link PortEnchantmentApplicabilityMixin}.
 */
@Mixin({DigDurabilityEnchantment.class, DamageEnchantment.class, DiggingEnchantment.class, ThornsEnchantment.class,
        BindingCurseEnchantment.class})
public abstract class PortEnchantmentSubclassApplicabilityMixin {
    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$supportedItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Boolean supported = PortVanillaEnchantmentRules.supports((Enchantment) (Object) this, stack);
        if (supported != null) cir.setReturnValue(supported);
    }
}
