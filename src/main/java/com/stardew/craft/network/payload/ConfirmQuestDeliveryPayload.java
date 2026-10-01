package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S：玩家在 "送X给Y完成任务Z?" 弹窗里选了"是"。
 * 服务端再次校验（手上还拿着、任务还在、NPC 在附近）后执行交付。
 */
@SuppressWarnings("null")
public record ConfirmQuestDeliveryPayload(String npcId, String questId) implements CustomPacketPayload {

    public static final Type<ConfirmQuestDeliveryPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "confirm_quest_delivery"));

    public static final StreamCodec<FriendlyByteBuf, ConfirmQuestDeliveryPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.npcId(), 64);
            buf.writeUtf(p.questId(), 64);
        },
        buf -> new ConfirmQuestDeliveryPayload(buf.readUtf(64), buf.readUtf(64))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfirmQuestDeliveryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            com.stardew.craft.npc.runtime.NpcInteractionService.handleConfirmedQuestDelivery(
                player, payload.npcId(), payload.questId());
        });
    }
}
