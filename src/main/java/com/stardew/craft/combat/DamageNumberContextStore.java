package com.stardew.craft.combat;

import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class DamageNumberContextStore {

    private static final String TAG_ROOT = "StardewDamageMeta";
    private static final String TAG_SKILL = "SkillId";
    private static final String TAG_CRIT = "Crit";
    private static final String TAG_EXPIRE = "Expire";
    private static final Map<UUID, BoundWeapon> WEAPONS = new HashMap<>();

    private DamageNumberContextStore() {}

    public static synchronized void set(
            Player player,
            String skillId,
            boolean crit,
            long expireTick
    ) {
        set(player, skillId, crit, null, expireTick);
    }

    public static synchronized void set(
            Player player,
            String skillId,
            boolean crit,
            WeaponDamageSnapshot weaponSnapshot,
            long expireTick
    ) {
        CompoundTag root = player.getPersistentData();
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_SKILL, skillId == null ? "" : skillId);
        tag.putBoolean(TAG_CRIT, crit);
        tag.putLong(TAG_EXPIRE, expireTick);
        root.put(TAG_ROOT, tag);
        if (weaponSnapshot == null) {
            WEAPONS.remove(player.getUUID());
        } else {
            WEAPONS.put(
                    player.getUUID(),
                    new BoundWeapon(expireTick, weaponSnapshot)
            );
        }
    }

    public static synchronized Meta consume(Player player, long nowTick) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(TAG_ROOT)) return null;

        CompoundTag tag = root.getCompound(TAG_ROOT);
        long expire = tag.getLong(TAG_EXPIRE);
        if (nowTick > expire) {
            root.remove(TAG_ROOT);
            WEAPONS.remove(player.getUUID());
            return null;
        }

        String skillId = tag.getString(TAG_SKILL);
        boolean crit = tag.getBoolean(TAG_CRIT);
        if (skillId != null && skillId.isEmpty()) skillId = null;
        BoundWeapon boundWeapon = WEAPONS.remove(player.getUUID());
        WeaponDamageSnapshot snapshot = boundWeapon != null
                && boundWeapon.expireTick() >= nowTick
                ? boundWeapon.snapshot()
                : null;
        return new Meta(skillId, crit, snapshot);
    }

    public static synchronized Meta peek(Player player, long nowTick) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(TAG_ROOT)) return null;

        CompoundTag tag = root.getCompound(TAG_ROOT);
        long expire = tag.getLong(TAG_EXPIRE);
        if (nowTick > expire) {
            root.remove(TAG_ROOT);
            WEAPONS.remove(player.getUUID());
            return null;
        }

        String skillId = tag.getString(TAG_SKILL);
        boolean crit = tag.getBoolean(TAG_CRIT);
        if (skillId != null && skillId.isEmpty()) skillId = null;
        BoundWeapon boundWeapon = WEAPONS.get(player.getUUID());
        WeaponDamageSnapshot snapshot = boundWeapon != null
                && boundWeapon.expireTick() >= nowTick
                ? boundWeapon.snapshot()
                : null;
        return new Meta(skillId, crit, snapshot);
    }

    public static synchronized void removePlayer(UUID playerId) {
        WEAPONS.remove(playerId);
    }

    public record Meta(
            String skillId,
            boolean crit,
            WeaponDamageSnapshot boundWeapon
    ) {
        public Optional<WeaponDamageSnapshot> weaponSnapshot() {
            return Optional.ofNullable(boundWeapon);
        }
    }

    private record BoundWeapon(
            long expireTick,
            WeaponDamageSnapshot snapshot
    ) {}
}
