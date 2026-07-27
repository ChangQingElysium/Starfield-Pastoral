package com.stardew.craft.blockentity;

import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Supplier;

/**
 * Heavy Furnace — 严格按 SDV Content/Data/Machines.json `(BC)HeavyFurnace` 实现：
 *
 *  recipe              input        output         minutes
 *  ─────────────────── ──────────── ─────────────  ───────
 *  CopperOre           25× ore      5..6× bar       30
 *  IronOre             25× ore      5..6× bar      120
 *  GoldOre             25× ore      5..6× bar      300
 *  IridiumOre          25× ore      5..6× bar      480
 *  RadioactiveOre      25× ore      5..6× bar      560
 *  Quartz              5× quartz    5..6× refined   90
 *  FireQuartz          5× fq        15..20 refined  90
 *  Bouquet             1× bouquet   1× wilted       10
 *
 *  AdditionalConsumedItems: 3× Coal (382) per batch
 */
public class HeavyFurnaceBlockEntity extends FurnaceBlockEntity {

    private static final int COAL_PER_BATCH = 3;

    /** 一条配方：要求 inputCount 个对应输入，产 minOut..maxOut 个输出，需要 minutes 时间。 */
    private record Recipe(
        Supplier<Item> input, int inputCount,
        Supplier<Item> output, int minOut, int maxOut,
        int minutes
    ) {}

    private static final List<Recipe> RECIPES = List.of(
        // Copper Ore → Copper Bar
        new Recipe(() -> ModItems.COPPER_ORE.get(),       25, () -> ModItems.COPPER_BAR.get(),     5,  6,  30),
        // Iron Ore → Iron Bar
        new Recipe(() -> ModItems.IRON_ORE.get(),         25, () -> ModItems.IRON_BAR.get(),       5,  6, 120),
        // Gold Ore → Gold Bar
        new Recipe(() -> ModItems.GOLD_ORE.get(),         25, () -> ModItems.GOLD_BAR.get(),       5,  6, 300),
        // Iridium Ore → Iridium Bar
        new Recipe(() -> ModItems.IRIDIUM_ORE.get(),      25, () -> ModItems.IRIDIUM_BAR.get(),    5,  6, 480),
        // Radioactive Ore → Radioactive Bar
        new Recipe(() -> ModItems.RADIOACTIVE_ORE.get(),  25, () -> ModItems.RADIOACTIVE_BAR.get(),5,  6, 560),
        // Quartz → Refined Quartz
        new Recipe(() -> ModItems.QUARTZ.get(),            5, () -> ModItems.REFINED_QUARTZ.get(), 5,  6,  90),
        // Fire Quartz → Refined Quartz (more)
        new Recipe(() -> ModItems.FIRE_QUARTZ.get(),       5, () -> ModItems.REFINED_QUARTZ.get(),15, 20,  90),
        // Bouquet → Wilted Bouquet
        new Recipe(() -> ModItems.BOUQUET.get(),           1, () -> ModItems.WILTED_BOUQUET.get(), 1,  1,  10)
    );

    public HeavyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEAVY_FURNACE.get(), pos, state);
    }

    private static Recipe findRecipe(ItemStack stack) {
        for (Recipe r : RECIPES) {
            if (stack.is(r.input.get())) return r;
        }
        return null;
    }

    @Override
    @SuppressWarnings("null")
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0) {
            return stack;
        }
        if (isCoalStack(stack)) {
            return insertCoal(stack, simulate);
        }

        Recipe recipe = findRecipe(stack);
        if (recipe == null || stack.getCount() < recipe.inputCount || coalBuffer < COAL_PER_BATCH) {
            return stack;
        }
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, recipe.inputCount);
        }

        int outputRange = recipe.maxOut - recipe.minOut + 1;
        int outputCount = recipe.minOut + (level != null ? level.getRandom().nextInt(outputRange) : 0);
        ItemStack output = new ItemStack(recipe.output.get(), outputCount);
        var plan = prepareProduction(
                stack, output, recipe.minutes,
                null, true);
        if (plan.isEmpty()) {
            return stack;
        }
        coalBuffer = Math.max(
                0, coalBuffer - COAL_PER_BATCH);
        ItemStack inputCopy = stack.copy();
        startWork(
                inputCopy, plan.get(),
                recipe.inputCount, null);
        return AutomationStackHelper.remainderAfterInsert(stack, recipe.inputCount);
    }

    @Override
    @SuppressWarnings("null")
    public InsertResult tryInsertWithResult(ItemStack stack, Player player) {
        if (stack.isEmpty()) return InsertResult.fail();
        if (!product.isEmpty() || readyAtAbsMinute >= 0) return InsertResult.fail();

        Recipe recipe = findRecipe(stack);
        if (recipe == null) return InsertResult.fail();

        if (stack.getCount() < recipe.inputCount) {
            return InsertResult.missing(new MissingItemRequirement(stack.getItem(), recipe.inputCount));
        }

        if (player == null) return InsertResult.fail();
        // SDV: 3 Coal per batch
        if (!player.isCreative() && countCoal(player) < COAL_PER_BATCH) {
            return InsertResult.missing(new MissingItemRequirement(ModItems.COAL.get(), COAL_PER_BATCH));
        }
        int outputCount = recipe.minOut + level.getRandom()
                .nextInt(recipe.maxOut - recipe.minOut + 1);
        ItemStack output = new ItemStack(
                recipe.output.get(), outputCount);
        var plan = prepareProduction(
                stack, output, recipe.minutes,
                player, false);
        if (plan.isEmpty()) {
            return InsertResult.fail();
        }
        if (!player.isCreative()) {
            for (int i = 0; i < COAL_PER_BATCH; i++) {
                if (!consumeCoal(player)) return InsertResult.fail();
            }
        }

        startWork(
                stack, plan.get(),
                recipe.inputCount, player);
        return InsertResult.success();
    }

    /** 统计玩家可用煤数量（手持 + 副手 + 背包），用于一次性扣 3 个的预检。 */
    @SuppressWarnings("null")
    private static int countCoal(Player player) {
        Item coal = ModItems.COAL.get();
        int total = 0;
        ItemStack main = player.getMainHandItem();
        if (main.is(coal)) total += main.getCount();
        ItemStack off = player.getOffhandItem();
        if (off.is(coal)) total += off.getCount();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(coal)) total += s.getCount();
        }
        return total;
    }
}
