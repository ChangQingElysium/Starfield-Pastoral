package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.client.FlowerDanceCutsceneClientState;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record FlowerDanceCutsceneStatePayload(
    List<FlowerDanceCutsceneClientState.DancePair> pairs,
    List<FlowerDanceCutsceneClientState.Partner> spectators
) implements CustomPacketPayload {
    public FlowerDanceCutsceneStatePayload {
        pairs = pairs == null ? List.of() : List.copyOf(pairs);
        spectators = spectators == null ? List.of() : List.copyOf(spectators);
    }

    public static final Type<FlowerDanceCutsceneStatePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "flower_dance_cutscene_state"));

    public static final StreamCodec<FriendlyByteBuf, FlowerDanceCutsceneStatePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            int pairCount = Math.min(payload.pairs().size(), 18);
            buf.writeVarInt(pairCount);
            for (int index = 0; index < pairCount; index++) {
                FlowerDanceCutsceneClientState.DancePair pair = payload.pairs().get(index);
                writePartner(buf, pair.femaleSide());
                writePartner(buf, pair.maleSide());
            }
            int spectatorCount = Math.min(payload.spectators().size(), 10);
            buf.writeVarInt(spectatorCount);
            for (int index = 0; index < spectatorCount; index++) {
                writePartner(buf, payload.spectators().get(index));
            }
        },
        buf -> {
            int pairCount = Math.min(buf.readVarInt(), 18);
            List<FlowerDanceCutsceneClientState.DancePair> pairs = new ArrayList<>(pairCount);
            for (int index = 0; index < pairCount; index++) {
                FlowerDanceCutsceneClientState.Partner female = readPartner(buf);
                FlowerDanceCutsceneClientState.Partner male = readPartner(buf);
                pairs.add(new FlowerDanceCutsceneClientState.DancePair(female, male));
            }
            int spectatorCount = Math.min(buf.readVarInt(), 10);
            List<FlowerDanceCutsceneClientState.Partner> spectators = new ArrayList<>(spectatorCount);
            for (int index = 0; index < spectatorCount; index++) {
                spectators.add(readPartner(buf));
            }
            return new FlowerDanceCutsceneStatePayload(pairs, spectators);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlowerDanceCutsceneStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(FlowerDanceCutsceneStatePayload payload) {
        FlowerDanceCutsceneClientState.set(payload.pairs(), payload.spectators());
    }

    private static void writePartner(FriendlyByteBuf buf, FlowerDanceCutsceneClientState.Partner partner) {
        FlowerDanceCutsceneClientState.Partner safe = partner == null ? FlowerDanceCutsceneClientState.Partner.npc("") : partner;
        buf.writeEnum(safe.kind());
        if (safe.kind() == FlowerDanceCutsceneClientState.Partner.Kind.PLAYER) {
            buf.writeUUID(safe.playerId() == null ? new UUID(0L, 0L) : safe.playerId());
        } else {
            buf.writeUtf(safe.npcId() == null ? "" : safe.npcId(), 64);
        }
    }

    private static FlowerDanceCutsceneClientState.Partner readPartner(FriendlyByteBuf buf) {
        FlowerDanceCutsceneClientState.Partner.Kind kind = buf.readEnum(FlowerDanceCutsceneClientState.Partner.Kind.class);
        if (kind == FlowerDanceCutsceneClientState.Partner.Kind.PLAYER) {
            return FlowerDanceCutsceneClientState.Partner.player(buf.readUUID());
        }
        return FlowerDanceCutsceneClientState.Partner.npc(buf.readUtf(64));
    }
}
