package com.stardew.craft.block.mine;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Mine coal cache: a bag lying in one cell and its strap in the next. */
public final class MineCoalBackpackBlock extends MapDecorStaticBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty DESERT = BooleanProperty.create("desert");
    public static final BooleanProperty DARK = BooleanProperty.create("dark");
    private static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            new ResourceLocation(StardewCraft.MODID, "gameplay/mine_coal_backpack"));

    public MineCoalBackpackBlock(Properties properties) {
        super(properties, "block/mine/backpack/frost_full");
        registerDefaultState(defaultBlockState().setValue(OPEN, false).setValue(DARK, false).setValue(DESERT, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN, DARK, DESERT);
    }

    @Override protected VoxelShape canonicalShape() {
        return Shapes.or(Block.box(1, 0, 2, 15, 9, 14), Block.box(15, 0, 4, 29, 3, 13));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        var saved = context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        Boolean dark = saved.get(DARK);
        if (dark == null) dark = MineBuildingTheme.FROST_DARK.rank(context.getLevel().getBlockState(context.getClickedPos().below())) >= 0;
        Boolean desert = saved.get(DESERT);
        if (desert == null) {
            var theme = MineBuildingTheme.forPlacement(context);
            desert = theme == MineBuildingTheme.DESERT || theme == MineBuildingTheme.DESERT_DARK;
        }
        Boolean open = saved.get(OPEN);
        return state.setValue(DARK, dark).setValue(DESERT, desert).setValue(OPEN, Boolean.TRUE.equals(open));
    }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        var stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(DARK, state).with(DESERT, state).with(OPEN, state));
        return stack;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        BlockPos anchor = findMainPos(level, pos, state);
        if (anchor == null) return InteractionResult.PASS;
        // Re-read the anchor: clicks on either cell and stale hit states share one claim.
        BlockState current = level.getBlockState(anchor);
        if (!current.is(this) || current.getValue(OPEN)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        var params = new LootParams.Builder(server)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(anchor))
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck()).create(LootContextParamSets.CHEST);
        var drops = server.getServer().reloadableRegistries().getLootTable(LOOT).getRandomItems(params);
        BlockState empty = current.setValue(OPEN, true);
        if (!level.setBlock(anchor, empty, Block.UPDATE_ALL)) return InteractionResult.PASS;
        BlockPos strap = anchor.relative(current.getValue(FACING).getClockWise());
        BlockState other = level.getBlockState(strap);
        if (other.is(this) && other.getValue(PART) == Part.EXTENSION && anchor.equals(findMainPos(level, strap, other))) {
            level.setBlock(strap, empty.setValue(PART, Part.EXTENSION), Block.UPDATE_ALL);
        }
        com.stardew.craft.mining.OrdinaryMineRuntime.coalCacheOpened(server, anchor);
        for (ItemStack stack : drops) Block.popResource(level, anchor, stack);
        level.playSound(null, anchor, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS, .7F, .85F);
        return InteractionResult.CONSUME;
    }

    // Structures can place the strap before the body when rotated west or south.
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                               LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, this, 1);
        return state;
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!super.canSurvive(state, level, pos)) runWithDropsSuppressed(() -> level.removeBlock(pos, false));
    }

    @Override protected java.util.List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return java.util.List.of();
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        runWithDropsSuppressed(() -> super.onRemove(state, level, pos, next, moving));
    }
}
