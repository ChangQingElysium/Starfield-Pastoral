package com.stardew.craft.combat.equipment;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.combat.DimensionDamageMapper;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Projects equipment values needed by Minecraft's basic attack timing.
 */
public final class EquipmentPlayerAttributes {
    private static final ResourceLocation WEAPON_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "equipment.weapon_speed"
            );
    private static final ResourceLocation DEFENSE_ID =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "equipment.stardew_defense"
            );
    private static final ResourceLocation LUCK_ID =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "equipment.stardew_luck"
            );
    private static final ResourceLocation ATTACK_FLAT_ID =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "equipment.stardew_attack"
            );
    private static final ResourceLocation ATTACK_MULTIPLIER_ID =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "equipment.stardew_attack_multiplier"
            );

    private EquipmentPlayerAttributes() {
    }

    public static void sync(ServerPlayer player) {
        EquipmentStats equipment = EquipmentResolver.getMergedStats(player);
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);

        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        syncModifier(
                attackSpeed,
                WEAPON_SPEED_ID,
                CombatRingRules.weaponSpeedToAttackRateBonus(
                        equipment.getWeaponSpeedMultiplier()
                ),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );

        syncModifier(
                player.getAttribute(Attributes.LUCK),
                LUCK_ID,
                CrossDimensionAttributeRules.minecraftLuck(
                        data.getLuckLevel() + equipment.getLuck(),
                        data.getDailyLuck()
                ),
                AttributeModifier.Operation.ADD_VALUE
        );

        boolean inStardewDimension = DimensionDamageMapper.isInStardewDimension(player);
        if (inStardewDimension) {
            removeModifier(player.getAttribute(Attributes.ARMOR), DEFENSE_ID);
            removeModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_FLAT_ID);
            removeModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_MULTIPLIER_ID);
            return;
        }

        float defense = equipment.getDefense()
                + WeaponStats.fromItemStack(player.getMainHandItem()).getDefense()
                + data.getTempDefenseBonus()
                + BookPowerEffects.getDefenseBonus(data);
        syncModifier(
                player.getAttribute(Attributes.ARMOR),
                DEFENSE_ID,
                CrossDimensionAttributeRules.minecraftArmor(defense),
                AttributeModifier.Operation.ADD_VALUE
        );
        syncModifier(
                player.getAttribute(Attributes.ATTACK_DAMAGE),
                ATTACK_FLAT_ID,
                CrossDimensionAttributeRules.minecraftAttackDamage(
                        equipment.getAttack() + data.getTempAttackBonus()
                ),
                AttributeModifier.Operation.ADD_VALUE
        );
        syncModifier(
                player.getAttribute(Attributes.ATTACK_DAMAGE),
                ATTACK_MULTIPLIER_ID,
                equipment.getAttackMultiplier(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    private static void syncModifier(
            AttributeInstance attribute,
            ResourceLocation id,
            double amount,
            AttributeModifier.Operation operation
    ) {
        if (attribute == null) {
            return;
        }
        AttributeModifier current = attribute.getModifier(id);
        if (Math.abs(amount) < 1.0E-6D) {
            if (current != null) {
                attribute.removeModifier(id);
            }
            return;
        }
        if (current != null
                && current.operation() == operation
                && Math.abs(current.amount() - amount) < 1.0E-6D) {
            return;
        }
        attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    private static void removeModifier(AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null && attribute.hasModifier(id)) {
            attribute.removeModifier(id);
        }
    }
}
