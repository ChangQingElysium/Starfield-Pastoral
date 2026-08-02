package com.stardew.craft.item.weapon;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.WeaponForgeData;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.StardewWeaponSpeedRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Shared projection from one weapon definition to its Minecraft item state.
 */
final class WeaponItemSupport {
    private static final float MINECRAFT_BASE_ATTACK_RANGE = 3.0F;
    private static final float MINECRAFT_BASE_ATTACK_SPEED = 4.0F;

    private WeaponItemSupport() {
    }

    static ItemAttributeModifiers createAttributeModifiers(String weaponId, WeaponData data) {
        float averageDamage = (float) (data.getAverageDamage() - 1.0);
        float attacksPerSecond = (float) StardewWeaponSpeedRules
                .attacksPerSecondFromRawSpeed(
                        data.getWeaponType(),
                        data.getRawSpeed(),
                        0.0F
                );
        float attackSpeedModifier = attacksPerSecond - MINECRAFT_BASE_ATTACK_SPEED;
        float attackRangeModifier = data.getWeaponType().getAttackRange()
                - MINECRAFT_BASE_ATTACK_RANGE;

        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        modifier(weaponId, "attack_damage", averageDamage),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        modifier(weaponId, "attack_speed", attackSpeedModifier),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ENTITY_INTERACTION_RANGE,
                        modifier(weaponId, "attack_range", attackRangeModifier),
                        EquipmentSlotGroup.MAINHAND
                )
                .build()
                .withTooltip(false);
    }

    static void ensureStats(ItemStack stack, WeaponData data) {
        if (data == null) {
            return;
        }
        if (WeaponStats.hasCurrentDataVersion(stack)) {
            WeaponForgeData.ensure(stack);
            return;
        }

        WeaponStats.builder()
                .weaponType(data.getWeaponType())
                .minDamage(data.getDamageMin())
                .maxDamage(data.getDamageMax())
                .critChance((float) data.getCritChance())
                .bonusCritPower((float) Math.max(
                        0.0,
                        (data.getCritMultiplier() - 3.0) * 50.0
                ))
                .speed(data.getSpeed())
                .rawSpeed(data.getRawSpeed())
                .defense(data.getDefense())
                .precision(data.getPrecision())
                .knockback((float) data.getKnockback())
                .build()
                .writeToItemStack(stack);
        WeaponForgeData.ensure(stack);
    }

    private static AttributeModifier modifier(String weaponId, String attribute, double value) {
        return new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID,
                        "weapon." + weaponId + "." + attribute
                ),
                value,
                AttributeModifier.Operation.ADD_VALUE
        );
    }
}
