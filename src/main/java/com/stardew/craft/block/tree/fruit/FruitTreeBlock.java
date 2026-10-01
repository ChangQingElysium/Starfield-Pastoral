package com.stardew.craft.block.tree.fruit;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.FruitTreeBlockEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.manager.FruitTreeGrowthManager;
import com.stardew.craft.tree.fruit.FruitTreeRules;
import com.stardew.craft.tree.fruit.FruitTreeType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.PortBlockInteraction;

public class FruitTreeBlock extends Block implements EntityBlock, PortBlockInteraction {
    private static final VoxelShape TRUNK_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
    private static boolean removingWholeTree;

    private final FruitTreeType type;

    public FruitTreeBlock(FruitTreeType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public FruitTreeType getType() {
        return type;
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return TRUNK_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return TRUNK_SHAPE;
    }

    @Override
    public boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        return FruitTreeRules.isValidGround(level.getBlockState(pos.below()));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new FruitTreeBlockEntity(pos, state);
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override
    public InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                                               @Nonnull Player player, @Nonnull BlockHitResult hit) {
        return useRootWithoutItem(state, level, pos, player, hit);
    }

    public InteractionResult useRootWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                                                @Nonnull Player player, @Nonnull BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof FruitTreeBlockEntity tree) {
            boolean harvested = tree.harvestFruit(player);
            return harvested ? InteractionResult.sidedSuccess(level.isClientSide()) : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            fellTree(level, pos, player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock()) && level instanceof ServerLevel serverLevel) {
            placeExtensions(serverLevel, pos);
            FruitTreeGrowthManager.get(serverLevel).addMatureTree(serverLevel, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            removeExtensions(serverLevel, pos);
            FruitTreeGrowthManager.get(serverLevel).removeMatureTree(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static void fellTree(Level level, BlockPos rootPos, Player player) {
        if (removingWholeTree) {
            return;
        }
        BlockState rootState = level.getBlockState(rootPos);
        if (!(rootState.getBlock() instanceof FruitTreeBlock)) {
            return;
        }

        removingWholeTree = true;
        try {
            if (!player.isCreative()) {
                if (level.getBlockEntity(rootPos) instanceof FruitTreeBlockEntity tree) {
                    tree.dropStoredFruit(level, rootPos);
                }
                popResource(level, rootPos, new ItemStack(ModItems.WOOD_NORMAL.get(), 12));
            }
            if (level instanceof ServerLevel serverLevel) {
                removeExtensions(serverLevel, rootPos);
            }
            level.setBlock(rootPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.levelEvent(null, 2001, rootPos, Block.getId(rootState));
        } finally {
            removingWholeTree = false;
        }
    }

    public static BlockPos findRoot(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FruitTreeBlock) {
            return pos;
        }
        if (!(state.getBlock() instanceof FruitTreeExtensionBlock)) {
            return null;
        }
        if (state.getBlock() instanceof FruitTreeExtensionBlock extensionBlock) {
            return findRoot(level, pos, extensionBlock.getType());
        }
        return null;
    }

    private static BlockPos findRoot(LevelReader level, BlockPos pos, FruitTreeType type) {
        for (BlockPos offset : extensionOffsets(type)) {
            BlockPos candidate = pos.subtract(offset);
            if (level.getBlockState(candidate).getBlock() instanceof FruitTreeBlock fruitTreeBlock
                    && fruitTreeBlock.getType() == type) {
                return candidate;
            }
        }
        return null;
    }

    public static VoxelShape extensionSelectionShape(LevelReader level, BlockPos pos) {
        BlockPos root = findRoot(level, pos);
        if (root == null) {
            return Shapes.empty();
        }
        BlockPos rel = pos.subtract(root);
        FruitTreeType type = getTreeType(level, root);
        if (type != null && isTrunkExtensionOffset(rel, type)) {
            return TRUNK_SHAPE;
        }
        return Shapes.empty();
    }

    public static VoxelShape extensionCollisionShape(LevelReader level, BlockPos pos) {
        BlockPos root = findRoot(level, pos);
        if (root == null) {
            return Shapes.empty();
        }
        BlockPos rel = pos.subtract(root);
        FruitTreeType type = getTreeType(level, root);
        if (type != null && isTrunkExtensionOffset(rel, type)) {
            return TRUNK_SHAPE;
        }
        return Shapes.empty();
    }

    private static void placeExtensions(ServerLevel level, BlockPos rootPos) {
        FruitTreeType type = getTreeType(level, rootPos);
        if (type == null) {
            return;
        }
        BlockState extension = type.extensionBlock().defaultBlockState();
        for (BlockPos offset : extensionOffsets(type)) {
            BlockPos pos = rootPos.offset(offset);
            BlockState current = level.getBlockState(pos);
            if (current.canBeReplaced()) {
                level.setBlock(pos, extension, Block.UPDATE_ALL);
            }
        }
    }

    public static void ensureExtensions(ServerLevel level, BlockPos rootPos) {
        placeExtensions(level, rootPos);
    }

    private static void removeExtensions(ServerLevel level, BlockPos rootPos) {
        FruitTreeType type = getTreeType(level, rootPos);
        if (type == null) {
            return;
        }
        for (BlockPos offset : extensionOffsets(type)) {
            BlockPos pos = rootPos.offset(offset);
            if (level.getBlockState(pos).getBlock() instanceof FruitTreeExtensionBlock extensionBlock
                    && extensionBlock.getType() == type) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    public static List<BlockPos> extensionOffsets(FruitTreeType type) {
        List<BlockPos> offsets = new ArrayList<>();
        for (int y = 1; y <= type.trunkTopY(); y++) {
            offsets.add(new BlockPos(0, y, 0));
        }
        return List.copyOf(offsets);
    }

    private static boolean isTrunkExtensionOffset(BlockPos offset, FruitTreeType type) {
        return offset.getX() == 0
                && offset.getZ() == 0
                && offset.getY() >= 1
                && offset.getY() <= type.trunkTopY();
    }

    @Nullable
    private static FruitTreeType getTreeType(LevelReader level, BlockPos rootPos) {
        if (level.getBlockState(rootPos).getBlock() instanceof FruitTreeBlock fruitTreeBlock) {
            return fruitTreeBlock.getType();
        }
        return null;
    }

}
