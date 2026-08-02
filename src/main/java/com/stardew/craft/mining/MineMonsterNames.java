package com.stardew.craft.mining;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

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

    private static final Map<String, LegacyName> LEGACY_ENGLISH_NAMES = Map.ofEntries(
            legacy("Green Slime", "green_slime", Set.of("sd_mob_slime"),
                    Set.of("sd_tier_2", "sd_tier_3", "sd_mob_prismatic_slime")),
            legacy("Frost Jelly", "frost_jelly", Set.of("sd_mob_slime", "sd_tier_2"),
                    Set.of("sd_mob_prismatic_slime")),
            legacy("Sludge", "sludge", Set.of("sd_mob_slime", "sd_tier_3"),
                    Set.of("sd_mob_prismatic_slime")),
            legacy("Prismatic Slime", "prismatic_slime", Set.of("sd_mob_prismatic_slime"), Set.of()),
            legacy("Bat", "bat", Set.of("sd_mob_bat"), Set.of("sd_tier_2", "sd_tier_3", "sd_tier_4")),
            legacy("Frost Bat", "frost_bat", Set.of("sd_mob_bat", "sd_tier_2"), Set.of()),
            legacy("Lava Bat", "lava_bat", Set.of("sd_mob_bat", "sd_tier_3"), Set.of()),
            legacy("Iridium Bat", "iridium_bat", Set.of("sd_mob_bat", "sd_tier_4"), Set.of()),
            legacy("Rock Crab", "rock_crab", Set.of("sd_mob_crab"),
                    Set.of("sd_truffle_crab", "sd_tier_2", "sd_tier_4")),
            legacy("Truffle Crab", "truffle_crab", Set.of("sd_mob_crab", "sd_truffle_crab"), Set.of()),
            legacy("Lava Crab", "lava_crab", Set.of("sd_mob_crab", "sd_tier_2"),
                    Set.of("sd_truffle_crab")),
            legacy("Iridium Crab", "iridium_crab", Set.of("sd_mob_crab", "sd_tier_4"),
                    Set.of("sd_truffle_crab")),
            legacy("Duggy", "duggy", Set.of("sd_mob_duggy"), Set.of()),
            legacy("Dust Spirit", "dust_sprite", Set.of("sd_mob_dust_sprite"), Set.of()),
            legacy("Grub", "grub", Set.of("sd_mob_grub"), Set.of("sd_mob_mutant_grub")),
            legacy("Bug", "bug", Set.of("sd_mob_bug"), Set.of()),
            legacy("Fly", "fly", Set.of("sd_mob_fly"), Set.of("sd_mob_mutant_fly")),
            legacy("Ghost", "ghost", Set.of("sd_mob_ghost"), Set.of("sd_tier_skull")),
            legacy("Carbon Ghost", "carbon_ghost", Set.of("sd_mob_ghost", "sd_tier_skull"), Set.of()),
            legacy("Skeleton", "skeleton", Set.of("sd_mob_skeleton"), Set.of()),
            legacy("Rock Golem", "rock_golem", Set.of("sd_mob_golem"), Set.of()),
            legacy("Metal Head", "metal_head", Set.of("sd_mob_metal_head"), Set.of()),
            legacy("Shadow Brute", "shadow_brute", Set.of("sd_mob_shadow"), Set.of("sd_tier_2")),
            legacy("Shadow Shaman", "shadow_shaman", Set.of("sd_mob_shadow", "sd_tier_2"), Set.of()),
            legacy("Squid Kid", "squid_kid", Set.of("sd_mob_squid"), Set.of()),
            legacy("Mummy", "mummy", Set.of("sd_mob_mummy"), Set.of()),
            legacy("Serpent", "serpent", Set.of("sd_mob_serpent"), Set.of()),
            legacy("Royal Serpent", "royal_serpent", Set.of("sd_mob_royal_serpent"), Set.of()),
            legacy("Pepper Rex", "pepper_rex", Set.of("sd_mob_dino"), Set.of()),
            legacy("Big Slime", "big_slime", Set.of("sd_mob_bigslime_skull"), Set.of()),
            legacy("Mutant Grub", "mutant_grub", Set.of("sd_mob_mutant_grub"), Set.of()),
            legacy("Mutant Fly", "mutant_fly", Set.of("sd_mob_mutant_fly"), Set.of())
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

    /** Converts only an untouched legacy English mine-monster name. */
    public static Optional<Component> migrateLegacyDisplayName(
            Component customName,
            Set<String> entityTags
    ) {
        if (customName == null
                || entityTags == null
                || !(customName.getContents()
                instanceof PlainTextContents.LiteralContents literal)
                || !customName.getSiblings().isEmpty()
                || !Style.EMPTY.equals(customName.getStyle())) {
            return Optional.empty();
        }
        LegacyName legacy = LEGACY_ENGLISH_NAMES.get(literal.text());
        if (legacy == null || !legacy.matches(entityTags)) {
            return Optional.empty();
        }
        return Optional.of(displayName(legacy.monsterId()));
    }

    private static Map.Entry<String, LegacyName> legacy(
            String englishName,
            String monsterId,
            Set<String> requiredTags,
            Set<String> forbiddenTags
    ) {
        return Map.entry(
                englishName,
                new LegacyName(monsterId, requiredTags, forbiddenTags)
        );
    }

    private record LegacyName(
            String monsterId,
            Set<String> requiredTags,
            Set<String> forbiddenTags
    ) {
        private boolean matches(Set<String> tags) {
            return tags.containsAll(requiredTags)
                    && forbiddenTags.stream().noneMatch(tags::contains);
        }
    }
}
