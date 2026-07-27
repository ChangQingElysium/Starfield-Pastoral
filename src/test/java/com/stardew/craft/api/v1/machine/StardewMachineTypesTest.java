package com.stardew.craft.api.v1.machine;

import com.stardew.craft.api.v1.internal.machine.StardewMachineRecipeDisplayRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StardewMachineTypesTest {
    @Test
    void addonMachineDescriptorAndDisplayDtoValidateTheirContracts() {
        ResourceLocation machineId = id("tea_fermenter");
        StardewMachineType machine = new StardewMachineType(
                machineId,
                ResourceLocation.withDefaultNamespace("barrel"),
                "jei.machine_test.tea_fermenter",
                StardewMachineType.Layout.AUXILIARY_INPUT,
                true,
                List.of(new StardewMachineType.AuxiliaryInput(
                        ResourceLocation.withDefaultNamespace("sugar"), 1))
        );
        StardewMachineRecipeDisplays.register(
                id("tea_display_provider"),
                100,
                requestedMachine -> requestedMachine.equals(machineId)
                        ? List.of(new StardewMachineRecipeDisplay(
                                id("tea_recipe"),
                                machineId,
                                List.of(
                                        new StardewMachineRecipeDisplay.Input(
                                                List.of(new ItemStack(Items.KELP)), 1, false),
                                        new StardewMachineRecipeDisplay.Input(
                                                List.of(new ItemStack(Items.SUGAR)), 1, true)
                                ),
                                List.of(new StardewMachineRecipeDisplay.Output(
                                        List.of(new ItemStack(Items.HONEY_BOTTLE)),
                                        1, 1, 1.0D)),
                                60,
                                false,
                                -1
                        ))
                        : List.of()
        );

        assertEquals(machineId, machine.id());
        assertEquals("jei.machine_test.tea_fermenter", machine.translationKey());
        assertEquals(1, StardewMachineRecipeDisplayRegistry.displays(machineId).size());
    }

    @Test
    void invalidAuxiliaryLayoutIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StardewMachineType(
                id("invalid"),
                ResourceLocation.withDefaultNamespace("barrel"),
                "jei.machine_test.invalid",
                StardewMachineType.Layout.STANDARD,
                true,
                List.of(new StardewMachineType.AuxiliaryInput(
                        ResourceLocation.withDefaultNamespace("coal"), 1))
        ));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("machine_test", path);
    }
}
