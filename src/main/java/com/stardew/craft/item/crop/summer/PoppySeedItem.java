package com.stardew.craft.item.crop.summer;

import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Seed item. */
public class PoppySeedItem extends Item implements IStardewItem {

    public PoppySeedItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.seed";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 25;
    }

    @Override
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        Level level = Objects.requireNonNull(context.getLevel(), "level");
        BlockPos pos = Objects.requireNonNull(context.getClickedPos(), "pos");
        BlockState clickedState = level.getBlockState(pos);

        if (!isFarmland(clickedState)) {
            return InteractionResult.PASS;
        }

        BlockPos abovePos = Objects.requireNonNull(pos.above(), "abovePos");
        BlockState aboveState = level.getBlockState(abovePos);
        if (!aboveState.isAir() || !level.getBlockState(abovePos.above()).isAir()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            int season = StardewTimeManager.get().getCurrentSeason();
            if (!com.stardew.craft.farming.SeasonLocationRules.isPlantingSeasonAllowed(level, abovePos, season, 1)) {
                var player = context.getPlayer();
                if (player != null) {
                    Component message = Component.translatable("stardewcraft.message.seed.wrong_season");
                    player.displayClientMessage(Objects.requireNonNull(message, "message"), true);
                }
                return InteractionResult.FAIL;
            }
        }

        if (!level.isClientSide) {
                BlockState cropState = Objects.requireNonNull(ModBlocks.POPPY_CROP.get().defaultBlockState(), "cropState");
                SoundEvent hoeSound = Objects.requireNonNull(SoundEvents.HOE_TILL, "hoeSound");
                if (!com.stardew.craft.farming.CropPlanting.place(level, abovePos, cropState)) {
                    return InteractionResult.FAIL;
                }
                level.playSound(null, abovePos,
                    hoeSound,
                    net.minecraft.sounds.SoundSource.BLOCKS,
                    1.0F, 1.0F);
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private boolean isFarmland(BlockState state) {
        return com.stardew.craft.block.terrain.TerrainSoils.cropSupport(state);
    }
}

