package com.stardew.craft.block.utility;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.WoodSignBlockEntity;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/** An SDV image sign, not a text editor or an inventory. */
@SuppressWarnings("null")
public final class WoodSignBlock extends MapDecorStaticBlock implements EntityBlock {
    private final boolean wall;

    public WoodSignBlock(Properties properties, boolean wall) {
        super(properties, "stardewcraft:block/utility/wood_sign_" + (wall ? "wall" : "standing"));
        this.wall = wall;
    }

    public boolean isWall() {
        return wall;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!wall) return super.getStateForPlacement(context);
        Direction facing = context.getClickedFace();
        if (!facing.getAxis().isHorizontal()) return null;
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(PART) == Part.EXTENSION) return super.canSurvive(state, level, pos);
        Direction support = wall ? state.getValue(FACING).getOpposite() : Direction.DOWN;
        BlockPos supportPos = pos.relative(support);
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, support.getOpposite());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new WoodSignBlockEntity(pos, state) : null;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.isEmpty() || !player.mayBuild()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null || !(level.getBlockEntity(mainPos) instanceof WoodSignBlockEntity sign)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            sign.setDisplayItem(stack);
            level.playSound(null, mainPos, ModSounds.COIN.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
