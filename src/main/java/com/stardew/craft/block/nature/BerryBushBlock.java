package com.stardew.craft.block.nature;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.BushBlockEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BerryBushBlock extends Block implements EntityBlock {
    public enum Part implements StringRepresentable {
        MAIN("main"),
        EXTENSION("extension");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum BerryKind implements StringRepresentable {
        NONE("none"),
        SALMONBERRY("salmonberry"),
        BLACKBERRY("blackberry");

        private final String name;

        BerryKind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<BerryKind> BERRY = EnumProperty.create("berry", BerryKind.class);
    public static final List<int[]> CELL_OFFSETS;
    private static final double BERRY_BLOOM_CHANCE = 0.4D;
    private static final VoxelShape[] OUTLINE_SHAPES = new VoxelShape[18];
    private static final VoxelShape[] COLLISION_SHAPES = new VoxelShape[18];

    static {
        List<int[]> cells = new ArrayList<>(18);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    cells.add(new int[]{dx, dy, dz});
                }
            }
        }
        CELL_OFFSETS = List.copyOf(cells);
        fillShapes();
    }

    public BerryBushBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.MAIN).setValue(BERRY, BerryKind.NONE));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, BERRY);
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state,
                               @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos,
                               @Nonnull CollisionContext context) {
        return shapeForCell(OUTLINE_SHAPES, level, pos, state);
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state,
                                        @Nonnull BlockGetter level,
                                        @Nonnull BlockPos pos,
                                        @Nonnull CollisionContext context) {
        return shapeForCell(COLLISION_SHAPES, level, pos, state);
    }

    @Override
    public VoxelShape getBlockSupportShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public PushReaction getPistonPushReaction(@Nonnull BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public boolean propagatesSkylightDown(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return 0;
    }

    @Override
    public float getShadeBrightness(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return 1.0F;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new BushBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (int[] offset : CELL_OFFSETS) {
            BlockPos cell = pos.offset(offset[0], offset[1], offset[2]);
            if (!level.getWorldBorder().isWithinBounds(cell) || !level.getBlockState(cell).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(PART, Part.MAIN);
    }

    @Override
    public void setPlacedBy(@Nonnull Level level,
                            @Nonnull BlockPos pos,
                            @Nonnull BlockState state,
                            @Nullable LivingEntity placer,
                            @Nonnull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || state.getValue(PART) != Part.MAIN) {
            return;
        }
        BlockState extensionState = state.setValue(PART, Part.EXTENSION);
        for (int[] offset : CELL_OFFSETS) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue;
            }
            level.setBlock(pos.offset(offset[0], offset[1], offset[2]), extensionState, 3);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        return state.getValue(PART) == Part.MAIN || findMainPos(level, pos, state) != null;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected BlockState updateShape(@Nonnull BlockState state,
                                     @Nonnull net.minecraft.core.Direction direction,
                                     @Nonnull BlockState neighborState,
                                     @Nonnull LevelAccessor level,
                                     @Nonnull BlockPos pos,
                                     @Nonnull BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack,
                                              @Nonnull BlockState state,
                                              @Nonnull Level level,
                                              @Nonnull BlockPos pos,
                                              @Nonnull Player player,
                                              @Nonnull InteractionHand hand,
                                              @Nonnull BlockHitResult hit) {
        InteractionResult result = useWithoutItem(state, level, pos, player, hit);
        return result.consumesAction()
            ? ItemInteractionResult.sidedSuccess(level.isClientSide)
            : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state,
                                               @Nonnull Level level,
                                               @Nonnull BlockPos pos,
                                               @Nonnull Player player,
                                               @Nonnull BlockHitResult hit) {
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        StardewTimeManager time = StardewTimeManager.get();
        BerryKind berry = getBloomBerry(time.getCurrentSeason(), time.getCurrentDay());
        if (berry == BerryKind.NONE) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = level.getBlockEntity(mainPos);
        if (!(blockEntity instanceof BushBlockEntity bush) || bush.getLastHarvestAbsoluteDay() == time.getAbsoluteDay()) {
            return InteractionResult.PASS;
        }
        if (!hasBerriesToday(mainPos, berry, time.getAbsoluteDay())) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            harvest(serverLevel, mainPos, serverPlayer, bush, berry, time.getAbsoluteDay());
        }
        return InteractionResult.CONSUME;
    }

    public static boolean hasBerriesToday(BlockPos mainPos, BerryKind berry, int absoluteDay) {
        long seed = ((long) absoluteDay * 0x9E3779B97F4A7C15L)
            ^ mainPos.asLong()
            ^ ((long) berry.ordinal() * 0xBF58476D1CE4E5B9L);
        return net.minecraft.util.RandomSource.create(seed).nextDouble() < BERRY_BLOOM_CHANCE;
    }

    private void harvest(ServerLevel level, BlockPos mainPos, ServerPlayer player, BushBlockEntity bush, BerryKind berry, int absoluteDay) {
        Item item = berryItem(berry);
        if (item == null) {
            return;
        }
        // Bush.cs: berry quantity is 1 + ForagingLevel / 4. Gatherer (13) is not
        // consulted for bush berries; Botanist (16) still controls their quality.
        int count = 1 + PlayerStardewDataAPI.getSkillLevel(player, SkillType.FORAGING) / 4;
        ItemStack stack = new ItemStack(item, count);
        QualityHelper.setQuality(stack, PlayerStardewDataAPI.hasProfession(player, ProfessionType.BOTANIST)
            ? QualityHelper.IRIDIUM
            : QualityHelper.NORMAL);
        ItemStack remaining = stack.copy();
        player.addItem(remaining);
        if (!remaining.isEmpty()) {
            player.drop(remaining, false);
        }
        PlayerStardewDataAPI.addExperience(player, SkillType.FORAGING, count);
        bush.setLastHarvestAbsoluteDay(absoluteDay);
        level.playSound(null, mainPos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
    }

    @Nullable
    private static Item berryItem(BerryKind berry) {
        DeferredItem<Item> entry = switch (berry) {
            case SALMONBERRY -> ModItems.VANILLA_CATEGORY_ITEMS.get("salmonberry");
            case BLACKBERRY -> ModItems.VANILLA_CATEGORY_ITEMS.get("blackberry");
            case NONE -> null;
        };
        return entry == null ? null : entry.get();
    }

    public static BerryKind getBloomBerry(int season, int day) {
        if (season == 0 && day >= 15 && day <= 18) {
            return BerryKind.SALMONBERRY;
        }
        if (season == 2 && day >= 8 && day <= 11) {
            return BerryKind.BLACKBERRY;
        }
        return BerryKind.NONE;
    }

    @Nullable
    public BlockPos findMainPos(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.is(this) && state.getValue(PART) == Part.MAIN) {
            return pos;
        }
        for (int[] offset : CELL_OFFSETS) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue;
            }
            BlockPos candidate = pos.offset(-offset[0], -offset[1], -offset[2]);
            BlockState candidateState = level.getBlockState(candidate);
            if (candidateState.is(this) && candidateState.getValue(PART) == Part.MAIN) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public BlockState playerWillDestroy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull Player player) {
        if (!level.isClientSide) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos != null) {
                if (!player.isCreative()) {
                    popResource(level, mainPos, new ItemStack(ModBlocks.BERRY_BUSH.get()));
                }
                for (int[] offset : CELL_OFFSETS) {
                    BlockPos cell = mainPos.offset(offset[0], offset[1], offset[2]);
                    if (!cell.equals(pos) && level.getBlockState(cell).is(this)) {
                        level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private VoxelShape shapeForCell(VoxelShape[] shapes, BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) {
            return Shapes.empty();
        }
        int index = cellIndex(pos.getX() - mainPos.getX(), pos.getY() - mainPos.getY(), pos.getZ() - mainPos.getZ());
        return index < 0 ? Shapes.empty() : shapes[index];
    }

    private static void fillShapes() {
        setShape(-1, 0, -1, Block.box(7.0D, 0.0D, 3.0D, 15.0D, 13.0D, 15.0D));
        setShape( 0, 0, -1, Block.box(1.0D, 0.0D, 2.0D, 15.0D, 14.0D, 15.0D));
        setShape( 1, 0, -1, Block.box(1.0D, 0.0D, 3.0D, 13.0D, 14.0D, 15.0D));
        setShape(-1, 0,  0, Block.box(4.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D));
        setShape( 0, 0,  0, Block.box(2.0D, 0.0D, 2.0D, 14.0D, 15.0D, 14.0D));
        setShape( 1, 0,  0, Block.box(1.0D, 0.0D, 1.0D, 13.0D, 14.0D, 15.0D));
        setShape(-1, 0,  1, Block.box(8.0D, 0.0D, 1.0D, 15.0D, 12.0D, 9.0D));
        setShape( 0, 0,  1, Block.box(2.0D, 0.0D, 1.0D, 14.0D, 13.0D, 10.0D));
        setShape( 1, 0,  1, Block.box(1.0D, 0.0D, 1.0D, 7.0D, 12.0D, 7.0D));

        setOutline(-1, 1, -1, Block.box(4.0D, 0.0D, 2.0D, 15.0D, 15.0D, 15.0D));
        setOutline( 0, 1, -1, Block.box(1.0D, 0.0D, 2.0D, 15.0D, 15.0D, 15.0D));
        setOutline( 1, 1, -1, Block.box(1.0D, 0.0D, 4.0D, 14.0D, 15.0D, 15.0D));
        setOutline(-1, 1,  0, Block.box(4.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D));
        setOutline( 0, 1,  0, Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D));
        setOutline( 1, 1,  0, Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D));
        setOutline(-1, 1,  1, Block.box(4.0D, 0.0D, 1.0D, 15.0D, 15.0D, 10.0D));
        setOutline( 0, 1,  1, Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 14.0D));
        setOutline( 1, 1,  1, Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 12.0D));
    }

    private static void setShape(int dx, int dy, int dz, VoxelShape shape) {
        int index = cellIndex(dx, dy, dz);
        OUTLINE_SHAPES[index] = shape;
        COLLISION_SHAPES[index] = shape;
    }

    private static void setOutline(int dx, int dy, int dz, VoxelShape shape) {
        int index = cellIndex(dx, dy, dz);
        OUTLINE_SHAPES[index] = shape;
        COLLISION_SHAPES[index] = Shapes.empty();
    }

    private static int cellIndex(int dx, int dy, int dz) {
        if (dx < -1 || dx > 1 || dy < 0 || dy > 1 || dz < -1 || dz > 1) {
            return -1;
        }
        return ((dx + 1) * 3 + (dz + 1)) * 2 + dy;
    }
}
