package com.stardew.craft.block.mine;

import com.stardew.craft.event.MiningVanillaExperienceEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 直接采集矿物节点：右键可拾取，左键可正常破坏。
 */
public class MineralNodeBlock extends Block {
    public static final BooleanProperty PLACED_BY_PLAYER = BooleanProperty.create("placed_by_player");

    @SuppressWarnings("null")
    public MineralNodeBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(PLACED_BY_PLAYER, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PLACED_BY_PLAYER);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@Nullable BlockPlaceContext context) {
        return this.defaultBlockState().setValue(PLACED_BY_PLAYER, Boolean.TRUE);
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state,
                                               @Nonnull Level level,
                                               @Nonnull BlockPos pos,
                                               @Nonnull Player player,
                                               @Nonnull BlockHitResult hitResult) {
        return pickNode(state, level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack,
                                              @Nonnull BlockState state,
                                              @Nonnull Level level,
                                              @Nonnull BlockPos pos,
                                              @Nonnull Player player,
                                              @Nonnull InteractionHand hand,
                                              @Nonnull BlockHitResult hitResult) {
        pickNode(state, level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private InteractionResult pickNode(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            ItemStack stack = new ItemStack(Objects.requireNonNull(this.asItem()));
            if (!player.addItem(stack)) {
                popResource(level, pos, stack);
            }
            MiningVanillaExperienceEvents.awardNodePickupExperience(player, state);
            level.setBlock(pos, Objects.requireNonNull(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()), Block.UPDATE_ALL);
            level.playSound(null, pos, Objects.requireNonNull(SoundEvents.ITEM_PICKUP), SoundSource.PLAYERS, 0.2F, 1.0F);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
