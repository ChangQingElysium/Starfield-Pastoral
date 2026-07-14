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
 * 甘蓝菜作物
 */
public class KaleCropBlock extends StardewCropBlock {

    private static final int[] PHASE_DAYS = new int[]{1, 2, 2, 1};

    @SuppressWarnings("null")
    public KaleCropBlock() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP));
    }

        private static final int[] OUTLINE_HEIGHTS = new int[]{5, 10, 12, 16};
        private static final int[] OUTLINE_WIDTHS = new int[]{6, 8, 11, 11};
    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.KALE_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.KALE;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        StardewTimeManager timeManager = StardewTimeManager.get();
        return timeManager.getCurrentSeason() == 0;
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
        ItemStack stack = new ItemStack(ModItems.KALE.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @Override
    protected HarvestMethod getHarvestMethod() {
        return HarvestMethod.SCYTHE;
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
        return "item.stardewcraft.kale";
    }
}
