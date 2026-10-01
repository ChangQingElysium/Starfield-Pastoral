package com.stardew.craft.route;

import com.stardew.craft.network.payload.RouteGuidanceStartPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class RouteGuidanceService {
    private RouteGuidanceService() {
    }

    public static boolean start(ServerPlayer player, String routeId, int durationTicks) {
        if (player == null || durationTicks <= 0) {
            return false;
        }
        RouteGuidanceRoute route = RouteGuidanceRegistry.get(routeId).orElse(null);
        if (route == null || !route.isUsable()) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new RouteGuidanceStartPayload(route.id(), route.points(), durationTicks));
        return true;
    }
}
