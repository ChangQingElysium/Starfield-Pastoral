package com.stardew.craft.workbench;

import com.stardew.craft.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Server-owned workstation access and payment; client requests never specify costs or materials. */
public final class TemplateWorkbenchCrafting {
    private static final Map<ServerPlayer, GlobalPos> OPEN_WORKBENCHES = new WeakHashMap<>();

    private TemplateWorkbenchCrafting() {}

    public static void open(ServerPlayer player, BlockPos pos) {
        OPEN_WORKBENCHES.put(player, GlobalPos.of(player.level().dimension(), pos.immutable()));
    }

    public static int craft(ServerPlayer player, ResourceLocation target, int requested) {
        GlobalPos station = OPEN_WORKBENCHES.get(player);
        if (station == null || station.dimension() != player.level().dimension()
                || player.distanceToSqr(station.pos().getCenter()) > 64
                || !player.level().getBlockState(station.pos()).is(ModBlocks.TEMPLATE_WORKBENCH.get())) {
            OPEN_WORKBENCHES.remove(player);
            return 0;
        }
        WorkbenchEntry recipe = WorkbenchRecipeManager.findEntry(WorkbenchType.TEMPLATE, target);
        if (recipe == null || requested <= 0) return 0;
        int output = craftInventory(player.getInventory(), recipe, requested, stack -> {
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        });
        player.containerMenu.broadcastChanges();
        return output;
    }

    static int craftInventory(Container inventory, WorkbenchEntry recipe, int requested, Consumer<ItemStack> deliver) {
        if (requested <= 0) return 0;
        Item material = BuiltInRegistries.ITEM.get(new ResourceLocation(recipe.inputItemId(WorkbenchType.TEMPLATE)));
        Item item = BuiltInRegistries.ITEM.get(recipe.itemId());
        if (material == Items.AIR || item == Items.AIR) return 0;
        int batches = Math.min(Math.min(requested, 999), count(inventory, material) / recipe.cost());
        // Cap batches before charging, so no paid output is discarded by the delivery limit.
        batches = Math.min(batches, item.getDefaultMaxStackSize() * 36 / recipe.outputCount());
        if (batches <= 0) return 0;
        int remaining = batches * recipe.cost();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(material)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        int output = batches * recipe.outputCount();
        for (int left = output; left > 0;) {
            int size = Math.min(left, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, size);
            deliver.accept(stack);
            left -= size;
        }
        inventory.setChanged();
        return output;
    }

    private static int count(Container inventory, Item item) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
}
