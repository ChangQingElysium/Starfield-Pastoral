package com.stardew.craft.platform.mixin.event.entity.living.mixin;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.stardew.craft.platform.fabric.event.entity.living.FabricLivingDamageEvents;
import com.stardew.craft.platform.fabric.event.entity.living.damage.DamageContainer;
import com.stardew.craft.platform.fabric.event.entity.living.damage.DamageFrames;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingDamageSequenceMixin implements DamageFrames.Access {
    @Unique private final DamageFrames stardewcraft$damageFrames = new DamageFrames();
    @Override @Unique public DamageFrames stardewcraft$damageFrames() { return stardewcraft$damageFrames; }

    @WrapMethod(method = "hurt")
    private boolean stardewcraft$damageFrame(DamageSource source, float amount, Operation<Boolean> original) {
        return stardewcraft$damageFrames.within(() -> original.call(source, amount));
    }

    @Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"), cancellable = true)
    private void stardewcraft$incoming(DamageSource source, float original, CallbackInfoReturnable<Boolean> callback,
                                      @Local(argsOnly = true) LocalFloatRef amount) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (FabricLivingDamageEvents.incoming(entity, source, amount.get())) callback.setReturnValue(false);
        else amount.set(stardewcraft$damageFrames.current().container.getNewDamage());
    }

    @WrapOperation(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurtCurrentlyUsedShield(F)V"))
    private void stardewcraft$shield(LivingEntity entity, float amount, Operation<Void> original) {
        stardewcraft$damageFrames.current().container.recordBlock(amount, amount);
        original.call(entity, amount);
    }

    @Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/WalkAnimationState;setSpeed(F)V"))
    private void stardewcraft$vanillaAdjustments(DamageSource source, float original, CallbackInfoReturnable<Boolean> callback,
                                               @Local(argsOnly = true) float amount) {
        stardewcraft$damageFrames.current().container.setNewDamage(amount);
    }

    @WrapOperation(method = "hurt", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void stardewcraft$cooldownDamage(LivingEntity entity, DamageSource source, float amount, Operation<Void> original) {
        var container = stardewcraft$damageFrames.current().container;
        container.setReduction(DamageContainer.Reduction.INVULNERABILITY, container.getNewDamage() - amount);
        original.call(entity, source, container.getNewDamage());
    }

    @WrapOperation(method = "hurt", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void stardewcraft$freshDamage(LivingEntity entity, DamageSource source, float amount, Operation<Void> original) {
        original.call(entity, source, stardewcraft$damageFrames.current().container.getNewDamage());
    }

    @ModifyConstant(method = "hurt", constant = @Constant(intValue = 20))
    private int stardewcraft$invulnerabilityTicks(int original) {
        return stardewcraft$damageFrames.current().container.getPostAttackInvulnerabilityTicks();
    }

    @Inject(method = "hurt", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/damagesource/DamageSource;getEntity()Lnet/minecraft/world/entity/Entity;"))
    private void stardewcraft$resolvedAmount(DamageSource source, float original, CallbackInfoReturnable<Boolean> callback,
                                           @Local(argsOnly = true) LocalFloatRef amount) {
        amount.set(stardewcraft$damageFrames.current().container.getNewDamage());
    }

    @ModifyExpressionValue(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float stardewcraft$resistance(float after, @Local(argsOnly = true) float before) {
        var frame = stardewcraft$damageFrames.currentOrNull();
        float reduced = before - after;
        if (frame != null && frame.container != null && reduced > 0F && reduced < 3.4028235E37F)
            frame.container.setReduction(DamageContainer.Reduction.MOB_EFFECTS, reduced);
        // NeoForge keeps the vanilla local for its resistance stat and following enchantment math.
        return after;
    }

    @ModifyExpressionValue(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterMagicAbsorb(FF)F"))
    private float stardewcraft$enchantments(float after) {
        var frame = stardewcraft$damageFrames.currentOrNull();
        if (frame != null && frame.container != null)
            frame.container.setReduction(DamageContainer.Reduction.ENCHANTMENTS, frame.container.getNewDamage() - after);
        return after;
    }

    // NeoForge removed this second vanilla subtraction. Absorption was already consumed before health.
    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/world/entity/LivingEntity;setAbsorptionAmount(F)V"))
    private void stardewcraft$noSecondAbsorptionConsumption(LivingEntity entity, float amount, Operation<Void> original) {}

    @WrapMethod(method = "knockback")
    private void stardewcraft$knockback(double strength, double x, double z, Operation<Void> original) {
        var event = FabricLivingDamageEvents.knockback((LivingEntity) (Object) this, strength, x, z);
        if (!event.isCanceled()) original.call((double) event.getStrength(), event.getRatioX(), event.getRatioZ());
    }
}
