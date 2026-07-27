package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.festival.StardewFestivalClientSessionSnapshot;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionSnapshot;
import com.stardew.craft.api.v1.internal.festival.StardewFestivalClientSessionCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Complete bounded replacement snapshot of festival sessions for one client. */
@SuppressWarnings("null")
public record FestivalSessionsSyncPayload(
        UUID serverEpoch,
        long revision,
        List<StardewFestivalClientSessionSnapshot> sessions
) implements CustomPacketPayload {
    public static final int MAX_SESSIONS = 64;
    private static final int MAX_RUNTIME_ID_LENGTH = 128;

    public static final Type<FestivalSessionsSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "festival_sessions_sync"));
    public static final StreamCodec<
            FriendlyByteBuf,
            FestivalSessionsSyncPayload> STREAM_CODEC = StreamCodec.of(
                    FestivalSessionsSyncPayload::write,
                    FestivalSessionsSyncPayload::read);

    public FestivalSessionsSyncPayload {
        Objects.requireNonNull(serverEpoch, "serverEpoch");
        sessions = List.copyOf(sessions);
        if (sessions.size() > MAX_SESSIONS) {
            throw new IllegalArgumentException(
                    "festival session snapshot exceeds "
                            + MAX_SESSIONS + " entries");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            FestivalSessionsSyncPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(
            net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(
            FestivalSessionsSyncPayload payload
    ) {
        StardewFestivalClientSessionCache.replace(
                payload.serverEpoch(),
                payload.revision(),
                payload.sessions());
    }

    private static void write(
            FriendlyByteBuf buffer,
            FestivalSessionsSyncPayload payload
    ) {
        buffer.writeUUID(payload.serverEpoch());
        buffer.writeVarLong(payload.revision());
        buffer.writeVarInt(payload.sessions().size());
        for (StardewFestivalClientSessionSnapshot session
                : payload.sessions()) {
            buffer.writeResourceLocation(session.festivalId());
            buffer.writeUtf(session.runtimeId(), MAX_RUNTIME_ID_LENGTH);
            buffer.writeVarInt(session.year());
            buffer.writeVarInt(session.season());
            buffer.writeVarInt(session.day());
            buffer.writeEnum(session.phase());
            buffer.writeEnum(session.mapPhase());
            buffer.writeVarInt(session.participantCount());
            buffer.writeBoolean(session.localPlayerParticipating());
        }
    }

    private static FestivalSessionsSyncPayload read(
            FriendlyByteBuf buffer
    ) {
        UUID serverEpoch = buffer.readUUID();
        long revision = buffer.readVarLong();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_SESSIONS) {
            throw new IllegalArgumentException(
                    "invalid festival session snapshot size: " + size);
        }
        ArrayList<StardewFestivalClientSessionSnapshot> sessions =
                new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            sessions.add(new StardewFestivalClientSessionSnapshot(
                    buffer.readResourceLocation(),
                    buffer.readUtf(MAX_RUNTIME_ID_LENGTH),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readEnum(
                            StardewFestivalSessionSnapshot.Phase.class),
                    buffer.readEnum(
                            StardewFestivalSessionSnapshot.MapPhase.class),
                    buffer.readVarInt(),
                    buffer.readBoolean()));
        }
        return new FestivalSessionsSyncPayload(
                serverEpoch, revision, sessions);
    }
}
