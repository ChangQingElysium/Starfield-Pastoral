package com.stardew.craft.player;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerEnergyPaymentTest {
    @Test
    void insufficientPaymentDoesNotMutateEnergyOrExhaustion() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        data.setEnergy(5.0F);

        assertFalse(data.consumeEnergy(10.0F));
        assertEquals(5.0F, data.getEnergy());
        assertFalse(data.isExhausted());
    }

    @Test
    void successfulExactPaymentConsumesAndExhausts() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        data.setEnergy(10.0F);

        assertTrue(data.consumeEnergy(10.0F));
        assertEquals(0.0F, data.getEnergy());
        assertTrue(data.isExhausted());

        data.rollbackEnergyPayment(10.0F, false);
        assertEquals(10.0F, data.getEnergy());
        assertFalse(data.isExhausted());
    }
}
