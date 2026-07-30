package com.stardew.craft.combat.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class LightCounterParryState {

    public static final int DEFAULT_WINDOW_TICKS = 20; // 1.0s
    public static final int COUNTER_ANIM_TICKS = 8;

    private static final String TAG_END_TICK = "StardewLightCounterEnd";
    private static final String TAG_WEAPON_ID = "StardewLightCounterWeapon";
    private static final String TAG_ACTIVE = "StardewLightCounterActive";
    private static final Map<UUID, WeaponDamageSnapshot> WEAPON_SNAPSHOTS =
            new HashMap<>();

    private LightCounterParryState() {}

    @SuppressWarnings("null")
    public static void start(
            Player player,
            long nowTick,
            int windowTicks,
            String weaponId
    ) {
        start(player, nowTick, windowTicks, weaponId, null);
    }

    @SuppressWarnings("null")
    public static synchronized void start(
            Player player,
            long nowTick,
            int windowTicks,
            String weaponId,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        CompoundTag root = player.getPersistentData();
        root.putLong(TAG_END_TICK, nowTick + Math.max(1, windowTicks));
        root.putString(TAG_WEAPON_ID, weaponId);
        root.putBoolean(TAG_ACTIVE, true);
        if (weaponSnapshot == null) {
            WEAPON_SNAPSHOTS.remove(player.getUUID());
        } else {
            WEAPON_SNAPSHOTS.put(player.getUUID(), weaponSnapshot);
        }
    }

    public static boolean isActive(Player player, long nowTick) {
        CompoundTag root = player.getPersistentData();
        if (!root.getBoolean(TAG_ACTIVE)) {
            return false;
        }
        long endTick = root.getLong(TAG_END_TICK);
        return nowTick <= endTick;
    }

    public static String getWeaponId(Player player) {
        CompoundTag root = player.getPersistentData();
        return root.contains(TAG_WEAPON_ID) ? root.getString(TAG_WEAPON_ID) : null;
    }

    public static synchronized Optional<WeaponDamageSnapshot> getWeaponSnapshot(
            Player player
    ) {
        return Optional.ofNullable(WEAPON_SNAPSHOTS.get(player.getUUID()));
    }

    public static synchronized void clear(Player player) {
        CompoundTag root = player.getPersistentData();
        root.remove(TAG_END_TICK);
        root.remove(TAG_WEAPON_ID);
        root.remove(TAG_ACTIVE);
        WEAPON_SNAPSHOTS.remove(player.getUUID());
    }
}
