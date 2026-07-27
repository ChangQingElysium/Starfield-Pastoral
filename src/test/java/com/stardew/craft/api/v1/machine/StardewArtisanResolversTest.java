package com.stardew.craft.api.v1.machine;

import com.stardew.craft.api.v1.internal.machine.StardewArtisanResolverRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewArtisanResolversTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void caskResolversAreOrderedIsolatedAndValidateRates() {
        int suffix = IDS.incrementAndGet();
        StardewArtisanResolvers.registerCaskAgingRate(
                id("cask_throwing_" + suffix), 300,
                input -> {
                    if (input.is(Items.BRICK)) {
                        throw new IllegalStateException("expected test failure");
                    }
                    return null;
                });
        StardewArtisanResolvers.registerCaskAgingRate(
                id("cask_invalid_" + suffix), 200,
                input -> input.is(Items.BRICK) ? -1.0F : null);
        StardewArtisanResolvers.registerCaskAgingRate(
                id("cask_selected_" + suffix), 100,
                input -> input.is(Items.BRICK) ? 2.5F : null);

        assertEquals(2.5F,
                StardewArtisanResolverRegistry.resolveCaskAgingRate(
                        new ItemStack(Items.BRICK)));
    }

    @Test
    void smokedAndSeedResolversSupportPassOutputAndDeny() {
        int suffix = IDS.incrementAndGet();
        StardewArtisanResolvers.registerSmokedOutput(
                id("smoked_" + suffix), 100,
                input -> input.is(Items.COD)
                        ? new ItemStack(Items.DIAMOND, 2)
                        : ItemStack.EMPTY);
        ItemStack input = new ItemStack(Items.COD, 16);
        ItemStack output = StardewArtisanResolverRegistry.resolveSmokedOutput(input);
        assertTrue(output.is(Items.DIAMOND));
        assertEquals(2, output.getCount());
        assertEquals(16, input.getCount());

        StardewArtisanResolvers.registerSeedMakerOutput(
                id("seed_deny_" + suffix), 200,
                item -> item == Items.POISONOUS_POTATO
                        ? StardewArtisanResolvers.SeedResult.deny()
                        : StardewArtisanResolvers.SeedResult.pass());
        StardewArtisanResolvers.registerSeedMakerOutput(
                id("seed_output_" + suffix), 100,
                item -> item == Items.APPLE
                        ? StardewArtisanResolvers.SeedResult.output(Items.MELON_SEEDS)
                        : StardewArtisanResolvers.SeedResult.pass());

        assertEquals(
                StardewArtisanResolvers.SeedDecision.DENY,
                StardewArtisanResolverRegistry.resolveSeedMaker(
                        Items.POISONOUS_POTATO).decision());
        assertEquals(
                Items.MELON_SEEDS,
                StardewArtisanResolverRegistry.resolveSeedMaker(Items.APPLE).output());
    }

    @Test
    void duplicateResolverIdsAreRejectedWithinAResolverKind() {
        ResourceLocation duplicate = id("duplicate_" + IDS.incrementAndGet());
        StardewArtisanResolvers.registerSmokedOutput(
                duplicate, 0, input -> ItemStack.EMPTY);
        assertThrows(IllegalStateException.class, () ->
                StardewArtisanResolvers.registerSmokedOutput(
                        duplicate, 0, input -> ItemStack.EMPTY));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("artisan_resolver_test", path);
    }
}
