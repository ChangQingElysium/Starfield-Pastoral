package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.money.MoneyContractService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

@SuppressWarnings("null")
public record MoneyContractTransferSubmitPayload(UUID targetId, int amount) implements CustomPacketPayload {
    public static final Type<MoneyContractTransferSubmitPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "money_contract_transfer_submit"));

    public static final StreamCodec<FriendlyByteBuf, MoneyContractTransferSubmitPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUUID(payload.targetId());
            buf.writeVarInt(payload.amount());
        },
        buf -> new MoneyContractTransferSubmitPayload(buf.readUUID(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MoneyContractTransferSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MoneyContractService.transferMoney(player, payload.targetId(), payload.amount());
            }
        });
    }
}
