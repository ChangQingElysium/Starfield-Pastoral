package com.stardew.craft.api.v1.farm;

import com.stardew.craft.api.v1.internal.farm.StardewFarmSnapshots;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.server.MinecraftServer;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only lookup facade for farm snapshots and their named attachments. */
public final class StardewFarms {
    private StardewFarms() {
    }

    public static Optional<StardewFarmSnapshot> find(
            MinecraftServer server,
            UUID ownerUuid
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return Optional.ofNullable(
                        FarmInstanceRegistry.get(server)
                                .getFarm(ownerUuid))
                .map(StardewFarmSnapshots::from);
    }

    public static Optional<StardewFarmSnapshot> findForPlayer(
            MinecraftServer server,
            UUID playerUuid
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerUuid, "playerUuid");
        return Optional.ofNullable(
                        FarmInstanceRegistry.get(server)
                                .getFarmForPlayer(playerUuid))
                .map(StardewFarmSnapshots::from);
    }

    public static List<StardewFarmSnapshot> all(
            MinecraftServer server
    ) {
        Objects.requireNonNull(server, "server");
        return FarmInstanceRegistry.get(server).getAllFarms().stream()
                .map(StardewFarmSnapshots::from)
                .sorted(Comparator.comparingInt(
                        StardewFarmSnapshot::slotIndex))
                .toList();
    }
}
