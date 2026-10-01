package com.stardew.craft.block.mine;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Three door cells plus a clickable, noncolliding call panel on the adjacent wall. */
@SuppressWarnings("null")
public class ElevatorBlock extends MapDecorStaticBlock {
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 3);
    private static final VoxelShape BUTTON = Block.box(18, 23, -3, 23, 31, 0);
    private static final VoxelShape BODY = Shapes.or(
            ModelVoxelShapeCache.shapeFromModelId("stardewcraft:block/elevator"),
            ModelVoxelShapeCache.shapeFromModelId("stardewcraft:block/elevator_middle").move(0, 1, 0),
            ModelVoxelShapeCache.shapeFromModelId("stardewcraft:block/elevator_upper").move(0, 2, 0)).optimize();
    private static final VoxelShape[] COLLISIONS = new VoxelShape[4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            COLLISIONS[ModelVoxelShapeCache.horizontalIndex(facing)] = rotateShapeForFacing(BODY, facing);
        }
    }

    public ElevatorBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(SECTION) == 2 ? 12 : 0), "block/elevator");
        registerDefaultState(defaultBlockState().setValue(SECTION, 0).setValue(MineBuildingTheme.PROPERTY, MineBuildingTheme.EARTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SECTION, MineBuildingTheme.PROPERTY);
    }

    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(MineBuildingTheme.PROPERTY, MineBuildingTheme.forPlacement(context));
    }

    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return MineBuildingTheme.picked(this, state);
    }

    @Override
    protected VoxelShape canonicalShape() {
        return Shapes.or(BODY, BUTTON);
    }

    @Override
    protected BlockState extensionState(BlockState mainState, BlockPos offset) {
        boolean doorColumn = offset.getX() == 0 && offset.getZ() == 0;
        return super.extensionState(mainState, offset).setValue(SECTION, doorColumn ? offset.getY() : 3);
    }

    @Override
    protected CellOffset findOffsetForExtension(BlockGetter level, BlockPos pos, BlockState state) {
        int section = state.getValue(SECTION);
        Direction facing = state.getValue(FACING);
        for (CellOffset offset : occupiedOffsets(facing)) {
            boolean matches = section == 3
                    ? offset.dy() == 1 && (offset.dx() != 0 || offset.dz() != 0)
                    : section > 0 && offset.dy() == section && offset.dx() == 0 && offset.dz() == 0;
            if (!matches) continue;
            BlockState main = level.getBlockState(pos.offset(-offset.dx(), -offset.dy(), -offset.dz()));
            if (main.is(this) && main.getValue(PART) == Part.MAIN && main.getValue(FACING) == facing) return offset;
        }
        return null;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(SECTION) == 3) return Shapes.empty();
        BlockPos main = findMainPos(level, pos, state);
        if (main == null) return Shapes.empty();
        return COLLISIONS[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))]
                .move(main.getX() - pos.getX(), main.getY() - pos.getY(), main.getZ() - pos.getZ());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(this) && state.getValue(PART) == Part.MAIN) {
            // Commands and structure placement do not invoke BlockItem.setPlacedBy.
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                        net.minecraft.util.RandomSource random) {
        if (state.getValue(PART) == Part.MAIN) placeExtensions(level, pos, state);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getShape(state, level, pos, CollisionContext.empty());
    }

    /**
     * 禁止生存模式破坏电梯
     */
    @SuppressWarnings("null")
    @Override
    public boolean onDestroyedByPlayer(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, boolean willHarvest, @SuppressWarnings("null") net.minecraft.world.level.material.FluidState fluid) {
        // 只有创造模式才能破坏
        if (!player.isCreative()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.stardewcraft.cannot_break_elevator"), true);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /**
     * 右键交互 - 打开电梯GUI
     */
    @SuppressWarnings("null")
    @Override
    public InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos,
                                               @SuppressWarnings("null")    Player player, @SuppressWarnings("null")    BlockHitResult hitResult) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }
        if (level.dimension() != com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.elevator.mine_only"));
            return InteractionResult.FAIL;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (containerId, playerInventory, playerEntity) ->
                    new com.stardew.craft.menu.ElevatorMenu(containerId, playerInventory),
                net.minecraft.network.chat.Component.translatable("container.stardew_craft.elevator")
            ));
        }

        return InteractionResult.CONSUME;
    }
}
