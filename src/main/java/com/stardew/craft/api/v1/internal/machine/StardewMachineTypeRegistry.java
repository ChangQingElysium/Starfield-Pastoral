package com.stardew.craft.api.v1.internal.machine;

import com.stardew.craft.api.v1.machine.StardewMachineType;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Core addon-machine descriptor registry. Not part of the public compatibility surface. */
public final class StardewMachineTypeRegistry {
    private static final Set<String> BUILTIN_PATHS = Set.of(
            "charcoal_kiln", "cheese_press", "crystalarium", "dehydrator",
            "fish_smoker", "furnace", "incubator", "keg", "loom",
            "mayonnaise_machine", "oil_maker", "preserves_jar", "seed_maker"
    );
    private static final Map<ResourceLocation, Registered> BY_REGISTRATION = new HashMap<>();
    private static final Map<ResourceLocation, StardewMachineType> BY_MACHINE = new HashMap<>();
    private static volatile List<StardewMachineType> snapshot = List.of();
    private static boolean frozen;

    private StardewMachineTypeRegistry() {
    }

    public static synchronized void register(
            ResourceLocation registrationId,
            StardewMachineType machine
    ) {
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(machine, "machine");
        if (frozen) {
            throw new IllegalStateException(
                    "Stardew machine types are frozen because JEI category registration has begun");
        }
        if (BY_REGISTRATION.containsKey(registrationId)) {
            throw new IllegalStateException(
                    "Stardew machine registration ID already used: " + registrationId);
        }
        if ("stardewcraft".equals(machine.id().getNamespace())
                && BUILTIN_PATHS.contains(machine.id().getPath())) {
            throw new IllegalStateException(
                    "Cannot replace built-in Stardew machine type: " + machine.id());
        }
        if (BY_MACHINE.containsKey(machine.id())) {
            throw new IllegalStateException(
                    "Stardew machine type already registered: " + machine.id());
        }
        BY_REGISTRATION.put(registrationId, new Registered(registrationId, machine));
        BY_MACHINE.put(machine.id(), machine);
        rebuildSnapshot();
    }

    @Nullable
    public static synchronized StardewMachineType definition(ResourceLocation machineId) {
        return BY_MACHINE.get(machineId);
    }

    public static List<StardewMachineType> definitions() {
        return snapshot;
    }

    public static Set<ResourceLocation> ids() {
        java.util.LinkedHashSet<ResourceLocation> ids =
                new java.util.LinkedHashSet<>();
        BUILTIN_PATHS.stream().sorted().map(path ->
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", path)).forEach(ids::add);
        snapshot.stream().map(StardewMachineType::id)
                .forEach(ids::add);
        return Set.copyOf(ids);
    }

    public static synchronized List<StardewMachineType> freezeAndGetDefinitions() {
        frozen = true;
        return snapshot;
    }

    public static String translationKey(ResourceLocation machineId) {
        StardewMachineType definition = definition(machineId);
        return definition == null
                ? "stardewcraft.jei.machine." + machineId.getPath()
                : definition.translationKey();
    }

    public static List<StardewMachineType.AuxiliaryInput> auxiliaryInputs(
            ResourceLocation machineId
    ) {
        StardewMachineType definition = definition(machineId);
        return definition == null ? List.of() : definition.auxiliaryInputs();
    }

    private static void rebuildSnapshot() {
        ArrayList<Registered> ordered = new ArrayList<>(BY_REGISTRATION.values());
        ordered.sort(Comparator.comparing(value -> value.registrationId().toString()));
        snapshot = ordered.stream().map(Registered::machine).toList();
    }

    private record Registered(
            ResourceLocation registrationId,
            StardewMachineType machine
    ) {
    }
}
