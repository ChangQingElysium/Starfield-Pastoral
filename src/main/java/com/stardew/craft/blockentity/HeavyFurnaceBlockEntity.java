package com.stardew.craft.blockentity;

import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;


/** Heavy furnace recipes share the normal artisan datapack registry. */
public class HeavyFurnaceBlockEntity extends FurnaceBlockEntity {
    private static int coalPerBatch() {
        return com.stardew.craft.production.MachineProductionData.profile("heavy_furnace").coalPerBatch();
    }

    public HeavyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEAVY_FURNACE.get(), pos, state);
    }

    private static com.stardew.craft.item.artisan.ArtisanRecipeDataManager.Recipe findRecipe(ItemStack stack) {
        return com.stardew.craft.item.artisan.ArtisanRecipeDataManager.getRecipe("heavy_furnace", stack)
            .filter(recipe -> recipe.outputId() != null).orElse(null);
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

        var recipe = findRecipe(stack);
        if (recipe == null || stack.getCount() < recipe.consumeCount() || coalBuffer < coalPerBatch()) {
            return stack;
        }
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, recipe.consumeCount());
        }

        int outputCount = recipe.rollOutputCount(level.random);
        ItemStack output = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(recipe.outputId()), outputCount);
        var plan = prepareProduction(
                stack, output, recipeMinutes(recipe),
                null, true);
        if (plan.isEmpty()) {
            return stack;
        }
        coalBuffer = Math.max(
                0, coalBuffer - coalPerBatch());
        ItemStack inputCopy = stack.copy();
        startWork(
                inputCopy, plan.get(),
                recipe.consumeCount(), null);
        return AutomationStackHelper.remainderAfterInsert(stack, recipe.consumeCount());
    }

    @Override
    @SuppressWarnings("null")
    public InsertResult tryInsertWithResult(ItemStack stack, Player player) {
        if (stack.isEmpty()) return InsertResult.fail();
        if (!product.isEmpty() || readyAtAbsMinute >= 0) return InsertResult.fail();

        var recipe = findRecipe(stack);
        if (recipe == null) return InsertResult.fail();

        if (stack.getCount() < recipe.consumeCount()) {
            return InsertResult.missing(new MissingItemRequirement(stack.getItem(), recipe.consumeCount()));
        }

        if (player == null) return InsertResult.fail();
        // SDV: 3 Coal per batch
        if (!player.isCreative() && countCoal(player) < coalPerBatch()) {
            return InsertResult.missing(new MissingItemRequirement(ModItems.COAL.get(), coalPerBatch()));
        }
        int outputCount = recipe.rollOutputCount(level.random);
        ItemStack output = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(recipe.outputId()), outputCount);
        var plan = prepareProduction(
                stack, output, recipeMinutes(recipe),
                player, false);
        if (plan.isEmpty()) {
            return InsertResult.fail();
        }
        if (!player.isCreative()) {
            for (int i = 0; i < coalPerBatch(); i++) {
                if (!consumeCoal(player)) return InsertResult.fail();
            }
        }

        startWork(
                stack, plan.get(),
                recipe.consumeCount(), player);
        return InsertResult.success();
    }

    /** 统计玩家可用煤数量（手持 + 副手 + 背包），用于一次性扣 3 个的预检。 */
    @SuppressWarnings("null")
    private static int countCoal(Player player) {
        Item coal = ModItems.COAL.get();
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(coal)) total += s.getCount();
        }
        return total;
    }
}
