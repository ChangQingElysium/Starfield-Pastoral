package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: tells the client to hide or show a specific NPC
 * for THIS client only (per-player visibility during cutscenes).
 */
public record NpcVisibilityPayload(String npcId, boolean hidden) implements CustomPacketPayload {

    public static final Type<NpcVisibilityPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "npc_visibility"));

    public static final StreamCodec<ByteBuf, NpcVisibilityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcVisibilityPayload::npcId,
            ByteBufCodecs.BOOL, NpcVisibilityPayload::hidden,
            NpcVisibilityPayload::new);

    @SuppressWarnings("null")
    public static void handle(NpcVisibilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.hidden) {
                ClientNpcVisibilityState.hide(payload.npcId);
            } else {
                ClientNpcVisibilityState.show(payload.npcId);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
