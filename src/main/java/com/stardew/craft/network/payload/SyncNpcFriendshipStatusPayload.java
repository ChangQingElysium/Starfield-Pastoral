package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Server -> client one NPC friendship status delta for interaction feedback and world icons.
 */
public record SyncNpcFriendshipStatusPayload(
    String npcId,
    int points,
    int hearts,
    int giftsThisWeek,
    boolean giftedToday,
    boolean talkedToday
) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<SyncNpcFriendshipStatusPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "sync_npc_friendship_status"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncNpcFriendshipStatusPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.npcId());
            buf.writeInt(payload.points());
            buf.writeInt(payload.hearts());
            buf.writeInt(payload.giftsThisWeek());
            buf.writeBoolean(payload.giftedToday());
            buf.writeBoolean(payload.talkedToday());
        },
        buf -> new SyncNpcFriendshipStatusPayload(
            buf.readUtf(64),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readBoolean(),
            buf.readBoolean()
        )
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncNpcFriendshipStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.NpcFriendshipClientCache.updateNpcState(
            payload.npcId(),
            payload.points(),
            payload.hearts(),
            payload.giftsThisWeek(),
            payload.giftedToday(),
            payload.talkedToday()
        ));
    }
}
