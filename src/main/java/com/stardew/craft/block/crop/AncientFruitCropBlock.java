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
 * 上古水果作物
 */
public class AncientFruitCropBlock extends StardewCropBlock {

    private static final int[] PHASE_DAYS = new int[]{2, 9, 9, 8}; // SDV: 28 days
    private static final int[] OUTLINE_HEIGHTS = new int[]{5, 8, 16, 16};
    private static final float INV_SQRT_2 = 0.70710677f;

    @SuppressWarnings("null")
    public AncientFruitCropBlock() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP));
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.ANCIENT_FRUIT_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.ANCIENT_FRUIT;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        StardewTimeManager timeManager = StardewTimeManager.get();
        return timeManager.getCurrentSeason() == 0 || timeManager.getCurrentSeason() == 1 || timeManager.getCurrentSeason() == 2;
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
        return new int[]{
                toOutlineWidth(4),
                toOutlineWidth(6),
                toOutlineWidth(11),
                toOutlineWidth(11)
        };
    }

    private static int toOutlineWidth(int textureWidthPx) {
        int width = Math.round(textureWidthPx * INV_SQRT_2);
        if (width < 0) {
            return 0;
        }
        return Math.min(16, width);
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        @SuppressWarnings("null")
        ItemStack stack = new ItemStack(ModItems.ANCIENT_FRUIT.get());
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
        return 7;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.ancient_fruit";
    }
}
