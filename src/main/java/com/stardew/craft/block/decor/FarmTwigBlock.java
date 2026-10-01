package com.stardew.craft.block.decor;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.tool.StardewAxeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ToolActions;

/** SDV objects 294/295. Placement and clearing only; farm generation is a separate system. */
public final class FarmTwigBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 1);
    private final VoxelShape[][] shapes = new VoxelShape[2][4];

    public FarmTwigBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VARIANT, 0));
    }

    public static boolean isAxe(ItemStack stack) {
        return stack.canPerformAction(ToolActions.AXE_DIG);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VARIANT);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(VARIANT, context.getLevel().random.nextInt(2));
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
        int variant = state.getValue(VARIANT);
        int turns = (state.getValue(FACING).get2DDataValue() + 2) % 4;
        if (shapes[variant][turns] == null) {
            VoxelShape model = ModelVoxelShapeCache.requiredShape("stardewcraft:block/decor/farm_twig_" + (variant + 1));
            shapes[variant][turns] = ModelVoxelShapeCache.rotateY(Shapes.create(model.bounds()), turns);
        }
        return shapes[variant][turns];
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        ItemStack tool = player.getMainHandItem();
        if (!isAxe(tool)) return 0;
        // One source-game swing at every axe tier; use the project's 12-tick ground-node adaptation.
        int ticks = tool.getItem() instanceof StardewAxeItem && StardewEnchantments.has(tool, StardewEnchantments.SWIFT) ? 8 : 12;
        return Math.nextUp(1.0F / ticks);
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
