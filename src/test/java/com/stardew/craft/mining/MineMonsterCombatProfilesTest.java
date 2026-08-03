package com.stardew.craft.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineMonsterCombatProfilesTest {
    @Test
    void everyLocalizedMineMonsterHasAnExplicitCombatProfile() {
        assertEquals(MineMonsterNames.ALL_IDS, MineMonsterCombatProfiles.ALL_IDS);
    }

    @Test
    void floorScalingIsDeclaredInsteadOfImplicitlyAppliedAtSpawnSites() {
        MineMonsterCombatProfiles.ResolvedProfile scaled =
                MineMonsterCombatProfiles.resolve("green_slime", 0.8f);
        MineMonsterCombatProfiles.ResolvedProfile bug =
                MineMonsterCombatProfiles.resolve("bug", 0.8f);
        MineMonsterCombatProfiles.ResolvedProfile fixed =
                MineMonsterCombatProfiles.resolve("mutant_grub", 0.8f);

        assertEquals(19.2, scaled.health(), 0.0001);
        assertEquals(4.0, scaled.damage(), 0.0001);
        assertEquals(1.0, bug.health(), 0.0001);
        assertEquals(6.4, bug.damage(), 0.0001);
        assertEquals(100.0, fixed.health(), 0.0001);
        assertEquals(12.0, fixed.damage(), 0.0001);
    }

    @Test
    void prismaticSlimeUsesOriginalFixedCombatStats() {
        MineMonsterCombatProfiles.ResolvedProfile prismatic =
                MineMonsterCombatProfiles.resolve("prismatic_slime", 0.8f);

        assertEquals(1000.0, prismatic.health(), 0.0001);
        assertEquals(35.0, prismatic.damage(), 0.0001);
        assertEquals(0.25, prismatic.movementSpeed(), 0.0001);
    }
}
