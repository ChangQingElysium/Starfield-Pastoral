package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.secretnote.SecretNoteService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: reopen a seen note from Collections without replaying its effects. */
@SuppressWarnings("null")
public record OpenSeenSecretNotePayload(String noteId) implements CustomPacketPayload {
    public static final Type<OpenSeenSecretNotePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_seen_secret_note")
    );
    public static final StreamCodec<ByteBuf, OpenSeenSecretNotePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenSeenSecretNotePayload::noteId,
            OpenSeenSecretNotePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSeenSecretNotePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ResourceLocation id = ResourceLocation.tryParse(payload.noteId());
            if (id != null) SecretNoteService.openSeen(player, id);
        });
    }
}
