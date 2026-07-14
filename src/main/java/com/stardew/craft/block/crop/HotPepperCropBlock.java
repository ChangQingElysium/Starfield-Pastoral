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
 * 辣椒作物
 */
public class HotPepperCropBlock extends StardewCropBlock {

    private static final int[] PHASE_DAYS = new int[]{1, 1, 1, 2}; // SDV: 5 days

    @SuppressWarnings("null")
    public HotPepperCropBlock() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP));
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.HOT_PEPPER_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.HOT_PEPPER;
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
        ItemStack stack = new ItemStack(ModItems.HOT_PEPPER.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
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
        return 3;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.hot_pepper";
    }
}
