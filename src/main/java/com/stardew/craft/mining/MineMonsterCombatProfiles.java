package com.stardew.craft.mining;

import java.util.Map;
import java.util.Set;

/**
 * Authoritative combat profiles for mine-monster identities.
 *
 * <p>The values preserve the mod's established Minecraft-adapted balance.
 * Floor scaling is declared separately so spawning code does not duplicate or
 * reinterpret HP, attack, resilience, or movement speed.</p>
 */
public final class MineMonsterCombatProfiles {
    private static final Map<String, Profile> PROFILES = Map.ofEntries(
            entry("green_slime", scaled(24, 5, 0, 0.25)),
            entry("frost_jelly", scaled(106, 7, 0, 0.25)),
            entry("sludge", scaled(205, 16, 0, 0.25)),
            entry("prismatic_slime", inherited()),
            entry("bat", scaled(24, 6, 0, 0.30)),
            entry("frost_bat", scaled(36, 7, 0, 0.30)),
            entry("lava_bat", scaled(80, 15, 0, 0.35)),
            entry("iridium_bat", scaled(300, 25, 0, 0.40)),
            entry("rock_crab", scaled(30, 5, 8, 0.20)),
            entry("truffle_crab", fixed(30, 5, 8, 0.20)),
            entry("lava_crab", scaled(120, 15, 12, 0.25)),
            entry("iridium_crab", scaled(300, 28, 16, 0.20)),
            entry("duggy", scaled(40, 6, 0, 0.20)),
            entry("dust_sprite", scaled(40, 6, 2, 0.35)),
            entry("grub", scaled(20, 4, 0, 0.15)),
            entry("bug", attackScaled(1, 8, 0, 0.30)),
            entry("fly", scaled(22, 6, 0, 0.30)),
            entry("ghost", scaled(96, 10, 3, 0.25)),
            entry("carbon_ghost", scaled(190, 25, 4, 0.30)),
            entry("skeleton", scaled(140, 10, 2, 0.25)),
            entry("rock_golem", scaled(45, 5, 10, 0.18)),
            entry("metal_head", scaled(40, 15, 16, 0.20)),
            entry("shadow_brute", scaled(160, 18, 4, 0.30)),
            entry("shadow_shaman", scaled(80, 17, 2, 0.25)),
            entry("squid_kid", scaled(50, 18, 2, 0.25)),
            entry("mummy", scaled(260, 30, 4, 0.20)),
            entry("serpent", scaled(150, 23, 2, 0.35)),
            entry("royal_serpent", scaled(300, 30, 3, 0.40)),
            entry("pepper_rex", scaled(300, 15, 6, 0.25)),
            entry("big_slime", scaled(200, 20, 2, 0.20)),
            entry("mutant_grub", fixed(100, 12, 0, 0.15)),
            entry("mutant_fly", fixed(66, 12, 0, 0.30))
    );

    public static final Set<String> ALL_IDS = Set.copyOf(PROFILES.keySet());

    private MineMonsterCombatProfiles() {
    }

    public static Profile profile(String monsterId) {
        Profile profile = PROFILES.get(monsterId);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown mine monster profile: " + monsterId);
        }
        return profile;
    }

    public static ResolvedProfile resolve(String monsterId, float floorScaling) {
        Profile profile = profile(monsterId);
        if (profile.scaling() == Scaling.INHERIT) {
            throw new IllegalArgumentException(
                    "Mine monster profile inherits its current stats: " + monsterId);
        }
        if (!Float.isFinite(floorScaling) || floorScaling < 0.0f) {
            throw new IllegalArgumentException("Floor scaling must be finite and non-negative");
        }
        float scaling = floorScaling;
        double health = profile.scaling() == Scaling.FLOOR
                ? profile.health() * scaling
                : profile.health();
        double damage = switch (profile.scaling()) {
            case FLOOR, ATTACK_ONLY -> profile.damage() * scaling;
            case NONE -> profile.damage();
            case INHERIT -> throw new IllegalStateException("inherit handled above");
        };
        return new ResolvedProfile(
                health,
                damage,
                profile.resilience(),
                profile.movementSpeed()
        );
    }

    private static Map.Entry<String, Profile> entry(String id, Profile profile) {
        return Map.entry(id, profile);
    }

    private static Profile scaled(
            double health,
            double damage,
            double resilience,
            double movementSpeed
    ) {
        return new Profile(health, damage, resilience, movementSpeed, Scaling.FLOOR);
    }

    private static Profile attackScaled(
            double health,
            double damage,
            double resilience,
            double movementSpeed
    ) {
        return new Profile(health, damage, resilience, movementSpeed, Scaling.ATTACK_ONLY);
    }

    private static Profile fixed(
            double health,
            double damage,
            double resilience,
            double movementSpeed
    ) {
        return new Profile(health, damage, resilience, movementSpeed, Scaling.NONE);
    }

    private static Profile inherited() {
        return new Profile(0, 0, 0, 0, Scaling.INHERIT);
    }

    public record Profile(
            double health,
            double damage,
            double resilience,
            double movementSpeed,
            Scaling scaling
    ) {
        public Profile {
            if (!Double.isFinite(health)
                    || !Double.isFinite(damage)
                    || !Double.isFinite(resilience)
                    || !Double.isFinite(movementSpeed)
                    || health < 0
                    || damage < 0
                    || resilience < 0
                    || movementSpeed < 0) {
                throw new IllegalArgumentException(
                        "Mine monster combat stats must be finite and non-negative");
            }
        }
    }

    public record ResolvedProfile(
            double health,
            double damage,
            double resilience,
            double movementSpeed
    ) {
    }

    public enum Scaling {
        FLOOR,
        ATTACK_ONLY,
        NONE,
        INHERIT
    }
}
