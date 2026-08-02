package com.stardew.craft.combat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;

/** Stardew combat XP mapping for one authoritative kill. */
final class CombatExperienceRules {
    private CombatExperienceRules() {
    }

    static int experienceForKill(LivingEntity target) {
        var tags = target.getTags();
        if (tags.contains("sd_mob_slime")) {
            if (tags.contains("sd_tier_5")) return 20;
            if (tags.contains("sd_tier_4")) return 10;
            if (tags.contains("sd_tier_3")) return 6;
            if (tags.contains("sd_tier_2")) return 5;
            return 3;
        }
        if (tags.contains("sd_mob_bat")) {
            if (tags.contains("sd_tier_4")) return 15;
            if (tags.contains("sd_tier_3")) return 10;
            if (tags.contains("sd_tier_2")) return 7;
            return 5;
        }
        if (tags.contains("sd_mob_fly")) return 3;
        if (tags.contains("sd_mob_grub")) return 2;
        if (tags.contains("sd_mob_bug")) {
            return tags.contains("sd_tier_2") ? 10 : 5;
        }
        if (tags.contains("sd_mob_dust_sprite")) return 3;
        if (tags.contains("sd_mob_skeleton")) {
            return tags.contains("sd_tier_3") ? 20 : 15;
        }
        if (tags.contains("sd_mob_ghost")) {
            return tags.contains("sd_tier_2") ? 20 : 15;
        }
        if (tags.contains("sd_mob_mummy")) return 20;
        if (tags.contains("sd_mob_serpent")) return 10;
        if (tags.contains("sd_mob_crab")) {
            if (tags.contains("sd_tier_3")) return 12;
            if (tags.contains("sd_tier_2")) return 8;
            return 5;
        }
        if (tags.contains("sd_mob_golem")) {
            return tags.contains("sd_tier_2") ? 15 : 10;
        }
        if (tags.contains("sd_mob_shadow")) {
            return tags.contains("sd_tier_2") ? 15 : 12;
        }
        if (tags.contains("sd_mob_duggy")) return 5;
        if (tags.contains("sd_mob_metal_head")) return 15;
        if (tags.contains("sd_mob_squid")) return 10;

        String path = BuiltInRegistries.ENTITY_TYPE
                .getKey(target.getType())
                .getPath();
        return switch (path) {
            case "slime" -> 3;
            case "phantom", "bat" -> 5;
            case "endermite" -> 2;
            case "spider", "cave_spider", "silverfish" -> 5;
            case "skeleton", "stray", "wither_skeleton", "zombie",
                    "drowned", "blaze" -> 15;
            case "vex" -> 10;
            default -> 3;
        };
    }
}
