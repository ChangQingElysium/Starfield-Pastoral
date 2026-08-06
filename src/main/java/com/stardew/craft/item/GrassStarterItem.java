package com.stardew.craft.item;

import com.stardew.craft.block.nature.PastureGrassBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

public class GrassStarterItem extends SimpleStardewItem {
    private final Supplier<? extends Block> grassBlock;

    public GrassStarterItem(Supplier<? extends Block> grassBlock, int sellPrice, Properties properties) {
        super("stardewcraft.type.seed", sellPrice, properties);
        this.grassBlock = grassBlock;
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@SuppressWarnings("null") UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockPos placePos = clickedPos;

        if (!level.getBlockState(placePos).canBeReplaced()) {
            placePos = clickedPos.relative(context.getClickedFace());
            if (!level.getBlockState(placePos).canBeReplaced()) {
                return InteractionResult.PASS;
            }
        }

        if (!(grassBlock.get() instanceof PastureGrassBlock pastureGrass)) {
            return InteractionResult.PASS;
        }

        BlockState grassState = pastureGrass.defaultBlockState().setValue(
                PastureGrassBlock.VARIANT,
                level.getRandom().nextInt(PastureGrassBlock.VISUAL_VARIANT_COUNT));
        if (!grassState.canSurvive(level, placePos)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            level.setBlock(placePos, grassState, 3);
            level.playSound(null, placePos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.9F, 1.0F);
            if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
                ItemStack stack = context.getItemInHand();
                stack.shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
