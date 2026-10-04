package com.stardew.craft.item;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.farming.SeasonLocationRules;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stardew Wild Seeds — season-specific forage seeds.
 * SDV IDs: Spring(495), Summer(496), Fall(497), Winter(498).
 *
 * When planted, grows through crop phases (7 days), then transforms
 * into a random seasonal forage block on maturity.
 */
public class WildSeedsItem extends Item implements IStardewItem {

    private final int season;    // 0=spring, 1=summer, 2=fall, 3=winter
    private final int sellPrice; // SDV sell price

    /**
     * @param season    0=spring, 1=summer, 2=fall, 3=winter
     * @param sellPrice SDV sell price
     */
    public WildSeedsItem(int season, int sellPrice, Item.Properties properties) {
        super(properties);
        this.season = season;
        this.sellPrice = sellPrice;
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.seed";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return sellPrice;
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(pos);

        if (!isFarmland(clickedState)) return InteractionResult.PASS;

        BlockPos abovePos = pos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        if (!aboveState.isAir()) return InteractionResult.PASS;

        if (!level.isClientSide) {
            // SDV: wild seeds can only be planted in their own season
            int currentSeason = StardewTimeManager.get().getCurrentSeason();
            if (!SeasonLocationRules.isPlantingSeasonAllowed(level, abovePos, currentSeason, season)) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(
                            Component.translatable("stardewcraft.message.seed.wrong_season"), true);
                }
                return InteractionResult.FAIL;
            }

            // Place the wild seed crop block for this season
            Block cropBlock = getCropBlock();
            if (cropBlock == null) return InteractionResult.FAIL;

            if (!com.stardew.craft.farming.CropPlanting.place(level, abovePos, cropBlock.defaultBlockState())) {
                return InteractionResult.FAIL;
            }
            level.playSound(null, abovePos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Get the corresponding WildSeedCropBlock for this season.
     */
    private Block getCropBlock() {
        return switch (season) {
            case 0 -> ModBlocks.SPRING_WILD_SEED_CROP.get();
            case 1 -> ModBlocks.SUMMER_WILD_SEED_CROP.get();
            case 2 -> ModBlocks.FALL_WILD_SEED_CROP.get();
            case 3 -> ModBlocks.WINTER_WILD_SEED_CROP.get();
            default -> null;
        };
    }

    @SuppressWarnings("null")
    private static boolean isFarmland(BlockState state) {
        return com.stardew.craft.block.terrain.TerrainSoils.cropSupport(state);
    }
}
