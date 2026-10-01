package com.stardew.craft.item.fish.crabpot;

import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 蟹笼物品基类
 * 用于蟹笼捕获的海洋生物
 * 
 * 蟹笼物品的品质规则:
 * - 普通蟹笼: 只能获得普通品质
 * - 使用高级鱼饵: 可以获得银星品质
 * - 通过采集获得的贝类(蛤蜊、鸟蛤、贻贝、牡蛎): 可以获得金星和铱星品质
 */
public class CrabPotCatchItem extends Item implements IStardewItem {

    /** 按品质存储的售价 [普通, 银星, 金星, 铱星] */
    protected final int[] priceByQuality;
    
    /**
     * 创建蟹笼物品
     * @param priceByQuality 按品质的售价数组 [普通, 银星, 金星, 铱星]
     * @param properties 物品属性
     */
    public CrabPotCatchItem(int[] priceByQuality, Item.Properties properties) {
        super(properties);
        this.priceByQuality = priceByQuality;
    }

    @SuppressWarnings("null")
    @Override
    public Component getName(@SuppressWarnings("null") ItemStack stack) {
        int quality = QualityHelper.getQuality(stack);
        Component prefix = QualityHelper.getQualityPrefix(quality);
        @SuppressWarnings("null")
        Component baseName = Component.translatable(this.getDescriptionId(stack))
                .withStyle(ChatFormatting.WHITE);

        // 设置CustomModelData以便于材质变体
        @SuppressWarnings("null")
        var customData = stack.getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT);
        if (quality != QualityHelper.NORMAL && customData.equals(com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT)) {
            stack.set(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(quality));
        }

        if (quality == QualityHelper.NORMAL) {
            return baseName;
        } else {
            return Component.empty().append(prefix).append(baseName);
        }
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.crabpot";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        int quality = QualityHelper.getQuality(stack);
        if (quality >= 0 && quality < priceByQuality.length) {
            return priceByQuality[quality];
        }
        return priceByQuality[0];
    }

    @Override
    public boolean isFood() {
        return false; // 蟹笼物品不能直接食用，用于烹饪
    }
}
