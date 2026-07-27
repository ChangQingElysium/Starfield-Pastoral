package com.stardew.craft.api.v1.internal.festival;

import com.stardew.craft.api.v1.festival.StardewFestivalClientSessionSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Client-side replacement cache; mutation is restricted to sync payloads. */
public final class StardewFestivalClientSessionCache {
    private static volatile Snapshot current = new Snapshot(
            new UUID(0L, 0L), 0L, List.of());

    private StardewFestivalClientSessionCache() {
    }

    public static long revision() {
        return current.revision();
    }

    public static List<StardewFestivalClientSessionSnapshot> all() {
        return current.sessions();
    }

    public static Optional<StardewFestivalClientSessionSnapshot> find(
            ResourceLocation festivalId
    ) {
        if (festivalId == null) {
            return Optional.empty();
        }
        return current.sessions().stream()
                .filter(session -> session.festivalId().equals(festivalId))
                .findFirst();
    }

    public static void replace(
            UUID serverEpoch,
            long revision,
            List<StardewFestivalClientSessionSnapshot> sessions
    ) {
        Snapshot previous = current;
        if (serverEpoch.equals(previous.serverEpoch())
                && revision <= previous.revision()) {
            return;
        }
        current = new Snapshot(serverEpoch, revision, sessions);
    }

    public static void clear() {
        current = new Snapshot(new UUID(0L, 0L), 0L, List.of());
    }

    private record Snapshot(
            UUID serverEpoch,
            long revision,
            List<StardewFestivalClientSessionSnapshot> sessions
    ) {
        private Snapshot {
            java.util.Objects.requireNonNull(
                    serverEpoch, "serverEpoch");
            sessions = List.copyOf(sessions);
        }
    }
}
