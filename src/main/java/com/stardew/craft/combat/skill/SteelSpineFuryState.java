package com.stardew.craft.combat.skill;

import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.combat.network.SteelSpineFuryPayload;
import com.stardew.craft.combat.network.SteelSpineFuryEnterPayload;
import com.stardew.craft.combat.network.SteelSpineFuryHitPayload;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 铁刃 - 钢脊之怒：进入4秒姿态，首击受伤后转化为下一击爆发
 */
public final class SteelSpineFuryState {

    static final int MAX_BONUS_DAMAGE = 12;
    static final float BONUS_RATIO = 0.40f;
    static final float FALLBACK_MULTIPLIER = 1.40f;

    private static final class State {
        private long endTick;
        private boolean tookHit;
        private boolean ready;
        private boolean weak;
        private int bonusDamage;
        private String weaponId;
        private String skillId;
        private int cooldownTicks;
        private boolean cooldownApplied;
        private boolean consumed;

        private State(long endTick, String weaponId, String skillId, int cooldownTicks) {
            this.endTick = endTick;
            this.weaponId = weaponId;
            this.skillId = skillId;
            this.cooldownTicks = cooldownTicks;
            this.cooldownApplied = false;
            this.consumed = false;
        }
    }

    public record AttackBoost(boolean strong, int bonusDamage, float damageMultiplier) {}

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private SteelSpineFuryState() {}

    public static void start(ServerPlayer player, long nowTick, int durationTicks, String weaponId, String skillId, int cooldownTicks) {
        if (player == null || durationTicks <= 0 || weaponId == null || skillId == null) {
            return;
        }
        ACTIVE.put(player.getUUID(), new State(nowTick + durationTicks, weaponId, skillId, cooldownTicks));
        PacketDistributor.sendToPlayer(player, new SteelSpineFuryPayload(true, durationTicks));
        PacketDistributor.sendToPlayer(player, new SteelSpineFuryEnterPayload());
    }

    public static boolean isBusy(ServerPlayer player) {
        if (player == null) return false;
        return ACTIVE.containsKey(player.getUUID());
    }

    @SuppressWarnings("null")
    public static void onDamageTaken(ServerPlayer player, long nowTick, int stardewDamage) {
        if (player == null || stardewDamage <= 0) return;
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return;
        if (nowTick > state.endTick) return;
        if (state.tookHit) return;

        state.tookHit = true;
        state.ready = true;
        state.weak = false;
        state.bonusDamage = calculateBonusDamage(stardewDamage);
        state.endTick = nowTick;

        if (!state.cooldownApplied && state.cooldownTicks > 0) {
            WeaponSkillCooldowns.setCooldown(player, state.weaponId, state.skillId, nowTick, state.cooldownTicks);
            state.cooldownApplied = true;
            PacketDistributor.sendToPlayer(player, new SteelSpineFuryPayload(false, 0));
        }

        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
        PacketDistributor.sendToPlayer(player, new SteelSpineFuryHitPayload());
    }

    public static void tick(ServerPlayer player, long nowTick) {
        if (player == null) return;
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return;

        if (nowTick > state.endTick) {
            if (!state.ready) {
                state.ready = true;
                state.weak = true;
                state.bonusDamage = 0;
            }

            if (!state.cooldownApplied && state.cooldownTicks > 0) {
                WeaponSkillCooldowns.setCooldown(player, state.weaponId, state.skillId, nowTick, state.cooldownTicks);
                state.cooldownApplied = true;
                PacketDistributor.sendToPlayer(player, new SteelSpineFuryPayload(false, 0));
            }

            if (state.cooldownApplied && state.consumed) {
                ACTIVE.remove(player.getUUID());
            }
        }
    }

    public static AttackBoost consumeAttack(ServerPlayer player, long nowTick) {
        if (player == null) return null;
        State state = ACTIVE.get(player.getUUID());
        if (state == null || !state.ready) return null;

        AttackBoost boost = createAttackBoost(state.weak, state.bonusDamage);
        state.ready = false;
        state.consumed = true;
        if (state.cooldownApplied) {
            ACTIVE.remove(player.getUUID());
        }
        return boost;
    }

    static int calculateBonusDamage(int stardewDamage) {
        return Math.min(MAX_BONUS_DAMAGE, (int) Math.ceil(stardewDamage * BONUS_RATIO));
    }

    static AttackBoost createAttackBoost(boolean weak, int bonusDamage) {
        return weak
                ? new AttackBoost(false, 0, FALLBACK_MULTIPLIER)
                : new AttackBoost(true, bonusDamage, 1.0f);
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
