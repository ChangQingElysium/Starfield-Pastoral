package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Server -> client friendship overview data for the V-menu social tab.
 */
public record SyncNpcFriendshipOverviewPayload(List<Entry> entries) implements CustomPacketPayload {
    public record Entry(String npcId, boolean met, boolean canReceiveGifts, int points, int hearts, int giftsThisWeek, boolean giftedToday, boolean talkedToday, int metOrder) {
        @SuppressWarnings("null")
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
            (buf, entry) -> {
                buf.writeUtf(entry.npcId());
                buf.writeBoolean(entry.met());
                buf.writeBoolean(entry.canReceiveGifts());
                buf.writeInt(entry.points());
                buf.writeInt(entry.hearts());
                buf.writeInt(entry.giftsThisWeek());
                buf.writeBoolean(entry.giftedToday());
                buf.writeBoolean(entry.talkedToday());
                buf.writeInt(entry.metOrder());
            },
            buf -> new Entry(
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt()
            )
        );
    }

    @SuppressWarnings("null")
    public static final Type<SyncNpcFriendshipOverviewPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "sync_npc_friendship_overview"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncNpcFriendshipOverviewPayload> STREAM_CODEC = StreamCodec.composite(
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list()),
        SyncNpcFriendshipOverviewPayload::entries,
        SyncNpcFriendshipOverviewPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncNpcFriendshipOverviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            List<com.stardew.craft.client.NpcFriendshipClientCache.Entry> rows = payload.entries().stream()
                .map(entry -> new com.stardew.craft.client.NpcFriendshipClientCache.Entry(entry.npcId(), entry.met(), entry.canReceiveGifts(), entry.points(), entry.hearts(), entry.giftsThisWeek(), entry.giftedToday(), entry.talkedToday(), entry.metOrder()))
                .toList();
            com.stardew.craft.client.NpcFriendshipClientCache.update(rows);
        });
    }
}
