package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 技能动画期间的普攻锁定
 */
public final class WeaponSkillAnimationLock {

    private static final String TAG_ROOT = "StardewSkillAnimLock";

    private WeaponSkillAnimationLock() {}

    public static boolean isLocked(Player player, long nowTick) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(TAG_ROOT)) {
            return false;
        }
        long endTick = root.getLong(TAG_ROOT);
        return nowTick < endTick;
    }

    public static void setLock(Player player, long nowTick, int durationTicks) {
        if (WeaponSkillRuntime.deferIfPreparing(() ->
                setLock(player, nowTick, durationTicks)
        )) {
            return;
        }
        CompoundTag root = player.getPersistentData();
        root.putLong(TAG_ROOT, nowTick + Math.max(1, durationTicks));
    }

    public static void clear(Player player) {
        if (player != null) {
            player.getPersistentData().remove(TAG_ROOT);
        }
    }
}
