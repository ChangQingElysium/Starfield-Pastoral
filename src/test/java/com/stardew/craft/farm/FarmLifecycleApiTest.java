package com.stardew.craft.farm;

import com.stardew.craft.api.v1.farm.StardewFarmLifecycle;
import com.stardew.craft.api.v1.farm.StardewFarmLifecycleListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmLifecycleApiTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void lifecycleObserversAreOrderedIsolatedAndOnlySeeCompletedTransactions() {
        int suffix = IDS.incrementAndGet();
        UUID owner = UUID.randomUUID();
        UUID nextOwner = UUID.randomUUID();
        List<String> calls = new ArrayList<>();

        StardewFarmLifecycle.register(id("throwing_" + suffix), 300,
                new StardewFarmLifecycleListener() {
                    @Override
                    public void beforeCreate(StardewFarmLifecycle.CreateRequest request) {
                        if (request.ownerUuid().equals(owner)) {
                            calls.add("throwing-before-create");
                            throw new IllegalStateException("expected test failure");
                        }
                    }
                });
        StardewFarmLifecycle.register(id("observer_" + suffix), 200,
                new StardewFarmLifecycleListener() {
                    @Override
                    public void beforeCreate(StardewFarmLifecycle.CreateRequest request) {
                        if (request.ownerUuid().equals(owner)) {
                            calls.add("before-create");
                        }
                    }

                    @Override
                    public void afterCreate(StardewFarmLifecycle.FarmContext context) {
                        if (context.farm().ownerUuid().equals(owner)) {
                            calls.add("after-create:" + context.farm().slotIndex());
                        }
                    }

                    @Override
                    public void beforeTransfer(StardewFarmLifecycle.TransferRequest request) {
                        if (request.source().ownerUuid().equals(owner)) {
                            calls.add("before-transfer");
                        }
                    }

                    @Override
                    public void afterTransfer(StardewFarmLifecycle.TransferResult result) {
                        if (result.source().ownerUuid().equals(owner)) {
                            calls.add("after-transfer:" + result.transferred().ownerUuid());
                        }
                    }

                    @Override
                    public void beforeDelete(StardewFarmLifecycle.FarmContext context) {
                        if (context.farm().ownerUuid().equals(nextOwner)) {
                            calls.add("before-delete");
                        }
                    }

                    @Override
                    public void afterDelete(StardewFarmLifecycle.FarmContext context) {
                        if (context.farm().ownerUuid().equals(nextOwner)) {
                            calls.add("after-delete");
                        }
                    }
                });

        FarmInstanceRegistry registry = new FarmInstanceRegistry();
        FarmInstance created = registry.createFarmAtDate(
                owner, "Old Owner", "Canary Farm", FarmType.STANDARD, 42, 2);
        assertNotNull(created);
        assertEquals(List.of(
                "throwing-before-create",
                "before-create",
                "after-create:0"
        ), calls);

        assertTrue(registry.transferFarm(owner, nextOwner, "New Owner"));
        assertNull(registry.getFarm(owner));
        assertNotNull(registry.getFarm(nextOwner));
        assertNotNull(registry.deleteFarm(nextOwner));
        assertEquals(List.of(
                "throwing-before-create",
                "before-create",
                "after-create:0",
                "before-transfer",
                "after-transfer:" + nextOwner,
                "before-delete",
                "after-delete"
        ), calls);
    }

    @Test
    void transferPreservesCoreAndAddonOwnedFarmState() {
        FarmInstanceRegistry registry = new FarmInstanceRegistry();
        UUID owner = UUID.randomUUID();
        UUID nextOwner = UUID.randomUUID();
        ResourceLocation stepId = id("init_state_" + IDS.incrementAndGet());
        FarmInstance source = registry.createFarmAtDate(
                owner, "Old Owner", "Stateful Farm", FarmType.FOREST, 91, 3);
        source.markInitialized();
        source.setCreatedTimestamp(123456L);
        source.setGraceDaysLeft(7);
        source.setCaveChoice(FarmCaveChoice.FRUIT_BATS);
        source.setGoldClockState(true, false);
        source.markInitializationStepComplete(stepId, 4);

        assertTrue(registry.transferFarm(owner, nextOwner, "New Owner"));
        FarmInstance transferred = registry.getFarm(nextOwner);
        assertNotNull(transferred);
        assertTrue(transferred.isInitialized());
        assertEquals(123456L, transferred.getCreatedTimestamp());
        assertEquals(91, transferred.getLastOnlineDay());
        assertEquals(3, transferred.getLastOnlineSeason());
        assertEquals(7, transferred.getGraceDaysLeft());
        assertEquals(FarmCaveChoice.FRUIT_BATS, transferred.getCaveChoice());
        assertTrue(transferred.hasGoldClock());
        assertFalse(transferred.isGoldClockEnabled());
        assertEquals(4, transferred.getInitializationStepVersion(stepId));
    }

    @Test
    void addonInitializationAndUnknownNamespacedStateRoundTrip() {
        ResourceLocation stepId = id("round_trip_" + IDS.incrementAndGet());
        FarmInstance source = new FarmInstance(
                UUID.randomUUID(), "Owner", "Farm", 3,
                FarmInstanceAllocator.getFarmOrigin(3, FarmType.STANDARD),
                FarmType.STANDARD);
        source.markInitializationStepComplete(stepId, 2);
        CompoundTag saved = source.save();
        CompoundTag addonEntry = new CompoundTag();
        addonEntry.putInt("version", 9);
        CompoundTag payload = new CompoundTag();
        payload.putString("variant", "grandpa");
        addonEntry.put("payload", payload);
        CompoundTag addonData = new CompoundTag();
        addonData.put("missing_addon:farm_state", addonEntry);
        saved.put("AddonData", addonData);

        FarmInstance loaded = FarmInstance.load(saved);
        CompoundTag rewritten = loaded.save();
        assertEquals(2, loaded.getInitializationStepVersion(stepId));
        assertEquals("grandpa", rewritten.getCompound("AddonData")
                .getCompound("missing_addon:farm_state")
                .getCompound("payload")
                .getString("variant"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("farm_lifecycle_test", path);
    }
}
