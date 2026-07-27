package com.stardew.craft.api.v1.farm;

import com.stardew.craft.api.v1.internal.farm.StardewFarmInitializationStepRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.UUID;

/**
 * Versioned, ordered and retryable addon initialization steps for farm instances.
 *
 * <p>A step's id and version form its idempotency key. A successful version is persisted on the
 * farm; increasing the version runs that step once again. Failed steps remain pending.
 */
public final class StardewFarmInitializationSteps {
    private StardewFarmInitializationSteps() {
    }

    public static void register(
            ResourceLocation id,
            int version,
            int priority,
            FailurePolicy failurePolicy,
            Step step
    ) {
        StardewFarmInitializationStepRegistry.register(
                id, version, priority, failurePolicy, step);
    }

    /**
     * Runs all pending steps for an existing farm.
     *
     * <p>This may be called by an addon to retry after an external dependency becomes available.
     * Core initialization and player login also call it automatically.
     */
    public static RunReport runPending(ServerLevel level, UUID ownerUuid) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return StardewFarmInitializationStepRegistry.runPending(level, ownerUuid);
    }

    public enum FailurePolicy {
        CONTINUE,
        STOP
    }

    @FunctionalInterface
    public interface Step {
        void initialize(Context context) throws Exception;
    }

    public record Context(ServerLevel level, StardewFarmSnapshot farm) {
        public Context {
            level = Objects.requireNonNull(level, "level");
            farm = Objects.requireNonNull(farm, "farm");
        }
    }

    public record RunReport(int attempted, int succeeded, int failed, boolean stopped) {
        public RunReport {
            if (attempted < 0 || succeeded < 0 || failed < 0) {
                throw new IllegalArgumentException("Run counts must be non-negative");
            }
        }
    }
}
