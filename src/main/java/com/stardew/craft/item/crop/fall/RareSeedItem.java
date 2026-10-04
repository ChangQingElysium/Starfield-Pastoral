package com.stardew.craft.item.crop.fall;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class RareSeedItem extends Item implements IStardewItem {
    public RareSeedItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.seed";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 200;
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
            int season = StardewTimeManager.get().getCurrentSeason();
            if (!com.stardew.craft.farming.SeasonLocationRules.isPlantingSeasonAllowed(level, lowerPos, season, 2)) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable("stardewcraft.message.seed.wrong_season"), true);
                }
                return InteractionResult.FAIL;
            }

            if (!com.stardew.craft.farming.CropPlanting.place(level, lowerPos, ModBlocks.SWEET_GEM_BERRY_CROP.get().defaultBlockState())) {
                return InteractionResult.FAIL;
            }
            level.playSound(null, lowerPos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @SuppressWarnings("deprecation")
    private boolean isFarmland(BlockState state) {
        return com.stardew.craft.block.terrain.TerrainSoils.cropSupport(state);
    }
}
