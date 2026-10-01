package com.stardew.craft.block.tree.fruit;

import com.stardew.craft.tree.fruit.FruitTreeType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.PortBlockInteraction;

public class FruitTreeExtensionBlock extends Block implements PortBlockInteraction {
    private final FruitTreeType type;

    public FruitTreeExtensionBlock(FruitTreeType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public FruitTreeType getType() {
        return type;
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos,
                               @Nonnull CollisionContext context) {
        if (level instanceof LevelReader reader) {
            return FruitTreeBlock.extensionSelectionShape(reader, pos);
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos,
                                        @Nonnull CollisionContext context) {
        if (level instanceof LevelReader reader) {
            return FruitTreeBlock.extensionCollisionShape(reader, pos);
        }
        return Shapes.empty();
    }

    @Override
    public float getDestroyProgress(@Nonnull BlockState state, @Nonnull Player player, @Nonnull BlockGetter level,
                                    @Nonnull BlockPos pos) {
        if (level instanceof LevelReader reader) {
            BlockPos root = FruitTreeBlock.findRoot(reader, pos);
            if (root != null) {
                return reader.getBlockState(root).getDestroyProgress(player, level, root);
            }
        }
        return super.getDestroyProgress(state, player, level, pos);
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
        BlockPos root = FruitTreeBlock.findRoot(level, pos);
        if (root == null) {
            return InteractionResult.PASS;
        }
        BlockState rootState = level.getBlockState(root);
        if (rootState.getBlock() instanceof FruitTreeBlock fruitTreeBlock) {
            return fruitTreeBlock.useRootWithoutItem(rootState, level, root, player, hit);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos root = FruitTreeBlock.findRoot(level, pos);
            if (root != null) {
                FruitTreeBlock.fellTree(level, root, player);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(@Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull BlockState state) {
        // PORT(1.20.1): the clone hook takes a BlockGetter; pick-block always passes the (LevelReader) level.
        BlockPos root = level instanceof net.minecraft.world.level.LevelReader reader ? FruitTreeBlock.findRoot(reader, pos) : null;
        if (root != null) {
            return level.getBlockState(root).getBlock().asItem().getDefaultInstance();
        }
        return ItemStack.EMPTY;
    }
}
