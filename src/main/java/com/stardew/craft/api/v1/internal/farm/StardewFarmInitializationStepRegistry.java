package com.stardew.craft.api.v1.internal.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmInitializationSteps;
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

/** Core execution bridge. Not part of the public compatibility surface. */
public final class StardewFarmInitializationStepRegistry {
    private static final Map<ResourceLocation, Registered> STEPS = new HashMap<>();
    private static volatile List<Registered> snapshot = List.of();

    private StardewFarmInitializationStepRegistry() {
    }

    public static synchronized void register(
            ResourceLocation id,
            int version,
            int priority,
            StardewFarmInitializationSteps.FailurePolicy failurePolicy,
            StardewFarmInitializationSteps.Step step
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        Objects.requireNonNull(step, "step");
        if (version < 0) {
            throw new IllegalArgumentException("Farm initialization step version must be non-negative");
        }
        if (STEPS.containsKey(id)) {
            throw new IllegalStateException("Stardew farm initialization step already registered: " + id);
        }
        STEPS.put(id, new Registered(id, version, priority, failurePolicy, step));
        ArrayList<Registered> ordered = new ArrayList<>(STEPS.values());
        ordered.sort(Comparator.comparingInt(Registered::priority).reversed()
                .thenComparing(value -> value.id().toString()));
        snapshot = List.copyOf(ordered);
    }

    public static StardewFarmInitializationSteps.RunReport runPending(
            ServerLevel level,
            UUID ownerUuid
    ) {
        FarmInstanceRegistry farmRegistry = FarmInstanceRegistry.get(level.getServer());
        FarmInstance farm = farmRegistry.getFarm(ownerUuid);
        if (farm == null) {
            return new StardewFarmInitializationSteps.RunReport(0, 0, 0, false);
        }

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        boolean stopped = false;
        for (Registered registered : snapshot) {
            if (farm.getInitializationStepVersion(registered.id()) >= registered.version()) {
                continue;
            }
            attempted++;
            try {
                registered.step().initialize(new StardewFarmInitializationSteps.Context(
                        level, StardewFarmSnapshots.from(farm)));
                farm.markInitializationStepComplete(registered.id(), registered.version());
                farmRegistry.setDirty();
                succeeded++;
            } catch (Exception exception) {
                failed++;
                StardewCraft.LOGGER.error(
                        "Stardew farm initialization step {} version {} failed for farm {}",
                        registered.id(),
                        registered.version(),
                        ownerUuid,
                        exception
                );
                if (registered.failurePolicy()
                        == StardewFarmInitializationSteps.FailurePolicy.STOP) {
                    stopped = true;
                    break;
                }
            }
        }
        return new StardewFarmInitializationSteps.RunReport(
                attempted, succeeded, failed, stopped);
    }

    private record Registered(
            ResourceLocation id,
            int version,
            int priority,
            StardewFarmInitializationSteps.FailurePolicy failurePolicy,
            StardewFarmInitializationSteps.Step step
    ) {
    }
}
