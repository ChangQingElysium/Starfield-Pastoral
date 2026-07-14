package com.stardew.craft.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMailIndexTest {
    @AfterEach
    void clearCache() {
        ClientMailIndex.clear();
    }

    @Test
    void replacementIsAtomicAndDefensivelyCopied() {
        List<ClientMailIndex.Entry> source = new ArrayList<>();
        source.add(new ClientMailIndex.Entry("example:first", "mail.example.first"));
        ClientMailIndex.replace(source);
        source.clear();

        assertEquals(List.of(new ClientMailIndex.Entry("example:first", "mail.example.first")),
                ClientMailIndex.entries());
        assertThrows(UnsupportedOperationException.class,
                () -> ClientMailIndex.entries().add(new ClientMailIndex.Entry("bad", "bad")));

        ClientMailIndex.replace(List.of(new ClientMailIndex.Entry("example:second", "mail.example.second")));
        assertEquals(List.of(new ClientMailIndex.Entry("example:second", "mail.example.second")),
                ClientMailIndex.entries());
    }

    @Test
    void clearPreventsCrossServerLeakage() {
        ClientMailIndex.replace(List.of(new ClientMailIndex.Entry("server_a:mail", "mail.server_a")));
        ClientMailIndex.clear();
        assertTrue(ClientMailIndex.entries().isEmpty());
    }

    @Test
    void nullReplacementIsAnExplicitEmptySnapshot() {
        ClientMailIndex.replace(List.of(new ClientMailIndex.Entry("server_a:mail", "mail.server_a")));
        ClientMailIndex.replace(null);
        assertTrue(ClientMailIndex.entries().isEmpty());
    }
}
