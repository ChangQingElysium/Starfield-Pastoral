package com.stardew.craft.item.equipment;

import com.stardew.craft.combat.equipment.EquipmentStats;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.weapon.WeaponIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * SDV 戒指物品基类
 * 不使用 MC 原版装备系统，纯自定义逻辑，通过 V 键 UI 穿戴。
 */
@SuppressWarnings("null")
public class StardewRingItem extends Item implements IStardewItem {

    private final RingType ringType;
    private final int sellPrice;

    public StardewRingItem(RingType ringType, int sellPrice, Properties properties) {
        super(properties.stacksTo(1));
        this.ringType = ringType;
        this.sellPrice = sellPrice;
    }

    public RingType getRingType() {
        return ringType;
    }

    /**
     * 获取该戒指提供的装备属性（Buff 型效果）
     */
    public EquipmentStats getEquipmentStats() {
        return ringType.buildStats();
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.ring";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return sellPrice;
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);

        EquipmentStats stats = getEquipmentStats();
        if (stats.getDefense() > 0) {
            tooltipComponents.add(buildStatLine(WeaponIcons.ICON_DEFENSE,
                    "+" + stats.getDefense(), ChatFormatting.BLUE,
                    "stardewcraft.weapon.tooltip.defense"));
        }
        if (stats.getImmunity() > 0) {
            tooltipComponents.add(buildStatLine(WeaponIcons.ICON_IMMUNITY,
                    "+" + stats.getImmunity(), ChatFormatting.YELLOW,
                    "stardewcraft.tooltip.immunity_label"));
        }
        // 攻击倍率型戒指（Ruby Ring / Iridium Band）
        if (ringType.isAttackMultiplier()) {
            tooltipComponents.add(buildStatLine(WeaponIcons.ICON_DAMAGE,
                    "+" + Math.round(ringType.getAttackMultiplier() * 100) + "%", ChatFormatting.RED,
                    "stardewcraft.tooltip.attack_label"));
        }
        if (stats.getCritChance() > 0) {
            tooltipComponents.add(buildStatLine(WeaponIcons.ICON_CRIT_CHANCE,
                    "+" + Math.round(stats.getCritChance() * 100) + "%", ChatFormatting.YELLOW,
                    "stardewcraft.weapon.tooltip.crit_chance"));
        }
        if (stats.getCritPower() > 0) {
            tooltipComponents.add(buildStatLine(WeaponIcons.ICON_CRIT_POWER,
                    "+" + Math.round(stats.getCritPower() * 100) + "%", ChatFormatting.GOLD,
                    "stardewcraft.weapon.tooltip.crit_damage"));
        }
        if (stats.getKnockbackBonus() > 0) {
            tooltipComponents.add(buildStatLine(WeaponIcons.ICON_WEIGHT,
                    "+" + Math.round(stats.getKnockbackBonus() * 100) + "%", ChatFormatting.GOLD,
                    "stardewcraft.weapon.tooltip.weight"));
        }
        if (stats.getMagneticRadius() > 0) {
            tooltipComponents.add(buildStatLine(WeaponIcons.ICON_MAGNETIC,
                    "+" + stats.getMagneticRadius(), ChatFormatting.AQUA,
                    "stardewcraft.tooltip.magnetic_label"));
        }
        if (stats.getLuck() > 0) {
            tooltipComponents.add(buildStatLine(WeaponIcons.ICON_LUCK,
                    "+" + (int) stats.getLuck(), ChatFormatting.GREEN,
                    "stardewcraft.tooltip.luck_label"));
        }
        // 武器速度型戒指
        if (ringType.isWeaponSpeedMultiplier()) {
            tooltipComponents.add(buildStatLine(WeaponIcons.ICON_SPEED,
                    "+" + Math.round(ringType.getWeaponSpeedMultiplier() * 100) + "%", ChatFormatting.YELLOW,
                    "stardewcraft.weapon.tooltip.speed"));
        }

        // 特殊效果描述
        String descKey = "stardewcraft.ring." + ringType.name().toLowerCase() + ".desc";
        if (ringType.hasSpecialEffect()) {
            tooltipComponents.add(Component.translatable(descKey).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @SuppressWarnings("null")
    private static MutableComponent buildStatLine(String icon, String value, ChatFormatting valueColor, String nameKey) {
        MutableComponent line = Component.empty();
        line.append(Component.literal(" "));
        line.append(WeaponIcons.icon(icon));
        line.append(Component.translatable("stardewcraft.weapon.tooltip.attr_value", value).withStyle(valueColor));
        line.append(Component.translatable(nameKey).withStyle(ChatFormatting.GRAY));
        return line;
    }
}
