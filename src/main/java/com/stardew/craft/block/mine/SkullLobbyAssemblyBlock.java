package com.stardew.craft.block.mine;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.SkullLobbyLightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import com.stardew.craft.port.PortBlockInteraction;

/** Fixed native-model assemblies for the Skull Cavern entrance hall. */
public final class SkullLobbyAssemblyBlock extends BaseEntityBlock implements PortBlockInteraction {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 9);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public enum Kind {
        SKULL_SHRINE_WALL(new BlockPos[]{new BlockPos(0,0,0), new BlockPos(0,1,0), new BlockPos(0,2,0), new BlockPos(1,0,0), new BlockPos(1,1,0), new BlockPos(1,2,0)}, Shapes.or(Shapes.empty(), Block.box(-1,4,5,33,36,16), Block.box(0,28,7,7,41,16), Block.box(25,28,7,32,41,16)), new int[]{1,4}),
        SKULL_SHRINE_ALTAR(new BlockPos[]{new BlockPos(0,0,0), new BlockPos(0,1,-1), new BlockPos(0,1,0), new BlockPos(1,0,-1), new BlockPos(1,0,0), new BlockPos(2,0,-1), new BlockPos(2,0,0), new BlockPos(3,0,0), new BlockPos(3,1,-1), new BlockPos(3,1,0)}, Shapes.or(Shapes.empty(), Block.box(16,0,-12,48,14,8), Block.box(4,0,-6,16,24,6), Block.box(48,0,-6,60,24,6)), new int[]{2,9}),
        SKULL_CAVERN_DOOR(new BlockPos[]{new BlockPos(0,0,0), new BlockPos(0,1,0), new BlockPos(0,2,0)}, Shapes.or(Shapes.empty(), Block.box(1,0,5,15,33,12)), new int[]{}),
        SKULL_WALL_BRAZIER(new BlockPos[]{new BlockPos(0,0,0), new BlockPos(0,1,0)}, Shapes.or(Shapes.empty(), Block.box(-2,2,1,18,10,16)), new int[]{1}),
        SKULL_STALAGMITE(new BlockPos[]{new BlockPos(0,0,0), new BlockPos(0,1,0)}, Shapes.or(Shapes.empty(), Block.box(2,0,3,14,2,13), Block.box(4,2,4,12,8,12), Block.box(5,8,5,11,14,11), Block.box(6,14,6,10,20,10), Block.box(7,20,7,9,24,9)), new int[]{});
        private final BlockPos[] offsets;
        private final VoxelShape[] shapes = new VoxelShape[4];
        private final int[] emitters;
        Kind(BlockPos[] offsets, VoxelShape shape, int[] emitters) {
            this.offsets = offsets;
            this.emitters = emitters;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                shapes[facing.get2DDataValue()] = ModelVoxelShapeCache.rotateY(shape, turns(facing));
            }
        }
    }
    private final Kind kind;
    public SkullLobbyAssemblyBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(SECTION, 0)
                .setValue(LIT, kind == Kind.SKULL_WALL_BRAZIER));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SECTION, LIT);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override @Nullable public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind.emitters.length == 0 ? null : new SkullLobbyLightBlockEntity(pos, state);
    }
    private static int turns(Direction facing) {
        return switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
    }
    public static BlockPos rotateOffset(BlockPos offset, Direction facing) {
        int x = offset.getX(), y = offset.getY(), z = offset.getZ();
        return switch (facing) {
            case EAST -> new BlockPos(-z, y, x);
            case SOUTH -> new BlockPos(-x, y, -z);
            case WEST -> new BlockPos(z, y, -x);
            default -> offset;
        };
    }
    private BlockPos offset(BlockState state) {
        int section = state.getValue(SECTION);
        return rotateOffset(kind.offsets[Math.min(section, kind.offsets.length - 1)], state.getValue(FACING));
    }
    public BlockPos anchor(BlockState state, BlockPos pos) { return pos.subtract(offset(state)); }
    private boolean matches(BlockState state, BlockState other) {
        return other.is(this) && other.getValue(FACING) == state.getValue(FACING);
    }
    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        for (BlockPos local : kind.offsets) {
            BlockPos q = context.getClickedPos().offset(rotateOffset(local, facing));
            if (context.getLevel().isOutsideBuildHeight(q) || !context.getLevel().getWorldBorder().isWithinBounds(q)
                    || !context.getLevel().getBlockState(q).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) for (int i = 1; i < kind.offsets.length; i++) {
            level.setBlock(pos.offset(rotateOffset(kind.offsets[i], state.getValue(FACING))), state.setValue(SECTION, i), 3);
        }
    }
    // Templates can place an extension before its root. Check after the entire placement.
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, this, 1);
        return state;
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos root = anchor(state, pos);
        for (int i = 0; i < kind.offsets.length; i++) {
            BlockState other = level.getBlockState(root.offset(rotateOffset(kind.offsets[i], state.getValue(FACING))));
            if (!matches(state, other) || other.getValue(SECTION) != i || state.getValue(SECTION) >= kind.offsets.length) {
                level.removeBlock(pos, false);
                return;
            }
        }
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!replacement.is(this) && !level.isClientSide) {
            BlockPos root = anchor(state, pos);
            for (int i = 0; i < kind.offsets.length; i++) {
                BlockPos q = root.offset(rotateOffset(kind.offsets[i], state.getValue(FACING)));
                if (q.equals(pos)) continue;
                BlockState other = level.getBlockState(q);
                if (matches(state, other) && other.getValue(SECTION) == i) level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos offset = offset(state);
        return kind.shapes[state.getValue(FACING).get2DDataValue()].move(-offset.getX(), -offset.getY(), -offset.getZ());
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.join(getShape(state, level, pos, context), Shapes.block(), BooleanOp.AND);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) { return new ItemStack(this); }
    @Override public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }
    public static int emission(BlockState state) {
        return state.getBlock() instanceof SkullLobbyAssemblyBlock block ? emission(block.kind, state) : 0;
    }
    public static int emission(Kind kind, BlockState state) {
        if (!state.getValue(LIT)) return 0;
        for (int section : kind.emitters) if (state.getValue(SECTION) == section) return 15;
        return 0;
    }
    public int lightColor() { return kind == Kind.SKULL_WALL_BRAZIER ? 0xffe4a6 : 0xf3afd8; }
    public Vec3 lightOffset(BlockState state) {
        int section = state.getValue(SECTION);
        Vec3 local = kind == Kind.SKULL_WALL_BRAZIER ? new Vec3(.5, .0, .5)
                : kind == Kind.SKULL_SHRINE_ALTAR ? new Vec3(section == 2 ? .625 : .375, .7, 0)
                : new Vec3(section == 1 ? .56 : .5, .25, .24);
        double x = local.x - .5, z = local.z - .5;
        return switch (state.getValue(FACING)) {
            case EAST -> new Vec3(.5-z, local.y, .5+x);
            case SOUTH -> new Vec3(.5-x, local.y, .5-z);
            case WEST -> new Vec3(.5+z, local.y, .5-x);
            default -> local;
        };
    }
    private void setLit(Level level, BlockPos root, BlockState state, boolean lit) {
        for (int i = 0; i < kind.offsets.length; i++) {
            BlockPos q = root.offset(rotateOffset(kind.offsets[i], state.getValue(FACING)));
            BlockState other = level.getBlockState(q);
            if (matches(state, other) && other.getValue(SECTION) == i) level.setBlock(q, other.setValue(LIT, lit), 3);
        }
    }
    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    /** Creative authoring switch only; dangerous-floor progression remains a separate system. */
    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(kind==Kind.SKULL_CAVERN_DOOR && level.dimension()==com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING) {
            if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                if(!com.stardew.craft.player.PlayerDataManager.getPlayerData(serverPlayer).hasMailFlag(com.stardew.craft.communitycenter.state.CCStoryFlags.HAS_SKULL_KEY)) {
                    com.stardew.craft.network.ObjectDialogueService.show(serverPlayer,"message.stardewcraft.skull_door_locked");
                } else {
                    com.stardew.craft.event.InteriorPortalInteractionEvents.unlockSkullDoorQuest(serverPlayer);
                    com.stardew.craft.mining.SkullCavernSessionManager.onPlayerEnter(serverPlayer);
                    com.stardew.craft.mining.MiningCoordinates.teleportPlayerToFloor(serverPlayer,(ServerLevel)level,121);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!player.isCreative() || (kind != Kind.SKULL_SHRINE_ALTAR && kind != Kind.SKULL_SHRINE_WALL)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            boolean lit = !state.getValue(LIT);
            BlockPos root = anchor(state, pos);
            setLit(level, root, state, lit);
            BlockPos partner = root.offset(rotateOffset(kind == Kind.SKULL_SHRINE_ALTAR
                    ? new BlockPos(1,1,0) : new BlockPos(-1,-1,0), state.getValue(FACING)));
            BlockState other = level.getBlockState(partner);
            Block expected = kind == Kind.SKULL_SHRINE_ALTAR ? ModBlocks.SKULL_SHRINE_WALL.get() : ModBlocks.SKULL_SHRINE_ALTAR.get();
            if (other.is(expected) && other.getValue(SECTION) == 0 && other.getValue(FACING) == state.getValue(FACING)) {
                ((SkullLobbyAssemblyBlock) other.getBlock()).setLit(level, partner, other, lit);
            }
            level.playSound(null, root, net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON,
                    net.minecraft.sounds.SoundSource.BLOCKS, .6F, lit ? .6F : .8F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
