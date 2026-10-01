package com.stardew.craft.block.nature;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.player.ForagingProfessionRules;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.block.utility.GardenPotBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.AABB;

import java.util.function.Supplier;
import com.stardew.craft.port.PortBlockInteraction;

/**
 * Forage block with a model-sized selection box for static 3D resources.
 * Used for world-generated forage items (wild_horseradish, daffodil, etc.).
 *
 * <p>SDV parity:
 * <ul>
 *   <li>Quality determined by Foraging level (level/30 gold, level/15 silver)</li>
 *   <li>Botanist profession → always iridium quality</li>
 *   <li>Gatherer profession → 20% chance double harvest</li>
 *   <li>7 Foraging XP per pickup</li>
 * </ul>
 */
public class ForageBlock extends BushBlock implements PortBlockInteraction {

    /** Foraging XP granted per forage pickup (SDV: 7) */
    private static final int FORAGE_XP = 7;

    private Supplier<ItemStack> dropSupplier;
    private int allowedSeasonMask = 0;

    public ForageBlock(Properties properties) {
        super(properties);
    }

    public ForageBlock setDrop(Supplier<ItemStack> drop) {
        this.dropSupplier = drop;
        return this;
    }

    public ForageBlock setAllowedSeasons(int... seasons) {
        int mask = 0;
        for (int season : seasons) {
            if (season >= 0 && season <= 3) {
                mask |= 1 << season;
            }
        }
        this.allowedSeasonMask = mask;
        return this;
    }

    public ItemStack getAutomationDrop() {
        return dropSupplier == null ? ItemStack.EMPTY : dropSupplier.get();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        String model = ModelVoxelShapeCache.variantModel(BuiltInRegistries.BLOCK.getKey(this).toString(), "");
        if (model != null && model.startsWith("stardewcraft:block/crop3d/forage_")) {
            AABB bounds = ModelVoxelShapeCache.requiredShape(model).bounds();
            BlockPos below = pos.below();
            BlockState support = level.getBlockState(below);
            if (support.getBlock() instanceof GardenPotBlock) {
                // Match GardenPotBlockEntityRenderer's whole-model scale and 11px soil plane.
                double scale = 13.0 / 16;
                bounds = new AABB((bounds.minX - .5) * scale + .5, bounds.minY * scale - 5.0 / 16,
                        (bounds.minZ - .5) * scale + .5, (bounds.maxX - .5) * scale + .5,
                        bounds.maxY * scale - 5.0 / 16, (bounds.maxZ - .5) * scale + .5);
            } else if (support.getBlock() instanceof FarmBlock) {
                VoxelShape floor = support.getCollisionShape(level, below);
                if (!floor.isEmpty()) bounds = bounds.move(0, floor.max(Direction.Axis.Y) - 1, 0);
            }
            // GardenPlanterPlantShapeMixin applies the planter's offset once at BlockState level.
            return Shapes.create(bounds);
        }
        if (com.stardew.craft.block.utility.GardenPotBlock.isPottedPlant(level, pos, state)) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        return super.getShape(state, level, pos, context);
    }

    @SuppressWarnings("null")
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getBlock() instanceof FarmBlock
                || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.isFaceSturdy(level, pos, Direction.UP);
    }

    @SuppressWarnings("null")
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (allowedSeasonMask == 0 || !level.canSeeSky(pos)) {
            return;
        }
        int currentSeason = StardewTimeManager.get().getCurrentSeason();
        if ((allowedSeasonMask & (1 << currentSeason)) == 0) {
            level.removeBlock(pos, false);
        }
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    /**
     * Right-click to pick up forage (SDV: click to collect).
     * Same quality / gatherer / XP logic as breaking.
     */
    @SuppressWarnings("null")
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        return pickForage(level, pos, player);
    }

    @SuppressWarnings("null")
    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        pickForage(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private InteractionResult pickForage(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer) {
            harvestForage(serverLevel, pos, serverPlayer);
            level.removeBlock(pos, false);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @SuppressWarnings("null")
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer && !player.isCreative()) {
            harvestForage(serverLevel, pos, serverPlayer);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Shared harvest logic for both right-click pickup and block breaking.
     */
    @SuppressWarnings("null")
    private void harvestForage(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (dropSupplier == null) return;

        ItemStack drop = dropSupplier.get();

        // ---- Quality (SDV: GetHarvestSpawnedObjectQuality) ----
        int quality = determineQuality(player, level.getRandom());
        QualityHelper.setQuality(drop, quality);

        int harvestCount = ForagingProfessionRules.applyGatherer(
                player, 1, level.getRandom().nextDouble());
        for (int i = 0; i < harvestCount; i++) {
            ItemStack harvested = i == 0 ? drop : dropSupplier.get();
            QualityHelper.setQuality(harvested, quality);
            popResource(level, pos, harvested);
        }

        // ---- Foraging XP ----
        PlayerStardewDataAPI.addExperience(player, SkillType.FORAGING, FORAGE_XP * harvestCount);
    }

    /**
     * SDV parity: GameLocation.GetHarvestSpawnedObjectQuality(isForage=true)
     * <ul>
     *   <li>Botanist (profession 16): always iridium</li>
     *   <li>level/30 chance → gold</li>
     *   <li>level/15 chance → silver</li>
     *   <li>else → normal</li>
     * </ul>
     */
    private static int determineQuality(ServerPlayer player, net.minecraft.util.RandomSource random) {
        // Botanist: always iridium
        if (PlayerStardewDataAPI.hasProfession(player, ProfessionType.BOTANIST)) {
            return QualityHelper.IRIDIUM;
        }

        int foragingLevel = PlayerStardewDataAPI.getSkillLevel(player, SkillType.FORAGING);

        // Gold: foragingLevel / 30 chance
        if (random.nextFloat() < foragingLevel / 30.0f) {
            return QualityHelper.GOLD;
        }
        // Silver: foragingLevel / 15 chance
        if (random.nextFloat() < foragingLevel / 15.0f) {
            return QualityHelper.SILVER;
        }
        return QualityHelper.NORMAL;
    }
}
