package com.stardew.craft.mixin;

import com.stardew.craft.port.PortInheritance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): 1.21.1 {@code Mob#isWithinMeleeAttackRange} tests the attack box (own box, widened over a vehicle,
 * inflated horizontally by {@code sqrt(2.04) - 0.6}) against the target's hit box; 1.20.1 compares the squared
 * distance with {@code (2 * width)^2 + targetWidth}. StardewCraft mobs use the 1.21.1 test; see also
 * {@code PortMeleeAttackGoalMixin}, the goal that consults it.
 */
@Mixin(Mob.class)
public abstract class PortMobMeleeRangeMixin {
    @Inject(method = "isWithinMeleeAttackRange", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$meleeRange121(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (PortInheritance.isModEntity(self)) cir.setReturnValue(PortInheritance.isWithinMeleeAttackRange(self, target));
    }
}
