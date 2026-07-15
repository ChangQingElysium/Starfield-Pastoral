package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Map;

/**
 * Multi-cell decoration backed by one world-space AABB.
 *
 * <p>Every occupied cell returns the same world-space collision and outline,
 * shifted into that cell's local coordinates. The shapes overlap exactly in
 * world space, so the decoration remains one AABB while collision queries from
 * any extension cell still find it.</p>
 */
@SuppressWarnings("null")
public class IntegratedAabbDecorBlock extends MapDecorStaticBlock {
    private final Map<Direction, VoxelShape> wholeShapes = new EnumMap<>(Direction.class);

    public IntegratedAabbDecorBlock(Properties properties, String modelId,
                                    double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ) {
        super(properties, modelId, minX, minY, minZ, maxX, maxY, maxZ);
        wholeShapes.putAll(createWholeShapes(minX, minY, minZ, maxX, maxY, maxZ));
    }

    static Map<Direction, VoxelShape> createWholeShapes(double minX, double minY, double minZ,
                                                        double maxX, double maxY, double maxZ) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        VoxelShape northShape = Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            shapes.put(facing, rotateShapeForFacing(northShape, facing));
        }
        return shapes;
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return wholeShapeForCell(state, level, pos);
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                                        @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return wholeShapeForCell(state, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
                                               @Nonnull BlockPos pos, @Nonnull Player player,
                                               @Nonnull BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos == null) {
                return InteractionResult.PASS;
            }
            BlockHitResult mainHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), mainPos, hit.isInside());
            return useWithoutItem(level.getBlockState(mainPos), level, mainPos, player, mainHit);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state,
                                              @Nonnull Level level, @Nonnull BlockPos pos,
                                              @Nonnull Player player, @Nonnull InteractionHand hand,
                                              @Nonnull BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos == null) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            BlockHitResult mainHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), mainPos, hit.isInside());
            return useItemOn(stack, level.getBlockState(mainPos), level, mainPos, player, hand, mainHit);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    private VoxelShape wholeShapeForCell(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape wholeShape = wholeShapes.getOrDefault(state.getValue(FACING), Shapes.empty());
        if (state.getValue(PART) == Part.MAIN) {
            return wholeShape;
        }
        CellOffset offset = findOffsetForExtension(level, pos, state);
        if (offset == null) {
            return Shapes.empty();
        }
        return shiftWholeShape(wholeShape, -offset.dx(), -offset.dy(), -offset.dz());
    }

    static VoxelShape shiftWholeShape(VoxelShape shape, int dx, int dy, int dz) {
        final VoxelShape[] shifted = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
            shifted[0] = Shapes.or(shifted[0], Shapes.box(
                minX + dx, minY + dy, minZ + dz,
                maxX + dx, maxY + dy, maxZ + dz
            ))
        );
        return shifted[0].optimize();
    }
}
