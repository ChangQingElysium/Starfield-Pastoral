package com.stardew.craft.pet;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.item.tool.WateringCanItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Building manager/body; its protected 2x2 pad uses existing surface-floor overlays. */
public final class PetBowlBlock extends Block {
    public static final BooleanProperty FULL = BooleanProperty.create("full");
    public static final IntegerProperty SEASON = IntegerProperty.create("season", 0, 3);
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 4, 14);
    public final String style;
    public PetBowlBlock(Properties properties, String style) {
        super(properties); this.style = style;
        registerDefaultState(stateDefinition.any().setValue(FULL, false).setValue(SEASON, 0));
    }
    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(p -> new PetBowlBlock(p, style)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FULL, SEASON); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level) {
            var pos = context.getClickedPos();
            if (level.dimension() != ModDimensions.STARDEW_VALLEY) return null;
            var owner = FarmInstanceRegistry.get(level.getServer()).getOwnerAt(pos);
            var farm = owner == null ? null : FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
            if (farm == null) return null;
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                var cell = pos.offset(x, 0, z);
                if (!farm.contains(cell) || !level.getBlockState(cell).canBeReplaced()
                        || !com.stardew.craft.floor.SurfaceFloorItem.supports(level, cell.below(), level.getBlockState(cell.below()))
                        || com.stardew.craft.building.runtime.BuildingWorldData.get(level.getServer()).occupying(level.dimension().location(), cell) != null) return null;
            }
        }
        return defaultBlockState().setValue(SEASON, StardewTimeManager.get().getCurrentSeason());
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP); }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (level instanceof ServerLevel server && server.dimension() == ModDimensions.STARDEW_VALLEY && previous.getBlock() != this
                && !com.stardew.craft.building.runtime.BuildingProtection.transferring()) {
            var owner = FarmInstanceRegistry.get().getOwnerAt(pos);
            var farm = owner == null ? null : FarmInstanceRegistry.get().getFarm(owner);
            if (farm != null && farm.contains(pos)) {
                PetWorldData.get(server.getServer()).bowl(new PetWorldData.Bowl(farm.getInstanceId(), pos.immutable(), style, -1, level.canSeeSky(pos.above())));
                PetBowlBuildings.floor(server, PetBowlBuildings.ensure(server, pos));
            }
        }
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (next.getBlock() != this && level instanceof ServerLevel server && server.dimension() == ModDimensions.STARDEW_VALLEY
                && !com.stardew.craft.building.runtime.BuildingProtection.transferring()) {
            PetWorldData.get(server.getServer()).removeBowl(pos); PetBowlBuildings.removed(server, pos);
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof WateringCanItem ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().getItem() instanceof WateringCanItem || player.getOffhandItem().getItem() instanceof WateringCanItem) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) PetManagement.openBowl(serverPlayer, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    /** Thin bowl rims can make the ray hit the supporting floor instead of the bowl body. */
    public static BlockPos wateringTarget(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof PetBowlBlock) return pos;
        if (level.getBlockState(pos.above()).getBlock() instanceof PetBowlBlock) return pos.above();
        return null;
    }
    public static boolean water(Level level, BlockPos pos) {
        pos = wateringTarget(level, pos);
        if (pos == null) return false;
        if (level instanceof ServerLevel server) {
            PetBowlBuildings.ensure(server, pos);
            level.setBlock(pos, level.getBlockState(pos).setValue(FULL, true), 3);
            if (server.dimension() == ModDimensions.STARDEW_VALLEY) {
                var data = PetWorldData.get(server.getServer()); var bowl = data.bowl(pos);
                if (bowl != null) data.bowl(bowl.watered(StardewTimeManager.get().getAbsoluteDay()));
            }
        }
        return true;
    }
}
