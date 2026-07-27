package com.stardew.craft.api.v1.internal.economy;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.economy.StardewCost;
import com.stardew.craft.api.v1.economy.StardewCostEntry;
import com.stardew.craft.api.v1.economy.StardewCurrencyCost;
import com.stardew.craft.api.v1.economy.StardewItemCost;
import com.stardew.craft.api.v1.economy.StardewPaymentReceipt;
import com.stardew.craft.api.v1.economy.StardewPaymentResult;
import com.stardew.craft.api.v1.requirement.StardewRequirement;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import com.stardew.craft.api.v1.requirement.StardewRequirementTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;

/** Composite payment implementation shared by shops, festivals and addon actions. */
public final class StardewCostTransactionService {
    private StardewCostTransactionService() {
    }

    public static StardewPaymentResult pay(
            ServerPlayer player,
            StardewCost cost
    ) {
        if (player == null || cost == null) {
            return StardewPaymentResult.failed(
                    "invalid_payment_context");
        }
        NormalizedCost normalized;
        try {
            normalized = normalize(cost);
        } catch (ArithmeticException exception) {
            return StardewPaymentResult.failed(
                    "cost_overflow");
        }

        ArrayList<PaymentOperation> operations =
                new ArrayList<>();
        for (Map.Entry<ResourceLocation, Long> entry
                : normalized.currencies().entrySet()) {
            ResourceLocation currencyId = entry.getKey();
            long amount = entry.getValue();
            operations.add(new PaymentOperation() {
                @Override
                public boolean canPay() {
                    OptionalLong balance =
                            StardewCurrencyRegistry.balance(
                                    currencyId, player);
                    return balance.isPresent()
                            && balance.getAsLong() >= amount;
                }

                @Override
                public boolean withdraw() {
                    return StardewCurrencyRegistry.withdraw(
                            currencyId, player, amount);
                }

                @Override
                public boolean refund() {
                    return StardewCurrencyRegistry.deposit(
                            currencyId, player, amount);
                }

                @Override
                public String failureReason() {
                    return "currency:" + currencyId;
                }
            });
        }
        for (Map.Entry<ResourceLocation, Integer> entry
                : normalized.items().entrySet()) {
            ResourceLocation itemId = entry.getKey();
            int amount = entry.getValue();
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                return StardewPaymentResult.failed(
                        "unknown_item:" + itemId);
            }
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null || item == Items.AIR) {
                return StardewPaymentResult.failed(
                        "unknown_item:" + itemId);
            }
            operations.add(new InventoryPaymentOperation(
                    player, itemId, item, amount));
        }

        LedgerResult result = executeOperations(operations);
        if (!result.success()) {
            return StardewPaymentResult.failed(
                    result.failureReason());
        }
        return StardewPaymentResult.paid(
                new StardewPaymentReceipt(result.refund()));
    }

    public static StardewRequirementReport requirements(
            ServerPlayer player,
            StardewCost cost
    ) {
        if (player == null || cost == null) {
            return errorReport("invalid_payment_context");
        }
        NormalizedCost normalized;
        try {
            normalized = normalize(cost);
        } catch (ArithmeticException exception) {
            return errorReport("cost_overflow");
        }
        ArrayList<StardewRequirement> requirements =
                new ArrayList<>();
        for (Map.Entry<ResourceLocation, Long> entry
                : normalized.currencies().entrySet()) {
            ResourceLocation currencyId = entry.getKey();
            long required = entry.getValue();
            OptionalLong balance =
                    StardewCurrencyRegistry.balance(
                            currencyId, player);
            Component display = StardewCurrencyRegistry
                    .definitions().stream()
                    .filter(currency ->
                            currency.id().equals(currencyId))
                    .findFirst()
                    .map(currency -> currency.displayName())
                    .orElseGet(() -> Component.literal(
                            currencyId.toString()));
            requirements.add(new StardewRequirement(
                    StardewRequirementTypes.COST_CURRENCY,
                    balance.isEmpty()
                            ? StardewRequirement.State.ERROR
                            : balance.getAsLong() >= required
                                    ? StardewRequirement.State.SATISFIED
                                    : StardewRequirement.State.UNSATISFIED,
                    Component.translatable(
                            "stardewcraft.requirement.cost.currency",
                            required, display),
                    true));
        }
        for (Map.Entry<ResourceLocation, Integer> entry
                : normalized.items().entrySet()) {
            ResourceLocation itemId = entry.getKey();
            int required = entry.getValue();
            Item item = BuiltInRegistries.ITEM.containsKey(itemId)
                    ? BuiltInRegistries.ITEM.get(itemId) : null;
            boolean known = item != null && item != Items.AIR;
            requirements.add(new StardewRequirement(
                    StardewRequirementTypes.COST_ITEM,
                    !known
                            ? StardewRequirement.State.ERROR
                            : player.getInventory().countItem(item)
                                    >= required
                                    ? StardewRequirement.State.SATISFIED
                                    : StardewRequirement.State.UNSATISFIED,
                    Component.translatable(
                            "stardewcraft.requirement.cost.item",
                            required,
                            known ? item.getDescription()
                                    : itemId.toString()),
                    true));
        }
        return new StardewRequirementReport(requirements);
    }

    private static StardewRequirementReport errorReport(
            String reason
    ) {
        return new StardewRequirementReport(List.of(
                new StardewRequirement(
                        StardewRequirementTypes.COST_VALID,
                        StardewRequirement.State.ERROR,
                        Component.translatable(
                                "stardewcraft.requirement.cost.invalid",
                                reason),
                        true)));
    }

    static LedgerResult executeOperations(
            List<? extends PaymentOperation> operations
    ) {
        for (PaymentOperation operation : operations) {
            try {
                if (!operation.canPay()) {
                    return LedgerResult.failed(
                            "insufficient_" + operation.failureReason());
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Payment preflight failed for {}",
                        operation.failureReason(), exception);
                return LedgerResult.failed(
                        "failed_" + operation.failureReason());
            }
        }

        ArrayList<PaymentOperation> paid =
                new ArrayList<>(operations.size());
        for (PaymentOperation operation : operations) {
            boolean withdrawn;
            try {
                withdrawn = operation.withdraw();
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Payment withdrawal failed for {}",
                        operation.failureReason(), exception);
                withdrawn = false;
            }
            if (!withdrawn) {
                refundOperations(paid);
                return LedgerResult.failed(
                        "failed_" + operation.failureReason());
            }
            paid.add(operation);
        }

        List<PaymentOperation> immutablePaid =
                List.copyOf(paid);
        return LedgerResult.paid(
                () -> refundOperations(immutablePaid));
    }

    private static boolean refundOperations(
            List<? extends PaymentOperation> operations
    ) {
        ArrayList<? extends PaymentOperation> reversed =
                new ArrayList<>(operations);
        Collections.reverse(reversed);
        boolean complete = true;
        for (PaymentOperation operation : reversed) {
            try {
                complete &= operation.refund();
            } catch (RuntimeException exception) {
                complete = false;
                StardewCraft.LOGGER.error(
                        "Payment refund failed for {}",
                        operation.failureReason(), exception);
            }
        }
        return complete;
    }

    private static NormalizedCost normalize(StardewCost cost) {
        LinkedHashMap<ResourceLocation, Long> currencies =
                new LinkedHashMap<>();
        LinkedHashMap<ResourceLocation, Integer> items =
                new LinkedHashMap<>();
        for (StardewCostEntry entry : cost.entries()) {
            if (entry instanceof StardewCurrencyCost currency) {
                currencies.merge(
                        currency.currency(),
                        currency.amount(),
                        Math::addExact);
            } else if (entry instanceof StardewItemCost item) {
                items.merge(
                        item.item(),
                        Math.toIntExact(item.amount()),
                        Math::addExact);
            }
        }
        return new NormalizedCost(
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(currencies)),
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(items)));
    }

    interface PaymentOperation {
        boolean canPay();

        boolean withdraw();

        boolean refund();

        String failureReason();
    }

    record LedgerResult(
            boolean success,
            String failureReason,
            BooleanSupplier refund
    ) {
        static LedgerResult paid(BooleanSupplier refund) {
            return new LedgerResult(true, "", refund);
        }

        static LedgerResult failed(String failureReason) {
            return new LedgerResult(
                    false, failureReason, () -> false);
        }
    }

    private record NormalizedCost(
            Map<ResourceLocation, Long> currencies,
            Map<ResourceLocation, Integer> items
    ) {
    }

    private static final class InventoryPaymentOperation
            implements PaymentOperation {
        private final ServerPlayer player;
        private final ResourceLocation itemId;
        private final Item item;
        private final int amount;
        private final List<ItemStack> removed = new ArrayList<>();

        private InventoryPaymentOperation(
                ServerPlayer player,
                ResourceLocation itemId,
                Item item,
                int amount
        ) {
            this.player = player;
            this.itemId = itemId;
            this.item = item;
            this.amount = amount;
        }

        @Override
        public boolean canPay() {
            return player.getInventory().countItem(item) >= amount;
        }

        @Override
        public boolean withdraw() {
            int remaining = amount;
            for (int slot = 0;
                 slot < player.getInventory().getContainerSize()
                         && remaining > 0;
                 slot++) {
                ItemStack stack =
                        player.getInventory().getItem(slot);
                if (stack.isEmpty() || !stack.is(item)) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                removed.add(stack.copyWithCount(take));
                stack.shrink(take);
                remaining -= take;
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (remaining == 0) {
                return true;
            }
            refund();
            return false;
        }

        @Override
        public boolean refund() {
            for (ItemStack removedStack : removed) {
                ItemStack returned = removedStack.copy();
                if (!player.getInventory().add(returned)
                        && !returned.isEmpty()) {
                    player.drop(returned, false);
                }
            }
            removed.clear();
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            return true;
        }

        @Override
        public String failureReason() {
            return "item:" + itemId;
        }
    }
}
