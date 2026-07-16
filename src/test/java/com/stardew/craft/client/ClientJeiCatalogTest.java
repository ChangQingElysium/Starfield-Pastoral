package com.stardew.craft.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientJeiCatalogTest {
    @AfterEach
    void clear() {
        ClientJeiCatalog.clear();
    }

    @Test
    void replacementIsDefensiveAndClearPreventsCrossServerLeakage() {
        ItemStack source = new ItemStack(Items.APPLE, 4);
        Set<Integer> seasons = new HashSet<>(Set.of(0));
        List<ClientJeiCatalog.ShopEntry> shops = new ArrayList<>(List.of(
                new ClientJeiCatalog.ShopEntry(
                        source, "example:apple_shop", "Pierre", 50, 1,
                        new ItemStack(Items.EMERALD, 3), 3, 2,
                        seasons, 2, 40, true, 4, 1, true, List.of("unknown"), false)));
        List<ClientJeiCatalog.GeodeEntry> geodes = new ArrayList<>(List.of(
                new ClientJeiCatalog.GeodeEntry(
                        new ItemStack(Items.AMETHYST_CLUSTER), new ItemStack(Items.DIAMOND, 2))));
        ClientJeiCatalog.replace(
                shops, geodes);
        source.setCount(1);
        seasons.clear();
        shops.clear();
        geodes.clear();

        assertTrue(ClientJeiCatalog.isSynced());
        assertEquals(4, ClientJeiCatalog.shops().getFirst().item().getCount());
        assertEquals("Pierre", ClientJeiCatalog.shops().getFirst().ownerNpcId());
        assertEquals(Set.of(0), ClientJeiCatalog.shops().getFirst().seasons());
        assertEquals(3, ClientJeiCatalog.shops().getFirst().tradeItem().getCount());
        assertEquals(2, ClientJeiCatalog.shops().getFirst().purchaseStack());
        ItemStack returned = ClientJeiCatalog.shops().getFirst().item();
        returned.setCount(2);
        assertEquals(4, ClientJeiCatalog.shops().getFirst().item().getCount());
        ItemStack returnedOutput = ClientJeiCatalog.geodes().getFirst().output();
        returnedOutput.setCount(1);
        assertEquals(2, ClientJeiCatalog.geodes().getFirst().output().getCount());
        assertThrows(UnsupportedOperationException.class, () -> ClientJeiCatalog.shops().clear());

        ClientJeiCatalog.replace(List.of(), List.of());
        assertTrue(ClientJeiCatalog.isSynced());
        assertTrue(ClientJeiCatalog.shops().isEmpty());
        assertTrue(ClientJeiCatalog.geodes().isEmpty());

        ClientJeiCatalog.clear();
        assertFalse(ClientJeiCatalog.isSynced());
        assertTrue(ClientJeiCatalog.shops().isEmpty());
        assertTrue(ClientJeiCatalog.geodes().isEmpty());
    }

    @Test
    void nullReplacementIsAnExplicitSyncedEmptySnapshot() {
        ClientJeiCatalog.replace(null, null);
        assertTrue(ClientJeiCatalog.isSynced());
        assertTrue(ClientJeiCatalog.shops().isEmpty());
        assertTrue(ClientJeiCatalog.geodes().isEmpty());
    }
}
