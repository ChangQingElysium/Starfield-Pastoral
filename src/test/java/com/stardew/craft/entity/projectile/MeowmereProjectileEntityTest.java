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

class MeowmereProjectileEntityTest {
    private static final HolderLookup.Provider REGISTRIES =
            VanillaRegistries.createLookup();

    @Test
    void rainbowBoltUsesTheAuthoredMinorOneHundredPercentContext() {
        SkillContext context = MeowmereProjectileEntity.createHitContext(
                "meowmere_shot",
                SkillContext.SkillTier.MINOR,
                1.0F
        );

        assertEquals("meowmere_shot", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.0F, context.getDamageMultiplier());
        assertFalse(context.isIgnoreDefense());
        assertFalse(context.isGuaranteedCrit());
        assertEquals(
                5,
                MeowmereProjectileEntity.HIT_CONTEXT_LIFETIME_TICKS
        );
    }

    @Test
    void legacySymphonyProfileRemainsMajorAndEightyPercent() {
        SkillContext context = MeowmereProjectileEntity.createHitContext(
                "meowmere_symphony",
                SkillContext.SkillTier.MAJOR,
                0.8F
        );

        assertEquals("meowmere_symphony", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, context.getTier());
        assertEquals(0.8F, context.getDamageMultiplier());
        assertEquals(
                SkillContext.SkillTier.MAJOR,
                MeowmereProjectileEntity.defaultTier(
                        "meowmere_symphony"
                )
        );
        assertEquals(
                0.8F,
                MeowmereProjectileEntity.defaultDamageMultiplier(
                        "meowmere_symphony"
                )
        );
    }

    @Test
    void projectileKeepsFourBouncesAndExpiresOnTickTwoHundredOne() {
        assertEquals(4, MeowmereProjectileEntity.MAX_BOUNCES);
        assertTrue(MeowmereProjectileEntity.canBounce(3));
        assertFalse(MeowmereProjectileEntity.canBounce(4));
        assertFalse(MeowmereProjectileEntity.isExpired(200));
        assertTrue(MeowmereProjectileEntity.isExpired(201));
        assertEquals(0.85, MeowmereProjectileEntity.BOUNCE_VELOCITY_RETENTION);
    }

    @Test
    void zeroPierceDiscardsOnTheFirstEntityHit() {
        assertTrue(MeowmereProjectileEntity.discardsAfterEntityHit(0));
        assertFalse(MeowmereProjectileEntity.discardsAfterEntityHit(1));
        assertFalse(MeowmereProjectileEntity.discardsAfterEntityHit(-1));
    }

    @Test
    void onePierceAllowsTwoEntityHits() {
        assertFalse(MeowmereProjectileEntity.discardsAfterEntityHit(1));
        assertTrue(MeowmereProjectileEntity.discardsAfterEntityHit(0));
    }

    @Test
    void existingFivePointTrailContractIsUnchanged() {
        assertEquals(5, MeowmereProjectileEntity.TRAIL_MAX_AGE);
        assertEquals(5, MeowmereProjectileEntity.TRAIL_MAX_POINTS);
    }

    @Test
    void releaseWeaponSnapshotRoundTripsAllItemComponents() {
        ResourceLocation weaponId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft",
                "meowmere"
        );
        ItemStack weapon = new ItemStack(Items.IRON_SWORD);
        weapon.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("release meowmere")
        );
        WeaponDamageSnapshot snapshot =
                WeaponDamageSnapshot.capture(weaponId, weapon);
        CompoundTag tag = new CompoundTag();

        MeowmereProjectileEntity.writeReleaseWeaponSnapshot(
                tag,
                snapshot,
                REGISTRIES
        );
        WeaponDamageSnapshot restored =
                MeowmereProjectileEntity.readReleaseWeaponSnapshot(
                        tag,
                        REGISTRIES
                );

        assertEquals(weaponId, restored.weaponId());
        assertTrue(restored.weapon().is(Items.IRON_SWORD));
        assertEquals(
                Component.literal("release meowmere"),
                restored.weapon().get(DataComponents.CUSTOM_NAME)
        );
    }

    @Test
    void legacyProjectileTagWithoutSnapshotRemainsReadable() {
        assertNull(MeowmereProjectileEntity.readReleaseWeaponSnapshot(
                new CompoundTag(),
                REGISTRIES
        ));
    }
}
