package com.stardew.craft.communitycenter.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleDataManagerAtomicSnapshotTest {
    @Test
    void networkCatalogPublishesAsOneCoherentSnapshot() throws Exception {
        BundleDataManager.Catalog before = BundleDataManager.catalog();
        BundleDefinition first = definition(101, 11);
        BundleDefinition second = definition(202, 22);
        Thread writer = null;
        try {
            BundleDataManager.applyFromNetwork(
                    List.of(first),
                    Map.of(11, "first"),
                    Map.of(11, "first.key"));

            AtomicBoolean running = new AtomicBoolean(true);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            writer = new Thread(() -> {
                try {
                    for (int index = 0; index < 2_000; index++) {
                        if ((index & 1) == 0) {
                            BundleDataManager.applyFromNetwork(
                                    List.of(second),
                                    Map.of(22, "second"),
                                    Map.of(22, "second.key"));
                        } else {
                            BundleDataManager.applyFromNetwork(
                                    List.of(first),
                                    Map.of(11, "first"),
                                    Map.of(11, "first.key"));
                        }
                    }
                } catch (Throwable throwable) {
                    failure.set(throwable);
                } finally {
                    running.set(false);
                }
            }, "bundle-catalog-writer");
            writer.start();

            while (running.get()) {
                assertCoherent(BundleDataManager.catalog());
            }
            writer.join();

            assertNull(failure.get());
            assertCoherent(BundleDataManager.catalog());
        } finally {
            if (writer != null) {
                writer.join();
            }
            BundleDataManager.applyFromNetwork(
                    before.bundlesById().values(),
                    before.areaNames(),
                    before.areaDisplayNameKeys());
        }
    }

    private static void assertCoherent(BundleDataManager.Catalog catalog) {
        assertTrue(catalog.revision() > 0);
        assertTrue(catalog.bundlesById().size() == 1);
        BundleDefinition definition =
                catalog.bundlesById().values().iterator().next();
        int areaId = definition.areaId();
        assertTrue(catalog.bundlesByArea().size() == 1);
        assertTrue(catalog.bundlesByArea()
                .getOrDefault(areaId, List.of())
                .equals(List.of(definition)));
        assertTrue(catalog.areaNames().keySet().equals(
                java.util.Set.of(areaId)));
        assertTrue(catalog.areaDisplayNameKeys().keySet().equals(
                java.util.Set.of(areaId)));
    }

    private static BundleDefinition definition(
            int bundleId,
            int areaId
    ) {
        return new BundleDefinition(
                bundleId,
                areaId,
                "bundle_" + bundleId,
                "bundle." + bundleId,
                "",
                List.of(),
                0,
                1);
    }
}
