package com.stardew.craft.api.v1.internal.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutMigrations;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Core execution bridge for layout-specific migrations. */
public final class StardewFarmLayoutMigrationRegistry {
    private static final Map<ResourceLocation, List<Registered>> MIGRATIONS =
            new HashMap<>();

    private StardewFarmLayoutMigrationRegistry() {
    }

    public static synchronized void register(
            ResourceLocation layoutId,
            int targetVersion,
            StardewFarmLayoutMigrations.FailurePolicy failurePolicy,
            StardewFarmLayoutMigrations.SnapshotPolicy snapshotPolicy,
            StardewFarmLayoutMigrations.Migration migration
    ) {
        Objects.requireNonNull(layoutId, "layoutId");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        Objects.requireNonNull(snapshotPolicy, "snapshotPolicy");
        Objects.requireNonNull(migration, "migration");
        if (targetVersion < 2) {
            throw new IllegalArgumentException(
                    "Farm layout migration target version must be at least 2");
        }
        List<Registered> registered = MIGRATIONS.computeIfAbsent(
                layoutId, ignored -> new ArrayList<>());
        if (registered.stream().anyMatch(value ->
                value.targetVersion() == targetVersion)) {
            throw new IllegalStateException(
                    "Farm layout migration already registered: "
                            + layoutId + " -> " + targetVersion);
        }
        registered.add(new Registered(
                targetVersion, failurePolicy, snapshotPolicy, migration));
        registered.sort(Comparator.comparingInt(Registered::targetVersion));
    }

    public static StardewFarmLayoutMigrations.RunReport runPending(
            ServerLevel level,
            UUID ownerUuid
    ) {
        FarmInstanceRegistry farmRegistry =
                FarmInstanceRegistry.get(level.getServer());
        FarmInstance farm = farmRegistry.getFarm(ownerUuid);
        if (farm == null) {
            return new StardewFarmLayoutMigrations.RunReport(
                    0, 0, 0, false, 1);
        }

        int currentVersion = farm.getFarmLayoutVersion();
        int registeredVersion = StardewFarmLayouts.findRegistration(
                        farm.getFarmLayoutId())
                .map(registration -> registration.version())
                .orElse(currentVersion);
        List<Registered> migrations;
        synchronized (StardewFarmLayoutMigrationRegistry.class) {
            migrations = List.copyOf(MIGRATIONS.getOrDefault(
                    farm.getFarmLayoutId(), List.of()));
        }

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        boolean stopped = false;
        for (Registered registered : migrations) {
            if (registered.targetVersion() <= currentVersion
                    || registered.targetVersion() > registeredVersion) {
                continue;
            }
            attempted++;
            try {
                registered.migration().migrate(
                        new StardewFarmLayoutMigrations.Context(
                                level,
                                StardewFarmSnapshots.from(farm),
                                farm.getFarmLayoutConfiguration(),
                                currentVersion,
                                registered.targetVersion()));
                farm.completeFarmLayoutMigration(
                        registered.targetVersion(),
                        registered.snapshotPolicy()
                                == StardewFarmLayoutMigrations.SnapshotPolicy
                                        .ADOPT_CURRENT_REGISTRATION
                                ? StardewFarmLayouts.findRegistration(
                                                farm.getFarmLayoutId())
                                        .orElse(null)
                                : null);
                currentVersion = registered.targetVersion();
                farmRegistry.setDirty();
                succeeded++;
            } catch (Exception exception) {
                failed++;
                StardewCraft.LOGGER.error(
                        "Farm layout migration {} -> {} failed for farm {}",
                        farm.getFarmLayoutId(),
                        registered.targetVersion(),
                        ownerUuid,
                        exception);
                if (registered.failurePolicy()
                        == StardewFarmLayoutMigrations.FailurePolicy.STOP) {
                    stopped = true;
                    break;
                }
            }
        }
        return new StardewFarmLayoutMigrations.RunReport(
                attempted, succeeded, failed, stopped, currentVersion);
    }

    private record Registered(
            int targetVersion,
            StardewFarmLayoutMigrations.FailurePolicy failurePolicy,
            StardewFarmLayoutMigrations.SnapshotPolicy snapshotPolicy,
            StardewFarmLayoutMigrations.Migration migration
    ) {
    }
}
