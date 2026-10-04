package com.stardew.craft.item.crop.spring;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.RiceCropBlock;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class RiceShootItem extends Item implements IStardewItem {
    public RiceShootItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.seed";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 20;
    }

    protected Block getCropBlock() {
        return ModBlocks.RICE_CROP.get();
    }

    protected int getPlantingSeason() {
        return 0;
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockPos farmPos = clickedPos;
        BlockState farmState = level.getBlockState(farmPos);
        if (!isFarmland(farmState)) {
            return InteractionResult.PASS;
        }

        BlockPos cropPos = farmPos.above();
        BlockState cropSpace = level.getBlockState(cropPos);
        if (!cropSpace.isAir()) {
            return InteractionResult.PASS;
        }

        BlockPos upperPos = cropPos.above();
        BlockState upperSpace = level.getBlockState(upperPos);
        if (!upperSpace.isAir()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            int season = StardewTimeManager.get().getCurrentSeason();
            if (!com.stardew.craft.farming.SeasonLocationRules.isPlantingSeasonAllowed(level, cropPos, season, getPlantingSeason())) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable("stardewcraft.message.seed.wrong_season"), true);
                }
                return InteractionResult.FAIL;
            }

            BlockState planted = getCropBlock().defaultBlockState()
                    .setValue(RiceCropBlock.HALF, DoubleBlockHalf.LOWER)
                    .setValue(RiceCropBlock.WATERLOGGED, false);
            if (!com.stardew.craft.farming.CropPlanting.place(level, cropPos, planted)) {
                return InteractionResult.FAIL;
            }
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                RiceCropBlock.keepPaddySoilWatered(serverLevel, cropPos);
            }
            level.playSound(null, cropPos,
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
}
