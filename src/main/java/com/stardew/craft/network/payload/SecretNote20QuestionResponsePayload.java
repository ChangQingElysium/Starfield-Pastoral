package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.secretnote.SecretNote20Service;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record SecretNote20QuestionResponsePayload(boolean accepted) implements CustomPacketPayload {
    public static final Type<SecretNote20QuestionResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "secret_note_20_question_response"));
    public static final StreamCodec<FriendlyByteBuf, SecretNote20QuestionResponsePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.accepted()),
            buf -> new SecretNote20QuestionResponsePayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SecretNote20QuestionResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SecretNote20Service.handleQuestionResponse(player, payload.accepted());
            }
        });
    }
}
