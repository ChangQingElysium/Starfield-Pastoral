package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.mail.MailService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: reopen received mail from Collections without replaying rewards or actions. */
@SuppressWarnings("null")
public record OpenSeenMailPayload(String mailId) implements CustomPacketPayload {
    public static final Type<OpenSeenMailPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_seen_mail")
    );
    public static final StreamCodec<ByteBuf, OpenSeenMailPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenSeenMailPayload::mailId,
            OpenSeenMailPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSeenMailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MailService.openSeenMail(player, payload.mailId());
            }
        });
    }
}
