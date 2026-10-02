package com.stardew.craft.block.utility;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.UtilityDropHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import com.stardew.craft.port.PortBlockInteraction;

public abstract class AbstractTwoBlockUtilityBlock<T extends BlockEntity> extends Block implements EntityBlock, PortBlockInteraction {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private final VoxelShape[] mainShapes;
    private final VoxelShape[] extensionShapes;

    public enum Part implements StringRepresentable {
        MAIN("main"),
        EXTENSION("extension");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @SuppressWarnings("null")
    protected AbstractTwoBlockUtilityBlock(Properties properties, String mainModelId, String extensionModelId, Direction shapeBaseFacing) {
        super(properties);
        this.mainShapes = ModelVoxelShapeCache.horizontalShapes(mainModelId, shapeBaseFacing);
        this.extensionShapes = ModelVoxelShapeCache.horizontalShapes(extensionModelId, shapeBaseFacing);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.SOUTH)
            .setValue(WORKING, false)
            .setValue(PART, Part.MAIN));
    }

    protected abstract BlockEntityType<T> blockEntityType();

    protected abstract T newMainBlockEntity(BlockPos pos, BlockState state);

    protected void serverTick(Level level, BlockPos pos, BlockState state, T blockEntity) {
    }

    protected void clientTick(Level level, BlockPos pos, BlockState state, T blockEntity) {
    }

    protected boolean tryHarvest(Level level, BlockPos pos, Player player, T blockEntity) {
        return false;
    }

    protected boolean tryInsert(ItemStack stack, Level level, BlockPos pos, Player player, T blockEntity) {
        return false;
    }

    @Nullable
    protected SoundEvent insertSound() {
        return null;
    }

    @Override
    protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WORKING, PART);
    }

    @Override
    public RenderShape getRenderShape(@SuppressWarnings("null") BlockState state) {
        return UtilityMachineRenderState.forWorkingState(
            state.getValue(WORKING), state.getValue(PART) == Part.EXTENSION);
    }

    @Override
    public List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LootParams.Builder params) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return List.of();
        }
        return List.of(new ItemStack(asItem()));
    }

    @Override
    public VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return getPartShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return getPartShape(state);
    }

    private VoxelShape getPartShape(BlockState state) {
        int index = ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING));
        return state.getValue(PART) == Part.EXTENSION ? extensionShapes[index] : mainShapes[index];
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return com.stardew.craft.port.PortMachineExtensions.createExtension(pos, state);
        }
        return newMainBlockEntity(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings({"unchecked", "null"})
    public <E extends BlockEntity> BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        if (state.getValue(PART) == Part.EXTENSION || type != blockEntityType()) {
            return null;
        }
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> clientTick(lvl, pos, st, (T) be);
        }
        return (lvl, pos, st, be) -> serverTick(lvl, pos, st, (T) be);
    }

    @Override
    public BlockState getStateForPlacement(@SuppressWarnings("null") BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos extensionPos = context.getClickedPos().above();
        if (!level.getWorldBorder().isWithinBounds(extensionPos) || !level.getBlockState(extensionPos).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection())
            .setValue(WORKING, false)
            .setValue(PART, Part.MAIN);
    }

    @Override
    public void setPlacedBy(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state, @Nullable LivingEntity placer, @SuppressWarnings("null") ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(PART) == Part.MAIN) {
            level.setBlock(pos.above(), state.setValue(PART, Part.EXTENSION), 3);
        }
    }

    @Override
    public BlockState rotate(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ItemInteractionResult useItemOn(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") InteractionHand hand, @SuppressWarnings("null") BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = getMainPos(pos, state);
            BlockState mainState = level.getBlockState(mainPos);
            if (!mainState.is(this)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            return useItemOn(stack, mainState, level, mainPos, player, hand, hit);
        }

        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be.getType() != blockEntityType()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        T blockEntity = (T) be;
        if (tryHarvest(level, pos, player, blockEntity)) {
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (!stack.isEmpty() && tryInsert(stack, level, pos, player, blockEntity)) {
            SoundEvent sound = insertSound();
            if (sound != null) {
                level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.7f, 0.9f);
            }
            return ItemInteractionResult.sidedSuccess(false);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    @SuppressWarnings("unchecked")
    public InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = getMainPos(pos, state);
            BlockState mainState = level.getBlockState(mainPos);
            if (!mainState.is(this)) {
                return InteractionResult.PASS;
            }
            return useWithoutItem(mainState, level, mainPos, player, hit);
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be.getType() != blockEntityType()) {
            return InteractionResult.PASS;
        }
        return tryHarvest(level, pos, player, (T) be) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public void onRemove(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!isMoving && state.getValue(PART) == Part.MAIN) {
                UtilityDropHelper.dropAutomationContents(level, pos);
            }
            BlockPos otherPos = state.getValue(PART) == Part.MAIN ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.getBlock() == this && otherState.getValue(PART) != state.getValue(PART)) {
                level.removeBlock(otherPos, false);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void playerWillDestroy(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Player player) {
        if (!level.isClientSide && state.getValue(PART) == Part.EXTENSION && !player.isCreative()) {
            popResource(level, pos, new ItemStack(asItem()));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState updateShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Direction direction, @SuppressWarnings("null") BlockState neighborState, @SuppressWarnings("null") LevelAccessor level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockPos neighborPos) {
        BlockPos otherPos = state.getValue(PART) == Part.MAIN ? pos.above() : pos.below();
        if (neighborPos.equals(otherPos) && !neighborState.is(this)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    public static BlockPos getMainPos(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.EXTENSION ? pos.below() : pos;
    }

    public static BlockPos getExtensionPos(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.EXTENSION ? pos : pos.above();
    }

    public static void updateWorkingState(Level level, BlockPos pos, BlockState state, boolean working) {
        BlockPos mainPos = getMainPos(pos, state);
        BlockState mainState = level.getBlockState(mainPos);
        if (!mainState.hasProperty(WORKING)) {
            return;
        }
        if (mainState.getValue(WORKING) != working) {
            level.setBlock(mainPos, mainState.setValue(WORKING, working), 3);
        }
        BlockPos extensionPos = getExtensionPos(mainPos, mainState);
        BlockState extensionState = level.getBlockState(extensionPos);
        if (extensionState.is(mainState.getBlock()) && extensionState.hasProperty(WORKING) && extensionState.getValue(WORKING) != working) {
            level.setBlock(extensionPos, extensionState.setValue(WORKING, working), 3);
        }
    }
}
