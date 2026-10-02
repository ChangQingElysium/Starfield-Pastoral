package com.stardew.craft.gingerisland;

import com.stardew.craft.api.v1.world.StardewLocations;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmPermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.Optional;
import java.util.UUID;

/** Instance ownership comes from the visited logical island, never the visitor's selected farm. */
public final class IslandContext {
    public static final ResourceLocation FARM_INSTANCE = new ResourceLocation("stardewcraft", "ginger_farm_instance");
    public static final ResourceLocation VOLCANO_FLOOR = new ResourceLocation("stardewcraft", "volcano_floor");
    private IslandContext() {}

    public static Optional<UUID> farmInstance(Level level, BlockPos pos) {
        return binding(level, pos).flatMap(value -> {
            try { return Optional.of(UUID.fromString(value)); }
            catch (IllegalArgumentException invalid) { return Optional.empty(); }
        });
    }

    private static Optional<String> binding(Level level, BlockPos pos) {
        for (var location : StardewLocations.hierarchy(level.dimension().location(), pos)) {
            var property = location.property(FARM_INSTANCE);
            if (property.isPresent()) return property;
        }
        return Optional.empty();
    }

    public static boolean isBound(Level level, BlockPos pos) { return binding(level, pos).isPresent(); }

    /** Unbound workshop locations retain their existing rules; malformed bindings fail closed. */
    public static boolean canModifyAt(ServerPlayer player, BlockPos pos) {
        return !isBound(player.level(), pos) || farmInstance(player.level(), pos)
                .map(id -> canModify(player, id)).orElse(false);
    }

    public static Optional<FarmInstance> farm(Level level, BlockPos pos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return Optional.empty();
        return farmInstance(level, pos).map(id -> FarmInstanceRegistry.get(server.getServer()).getFarmByInstanceId(id));
    }

    public static boolean canModify(ServerPlayer player, UUID farmId) {
        var registry = FarmInstanceRegistry.get(player.server);
        var farm = registry.getFarmByInstanceId(farmId);
        if (farm == null) return false;
        var key = registry.getRegistryKey(farm);
        return key != null && FarmPermissionManager.get().canModify(key, player.getUUID());
    }
}
