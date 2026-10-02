package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.PortInheritance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): 1.21.1 {@code MeleeAttackGoal} for StardewCraft mobs (used by {@code LuckyPurpleShortsMonsterEntity}).
 * <ul>
 * <li>{@code canUse} without a path: 1.21 asks {@code mob.isWithinMeleeAttackRange(target)}; 1.20.1 compares
 * {@code getAttackReachSqr} with the squared distance.</li>
 * <li>{@code tick}: the distance used for the path-recalculation back-off is {@code mob.distanceToSqr(target)}
 * (1.20.1: {@code getPerceivedTargetDistanceSquareForMeleeAttack}, which differs only for riding targets).</li>
 * <li>{@code checkAndPerformAttack}: 1.21 attacks when the cooldown is over, the target is within
 * {@code isWithinMeleeAttackRange} <em>and</em> the mob has line of sight; 1.20.1 uses the squared-distance reach and
 * no line-of-sight test.</li>
 * </ul>
 */
@Mixin(MeleeAttackGoal.class)
public abstract class PortMeleeAttackGoalMixin {
    @Shadow
    @Final
    protected PathfinderMob mob;

    @Shadow
    protected abstract boolean isTimeToAttack();

    @Shadow
    protected abstract void resetAttackCooldown();

    @WrapOperation(method = "canUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/goal/MeleeAttackGoal;getAttackReachSqr(Lnet/minecraft/world/entity/LivingEntity;)D"))
    private double stardewcraft$canUseRange121(MeleeAttackGoal goal, LivingEntity target, Operation<Double> original) {
        if (!PortInheritance.isModEntity(this.mob)) return original.call(goal, target);
        // Compared as "reach >= distanceSqr": +inf/-inf turn it into the 1.21 isWithinMeleeAttackRange result.
        return this.mob.isWithinMeleeAttackRange(target) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/PathfinderMob;getPerceivedTargetDistanceSquareForMeleeAttack(Lnet/minecraft/world/entity/LivingEntity;)D"))
    private double stardewcraft$tickDistance121(PathfinderMob mob, LivingEntity target, Operation<Double> original) {
        return PortInheritance.isModEntity(mob) ? mob.distanceToSqr(target) : original.call(mob, target);
    }

    @Inject(method = "checkAndPerformAttack", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$attack121(LivingEntity target, double distanceSqr, CallbackInfo ci) {
        Mob mob = this.mob;
        if (!PortInheritance.isModEntity(mob)) return;
        ci.cancel();
        if (this.isTimeToAttack() && mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
            this.resetAttackCooldown();
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
        }
    }
}
