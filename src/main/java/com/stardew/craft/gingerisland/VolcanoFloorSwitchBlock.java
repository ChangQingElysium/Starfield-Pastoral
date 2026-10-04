package com.stardew.craft.gingerisland;

import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** DwarfSwitch is latched by a player stepping on it; leaving does not release it. */
public final class VolcanoFloorSwitchBlock extends Block {
    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape RAISED = Block.box(1, 0, 1, 15, 4, 15);
    private static final VoxelShape DOWN = Block.box(1, 0, 1, 15, 2, 15);

    public VolcanoFloorSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PRESSED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PRESSED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getPlayer() instanceof ServerPlayer player && !IslandContext.canModifyAt(player, context.getClickedPos()))
            return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        // BlockItem reapplies BLOCK_STATE after getStateForPlacement. A held
        // component cannot latch a player-operated switch before anyone steps on it.
        BlockState placed = state.setValue(PRESSED, false);
        if (placed != state) level.setBlock(pos, placed, Block.UPDATE_CLIENTS);
        super.setPlacedBy(level, pos, placed, placer, stack);
    }

    @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || player.isSpectator()
                || !player.isAlive() || !player.onGround() || !IslandContext.canModifyAt(player, pos)) return;
        var current = level.getBlockState(pos);
        if (!current.is(this) || current.getValue(PRESSED)) return;
        level.setBlock(pos, current.setValue(PRESSED, true), Block.UPDATE_ALL);
        level.playSound(null, pos, ModSounds.OPENBOX.get(), SoundSource.BLOCKS, 1F, 1F);
        level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, pos);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PRESSED) ? DOWN : RAISED;
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
