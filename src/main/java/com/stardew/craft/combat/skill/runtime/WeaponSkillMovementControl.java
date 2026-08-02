package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.combat.skill.TideAnchorRootTracker;
import com.stardew.craft.combat.skill.YetiFreezeTracker;
import net.minecraft.world.entity.LivingEntity;

/** Single query boundary for hard controls that prohibit skill displacement. */
public final class WeaponSkillMovementControl {
    private WeaponSkillMovementControl() {
    }

    public static boolean isLocked(LivingEntity entity, long nowTick) {
        return YetiFreezeTracker.isMovementLocked(entity, nowTick)
                || TideAnchorRootTracker.isMovementLocked(entity, nowTick);
    }
}
