package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientMailIndex;
import com.stardew.craft.mail.MailRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Comparator;
import java.util.List;

/** S→C: safe mail metadata for the collections screen; deliberately excludes actions and rewards. */
public record MailIndexSyncPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<MailIndexSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mail_index_sync"));

    public static final StreamCodec<ByteBuf, Entry> ENTRY_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Entry::mailId,
            ByteBufCodecs.STRING_UTF8, Entry::textKey,
            Entry::new);

    public static final StreamCodec<ByteBuf, MailIndexSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ENTRY_STREAM_CODEC.apply(ByteBufCodecs.list()), MailIndexSyncPayload::entries,
            MailIndexSyncPayload::new);

    public MailIndexSyncPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static MailIndexSyncPayload current() {
        List<Entry> entries = MailRegistry.getAll().stream()
                .map(mail -> new Entry(mail.getId(), mail.getText()))
                .sorted(Comparator.comparing(Entry::mailId))
                .toList();
        return new MailIndexSyncPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MailIndexSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientMailIndex.replace(payload.entries().stream()
                .map(entry -> new ClientMailIndex.Entry(entry.mailId(), entry.textKey()))
                .toList()));
    }

    public record Entry(String mailId, String textKey) {
        public Entry {
            mailId = mailId == null ? "" : mailId;
            textKey = textKey == null ? "" : textKey;
        }
    }
}
