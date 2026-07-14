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
 * 土豆作物
 */
public class PotatoCropBlock extends StardewCropBlock {

    private static final int[] PHASE_DAYS = new int[]{1, 1, 2, 2}; // SDV: 6 days
    private static final int[] OUTLINE_HEIGHTS = new int[]{7, 8, 10, 15};
    private static final int[] OUTLINE_WIDTHS = new int[]{6, 6, 8, 8};

    @SuppressWarnings("null")
    public PotatoCropBlock() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP));
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.POTATO_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.POTATO;
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
        ItemStack stack = new ItemStack(ModItems.POTATO.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @Override
    protected double getExtraHarvestChance() {
        // 原版土豆：基础 1 个，20% 概率额外 +1（可连触发，单次概率上限 0.9）。
        return 0.2;
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
        return "item.stardewcraft.potato";
    }
}
