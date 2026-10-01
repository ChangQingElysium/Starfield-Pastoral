package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.event.PortDamageHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): {@code Player#actuallyHurt} fully overrides {@code LivingEntity#actuallyHurt}; same MinecraftForge damage
 * sequence hooks as {@link PortLivingEntityDamageMixin}.
 */
@Mixin(Player.class)
public abstract class PortPlayerDamageMixin {
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void stardewcraft$beginActuallyHurt(DamageSource source, float amount, CallbackInfo ci) {
        PortDamageHooks.beginActuallyHurt((Player) (Object) this);
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float stardewcraft$armorReduction(Player self, DamageSource source, float amount, Operation<Float> original) {
        return PortDamageHooks.afterArmor(self, amount, original.call(self, source, amount));
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float stardewcraft$magicReductionAndPre(Player self, DamageSource source, float amount, Operation<Float> original) {
        return PortDamageHooks.afterMagic(self, source, amount, original.call(self, source, amount));
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float stardewcraft$healthDamage(LivingEntity entity, DamageSource source, float amount, Operation<Float> original) {
        return PortDamageHooks.afterForgeLivingDamage(entity, amount, original.call(entity, source, amount));
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void stardewcraft$endActuallyHurt(DamageSource source, float amount, CallbackInfo ci) {
        PortDamageHooks.endActuallyHurt((Player) (Object) this, source);
    }
}
