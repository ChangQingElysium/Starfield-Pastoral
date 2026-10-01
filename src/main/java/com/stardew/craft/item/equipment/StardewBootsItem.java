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
 * SDV 靴子物品基类
 * 提供防御和免疫两个属性，通过 V 键 UI 穿戴。
 */
@SuppressWarnings("null")
public class StardewBootsItem extends Item implements IStardewItem {

    private final BootsType bootsType;
    private final int sellPrice;

    public StardewBootsItem(BootsType bootsType, Properties properties) {
        super(properties.stacksTo(1));
        this.bootsType = bootsType;
        this.sellPrice = bootsType.getDefense() * 100 + bootsType.getImmunity() * 100;
    }

    public BootsType getBootsType() {
        return bootsType;
    }

    public EquipmentStats getEquipmentStats() {
        return EquipmentStats.builder()
                .defense(bootsType.getDefense())
                .immunity(bootsType.getImmunity())
                .build();
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.boots";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return sellPrice;
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);

        if (bootsType.getDefense() > 0) {
            tooltipComponents.add(buildStatLine(WeaponIcons.ICON_DEFENSE,
                    "+" + bootsType.getDefense(), ChatFormatting.BLUE,
                    "stardewcraft.weapon.tooltip.defense"));
        }
        if (bootsType.getImmunity() > 0) {
            tooltipComponents.add(buildStatLine(WeaponIcons.ICON_IMMUNITY,
                    "+" + bootsType.getImmunity(), ChatFormatting.YELLOW,
                    "stardewcraft.tooltip.immunity_label"));
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
