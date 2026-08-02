package com.stardew.craft.api.v1.equipment;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.combat.WeaponStats;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                                "sword", 1, 2, 0.02F, 2, 0, 0, 0,
                                Optional.of(TEST_SKILL), Optional.empty()))) : null);
        StardewEquipmentDataApi.registerProvider(
                ResourceLocation.fromNamespaceAndPath("test", "raw_speed_weapon"),
                100,
                stack -> stack.is(Items.IRON_SWORD) ? equipment(
                        EquipmentSlotResolver.WEAPON,
                        Optional.of(new StardewEquipmentData.Weapon(
                                "sword",
                                1,
                                2,
                                0.02F,
                                1,
                                0,
                                0,
                                1.0F,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(3)
                        ))) : null
        );
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
        assertEquals(
                0.5F,
                WeaponStats.fromItemStack(stack).getCritPowerMultiplierBonus()
        );
        assertEquals(4, WeaponStats.fromItemStack(stack).getRawSpeed());
        assertEquals(2, WeaponStats.fromItemStack(stack).getSpeed());
    }

    @Test
    void optionalRawSpeedOverridesTheLegacyDisplayedSpeed() {
        WeaponStats stats = WeaponStats.fromItemStack(
                new ItemStack(Items.IRON_SWORD)
        );

        assertEquals(3, stats.getRawSpeed());
        assertEquals(1, stats.getSpeed());
    }

    @Test
    void weaponCodecKeepsLegacySpeedAndRoundTripsOptionalRawSpeed() {
        StardewEquipmentData.Weapon legacy =
                StardewEquipmentData.Weapon.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("""
                                {
                                  "min_damage": 1.0,
                                  "max_damage": 2.0,
                                  "speed": 2
                                }
                                """)
                ).result().orElseThrow();
        assertEquals(2, legacy.speed());
        assertEquals(Optional.empty(), legacy.rawSpeed());
        assertFalse(StardewEquipmentData.Weapon.CODEC.encodeStart(
                JsonOps.INSTANCE,
                legacy
        ).result().orElseThrow().getAsJsonObject().has("raw_speed"));

        StardewEquipmentData.Weapon exact =
                StardewEquipmentData.Weapon.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("""
                                {
                                  "min_damage": 1.0,
                                  "max_damage": 2.0,
                                  "speed": 1,
                                  "raw_speed": 3
                                }
                                """)
                ).result().orElseThrow();
        assertEquals(1, exact.speed());
        assertEquals(Optional.of(3), exact.rawSpeed());
        assertEquals(
                3,
                StardewEquipmentData.Weapon.CODEC.encodeStart(
                        JsonOps.INSTANCE,
                        exact
                ).result().orElseThrow().getAsJsonObject()
                        .get("raw_speed").getAsInt()
        );
    }

    private static StardewEquipmentData equipment(
            ResourceLocation slot, Optional<StardewEquipmentData.Weapon> weapon) {
        return new StardewEquipmentData(slot, 1, 2, 3, 0.04F, 0.5F, 8, 0.2F, 1, 0,
                List.of(), weapon);
    }
}
