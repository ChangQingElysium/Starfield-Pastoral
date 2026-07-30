package com.stardew.craft.entity.projectile;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TideAnchorProjectileEntityTest {
    private static final HolderLookup.Provider REGISTRIES =
            VanillaRegistries.createLookup();

    @Test
    void impactUsesTheRegistryMajorDamageSnapshot() {
        SkillContext impact = TideAnchorProjectileEntity.createHitContext(
                "tide_anchor",
                1.5F
        );

        assertEquals("tide_anchor", impact.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, impact.getTier());
        assertEquals(1.5F, impact.getDamageMultiplier());
        assertFalse(impact.isIgnoreDefense());
        assertFalse(impact.isGuaranteedCrit());
        assertEquals(
                5,
                TideAnchorProjectileEntity.HIT_CONTEXT_LIFETIME_TICKS
        );
    }

    @Test
    void projectileExpiresAfterTheOriginalInclusiveLifetimeWindow() {
        assertFalse(TideAnchorProjectileEntity.isExpired(80));
        assertTrue(TideAnchorProjectileEntity.isExpired(81));
        assertEquals(80, TideAnchorProjectileEntity.MAX_LIFETIME_TICKS);
    }

    @Test
    void markedTargetKeepsTheOriginalFiveSecondRoot() {
        assertEquals(100, TideAnchorProjectileEntity.ROOT_DURATION_TICKS);
        assertEquals(4, TideAnchorProjectileEntity.ROOT_SLOW_AMPLIFIER);
        assertEquals(128, TideAnchorProjectileEntity.ROOT_JUMP_AMPLIFIER);
        assertEquals(
                20,
                TideAnchorProjectileEntity.WATER_RING_DURATION_TICKS
        );
        assertEquals(0.03, TideAnchorProjectileEntity.GRAVITY);
    }

    @Test
    void releaseWeaponSnapshotRoundTripsAllItemComponents() {
        ResourceLocation weaponId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft",
                "neptunes_glaive"
        );
        ItemStack weapon = new ItemStack(Items.TRIDENT);
        weapon.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("release anchor")
        );
        WeaponDamageSnapshot snapshot =
                WeaponDamageSnapshot.capture(weaponId, weapon);
        CompoundTag tag = new CompoundTag();

        TideAnchorProjectileEntity.writeReleaseWeaponSnapshot(
                tag,
                snapshot,
                REGISTRIES
        );
        WeaponDamageSnapshot restored =
                TideAnchorProjectileEntity.readReleaseWeaponSnapshot(
                        tag,
                        REGISTRIES
                );

        assertEquals(weaponId, restored.weaponId());
        assertTrue(restored.weapon().is(Items.TRIDENT));
        assertEquals(
                Component.literal("release anchor"),
                restored.weapon().get(DataComponents.CUSTOM_NAME)
        );
    }

    @Test
    void legacyProjectileTagWithoutSnapshotRemainsReadable() {
        assertNull(TideAnchorProjectileEntity.readReleaseWeaponSnapshot(
                new CompoundTag(),
                REGISTRIES
        ));
    }
}
