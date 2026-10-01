package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.SupplyCrateBlockEntity;
import com.stardew.craft.farm.SupplyCrateRewards;
import com.stardew.craft.item.tool.HoeItem;
import com.stardew.craft.item.tool.StardewAxeItem;
import com.stardew.craft.item.tool.StardewPickaxeItem;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.stardew.craft.port.net.neoforged.neoforge.common.ItemAbilities;

import java.util.List;

/** A surface-water crate. Appearance is stable; rewards use progression at the moment of breaking. */
public final class SupplyCrateBlock extends Block implements EntityBlock {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 10, 16);

    public SupplyCrateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(VARIANT, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(VARIANT); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SupplyCrateBlockEntity(pos, state); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = defaultBlockState().setValue(VARIANT, context.getLevel().random.nextInt(3));
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var support = level.getBlockState(pos.below());
        var fluid = support.getFluidState();
        return support.getBlock() instanceof LiquidBlock && fluid.is(FluidTags.WATER) && fluid.isSource()
                && level.getFluidState(pos).isEmpty();
    }

    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Losing water removes the decoration without issuing a reward or a reusable reward crate.
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    public static boolean isHeavyHitter(ItemStack tool) {
        return tool.canPerformAction(ItemAbilities.AXE_DIG) || tool.canPerformAction(ItemAbilities.PICKAXE_DIG)
                || tool.canPerformAction(ItemAbilities.HOE_DIG) || tool.canPerformAction(ItemAbilities.SWORD_DIG)
                || tool.getItem() instanceof com.stardew.craft.item.tool.ScytheItem;
    }

    public static int swingsToBreak(ItemStack tool) {
        int tier = tool.getItem() instanceof StardewAxeItem axe ? axe.getTierLevel()
                : tool.getItem() instanceof StardewPickaxeItem pick ? pick.getStardewTier()
                : tool.getItem() instanceof HoeItem hoe ? hoe.getTier().getMaxChargeLevel() : 0;
        return (3 + tier) / (tier + 1);
    }

    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        var tool = player.getMainHandItem();
        // SDV has 3 HP, each heavy-tool swing removes upgradeLevel + 1. Native MC hold-to-break input.
        return isHeavyHitter(tool) ? Math.nextUp(1F / (12 * swingsToBreak(tool))) : 0;
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var breaker = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        var tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (!(breaker instanceof Player player) || player.isCreative() || tool == null || !isHeavyHitter(tool)) return List.of();
        var pos = BlockPos.containing(params.getParameter(LootContextParams.ORIGIN));
        var time = StardewTimeManager.get();
        // As in SDV, the world and tile fix the draw; luck, skin, Fortune and Silk Touch do not change it.
        return SupplyCrateRewards.roll(SupplyCrateRewards.tierForDate(time.getCurrentYear(), time.getCurrentSeason()),
                RandomSource.create(params.getLevel().getSeed() + pos.getX() * 777L + pos.getZ() * 7L));
    }

    public ItemStack variantStack(int variant) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(VARIANT, defaultBlockState().setValue(VARIANT, variant)));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(variant));
        return stack;
    }

    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return variantStack(state.getValue(VARIANT));
    }
}
