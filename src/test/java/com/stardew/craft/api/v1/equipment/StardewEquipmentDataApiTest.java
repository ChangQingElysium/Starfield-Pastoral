package com.stardew.craft.api.v1.equipment;

import com.stardew.craft.combat.equipment.EquipmentSlotResolver;
import com.stardew.craft.combat.skill.WeaponSkillDispatcher;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewEquipmentDataApiTest {
    private static final ResourceLocation TEST_SKILL = ResourceLocation.fromNamespaceAndPath("test", "spark");

    @BeforeAll
    static void registerProviders() {
        StardewEquipmentDataApi.registerProvider(
                ResourceLocation.fromNamespaceAndPath("test", "throwing"), 200,
                stack -> {
                    if (stack.is(Items.BLAZE_ROD)) throw new IllegalStateException("expected test failure");
                    return null;
                });
        StardewEquipmentDataApi.registerProvider(
                ResourceLocation.fromNamespaceAndPath("test", "ring"), 100,
                stack -> stack.is(Items.BLAZE_ROD) ? equipment(EquipmentSlotResolver.RING, Optional.empty()) : null);
        StardewEquipmentDataApi.registerProvider(
                ResourceLocation.fromNamespaceAndPath("test", "weapon"), 100,
                stack -> stack.is(Items.STICK) ? equipment(
                        EquipmentSlotResolver.WEAPON,
                        Optional.of(new StardewEquipmentData.Weapon(
                                "sword", 1, 2, 0.02F, 0, 0, 0, 0,
                                Optional.of(TEST_SKILL), Optional.empty()))) : null);
    }

    @Test
    void providerFailureFallsThroughToTheNextProvider() {
        ItemStack stack = new ItemStack(Items.BLAZE_ROD);
        StardewEquipmentData resolved = StardewEquipmentDataApi.get(stack);
        assertEquals(EquipmentSlotResolver.RING, resolved.slot());
        assertTrue(EquipmentSlotResolver.isRing(stack));
    }

    @Test
    void publicWeaponSkillsAreResolvedForItemsWithoutLegacyWeaponClasses() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertTrue(EquipmentSlotResolver.isWeapon(stack));
        assertEquals(Optional.of(TEST_SKILL), WeaponSkillDispatcher.publicSkillId(stack, false));
        assertEquals(Optional.empty(), WeaponSkillDispatcher.publicSkillId(stack, true));
    }

    private static StardewEquipmentData equipment(
            ResourceLocation slot, Optional<StardewEquipmentData.Weapon> weapon) {
        return new StardewEquipmentData(slot, 1, 2, 3, 0.04F, 0.5F, 8, 0.2F, 1, 0,
                List.of(), weapon);
    }
}
