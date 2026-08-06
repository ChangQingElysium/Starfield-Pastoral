package com.stardew.craft.block.nature;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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

import java.util.function.Supplier;

/**
 * Generic forage block using cross model. Drops a specified item when broken.
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
public class ForageBlock extends BushBlock {
    public static final MapCodec<ForageBlock> CODEC = simpleCodec(ForageBlock::new);

    /** Foraging XP granted per forage pickup (SDV: 7) */
    private static final int FORAGE_XP = 7;

    private Supplier<ItemStack> dropSupplier;
    private int allowedSeasonMask = 0;

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
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
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (allowedSeasonMask == 0 || !level.canSeeSky(pos)) {
            return;
        }
        int currentSeason = StardewTimeManager.get().getCurrentSeason();
        if ((allowedSeasonMask & (1 << currentSeason)) == 0) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * Right-click to pick up forage (SDV: click to collect).
     * Same quality / gatherer / XP logic as breaking.
     */
    @SuppressWarnings("null")
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        return pickForage(level, pos, player);
    }

    @SuppressWarnings("null")
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
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
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer && !player.isCreative()) {
            harvestForage(serverLevel, pos, serverPlayer);
        }
        return super.playerWillDestroy(level, pos, state, player);
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

        // ---- Drop the item ----
        popResource(level, pos, drop);

        // ---- Gatherer profession: 20% chance double harvest ----
        if (PlayerStardewDataAPI.hasProfession(player, ProfessionType.GATHERER)) {
            if (level.getRandom().nextFloat() < 0.2f) {
                ItemStack bonus = dropSupplier.get();
                QualityHelper.setQuality(bonus, quality);
                popResource(level, pos, bonus);
            }
        }

        // ---- Foraging XP ----
        PlayerStardewDataAPI.addExperience(player, SkillType.FORAGING, FORAGE_XP);
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
