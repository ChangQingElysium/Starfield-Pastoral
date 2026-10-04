package com.stardew.craft.item.crop.summer;

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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import javax.annotation.Nonnull;

/** Seed item. */
public class HotPepperSeedItem extends Item implements IStardewItem {

    public HotPepperSeedItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.seed";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 10;
    }

    @Override
    @SuppressWarnings("null")
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        @Nonnull Level level = context.getLevel();
        @Nonnull BlockPos pos = context.getClickedPos();
        @Nonnull BlockState clickedState = level.getBlockState(pos);

        if (!isFarmland(clickedState)) {
            return InteractionResult.PASS;
        }

        @Nonnull BlockPos abovePos = pos.above();
        @Nonnull BlockState aboveState = level.getBlockState(abovePos);
        if (!aboveState.isAir() || !level.getBlockState(abovePos.above()).isAir()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            int season = StardewTimeManager.get().getCurrentSeason();
            if (!com.stardew.craft.farming.SeasonLocationRules.isPlantingSeasonAllowed(level, abovePos, season, 1)) {
                var player = context.getPlayer();
                if (player != null) {
                    @Nonnull Component message = Component.translatable("stardewcraft.message.seed.wrong_season");
                    player.displayClientMessage(message, true);
                }
                return InteractionResult.FAIL;
            }
        }

        if (!level.isClientSide) {
            @Nonnull BlockState cropState = ModBlocks.HOT_PEPPER_CROP.get().defaultBlockState();
            if (!com.stardew.craft.farming.CropPlanting.place(level, abovePos, cropState)) {
                return InteractionResult.FAIL;
            }
            @Nonnull SoundEvent tillSound = net.minecraft.sounds.SoundEvents.HOE_TILL;
            level.playSound(null, abovePos,
                    tillSound,
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

