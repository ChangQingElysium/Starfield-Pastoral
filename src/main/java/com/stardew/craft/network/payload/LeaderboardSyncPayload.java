package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.leaderboard.LeaderboardMetric;
import com.stardew.craft.leaderboard.LeaderboardPeriod;
import com.stardew.craft.leaderboard.LeaderboardService;
import com.stardew.craft.leaderboard.LeaderboardSnapshot;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record LeaderboardSyncPayload(
        String metricId,
    String periodId,
    int page,
        List<Entry> rows,
        Entry selfEntry,
        int totalPlayers,
        long generatedAtMillis,
        String errorKey
) implements CustomPacketPayload {
    public record Entry(UUID playerId, String playerName, long value, int rank, boolean online, boolean self) {
    }

    public static final Type<LeaderboardSyncPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "leaderboard_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LeaderboardSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public LeaderboardSyncPayload decode(RegistryFriendlyByteBuf buf) {
                    String metricId = buf.readUtf();
                    String periodId = buf.readUtf();
                    int page = buf.readVarInt();
                    int count = buf.readVarInt();
                    List<Entry> rows = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        rows.add(readEntry(buf));
                    }
                    Entry selfEntry = buf.readBoolean() ? readEntry(buf) : null;
                    int totalPlayers = buf.readVarInt();
                    long generatedAtMillis = buf.readLong();
                    String errorKey = buf.readUtf();
                    return new LeaderboardSyncPayload(metricId, periodId, page, rows, selfEntry, totalPlayers, generatedAtMillis, errorKey);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, LeaderboardSyncPayload payload) {
                    buf.writeUtf(payload.metricId == null ? LeaderboardMetric.MONEY.id() : payload.metricId);
                    buf.writeUtf(payload.periodId == null ? LeaderboardPeriod.TOTAL.id() : payload.periodId);
                    buf.writeVarInt(Math.max(0, payload.page));
                    buf.writeVarInt(payload.rows.size());
                    for (Entry entry : payload.rows) {
                        writeEntry(buf, entry);
                    }
                    buf.writeBoolean(payload.selfEntry != null);
                    if (payload.selfEntry != null) {
                        writeEntry(buf, payload.selfEntry);
                    }
                    buf.writeVarInt(Math.max(0, payload.totalPlayers));
                    buf.writeLong(payload.generatedAtMillis);
                    buf.writeUtf(payload.errorKey == null ? "" : payload.errorKey);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LeaderboardSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(LeaderboardSyncPayload payload) {
        com.stardew.craft.client.LeaderboardClientCache.update(payload);
    }

    public static void sendToPlayer(ServerPlayer player, LeaderboardMetric metric, LeaderboardPeriod period, int page) {
        LeaderboardSnapshot snapshot = LeaderboardService.buildSnapshot(player, metric, period, page);
        List<Entry> rows = snapshot.rows().stream()
                .map(entry -> new Entry(entry.playerId(), entry.playerName(), entry.value(), entry.rank(), entry.online(), entry.self()))
                .toList();
        Entry selfEntry = snapshot.selfEntry() == null ? null : new Entry(
                snapshot.selfEntry().playerId(),
                snapshot.selfEntry().playerName(),
                snapshot.selfEntry().value(),
                snapshot.selfEntry().rank(),
                snapshot.selfEntry().online(),
                snapshot.selfEntry().self());
        PacketDistributor.sendToPlayer(player, new LeaderboardSyncPayload(
            metric.id(), snapshot.period().id(), Math.max(0, page), rows, selfEntry, snapshot.totalPlayers(), snapshot.generatedAtMillis(), ""));
    }

        public static void sendErrorToPlayer(ServerPlayer player, LeaderboardMetric metric, LeaderboardPeriod period, int page, String errorKey) {
        PacketDistributor.sendToPlayer(player, new LeaderboardSyncPayload(
            metric.id(), period == null ? LeaderboardPeriod.TOTAL.id() : period.id(), Math.max(0, page), List.of(), null, 0, System.currentTimeMillis(), errorKey));
    }

    private static Entry readEntry(RegistryFriendlyByteBuf buf) {
        return new Entry(buf.readUUID(), buf.readUtf(), buf.readLong(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    private static void writeEntry(RegistryFriendlyByteBuf buf, Entry entry) {
        buf.writeUUID(entry.playerId());
        buf.writeUtf(entry.playerName());
        buf.writeLong(entry.value());
        buf.writeVarInt(entry.rank());
        buf.writeBoolean(entry.online());
        buf.writeBoolean(entry.self());
    }
}
