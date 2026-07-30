package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.WeaponType;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LavaKatanaReverbTrackerTest {
    @Test
    void reverbUsesItsAuthoredInclusiveFourSecondWindow() {
        long endTick = 180L;

        assertEquals(
            LavaKatanaReverbTracker.Status.ACTIVE,
            LavaKatanaReverbTracker.statusForSnapshot(
                true,
                endTick,
                endTick
            )
        );
        assertEquals(
            LavaKatanaReverbTracker.Status.COMPLETED,
            LavaKatanaReverbTracker.statusForSnapshot(
                true,
                endTick + 1L,
                endTick
            )
        );
        assertEquals(
            80,
            LavaKatanaReverbTracker.ACTIVE_DURATION_TICKS
        );
        assertEquals(
            5,
            LavaKatanaReverbTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(
            1.5F,
            LavaKatanaReverbTracker.FINISHER_BASE_SCALAR
        );
        assertEquals(
            0.05F,
            LavaKatanaReverbTracker.FINISHER_HEAT_SCALAR
        );
    }

    @Test
    void finisherPreservesRemainingBurnJumpAndHeatFormula() {
        assertEquals(
            4,
            LavaKatanaReverbTracker.remainingBurnJumps(39)
        );
        assertEquals(
            4,
            LavaKatanaReverbTracker.remainingBurnJumps(40)
        );
        assertEquals(
            0,
            LavaKatanaReverbTracker.remainingBurnJumps(0)
        );
        assertEquals(
            3.85F,
            LavaKatanaReverbTracker.finisherDamageMultiplier(40, 5),
            0.0001F
        );

        SkillContext finisher =
            LavaKatanaReverbTracker.createFinisherContext(3.85F);
        assertEquals(
            LavaKatanaReverbTracker.FINISHER_SKILL_ID,
            finisher.getSkillId()
        );
        assertEquals(
            SkillContext.SkillTier.MAJOR,
            finisher.getTier()
        );
        assertEquals(3.85F, finisher.getDamageMultiplier());
        assertFalse(finisher.isIgnoreDefense());
        assertFalse(finisher.isGuaranteedCrit());
    }

    @Test
    void activeSessionIsBoundToItsStartingDimension() {
        assertEquals(
            LavaKatanaReverbTracker.Status.INVALIDATED,
            LavaKatanaReverbTracker.statusForSnapshot(
                false,
                120L,
                180L
            )
        );
        assertTrue(LavaKatanaReverbTracker.isSameDimension(
            Level.OVERWORLD,
            Level.OVERWORLD
        ));
        assertFalse(LavaKatanaReverbTracker.isSameDimension(
            Level.OVERWORLD,
            Level.NETHER
        ));
    }

    @Test
    void logoutCleanupIsIdempotentWithoutOnlinePlayer() {
        assertDoesNotThrow(() ->
            LavaKatanaReverbTracker.removePlayer(UUID.randomUUID())
        );
    }

    @Test
    void finisherStatsComeFromTheImmutableReleaseSnapshot() {
        ItemStack releasedWeapon = new ItemStack(Items.IRON_SWORD);
        WeaponStats.builder()
            .weaponType(WeaponType.SWORD)
            .minDamage(30.0F)
            .maxDamage(40.0F)
            .build()
            .writeToItemStack(releasedWeapon);
        WeaponDamageSnapshot snapshot = WeaponDamageSnapshot.capture(
            ResourceLocation.fromNamespaceAndPath(
                "stardewcraft",
                "lava_katana"
            ),
            releasedWeapon
        );

        WeaponStats.builder()
            .weaponType(WeaponType.SWORD)
            .minDamage(1.0F)
            .maxDamage(1.0F)
            .build()
            .writeToItemStack(releasedWeapon);

        assertEquals(
            35.0F,
            LavaKatanaReverbTracker
                .weaponStatsFromSnapshot(snapshot)
                .getAverageDamage()
        );
    }

    @Test
    void releaseSnapshotStartKeepsTheLegacyOverload() {
        assertDoesNotThrow(() ->
            LavaKatanaReverbTracker.class.getDeclaredMethod(
                "start",
                ServerPlayer.class,
                long.class,
                int.class
            )
        );
        assertDoesNotThrow(() ->
            LavaKatanaReverbTracker.class.getDeclaredMethod(
                "start",
                ServerPlayer.class,
                long.class,
                int.class,
                WeaponDamageSnapshot.class
            )
        );
    }
}
