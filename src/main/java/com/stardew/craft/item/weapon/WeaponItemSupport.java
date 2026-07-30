package com.stardew.craft.item.weapon;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.WeaponForgeData;
import com.stardew.craft.combat.WeaponStats;
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
    private static final float ATTACK_SPEED_PER_STARDEW_POINT = 0.1F;

    private WeaponItemSupport() {
    }

    static ItemAttributeModifiers createAttributeModifiers(String weaponId, WeaponData data) {
        float averageDamage = (float) (data.getAverageDamage() - 1.0);
        float attacksPerSecond = data.getWeaponType().getAttackSpeed()
                + data.getSpeed() * ATTACK_SPEED_PER_STARDEW_POINT;
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
                .build();
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
                .bonusCritPower((float) Math.max(0, (data.getCritPower() - 1.0) * 100.0))
                .speed(data.getSpeed())
                .defense(data.getDefense())
                .knockback((float) data.getWeight())
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
