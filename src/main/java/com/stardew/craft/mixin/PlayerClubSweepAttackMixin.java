package com.stardew.craft.mixin;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.WeaponType;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

import javax.annotation.Nonnull;

@Mixin(Player.class)
public abstract class PlayerClubSweepAttackMixin {

    @SuppressWarnings("null")
    @Inject(method = "attack", at = @At("TAIL"))
    private void stardewcraft$clubSweepAttack(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player.level().isClientSide) {
            return;
        }
        if (!(target instanceof LivingEntity primary)) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IStardewWeapon weaponItem)) {
            return;
        }

        WeaponType weaponType = resolveWeaponType(stack, weaponItem);
        if (weaponType != WeaponType.CLUB) {
            return;
        }

        if (player.getAttackStrengthScale(0.5f) <= 0.9f) {
            return;
        }
        if (player.isSprinting() || player.isUsingItem() || !player.onGround() || player.isInWaterOrBubble()) {
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        WeaponDamageSnapshot weaponSnapshot = WeaponDamageSnapshot.capture(
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID,
                        weaponItem.getWeaponId()
                ),
                stack
        );
        long nowTick = serverLevel.getGameTime();

        @Nonnull AABB box = Objects.requireNonNull(player.getBoundingBox());

        boolean hitAny = false;
        for (LivingEntity entity : player.level().getEntitiesOfClass(
            LivingEntity.class,
            box.inflate(1.0D, 0.25D, 1.0D)
        )) {
            if (entity == player || entity == primary) {
                continue;
            }
            if (player.isAlliedTo(Objects.requireNonNull(entity))) {
                continue;
            }
            if (player.distanceToSqr(Objects.requireNonNull(entity)) >= 9.0D) {
                continue;
            }
            entity.knockback(0.4F, Mth.sin(player.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(player.getYRot() * Mth.DEG_TO_RAD));
            WeaponSkillDamage.apply(
                    player,
                    entity,
                    SkillContext.normalAttack(),
                    weaponSnapshot,
                    nowTick + 5
            );
            hitAny = true;
        }

        if (hitAny) {
            Vec3 look = player.getLookAngle();
            double x = player.getX() + look.x;
            double y = player.getY() + player.getBbHeight() * 0.5;
            double z = player.getZ() + look.z;
            serverLevel.sendParticles(Objects.requireNonNull(ParticleTypes.SWEEP_ATTACK), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            serverLevel.playSound(null, Objects.requireNonNull(player.blockPosition()), Objects.requireNonNull(SoundEvents.PLAYER_ATTACK_SWEEP), SoundSource.PLAYERS, 0.6f, 1.15f);
        }
    }

    private static WeaponType resolveWeaponType(ItemStack stack, IStardewWeapon weaponItem) {
        WeaponType weaponType = WeaponType.SWORD;
        WeaponData weaponData = weaponItem.getWeaponData();
        if (weaponData == null) {
            String weaponId = weaponItem.getWeaponId();
            if (weaponId != null && !weaponId.isEmpty()) {
                weaponData = WeaponRegistry.get(weaponId);
            }
        }
        if (weaponData != null) {
            weaponType = weaponData.getWeaponType();
        } else {
            WeaponStats stats = WeaponStats.fromItemStack(stack);
            if (stats != null) {
                weaponType = stats.getWeaponType();
            }
        }
        return weaponType;
    }
}
