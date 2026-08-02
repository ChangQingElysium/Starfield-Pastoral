package com.stardew.craft.combat.equipment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipmentPlayerAttributesTest {
    @Test
    void publicWeaponAttackRateIgnoresAttackDamageModifiers() {
        assertEquals(
                1.6D,
                EquipmentPlayerAttributes.mainHandAttackRate(
                        new ItemStack(Items.DIAMOND_SWORD)
                ),
                0.000001D
        );
    }

    @Test
    void itemWithoutAttackSpeedModifierKeepsPlayerBaseRate() {
        assertEquals(
                4.0D,
                EquipmentPlayerAttributes.mainHandAttackRate(
                        new ItemStack(Items.STICK)
                ),
                0.000001D
        );
    }

    @Test
    void multipliedBaseUsesBaseAfterAddValueAndStillIgnoresAttackDamage() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(
                DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.builder()
                        .add(
                                Attributes.ATTACK_SPEED,
                                modifier("speed_add", 2.0D, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND
                        )
                        .add(
                                Attributes.ATTACK_SPEED,
                                modifier("speed_base", 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                                EquipmentSlotGroup.MAINHAND
                        )
                        .add(
                                Attributes.ATTACK_DAMAGE,
                                modifier("damage", 100.0D, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND
                        )
                        .build()
        );

        assertEquals(
                9.0D,
                EquipmentPlayerAttributes.mainHandAttackRate(stack),
                0.000001D
        );
        assertEquals(
                1.5D,
                EquipmentPlayerAttributes.mainHandAttackSpeed(stack)
                        .addValueScale(),
                0.000001D
        );
    }

    @Test
    void interactionRangeReadsOnlyTheMainHandItemProjection() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(
                DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.builder()
                        .add(
                                Attributes.ENTITY_INTERACTION_RANGE,
                                modifier("range", 1.0D, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND
                        )
                        .build()
        );
        assertEquals(
                4.0D,
                EquipmentPlayerAttributes.mainHandInteractionRange(stack),
                0.000001D
        );
    }

    private static AttributeModifier modifier(
            String path,
            double amount,
            AttributeModifier.Operation operation
    ) {
        return new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath("stardewcraft_test", path),
                amount,
                operation
        );
    }
}
