package com.stardew.craft.item.crop.special;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.item.IStardewItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class FiberSeedsItem extends Item implements IStardewItem {
    public FiberSeedsItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.seed";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 5;
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos farmlandPos = context.getClickedPos();
        BlockState clicked = level.getBlockState(farmlandPos);
        if (!isFarmland(clicked)) {
            return InteractionResult.PASS;
        }

        BlockPos lowerPos = farmlandPos.above();
        BlockPos upperPos = lowerPos.above();
        if (!level.getBlockState(lowerPos).isAir() || !level.getBlockState(upperPos).isAir()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            if (!com.stardew.craft.farming.CropPlanting.place(level, lowerPos, ModBlocks.FIBER_CROP.get().defaultBlockState())) {
                return InteractionResult.FAIL;
            }
            level.playSound(null, lowerPos,
                net.minecraft.sounds.SoundEvents.HOE_TILL,
                net.minecraft.sounds.SoundSource.BLOCKS,
                1.0F, 1.0F);
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @SuppressWarnings("deprecation")
    private boolean isFarmland(BlockState state) {
        return com.stardew.craft.block.terrain.TerrainSoils.cropSupport(state);
    }
}
