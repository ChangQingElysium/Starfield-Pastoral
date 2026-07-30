package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.ObsidianResonanceSyncPayload;
import com.stardew.craft.item.weapon.IStardewWeapon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 黑曜石之刃 - 玄刃共鸣：7秒自动充能，下次普攻追加100%伤害（首段暴击则追加必暴）。
 */
public final class ObsidianResonanceTracker {

    public static final int CHARGE_TICKS = 7 * 20;
    public static final float BONUS_DAMAGE_MULTIPLIER = 0.70F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private static final class State {
        private long nextReadyTick;
        private boolean charged;

        private State(long nextReadyTick) {
            this.nextReadyTick = nextReadyTick;
            this.charged = false;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private ObsidianResonanceTracker() {}

    public static void tick(ServerPlayer player, long nowTick) {
        if (player == null) {
            return;
        }
        if (!hasObsidianEdge(player)) {
            if (ACTIVE.remove(player.getUUID()) != null) {
                PacketDistributor.sendToPlayer(player, new ObsidianResonanceSyncPayload(false, 0, CHARGE_TICKS));
            }
            return;
        }

        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            State created = new State(nowTick + CHARGE_TICKS);
            ACTIVE.put(player.getUUID(), created);
            PacketDistributor.sendToPlayer(player, new ObsidianResonanceSyncPayload(true, CHARGE_TICKS, CHARGE_TICKS));
            return;
        }

        if (!state.charged && nowTick >= state.nextReadyTick) {
            state.charged = true;
        }
    }

    public static boolean isCharged(ServerPlayer player, long nowTick) {
        if (!hasObsidianEdge(player)) {
            ACTIVE.remove(player.getUUID());
            return false;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return false;
        }
        if (!state.charged && nowTick >= state.nextReadyTick) {
            state.charged = true;
        }
        return state.charged;
    }

    @SuppressWarnings("null")
    public static void consumeAndStrike(ServerPlayer player, LivingEntity target, long nowTick, boolean firstCrit) {
        consumeAndStrike(player, target, nowTick, firstCrit, null);
    }

    @SuppressWarnings("null")
    public static void consumeAndStrike(
            ServerPlayer player,
            LivingEntity target,
            long nowTick,
            boolean firstCrit,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (!hasObsidianEdge(player)) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null || !state.charged) {
            return;
        }
        state.charged = false;
        state.nextReadyTick = nowTick + CHARGE_TICKS;
        PacketDistributor.sendToPlayer(player, new ObsidianResonanceSyncPayload(true, CHARGE_TICKS, CHARGE_TICKS));

        SkillContext context = createBonusContext(firstCrit);
        long expireTick = nowTick + HIT_CONTEXT_LIFETIME_TICKS;
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        if (weaponSnapshot == null) {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    context,
                    expireTick
            );
        } else {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    context,
                    weaponSnapshot,
                    expireTick
            );
        }

        if (player.level() != null) {
            player.level().playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.5f, 1.6f);
            player.level().addParticle(ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                0.0, 0.05, 0.0);
        }
    }

    static SkillContext createBonusContext(boolean guaranteedCrit) {
        return SkillContext.builder()
                .skillId("obsidian_resonance")
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(BONUS_DAMAGE_MULTIPLIER)
                .guaranteedCrit(guaranteedCrit)
                .build();
    }

    private static boolean hasObsidianEdge(ServerPlayer player) {
        if (isObsidianEdge(player.getMainHandItem()) || isObsidianEdge(player.getOffhandItem())) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (isObsidianEdge(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isObsidianEdge(ItemStack stack) {
        if (!(stack.getItem() instanceof IStardewWeapon weaponItem)) {
            return false;
        }
        return "obsidian_edge".equals(weaponItem.getWeaponId());
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
