package com.stardew.craft.combat.equipment;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.combat.DimensionDamageMapper;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.WeaponCombatIdentity;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Projects equipment values needed by Minecraft's basic attack timing.
 */
public final class EquipmentPlayerAttributes {
    private static final ResourceLocation WEAPON_SPEED_ID =
            new ResourceLocation(
                    StardewCraft.MODID,
                    "equipment.weapon_speed"
            );
    private static final ResourceLocation DEFENSE_ID =
            new ResourceLocation(
                    StardewCraft.MODID,
                    "equipment.stardew_defense"
            );
    private static final ResourceLocation LUCK_ID =
            new ResourceLocation(
                    StardewCraft.MODID,
                    "equipment.stardew_luck"
            );
    private static final ResourceLocation ATTACK_FLAT_ID =
            new ResourceLocation(
                    StardewCraft.MODID,
                    "equipment.stardew_attack"
            );
    private static final ResourceLocation ATTACK_MULTIPLIER_ID =
            new ResourceLocation(
                    StardewCraft.MODID,
                    "equipment.stardew_attack_multiplier"
            );
    private static final ResourceLocation MAX_HEALTH_ID =
            new ResourceLocation(
                    StardewCraft.MODID,
                    "equipment.stardew_max_health"
            );
    private static final ResourceLocation ATTACK_KNOCKBACK_ID =
            new ResourceLocation(
                    StardewCraft.MODID,
                    "equipment.stardew_attack_knockback"
            );
    private static final ResourceLocation WEAPON_STATS_SPEED_ID =
            new ResourceLocation(
                    StardewCraft.MODID,
                    "equipment.weapon_stats_speed"
            );
    private static final ResourceLocation WEAPON_STATS_RANGE_ID =
            new ResourceLocation(
                    StardewCraft.MODID,
                    "equipment.weapon_stats_range"
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
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        WeaponCombatIdentity.Resolved weaponIdentity =
                WeaponCombatIdentity.resolve(
                        player.getMainHandItem()
                ).orElse(null);
        WeaponStats mainHandStats = WeaponStats.fromItemStack(
                player.getMainHandItem()
        );
        double weaponStatsSpeedCorrection = 0.0D;
        if (weaponIdentity != null) {
            MainHandAttackSpeed itemAttackSpeed = mainHandAttackSpeed(
                    player.getMainHandItem()
            );
            weaponStatsSpeedCorrection = CrossDimensionAttributeRules
                    .weaponRawAttackSpeedCorrection(
                            itemAttackSpeed.attackRate(),
                            itemAttackSpeed.addValueScale(),
                            mainHandStats.getWeaponType(),
                            mainHandStats.getRawSpeed(),
                            mainHandStats.getWeaponSpeedMultiplier(),
                            equipment.getWeaponSpeedMultiplier()
                    );
        }
        syncModifier(
                attackSpeed,
                WEAPON_STATS_SPEED_ID,
                weaponStatsSpeedCorrection,
                AttributeModifier.Operation.ADDITION
        );
        MainHandInteractionRange itemRange = mainHandInteractionRangeParts(
                player.getMainHandItem()
        );
        double weaponRangeCorrection = weaponIdentity != null
                && Math.abs(itemRange.addValueScale()) >= 1.0E-6D
                ? (mainHandStats.getWeaponType().getAttackRange()
                        - itemRange.range()) / itemRange.addValueScale()
                : 0.0D;
        syncModifier(
                player.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get()),
                WEAPON_STATS_RANGE_ID,
                weaponRangeCorrection,
                AttributeModifier.Operation.ADDITION
        );

        syncModifier(
                player.getAttribute(Attributes.LUCK),
                LUCK_ID,
                CrossDimensionAttributeRules.minecraftLuck(
                        data.getLuckLevel() + equipment.getLuck(),
                        PlayerStardewDataAPI.getDailyLuck(player)
                ),
                AttributeModifier.Operation.ADDITION
        );

        boolean inStardewDimension = DimensionDamageMapper.isInStardewDimension(player);
        if (inStardewDimension) {
            removeModifier(player.getAttribute(Attributes.ARMOR), DEFENSE_ID);
            removeModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_FLAT_ID);
            removeModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_MULTIPLIER_ID);
            removeModifier(player.getAttribute(Attributes.MAX_HEALTH), MAX_HEALTH_ID);
            removeModifier(player.getAttribute(Attributes.ATTACK_KNOCKBACK), ATTACK_KNOCKBACK_ID);
            return;
        }

        boolean usesNativeAttack = !WeaponCombatIdentity.isWeapon(
                player.getMainHandItem()
        );

        float defense = equipment.getDefense()
                + mainHandStats.getDefense()
                + data.getTempDefenseBonus()
                + BookPowerEffects.getDefenseBonus(data)
                - (player.hasEffect(com.stardew.craft.effect.ModMobEffects.JINXED.get()) ? 8 : 0);
        syncModifier(
                player.getAttribute(Attributes.ARMOR),
                DEFENSE_ID,
                CrossDimensionAttributeRules.minecraftArmor(defense),
                AttributeModifier.Operation.ADDITION
        );
        syncModifier(
                player.getAttribute(Attributes.ATTACK_DAMAGE),
                ATTACK_FLAT_ID,
                usesNativeAttack
                        ? CrossDimensionAttributeRules.minecraftAttackDamage(
                                equipment.getAttack()
                                        + data.getTempAttackBonus()
                        )
                        : 0.0D,
                AttributeModifier.Operation.ADDITION
        );
        syncModifier(
                player.getAttribute(Attributes.ATTACK_DAMAGE),
                ATTACK_MULTIPLIER_ID,
                usesNativeAttack
                        ? CrossDimensionAttributeRules
                                .minecraftAttackMultiplier(
                                        equipment.getAttackMultiplier(),
                                        data.hasProfession(
                                                ProfessionType.FIGHTER
                                        ),
                                        data.hasProfession(
                                                ProfessionType.BRUTE
                                        )
                                )
                        : 0.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        syncModifier(
                player.getAttribute(Attributes.MAX_HEALTH),
                MAX_HEALTH_ID,
                CrossDimensionAttributeRules.minecraftMaximumHealthBonus(
                        data.getMaxHealth()
                ),
                AttributeModifier.Operation.ADDITION
        );
        syncModifier(
                player.getAttribute(Attributes.ATTACK_KNOCKBACK),
                ATTACK_KNOCKBACK_ID,
                usesNativeAttack
                        ? CrossDimensionAttributeRules.minecraftAttackKnockback(
                                equipment.getKnockbackBonus()
                        )
                        : 0.0D,
                AttributeModifier.Operation.ADDITION
        );
    }

    static double mainHandAttackRate(ItemStack stack) {
        return mainHandAttackSpeed(stack).attackRate();
    }

    static MainHandAttackSpeed mainHandAttackSpeed(ItemStack stack) {
        double[] parts = {4.0D, 0.0D, 0.0D, 1.0D};
        com.stardew.craft.port.PortItemStacks.forEachModifier(stack, EquipmentSlot.MAINHAND,
                (attribute, modifier) -> {
                    if (!attribute.equals(Attributes.ATTACK_SPEED)) {
                        return;
                    }
                    switch (modifier.getOperation()) {
                        case ADDITION -> parts[1] += modifier.getAmount();
                        case MULTIPLY_BASE -> parts[2] += modifier.getAmount();
                        case MULTIPLY_TOTAL -> parts[3] *= 1.0D + modifier.getAmount();
                    }
                }
        );
        double addValueScale = (1.0D + parts[2]) * parts[3];
        return new MainHandAttackSpeed(
                (parts[0] + parts[1]) * addValueScale,
                addValueScale
        );
    }

    static double mainHandInteractionRange(ItemStack stack) {
        return mainHandInteractionRangeParts(stack).range();
    }

    static MainHandInteractionRange mainHandInteractionRangeParts(
            ItemStack stack
    ) {
        double[] parts = {3.0D, 0.0D, 0.0D, 1.0D};
        com.stardew.craft.port.PortItemStacks.forEachModifier(stack, EquipmentSlot.MAINHAND,
                (attribute, modifier) -> {
                    if (!attribute.equals(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get())) {
                        return;
                    }
                    switch (modifier.getOperation()) {
                        case ADDITION -> parts[1] += modifier.getAmount();
                        case MULTIPLY_BASE -> parts[2] += modifier.getAmount();
                        case MULTIPLY_TOTAL ->
                                parts[3] *= 1.0D + modifier.getAmount();
                    }
                }
        );
        double addValueScale = (1.0D + parts[2]) * parts[3];
        return new MainHandInteractionRange(
                (parts[0] + parts[1]) * addValueScale,
                addValueScale
        );
    }

    record MainHandAttackSpeed(double attackRate, double addValueScale) {
    }

    record MainHandInteractionRange(double range, double addValueScale) {
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
        AttributeModifier current = com.stardew.craft.port.PortAttributeModifiers.getModifier(attribute, id);
        if (Math.abs(amount) < 1.0E-6D) {
            if (current != null) {
                com.stardew.craft.port.PortAttributeModifiers.removeModifier(attribute, id);
            }
            return;
        }
        if (current != null
                && current.getOperation() == operation
                && Math.abs(current.getAmount() - amount) < 1.0E-6D) {
            return;
        }
        com.stardew.craft.port.PortAttributeModifiers.addOrUpdateTransientModifier(attribute, com.stardew.craft.port.PortAttributeModifiers.create(id, amount, operation));
    }

    private static void removeModifier(AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null && com.stardew.craft.port.PortAttributeModifiers.hasModifier(attribute, id)) {
            com.stardew.craft.port.PortAttributeModifiers.removeModifier(attribute, id);
        }
    }
}
