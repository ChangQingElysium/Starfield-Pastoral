package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.casino.CasinoService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record CasinoGameActionPayload(long sessionId, int action) implements CustomPacketPayload {
    public static final int CALICO_HIT = 0;
    public static final int CALICO_STAND = 1;
    public static final int CALICO_DOUBLE_OR_NOTHING = 2;
    public static final int CALICO_PLAY_AGAIN = 3;
    public static final int SLOTS_SPIN_10 = 4;
    public static final int SLOTS_SPIN_100 = 5;
    public static final int SLOTS_COLLECT = 6;
    public static final int CLOSE = 7;
    public static final int CALICO_START = 8;

    public static final Type<CasinoGameActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "casino_game_action"));

    public static final StreamCodec<ByteBuf, CasinoGameActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            CasinoGameActionPayload::sessionId,
            ByteBufCodecs.VAR_INT,
            CasinoGameActionPayload::action,
            CasinoGameActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CasinoGameActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                CasinoService.handleAction(player, payload.sessionId, payload.action);
            }
        });
    }
}
