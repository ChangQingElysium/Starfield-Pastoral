package com.stardew.craft.api.v1.farm;

import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutMigrationRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.UUID;

/**
 * Explicit, retryable world migrations for versioned farm layouts.
 *
 * <p>Registering a newer layout never rewrites an existing farm by itself.
 * A farm advances only after its addon migration succeeds, so geometry changes
 * remain deliberate and failed migrations retry on a later login.
 */
public final class StardewFarmLayoutMigrations {
    private StardewFarmLayoutMigrations() {
    }

    public static void register(
            ResourceLocation layoutId,
            int targetVersion,
            FailurePolicy failurePolicy,
            Migration migration
    ) {
        register(layoutId, targetVersion, failurePolicy,
                SnapshotPolicy.PRESERVE, migration);
    }

    public static void register(
            ResourceLocation layoutId,
            int targetVersion,
            FailurePolicy failurePolicy,
            SnapshotPolicy snapshotPolicy,
            Migration migration
    ) {
        StardewFarmLayoutMigrationRegistry.register(
                layoutId, targetVersion, failurePolicy,
                snapshotPolicy, migration);
    }

    public static RunReport runPending(ServerLevel level, UUID ownerUuid) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return StardewFarmLayoutMigrationRegistry.runPending(level, ownerUuid);
    }

    public enum FailurePolicy {
        CONTINUE,
        STOP
    }

    /**
     * Controls whether a successful migration also adopts the registration's
     * current bounds, entries and other geometry for the existing farm.
     */
    public enum SnapshotPolicy {
        /** Keep the creation-time geometry snapshot. Safest default. */
        PRESERVE,
        /** Explicitly replace it after the migration callback succeeds. */
        ADOPT_CURRENT_REGISTRATION
    }

    @FunctionalInterface
    public interface Migration {
        void migrate(Context context) throws Exception;
    }

    public record Context(
            ServerLevel level,
            StardewFarmSnapshot farm,
            StardewFarmLayoutConfiguration configuration,
            int fromVersion,
            int targetVersion
    ) {
        public Context {
            level = Objects.requireNonNull(level, "level");
            farm = Objects.requireNonNull(farm, "farm");
            configuration = Objects.requireNonNull(configuration, "configuration");
            if (fromVersion < 1 || targetVersion <= fromVersion) {
                throw new IllegalArgumentException("Invalid farm layout migration version range");
            }
        }
    }

    public record RunReport(
            int attempted,
            int succeeded,
            int failed,
            boolean stopped,
            int resultingVersion
    ) {
        public RunReport {
            if (attempted < 0 || succeeded < 0 || failed < 0 || resultingVersion < 1) {
                throw new IllegalArgumentException("Invalid farm layout migration report");
            }
        }
    }
}
