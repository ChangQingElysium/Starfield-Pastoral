package com.stardew.craft.building;

import com.stardew.craft.api.v1.building.StardewBuildingBlueprint;
import com.stardew.craft.network.payload.OpenCarpenterMenuPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.CarpenterBlueprint;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived server authorization for one exact building catalog view. */
public final class BuildingCatalogService {
    private static final long SESSION_TICKS = 20L * 60L;
    private static final Map<UUID, Session> SESSIONS =
            new ConcurrentHashMap<>();

    private BuildingCatalogService() {
    }

    public static boolean open(
            ServerPlayer player,
            ResourceLocation builder
    ) {
        List<StardewBuildingBlueprint> available =
                BuildingBlueprintRegistry.availableFor(player, builder);
        if (available.isEmpty()) {
            return false;
        }
        long revision = BuildingBlueprintRegistry.revision();
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
        List<CarpenterBlueprint> clientBlueprints = available.stream()
                .map(blueprint -> {
                    ids.add(blueprint.id());
                    return CarpenterBlueprint.from(blueprint);
                })
                .toList();
        SESSIONS.put(player.getUUID(), new Session(
                builder,
                revision,
                Set.copyOf(ids),
                player.serverLevel().getGameTime() + SESSION_TICKS));
        PacketDistributor.sendToPlayer(player,
                new OpenCarpenterMenuPayload(
                        builder.toString(),
                        PlayerStardewDataAPI.getMoney(player),
                        clientBlueprints,
                        revision));
        return true;
    }

    public static boolean authorizes(
            ServerPlayer player,
            ResourceLocation builder,
            ResourceLocation blueprint,
            long revision
    ) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null
                || session.expiresAtTick()
                        < player.serverLevel().getGameTime()
                || !session.builder().equals(builder)
                || session.revision() != revision
                || revision != BuildingBlueprintRegistry.revision()
                || !session.blueprintIds().contains(blueprint)) {
            SESSIONS.remove(player.getUUID());
            return false;
        }
        return true;
    }

    private record Session(
            ResourceLocation builder,
            long revision,
            Set<ResourceLocation> blueprintIds,
            long expiresAtTick
    ) {
    }
}
