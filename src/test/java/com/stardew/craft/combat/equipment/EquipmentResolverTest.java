package com.stardew.craft.combat.equipment;

import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipmentResolverTest {
    @Test
    void publicEquipmentMapsAttackAndWeaponSpeedMultipliers() {
        StardewEquipmentData data = new StardewEquipmentData(
                ResourceLocation.fromNamespaceAndPath("stardewcraft", "ring"),
                0,
                0,
                0,
                0.25F,
                0.0F,
                0.0F,
                0,
                0.0F,
                0.40F,
                0.0F,
                0,
                List.of(),
                Optional.empty()
        );

        EquipmentStats stats = EquipmentResolver.fromApiData(data);

        assertEquals(0.25F, stats.getAttackMultiplier());
        assertEquals(0.40F, stats.getWeaponSpeedMultiplier());
    }
}
