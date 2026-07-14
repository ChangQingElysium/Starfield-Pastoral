package com.stardew.craft.api.v1.content;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicDefinitionStoreTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("testaddon", "entry");

    @Test
    void failedCandidateKeepsPreviousSnapshotIntact() {
        AtomicDefinitionStore<String> store = new AtomicDefinitionStore<>();
        var first = store.applyLocal(Map.of(ID, "live"), Map.of(ID, "{live}"), List.of());

        var rejected = store.applyLocal(
                Map.of(ID, "broken"),
                Map.of(ID, "{broken}"),
                List.of(DefinitionDiagnostic.error(ID, ID, "invalid test definition")));

        assertTrue(first.accepted());
        assertFalse(rejected.accepted());
        assertSame(first.snapshot(), store.snapshot());
        assertEquals("live", store.snapshot().definitions().get(ID));
        assertEquals(1L, store.snapshot().version());
    }

    @Test
    void remoteSnapshotRequiresMatchingVersionHashAndContent() {
        AtomicDefinitionStore<String> server = new AtomicDefinitionStore<>();
        var published = server.applyLocal(
                Map.of(ID, "value"), Map.of(ID, "canonical"), List.of()).snapshot();
        AtomicDefinitionStore<String> client = new AtomicDefinitionStore<>();

        var accepted = client.applyRemote(
                published.version(), published.contentHash(),
                published.definitions(), Map.of(ID, "canonical"), List.of());
        var tampered = client.applyRemote(
                published.version() + 1L, published.contentHash(),
                Map.of(ID, "changed"), Map.of(ID, "changed"), List.of());

        assertTrue(accepted.accepted());
        assertFalse(tampered.accepted());
        assertEquals(published.contentHash(), client.snapshot().contentHash());
        assertEquals("value", client.snapshot().definitions().get(ID));
    }

    @Test
    void laterResolvedDatapackSnapshotReplacesTheSameDefinitionId() {
        AtomicDefinitionStore<String> store = new AtomicDefinitionStore<>();
        store.applyLocal(Map.of(ID, "low-priority"), Map.of(ID, "low-source"), List.of());

        var higherPriority = store.applyLocal(
                Map.of(ID, "high-priority"), Map.of(ID, "high-source"), List.of());

        assertTrue(higherPriority.accepted());
        assertTrue(higherPriority.changed());
        assertEquals(2L, higherPriority.snapshot().version());
        assertEquals("high-priority", higherPriority.snapshot().definitions().get(ID));
    }

    @Test
    void snapshotPreservesDefinitionInsertionOrder() {
        AtomicDefinitionStore<String> store = new AtomicDefinitionStore<>();
        ResourceLocation second = ResourceLocation.fromNamespaceAndPath("testaddon", "second");
        LinkedHashMap<ResourceLocation, String> definitions = new LinkedHashMap<>();
        definitions.put(ID, "first");
        definitions.put(second, "second");

        var snapshot = store.applyLocal(
                definitions,
                Map.of(ID, "first", second, "second"),
                List.of()).snapshot();

        assertEquals(List.of(ID, second), List.copyOf(snapshot.definitions().keySet()));
    }
}
