package com.stardew.craft.item.crop.other;

    import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 咖啡豆（既是作物也是种子）
 */
@SuppressWarnings("unused")
public class CoffeeBeanItem extends Item implements IStardewItem {

    private static final int[] SELL_PRICE_BY_QUALITY = new int[]{15, 18, 22, 30};
    private static final int[] ENERGY_BY_QUALITY = new int[]{0, 0, 0, 0};
    private static final int[] HEALTH_BY_QUALITY = new int[]{0, 0, 0, 0};


    public CoffeeBeanItem(Item.Properties properties) {
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
        return "stardewcraft.type.crop_seed"; // 咖啡豆既是作物也是种子
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return getSellPrice(QualityHelper.getQuality(stack));
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@SuppressWarnings("null") UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        @SuppressWarnings("null")
        BlockState clickedState = level.getBlockState(pos);

        if (!isFarmland(clickedState)) {
            return InteractionResult.PASS;
        }

        BlockPos abovePos = pos.above();
        @SuppressWarnings("null")
        BlockState aboveState = level.getBlockState(abovePos);
        if (!aboveState.isAir()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            int season = StardewTimeManager.get().getCurrentSeason();
            // 咖啡豆可以在春季(0)或夏季(1)种植
            if (!(season == 0 || season == 1)) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("stardewcraft.message.seed.wrong_season"),
                            true);
                }
                return InteractionResult.FAIL;
            }
        }

        if (!level.isClientSide) {
            level.setBlock(abovePos, ModBlocks.COFFEE_BEAN_CROP.get().defaultBlockState(), 3);
            level.playSound(null, abovePos,
                    net.minecraft.sounds.SoundEvents.HOE_TILL,
                    net.minecraft.sounds.SoundSource.BLOCKS,
                    1.0F, 1.0F);
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @SuppressWarnings("null")
    private boolean isFarmland(BlockState state) {
        return com.stardew.craft.block.terrain.TerrainSoils.cropSupport(state);
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
