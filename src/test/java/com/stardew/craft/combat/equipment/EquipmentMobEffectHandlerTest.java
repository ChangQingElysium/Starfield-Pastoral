package com.stardew.craft.combat.equipment;

import net.minecraft.world.effect.MobEffectCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentMobEffectHandlerTest {
    @Test
    void immunityBoundaryOnlyIncludesEffectsMinecraftMarksHarmful() {
        assertTrue(EquipmentMobEffectHandler.isNegativeCategory(
                MobEffectCategory.HARMFUL
        ));
        assertFalse(EquipmentMobEffectHandler.isNegativeCategory(
                MobEffectCategory.NEUTRAL
        ));
        assertFalse(EquipmentMobEffectHandler.isNegativeCategory(
                MobEffectCategory.BENEFICIAL
        ));
    }
}
