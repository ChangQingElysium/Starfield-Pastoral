package com.stardew.craft.item.crop.other;

    import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 小麦
 */
public class WheatItem extends Item implements IStardewItem {

    private static final int[] SELL_PRICE_BY_QUALITY = new int[]{25, 31, 37, 50};
    private static final int[] ENERGY_BY_QUALITY = new int[]{0, 0, 0, 0};
    private static final int[] HEALTH_BY_QUALITY = new int[]{0, 0, 0, 0};


    public WheatItem(Item.Properties properties) {
        super(properties);
    }


    @SuppressWarnings("null")
    @Override
    public Component getName(@SuppressWarnings("null") ItemStack stack) {
        int quality = QualityHelper.getQuality(stack);
        Component prefix = QualityHelper.getQualityPrefix(quality);
        @SuppressWarnings("null")
        Component baseName = Component.translatable(this.getDescriptionId(stack))
                .withStyle(ChatFormatting.WHITE);

        @SuppressWarnings("null")
        var customData = stack.getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT);
        if (quality != QualityHelper.NORMAL && customData.equals(com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT)) {
            stack.set(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(quality));
        }

        if (quality == QualityHelper.NORMAL) {
            return baseName;
        }

        return Component.empty().append(prefix).append(baseName);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.crop";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return getSellPrice(QualityHelper.getQuality(stack));
    }

    @Override
    public boolean isFood() {
        return false;
    }

    @Override
    public int getHealth(ItemStack stack) {
        return 0;
    }

    @Override
    public int getEnergy(ItemStack stack) {
        return 0;
    }

    public static int getHealthRestoration(int quality) {
        return HEALTH_BY_QUALITY[Math.max(0, Math.min(3, quality))];
    }

    public static int getEnergyRestoration(int quality) {
        return ENERGY_BY_QUALITY[Math.max(0, Math.min(3, quality))];
    }

    public static int getSellPrice(int quality) {
        return SELL_PRICE_BY_QUALITY[Math.max(0, Math.min(3, quality))];
    }
}
