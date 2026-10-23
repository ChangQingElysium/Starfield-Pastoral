package com.stardew.craft.platform.mixin.event.entity.living.mixin;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.entity.living.FabricLivingDamageEvents;
import com.stardew.craft.platform.fabric.event.entity.living.damage.DamageContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player overrides actuallyHurt, so both real implementations need identical phase hooks. */
@Mixin({LivingEntity.class, Player.class})
public abstract class ActuallyHurtEventsMixin {
    @WrapOperation(method = "actuallyHurt", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F")})
    private float stardewcraft$armor(@Coerce Object receiver, DamageSource source, float amount, Operation<Float> original) {
        var entity = (LivingEntity) receiver;
        var container = FabricLivingDamageEvents.frames(entity).current().container;
        float after = original.call(receiver, source, container.getNewDamage());
        container.setReduction(DamageContainer.Reduction.ARMOR, container.getNewDamage() - after);
        return container.getNewDamage();
    }

    @WrapOperation(method = "actuallyHurt", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F")})
    private float stardewcraft$damagePre(@Coerce Object receiver, DamageSource source, float amount, Operation<Float> original) {
        var entity = (LivingEntity) receiver;
        original.call(receiver, source, FabricLivingDamageEvents.frames(entity).current().container.getNewDamage());
        return FabricLivingDamageEvents.pre(entity);
    }

    @ModifyExpressionValue(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float stardewcraft$absorption(float vanillaFinalDamage) {
        var entity = (LivingEntity) (Object) this;
        return FabricLivingDamageEvents.absorption(entity, entity.getAbsorptionAmount());
    }

    @WrapOperation(method = "actuallyHurt", at = {
            @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/world/entity/LivingEntity;setAbsorptionAmount(F)V"),
            @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/world/entity/player/Player;setAbsorptionAmount(F)V")})
    private void stardewcraft$consumeAbsorption(@Coerce Object receiver, float vanillaAmount, Operation<Void> original) {
        var entity = (LivingEntity) receiver;
        float consumed = FabricLivingDamageEvents.frames(entity).current().absorbed;
        original.call(receiver, Math.max(0F, entity.getAbsorptionAmount() - consumed));
    }

    @ModifyVariable(method = "actuallyHurt", at = @At("STORE"), index = 4)
    private float stardewcraft$absorbedStat(float vanillaAbsorbed) {
        return FabricLivingDamageEvents.frames((LivingEntity) (Object) this).current().absorbed;
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void stardewcraft$damagePost(DamageSource source, float amount, CallbackInfo callback) {
        FabricLivingDamageEvents.post((LivingEntity) (Object) this);
    }
}
