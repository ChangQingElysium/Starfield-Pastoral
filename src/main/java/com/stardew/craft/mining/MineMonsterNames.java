package com.stardew.craft.mining;

import java.util.Set;
import net.minecraft.network.chat.Component;

/** Stable mine-monster identities and their client-resolved display names. */
public final class MineMonsterNames {
    private static final String TRANSLATION_PREFIX =
            "entity.stardewcraft.mine_monster.";

    public static final Set<String> ALL_IDS = Set.of(
            "green_slime",
            "frost_jelly",
            "sludge",
            "prismatic_slime",
            "bat",
            "frost_bat",
            "lava_bat",
            "iridium_bat",
            "rock_crab",
            "truffle_crab",
            "lava_crab",
            "iridium_crab",
            "duggy",
            "dust_sprite",
            "grub",
            "bug",
            "fly",
            "ghost",
            "carbon_ghost",
            "skeleton",
            "rock_golem",
            "metal_head",
            "shadow_brute",
            "shadow_shaman",
            "squid_kid",
            "mummy",
            "serpent",
            "royal_serpent",
            "pepper_rex",
            "big_slime",
            "mutant_grub",
            "mutant_fly"
    );

    private MineMonsterNames() {
    }

    public static String translationKey(String monsterId) {
        if (!ALL_IDS.contains(monsterId)) {
            throw new IllegalArgumentException(
                    "Unknown mine monster id: " + monsterId);
        }
        return TRANSLATION_PREFIX + monsterId;
    }

    public static Component displayName(String monsterId) {
        return Component.translatable(translationKey(monsterId));
    }
}
