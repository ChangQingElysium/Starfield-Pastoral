package com.stardew.craft.block.mine;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.mining.OrdinaryMineRuntime;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.secretnote.SecretNoteService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ground debris 313..318: fixed theme, three appearances, independent of counted stones. */
public final class MineGroundWeedsBlock extends Block {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
    private static final VoxelShape[] SHAPES = {
            box(0.5, 0, 0.5, 15.5, 4.75, 14.5),
            box(-0.25, 0, -0.25, 16.25, 4.25, 16.25),
            box(0.25, 0, -0.5, 16.5, 5.75, 15.75)
    };

    public MineGroundWeedsBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(VARIANT, context.getLevel().random.nextInt(3));
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(VARIANT)];
    }

    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && !player.isCreative()) spawnDrops(serverLevel, pos, serverPlayer, true);
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Swing and bomb paths remove first, so overlapping hit volumes cannot award loot twice. */
    public static boolean breakBy(ServerLevel level, BlockPos pos, ServerPlayer player, boolean toolHit) {
        if (!(level.getBlockState(pos).getBlock() instanceof MineGroundWeedsBlock)
                || OrdinaryMineRuntime.isArchitecture(level, pos)) return false;
        if (!level.destroyBlock(pos, false, player)) return false;
        if (player == null || !player.isCreative()) spawnDrops(level, pos, player, toolHit);
        return true;
    }

    private static void spawnDrops(ServerLevel level, BlockPos pos, ServerPlayer player, boolean toolHit) {
        var random = level.random;
        // Object.cutWeed: seed chance is conditional on failing the 50% fiber roll.
        if (random.nextBoolean()) popResource(level, pos, new ItemStack(ModItems.FIBER.get()));
        else {
            double chance = player == null ? 0.05
                    : BookPowerEffects.getWildSeedsChance(PlayerDataManager.getPlayerData(player));
            if (random.nextDouble() < chance) popResource(level, pos, new ItemStack(ModItems.MIXED_SEEDS.get()));
        }
        // Mixed flower seeds, Living Hat and Qi's bean quest are not implemented by this mod.
        if (player != null) {
            ItemStack note = SecretNoteService.tryCreateFromSource(player, random, 0.009F);
            if (!note.isEmpty()) popResource(level, pos, note);
            if (toolHit && StardewEnchantments.has(player.getMainHandItem(), StardewEnchantments.HAYMAKER)) {
                if (random.nextBoolean()) popResource(level, pos, new ItemStack(ModItems.FIBER.get()));
                if (random.nextDouble() < 0.33) popResource(level, pos, new ItemStack(ModItems.HAY.get()));
            }
        }
    }
}
