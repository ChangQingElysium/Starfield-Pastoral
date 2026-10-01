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
public record MoneyContractActionPayload(UUID targetId, int choice) implements CustomPacketPayload {
    public static final Type<MoneyContractActionPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "money_contract_action"));

    public static final StreamCodec<FriendlyByteBuf, MoneyContractActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUUID(payload.targetId());
            buf.writeVarInt(payload.choice());
        },
        buf -> new MoneyContractActionPayload(buf.readUUID(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MoneyContractActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MoneyContractService.handleAction(player, payload.targetId(), payload.choice());
            }
        });
    }
}
