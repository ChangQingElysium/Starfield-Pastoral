package com.stardew.craft.item.quality;

import com.stardew.craft.port.PortItemData;
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
        return PortItemData.getOrDefault(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA, 
                com.stardew.craft.port.net.minecraft.world.item.component.CustomData.EMPTY)
                .copyTag().getInt(QUALITY_NBT_KEY);
    }
    
    /**
     * 设置物品的品质等级
     */
    @SuppressWarnings("null")
    public static void setQuality(ItemStack stack, int quality) {
        if (stack.isEmpty()) return;
        quality = com.stardew.craft.port.PortJava.clamp(quality, NORMAL, IRIDIUM);
        // Seaweed has fixed value/food stats and no quality variants, including beach pickups.
        if (stack.getItem() instanceof com.stardew.craft.item.fish.misc.SeaweedItem) quality = NORMAL;
        var tag = PortItemData.getOrDefault(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        // Absence is the canonical representation of normal quality, including plain machine drops.
        if (quality == NORMAL) tag.remove(QUALITY_NBT_KEY);
        else tag.putInt(QUALITY_NBT_KEY, quality);
        writeCustomData(stack, tag);
        ensureQualityModelData(stack);
    }

    private static void writeCustomData(ItemStack stack, net.minecraft.nbt.CompoundTag tag) {
        if (tag.isEmpty()) PortItemData.remove(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        else PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomData.of(tag));
    }

    /** Repair legacy redundant quality fields on load/copy; never discard other item components. */
    public static void normalizeQualityComponents(ItemStack stack) {
        if (stack.isEmpty()) return;
        var data = PortItemData.get(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        var model = PortItemData.get(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        boolean seaweed = stack.getItem() instanceof com.stardew.craft.item.fish.misc.SeaweedItem;
        if (data == null && model == null || !seaweed && !usesQualityComponents(stack.getItem())) return;
        var tag = data == null ? new net.minecraft.nbt.CompoundTag() : data.copyTag();
        // Leave malformed/addon data untouched rather than guessing its meaning.
        if (tag.contains(QUALITY_NBT_KEY) && !tag.contains(QUALITY_NBT_KEY, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) return;
        int quality = getQuality(stack);
        if (quality < NORMAL || quality > IRIDIUM) return;
        if (seaweed) {
            setQuality(stack, NORMAL);
            return;
        }
        if (quality == NORMAL && tag.contains(QUALITY_NBT_KEY)) {
            tag.remove(QUALITY_NBT_KEY);
            writeCustomData(stack, tag);
        } else if (data != null && tag.isEmpty()) {
            PortItemData.remove(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        }
        ensureQualityModelData(stack);
    }

    private static boolean usesQualityComponents(net.minecraft.world.item.Item item) {
        if (item instanceof com.stardew.craft.item.StardewQualityItem quality) return quality.supportsQuality();
        if (item instanceof com.stardew.craft.item.artisan.ArtisanDrinkItem drink) return drink.supportsQuality();
        if (item instanceof com.stardew.craft.item.artisan.SmokedFishItem) return true;
        if (!(item instanceof com.stardew.craft.item.IStardewItem stardew)) return false;
        return switch (stardew.getItemTypeKey()) {
            case "stardewcraft.type.crop", "stardewcraft.type.crop_seed", "stardewcraft.type.fruit",
                    "stardewcraft.type.forage", "stardewcraft.type.fish", "stardewcraft.type.crabpot",
                    "stardewcraft.type.legendary_fish", "stardewcraft.type.animal_product",
                    "stardewcraft.type.artisan_animal_quality", "stardewcraft.type.artifact_quality" -> true;
            default -> false;
        };
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
        if (stack.isEmpty()) return;
        int quality = getQuality(stack);
        var tag = PortItemData.getOrDefault(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        var model = PortItemData.get(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        int raw = model == null ? 0 : model.value();
        if (tag.contains("FlowerColor") || raw >= 100 && raw < 200) {
            int color = tag.contains("FlowerColor") ? Math.max(0, tag.getInt("FlowerColor")) : raw % 10;
            PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(100 + quality * 10 + color));
        } else if (raw >= NORMAL && raw <= IRIDIUM) {
            if (quality == NORMAL) PortItemData.remove(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            else PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(quality));
        }
        // Values outside the established quality/flower ranges belong to other presentation systems.
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
