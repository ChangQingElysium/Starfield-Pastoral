package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.SecretNote21ClientEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: play secret note 21's Marnie/Lewis bush animation in the world. */
public record PlaySecretNote21BushEventPayload(BlockPos actorOrigin) implements CustomPacketPayload {
    public static final Type<PlaySecretNote21BushEventPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "play_secret_note_21_bush_event"));
    public static final StreamCodec<FriendlyByteBuf, PlaySecretNote21BushEventPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBlockPos(payload.actorOrigin()),
            buf -> new PlaySecretNote21BushEventPayload(buf.readBlockPos().immutable()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlaySecretNote21BushEventPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SecretNote21ClientEvent.start(payload.actorOrigin()));
    }
}
