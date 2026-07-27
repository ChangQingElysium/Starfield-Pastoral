package com.stardew.craft.animal.service;

import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.item.ModItems;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimalProduceStatServiceTest {
    @Test
    void duckEggStatRejectsTheDeluxeFeather() {
        var duck = FarmAnimalDefinitions.require("duck");

        assertEquals(
                List.of("DuckEggsLayed"),
                AnimalProduceStatService.matchingStats(
                        duck,
                        new ItemStack(
                                ModItems.DUCK_EGG.get())));
        assertEquals(
                List.of(),
                AnimalProduceStatService.matchingStats(
                        duck,
                        new ItemStack(
                                ModItems.DUCK_FEATHER.get())));
    }

    @Test
    void rabbitWoolStatRejectsTheDeluxeFoot() {
        var rabbit =
                FarmAnimalDefinitions.require("rabbit");

        assertEquals(
                List.of("RabbitWoolProduced"),
                AnimalProduceStatService.matchingStats(
                        rabbit,
                        new ItemStack(ModItems.WOOL.get())));
        assertEquals(
                List.of(),
                AnimalProduceStatService.matchingStats(
                        rabbit,
                        new ItemStack(
                                ModItems.RABBITS_FOOT.get())));
    }

    @Test
    void unconditionalToolStatsRetainSourceNames() {
        assertEquals(
                List.of("cowMilkProduced"),
                AnimalProduceStatService.matchingStats(
                        FarmAnimalDefinitions.require("cow"),
                        new ItemStack(ModItems.MILK.get())));
        assertEquals(
                List.of("sheepWoolProduced"),
                AnimalProduceStatService.matchingStats(
                        FarmAnimalDefinitions.require("sheep"),
                        new ItemStack(ModItems.WOOL.get())));
    }
}
