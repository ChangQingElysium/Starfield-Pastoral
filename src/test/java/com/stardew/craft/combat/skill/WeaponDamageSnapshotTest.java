package com.stardew.craft.combat.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponDamageSnapshotTest {
    @Test
    void captureAndAccessorDoNotExposeMutableStackReference() {
        ResourceLocation weaponId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft",
                "test_weapon"
        );
        ItemStack source = new ItemStack(Items.IRON_SWORD);
        WeaponDamageSnapshot snapshot =
                WeaponDamageSnapshot.capture(weaponId, source);

        source.setCount(2);
        ItemStack firstRead = snapshot.weapon();
        firstRead.setCount(3);

        assertEquals(weaponId, snapshot.weaponId());
        assertEquals(1, snapshot.weapon().getCount());
    }
}
