package com.stardew.craft.item;

import com.stardew.craft.block.nature.TeaBushBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/** Places SDV's tea sapling directly as a persistent tea bush. */
public final class TeaSaplingItem extends StardewBlockItem {
    private final Supplier<? extends Block> bush;

    public TeaSaplingItem(Supplier<? extends Block> bush, Properties properties) {
        super(bush.get(), "stardewcraft.type.seed", 250, properties);
        this.bush = bush;
    }

    @Override
    public String getDescriptionId() {
        return "item.stardewcraft.tea_sapling";
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockPos placePos = level.getBlockState(clicked).canBeReplaced()
                ? clicked
                : clicked.relative(context.getClickedFace());

        if (!TeaBushBlock.canPlantAt(level, placePos)) {
            return InteractionResult.PASS;
        }
        if (context.getPlayer() instanceof ServerPlayer player
                && level instanceof ServerLevel serverLevel
                && !TeaBushBlock.canModify(player, serverLevel, placePos)) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            level.setBlock(placePos, bush.get().defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, placePos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.9F, 1.0F);
            if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
                ItemStack stack = context.getItemInHand();
                stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
