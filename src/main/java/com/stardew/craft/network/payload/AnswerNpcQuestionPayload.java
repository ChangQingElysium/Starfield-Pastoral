package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnswerNpcQuestionPayload(
        String npcId,
        String nextDialogueNode,
        int friendshipDelta,
        String answerId
) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<AnswerNpcQuestionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "answer_npc_question"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, AnswerNpcQuestionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.npcId(), 64);
                buf.writeUtf(payload.nextDialogueNode() == null ? "" : payload.nextDialogueNode(), 128);
                buf.writeInt(payload.friendshipDelta());
                buf.writeUtf(payload.answerId() == null ? "" : payload.answerId(), 64);
            },
            buf -> new AnswerNpcQuestionPayload(
                    buf.readUtf(64),
                    buf.readUtf(128),
                    buf.readInt(),
                    buf.readUtf(64)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnswerNpcQuestionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                NpcInteractionService.handleClientQuestionAnswer(
                        serverPlayer,
                        payload.npcId(),
                        payload.nextDialogueNode(),
                        payload.friendshipDelta(),
                        payload.answerId());
            }
        });
    }
}
