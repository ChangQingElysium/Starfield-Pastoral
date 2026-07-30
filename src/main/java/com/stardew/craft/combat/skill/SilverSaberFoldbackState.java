package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SilverSaberFoldbackState {

    public static final int DEFAULT_DURATION_TICKS = 20; // 1.0s

    private static final String TAG_ACTIVE = "StardewSilverFoldbackActive";
    private static final String TAG_END_TICK = "StardewSilverFoldbackEnd";
    private static final String TAG_DURATION = "StardewSilverFoldbackDuration";
    private static final String TAG_WEAPON_ID = "StardewSilverFoldbackWeapon";
    private static final String TAG_SKILL_ID = "StardewSilverFoldbackSkill";
    private static final String TAG_COOLDOWN = "StardewSilverFoldbackCooldown";
    private static final String TAG_DIMENSION = "StardewSilverFoldbackDimension";
    private static final String TAG_ORIGIN_X = "StardewSilverFoldbackOriginX";
    private static final String TAG_ORIGIN_Y = "StardewSilverFoldbackOriginY";
    private static final String TAG_ORIGIN_Z = "StardewSilverFoldbackOriginZ";
    private static final Map<UUID, WeaponDamageSnapshot> WEAPON_SNAPSHOTS =
            new HashMap<>();

    private SilverSaberFoldbackState() {}

    public static void start(Player player, long nowTick, int durationTicks, String weaponId, Vec3 origin) {
        start(
                player,
                nowTick,
                durationTicks,
                weaponId,
                "",
                0,
                origin
        );
    }

    public static void start(
            Player player,
            long nowTick,
            int durationTicks,
            String weaponId,
            String skillId,
            int cooldownTicks,
            Vec3 origin
    ) {
        start(
                player,
                nowTick,
                durationTicks,
                weaponId,
                skillId,
                cooldownTicks,
                origin,
                null
        );
    }

    public static synchronized void start(
            Player player,
            long nowTick,
            int durationTicks,
            String weaponId,
            String skillId,
            int cooldownTicks,
            Vec3 origin,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        CompoundTag root = player.getPersistentData();
        int duration = Math.max(1, durationTicks);
        root.putBoolean(TAG_ACTIVE, true);
        root.putLong(TAG_END_TICK, nowTick + duration);
        root.putInt(TAG_DURATION, duration);
        root.putString(TAG_WEAPON_ID, weaponId == null ? "" : weaponId);
        root.putString(TAG_SKILL_ID, skillId == null ? "" : skillId);
        root.putInt(TAG_COOLDOWN, Math.max(0, cooldownTicks));
        root.putString(
                TAG_DIMENSION,
                player.level().dimension().location().toString()
        );
        root.putDouble(TAG_ORIGIN_X, origin.x);
        root.putDouble(TAG_ORIGIN_Y, origin.y);
        root.putDouble(TAG_ORIGIN_Z, origin.z);
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
        return shouldRemainActive(
                root.getLong(TAG_END_TICK),
                nowTick,
                isInOriginDimension(player)
        );
    }

    public static boolean isActiveRaw(Player player) {
        CompoundTag root = player.getPersistentData();
        return root.getBoolean(TAG_ACTIVE);
    }

    public static long getEndTick(Player player) {
        CompoundTag root = player.getPersistentData();
        return root.getLong(TAG_END_TICK);
    }

    public static String getWeaponId(Player player) {
        CompoundTag root = player.getPersistentData();
        return root.contains(TAG_WEAPON_ID) ? root.getString(TAG_WEAPON_ID) : null;
    }

    public static String getSkillId(Player player) {
        CompoundTag root = player.getPersistentData();
        return root.contains(TAG_SKILL_ID)
                ? root.getString(TAG_SKILL_ID)
                : null;
    }

    public static int getCooldownTicks(Player player) {
        CompoundTag root = player.getPersistentData();
        return root.contains(TAG_COOLDOWN)
                ? Math.max(0, root.getInt(TAG_COOLDOWN))
                : 0;
    }

    public static String getOriginDimension(Player player) {
        CompoundTag root = player.getPersistentData();
        return root.contains(TAG_DIMENSION)
                ? root.getString(TAG_DIMENSION)
                : null;
    }

    public static boolean isInOriginDimension(Player player) {
        String expected = getOriginDimension(player);
        return expected == null
                || expected.isEmpty()
                || expected.equals(
                        player.level().dimension().location().toString()
                );
    }

    public static Vec3 getOrigin(Player player) {
        CompoundTag root = player.getPersistentData();
        double x = root.getDouble(TAG_ORIGIN_X);
        double y = root.getDouble(TAG_ORIGIN_Y);
        double z = root.getDouble(TAG_ORIGIN_Z);
        return new Vec3(x, y, z);
    }

    public static int getDurationTicks(Player player) {
        CompoundTag root = player.getPersistentData();
        int duration = root.getInt(TAG_DURATION);
        return Math.max(1, duration);
    }

    public static synchronized Optional<WeaponDamageSnapshot> getWeaponSnapshot(
            Player player
    ) {
        return Optional.ofNullable(WEAPON_SNAPSHOTS.get(player.getUUID()));
    }

    public static synchronized void clear(Player player) {
        CompoundTag root = player.getPersistentData();
        root.remove(TAG_ACTIVE);
        root.remove(TAG_END_TICK);
        root.remove(TAG_DURATION);
        root.remove(TAG_WEAPON_ID);
        root.remove(TAG_SKILL_ID);
        root.remove(TAG_COOLDOWN);
        root.remove(TAG_DIMENSION);
        root.remove(TAG_ORIGIN_X);
        root.remove(TAG_ORIGIN_Y);
        root.remove(TAG_ORIGIN_Z);
        WEAPON_SNAPSHOTS.remove(player.getUUID());
    }

    static boolean shouldRemainActive(
            long endTick,
            long nowTick,
            boolean sameDimension
    ) {
        return sameDimension && nowTick <= endTick;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SilverSaberSkillHelper.cancelFoldback(
                    player,
                    player.level().getGameTime()
            );
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SilverSaberSkillHelper.cancelFoldback(
                    player,
                    player.level().getGameTime()
            );
        }
    }
}
