package com.stardew.craft.item.crop.spring;

    import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.FlowerPlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * 郁金香
 */
public class TulipItem extends Item implements IStardewItem {

    private static final int[] SELL_PRICE_BY_QUALITY = new int[]{30, 37, 45, 60};
    private static final int[] ENERGY_BY_QUALITY = new int[]{45, 63, 81, 117};
    private static final int[] HEALTH_BY_QUALITY = new int[]{20, 28, 36, 52};


    @SuppressWarnings("null")
    public TulipItem(Item.Properties properties) {
        super(properties
                .food(new FoodProperties.Builder()
                        .nutrition(2)
                        .saturationMod(0.3f)
                        .alwaysEat()
                        .build())
        );
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = FlowerPlacement.place(context, ModBlocks.TULIP_CROP);
        if (result != InteractionResult.PASS) {
            return result;
        }
        return super.useOn(context);
    }


    @SuppressWarnings("null")
    @Override
    public Component getName(@SuppressWarnings("null") ItemStack stack) {
        int quality = QualityHelper.getQuality(stack);
        Component prefix = QualityHelper.getQualityPrefix(quality);
        @SuppressWarnings("null")
        Component baseName = Component.translatable(this.getDescriptionId(stack))
                .withStyle(ChatFormatting.WHITE);

        QualityHelper.ensureQualityModelData(stack);

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
            if (health > 0) {
                int currentSDHealth = com.stardew.craft.player.PlayerStardewDataAPI.getHealth(serverPlayer);
                int maxSDHealth = com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(serverPlayer);
                com.stardew.craft.player.PlayerStardewDataAPI.setHealth(serverPlayer, Math.min(maxSDHealth, currentSDHealth + health));
            }

            if (energy > 0) {
                com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(serverPlayer, energy);
            }
        }

        return result;
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
