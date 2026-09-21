package com.stardew.craft.route;

import net.minecraft.core.BlockPos;

import java.util.List;

public record RouteGuidanceRoute(String id, List<BlockPos> points) {
    public RouteGuidanceRoute {
        id = id == null ? "" : id.trim();
        points = List.copyOf(points == null ? List.of() : points);
    }

    public boolean isUsable() {
        return !id.isBlank() && points.size() >= 2;
    }
}
