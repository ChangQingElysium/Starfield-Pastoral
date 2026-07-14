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

import java.util.function.Supplier;

/**
 * 咖啡豆作物
 */
public class CoffeeBeanCropBlock extends StardewCropBlock {

    private static final int[] PHASE_DAYS = new int[]{1, 3, 3, 3}; // SDV: 10 days
    private static final int[] OUTLINE_HEIGHTS = new int[]{4, 10, 15, 15};
    private static final int[] OUTLINE_WIDTHS = new int[]{6, 8, 8, 8};

    @SuppressWarnings("null")
    public CoffeeBeanCropBlock() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP));
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.COFFEE_BEAN; // 咖啡豆自己就是种子
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.COFFEE_BEAN;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        StardewTimeManager timeManager = StardewTimeManager.get();
        return timeManager.getCurrentSeason() == 0 || timeManager.getCurrentSeason() == 1;
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected int[] getOutlineHeightsPxByAge() {
        return OUTLINE_HEIGHTS;
    }

    @Override
    protected int[] getOutlineWidthsPxByAge() {
        return OUTLINE_WIDTHS;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        @SuppressWarnings("null")
        ItemStack stack = new ItemStack(ModItems.COFFEE_BEAN.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @Override
    protected int getHarvestMinStack() {
        return 4;
    }

    @Override
    protected int getHarvestMaxStack() {
        return 4;
    }

    @Override
    protected boolean canRegrow() {
        return true;
    }

    @Override
    protected int getRegrowAge() {
        return 2;
    }

    @Override
    protected int getRegrowDays() {
        return 2;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.coffee_bean";
    }
}
