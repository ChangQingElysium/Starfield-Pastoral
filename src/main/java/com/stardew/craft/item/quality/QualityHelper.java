package com.stardew.craft.item.quality;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 物品品质系统
 * 星露谷物语品质等级：普通(0)、银星(1)、金星(2)、铱星(3)
 */
public class QualityHelper {
    
    // 品质等级
    public static final int NORMAL = 0;
    public static final int SILVER = 1;
    public static final int GOLD = 2;
    public static final int IRIDIUM = 3;
    
    // NBT键名
    public static final String QUALITY_NBT_KEY = "Quality";
    
    /**
     * 获取物品的品质等级
     */
    @SuppressWarnings("null")
    public static int getQuality(ItemStack stack) {
        if (stack.isEmpty()) {
            return NORMAL;
        }
        return stack.getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA, 
                com.stardew.craft.port.net.minecraft.world.item.component.CustomData.EMPTY)
                .copyTag().getInt(QUALITY_NBT_KEY);
    }
    
    /**
     * 设置物品的品质等级
     */
    @SuppressWarnings("null")
    public static void setQuality(ItemStack stack, int quality) {
        if (stack.isEmpty()) {
            return;
        }
        @SuppressWarnings("null")
        var customData = stack.getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomData.EMPTY);
        var tag = customData.copyTag();
        tag.putInt(QUALITY_NBT_KEY, quality);
        stack.set(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA, 
                com.stardew.craft.port.net.minecraft.world.item.component.CustomData.of(tag));

        // 花卉颜色变体：使用 quality + color 组合的 custom_model_data
        if (tag.contains("FlowerColor")) {
            int color = Math.max(0, tag.getInt("FlowerColor"));
            int cmd = 100 + (quality * 10) + color;
            stack.set(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(cmd));
            return;
        }

        // 如果没有 FlowerColor，但 CMD 处于花卉颜色范围(100~199)，推断颜色并同步品质
        @SuppressWarnings("null")
        var existingCmd = stack.getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT);
        if (!existingCmd.equals(com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT)) {
            int raw = existingCmd.value();
            if (raw >= 100 && raw < 200) {
                int color = Math.max(0, raw % 10);
                int cmd = 100 + (quality * 10) + color;
                stack.set(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                        new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(cmd));
                return;
            }
        }

        // 视觉层（资源包/模型通常靠 custom_model_data 区分品质贴图）
        // 以前很多物品是在 getName() 里“懒设置”，导致刚获得/刚拾取时外观不稳定。
        @SuppressWarnings("null")
        var cmd = stack.getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT);
        if (quality == NORMAL) {
            // 仅当当前 CMD 看起来就是品质值时才回收，避免误伤其它系统的 CMD 用途。
            if (cmd.equals(new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(SILVER))
                    || cmd.equals(new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(GOLD))
                    || cmd.equals(new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(IRIDIUM))) {
                stack.remove(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            }
        } else {
            // 只在默认值时写入，避免覆盖其它来源的 CMD。
            if (cmd.equals(com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT)) {
                stack.set(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                        new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(quality));
            }
        }
    }
    
    /**
     * 创建带品质的物品
     */
    public static ItemStack createWithQuality(ItemStack stack, int quality) {
        ItemStack result = stack.copy();
        setQuality(result, quality);
        return result;
    }

    /**
     * 确保物品的视觉模型数据与品质/颜色一致。
     * - 普通品质：不强制写入 CMD（除非当前 CMD 看起来是品质值）
     * - 花卉颜色变体：使用 quality + color 组合的 CMD
     */
    @SuppressWarnings("null")
    public static void ensureQualityModelData(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        int quality = getQuality(stack);
        @SuppressWarnings("null")
        var customData = stack.getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomData.EMPTY);
        var tag = customData.copyTag();

        if (tag.contains("FlowerColor")) {
            int color = Math.max(0, tag.getInt("FlowerColor"));
            int cmd = 100 + (quality * 10) + color;
            stack.set(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(cmd));
            return;
        }

        @SuppressWarnings("null")
        var cmd = stack.getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT);

        if (quality == NORMAL) {
            if (cmd.equals(new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(SILVER))
                    || cmd.equals(new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(GOLD))
                    || cmd.equals(new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(IRIDIUM))) {
                stack.remove(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            }
        } else if (cmd.equals(com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT)
            || cmd.equals(new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(SILVER))
            || cmd.equals(new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(GOLD))
            || cmd.equals(new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(IRIDIUM))) {
            stack.set(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(quality));
        }
    }
    
    /**
     * 获取品质名称
     */
    public static Component getQualityName(int quality) {
        return switch (quality) {
            case SILVER -> Component.translatable("stardewcraft.quality.silver").withStyle(ChatFormatting.GRAY);
            case GOLD -> Component.translatable("stardewcraft.quality.gold").withStyle(ChatFormatting.GOLD);
            case IRIDIUM -> Component.translatable("stardewcraft.quality.iridium").withStyle(ChatFormatting.LIGHT_PURPLE);
            default -> Component.translatable("stardewcraft.quality.normal");
        };
    }
    
    /**
     * 获取品质前缀（用于显示名称）
     * 格式：(银星) / (金星)
     */
    @SuppressWarnings("null")
    public static Component getQualityPrefix(int quality) {
        if (quality == NORMAL) {
            return Component.empty();
        }
        
        Component starName = switch (quality) {
            case SILVER -> Component.translatable("stardewcraft.quality.silver").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
            case GOLD -> Component.translatable("stardewcraft.quality.gold").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            case IRIDIUM -> Component.translatable("stardewcraft.quality.iridium").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
            default -> Component.empty();
        };
        
        // 返回 “(银星) ”
        return Component.literal("(").withStyle(ChatFormatting.WHITE)
                .append(starName)
                .append(Component.literal(") ").withStyle(ChatFormatting.WHITE));
    }
    
    /**
     * 获取品质价格倍数
     */
    public static float getPriceMultiplier(int quality) {
        return switch (quality) {
            case SILVER -> 1.25f;
            case GOLD -> 1.5f;
            case IRIDIUM -> 2.0f;
            default -> 1.0f;
        };
    }
}
