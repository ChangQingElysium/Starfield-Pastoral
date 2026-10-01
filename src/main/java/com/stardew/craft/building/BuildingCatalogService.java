package com.stardew.craft.building;

import com.stardew.craft.api.v1.building.StardewBuildingBlueprint;
import com.stardew.craft.network.payload.OpenCarpenterMenuPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.CarpenterBlueprint;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

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
        var farm = com.stardew.craft.farm.FarmInstanceRegistry.get(player.server)
                .getFarmForPlayer(player.getUUID());
        boolean robinBusy = builder.equals(com.stardew.craft.api.v1.building.StardewBuildingBuilders.ROBIN)
                && farm != null
                && com.stardew.craft.building.runtime.BuildingWorldData.get(player.server)
                        .hasActiveConstruction(farm.getInstanceId());
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
        if (!ids.isEmpty()) {
            for (var family : ids) if (com.stardew.craft.building.runtime.PrefabDefinitions.available(family))
                com.stardew.craft.building.runtime.BuildingPreviewService.sendTemplate(player, family, 1);
        }
        PacketDistributor.sendToPlayer(player,
                new OpenCarpenterMenuPayload(
                        builder.toString(),
                        PlayerStardewDataAPI.getMoney(player),
                        clientBlueprints,
                        revision,
                        robinBusy));
        return true;
    }

    public static java.util.Optional<ResourceLocation> authorizedBuilder(ServerPlayer player,ResourceLocation blueprint,long revision){
        var session=SESSIONS.get(player.getUUID());
        return session!=null&&authorizes(player,session.builder(),blueprint,revision)?java.util.Optional.of(session.builder()):java.util.Optional.empty();
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
