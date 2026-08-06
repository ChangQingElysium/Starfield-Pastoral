package com.stardew.craft.block.crop;

import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewCropData;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farming.SeasonLocationRules;
import com.stardew.craft.item.tool.WateringCanItem;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.manager.FertilizerManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.stardew.craft.manager.CropGrowthManager;
import javax.annotation.Nonnull;

/**
 * 星露谷作物基类
 * 完全照抄原版Crop.cs的机制
 */
@SuppressWarnings("null")
public abstract class StardewCropBlock extends Block {

    private static final Map<String, Integer> CROP_FARMING_XP = new HashMap<>();
    private static final Map<String, String> CROP_XP_ALIASES = new HashMap<>();

    static {
        // 对齐 数据包/StardewCore/data/stardew/function/farming/xp/crop_xp_table.mcfunction
        CROP_FARMING_XP.put("parsnip", 8);
        CROP_FARMING_XP.put("garlic", 12);
        CROP_FARMING_XP.put("potato", 14);
        CROP_FARMING_XP.put("tulip", 7);
        CROP_FARMING_XP.put("kale", 17);
        CROP_FARMING_XP.put("blue_jazz", 10);
        CROP_FARMING_XP.put("cauliflower", 23);
        CROP_FARMING_XP.put("carrot", 8);
        CROP_FARMING_XP.put("rhubarb", 26);
        CROP_FARMING_XP.put("green_bean", 9);
        CROP_FARMING_XP.put("strawberry", 18);
        CROP_FARMING_XP.put("coffee_bean", 4);

        CROP_FARMING_XP.put("wheat", 6);
        CROP_FARMING_XP.put("radish", 15);
        CROP_FARMING_XP.put("red_cabbage", 28);
        CROP_FARMING_XP.put("poppy", 20);
        CROP_FARMING_XP.put("summer_spangle", 15);
        CROP_FARMING_XP.put("melon", 27);
        CROP_FARMING_XP.put("corn", 10);
        CROP_FARMING_XP.put("tomato", 12);
        CROP_FARMING_XP.put("blueberry", 10);
        CROP_FARMING_XP.put("hot_pepper", 9);
        CROP_FARMING_XP.put("hops", 6);
        CROP_FARMING_XP.put("starfruit", 43);
        CROP_FARMING_XP.put("summer_squash", 9);

        CROP_FARMING_XP.put("eggplant", 12);
        CROP_FARMING_XP.put("broccoli", 13);
        CROP_FARMING_XP.put("bok_choy", 14);
        CROP_FARMING_XP.put("cranberries", 14);
        CROP_FARMING_XP.put("grape", 14);
        CROP_FARMING_XP.put("sunflower", 5);
        CROP_FARMING_XP.put("beet", 16);
        CROP_FARMING_XP.put("amaranth", 21);
        CROP_FARMING_XP.put("artichoke", 22);
        CROP_FARMING_XP.put("yam", 22);
        CROP_FARMING_XP.put("fairy_rose", 29);
        CROP_FARMING_XP.put("pumpkin", 31);

        CROP_FARMING_XP.put("ancient_fruit", 38);
        CROP_FARMING_XP.put("sweet_gem_berry", 64);
        CROP_FARMING_XP.put("cactus_fruit", 14);
        CROP_FARMING_XP.put("taro_root", 16);
        CROP_FARMING_XP.put("pineapple", 30);
        CROP_FARMING_XP.put("powdermelon", 12);
        CROP_FARMING_XP.put("unmilled_rice", 7);

        CROP_XP_ALIASES.put("cranberry", "cranberries");
        CROP_XP_ALIASES.put("powder_melon", "powdermelon");
    }

    public enum HarvestMethod {
        GRAB,
        SCYTHE
    }
    
    // 作物生长阶段 (0-3, 4个阶段)
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    /** Server-synced render marker: the real crop block is a hidden carrier above a Garden Pot. */
    public static final BooleanProperty POTTED = BooleanProperty.create("potted");
    /**
     * 历史属性名保留为 placed_by_player；实际语义是“成品花装饰放置”。
     * 它不等同于作物的播种者，播种者记录在 CropGrowthState.planterUuid。
     */
    public static final BooleanProperty PLACED_BY_PLAYER = BooleanProperty.create("placed_by_player");
    public static final int SEED_PHASE = 0;
    public static final int MAX_AGE = 3;
        private final boolean solidCollision;
        private final VoxelShape[] outlineShapeByAge;
        private volatile VoxelShape[] modelShapeByAge;
        private volatile boolean modelShapeResolved;

    public static boolean isDecorativeFlowerState(BlockState state) {
        return state.hasProperty(PLACED_BY_PLAYER)
                && state.hasProperty(AGE)
                && state.getValue(PLACED_BY_PLAYER)
                && state.getValue(AGE) >= MAX_AGE;
    }

    public static boolean isPlayerPlacedDecorative(ServerLevel level, BlockPos pos, BlockState state) {
        return isDecorativeFlowerState(state)
                && CropGrowthManager.get(level).getState(level, pos) == null;
    }
    
    protected StardewCropBlock(Properties properties) {
        this(properties, false);
    }

    /**
     * @param properties       block properties
     * @param solidCollision   true = 具有碰撞体积（用于藤架/作物架类作物）
     */
    @SuppressWarnings("null")
    protected StardewCropBlock(Properties properties, boolean solidCollision) {
        super(configureProperties(properties, solidCollision));
        this.solidCollision = solidCollision;
        this.outlineShapeByAge = buildOutlineShapes(getOutlineHeightsPxByAge(), getOutlineWidthsPxByAge(), solidCollision);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0).setValue(POTTED, false));
    }

    private static Properties configureProperties(Properties properties, boolean solidCollision) {
        Properties p = properties.instabreak().noOcclusion();
        // 普通作物：不阻挡行走；藤架/作物架：需要碰撞。
        if (!solidCollision) {
            p = p.noCollission();
        }
        return p;
    }

    /**
     * 选框/轮廓高度（单位：像素，0..16），按 AGE=0..3。
     * 默认给一个更明显的高度变化，避免“鼠标边框很穿帮”。
     */
    protected int[] getOutlineHeightsPxByAge() {
        return new int[]{ 2, 6, 12, 16 };
    }

    /**
     * 选框/碰撞宽度（单位：像素，0..16），按 AGE=0..3。
     * 默认返回 null 表示使用满格 16。
     */
    protected int[] getOutlineWidthsPxByAge() {
        return null;
    }

    private static VoxelShape[] buildOutlineShapes(int[] heightsPx, int[] widthsPx, boolean solidCollision) {
        VoxelShape[] shapes = new VoxelShape[MAX_AGE + 1];
        for (int age = 0; age <= MAX_AGE; age++) {
            int h = 16;
            if (!solidCollision) {
                if (heightsPx != null && age < heightsPx.length) {
                    h = heightsPx[age];
                } else {
                    h = 16;
                }
            }
            h = Math.max(0, Math.min(16, h));
            int w = 16;
            if (widthsPx != null && age < widthsPx.length) {
                w = widthsPx[age];
            }
            w = Math.max(0, Math.min(16, w));

            double halfGap = (16.0 - w) / 2.0;
            double min = halfGap;
            double max = 16.0 - halfGap;
            shapes[age] = Block.box(min, 0.0, min, max, h, max);
        }
        return shapes;
    }

    private static int getFarmingLevel(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return PlayerStardewDataAPI.getSkillLevel(serverPlayer, SkillType.FARMING);
        }
        return 0;
    }

    /**
     * 获取肥料等级（预留接口）
     */
    @SuppressWarnings("null")
    private static int getFertilizerLevel(ServerLevel level, BlockPos pos) {
        BlockPos farmPos = pos.below();
        if (!(level.getBlockState(farmPos).getBlock() instanceof net.minecraft.world.level.block.FarmBlock)) {
            return 0;
        }
        FertilizerManager manager = FertilizerManager.get(level);
        return manager.getQualityLevel(level, farmPos);
    }

    @SuppressWarnings("null")
    private static float getSpeedBoost(ServerLevel level, BlockPos pos) {
        BlockPos farmPos = pos.below();
        if (!(level.getBlockState(farmPos).getBlock() instanceof net.minecraft.world.level.block.FarmBlock)) {
            return 0f;
        }
        FertilizerManager manager = FertilizerManager.get(level);
        return manager.getSpeedBoost(level, farmPos);
    }

    private static float getAgriculturistSpeedBoost(ServerLevel level, CropGrowthManager.CropGrowthState growthState) {
        if (growthState == null || growthState.planterUuid == null) {
            return 0f;
        }

        if (PlayerDataManager.getPlayerData(growthState.planterUuid).hasProfession(ProfessionType.AGRICULTURIST)) {
            // Stardew Agriculturist: crop growth speed +10%
            return 0.1f;
        }
        return 0f;
    }

    protected float getAdditionalSpeedBoost(ServerLevel level, BlockPos pos, CropGrowthManager.CropGrowthState growthState) {
        return 0f;
    }

    private float getTotalSpeedBoost(ServerLevel level, BlockPos pos, CropGrowthManager.CropGrowthState growthState) {
        return getSpeedBoost(level, pos)
                + getAgriculturistSpeedBoost(level, growthState)
                + getAdditionalSpeedBoost(level, pos, growthState);
    }

    private static int[] applySpeedGroToPhaseDays(int[] phaseDays, float speedBoost) {
        if (phaseDays == null || phaseDays.length == 0 || speedBoost <= 0f) {
            return phaseDays;
        }

    // 1:1 参考 Stardew Valley: HoeDirt.applySpeedIncreases
    // daysToRemove = ceil(totalDaysOfCropGrowth * speedIncrease)
    // 然后最多 3 次 pass，逐段 -1（第 0 段至少保留 1 天；其余段允许减到 0；99999 不处理）。
        int totalDaysOfCropGrowth = 0;
        for (int d : phaseDays) {
            totalDaysOfCropGrowth += Math.max(0, d);
        }

        int daysToRemove = (int) Math.ceil(totalDaysOfCropGrowth * (double) speedBoost);
        if (daysToRemove <= 0) {
            return phaseDays;
        }

        int[] result = phaseDays.clone();
        int tries = 0;
        while (daysToRemove > 0 && tries < 3) {
            for (int j = 0; j < result.length; j++) {
                int d = result[j];
                if (d <= 0) {
                    continue;
                }
                if (d == 99999) {
                    continue;
                }
                // 第 0 阶段至少保留1天
                if (j == 0 && d <= 1) {
                    continue;
                }

                result[j] = d - 1;
                daysToRemove--;
                if (daysToRemove <= 0) {
                    break;
                }
            }
            tries++;
        }

        return result;
    }

    /**
     * Stardew 的 phaseDays 末尾有一个 99999 哨兵阶段（可收割阶段）。
     */
    private static int[] withHarvestSentinel(int[] growthPhaseDays) {
        if (growthPhaseDays == null || growthPhaseDays.length == 0) {
            return new int[]{99999};
        }
        int[] result = java.util.Arrays.copyOf(growthPhaseDays, growthPhaseDays.length + 1);
        result[growthPhaseDays.length] = 99999;
        return result;
    }

    /**
     * 该作物“完全成熟可收割”的判定。
     * 注意：我们的 AGE 只有 0-3，但 Wiki 的 phaseDays 是 4 段；
     * 因此 AGE==3 只是进入最后阶段，仍需等待 phaseDays[3] 天才算成熟。
     */
    @SuppressWarnings("null")
    protected boolean isMature(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(AGE) < MAX_AGE) {
            return false;
        }

        CropGrowthManager.CropGrowthState growthState = CropGrowthManager.get(level).getState(level, pos);
        if (growthState == null) {
            // 兼容旧状态缺失：成熟贴图直接允许收割，避免“永远不可收割”卡死。
            return true;
        }

        int[] phaseDays = withHarvestSentinel(applySpeedGroToPhaseDays(getPhaseDays(), getTotalSpeedBoost(level, pos, growthState)));
        int lastPhase = Math.max(0, phaseDays.length - 1);

        if (growthState.phase < lastPhase) {
            growthState.phase = lastPhase;
            if (!growthState.regrowing) {
                growthState.dayInPhase = 0;
            }
        }

        if (growthState.regrowing) {
            return growthState.phase >= lastPhase && growthState.dayInPhase <= 0;
        }
        return growthState.phase >= lastPhase;
    }

    public boolean isReadyForFarmComputer(ServerLevel level, BlockPos pos, BlockState state) {
        return isMature(level, pos, state);
    }

    /** Read-only probe shared by contextual UI and the actual right-click rule. */
    public boolean isReadyForRightClickHarvest(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        BlockPos rootPos = resolveMultiBlockRootPos(level, pos, state);
        BlockState rootState = level.getBlockState(rootPos);
        return rootState.getBlock() == this
                && getHarvestMethod() == HarvestMethod.GRAB
                && isMature(level, rootPos, rootState);
    }
    
    @Override
    protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, POTTED);
        addExtraProperties(builder);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(POTTED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    /**
     * 允许子类添加额外的方块状态属性（例如颜色变体）。
     */
    protected void addExtraProperties(StateDefinition.Builder<Block, BlockState> builder) {
        // no-op by default
    }
    
    /**
     * 检查是否可以存活（只能种在耕地上）
     */
    @SuppressWarnings("null")
    @Override
    protected boolean canSurvive(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") net.minecraft.world.level.LevelReader level, @SuppressWarnings("null") BlockPos pos) {
        BlockPos belowPos = pos.below();
        @SuppressWarnings("null")
        BlockState belowState = level.getBlockState(belowPos);
        @Nonnull Block block = belowState.getBlock();

        // 双高作物：UPPER 只要求下方是同类作物的 LOWER
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return belowState.getBlock() == this
                    && belowState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && belowState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
        }

        // 玩家放置的成品花：允许种在泥土类自然地表上
        if (isDecorativeFlowerState(state)) {
            if (isNaturalSoil(belowState)) {
                return true;
            }
        }

        // 检查是否是耕地
        if (block instanceof net.minecraft.world.level.block.FarmBlock) {
            return true;
        }

        // 兼容其他模组的耕地
        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString().toLowerCase();
        return blockId.contains("farmland");
    }

    /**
     * 是否是“泥土类”自然地表：草方块、泥土、砂土、灰化土、菌丝、黄土、苔藓、湿泥、根泥以及耕地。
     */
    public static boolean isNaturalSoil(BlockState belowState) {
        Block block = belowState.getBlock();
        if (block instanceof net.minecraft.world.level.block.FarmBlock) {
            return true;
        }
        if (belowState.is(net.minecraft.tags.BlockTags.DIRT)) {
            return true;
        }
        if (belowState.is(Blocks.GRASS_BLOCK)
                || blockStateIsAny(belowState,
                        Blocks.DIRT,
                        Blocks.COARSE_DIRT,
                        Blocks.ROOTED_DIRT,
                        Blocks.PODZOL,
                        Blocks.MYCELIUM,
                        Blocks.MOSS_BLOCK,
                        Blocks.MUD,
                        Blocks.MUDDY_MANGROVE_ROOTS)) {
            return true;
        }
        String id = BuiltInRegistries.BLOCK.getKey(block).toString().toLowerCase();
        return id.contains("farmland") || id.contains("dirt") || id.contains("grass_block") || id.contains("mycelium");
    }

    private static boolean blockStateIsAny(BlockState state, Block... blocks) {
        for (Block b : blocks) {
            if (state.is(b)) {
                return true;
            }
        }
        return false;
    }
    
    @SuppressWarnings("null")
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(AGE) > 0 && entity instanceof Player player && player.isSprinting()) {
            double factor = player instanceof ServerPlayer serverPlayer
                    ? BookPowerEffects.getGrassSpeedFactor(PlayerDataManager.getPlayerData(serverPlayer))
                    : level.isClientSide ? BookPowerEffects.getClientGrassSpeedFactor() : BookPowerEffects.getGrassSpeedFactor(false);
            entity.makeStuckInBlock(state, new Vec3(factor, 1.0D, factor));
        }
        super.entityInside(state, level, pos, entity);
    }

    @SuppressWarnings("null")
    @Override
    protected VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        if (com.stardew.craft.block.utility.GardenPotBlock.isPottedPlant(level, pos, state)) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        VoxelShape[] shapes = getResolvedShapeByAge(state);
        return shapes[state.getValue(AGE)];
    }

    @SuppressWarnings("null")
    @Override
    protected VoxelShape getCollisionShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        if (com.stardew.craft.block.utility.GardenPotBlock.isPottedPlant(level, pos, state)) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        // 普通作物：无碰撞；藤架/作物架：给满格碰撞，避免穿帮与可穿过。
        if (!solidCollision) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        VoxelShape[] shapes = getResolvedShapeByAge(state);
        return shapes[state.getValue(AGE)];
    }

    @SuppressWarnings("null")
    private VoxelShape[] getResolvedShapeByAge(BlockState state) {
        if (modelShapeResolved) {
            return modelShapeByAge != null ? modelShapeByAge : outlineShapeByAge;
        }

        synchronized (this) {
            if (modelShapeResolved) {
                return modelShapeByAge != null ? modelShapeByAge : outlineShapeByAge;
            }

            @SuppressWarnings("null")
            Block block = state.getBlock();
            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
            VoxelShape[] resolved = new VoxelShape[MAX_AGE + 1];
            for (int age = 0; age <= MAX_AGE; age++) {
                // Try simple key first, then compound keys for double-height crops
                String modelId = ModelVoxelShapeCache.variantModel(blockId, "age=" + age);
                if (modelId == null || modelId.isBlank()) {
                    modelId = ModelVoxelShapeCache.variantModel(blockId, "age=" + age + ",half=lower");
                }
                if (modelId == null || modelId.isBlank()) {
                    resolved = null;
                    break;
                }
                resolved[age] = ModelVoxelShapeCache.shape(modelId);
            }

            modelShapeByAge = resolved;
            modelShapeResolved = true;
            return modelShapeByAge != null ? modelShapeByAge : outlineShapeByAge;
        }
    }

    /**
     * 更新方块形态（检查由于邻居更新导致的存活状态）
     * 解决下方方块被破坏后作物悬空不消失的问题
     */
    @SuppressWarnings("null")
    @Override
    protected BlockState updateShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") net.minecraft.core.Direction direction, @SuppressWarnings("null") BlockState neighborState, @SuppressWarnings("null") net.minecraft.world.level.LevelAccessor level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockPos neighborPos) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
    
    @SuppressWarnings("null")
    @Override
    public ItemStack getCloneItemStack(@SuppressWarnings("null") net.minecraft.world.level.LevelReader level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
        // 返回作物物品作为图标（防风草）
        return new ItemStack(this.getCropItem().get());
    }

    /**
     * 右键收割成熟作物
     */
    @SuppressWarnings("null")
    @Override
    protected InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") BlockHitResult hitResult) {
        return tryRightClickHarvest(state, level, pos, player);
    }

    @SuppressWarnings("null")
    @Override
    protected ItemInteractionResult useItemOn(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") BlockState state,
                                              @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos,
                                              @SuppressWarnings("null") Player player, @SuppressWarnings("null") InteractionHand hand,
                                              @SuppressWarnings("null") BlockHitResult hitResult) {
        if (stack.getItem() instanceof WateringCanItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        InteractionResult result = tryRightClickHarvest(state, level, pos, player);
        if (result == InteractionResult.PASS) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (result == InteractionResult.FAIL) {
            return ItemInteractionResult.FAIL;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private InteractionResult tryRightClickHarvest(BlockState state, Level level, BlockPos pos, Player player) {
        BlockPos interactionPos = resolveMultiBlockRootPos(level, pos, state);
        BlockState interactionState = level.getBlockState(interactionPos);
        if (interactionState.getBlock() != this) {
            return InteractionResult.PASS;
        }

        if (getHarvestMethod() == HarvestMethod.SCYTHE) {
            // 镰刀作物不允许右键收割。
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }

        @SuppressWarnings("null")
        int currentAge = interactionState.getValue(AGE);
        
        // 只有成熟时才能收割
        if (currentAge < MAX_AGE) {
            // 不成熟的作物，不做任何处理，直接消费事件防止其他交互
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }

        // AGE==3 但仍在最后阶段生长中（还没到 phaseDays[3]），也视为未成熟
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            if (!isMature(serverLevel, interactionPos, interactionState)) {
                return InteractionResult.CONSUME;
            }
        }
        
        // 成熟的作物，进行收割
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            // 农场保护：在别人农场上无权收割
            if (player instanceof net.minecraft.server.level.ServerPlayer sp
                && level.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                && !sp.isCreative()) {
            boolean canHarvest = com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(serverLevel, interactionPos)
                ? com.stardew.craft.event.FarmAreaProtectionEvents.canModifyGreenhouseAt(sp, serverLevel, interactionPos)
                : com.stardew.craft.event.FarmAreaProtectionEvents.canModifyAt(sp, interactionPos);
            if (!canHarvest) {
                sp.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("stardewcraft.farm.build_farm_only"), true);
                return InteractionResult.CONSUME;
            }
            }
            harvest(serverLevel, interactionPos, interactionState, player);
        }
        
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 玩家左键破坏方块前触发
     * 如果是成熟作物，触发掉落逻辑
     */
    @SuppressWarnings("null")
    @Override
    public BlockState playerWillDestroy(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Player player) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockPos interactionPos = resolveMultiBlockRootPos(level, pos, state);
            BlockState interactionState = level.getBlockState(interactionPos);
            boolean mature = interactionState.getBlock() == this
                    && interactionState.getValue(AGE) == MAX_AGE
                    && isMature(serverLevel, interactionPos, interactionState);
            boolean canGrabHarvest = getHarvestMethod() == HarvestMethod.GRAB;
            boolean decorativePlacedFlower = isPlayerPlacedDecorative(serverLevel, interactionPos, interactionState);
            // 如果不是创造模式，生成掉落物并给予经验
            if (mature && canGrabHarvest && !player.isCreative() && player instanceof ServerPlayer serverPlayer) {
                int farmingLevel = getFarmingLevel(player);
                int fertilizerLevel = getFertilizerLevel(serverLevel, interactionPos);
                spawnHarvestDrops(serverLevel, interactionPos, interactionState, level.getRandom(), fertilizerLevel, farmingLevel);
                if (!decorativePlacedFlower) {
                    spawnHarvestSideProducts(serverLevel, interactionPos, interactionState, level.getRandom(), player, fertilizerLevel, farmingLevel);
                }

                if (!decorativePlacedFlower) {
                    int farmingExp = getHarvestFarmingExperience(serverLevel, interactionPos, interactionState);
                    if (farmingExp > 0) {
                        PlayerStardewDataAPI.addExperience(serverPlayer, SkillType.FARMING, farmingExp);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * 作物的所有掉落（成熟作物 + 经验）已由 playerWillDestroy 中的 spawnHarvestDrops 完整处理，
     * loot table 必须被覆盖为空，否则会出现：
     *   - 未成熟时左键也掉种子（不应该）
     *   - 成熟时既掉 loot table 的无品质作物，又掉 spawnHarvestDrops 的有品质作物，造成 2 个不可叠加的副本
     */
    @SuppressWarnings("null")
    @Override
    public java.util.List<ItemStack> getDrops(BlockState state,
                                              net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return java.util.Collections.emptyList();
    }

    /**
     * 收割作物（照抄原版Crop.harvest逻辑）
     */
    @SuppressWarnings("null")
    protected void harvest(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        BlockPos harvestPos = resolveMultiBlockRootPos(level, pos, state);

        // 重新获取当前最新的state（防止使用旧state）
        BlockState currentState = level.getBlockState(harvestPos);
        if (!(currentState.getBlock() instanceof StardewCropBlock)) {
            return; // 方块已经改变，停止收割
        }
        
        int farmingLevel = getFarmingLevel(player);
        int fertilizerLevel = getFertilizerLevel(level, harvestPos);
        
        // 生成并掉落物品
        spawnHarvestDrops(level, harvestPos, currentState, level.getRandom(), fertilizerLevel, farmingLevel);

        // 副产物（如小麦掉落干草）
        boolean decorativePlacedFlower = isPlayerPlacedDecorative(level, harvestPos, currentState);
        if (!decorativePlacedFlower) {
            spawnHarvestSideProducts(level, harvestPos, currentState, level.getRandom(), player, fertilizerLevel, farmingLevel);
        }

        if (!decorativePlacedFlower && player instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative()) {
            int farmingExp = getHarvestFarmingExperience(level, harvestPos, currentState);
            if (farmingExp > 0) {
                PlayerStardewDataAPI.addExperience(serverPlayer, SkillType.FARMING, farmingExp);
            }
        }
        
        // 播放收割音效（原版harvest）
        level.playSound(null, harvestPos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        
        // 检查是否可以再生长
        if (canRegrow()) {
            // 重新生长：重置为regrow阶段
            int regrowAge = getRegrowAge();
            BlockState regrowState = currentState.setValue(AGE, regrowAge);
            level.setBlock(harvestPos, regrowState, 3);
            syncMultiBlockPartnerFromRoot(level, harvestPos, regrowState);

            // 标记为“再生长倒计时”状态，让每日生长按 regrowDays 计算
            CropGrowthManager.CropGrowthState growthState = CropGrowthManager.get(level).getState(level, harvestPos);
            int[] phaseDays = withHarvestSentinel(applySpeedGroToPhaseDays(getPhaseDays(), getTotalSpeedBoost(level, harvestPos, growthState)));
            int lastPhase = Math.max(0, phaseDays.length - 1);
            int regrowDays = Math.max(1, getRegrowDays());
            CropGrowthManager.get(level).setRegrowing(level, harvestPos, true, regrowDays, lastPhase);
        } else {
            // 一次性作物：移除方块
            level.setBlock(harvestPos, getPostHarvestState(level, harvestPos, currentState), 3);
        }
    }

    protected BlockState getPostHarvestState(ServerLevel level, BlockPos pos, BlockState harvestedState) {
        return Blocks.AIR.defaultBlockState();
    }

    static int getHarvestFarmingExperience(Level level, BlockPos pos, BlockState state) {
        StardewCropData apiData = StardewAgricultureDataApi.crop(level, pos, state);
        if (apiData != null) {
            return apiData.farmingExperience();
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.endsWith("_crop")) {
            path = path.substring(0, path.length() - 5);
        }
        String key = CROP_XP_ALIASES.getOrDefault(path, path);
        return CROP_FARMING_XP.getOrDefault(key, 0);
    }

    /**
     * 供工具（例如镰刀）触发的收割入口。
     * 返回是否实际发生了收割。
     */
    @SuppressWarnings("null")
    public boolean tryHarvestByTool(ServerLevel level, BlockPos pos, BlockState state, Player player, boolean forceScytheHarvest) {
        BlockPos harvestPos = resolveMultiBlockRootPos(level, pos, state);
        BlockState harvestState = level.getBlockState(harvestPos);
        if (harvestState.getBlock() != this) {
            return false;
        }

        if (harvestState.getValue(AGE) < MAX_AGE) {
            return false;
        }
        if (!isMature(level, harvestPos, harvestState)) {
            return false;
        }
        if (getHarvestMethod() != HarvestMethod.SCYTHE && !forceScytheHarvest) {
            return false;
        }
        harvest(level, harvestPos, harvestState, player);
        return true;
    }

    /** Server-authoritative hand-harvest entry used by the public crop runtime bridge. */
    @SuppressWarnings("null")
    public boolean tryHarvestByHand(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            Player player
    ) {
        BlockPos harvestPos = resolveMultiBlockRootPos(level, pos, state);
        BlockState harvestState = level.getBlockState(harvestPos);
        if (harvestState.getBlock() != this
                || getHarvestMethod() != HarvestMethod.GRAB
                || harvestState.getValue(AGE) < MAX_AGE
                || !isMature(level, harvestPos, harvestState)) {
            return false;
        }
        harvest(level, harvestPos, harvestState, player);
        return true;
    }

    /**
     * Harvest entry used by a Junimo Hut. Unlike player harvesting, every result is
     * inserted into the hut and no farming experience is awarded.
     *
     * @return the primary harvested stack, or {@link ItemStack#EMPTY} if nothing was harvested.
     */
    public ItemStack tryHarvestByJunimo(ServerLevel level, BlockPos pos, BlockState state,
                                        int farmingLevel, Consumer<ItemStack> output) {
        BlockPos harvestPos = resolveMultiBlockRootPos(level, pos, state);
        BlockState harvestState = level.getBlockState(harvestPos);
        if (harvestState.getBlock() != this
                || harvestState.getValue(AGE) < MAX_AGE
                || !isMature(level, harvestPos, harvestState)
                || isPlayerPlacedDecorative(level, harvestPos, harvestState)) {
            return ItemStack.EMPTY;
        }

        RandomSource random = level.getRandom();
        int fertilizerLevel = getFertilizerLevel(level, harvestPos);
        int quality = getHarvestQuality(random, fertilizerLevel, farmingLevel);
        ItemStack harvested = getHarvestItem(quality);
        harvested.setCount(getHarvestCount(random, farmingLevel));
        harvested = applyHarvestItemCustomization(harvested, harvestState);
        output.accept(harvested.copy());
        collectJunimoHarvestSideProducts(level, harvestPos, harvestState, random,
                fertilizerLevel, farmingLevel, output);

        level.playSound(null, harvestPos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (canRegrow()) {
            int regrowAge = getRegrowAge();
            BlockState regrowState = harvestState.setValue(AGE, regrowAge);
            level.setBlock(harvestPos, regrowState, 3);
            syncMultiBlockPartnerFromRoot(level, harvestPos, regrowState);

            CropGrowthManager.CropGrowthState growthState = CropGrowthManager.get(level).getState(level, harvestPos);
            int[] phaseDays = withHarvestSentinel(applySpeedGroToPhaseDays(
                    getPhaseDays(), getTotalSpeedBoost(level, harvestPos, growthState)));
            int lastPhase = Math.max(0, phaseDays.length - 1);
            CropGrowthManager.get(level).setRegrowing(level, harvestPos, true,
                    Math.max(1, getRegrowDays()), lastPhase);
        } else {
            level.setBlock(harvestPos, getPostHarvestState(level, harvestPos, harvestState), 3);
        }
        return harvested;
    }

    /** Read-only output hint used by pull-based automation before it performs a real harvest. */
    public final ItemStack getAutomationHarvestPreview(BlockState state) {
        ItemStack preview = getHarvestItem(QualityHelper.NORMAL);
        if (preview.isEmpty()) {
            return ItemStack.EMPTY;
        }
        preview = applyHarvestItemCustomization(preview, state);
        preview.setCount(Math.max(1, getHarvestMinStack()));
        return preview;
    }

    /** Side products routed to a Junimo Hut instead of the world/player silo. */
    protected void collectJunimoHarvestSideProducts(ServerLevel level, BlockPos pos, BlockState state,
                                                     RandomSource random, int fertilizerLevel,
                                                     int farmingLevel, Consumer<ItemStack> output) {
    }

    protected HarvestMethod getHarvestMethod() {
        return HarvestMethod.GRAB;
    }

    public boolean requiresScytheHarvest() {
        return getHarvestMethod() == HarvestMethod.SCYTHE;
    }

    /**
     * 生成并掉落收获物品（供右键收割和左键破坏共用）
     */
    @SuppressWarnings("null")
    protected void spawnHarvestDrops(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, int fertilizerLevel, int farmingLevel) {
        boolean decorativePlacedFlower = isPlayerPlacedDecorative(level, pos, state);
        int quality = decorativePlacedFlower ? QualityHelper.NORMAL : getHarvestQuality(random, fertilizerLevel, farmingLevel);

        // 获取收获数量（玩家放置的花只掉 1 个普通品质，且不受农业等级影响，方便与正常收割的普通品质堆叠）
        int numToHarvest = decorativePlacedFlower ? 1 : getHarvestCount(random, farmingLevel);

        // 创建作物物品
        ItemStack harvestItem = getHarvestItem(quality);
        harvestItem.setCount(numToHarvest);
        harvestItem = applyHarvestItemCustomization(harvestItem, state);

        // 掉落物品
        popResource(level, pos, harvestItem);
    }

    /**
     * 收割时的副产物（默认无）。例如小麦在 SDV 中有 40% 概率额外掉落 1 份干草。
     * 默认空实现，子类可按需重写。
     */
    @SuppressWarnings({"null", "unused"})
    protected void spawnHarvestSideProducts(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                            Player player, int fertilizerLevel, int farmingLevel) {
    }
    
    /**
     * 获取收获数量（基于farming等级和随机）
     */
    protected int getHarvestCount(RandomSource random, int farmingLevel) {
        // 1:1 对齐 Stardew Crop.harvest 的栈数与额外产出逻辑。
        int minStack = getHarvestMinStack();
        int maxStack = Math.max(minStack, getHarvestMaxStack());

        float maxIncreasePerFarmingLevel = getHarvestMaxIncreasePerFarmingLevel();
        if (maxIncreasePerFarmingLevel > 0f) {
            maxStack += (int) (farmingLevel * maxIncreasePerFarmingLevel);
        }

        int count = 1;
        if (minStack > 1 || maxStack > 1) {
            count = random.nextInt(maxStack - minStack + 1) + minStack;
        }

        double extraHarvestChance = getExtraHarvestChance();
        while (extraHarvestChance > 0.0 && random.nextDouble() < Math.min(0.9, extraHarvestChance)) {
            count++;
        }

        return count;
    }

    /**
     * 原版 CropData.HarvestMinStack
     */
    protected int getHarvestMinStack() {
        return 1;
    }

    /**
     * 原版 CropData.HarvestMaxStack
     */
    protected int getHarvestMaxStack() {
        return 1;
    }

    /**
     * 原版 CropData.HarvestMaxIncreasePerFarmingLevel
     */
    protected float getHarvestMaxIncreasePerFarmingLevel() {
        return 0f;
    }

    /**
     * 原版 CropData.ExtraHarvestChance
     */
    protected double getExtraHarvestChance() {
        return 0.0;
    }
    
    /**
     * 是否可以再生长（如番茄、蓝莓等）
     */
    protected abstract boolean canRegrow();
    
    /**
     * 再生长时的年龄阶段
     */
    protected abstract int getRegrowAge();

    /**
     * 再生长天数（如番茄、蓝莓等）。一次性作物返回 0。
     */
    protected int getRegrowDays() {
        return 0;
    }

    /**
     * 给集成/显示层（如 Jade）用的公开访问器。
     * 这些方法只是转调 protected 抽象方法，避免破坏现有子类 override 签名。
     */
    public final int[] getPhaseDaysForDisplay() {
        return getPhaseDays();
    }

    public final boolean canRegrowForDisplay() {
        return canRegrow();
    }

    public final int getRegrowDaysForDisplay() {
        return getRegrowDays();
    }
    
    /**
     * 获取收获的物品（子类实现）
     */
    protected abstract ItemStack getHarvestItem(int quality);

    /**
     * 子类可在此根据方块状态调整收获物（例如写入颜色变体）。
     */
    protected ItemStack applyHarvestItemCustomization(ItemStack stack, BlockState state) {
        return stack;
    }

    /**
     * 花卉颜色变体 + 品质组合的模型编码。
     * custom_model_data = 100 + quality * 10 + color
     */
    @SuppressWarnings("null")
    protected static void setFlowerVariantModelData(ItemStack stack, int color) {
        int quality = QualityHelper.getQuality(stack);
        int cmd = 100 + (quality * 10) + Math.max(0, color);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(cmd));
    }
    
    /**
     * 获取作物显示名称（用于Jade等信息显示模组）
     */
    public abstract String getCropDisplayNameKey();
    
    /**
     * 获取作物种子物品
     */
    protected abstract Supplier<Item> getSeedsItem();

    public final boolean isSeedItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(getSeedsItem().get());
    }

    public final boolean canPlantAt(Level level, BlockPos pos) {
        return SeasonLocationRules.seedsIgnoreSeasonsHere(level, pos) || isInSeason(level);
    }
    
    /**
     * 获取作物产物物品
     */
    protected abstract Supplier<Item> getCropItem();
    
    /**
     * 判断当前是否在合适的季节
     */
    protected abstract boolean isInSeason(Level level);
    
    /**
     * 获取phaseDays数组（每个阶段的天数）
     * 原版: phaseDays = {1, 1, 1, 1} 表示4个阶段各需1天
     */
    protected abstract int[] getPhaseDays();

    /**
     * 颜色变体属性（仅用于有多色成熟外观的作物）。
     */
    protected IntegerProperty getColorVariantProperty() {
        return null;
    }

    /**
     * 颜色变体数量（用于随机）。默认 1 表示无变体。
     */
    protected int getColorVariantCount() {
        return 1;
    }

    protected BlockState applyMatureVariant(ServerLevel level, BlockPos pos, BlockState state) {
        IntegerProperty color = getColorVariantProperty();
        int count = getColorVariantCount();
        if (color != null && count > 1) {
            int variant = level.getRandom().nextInt(count);
            return state.setValue(color, variant);
        }
        return state;
    }
    
    /**
     * 获取作物品质（基于随机+农业等级+肥料）
     * 完全照抄原版的harvest()方法的品质计算
     */
    protected int getHarvestQuality(RandomSource random, int fertilizerLevel, int farmingLevel) {
        // 原版代码:
        // double chanceForGoldQuality = 0.2 * (Game1.player.FarmingLevel / 10.0) + 0.2 * fertilizerQualityLevel * ((Game1.player.FarmingLevel + 2.0) / 12.0) + 0.01;
        // double chanceForSilverQuality = Math.Min(0.75, chanceForGoldQuality * 2.0);
        
        double chanceForGoldQuality = 0.2 * (farmingLevel / 10.0) + 0.2 * fertilizerLevel * ((farmingLevel + 2.0) / 12.0) + 0.01;
        double chanceForSilverQuality = Math.min(0.75, chanceForGoldQuality * 2.0);
        
        int quality = QualityHelper.NORMAL;
        
        // 铱星肥料 (fertilizerLevel >= 3) 有机会铱星
        if (fertilizerLevel >= 3 && random.nextDouble() < chanceForGoldQuality / 2.0) {
            quality = QualityHelper.IRIDIUM;
        }
        // 金星
        else if (random.nextDouble() < chanceForGoldQuality) {
            quality = QualityHelper.GOLD;
        }
        // 银星
        else if (random.nextDouble() < chanceForSilverQuality || fertilizerLevel >= 3) {
            quality = QualityHelper.SILVER;
        }
        
        return quality;
    }
    
    /**
     * 每日生长（照抄原版newDay()方法）
     * @param level 世界
     * @param pos 位置
     * @param state 当前方块状态
     * @param watered 是否被浇水了
     */
    @SuppressWarnings("null")
    public void growCropOneDay(ServerLevel level, BlockPos pos, BlockState state, boolean watered, CropGrowthManager.CropGrowthState growthState) {
        // 检查是否在星露谷维度
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        
        // 检查季节（对齐 Stardew 的 SeedsIgnoreSeasonsHere 语义）。
        if (!SeasonLocationRules.seedsIgnoreSeasonsHere(level, pos) && !isInSeason(level)) {
            // 原版: Kill() -> replace with Dead Crop
            level.setBlock(pos, com.stardew.craft.block.ModBlocks.DEAD_CROP.get().defaultBlockState()
                    .setValue(com.stardew.craft.block.crop.DeadCropBlock.VARIANT, level.random.nextInt(4)), 3);
            return;
        }
        
        if (growthState == null) {
            // 理论上 manager 会传进来，这里兜底。
            return;
        }

        float speedBoost = getTotalSpeedBoost(level, pos, growthState);
        int[] phaseDays = withHarvestSentinel(applySpeedGroToPhaseDays(getPhaseDays(), speedBoost));
        if (phaseDays == null || phaseDays.length <= 0) {
            return;
        }

        // 检查是否被浇水
        if (!watered) {
            return; // 没浇水就不生长
        }

        int lastPhase = Math.max(0, phaseDays.length - 1);
        int preHarvestPhase = Math.max(0, lastPhase - 1);
        int currentAge = state.getValue(AGE);

        // 兼容旧状态：如果只存在贴图年龄，映射到对应 phase。
        if (growthState.phase == 0 && growthState.dayInPhase == 0 && !growthState.regrowing && currentAge > 0) {
            growthState.phase = currentAge >= MAX_AGE ? lastPhase : Math.min(currentAge, preHarvestPhase);
        }

        if (growthState.regrowing && canRegrow()) {
            // 再生倒计时：dayInPhase 表示 remaining days，<=0 即成熟可收割。
            growthState.dayInPhase--;
            if (growthState.dayInPhase <= 0) {
                growthState.regrowing = false;
                growthState.dayInPhase = 0;
                growthState.phase = lastPhase;
                BlockState matureState = applyMatureVariant(level, pos, level.getBlockState(pos)).setValue(AGE, MAX_AGE);
                level.setBlock(pos, matureState, 2);
                syncMultiBlockPartnerFromRoot(level, pos, matureState);
            } else {
                int regrowAge = getRegrowAge();
                if (regrowAge < 0) regrowAge = 0;
                if (regrowAge > MAX_AGE - 1) regrowAge = MAX_AGE - 1;
                if (level.getBlockState(pos).getValue(AGE) != regrowAge) {
                    BlockState regrowState = level.getBlockState(pos).setValue(AGE, regrowAge);
                    level.setBlock(pos, regrowState, 2);
                    syncMultiBlockPartnerFromRoot(level, pos, regrowState);
                }
            }
            return;
        }

        int phase = Math.max(0, Math.min(growthState.phase, lastPhase));
        int daysThisPhase = phaseDays[Math.min(phase, phaseDays.length - 1)];

        if (phase >= lastPhase) {
            growthState.phase = lastPhase;
            growthState.dayInPhase = 0;
            if (level.getBlockState(pos).getValue(AGE) != MAX_AGE) {
                BlockState matureState = applyMatureVariant(level, pos, level.getBlockState(pos)).setValue(AGE, MAX_AGE);
                level.setBlock(pos, matureState, 2);
            }
            return;
        }

        if (daysThisPhase > 0) {
            growthState.dayInPhase++;
        }

        if (daysThisPhase <= 0 || growthState.dayInPhase >= daysThisPhase) {
            phase++;
            growthState.phase = phase;
            growthState.dayInPhase = 0;
            while (growthState.phase < lastPhase && phaseDays[growthState.phase] <= 0) {
                growthState.phase++;
            }
            phase = growthState.phase;
        }

        if (phase >= lastPhase) {
            if (level.getBlockState(pos).getValue(AGE) != MAX_AGE) {
                BlockState matureState = applyMatureVariant(level, pos, level.getBlockState(pos)).setValue(AGE, MAX_AGE);
                level.setBlock(pos, matureState, 2);
                syncMultiBlockPartnerFromRoot(level, pos, matureState);
            }
        } else {
            int targetAge = Math.min(phase, MAX_AGE - 1);
            if (level.getBlockState(pos).getValue(AGE) != targetAge) {
                BlockState nextState = level.getBlockState(pos).setValue(AGE, targetAge);
                level.setBlock(pos, nextState, 2);
                syncMultiBlockPartnerFromRoot(level, pos, nextState);
            }
        }
    }

    @SuppressWarnings("null")
    protected BlockPos resolveMultiBlockRootPos(Level level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return pos;
        }
        if (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER) {
            return pos;
        }

        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (belowState.getBlock() == this
                && belowState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && belowState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            return belowPos;
        }
        return pos;
    }

    @SuppressWarnings("null")
    protected void syncMultiBlockPartnerFromRoot(Level level, BlockPos rootPos, BlockState rootState) {
        if (!rootState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return;
        }

        BlockPos lowerPos = rootPos;
        BlockState lowerState = rootState;
        if (rootState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            lowerPos = rootPos.below();
            lowerState = level.getBlockState(lowerPos);
        }

        if (lowerState.getBlock() != this
                || !lowerState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || lowerState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
            return;
        }

        BlockPos upperPos = lowerPos.above();
        BlockState upperState = level.getBlockState(upperPos);
        BlockState expectedUpper = lowerState.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);

        if (upperState.getBlock() != this
                || !upperState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || upperState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER) {
            level.setBlock(upperPos, expectedUpper, 3);
            return;
        }

        if (!upperState.equals(expectedUpper)) {
            level.setBlock(upperPos, expectedUpper, 3);
        }
    }
    
    /**
     * 检查能否存活
     */
    // @Override
    // protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
    //    // Removed unsafe onPlace check. Rely on canSurvive and updateShape.
    // }
    
    @SuppressWarnings("null")
    @Override
    protected void onPlace(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState oldState, boolean isMoving) {
        boolean pottedHere = com.stardew.craft.block.utility.GardenPotBlock.isPottedPlant(level, pos, state);
        if (!level.isClientSide && state.getValue(POTTED) != pottedHere) {
            state = state.setValue(POTTED, pottedHere);
            level.setBlock(pos, state, Block.UPDATE_ALL);

            // Double-height crop subclasses create their upper part before delegating here.
            // Mark that carrier invisible in the same update so both halves change atomically.
            if (pottedHere
                    && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                BlockPos upperPos = pos.above();
                BlockState upper = level.getBlockState(upperPos);
                if (upper.getBlock() == state.getBlock() && upper.hasProperty(POTTED)) {
                    level.setBlock(upperPos, upper.setValue(POTTED, true), Block.UPDATE_ALL);
                }
            }
        }
        if (!state.is(oldState.getBlock())) {
            // 新放置 (或者方块类型改变)，注册到管理器；玩家手放成品花只是装饰，不进入生长表。
            if (level instanceof ServerLevel serverLevel) {
                if (isDecorativeFlowerState(state)) {
                    super.onPlace(state, level, pos, oldState, isMoving);
                    return;
                }
                UUID planterUuid = null;
                net.minecraft.world.entity.player.Player nearest = serverLevel.getNearestPlayer(
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D,
                    6.0D,
                    false
                );
                if (nearest instanceof ServerPlayer serverPlayer) {
                    planterUuid = serverPlayer.getUUID();
                }
                com.stardew.craft.manager.CropGrowthManager.get(serverLevel).addCrop(serverLevel, pos, planterUuid);
            }
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }
    
    @SuppressWarnings("null")
    @Override
    protected void onRemove(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // 被移除 (或者变成别的方块了)，从管理器移除
            if (level instanceof ServerLevel serverLevel) {
                com.stardew.craft.manager.CropGrowthManager.get(serverLevel).removeCrop(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
