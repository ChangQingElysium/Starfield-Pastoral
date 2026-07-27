package com.stardew.craft.integration.jei;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineJeiRegistryTest {
    @Test
    void registersAllThirteenMachinesWithStableNamespacedTypes() {
        var builtinMachines = MachineJeiRegistry.all().stream()
                .filter(machine -> "stardewcraft".equals(machine.id().getNamespace()))
                .toList();
        assertEquals(13, builtinMachines.size());

        Set<String> machineIds = new HashSet<>();
        Set<String> recipeTypeIds = new HashSet<>();
        for (MachineJeiRegistry.Machine machine : builtinMachines) {
            assertEquals("stardewcraft", machine.id().getNamespace());
            assertEquals(machine.id(), machine.itemId());
            assertTrue(machineIds.add(machine.id().toString()), "duplicate machine " + machine.id());

            String recipeTypeId = machine.recipeType().getUid().toString();
            assertTrue(recipeTypeIds.add(recipeTypeId), "duplicate recipe type " + recipeTypeId);
            assertTrue(recipeTypeId.startsWith("stardewcraft:machine/"));
            assertFalse(machine.recipeType().getUid().getPath().contains(":"));
        }
        assertEquals(
                MachineJeiRegistry.all().size(),
                MachineJeiRegistry.all().stream().map(MachineJeiRegistry.Machine::id).distinct().count(),
                "addon and built-in machine IDs must remain globally unique"
        );
    }

    @Test
    void onlyIncubatorIsDeclaredAsAConsumeOnlyMachine() {
        var consumeOnly = MachineJeiRegistry.all().stream()
                .filter(machine -> !machine.producesItem())
                .map(machine -> machine.id().getPath())
                .toList();
        assertEquals(java.util.List.of("incubator"), consumeOnly);
    }
}
