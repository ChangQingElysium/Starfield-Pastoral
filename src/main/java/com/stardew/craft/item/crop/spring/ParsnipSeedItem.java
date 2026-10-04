package com.stardew.craft.item.crop.spring;

import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Seed item. */
public class ParsnipSeedItem extends Item implements IStardewItem {

    public ParsnipSeedItem(Item.Properties properties) {
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
    public InteractionResult useOn(@SuppressWarnings("null") UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        @SuppressWarnings("null")
        BlockState clickedState = level.getBlockState(pos);

        if (!isFarmland(clickedState)) {
            return InteractionResult.PASS;
        }

        BlockPos abovePos = pos.above();
        @SuppressWarnings("null")
        BlockState aboveState = level.getBlockState(abovePos);
        if (!aboveState.isAir()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            int season = StardewTimeManager.get().getCurrentSeason();
            if (!com.stardew.craft.farming.SeasonLocationRules.isPlantingSeasonAllowed(level, abovePos, season, 0)) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("stardewcraft.message.seed.wrong_season"),
                            true);
                }
                return InteractionResult.FAIL;
            }
        }

        if (!level.isClientSide) {
            if (!com.stardew.craft.farming.CropPlanting.place(level, abovePos, ModBlocks.PARSNIP_CROP.get().defaultBlockState())) {
                return InteractionResult.FAIL;
            }
            level.playSound(null, abovePos,
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

