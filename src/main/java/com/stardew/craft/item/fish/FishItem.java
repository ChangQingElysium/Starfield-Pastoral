package com.stardew.craft.item.fish;

import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 鱼类物品基类
 * 所有星露谷鱼类物品的通用实现
 * 
 * 格式与作物对齐：使用预设数组存储各品质的价格/能量/生命值
 * 
 * 品质索引: 0=普通, 1=银星, 2=金星, 3=铱星
 */
public class FishItem extends Item implements IStardewItem {

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        return context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? com.stardew.craft.item.cooking.PlacedFoodPlacement.place(context) : super.useOn(context);
    }


    /** 各品质售价 [普通, 银星, 金星, 铱星] */
    protected final int[] priceByQuality;
    /** 各品质能量恢复 [普通, 银星, 金星, 铱星] */
    protected final int[] energyByQuality;
    /** 各品质生命恢复 [普通, 银星, 金星, 铱星] */
    protected final int[] healthByQuality;
    /** 难度等级 (0-110) */
    protected final int difficulty;
    /** 行为类型 (mixed, dart, smooth, sinker, floater) */
    protected final String behavior;
    
    /**
     * 鱼类物品构造器（完整数组格式 - 与作物对齐）
     * @param priceByQuality 各品质售价数组 [普通, 银星, 金星, 铱星]
     * @param energyByQuality 各品质能量数组 [普通, 银星, 金星, 铱星]
     * @param healthByQuality 各品质生命数组 [普通, 银星, 金星, 铱星]
     * @param difficulty 难度等级 (0-110)
     * @param behavior 行为类型 (mixed, dart, smooth, sinker, floater)
     * @param properties 物品属性
     */
    @SuppressWarnings("null")
    public FishItem(int[] priceByQuality, int[] energyByQuality, int[] healthByQuality, 
                    int difficulty, String behavior, Item.Properties properties) {
        super(properties
                .food(new FoodProperties.Builder()
                        .nutrition(2)
                        .saturationModifier(0.3f)
                        .alwaysEdible()
                        .build())
        );
        this.priceByQuality = priceByQuality;
        this.energyByQuality = energyByQuality;
        this.healthByQuality = healthByQuality;
        this.difficulty = difficulty;
        this.behavior = behavior;
    }
    
    /**
     * 简化构造器 - 用于不需要指定难度和行为的情况
     */
    public FishItem(int[] priceByQuality, int[] energyByQuality, int[] healthByQuality, 
                    Item.Properties properties) {
        this(priceByQuality, energyByQuality, healthByQuality, 0, "mixed", properties);
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
        }

        return Component.empty().append(prefix).append(baseName);
    }

    @Override
    public ItemStack finishUsingItem(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") net.minecraft.world.level.Level level, @SuppressWarnings("null") net.minecraft.world.entity.LivingEntity livingEntity) {
        int quality = QualityHelper.getQuality(stack);
        int health = getHealthRestoration(quality);
        int energy = getEnergyRestoration(quality);

        @SuppressWarnings("null")
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);

        if (!level.isClientSide && livingEntity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // 处理生命恢复/伤害
            if (health != 0) {
                int currentSDHealth = com.stardew.craft.player.PlayerStardewDataAPI.getHealth(serverPlayer);
                int maxSDHealth = com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(serverPlayer);
                int newHealth = Math.max(0, Math.min(maxSDHealth, currentSDHealth + health));
                com.stardew.craft.player.PlayerStardewDataAPI.setHealth(serverPlayer, newHealth);
            }

            // 处理能量恢复/消耗
            if (energy != 0) {
                if (energy > 0) {
                    com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(serverPlayer, energy);
                } else {
                    // 负面能量：消耗能量
                    com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, -energy);
                }
            }
        }

        return result;
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.fish";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        int quality = QualityHelper.getQuality(stack);
        return getSellPrice(quality);
    }

    @Override
    public boolean isFood() {
        return true;
    }

    @Override
    public int getHealth(ItemStack stack) {
        return getHealthRestoration(QualityHelper.getQuality(stack));
    }

    @Override
    public int getEnergy(ItemStack stack) {
        return getEnergyRestoration(QualityHelper.getQuality(stack));
    }

    /**
     * 根据品质获取生命恢复值
     * 直接从预设数组中获取对应品质的值
     */
    public int getHealthRestoration(int quality) {
        if (quality < 0 || quality >= healthByQuality.length) {
            quality = QualityHelper.NORMAL;
        }
        return healthByQuality[quality];
    }

    /**
     * 根据品质获取能量恢复值
     * 直接从预设数组中获取对应品质的值
     */
    public int getEnergyRestoration(int quality) {
        if (quality < 0 || quality >= energyByQuality.length) {
            quality = QualityHelper.NORMAL;
        }
        return energyByQuality[quality];
    }

    /**
     * 根据品质获取售价
     * 直接从预设数组中获取对应品质的值
     */
    public int getSellPrice(int quality) {
        if (quality < 0 || quality >= priceByQuality.length) {
            quality = QualityHelper.NORMAL;
        }
        return priceByQuality[quality];
    }
    
    /**
     * 获取鱼类难度
     */
    public int getDifficulty() {
        return difficulty;
    }
    
    /**
     * 获取鱼类行为类型
     */
    public String getBehavior() {
        return behavior;
    }
    
    /**
     * 获取基础售价
     */
    public int getBasePrice() {
        return priceByQuality[QualityHelper.NORMAL];
    }
}
