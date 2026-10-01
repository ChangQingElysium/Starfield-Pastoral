package com.stardew.craft.block.mine;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import com.stardew.craft.port.PortBlockInteraction;

/** Four-high mine return ladder. All sections retain the existing exit interaction. */
@SuppressWarnings("null")
public class MineExitBlock extends Block implements PortBlockInteraction {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<MineLadderBlock.Theme> THEME = EnumProperty.create("theme", MineLadderBlock.Theme.class);
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 3);
    private static final VoxelShape[][] COLLISIONS = new VoxelShape[4][];
    private static final VoxelShape[] OUTLINES = new VoxelShape[4];

    static {
        for (int tier = 0; tier < 4; tier++) {
            COLLISIONS[tier] = ModelVoxelShapeCache.horizontalShapes(
                    "stardewcraft:block/mine_exit_ladder/" + tier, Direction.SOUTH);
        }
        for (int direction = 0; direction < 4; direction++) {
            VoxelShape shape = Shapes.empty();
            for (int tier = 0; tier < 4; tier++) shape = Shapes.or(shape, COLLISIONS[tier][direction].move(0, tier, 0));
            OUTLINES[direction] = shape.optimize();
        }
    }

    public MineExitBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(TIER, 0).setValue(THEME, MineLadderBlock.Theme.EARTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER, THEME);
    }

    @Nullable
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (pos.getY() + 3 >= level.getMaxBuildHeight()) return null;
        Direction facing = context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        for (int tier = 0; tier < 4; tier++) {
            BlockPos cell = pos.above(tier), wall = cell.relative(facing.getOpposite());
            if (tier > 0 && !level.getBlockState(cell).canBeReplaced(context)) return null;
            if (!level.getBlockState(wall).isFaceSturdy(level, wall, facing)) return null;
        }
        var theme = PortItemData.getOrDefault(context.getItemInHand(), com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_STATE,
                com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties.EMPTY).get(THEME);
        if (theme == null) {
            theme = MineLadderBlock.Theme.EARTH;
            var wall = level.getBlockState(pos.relative(facing.getOpposite()));
            for (var family : MineBuildingTheme.values()) if (family.rank(wall) >= 0) theme = MineLadderBlock.Theme.valueOf(family.name());
        }
        return defaultBlockState().setValue(FACING, facing).setValue(THEME, theme);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) placeExtensions(level, pos, state);
    }

    /** Also used by authored mine generation; never overwrites an occupied upper cell. */
    public void placeExtensions(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(TIER) != 0 || pos.getY() + 3 >= level.getMaxBuildHeight()) return;
        for (int tier = 1; tier < 4; tier++) {
            BlockState other = level.getBlockState(pos.above(tier));
            if (!other.canBeReplaced() && other != state.setValue(TIER, tier)) return;
        }
        for (int tier = 1; tier < 4; tier++) level.setBlock(pos.above(tier), state.setValue(TIER, tier), 3);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!replacement.is(this) && !level.isClientSide) {
            BlockPos root = pos.below(state.getValue(TIER));
            for (int tier = 0; tier < 4; tier++) {
                BlockPos cell = root.above(tier);
                BlockState other = level.getBlockState(cell);
                if (!cell.equals(pos) && other.is(this) && other.getValue(TIER) == tier
                        && other.getValue(FACING) == state.getValue(FACING)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                                 boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        return player.isCreative() && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))].move(0, -state.getValue(TIER), 0);
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISIONS[state.getValue(TIER)][ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
    }

    @Override public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_STATE,
                com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties.EMPTY.with(THEME, state));
        return stack;
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    /**
     * 右键交互 — 映射自 SDV MineShaft.checkAction case 115 (梯子)：
     * createQuestionDialogue(" ", { "Leave", "Do nothing" }, "ExitMine");
     */
    @SuppressWarnings("null")
    @Override
    public InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, 
                                               @SuppressWarnings("null")    Player player, @SuppressWarnings("null")    BlockHitResult hitResult) {
        if (level.dimension() != com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING) {
            return InteractionResult.FAIL;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            com.stardew.craft.mining.MiningPlayerData playerData = 
                com.stardew.craft.mining.MiningDataManager.getPlayerData(serverPlayer);
            int currentFloor = playerData != null ? playerData.getCurrentFloor() : 0;
            com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                serverPlayer,
                new com.stardew.craft.network.payload.OpenMineExitDialogPayload(currentFloor)
            );
        }

        return InteractionResult.CONSUME;
    }
}
