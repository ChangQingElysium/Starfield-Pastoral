package com.stardew.craft.gingerisland;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import java.util.List;

/** Authored ground variants chosen once; save/reload never reshuffles the painted surface. */
public final class GingerIslandSurfaceBlock extends Block {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 5);
    private final int count;
    private final int baseWeight;

    public GingerIslandSurfaceBlock(Properties properties, int count, int baseWeight) {
        super(properties);
        this.count = count;
        this.baseWeight = baseWeight;
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(VARIANT); }
    public int variantCount() { return count; }
    private int normalized(int value) { return value >= 0 && value < count ? value : 0; }
    private Integer fixedChoice(ItemStack stack) {
        String value = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
                .properties().get(VARIANT.getName());
        if (value == null) return null;
        try { return normalized(Integer.parseInt(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }
    public BlockItemStateProperties itemState(ItemStack stack) {
        Integer fixed = fixedChoice(stack);
        return fixed == null ? BlockItemStateProperties.EMPTY
                : BlockItemStateProperties.EMPTY.with(VARIANT, fixed);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Integer fixed = fixedChoice(context.getItemInHand());
        if (fixed != null) return defaultBlockState().setValue(VARIANT, fixed);
        if (context.getLevel().isClientSide || count == 1) return defaultBlockState();
        int roll = context.getLevel().getRandom().nextInt(100);
        int selected = roll < baseWeight ? 0 : 1 + (roll - baseWeight) * (count - 1) / (100 - baseWeight);
        return defaultBlockState().setValue(VARIANT, selected);
    }
    @Override public void setPlacedBy(Level level, net.minecraft.core.BlockPos pos, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // BlockItem applies components again after getStateForPlacement. A shared
        // 0..5 property must not turn a three-surface block into an unused state.
        Integer fixed = fixedChoice(stack);
        BlockState placed = level.getBlockState(pos);
        if (placed.is(this)) {
            int value = fixed == null ? normalized(placed.getValue(VARIANT)) : fixed;
            if (placed.getValue(VARIANT) != value) level.setBlock(pos, placed.setValue(VARIANT, value), 2 | 16);
        }
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        var stack = new ItemStack(this);
        return retainChoice(stack, state);
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // Preserve the loot table's explosion conditions and quantities.
        return super.getDrops(state, params).stream().map(stack -> stack.is(asItem())
                ? retainChoice(stack, state) : stack).toList();
    }
    private ItemStack retainChoice(ItemStack stack, BlockState state) {
        stack.set(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY.with(VARIANT, normalized(state.getValue(VARIANT))));
        return GingerIslandVariantStacks.nameStack(stack);
    }
}
