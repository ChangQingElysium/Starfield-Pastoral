package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.animal.model.AnimalAcquisitionSource;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewAnimalLifecycleHandlersTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void dailyHandlersAreOrderedIsolatedAndCanReplaceOnlyDefaultProduction() {
        int suffix = IDS.incrementAndGet();
        String animalTypeId = "animal_lifecycle_test:goose_" + suffix;
        List<String> calls = new ArrayList<>();

        StardewAnimalDailyHandlers.register(id("daily_throwing_" + suffix), animalTypeId, 300,
                context -> {
                    calls.add("throwing");
                    throw new IllegalStateException("expected test failure");
                });
        StardewAnimalDailyHandlers.register(id("daily_pass_" + suffix), animalTypeId, 200,
                context -> {
                    calls.add("pass");
                    context.addHappiness(5);
                    return StardewAnimalDailyHandlers.Result.PASS;
                });
        StardewAnimalDailyHandlers.register(id("daily_selected_" + suffix), animalTypeId, 100,
                context -> {
                    calls.add("selected");
                    context.resetProduceCooldown();
                    return StardewAnimalDailyHandlers.Result.SKIP_DEFAULT_PRODUCTION;
                });
        StardewAnimalDailyHandlers.register(id("daily_unreached_" + suffix), animalTypeId, 0,
                context -> {
                    calls.add("unreached");
                    return StardewAnimalDailyHandlers.Result.PASS;
                });

        FarmAnimalRecord animal = animal(animalTypeId);
        animal.incrementDaysSinceLastProduce();
        StardewAnimalDailyHandlers.Result result = StardewAnimalDailyHandlers.run(
                new StardewAnimalDailyContext(animal, 42, false));

        assertEquals(StardewAnimalDailyHandlers.Result.SKIP_DEFAULT_PRODUCTION, result);
        assertEquals(List.of("throwing", "pass", "selected"), calls);
        assertEquals(255, animal.happiness());
        assertEquals(0, animal.daysSinceLastProduce());
    }

    @Test
    void dailyContextStoresHeldProduceWithoutExposingTheAnimalRecord() {
        FarmAnimalRecord animal = animal("animal_lifecycle_test:alpaca_" + IDS.incrementAndGet());
        StardewAnimalDailyContext context = new StardewAnimalDailyContext(animal, 7, false);

        assertTrue(context.setHeldProduce(new ItemStack(Items.WHITE_WOOL)));
        assertEquals("minecraft:white_wool", animal.currentProduceId());
        assertFalse(context.setHeldProduce(ItemStack.EMPTY));
    }

    @Test
    void reproductionRulesFailOpenUntilAProviderExplicitlyDenies() {
        int suffix = IDS.incrementAndGet();
        String animalTypeId = "animal_lifecycle_test:camel_" + suffix;
        List<String> calls = new ArrayList<>();

        StardewAnimalReproductionRules.register(
                id("reproduction_throwing_" + suffix), animalTypeId, 200, context -> {
                    calls.add("throwing");
                    throw new IllegalStateException("expected test failure");
                });
        StardewAnimalReproductionRules.register(
                id("reproduction_pass_" + suffix), animalTypeId, 100, context -> {
                    calls.add("pass");
                    return StardewAnimalReproductionRules.Decision.PASS;
                });
        StardewAnimalReproductionRules.register(
                id("reproduction_deny_" + suffix), animalTypeId, 0, context -> {
                    calls.add("deny");
                    return StardewAnimalReproductionRules.Decision.DENY;
                });

        boolean allowed = StardewAnimalReproductionRules.allows(
                new StardewAnimalReproductionContext(animal(animalTypeId), 42));

        assertFalse(allowed);
        assertEquals(List.of("throwing", "pass", "deny"), calls);
    }

    @Test
    void lifecycleRegistrationIdsCannotBeReused() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation registrationId = id("duplicate_" + suffix);
        StardewAnimalDailyHandlers.register(
                registrationId,
                "animal_lifecycle_test:first_" + suffix,
                0,
                context -> StardewAnimalDailyHandlers.Result.PASS
        );

        assertThrows(IllegalStateException.class, () -> StardewAnimalDailyHandlers.register(
                registrationId,
                "animal_lifecycle_test:second_" + suffix,
                0,
                context -> StardewAnimalDailyHandlers.Result.PASS
        ));
    }

    private static FarmAnimalRecord animal(String animalTypeId) {
        return new FarmAnimalRecord(
                IDS.incrementAndGet(),
                animalTypeId,
                "",
                "test_building",
                AnimalAcquisitionSource.PURCHASE,
                1,
                0,
                1,
                5,
                3
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("animal_lifecycle_test", path);
    }
}
