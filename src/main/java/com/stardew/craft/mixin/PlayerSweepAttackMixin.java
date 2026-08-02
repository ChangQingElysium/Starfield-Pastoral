package com.stardew.craft.mixin;

import com.stardew.craft.combat.OrdinaryWeaponAttackFrameStore;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.WeaponCombatIdentity;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerSweepAttackMixin {
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    private boolean stardewcraft$authorizedPrimaryHurt(
            Entity target,
            DamageSource source,
            float vanillaDamage
    ) {
        Player player = (Player) (Object) this;
        ItemStack weapon = player.getMainHandItem();
        WeaponCombatIdentity.Resolved identity = WeaponCombatIdentity
                .resolve(weapon)
                .orElse(null);
        LivingEntity combatTarget = canonicalLivingTarget(target);
        if (identity == null || combatTarget == null) {
            return target.hurt(source, vanillaDamage);
        }

        WeaponDamageSnapshot snapshot = WeaponDamageSnapshot.capture(
                identity.id(),
                weapon
        );
        OrdinaryWeaponAttackFrameStore.bind(
                player,
                combatTarget,
                source,
                snapshot,
                player.level().getGameTime()
        );
        try {
            return target.hurt(
                    source,
                    stableStardewDamageInput(vanillaDamage)
            );
        } finally {
            OrdinaryWeaponAttackFrameStore.discard(
                    player,
                    combatTarget,
                    source
            );
        }
    }

    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    private boolean stardewcraft$suppressNativeSweepHurt(
            LivingEntity target,
            DamageSource source,
            float vanillaDamage
    ) {
        Player player = (Player) (Object) this;
        if (WeaponCombatIdentity.isWeapon(player.getMainHandItem())) {
            return false;
        }
        return target.hurt(source, vanillaDamage);
    }

    private float stableStardewDamageInput(float vanillaDamage) {
        Player player = (Player) (Object) this;
        ItemStack weapon = player.getMainHandItem();
        if (!WeaponCombatIdentity.isWeapon(weapon)) {
            return vanillaDamage;
        }
        float averageDamage = WeaponStats.fromItemStack(weapon)
                .getAverageDamage();
        return Float.isFinite(averageDamage)
                ? Math.max(0.001F, averageDamage)
                : 0.001F;
    }

    private static LivingEntity canonicalLivingTarget(Entity target) {
        Entity canonical = target instanceof PartEntity<?> part
                ? part.getParent()
                : target;
        return canonical instanceof LivingEntity living ? living : null;
    }

    /**
     * Stardew weapon hits apply their authored weight exactly once inside
     * WeaponCombatEvents. Suppress both vanilla's post-hurt primary knockback
     * and its pre-hurt sweep knockback while preserving vanilla weapons.
     */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"
            )
    )
    private void stardewcraft$singleKnockbackOwner(
            LivingEntity target,
            double strength,
            double x,
            double z
    ) {
        Player player = (Player) (Object) this;
        if (!WeaponCombatIdentity.isWeapon(player.getMainHandItem())) {
            target.knockback(strength, x, z);
        }
    }

}
