package com.stardew.craft.player;

import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassOutRecoveryDataTest {
    @Test
    void combatTransactionSurvivesNbtRoundTrip() {
        var registries = VanillaRegistries.createLookup();
        UUID playerId = UUID.randomUUID();
        var data = new PassOutRecoveryData();
        data.put(playerId, new PassOutRecoveryData.Entry(
                987654321L,
                PassOutService.PassOutType.COMBAT_MINE,
                true,
                PassOutRecoveryData.Stage.WAITING_FOR_DESTINATION,
                345,
                List.of(new ItemStack(Items.DIAMOND, 2)),
                Integer.MIN_VALUE,
                "linus",
                "LINUS"));

        CompoundTag saved = data.save(new CompoundTag(), registries);
        PassOutRecoveryData restored = PassOutRecoveryData.load(saved, registries);
        PassOutRecoveryData.Entry entry = restored.get(playerId);

        assertNotNull(entry);
        assertTrue(entry.combat());
        assertEquals(987654321L, entry.transactionId());
        assertEquals(PassOutRecoveryData.Stage.WAITING_FOR_DESTINATION, entry.stage());
        assertEquals(345, entry.moneyLost());
        assertEquals(2, entry.lostItems().getFirst().getCount());
        assertEquals("linus", entry.rescuerNpcId());
        assertEquals("LINUS", entry.dialogueName());
    }
}
