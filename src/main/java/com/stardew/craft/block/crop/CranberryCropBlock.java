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
 * 蔓越莓作物
 */
public class CranberryCropBlock extends StardewCropBlock {

    private static final int[] PHASE_DAYS = new int[]{1, 2, 2, 2}; // SDV: 7 days

    @SuppressWarnings("null")
    public CranberryCropBlock() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP));
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.CRANBERRY_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.CRANBERRY;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        StardewTimeManager timeManager = StardewTimeManager.get();
        return timeManager.getCurrentSeason() == 2;
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        @SuppressWarnings("null")
        ItemStack stack = new ItemStack(ModItems.CRANBERRY.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @Override
    protected int getHarvestMinStack() {
        return 2;
    }

    @Override
    protected int getHarvestMaxStack() {
        return 2;
    }

    @Override
    protected double getExtraHarvestChance() {
        return 0.1;
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
        return 5;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.cranberry";
    }
}
