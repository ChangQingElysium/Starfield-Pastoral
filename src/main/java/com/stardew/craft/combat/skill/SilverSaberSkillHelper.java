package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.SilverSaberFoldbackPayload;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.item.weapon.WeaponData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 银军刀"银纹折返"技能的统一操作辅助类
 * 集中管理所有技能相关的操作，避免代码重复和不一致
 */
public final class SilverSaberSkillHelper {

    public static final int SKILL_ANIM_TICKS = 8;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private SilverSaberSkillHelper() {}

    /**
     * 攻击目标（带技能上下文）
     */
    @SuppressWarnings("null")
    public static void attackWithSkillContext(Player player, LivingEntity target, WeaponSkillData skill, long nowTick) {
        attackWithSkillContext(player, target, skill, nowTick, null);
    }

    @SuppressWarnings("null")
    public static void attackWithSkillContext(
            Player player,
            LivingEntity target,
            WeaponSkillData skill,
            long nowTick,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        SkillContext context = createSkillContext(skill);
        long expireTick = nowTick + HIT_CONTEXT_LIFETIME_TICKS;
        if (weaponSnapshot == null) {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    context,
                    expireTick,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                    WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
            );
        } else {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    context,
                    weaponSnapshot,
                    expireTick,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                    WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
            );
        }
    }

    /**
     * 进入折返状态
     */
    public static void enterFoldbackState(
            Player player,
            long nowTick,
            Vec3 origin,
            WeaponDamageSnapshot weaponSnapshot,
            DeferredSkillCooldown cooldown
    ) {
        SilverSaberFoldbackState.start(
                player,
                nowTick,
                SilverSaberFoldbackState.DEFAULT_DURATION_TICKS,
                origin,
                weaponSnapshot,
                cooldown
        );
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new SilverSaberFoldbackPayload(
                            true,
                            SilverSaberFoldbackState.DEFAULT_DURATION_TICKS
                    )
            );
        }
    }

    /**
     * 退出折返状态（不返回原点）
     */
    public static void exitFoldbackState(Player player) {
        SilverSaberFoldbackState.clear(player);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new SilverSaberFoldbackPayload(false, 0));
        }
    }

    /**
     * 进入冷却并发送动画
     */
    public static void sendCooldownAnimation(
            Player player,
            String weaponId,
            WeaponSkillData skill,
            long nowTick
    ) {
        WeaponSkillAnimationLock.setLock(player, nowTick, SKILL_ANIM_TICKS);
        if (player instanceof ServerPlayer serverPlayer) {
            WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skill.getId(), SKILL_ANIM_TICKS);
        }
    }

    /**
     * 完整的"折返中右键"操作：攻击 + 返回原点 + 进入冷却
     */
    public static void executeReturnStrike(Player player, LivingEntity target, Vec3 origin,
                                           String weaponId, WeaponSkillData skill, long nowTick,
                                           TeleportFunction teleportFunc) {
        executeReturnStrike(
                player,
                target,
                origin,
                weaponId,
                skill,
                nowTick,
                teleportFunc,
                null
        );
    }

    public static void executeReturnStrike(
            Player player,
            LivingEntity target,
            Vec3 origin,
            String weaponId,
            WeaponSkillData skill,
            long nowTick,
            TeleportFunction teleportFunc,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        DeferredSkillCooldown cooldown =
                SilverSaberFoldbackState.getCooldown(player).orElse(null);
        RuntimeException failure = null;
        try {
            exitFoldbackState(player);
            if (target != null) {
                attackWithSkillContext(
                        player,
                        target,
                        skill,
                        nowTick,
                        weaponSnapshot
                );
            }
            teleportFunc.teleport(player, origin);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        failure = commitAfterAction(player, cooldown, nowTick, failure);
        if (failure != null) {
            throw failure;
        }
        sendCooldownAnimation(player, weaponId, skill, nowTick);
    }

    /**
     * 完整的"折返中左键"操作：攻击 + 不返回 + 进入冷却
     */
    public static boolean tryHandleStayStrike(
            Player player,
            LivingEntity target,
            ItemStack weapon,
            long nowTick
    ) {
        if (!SilverSaberFoldbackState.isActive(player, nowTick)
                || !(weapon.getItem() instanceof IStardewWeapon weaponItem)) {
            return false;
        }
        WeaponData data = weaponItem.getWeaponData();
        if (data == null || data.getSkill1() == null) {
            return false;
        }
        WeaponSkillData skill = data.getSkill1();
        if (!"silver_foldback".equals(skill.getId())) {
            return false;
        }
        executeStayStrike(
                player,
                target,
                weaponItem.getWeaponId(),
                skill,
                nowTick
        );
        return true;
    }

    public static void executeStayStrike(Player player, LivingEntity target,
                                         String weaponId, WeaponSkillData skill, long nowTick) {
        WeaponDamageSnapshot weaponSnapshot =
                SilverSaberFoldbackState.getWeaponSnapshot(player)
                        .orElse(null);
        DeferredSkillCooldown cooldown =
                SilverSaberFoldbackState.getCooldown(player).orElse(null);
        RuntimeException failure = null;
        try {
            exitFoldbackState(player);
            if (target != null) {
                attackWithSkillContext(
                        player,
                        target,
                        skill,
                        nowTick,
                        weaponSnapshot
                );
            }
        } catch (RuntimeException exception) {
            failure = exception;
        }
        failure = commitAfterAction(player, cooldown, nowTick, failure);
        if (failure != null) {
            throw failure;
        }
        sendCooldownAnimation(player, weaponId, skill, nowTick);
    }

    /**
     * 完整的"首次右键有目标"操作（传送完成后调用）：攻击 + 进入折返状态
     * 顺序：攻击 → 进入折返状态
     */
    @SuppressWarnings("null")
    public static void executeInitialDashAfterTeleport(
            Player player,
            LivingEntity target,
            Vec3 origin,
            String weaponId,
            WeaponSkillData skill,
            long nowTick,
            WeaponDamageSnapshot weaponSnapshot,
            DeferredSkillCooldown cooldown
    ) {
        // 1. 传送完成后，再攻击目标
        attackWithSkillContext(
                player,
                target,
                skill,
                nowTick,
                weaponSnapshot
        );

        // 2. 进入折返状态（记录的是传送前的原点）
        enterFoldbackState(
                player,
                nowTick,
                origin,
                weaponSnapshot,
                cooldown
        );

        // 3. 设置动画锁和发送动画包
        WeaponSkillAnimationLock.setLock(player, nowTick, SKILL_ANIM_TICKS);
        if (player instanceof ServerPlayer serverPlayer) {
            WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skill.getId(), SKILL_ANIM_TICKS);
        }
    }

    /**
     * 完整的"首次右键无目标"操作：突进 + 直接冷却
     */
    public static void executeEmptyDash(Player player, String weaponId, WeaponSkillData skill,
                                        long nowTick, DashFunction dashFunc) {
        dashFunc.dash(player, 5.0);
        sendCooldownAnimation(player, weaponId, skill, nowTick);
    }

    /**
     * Advances the persisted foldback continuation independently of an active
     * skill instance. Expiry and a dimension mismatch both settle the frozen
     * release-time cooldown transaction.
     */
    public static void tickPersistedFoldback(
            ServerPlayer player,
            long nowTick
    ) {
        settleInvalidFoldback(player, nowTick);
    }

    public static boolean settleInvalidFoldback(
            ServerPlayer player,
            long nowTick
    ) {
        if (!SilverSaberFoldbackState.isActiveRaw(player)
                || SilverSaberFoldbackState.isActive(player, nowTick)) {
            return false;
        }
        cancelFoldback(player, nowTick);
        return true;
    }

    public static void cancelFoldback(
            ServerPlayer player,
            long nowTick
    ) {
        if (player == null
                || !SilverSaberFoldbackState.isActiveRaw(player)) {
            return;
        }

        DeferredSkillCooldown cooldown =
                SilverSaberFoldbackState.getCooldown(player).orElse(null);
        RuntimeException failure = null;
        try {
            exitFoldbackState(player);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        failure = commitAfterAction(player, cooldown, nowTick, failure);
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException commitAfterAction(
            Player player,
            DeferredSkillCooldown cooldown,
            long nowTick,
            RuntimeException failure
    ) {
        try {
            commitFoldbackCooldown(player, cooldown, nowTick);
        } catch (RuntimeException commitFailure) {
            if (failure == null) {
                return commitFailure;
            }
            if (failure != commitFailure) {
                failure.addSuppressed(commitFailure);
            }
        }
        return failure;
    }

    private static void commitFoldbackCooldown(
            Player player,
            DeferredSkillCooldown cooldown,
            long nowTick
    ) {
        if (player instanceof ServerPlayer serverPlayer && cooldown != null) {
            WeaponSkillRuntime.commitDeferredCooldown(
                    serverPlayer,
                    cooldown,
                    nowTick
            );
        }
    }

    static SkillContext createSkillContext(WeaponSkillData skill) {
        return SkillContext.builder()
                .skillId(skill.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(skill.getDamagePercent() / 100.0F)
                .build();
    }

    @FunctionalInterface
    public interface TeleportFunction {
        void teleport(Player player, Vec3 pos);
    }

    @FunctionalInterface
    public interface DashFunction {
        void dash(Player player, double distance);
    }
}
