package com.stardew.craft.world;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.world.StardewLocationTransitionRegistry;
import com.stardew.craft.api.v1.world.StardewLocationTransition;
import com.stardew.craft.api.v1.world.StardewLocations;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Tracks final logical-location changes after all movement for a tick. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class PlayerLocationTransitionService {
    private static final Map<UUID, ObservedLocation> OBSERVED =
            new HashMap<>();

    private PlayerLocationTransitionService() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.isRemoved()) {
            return;
        }
        observe(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ObservedLocation previous = OBSERVED.remove(player.getUUID());
        if (previous == null || previous.locationId() == null) {
            return;
        }
        ResourceLocation currentDimension =
                player.level().dimension().location();
        BlockPos currentPosition = player.blockPosition();
        StardewLocationTransitionRegistry.dispatch(
                new StardewLocationTransition(
                        player,
                        previous.locationId(),
                        null,
                        previous.dimension(),
                        currentDimension,
                        previous.position(),
                        currentPosition,
                        StardewLocationTransition.Reason.LOGOUT));
    }

    static void observe(ServerPlayer player) {
        ResourceLocation dimension =
                player.level().dimension().location();
        BlockPos position = player.blockPosition();
        ResourceLocation locationId = StardewLocations
                .find(dimension, position)
                .map(location -> location.id())
                .orElse(null);
        ObservedLocation current = new ObservedLocation(
                dimension, position, locationId);
        ObservedLocation previous = OBSERVED.put(
                player.getUUID(), current);
        if (previous == null) {
            if (locationId != null) {
                StardewLocationTransitionRegistry.dispatch(
                        transition(
                                player,
                                current,
                                current,
                                null,
                                locationId,
                                StardewLocationTransition.Reason.INITIAL));
            }
            return;
        }
        boolean dimensionChanged = !previous.dimension().equals(dimension);
        boolean locationChanged = !Objects.equals(
                previous.locationId(), locationId);
        if (!dimensionChanged && !locationChanged) {
            return;
        }
        // Game1.ShouldDismountOnWarp: going from an outdoor location to an indoor one drops a rider off the horse.
        if (player.getVehicle() instanceof net.minecraft.world.entity.animal.horse.AbstractHorse
                && isIndoor(locationId) && !isIndoor(previous.locationId())) {
            player.stopRiding();
        }
        StardewLocationTransitionRegistry.dispatch(
                transition(
                        player,
                        previous,
                        current,
                        previous.locationId(),
                        locationId,
                        dimensionChanged
                                ? StardewLocationTransition.Reason
                                        .DIMENSION_CHANGED
                                : StardewLocationTransition.Reason
                                        .LOCATION_CHANGED));
    }

    private static boolean isIndoor(ResourceLocation locationId) {
        return locationId != null && StardewLocations.get(locationId)
                .map(com.stardew.craft.api.v1.world.StardewLocation::indoor).orElse(false);
    }

    private static StardewLocationTransition transition(
            ServerPlayer player,
            ObservedLocation previous,
            ObservedLocation current,
            ResourceLocation previousLocation,
            ResourceLocation currentLocation,
            StardewLocationTransition.Reason reason
    ) {
        return new StardewLocationTransition(
                player,
                previousLocation,
                currentLocation,
                previous.dimension(),
                current.dimension(),
                previous.position(),
                current.position(),
                reason);
    }

    private record ObservedLocation(
            ResourceLocation dimension,
            BlockPos position,
            ResourceLocation locationId
    ) {
        private ObservedLocation {
            position = position.immutable();
        }
    }
}
