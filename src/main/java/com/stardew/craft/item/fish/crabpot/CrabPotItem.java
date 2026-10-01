package com.stardew.craft.item.fish.crabpot;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 蟹笼【捕获物】物品基类（请注意：不是可放置的“蟹笼方块物品”）。
 * <p>
 * 可放置的蟹笼是 {@link com.stardew.craft.item.CrabPotItem}（负责“像桶/船那样放到水里”+“右键塞鱼饵”）。
 * 这个类仅用于蟹笼捕获出来的海洋生物/贝类等物品的通用行为（品质前缀、售价等）。
 * </p>
 *
 * <p>蟹笼捕获物的品质规则:</p>
 * <ul>
 *   <li>普通蟹笼: 只能获得普通品质</li>
 *   <li>使用高级鱼饵: 可以获得银星品质</li>
 *   <li>通过采集获得的贝类(蛤蜊、鸟蛤、贻贝、牡蛎): 可以获得金星和铱星品质</li>
 * </ul>
 */
public class CrabPotItem extends Item implements IStardewItem {

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        return context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? com.stardew.craft.item.cooking.PlacedFoodPlacement.place(context) : super.useOn(context);
    }


    /** 按品质存储的售价 [普通, 银星, 金星, 铱星] */
    protected final int[] priceByQuality;
    
    /**
     * 创建蟹笼物品
     * @param priceByQuality 按品质的售价数组 [普通, 银星, 金星, 铱星]
     * @param properties 物品属性
     */
    public CrabPotItem(int[] priceByQuality, Item.Properties properties) {
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
        var customData = PortItemData.getOrDefault(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT);
        if (quality != QualityHelper.NORMAL && customData.equals(com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT)) {
            PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
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
