package com.stardew.craft.api.v1.agriculture;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class StardewTruffleFoundHandlersTest {
    @Test
    void duplicateHandlerIdsAreRejectedDeterministically() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "test",
                "truffle_replacement"
        );
        StardewTruffleFoundHandlers.register(
                id,
                0,
                context -> StardewTruffleFoundHandlers.Result.PASS
        );

        assertThrows(
                IllegalStateException.class,
                () -> StardewTruffleFoundHandlers.register(
                        id,
                        10,
                        context -> StardewTruffleFoundHandlers.Result.PASS
                )
        );
    }
}
