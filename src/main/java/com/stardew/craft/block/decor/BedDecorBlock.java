package com.stardew.craft.block.decor;

import com.stardew.craft.block.utility.WoodenChestColorPalette;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Set;

/** Two-cell Stardew bed with authored default fabric and optional palette dyeing. */
public class BedDecorBlock extends MapDecorStaticBlock {
    public static final BooleanProperty DYED = BooleanProperty.create("dyed");
    public static final IntegerProperty COLOR = IntegerProperty.create(
        "color",
        0,
        WoodenChestColorPalette.size() - 1
    );

    /** Vanilla places a sleeper two pixels above the visual mattress surface. */
    public static final double SLEEP_Y_OFFSET = 10.0D / 16.0D;

    private static final VoxelShape BED_SURFACE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    private static final VoxelShape SINGLE_HEAD = Shapes.or(
        BED_SURFACE,
        Block.box(1.0D, 8.0D, 5.5D, 15.0D, 10.0D, 13.5D),
        Block.box(0.0D, 8.0D, 13.0D, 16.0D, 14.0D, 16.0D)
    ).optimize();
    private static final VoxelShape DOUBLE_HEAD = Shapes.or(
        BED_SURFACE,
        Block.box(2.5D, 8.0D, 5.3D, 16.0D, 10.0D, 13.3D),
        Block.box(0.0D, 8.0D, 13.0D, 16.0D, 14.0D, 16.0D)
    ).optimize();

    private final boolean doubleBed;

    public BedDecorBlock(Properties properties, String modelId, boolean doubleBed) {
        super(properties, modelId);
        this.doubleBed = doubleBed;
        registerDefaultState(defaultBlockState()
            .setValue(DYED, false)
            .setValue(COLOR, WoodenChestColorPalette.defaultColorIndex()));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DYED, COLOR);
    }

    @Override
    protected Set<CellOffset> localOccupiedOffsets() {
        if (usesAuthoredShape()) return super.localOccupiedOffsets();
        Set<CellOffset> cells = new LinkedHashSet<>();
        cells.add(CellOffset.ZERO);
        cells.add(new CellOffset(0, 0, 1));
        if (doubleBed) {
            cells.add(new CellOffset(-1, 0, 0));
            cells.add(new CellOffset(-1, 0, 1));
        }
        return cells;
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state,
                               @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos,
                               @Nonnull CollisionContext context) {
        return stagedShape(state, level, pos);
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state,
                                        @Nonnull BlockGetter level,
                                        @Nonnull BlockPos pos,
                                        @Nonnull CollisionContext context) {
        return stagedShape(state, level, pos);
    }

    private VoxelShape stagedShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (usesAuthoredShape()) return super.getShape(state, level, pos, CollisionContext.empty());
        Direction facing = state.getValue(FACING);
        CellOffset localOffset = CellOffset.ZERO;
        if (state.getValue(PART) == Part.EXTENSION) {
            CellOffset worldOffset = findOffsetForExtension(level, pos, state);
            if (worldOffset == null) {
                return Shapes.empty();
            }
            localOffset = worldOffset.unrotateY(facing);
        }

        VoxelShape localShape = localOffset.dz() == 1
            ? (doubleBed ? DOUBLE_HEAD : SINGLE_HEAD)
            : BED_SURFACE;
        return rotateShapeForFacing(localShape, facing);
    }

    public BlockPos resolveMainPos(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos mainPos = findMainPos(level, pos, state);
        return mainPos == null ? pos : mainPos;
    }

    protected boolean usesAuthoredShape() { return false; }

    public double sleepYOffset() { return SLEEP_Y_OFFSET; }

    /** The clicked lane selects a sleeper in double beds; all cells share one main block. */
    public BlockPos sleepAnchor(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos main = resolveMainPos(level, pos, state);
        Direction facing = state.getValue(FACING);
        CellOffset clicked = new CellOffset(pos.getX() - main.getX(), 0, pos.getZ() - main.getZ()).unrotateY(facing);
        CellOffset head = new CellOffset(doubleBed && clicked.dx() < 0 ? -1 : 0, 0, 1).rotateY(facing);
        BlockPos target = main.offset(head.dx(), head.dy(), head.dz());
        return level.getBlockState(target).is(this) ? target : main;
    }

    public int currentColor(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos mainPos = resolveMainPos(level, pos, state);
        BlockState mainState = level.getBlockState(mainPos);
        if (mainState.is(this) && mainState.hasProperty(COLOR)) {
            return mainState.getValue(COLOR);
        }
        return WoodenChestColorPalette.defaultColorIndex();
    }

    public void applyColor(Level level, BlockPos pos, BlockState state, int colorIndex) {
        BlockPos mainPos = resolveMainPos(level, pos, state);
        BlockState mainState = level.getBlockState(mainPos);
        if (!mainState.is(this) || mainState.getValue(PART) != Part.MAIN) {
            return;
        }

        int color = WoodenChestColorPalette.clampIndex(colorIndex);
        if (color < 0) {
            color = WoodenChestColorPalette.defaultColorIndex();
        }
        Direction facing = mainState.getValue(FACING);
        for (CellOffset offset : occupiedOffsets(facing)) {
            BlockPos targetPos = mainPos.offset(offset.dx(), offset.dy(), offset.dz());
            BlockState targetState = level.getBlockState(targetPos);
            if (!targetState.is(this)) {
                continue;
            }
            BlockState updated = targetState.setValue(DYED, colorIndex >= 0).setValue(COLOR, color);
            if (updated != targetState) {
                level.setBlock(targetPos, updated, Block.UPDATE_ALL);
            }
        }
    }
}
