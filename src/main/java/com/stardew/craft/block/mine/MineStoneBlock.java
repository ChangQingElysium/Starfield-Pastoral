package com.stardew.craft.block.mine;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.mining.MineStoneMining;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Source-mapped ground stones. Models extend into supporting soil; interaction never does. */
@SuppressWarnings("null")
public final class MineStoneBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    // The original generator assigns different health to the same appearance in different areas.
    public static final IntegerProperty STONE_HEALTH = IntegerProperty.create("stone_health", 1, 25);
    private final VoxelShape[] shapes = new VoxelShape[4];
    private final String sourceId;

    public MineStoneBlock(int sourceId, Properties properties) {
        this(Integer.toString(sourceId), properties);
    }

    public MineStoneBlock(String sourceId, Properties properties) {
        super(properties);
        this.sourceId = sourceId;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STONE_HEALTH, defaultHealth(sourceId)));
    }

    private static int defaultHealth(String sourceId) {
        return switch (sourceId) { case "668", "670" -> 2; case "75", "751", "48", "50", "52", "54" -> 3; case "817", "818", "816", "290", "56", "58", "760", "762", "46" -> 4; case "BasicCoalNode0", "BasicCoalNode1", "76", "4", "6", "8", "10", "12", "14" -> 5; case "2", "VolcanoCoalNode0", "VolcanoCoalNode1" -> 10; case "VolcanoGoldNode", "819", "25", "764", "CalicoEggStone_0", "CalicoEggStone_1", "CalicoEggStone_2" -> 8; case "765" -> 16; case "77" -> 7; case "95" -> 25; case "843", "844" -> 12; case "845", "846", "847", "849" -> 6; default -> 1; };
    }

    public String sourceId() { return sourceId; }

    public static String modelStem(String sourceId) {
        return sourceId.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STONE_HEALTH);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var theme = MineBuildingTheme.forPlacement(context);
        int hp = switch (theme) {
            case LAVA, LAVA_DARK -> 4;
            case DESERT, DESERT_DARK -> 5;
            default -> 1;
        };
        if (sourceId.equals("4") || sourceId.equals("12") || sourceId.equals("760") || sourceId.equals("762") || sourceId.equals("764") || sourceId.equals("56") || sourceId.equals("58") || sourceId.equals("2") || sourceId.equals("14") || sourceId.equals("668") || sourceId.equals("670") || sourceId.equals("6") || sourceId.equals("290") || sourceId.equals("8") || sourceId.equals("10")) hp = defaultHealth(sourceId);
        if (sourceId.equals("48") || sourceId.equals("50") || sourceId.equals("52") || sourceId.equals("54")) hp = defaultHealth(sourceId);
        if (sourceId.equals("VolcanoCoalNode1") || sourceId.equals("BasicCoalNode0") || sourceId.equals("BasicCoalNode1") || sourceId.equals("850") || sourceId.equals("VolcanoGoldNode") || sourceId.equals("VolcanoCoalNode0") || sourceId.equals("846") || sourceId.equals("847") || sourceId.equals("849") || sourceId.equals("843") || sourceId.equals("844") || sourceId.equals("845") || sourceId.equals("817") || sourceId.equals("818") || sourceId.equals("819") || sourceId.equals("450") || sourceId.equals("25") || sourceId.equals("816") || sourceId.equals("343") || sourceId.equals("77") || sourceId.equals("95") || sourceId.equals("75") || sourceId.equals("76") || sourceId.equals("765") || MineStoneMining.isCalicoStone(sourceId)) hp = defaultHealth(sourceId);
        if (sourceId.equals("46")) hp = theme == MineBuildingTheme.DESERT || theme == MineBuildingTheme.DESERT_DARK ? 5 : 4;
        if (sourceId.equals("751")) hp = theme == MineBuildingTheme.DESERT || theme == MineBuildingTheme.DESERT_DARK ? 2 : 3;
        if (sourceId.equals("44") && (theme == MineBuildingTheme.FROST || theme == MineBuildingTheme.FROST_DARK)) hp = 3;
        if (sourceId.equals("849") && com.stardew.craft.api.v1.world.StardewLocations.hierarchy(
                context.getLevel().dimension().location(), context.getClickedPos()).stream()
                .anyMatch(com.stardew.craft.mining.IslandStoneRewards::isVolcano)) hp = 1;
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(STONE_HEALTH, hp);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
                                               LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return side == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int turns = (state.getValue(FACING).get2DDataValue() + 2) % 4;
        if (shapes[turns] == null) {
            String modelId = "stardewcraft:block/mine/nodes/stone_" + modelStem(sourceId);
            VoxelShape modelShape = ModelVoxelShapeCache.requiredShape(modelId);
            AABB bounds = modelShape.bounds();
            VoxelShape aboveSoil = Shapes.create(new AABB(bounds.minX, Math.max(0, bounds.minY), bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ));
            shapes[turns] = ModelVoxelShapeCache.rotateY(aboveSoil, turns);
        }
        return shapes[turns];
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        int power = MineStoneMining.pickaxePower(player.getMainHandItem());
        // Server multiplies this rate by elapsed ticks; the client uses absolute progress from its tick counter.
        // Bypass vanilla material speed, Efficiency and tool components for these nodes only.
        return power == 0 ? 0 : Math.nextUp(1.0F / MineStoneMining.breakTicks(
                state.getValue(STONE_HEALTH), player.getMainHandItem()));
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
