package com.stardew.craft.item.crop.fall;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class SweetGemBerryItem extends Item implements IStardewItem {
    private static final int[] SELL_PRICE_BY_QUALITY = new int[]{3000, 3750, 4500, 6000};

    public SweetGemBerryItem(Properties properties) {
        super(properties);
    }

    @SuppressWarnings("null")
    @Override
    public Component getName(ItemStack stack) {
        int quality = QualityHelper.getQuality(stack);
        Component baseName = Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.WHITE);

        var customData = PortItemData.getOrDefault(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT);
        if (quality != QualityHelper.NORMAL && customData.equals(com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT)) {
            PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(quality));
        }

        if (quality == QualityHelper.NORMAL) {
            return baseName;
        }
        return Component.empty().append(QualityHelper.getQualityPrefix(quality)).append(baseName);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.crop";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        int quality = QualityHelper.getQuality(stack);
        if (quality < 0 || quality >= SELL_PRICE_BY_QUALITY.length) {
            quality = QualityHelper.NORMAL;
        }
        return SELL_PRICE_BY_QUALITY[quality];
    }
}
