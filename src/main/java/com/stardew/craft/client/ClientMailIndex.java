package com.stardew.craft.client;

import java.util.List;

/** Client-only, read-only mail metadata used by the collections screen. */
public final class ClientMailIndex {
    private static volatile List<Entry> entries = List.of();

    private ClientMailIndex() {
    }

    public static List<Entry> entries() {
        return entries;
    }

    public static void replace(List<Entry> replacement) {
        entries = replacement == null ? List.of() : List.copyOf(replacement);
    }

    public static void clear() {
        entries = List.of();
    }

    public record Entry(String mailId, String textKey) {
        public Entry {
            mailId = mailId == null ? "" : mailId;
            textKey = textKey == null ? "" : textKey;
        }
    }
}
