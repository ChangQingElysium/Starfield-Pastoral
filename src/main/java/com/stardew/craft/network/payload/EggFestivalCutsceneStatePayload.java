package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.client.EggFestivalCutsceneClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record EggFestivalCutsceneStatePayload(
    int participantCount,
    boolean playerWon,
    int winnerMask,
    String winnerText,
    List<UUID> participantIds
) implements CustomPacketPayload {
    private static final int MAX_WINNER_TEXT_LENGTH = 1024;
    public EggFestivalCutsceneStatePayload {
        participantIds = participantIds == null ? List.of() : List.copyOf(participantIds);
    }

    public static final Type<EggFestivalCutsceneStatePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "egg_festival_cutscene_state"));

    public static final StreamCodec<FriendlyByteBuf, EggFestivalCutsceneStatePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.participantCount());
            buf.writeBoolean(payload.playerWon());
            buf.writeVarInt(payload.winnerMask());
            buf.writeUtf(payload.winnerText() == null ? "" : payload.winnerText(), MAX_WINNER_TEXT_LENGTH);
            int count = Math.min(payload.participantIds().size(), 24);
            buf.writeVarInt(count);
            for (int index = 0; index < count; index++) {
                buf.writeUUID(payload.participantIds().get(index));
            }
        },
        buf -> {
            int participantCount = buf.readVarInt();
            boolean playerWon = buf.readBoolean();
            int winnerMask = buf.readVarInt();
            String winnerText = buf.readUtf(MAX_WINNER_TEXT_LENGTH);
            int idCount = Math.min(buf.readVarInt(), 24);
            java.util.ArrayList<UUID> ids = new java.util.ArrayList<>(idCount);
            for (int index = 0; index < idCount; index++) {
                ids.add(buf.readUUID());
            }
            return new EggFestivalCutsceneStatePayload(participantCount, playerWon, winnerMask, winnerText, ids);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EggFestivalCutsceneStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(EggFestivalCutsceneStatePayload payload) {
        EggFestivalCutsceneClientState.set(payload.participantCount(), payload.playerWon(), payload.winnerMask(), payload.winnerText(), payload.participantIds());
    }
}
