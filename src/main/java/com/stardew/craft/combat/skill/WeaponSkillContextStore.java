package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 存储/读取玩家即将触发的技能上下文（用于下一次伤害计算）
 */
public final class WeaponSkillContextStore {

    private static final String TAG_ROOT = "StardewSkillContext";
    private static final String TAG_SKILL_ID = "SkillId";
    private static final String TAG_TIER = "Tier";
    private static final String TAG_DAMAGE_MULT = "DamageMultiplier";
    private static final String TAG_IGNORE_DEF = "IgnoreDefense";
    private static final String TAG_GUARANTEED_CRIT = "GuaranteedCrit";
    private static final String TAG_CRIT_CHANCE_BONUS = "CritChanceBonus";
    private static final String TAG_EXPIRE_TICK = "ExpireTick";
    private static final Map<UUID, BoundWeaponSnapshot> BOUND_WEAPONS =
            new HashMap<>();

    private WeaponSkillContextStore() {}

    @SuppressWarnings("null")
    public static synchronized void setPending(
            Player player,
            SkillContext context,
            long expireTick
    ) {
        Optional<WeaponDamageSnapshot> releaseWeapon =
                WeaponSkillRuntime.releaseWeaponSnapshot(
                        player.getUUID(),
                        context.getSkillId()
                );
        setPendingInternal(
                player,
                context,
                expireTick,
                releaseWeapon.orElse(null)
        );
    }

    /**
     * Explicit binding hook for projectile and tracker state that already owns
     * a release snapshot after its runtime execution has ended.
     */
    public static synchronized void setPending(
            Player player,
            SkillContext context,
            WeaponDamageSnapshot weaponSnapshot,
            long expireTick
    ) {
        setPendingInternal(
                player,
                context,
                expireTick,
                Objects.requireNonNull(weaponSnapshot, "weaponSnapshot")
        );
    }

    private static void setPendingInternal(
            Player player,
            SkillContext context,
            long expireTick,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(context, "context");
        CompoundTag root = player.getPersistentData();
        CompoundTag tag = writeContextTag(context, expireTick);
        root.put(TAG_ROOT, tag);

        UUID playerId = player.getUUID();
        if (weaponSnapshot == null) {
            BOUND_WEAPONS.remove(playerId);
        } else {
            BOUND_WEAPONS.put(
                    playerId,
                    new BoundWeaponSnapshot(
                            context.getSkillId(),
                            expireTick,
                            weaponSnapshot
                    )
            );
        }
    }

    /**
     * Legacy context-only consumer. New damage entry points should use
     * {@link #consumePending(Player, long)} to consume the context and release
     * weapon atomically.
     */
    public static synchronized SkillContext consume(Player player, long nowTick) {
        PendingHit pending = consumePending(player, nowTick);
        return pending == null ? null : pending.skillContext();
    }

    /**
     * Atomically consumes the pending skill context and its optional
     * release-time weapon snapshot.
     */
    public static synchronized PendingHit consumePending(
            Player player,
            long nowTick
    ) {
        CompoundTag root = player.getPersistentData();
        UUID playerId = player.getUUID();
        if (!root.contains(TAG_ROOT)) {
            BOUND_WEAPONS.remove(playerId);
            return null;
        }

        CompoundTag tag = root.getCompound(TAG_ROOT);
        long expire = tag.getLong(TAG_EXPIRE_TICK);
        if (expire < nowTick) {
            root.remove(TAG_ROOT);
            BOUND_WEAPONS.remove(playerId);
            return null;
        }

        root.remove(TAG_ROOT);
        BoundWeaponSnapshot boundWeapon = BOUND_WEAPONS.remove(playerId);
        SkillContext context = readContextTag(tag);
        WeaponDamageSnapshot snapshot =
                isMatchingBinding(boundWeapon, context.getSkillId(), nowTick)
                        ? boundWeapon.snapshot()
                        : null;
        return new PendingHit(context, snapshot);
    }

    public static synchronized boolean hasPending(Player player, long nowTick) {
        CompoundTag root = player.getPersistentData();
        UUID playerId = player.getUUID();
        if (!root.contains(TAG_ROOT)) {
            BOUND_WEAPONS.remove(playerId);
            return false;
        }
        CompoundTag tag = root.getCompound(TAG_ROOT);
        long expire = tag.getLong(TAG_EXPIRE_TICK);
        if (expire < nowTick) {
            root.remove(TAG_ROOT);
            BOUND_WEAPONS.remove(playerId);
            return false;
        }
        return true;
    }

    /**
     * Performs the same expiry cleanup as consume without consuming a valid
     * pending hit. Called from the per-player runtime tick.
     */
    public static synchronized void clearExpired(Player player, long nowTick) {
        CompoundTag root = player.getPersistentData();
        UUID playerId = player.getUUID();
        if (!root.contains(TAG_ROOT)) {
            BOUND_WEAPONS.remove(playerId);
            return;
        }
        if (root.getCompound(TAG_ROOT).getLong(TAG_EXPIRE_TICK) < nowTick) {
            root.remove(TAG_ROOT);
            BOUND_WEAPONS.remove(playerId);
        }
    }

    /**
     * Clears in-memory release snapshots when the caster leaves the server.
     * The legacy NBT context has a short absolute expiry and is discarded by
     * the next has/consume call.
     */
    public static synchronized void removePlayer(UUID playerId) {
        if (playerId != null) {
            BOUND_WEAPONS.remove(playerId);
        }
    }

    static CompoundTag writeContextTag(SkillContext context, long expireTick) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_SKILL_ID, context.getSkillId());
        tag.putInt(TAG_TIER, context.getTier().ordinal());
        tag.putFloat(TAG_DAMAGE_MULT, context.getDamageMultiplier());
        tag.putBoolean(TAG_IGNORE_DEF, context.isIgnoreDefense());
        tag.putBoolean(TAG_GUARANTEED_CRIT, context.isGuaranteedCrit());
        tag.putFloat(TAG_CRIT_CHANCE_BONUS, context.getCritChanceBonus());
        tag.putLong(TAG_EXPIRE_TICK, expireTick);
        return tag;
    }

    static SkillContext readContextTag(CompoundTag tag) {
        int tierOrdinal = tag.getInt(TAG_TIER);
        SkillContext.SkillTier tier = SkillContext.SkillTier.NORMAL;
        if (tierOrdinal >= 0 && tierOrdinal < SkillContext.SkillTier.values().length) {
            tier = SkillContext.SkillTier.values()[tierOrdinal];
        }
        return SkillContext.builder()
                .skillId(tag.getString(TAG_SKILL_ID))
                .tier(tier)
                .damageMultiplier(tag.getFloat(TAG_DAMAGE_MULT))
                .ignoreDefense(tag.getBoolean(TAG_IGNORE_DEF))
                .guaranteedCrit(tag.getBoolean(TAG_GUARANTEED_CRIT))
                .critChanceBonus(tag.getFloat(TAG_CRIT_CHANCE_BONUS))
                .build();
    }

    private static boolean isMatchingBinding(
            BoundWeaponSnapshot binding,
            String skillId,
            long nowTick
    ) {
        return binding != null
                && binding.expireTick() >= nowTick
                && Objects.equals(binding.skillId(), skillId);
    }

    public static final class PendingHit {
        private final SkillContext skillContext;
        private final WeaponDamageSnapshot weaponSnapshot;

        private PendingHit(
                SkillContext skillContext,
                WeaponDamageSnapshot weaponSnapshot
        ) {
            this.skillContext = Objects.requireNonNull(
                    skillContext,
                    "skillContext"
            );
            this.weaponSnapshot = weaponSnapshot;
        }

        public SkillContext skillContext() {
            return skillContext;
        }

        public Optional<WeaponDamageSnapshot> weaponSnapshot() {
            return Optional.ofNullable(weaponSnapshot);
        }
    }

    private record BoundWeaponSnapshot(
            String skillId,
            long expireTick,
            WeaponDamageSnapshot snapshot
    ) {}
}
