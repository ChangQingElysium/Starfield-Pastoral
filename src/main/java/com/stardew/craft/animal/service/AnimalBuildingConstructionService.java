package com.stardew.craft.animal.service;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.model.AnimalBuildingTierDefinition;
import com.stardew.craft.animal.model.AnimalBuildingType;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Atomic payment and immediate activation for Coop/Barn construction. */
public final class AnimalBuildingConstructionService {
    private AnimalBuildingConstructionService() {
    }

    public record StartResult(
            boolean started,
            String buildingId
    ) {
        private static StartResult failed() {
            return new StartResult(false, "");
        }
    }

    /**
     * Tier-one manager items have already been purchased through Robin's
     * blueprint transaction. Upgrades charge their own source-defined cost.
     */
    public static StartResult start(
            ServerPlayer player,
            AnimalBuildingType targetType,
            boolean paymentAlreadyHandled,
            Supplier<String> applyValidatedStructure
    ) {
        AnimalBuildingTierDefinition definition =
                targetType.definition();
        Receipt receipt = paymentAlreadyHandled
                ? Receipt.EMPTY
                : charge(player, definition);
        if (receipt == null) {
            return StartResult.failed();
        }

        String buildingId;
        try {
            buildingId = applyValidatedStructure.get();
        } catch (RuntimeException exception) {
            refund(player, receipt);
            StardewCraft.LOGGER.error(
                    "[ANIMAL_BUILDING] Structure transaction failed; payment refunded",
                    exception);
            return StartResult.failed();
        }
        if (buildingId == null || buildingId.isBlank()) {
            refund(player, receipt);
            return StartResult.failed();
        }
        return new StartResult(true, buildingId);
    }

    private static Receipt charge(
            ServerPlayer player,
            AnimalBuildingTierDefinition definition
    ) {
        if (PlayerStardewDataAPI.getMoney(player)
                < definition.money()) {
            return null;
        }
        ArrayList<SlotDebit> plan = new ArrayList<>();
        for (AnimalBuildingTierDefinition.Material material :
                definition.materials()) {
            Item item = BuiltInRegistries.ITEM.get(material.item());
            if (item == null || item == Items.AIR
                    || !planMaterial(
                            player,
                            item,
                            material.count(),
                            plan)) {
                return null;
            }
        }
        if (definition.money() > 0
                && !PlayerStardewDataAPI.removeMoney(
                        player, definition.money())) {
            return null;
        }
        for (SlotDebit debit : plan) {
            player.getInventory().getItem(debit.slot())
                    .shrink(debit.count());
        }
        return new Receipt(
                definition.money(),
                definition.materials());
    }

    private static boolean planMaterial(
            ServerPlayer player,
            Item item,
            int count,
            List<SlotDebit> plan
    ) {
        int remaining = count;
        for (int slot = 0;
             slot < player.getInventory().getContainerSize()
                     && remaining > 0;
             slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                int reserved = 0;
                for (SlotDebit debit : plan) {
                    if (debit.slot() == slot) {
                        reserved += debit.count();
                    }
                }
                int available = Math.max(
                        0, stack.getCount() - reserved);
                int take = Math.min(remaining, available);
                if (take <= 0) {
                    continue;
                }
                plan.add(new SlotDebit(slot, take));
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    private static void refund(
            ServerPlayer player,
            Receipt receipt
    ) {
        if (receipt.money() > 0) {
            PlayerStardewDataAPI.addMoney(
                    player, receipt.money());
        }
        for (AnimalBuildingTierDefinition.Material material :
                receipt.materials()) {
            Item item = BuiltInRegistries.ITEM.get(material.item());
            int remaining = material.count();
            while (item != null && item != Items.AIR
                    && remaining > 0) {
                int count = Math.min(
                        remaining,
                        item.getDefaultMaxStackSize());
                ItemStack stack = new ItemStack(item, count);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                remaining -= count;
            }
        }
    }

    public static int currentAbsoluteDay() {
        StardewTimeManager time = StardewTimeManager.get();
        return (time.getCurrentYear() - 1) * (28 * 4)
                + time.getCurrentSeason() * 28
                + time.getCurrentDay();
    }

    private record SlotDebit(int slot, int count) {
    }

    private record Receipt(
            int money,
            List<AnimalBuildingTierDefinition.Material> materials
    ) {
        private static final Receipt EMPTY =
                new Receipt(0, List.of());

        private Receipt {
            materials = List.copyOf(materials);
        }
    }
}
