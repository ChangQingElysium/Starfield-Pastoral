package com.stardew.craft.entity.projectile;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemperedBilletProjectileEntityTest {
    private static final HolderLookup.Provider REGISTRIES =
            VanillaRegistries.createLookup();

    @Test
    void releaseWeaponSnapshotRoundTripsAllItemComponents() {
        ResourceLocation weaponId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft",
                "tempered_broadsword"
        );
        ItemStack weapon = new ItemStack(Items.IRON_SWORD);
        weapon.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("release tempered broadsword")
        );
        WeaponDamageSnapshot snapshot =
                WeaponDamageSnapshot.capture(weaponId, weapon);
        CompoundTag tag = new CompoundTag();

        TemperedBilletProjectileEntity.writeReleaseWeaponSnapshot(
                tag,
                snapshot,
                REGISTRIES
        );
        WeaponDamageSnapshot restored =
                TemperedBilletProjectileEntity.readReleaseWeaponSnapshot(
                        tag,
                        REGISTRIES
                );

        assertEquals(weaponId, restored.weaponId());
        assertTrue(restored.weapon().is(Items.IRON_SWORD));
        assertEquals(
                Component.literal("release tempered broadsword"),
                restored.weapon().get(DataComponents.CUSTOM_NAME)
        );
    }

    @Test
    void legacyProjectileTagWithoutSnapshotRemainsReadable() {
        assertNull(TemperedBilletProjectileEntity.readReleaseWeaponSnapshot(
                new CompoundTag(),
                REGISTRIES
        ));
    }
}
