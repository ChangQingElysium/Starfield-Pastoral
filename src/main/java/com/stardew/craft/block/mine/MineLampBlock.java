package com.stardew.craft.block.mine;

import com.stardew.craft.port.PortItemData;
import com.mojang.serialization.MapCodec;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.MineLampBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import com.stardew.craft.port.PortBlockInteraction;

/** One wall-mounted lamp family; theme survives picking, dropping and placement. */
public final class MineLampBlock extends BaseEntityBlock implements PortBlockInteraction {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<Theme> THEME = EnumProperty.create("theme", Theme.class);

    public enum Theme implements StringRepresentable {
        EARTH("earth", 0xffd397), FROST("frost", 0x86e9df), LAVA("lava", 0xffb184), DESERT("desert", 0xffe5a1),
        EARTH_DARK("earth_dark", 0xf0cb92), FROST_DARK("frost_dark", 0x96caff),
        LAVA_DARK("lava_dark", 0xffa491), DESERT_DARK("desert_dark", 0xa8eee0);
        private final String id;
        private final int lightColor;
        Theme(String id, int lightColor) { this.id = id; this.lightColor = lightColor; }
        @Override public String getSerializedName() { return id; }
        public int lightColor() { return lightColor; }
    }

    private static final java.util.Map<Theme, VoxelShape[]> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    public MineLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(LIT, true).setValue(THEME, Theme.EARTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, LIT, THEME); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MineLampBlockEntity(pos, state); }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Theme fixed = PortItemData.getOrDefault(context.getItemInHand(), DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(THEME);
        java.util.List<Direction> attachmentDirections = new java.util.ArrayList<>();
        if (context.getClickedFace().getAxis().isHorizontal()) attachmentDirections.add(context.getClickedFace().getOpposite());
        attachmentDirections.addAll(java.util.Arrays.asList(context.getNearestLookingDirections()));
        for (Direction look : attachmentDirections) {
            if (!look.getAxis().isHorizontal()) continue;
            Direction facing = look.getOpposite();
            BlockState state = defaultBlockState().setValue(FACING, facing);
            if (!state.canSurvive(context.getLevel(), context.getClickedPos())) continue;
            Theme theme = fixed;
            if (theme == null) {
                theme = Theme.EARTH;
                BlockState wall = context.getLevel().getBlockState(context.getClickedPos().relative(look));
                for (MineBuildingTheme family : MineBuildingTheme.values()) {
                    if (family.rank(wall) >= 0) theme = Theme.valueOf(family.name());
                }
            }
            return state.setValue(THEME, theme);
        }
        return null;
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction face = state.getValue(FACING);
        BlockPos wall = pos.relative(face.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, face);
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }
    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            level.setBlock(pos, state.cycle(LIT), Block.UPDATE_ALL);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.LEVER_CLICK, net.minecraft.sounds.SoundSource.BLOCKS, .3F, state.getValue(LIT) ? .5F : .7F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.computeIfAbsent(state.getValue(THEME), theme -> ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/mine/lantern/" + theme.id, Direction.NORTH))[state.getValue(FACING).get2DDataValue()];
    }
    @Override public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        PortItemData.set(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(THEME, state));
        return stack;
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
}
