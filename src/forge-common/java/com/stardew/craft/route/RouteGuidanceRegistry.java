package com.stardew.craft.route;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RouteGuidanceRegistry {
    public static final String SPIRIT_EVE_MAZE_EXIT = "spirit_eve_maze_exit";

    private static final Map<String, RouteGuidanceRoute> ROUTES = new LinkedHashMap<>();

    static {
        register(new RouteGuidanceRoute(SPIRIT_EVE_MAZE_EXIT, List.of(
            new BlockPos(-5, 66, -30),
            new BlockPos(-5, 66, -35),
            new BlockPos(-15, 66, -35),
            new BlockPos(-15, 66, -37),
            new BlockPos(-2, 66, -37),
            new BlockPos(-2, 66, -40),
            new BlockPos(3, 66, -40),
            new BlockPos(3, 66, -46),
            new BlockPos(6, 66, -46),
            new BlockPos(6, 66, -43),
            new BlockPos(8, 66, -43),
            new BlockPos(8, 66, -54),
            new BlockPos(10, 66, -54),
            new BlockPos(10, 66, -56),
            new BlockPos(-10, 66, -56),
            new BlockPos(-10, 66, -51),
            new BlockPos(-17, 66, -51),
            new BlockPos(-17, 66, -47),
            new BlockPos(-20, 66, -47),
            new BlockPos(-20, 66, -55),
            new BlockPos(-12, 66, -55),
            new BlockPos(-12, 66, -58),
            new BlockPos(4, 66, -58),
            new BlockPos(4, 66, -63),
            new BlockPos(7, 66, -63),
            new BlockPos(7, 66, -64),
            new BlockPos(16, 66, -64),
            new BlockPos(16, 66, -58),
            new BlockPos(18, 66, -58),
            new BlockPos(18, 66, -56),
            new BlockPos(16, 66, -56),
            new BlockPos(16, 66, -45),
            new BlockPos(20, 66, -45),
            new BlockPos(20, 66, -38),
            new BlockPos(22, 66, -38),
            new BlockPos(22, 66, -45),
            new BlockPos(30, 66, -45),
            new BlockPos(30, 66, -42),
            new BlockPos(34, 66, -42),
            new BlockPos(34, 66, -51),
            new BlockPos(39, 66, -51),
            new BlockPos(39, 66, -44),
            new BlockPos(68, 66, -44),
            new BlockPos(68, 66, -56)
        )));
    }

    private RouteGuidanceRegistry() {
    }

    public static void register(RouteGuidanceRoute route) {
        if (route == null || !route.isUsable()) {
            return;
        }
        ROUTES.put(route.id(), route);
    }

    public static Optional<RouteGuidanceRoute> get(String id) {
        return Optional.ofNullable(ROUTES.get(id == null ? "" : id.trim()));
    }
}
