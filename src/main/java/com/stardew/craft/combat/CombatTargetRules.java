package com.stardew.craft.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/** Shared classification for targets that may grant combat rewards. */
public final class CombatTargetRules {
    private static final String STARDEW_MONSTER_TAG_PREFIX = "sd_mob_";

    private CombatTargetRules() {
    }

    public static boolean isCombatMonster(LivingEntity target) {
        return target != null && isCombatMonster(
                target instanceof Enemy,
                target.getTags()
        );
    }

    static boolean isCombatMonster(
            boolean vanillaEnemy,
            Iterable<String> tags
    ) {
        if (vanillaEnemy) {
            return true;
        }
        if (tags == null) {
            return false;
        }
        for (String tag : tags) {
            if (tag != null && tag.startsWith(STARDEW_MONSTER_TAG_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
