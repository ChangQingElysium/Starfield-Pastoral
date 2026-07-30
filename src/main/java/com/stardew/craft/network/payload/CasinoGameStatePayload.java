package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server-authored casino state used by both Calico Jack and Slots. */
@SuppressWarnings("null")
public record CasinoGameStatePayload(
        int game,
        long sessionId,
        int clubCoins,
        int currentBet,
        boolean highStakes,
        int phase,
        int result,
        List<Integer> playerCards,
        List<Integer> dealerCards,
        int slot0,
        int slot1,
        int slot2,
        int payoutMultiplier
) implements CustomPacketPayload {
    public static final int GAME_CALICO_JACK = 0;
    public static final int GAME_SLOTS = 1;
    public static final int PHASE_CALICO_PLAYING = 0;
    public static final int PHASE_CALICO_RESULT = 1;
    public static final int PHASE_SLOTS_IDLE = 2;
    public static final int PHASE_SLOTS_SPINNING = 3;

    public static final Type<CasinoGameStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "casino_game_state"));

    public static final StreamCodec<FriendlyByteBuf, CasinoGameStatePayload> STREAM_CODEC = StreamCodec.of(
            CasinoGameStatePayload::write,
            CasinoGameStatePayload::read
    );

    private static void write(FriendlyByteBuf buf, CasinoGameStatePayload payload) {
        buf.writeVarInt(payload.game);
        buf.writeLong(payload.sessionId);
        buf.writeVarInt(payload.clubCoins);
        buf.writeVarInt(payload.currentBet);
        buf.writeBoolean(payload.highStakes);
        buf.writeVarInt(payload.phase);
        buf.writeVarInt(payload.result + 1);
        writeIntList(buf, payload.playerCards);
        writeIntList(buf, payload.dealerCards);
        buf.writeVarInt(payload.slot0 + 1);
        buf.writeVarInt(payload.slot1 + 1);
        buf.writeVarInt(payload.slot2 + 1);
        buf.writeVarInt(payload.payoutMultiplier);
    }

    private static CasinoGameStatePayload read(FriendlyByteBuf buf) {
        return new CasinoGameStatePayload(
                buf.readVarInt(),
                buf.readLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt() - 1,
                readIntList(buf),
                readIntList(buf),
                buf.readVarInt() - 1,
                buf.readVarInt() - 1,
                buf.readVarInt() - 1,
                buf.readVarInt()
        );
    }

    private static void writeIntList(FriendlyByteBuf buf, List<Integer> values) {
        buf.writeVarInt(values.size());
        values.forEach(buf::writeVarInt);
    }

    private static List<Integer> readIntList(FriendlyByteBuf buf) {
        int size = Math.min(64, buf.readVarInt());
        List<Integer> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(buf.readVarInt());
        }
        return List.copyOf(values);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CasinoGameStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(CasinoGameStatePayload payload) {
        com.stardew.craft.client.gui.casino.CasinoScreenRouter.accept(payload);
    }
}
