package com.stardew.craft.templates;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.stardew.craft.port.PortBlockInteraction;

public class MaterialTemplateBlock extends BaseEntityBlock implements TemplateBlock, PortBlockInteraction {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FLIPPED = BooleanProperty.create("flipped");
    public static final BooleanProperty SOLID = BooleanProperty.create("solid");
    public static final BooleanProperty PROPAGATES_SKYLIGHT = BooleanProperty.create("propagates_skylight");

    private final TemplateShape templateShape;

    public MaterialTemplateBlock(TemplateShape templateShape, BlockBehaviour.Properties properties) {
        super(properties);
        this.templateShape = templateShape;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(FLIPPED, false)
                .setValue(SOLID, true)
                .setValue(PROPAGATES_SKYLIGHT, false));
    }

    public TemplateShape templateShape() {
        return templateShape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FLIPPED, SOLID, PROPAGATES_SKYLIGHT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        double localY = context.getClickLocation().y - context.getClickedPos().getY();
        Direction facing = placementFacing(context);
        boolean flipped = switch (templateShape.flipMode()) {
            case NONE -> false;
            case HALF -> face == Direction.DOWN || (face.getAxis().isHorizontal() && localY >= 0.5D);
            case SLOPE -> face == Direction.DOWN || (face.getAxis().isHorizontal() && localY > 0.8125D);
        };
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(FLIPPED, flipped);
    }

    private Direction placementFacing(BlockPlaceContext context) {
        Direction side = context.getClickedFace();
        return switch (templateShape.placementMode()) {
            case NONE -> defaultBlockState().getValue(FACING);
            case PLAYER -> context.getHorizontalDirection();
            case TARGET_OR_PLAYER -> side.getAxis().isHorizontal()
                    ? side.getOpposite()
                    : context.getHorizontalDirection();
            case HALF -> halfFacing(context, side);
            case HALF_OR_QUARTER -> halfOrQuarterFacing(context, side);
        };
    }

    private static Direction halfFacing(BlockPlaceContext context, Direction side) {
        if (!side.getAxis().isHorizontal()) {
            Direction facing = context.getHorizontalDirection();
            return fractionInDirection(context, facing.getClockWise()) > 0.5D
                    ? facing.getClockWise()
                    : facing;
        }
        return fractionInDirection(context, side.getCounterClockWise()) > 0.5D
                ? side.getOpposite().getClockWise()
                : side.getOpposite();
    }

    private static Direction halfOrQuarterFacing(BlockPlaceContext context, Direction side) {
        if (side.getAxis().isHorizontal()) {
            return halfFacing(context, side);
        }
        double x = fractionInDirection(context, Direction.EAST);
        double z = fractionInDirection(context, Direction.SOUTH);
        Direction facing = z > 0.5D ? Direction.SOUTH : Direction.NORTH;
        if ((x > 0.5D) != (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE)) {
            facing = facing.getClockWise();
        }
        return facing;
    }

    private static double fractionInDirection(BlockPlaceContext context, Direction direction) {
        double coordinate = switch (direction.getAxis()) {
            case X -> context.getClickLocation().x - context.getClickedPos().getX();
            case Y -> context.getClickLocation().y - context.getClickedPos().getY();
            case Z -> context.getClickLocation().z - context.getClickedPos().getZ();
        };
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? coordinate : 1D - coordinate;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TemplateShapeCache.get(templateShape, state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return templateShape.canOccludeWithSolidMaterial() && state.getValue(SOLID)
                ? TemplateShapeCache.get(templateShape, state)
                : Shapes.empty();
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        // Called while Block's super-constructor is still building every state,
        // before templateShape has been assigned. Non-occluding template types
        // are handled by Properties.noOcclusion().
        return state.getValue(SOLID);
    }

    @Override
    public boolean supportsExternalFaceHiding(BlockState state) {
        return true;
    }

    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state,
                                     BlockState neighborState, Direction direction) {
        if (this instanceof CompositeTemplateBlock || neighborState.getBlock() instanceof CompositeTemplateBlock
                || !state.getValue(SOLID)
                || !(neighborState.getBlock() instanceof MaterialTemplateBlock neighborBlock)) {
            return false;
        }

        // This block is the covering neighbor. Only discard the other block's
        // directional quad list if every part of that boundary is covered here.
        VoxelShape coveringFace = Shapes.getFaceShape(
                TemplateShapeCache.get(templateShape, state), direction);
        VoxelShape coveredFace = Shapes.getFaceShape(
                TemplateShapeCache.get(neighborBlock.templateShape(), neighborState), direction.getOpposite());
        return !coveredFace.isEmpty()
                && !Shapes.joinIsNotEmpty(coveredFace, coveringFace, BooleanOp.ONLY_FIRST);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        float ownShade = Block.isShapeFullBlock(TemplateShapeCache.get(templateShape, state)) ? 0.2F : 1.0F;
        return Math.max(TemplateMaterials.effectiveMaterial(level, pos).getShadeBrightness(level, pos), ownShade);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        // Lighting can run before this chunk is FULL. Dynamic selection shapes
        // may query neighboring chunks, so use only the state-local geometry here.
        return state.getValue(PROPAGATES_SKYLIGHT)
                || (!Block.isShapeFullBlock(TemplateShapeCache.get(templateShape, state))
                    && state.getFluidState().isEmpty());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TemplateBlockEntity(pos, state);
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof TemplateBlockEntity template
                && template.material() != null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockState material = blockItem.getBlock().defaultBlockState();
        if (!TemplateMaterials.isValid(material)) {
            return ItemInteractionResult.FAIL;
        }

        if (!(level.getBlockEntity(pos) instanceof TemplateBlockEntity template)) {
            return ItemInteractionResult.FAIL;
        }
        if (material.equals(template.material())) {
            return ItemInteractionResult.SUCCESS;
        }

        if (!level.isClientSide()) {
            BlockState previous = template.material();
            template.setMaterial(material);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                returnMaterial(player, previous);
            }
            level.playSound(null, pos, material.getSoundType(level, pos, player).getPlaceSound(),
                    SoundSource.BLOCKS, 1F, 1F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof TemplateBlockEntity template) || template.material() == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            BlockState previous = template.material();
            template.setMaterial(null);
            returnMaterial(player, previous);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 1F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!(this instanceof CompositeTemplateBlock) && !level.isClientSide() && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof TemplateBlockEntity template) {
            BlockState material = template.material();
            if (material != null && material.getBlock().asItem() != net.minecraft.world.item.Items.AIR) {
                popResource(level, pos, new ItemStack(material.getBlock().asItem()));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return TemplateMaterials.effectiveMaterial(level, pos).getSoundType(level, pos, entity);
    }

    @Override
    public float getFriction(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return TemplateMaterials.effectiveMaterial(level, pos).getFriction(level, pos, entity);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return TemplateMaterials.effectiveMaterial(level, pos).getDestroyProgress(player, level, pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return TemplateMaterials.effectiveMaterial(level, pos).getExplosionResistance(level, pos, explosion);
    }

    @Override
    public MapColor getMapColor(BlockState state, BlockGetter level, BlockPos pos, MapColor defaultColor) {
        return TemplateMaterials.effectiveMaterial(level, pos).getMapColor(level, pos);
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return templateShape == TemplateShape.LADDER;
    }

    private static void returnMaterial(Player player, @Nullable BlockState state) {
        if (state == null || state.getBlock().asItem() == net.minecraft.world.item.Items.AIR) {
            return;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(state.getBlock().asItem()));
    }
}
