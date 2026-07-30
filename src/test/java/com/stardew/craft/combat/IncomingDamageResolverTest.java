package com.stardew.craft.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncomingDamageResolverTest {
    @Test
    void monsterSnapshotOverridesMinecraftProjectileDamage() {
        IncomingDamageResolver.DamageRange range =
                IncomingDamageResolver.resolveRange(
                        5.0f,
                        5.0f,
                        DamageRequest.SourceKind.MONSTER_ATTACK,
                        18.0f,
                        100.0f,
                        20.0f
                );

        assertEquals(16.0f, range.minimum());
        assertEquals(19.0f, range.maximum());
    }

    @Test
    void monsterVarianceMatchesStardewsInclusiveResults() {
        assertEquals(
                new IncomingDamageResolver.DamageRange(4.0f, 5.0f),
                IncomingDamageResolver.stardewMonsterRange(5.0f)
        );
        assertEquals(
                new IncomingDamageResolver.DamageRange(14.0f, 17.0f),
                IncomingDamageResolver.stardewMonsterRange(16.0f)
        );
    }

    @Test
    void environmentDamageStillUsesTheHealthScaleBoundary() {
        IncomingDamageResolver.DamageRange range =
                IncomingDamageResolver.resolveRange(
                        4.0f,
                        4.0f,
                        DamageRequest.SourceKind.ENVIRONMENT,
                        0.0f,
                        100.0f,
                        20.0f
                );

        assertEquals(20.0f, range.minimum());
        assertEquals(20.0f, range.maximum());
    }

    @Test
    void upstreamParryMultiplierIsPreservedWhenUsingMonsterSnapshot() {
        IncomingDamageResolver.DamageRange range =
                IncomingDamageResolver.resolveRange(
                        2.0f,
                        5.0f,
                        DamageRequest.SourceKind.MONSTER_ATTACK,
                        20.0f,
                        100.0f,
                        20.0f
                );

        assertEquals(7.0f, range.minimum());
        assertEquals(8.0f, range.maximum());
    }
}
