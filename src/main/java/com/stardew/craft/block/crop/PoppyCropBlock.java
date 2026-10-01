package com.stardew.craft.block.crop;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.function.Supplier;

/**
 * 虞美人花作物
 */
public class PoppyCropBlock extends BlueJazzCropBlock {

    private static final int[] PHASE_DAYS = new int[]{1, 2, 2, 2};
    private static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 2);
    private static final int COLOR_COUNT = 3;

    @SuppressWarnings("null")
	public PoppyCropBlock() {
        super();
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.POPPY_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.POPPY;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        return seasonForGrowth() == 1;
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        @SuppressWarnings("null")
		ItemStack stack = new ItemStack(ModItems.POPPY.get());
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
			var customData = PortItemData.getOrDefault(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    com.stardew.craft.port.net.minecraft.world.item.component.CustomData.EMPTY);
            var tag = customData.copyTag();
            tag.putInt("FlowerColor", color);
            PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    com.stardew.craft.port.net.minecraft.world.item.component.CustomData.of(tag));
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
        return "item.stardewcraft.poppy";
    }

    @Override
    protected void addExtraProperties(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(COLOR);
        builder.add(PLACED_BY_PLAYER);
        builder.add(HALF);
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
