package com.stardew.craft.block.utility;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.TableDisplayBlockEntity;
import com.stardew.craft.festival.FairFestivalService;
import com.stardew.craft.item.furniture.FloralTableclothItem;
import com.stardew.craft.item.furniture.PinkTableclothItem;
import com.stardew.craft.item.furniture.SkyBlueTableclothItem;
import com.stardew.craft.item.furniture.BlankTableclothItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
public class OakTableBlock extends Block implements EntityBlock {
    public static final BooleanProperty NORTH_CONNECTED = BooleanProperty.create("north_connected");
    public static final BooleanProperty EAST_CONNECTED = BooleanProperty.create("east_connected");
    public static final BooleanProperty SOUTH_CONNECTED = BooleanProperty.create("south_connected");
    public static final BooleanProperty WEST_CONNECTED = BooleanProperty.create("west_connected");

    public static final BooleanProperty NORTH_WEST_CONNECTED = BooleanProperty.create("north_west_connected");
    public static final BooleanProperty NORTH_EAST_CONNECTED = BooleanProperty.create("north_east_connected");
    public static final BooleanProperty SOUTH_EAST_CONNECTED = BooleanProperty.create("south_east_connected");
    public static final BooleanProperty SOUTH_WEST_CONNECTED = BooleanProperty.create("south_west_connected");

    public static final BooleanProperty HAS_CLOTH = BooleanProperty.create("has_cloth");
    public static final IntegerProperty CLOTH_STYLE = IntegerProperty.create("cloth_style", 0, 3);

    public static final int CLOTH_STYLE_PINK = 0;
    public static final int CLOTH_STYLE_SKY = 1;
    public static final int CLOTH_STYLE_FLOWER = 2;
    public static final int CLOTH_STYLE_BLANK = 3;
    public static final int DEFAULT_CLOTH_COLOR = WoodenChestColorPalette.size() - 1;

    private final String topModel;
    private final String topClothedModel;
    private final String apronCoreModel;
    private final String apronNModel;
    private final String apronEModel;
    private final String apronSModel;
    private final String apronWModel;
    private final String legNwModel;
    private final String legNeModel;
    private final String legSeModel;
    private final String legSwModel;
    private final boolean usesApronModels;
    private final float displayItemY;
    private final float clothedDisplayItemY;

    private static final float TABLE_DISPLAY_ITEM_Y = 16.02f / 16.0f;
    private static final float TABLE_CLOTHED_DISPLAY_ITEM_Y = 16.10f / 16.0f;
    private static final VoxelShape FALLBACK_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private final Map<String, VoxelShape> shapeCache = new ConcurrentHashMap<>();

    public OakTableBlock(Properties properties) {
        this(properties, "oak_table");
    }

    public OakTableBlock(Properties properties, String modelBaseName) {
        super(properties);
        String modelRoot = "stardewcraft:block/utility/" + modelBaseName;
        this.topModel = modelRoot + "_top";
        this.topClothedModel = modelRoot + "_top_clothed";
        this.apronCoreModel = modelRoot + "_apron_core";
        this.apronNModel = modelRoot + "_apron_n";
        this.apronEModel = modelRoot + "_apron_e";
        this.apronSModel = modelRoot + "_apron_s";
        this.apronWModel = modelRoot + "_apron_w";
        this.legNwModel = modelRoot + "_leg_nw";
        this.legNeModel = modelRoot + "_leg_ne";
        this.legSeModel = modelRoot + "_leg_se";
        this.legSwModel = modelRoot + "_leg_sw";
        this.usesApronModels = false;
        this.displayItemY = TABLE_DISPLAY_ITEM_Y;
        this.clothedDisplayItemY = TABLE_CLOTHED_DISPLAY_ITEM_Y;

        registerDefaultState(stateDefinition.any()
            .setValue(NORTH_CONNECTED, false)
            .setValue(EAST_CONNECTED, false)
            .setValue(SOUTH_CONNECTED, false)
            .setValue(WEST_CONNECTED, false)
            .setValue(NORTH_WEST_CONNECTED, false)
            .setValue(NORTH_EAST_CONNECTED, false)
            .setValue(SOUTH_EAST_CONNECTED, false)
            .setValue(SOUTH_WEST_CONNECTED, false)
            .setValue(HAS_CLOTH, false)
            .setValue(CLOTH_STYLE, CLOTH_STYLE_PINK)
            );
    }

    public float getDisplayItemY(BlockState state) {
        if (state != null && state.hasProperty(HAS_CLOTH) && state.getValue(HAS_CLOTH)) {
            return clothedDisplayItemY;
        }
        return displayItemY;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // Connected tables are independent blocks, so each broken table drops
        // exactly its own registered block item once.
        return List.of(new ItemStack(this));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH_CONNECTED, EAST_CONNECTED, SOUTH_CONNECTED, WEST_CONNECTED,
            NORTH_WEST_CONNECTED, NORTH_EAST_CONNECTED, SOUTH_EAST_CONNECTED, SOUTH_WEST_CONNECTED,
            HAS_CLOTH, CLOTH_STYLE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return updateConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || state.is(oldState.getBlock())) {
            return;
        }
        BlockState updatedSelf = updateConnections(level, pos, state);
        if (updatedSelf != state) {
            level.setBlock(pos, updatedSelf, 3);
        }
        refreshNeighbors(level, pos, state.getBlock());
    }

    @Override
    protected BlockState updateShape(BlockState state,
                                     Direction direction,
                                     BlockState neighborState,
                                     LevelAccessor level,
                                     BlockPos pos,
                                     BlockPos neighborPos) {
        return updateConnections(level, pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TableDisplayBlockEntity tableBe && tableBe.hasDisplayItem()) {
                popResource(level, pos, tableBe.removeDisplayItem());
            }
            refreshNeighbors(level, pos, state.getBlock());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TableDisplayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult fairResult = FairFestivalService.handleFairSpruceTableUseItem(stack, state, level, pos, player);
        if (fairResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return fairResult;
        }

        // Let dedicated tablecloth items handle right-click first.
        if (isTableclothItem(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!(level.getBlockEntity(pos) instanceof TableDisplayBlockEntity tableBe)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (tableBe.hasDisplayItem()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }

        ItemStack placed = stack.copy();
        placed.setCount(1);
        // Snap display orientation to player cardinal facing so placement feels intentional.
        float snappedYaw = (float) net.minecraft.core.Direction.fromYRot(player.getYRot()).toYRot();
        tableBe.setDisplayItem(placed, snappedYaw);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7f, 1.0f);
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        InteractionResult fairResult = FairFestivalService.handleFairSpruceTableUse(state, level, pos, player);
        if (fairResult != InteractionResult.PASS) {
            return fairResult;
        }

        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (!(level.getBlockEntity(pos) instanceof TableDisplayBlockEntity tableBe)) {
            return InteractionResult.PASS;
        }
        if (!tableBe.hasDisplayItem()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack removed = tableBe.removeDisplayItem();
        if (removed.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!player.addItem(removed)) {
            player.drop(removed, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7f, 1.0f);
        return InteractionResult.CONSUME;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getModelShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Keep gameplay collision identical whether cloth is present or not.
        return getModelShape(state.setValue(HAS_CLOTH, false));
    }

    private BlockState updateConnections(LevelAccessor level, BlockPos pos, BlockState state) {
        Block tableBlock = state.getBlock();

        boolean north = isSameTable(level.getBlockState(pos.north()), tableBlock);
        boolean east = isSameTable(level.getBlockState(pos.east()), tableBlock);
        boolean south = isSameTable(level.getBlockState(pos.south()), tableBlock);
        boolean west = isSameTable(level.getBlockState(pos.west()), tableBlock);

        boolean northWest = isSameTable(level.getBlockState(pos.north().west()), tableBlock);
        boolean northEast = isSameTable(level.getBlockState(pos.north().east()), tableBlock);
        boolean southEast = isSameTable(level.getBlockState(pos.south().east()), tableBlock);
        boolean southWest = isSameTable(level.getBlockState(pos.south().west()), tableBlock);

        boolean hasCloth = state.getValue(HAS_CLOTH);
        int clothStyle = state.getValue(CLOTH_STYLE);

        // Keep connected components cloth-homogeneous: new table touching clothed tables inherits cloth.
        if (!hasCloth) {
            BlockState[] cardinals = new BlockState[] {
                level.getBlockState(pos.north()),
                level.getBlockState(pos.east()),
                level.getBlockState(pos.south()),
                level.getBlockState(pos.west())
            };
            for (BlockState neighbor : cardinals) {
                if (isSameTable(neighbor, tableBlock) && neighbor.getValue(HAS_CLOTH)) {
                    hasCloth = true;
                    clothStyle = neighbor.getValue(CLOTH_STYLE);
                    break;
                }
            }
        }

        return state
            .setValue(NORTH_CONNECTED, north)
            .setValue(EAST_CONNECTED, east)
            .setValue(SOUTH_CONNECTED, south)
            .setValue(WEST_CONNECTED, west)
            .setValue(NORTH_WEST_CONNECTED, northWest)
            .setValue(NORTH_EAST_CONNECTED, northEast)
            .setValue(SOUTH_EAST_CONNECTED, southEast)
            .setValue(SOUTH_WEST_CONNECTED, southWest)
            .setValue(HAS_CLOTH, hasCloth)
                .setValue(CLOTH_STYLE, normalizeClothStyle(clothStyle));
    }

    private static boolean isSameTable(BlockState state, Block tableBlock) {
        return state.getBlock() == tableBlock;
    }

    public static boolean applyClothToConnectedTables(Level level, BlockPos startPos) {
        return applyClothToConnectedTables(level, startPos, CLOTH_STYLE_PINK);
    }

    public static boolean applyClothToConnectedTables(Level level, BlockPos startPos, int clothStyle) {
        BlockState start = level.getBlockState(startPos);
        if (!(start.getBlock() instanceof OakTableBlock)) {
            return false;
        }

        Block targetBlock = start.getBlock();
        OakTableBlock tableBlock = (OakTableBlock) targetBlock;
        int targetStyle = normalizeClothStyle(clothStyle);
        Set<BlockPos> cluster = collectConnectedTables(level, startPos, targetBlock);
        boolean changed = false;

        for (BlockPos pos : cluster) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() != targetBlock) {
                continue;
            }
            boolean hasCloth = state.getValue(HAS_CLOTH);
            int currentStyle = state.getValue(CLOTH_STYLE);
            if (!hasCloth || currentStyle != targetStyle) {
                level.setBlock(pos, state.setValue(HAS_CLOTH, true).setValue(CLOTH_STYLE, targetStyle), 2);
                changed = true;
            }
            if (isDyeableClothStyle(targetStyle) && level.getBlockEntity(pos) instanceof TableDisplayBlockEntity tableBe) {
                tableBe.setClothColor(DEFAULT_CLOTH_COLOR);
            }
        }

        for (BlockPos pos : cluster) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() != targetBlock) {
                continue;
            }
            BlockState updated = tableBlock.updateConnections(level, pos, state);
            if (updated != state) {
                level.setBlock(pos, updated, 3);
            }
        }

        return changed;
    }

    public static boolean applyClothColorToConnectedTables(Level level, BlockPos startPos, int color) {
        BlockState start = level.getBlockState(startPos);
        if (!(start.getBlock() instanceof OakTableBlock) || !start.getValue(HAS_CLOTH) || !isDyeableClothStyle(start.getValue(CLOTH_STYLE))) {
            return false;
        }

        Block targetBlock = start.getBlock();
        int targetColor = normalizeClothColor(color);
        Set<BlockPos> cluster = collectConnectedTables(level, startPos, targetBlock);
        boolean changed = false;

        for (BlockPos pos : cluster) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() != targetBlock || !state.getValue(HAS_CLOTH) || !isDyeableClothStyle(state.getValue(CLOTH_STYLE))) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof TableDisplayBlockEntity tableBe && tableBe.getClothColor() != targetColor) {
                tableBe.setClothColor(targetColor);
                changed = true;
            }
        }

        return changed;
    }

    public static boolean isTableclothItem(ItemStack stack) {
        return stack.getItem() instanceof PinkTableclothItem
            || stack.getItem() instanceof SkyBlueTableclothItem
            || stack.getItem() instanceof FloralTableclothItem
            || stack.getItem() instanceof BlankTableclothItem;
    }

    public static boolean isDyeableClothStyle(int clothStyle) {
        return clothStyle == CLOTH_STYLE_FLOWER || clothStyle == CLOTH_STYLE_BLANK;
    }

    private static int normalizeClothStyle(int clothStyle) {
        return switch (clothStyle) {
            case CLOTH_STYLE_SKY -> CLOTH_STYLE_SKY;
            case CLOTH_STYLE_FLOWER -> CLOTH_STYLE_FLOWER;
            case CLOTH_STYLE_BLANK -> CLOTH_STYLE_BLANK;
            default -> CLOTH_STYLE_PINK;
        };
    }

    public static int normalizeClothColor(int clothColor) {
        int clamped = WoodenChestColorPalette.clampIndex(clothColor);
        return clamped < 0 ? DEFAULT_CLOTH_COLOR : clamped;
    }

    private static Set<BlockPos> collectConnectedTables(Level level, BlockPos startPos, Block tableBlock) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(startPos.immutable());

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }

            BlockState state = level.getBlockState(current);
            if (state.getBlock() != tableBlock) {
                continue;
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction).immutable();
                if (!visited.contains(next)) {
                    queue.add(next);
                }
            }
        }

        visited.removeIf(pos -> level.getBlockState(pos).getBlock() != tableBlock);
        return visited;
    }

    private void refreshNeighbors(Level level, BlockPos pos, Block tableBlock) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() != tableBlock) {
                continue;
            }
            BlockState updated = updateConnections(level, neighborPos, neighborState);
            if (updated != neighborState) {
                level.setBlock(neighborPos, updated, 3);
            }
        }
    }

    private VoxelShape getModelShape(BlockState state) {
        String key = state.getValue(NORTH_CONNECTED) + ":"
            + state.getValue(EAST_CONNECTED) + ":"
            + state.getValue(SOUTH_CONNECTED) + ":"
            + state.getValue(WEST_CONNECTED) + ":"
            + state.getValue(NORTH_WEST_CONNECTED) + ":"
            + state.getValue(NORTH_EAST_CONNECTED) + ":"
            + state.getValue(SOUTH_EAST_CONNECTED) + ":"
            + state.getValue(SOUTH_WEST_CONNECTED) + ":"
            + state.getValue(HAS_CLOTH);

        return shapeCache.computeIfAbsent(key, ignored -> {
            VoxelShape shape = Shapes.empty();

            shape = Shapes.or(shape, ModelVoxelShapeCache.shapeFromModelId(
                state.getValue(HAS_CLOTH) ? topClothedModel : topModel
            ));
            if (usesApronModels) {
                shape = Shapes.or(shape, ModelVoxelShapeCache.shapeFromModelId(apronCoreModel));

                if (state.getValue(NORTH_CONNECTED)) {
                    shape = Shapes.or(shape, ModelVoxelShapeCache.shapeFromModelId(apronNModel));
                }
                if (state.getValue(EAST_CONNECTED)) {
                    shape = Shapes.or(shape, ModelVoxelShapeCache.shapeFromModelId(apronEModel));
                }
                if (state.getValue(SOUTH_CONNECTED)) {
                    shape = Shapes.or(shape, ModelVoxelShapeCache.shapeFromModelId(apronSModel));
                }
                if (state.getValue(WEST_CONNECTED)) {
                    shape = Shapes.or(shape, ModelVoxelShapeCache.shapeFromModelId(apronWModel));
                }
            }

            if (shouldRenderLegNw(state)) {
                shape = Shapes.or(shape, ModelVoxelShapeCache.shapeFromModelId(legNwModel));
            }
            if (shouldRenderLegNe(state)) {
                shape = Shapes.or(shape, ModelVoxelShapeCache.shapeFromModelId(legNeModel));
            }
            if (shouldRenderLegSe(state)) {
                shape = Shapes.or(shape, ModelVoxelShapeCache.shapeFromModelId(legSeModel));
            }
            if (shouldRenderLegSw(state)) {
                shape = Shapes.or(shape, ModelVoxelShapeCache.shapeFromModelId(legSwModel));
            }

            shape = shape.optimize();
            return shape.isEmpty() ? FALLBACK_SHAPE : shape;
        });
    }

    private static boolean shouldRenderLegNw(BlockState state) {
        boolean north = state.getValue(NORTH_CONNECTED);
        boolean west = state.getValue(WEST_CONNECTED);
        boolean northWest = state.getValue(NORTH_WEST_CONNECTED);
        return (!north && !west) || (north && west && !northWest);
    }

    private static boolean shouldRenderLegNe(BlockState state) {
        boolean north = state.getValue(NORTH_CONNECTED);
        boolean east = state.getValue(EAST_CONNECTED);
        boolean northEast = state.getValue(NORTH_EAST_CONNECTED);
        return (!north && !east) || (north && east && !northEast);
    }

    private static boolean shouldRenderLegSe(BlockState state) {
        boolean south = state.getValue(SOUTH_CONNECTED);
        boolean east = state.getValue(EAST_CONNECTED);
        boolean southEast = state.getValue(SOUTH_EAST_CONNECTED);
        return (!south && !east) || (south && east && !southEast);
    }

    private static boolean shouldRenderLegSw(BlockState state) {
        boolean south = state.getValue(SOUTH_CONNECTED);
        boolean west = state.getValue(WEST_CONNECTED);
        boolean southWest = state.getValue(SOUTH_WEST_CONNECTED);
        return (!south && !west) || (south && west && !southWest);
    }
}
