package com.stardew.craft.block.crop;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.function.Supplier;

/**
 * 夏季亮片作物
 */
public class SummerSpangleCropBlock extends StardewCropBlock {

    private static final int[] PHASE_DAYS = new int[]{1, 2, 3, 2};
    private static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 5);
    private static final int COLOR_COUNT = 6;

    @SuppressWarnings("null")
    public SummerSpangleCropBlock() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP));
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.SUMMER_SPANGLE_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.SUMMER_SPANGLE;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        StardewTimeManager timeManager = StardewTimeManager.get();
        return timeManager.getCurrentSeason() == 1;
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        @SuppressWarnings("null")
        ItemStack stack = new ItemStack(ModItems.SUMMER_SPANGLE.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @SuppressWarnings("null")
    @Override
    protected ItemStack applyHarvestItemCustomization(ItemStack stack, BlockState state) {
        if (state.hasProperty(COLOR)) {
            @SuppressWarnings("null")
            int color = state.getValue(COLOR);
            @SuppressWarnings("null")
            var customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY);
            var tag = customData.copyTag();
            tag.putInt("FlowerColor", color);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
            setFlowerVariantModelData(stack, color);
        }
        return stack;
    }

    @Override
    protected boolean canRegrow() {
        return false;
    }

    @Override
    protected int getRegrowAge() {
        return 0;
    }

    @Override
    protected int getRegrowDays() {
        return 0;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.summer_spangle";
    }

    @Override
    protected void addExtraProperties(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(COLOR);
        builder.add(PLACED_BY_PLAYER);
    }

    @Override
    protected IntegerProperty getColorVariantProperty() {
        return COLOR;
    }

    @Override
    protected int getColorVariantCount() {
        return COLOR_COUNT;
    }
}
