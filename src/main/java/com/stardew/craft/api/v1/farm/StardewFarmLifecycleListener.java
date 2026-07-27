package com.stardew.craft.api.v1.farm;

/**
 * Observer for authoritative farm-registry transactions.
 *
 * <p>Callbacks cannot veto a transaction. A listener failure is logged and isolated so one addon
 * cannot leave the core registry or another addon's state half-updated.
 */
public interface StardewFarmLifecycleListener {
    default void beforeCreate(StardewFarmLifecycle.CreateRequest request) {
    }

    default void afterCreate(StardewFarmLifecycle.FarmContext context) {
    }

    default void beforeTransfer(StardewFarmLifecycle.TransferRequest request) {
    }

    default void afterTransfer(StardewFarmLifecycle.TransferResult result) {
    }

    default void beforeDelete(StardewFarmLifecycle.FarmContext context) {
    }

    default void afterDelete(StardewFarmLifecycle.FarmContext context) {
    }
}
