package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.event.PortDamageHooks;
import com.stardew.craft.port.net.neoforged.neoforge.common.damagesource.DamageContainer;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): MinecraftForge 21.1 damage sequence hooks ({@code LivingIncomingDamageEvent}, {@code DamageContainer},
 * {@code LivingDamageEvent.Pre/Post}) on {@link LivingEntity}. Logic lives in {@link PortDamageHooks}.
 */
@Mixin(LivingEntity.class)
public abstract class PortLivingEntityDamageMixin implements PortDamageHooks.ContainerHolder {
    @Unique
    private final Deque<DamageContainer> stardewcraft$damageContainers = new ArrayDeque<>();

    @Override
    public Deque<DamageContainer> stardewcraft$damageContainers() {
        return this.stardewcraft$damageContainers;
    }

    @WrapMethod(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    private boolean stardewcraft$incomingDamage(DamageSource source, float amount, Operation<Boolean> original) {
        return PortDamageHooks.hurt((LivingEntity) (Object) this, source, amount, value -> original.call(source, value));
    }

    /** Vanilla's {@code invulnerableTime = 20} becomes the container's post-attack invulnerability. */
    @ModifyExpressionValue(
            method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "CONSTANT", args = "intValue=20"))
    private int stardewcraft$postAttackInvulnerability(int ticks) {
        return PortDamageHooks.postAttackInvulnerabilityTicks((LivingEntity) (Object) this, ticks);
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void stardewcraft$beginActuallyHurt(DamageSource source, float amount, CallbackInfo ci) {
        PortDamageHooks.beginActuallyHurt((LivingEntity) (Object) this);
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float stardewcraft$armorReduction(LivingEntity self, DamageSource source, float amount, Operation<Float> original) {
        return PortDamageHooks.afterArmor(self, amount, original.call(self, source, amount));
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float stardewcraft$magicReductionAndPre(LivingEntity self, DamageSource source, float amount, Operation<Float> original) {
        return PortDamageHooks.afterMagic(self, source, amount, original.call(self, source, amount));
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float stardewcraft$healthDamage(LivingEntity entity, DamageSource source, float amount, Operation<Float> original) {
        return PortDamageHooks.afterForgeLivingDamage(entity, amount, original.call(entity, source, amount));
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void stardewcraft$endActuallyHurt(DamageSource source, float amount, CallbackInfo ci) {
        PortDamageHooks.endActuallyHurt((LivingEntity) (Object) this, source);
    }
}
