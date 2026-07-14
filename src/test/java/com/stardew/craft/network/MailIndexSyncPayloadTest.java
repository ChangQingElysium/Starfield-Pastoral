package com.stardew.craft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailIndexSyncPayloadTest {
    @Test
    void safeMailIndexRoundTripsAndPreservesNamespacedIds() {
        MailIndexSyncPayload expected = new MailIndexSyncPayload(List.of(
                new MailIndexSyncPayload.Entry("spring_2_1", "mail.spring_2_1"),
                new MailIndexSyncPayload.Entry("example:addon_letter", "mail.example.addon_letter")));
        ByteBuf buffer = Unpooled.buffer();
        try {
            MailIndexSyncPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, MailIndexSyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void wireEntryCannotCarryServerActionsOrRewards() {
        Set<String> components = List.of(MailIndexSyncPayload.Entry.class.getRecordComponents()).stream()
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("mailId", "textKey"), components);
    }

    @Test
    void snapshotOwnsAnImmutableCopyAndNullMeansEmpty() {
        List<MailIndexSyncPayload.Entry> source = new ArrayList<>(List.of(
                new MailIndexSyncPayload.Entry("example:mail", "mail.example")));
        MailIndexSyncPayload payload = new MailIndexSyncPayload(source);
        source.clear();

        assertEquals(1, payload.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> payload.entries().clear());
        assertTrue(new MailIndexSyncPayload(null).entries().isEmpty());
    }
}
