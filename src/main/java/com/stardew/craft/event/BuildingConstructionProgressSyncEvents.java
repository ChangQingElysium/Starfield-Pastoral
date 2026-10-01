package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.client.StardewConstructionOrderSnapshot;
import com.stardew.craft.api.v1.client.StardewConstructionProgressSnapshot;
import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.building.runtime.BuildingService;
import com.stardew.craft.building.runtime.BuildingWorldData;
import com.stardew.craft.network.payload.BuildingConstructionProgressSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Synchronizes player-scoped Robin orders without exposing building-world internals. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class BuildingConstructionProgressSyncEvents {
    private static final Map<UUID, StardewConstructionProgressSnapshot> SENT = new HashMap<>();

    private BuildingConstructionProgressSyncEvents() {
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0 || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        BuildingWorldData data = BuildingWorldData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            StardewConstructionProgressSnapshot snapshot = snapshotFor(data, player);
            if (!snapshot.equals(SENT.get(player.getUUID()))) {
                PacketDistributor.sendToPlayer(
                        player, new BuildingConstructionProgressSyncPayload(snapshot));
                SENT.put(player.getUUID(), snapshot);
            }
        }
    }

    /** Public for deterministic GameTests; callers must stay on the server thread. */
    public static StardewConstructionProgressSnapshot snapshotFor(
            BuildingWorldData data,
            ServerPlayer player
    ) {
        List<Entry> visible = data.all().stream()
                .filter(record -> record.phase() == BuildingRecord.Phase.CONSTRUCTING
                        || record.phase() == BuildingRecord.Phase.UPGRADING)
                .filter(record -> BuildingService.canManage(player, record))
                .map(record -> new Entry(record, data.order(record.id())))
                .filter(entry -> entry.order() != null)
                .sorted(Comparator
                        .comparingInt((Entry entry) -> entry.record().farmSlot())
                        .thenComparing(entry -> entry.record().id()))
                .toList();
        List<StardewConstructionOrderSnapshot> orders = visible.stream()
                .limit(BuildingConstructionProgressSyncPayload.MAX_ORDERS)
                .map(Entry::snapshot)
                .toList();
        return new StardewConstructionProgressSnapshot(
                player.getUUID(), visible.size(), orders);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SENT.clear();
    }

    private record Entry(
            BuildingRecord record,
            com.stardew.craft.building.runtime.ConstructionOrder order
    ) {
        private StardewConstructionOrderSnapshot snapshot() {
            boolean upgrade = record.phase() == BuildingRecord.Phase.UPGRADING;
            return new StardewConstructionOrderSnapshot(
                    record.id(),
                    record.family(),
                    record.displayName(),
                    upgrade
                            ? StardewConstructionOrderSnapshot.WorkType.UPGRADE
                            : StardewConstructionOrderSnapshot.WorkType.CONSTRUCTION,
                    upgrade ? record.tier() + 1 : record.tier(),
                    order.remainingDays());
        }
    }
}
