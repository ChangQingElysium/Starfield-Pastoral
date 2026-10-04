package com.stardew.craft.gingerisland;

import com.stardew.craft.api.v1.world.StardewLocation;
import com.stardew.craft.api.v1.world.StardewLocations;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Resolves a constructed island for the player's farm; never allocates or generates an island. */
@EventBusSubscriber(modid = "stardewcraft")
public final class GingerIslandArrivalService {
    /** Feet coordinates, e.g. "25 65 47". The farm binding may be inherited from the island parent. */
    public static final ResourceLocation ARRIVAL = id("ginger_arrival");
    private static final ResourceLocation ARRIVAL_TOTEM = id("ginger_island_arrival_totem");
    private static final Map<UUID, ServerPlayer> PREPARING = new HashMap<>();

    public record Destination(ResourceLocation dimension, BlockPos feet, UUID farmId, ResourceLocation locationId) {
        public ServerLevel level(MinecraftServer server) {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        }
    }

    private GingerIslandArrivalService() {}

    public static Optional<Destination> resolve(ServerPlayer player) {
        var farm = FarmInstanceRegistry.get(player.server).getFarmForPlayer(player.getUUID());
        return farm == null ? Optional.empty() : resolve(farm.getInstanceId(), StardewLocations.all());
    }

    /** Metadata-only resolution: no chunk read, ticket, world mutation or public-island fallback. */
    static Optional<Destination> resolve(UUID farmId, List<StardewLocation> locations) {
        Destination found = null;
        for (var location : locations) {
            var arrival = location.property(ARRIVAL);
            if (arrival.isEmpty() || !farmId.toString().equals(farmBinding(location, locations))) continue;
            if (found != null) return Optional.empty();
            try {
                String[] coordinates = arrival.get().trim().split("\\s+");
                if (coordinates.length != 3) return Optional.empty();
                var feet = new BlockPos(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]),
                        Integer.parseInt(coordinates[2]));
                if (!location.contains(location.dimension(), feet)
                        || !location.contains(location.dimension(), feet.above())) return Optional.empty();
                found = new Destination(location.dimension(), feet, farmId, location.id());
            } catch (IllegalArgumentException invalid) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(found);
    }

    private static String farmBinding(StardewLocation location, List<StardewLocation> locations) {
        Set<ResourceLocation> seen = new HashSet<>();
        while (location != null && seen.add(location.id())) {
            var binding = location.property(IslandContext.FARM_INSTANCE);
            if (binding.isPresent()) return binding.get();
            ResourceLocation parent = location.parentId();
            location = parent == null ? null : locations.stream().filter(l -> l.id().equals(parent)).findFirst().orElse(null);
        }
        return "";
    }

    /** Only an explicit travel request loads its already-saved destination chunk. UNKNOWN tickets expire normally. */
    public static boolean prepare(ServerPlayer player, Consumer<Destination> ready) {
        if (!player.isAlive() || PREPARING.putIfAbsent(player.getUUID(), player) != null) return false;
        var destination = resolve(player).orElse(null);
        var target = destination == null ? null : destination.level(player.server);
        if (target == null) {
            PREPARING.remove(player.getUUID(), player);
            unavailable(player);
            return false;
        }
        var landingChunk = new ChunkPos(destination.feet());
        var poleChunk = new ChunkPos(destination.feet().north());
        savedChunk(player, destination, landingChunk)
                .thenComposeAsync(loaded -> !loaded || poleChunk.equals(landingChunk)
                        ? CompletableFuture.completedFuture(loaded) : savedChunk(player, destination, poleChunk), player.server)
                .whenCompleteAsync((loaded, failure) -> {
                    if (failure == null && Boolean.TRUE.equals(loaded)) finishPreparation(player, destination, ready);
                    else {
                        PREPARING.remove(player.getUUID(), player);
                        if (connected(player)) unavailable(player);
                    }
                }, player.server);
        return true;
    }

    private static CompletableFuture<Boolean> savedChunk(ServerPlayer player, Destination destination, ChunkPos pos) {
        var target = destination.level(player.server);
        if (!connected(player) || !resolve(player).filter(destination::equals).isPresent()) {
            return CompletableFuture.completedFuture(false);
        }
        if (target.getChunkSource().getChunkNow(pos.x, pos.z) != null) return CompletableFuture.completedFuture(true);
        // A stale metadata entry must not cause Minecraft to generate a blank arrival chunk.
        return target.getChunkSource().chunkMap.read(pos).thenComposeAsync(saved -> {
            if (saved.isEmpty() || !(saved.get().getString("Status").equals("minecraft:full")
                    || saved.get().getString("Status").equals("full"))
                    || !connected(player) || !resolve(player).filter(destination::equals).isPresent()) {
                return CompletableFuture.completedFuture(false);
            }
            return target.getChunkSource().getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true)
                    .thenApply(chunk -> chunk.left().isPresent());
        }, player.server);
    }

    private static void finishPreparation(ServerPlayer player, Destination destination, Consumer<Destination> ready) {
        PREPARING.remove(player.getUUID(), player);
        if (!connected(player)) return;
        if (isUsable(player, destination)) ready.accept(destination);
        else unavailable(player);
    }

    /** Recheck ownership and the exact authored landing before consuming or moving the player. */
    public static boolean isUsable(ServerPlayer player, Destination destination) {
        if (!player.isAlive() || !resolve(player).filter(destination::equals).isPresent()) return false;
        var target = destination.level(player.server);
        var feet = destination.feet();
        if (target == null || !target.hasChunkAt(feet) || !target.hasChunkAt(feet.north())
                || !IslandContext.farmInstance(target, feet).filter(destination.farmId()::equals).isPresent()) return false;
        var pole = target.getBlockState(feet.north());
        if (!BuiltInRegistries.BLOCK.getKey(pole.getBlock()).equals(ARRIVAL_TOTEM)
                || !pole.hasProperty(MapDecorStaticBlock.PART)
                || !pole.hasProperty(MapDecorStaticBlock.FACING)
                || pole.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.MAIN
                || pole.getValue(MapDecorStaticBlock.FACING) != Direction.SOUTH) return false;
        var head = feet.above();
        return target.getBlockState(feet.below()).isFaceSturdy(target, feet.below(), Direction.UP)
                && target.getFluidState(feet).isEmpty() && target.getFluidState(head).isEmpty()
                && target.getBlockState(feet).getCollisionShape(target, feet).isEmpty()
                && target.getBlockState(head).getCollisionShape(target, head).isEmpty();
    }

    public static void unavailable(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.stardewcraft.wizard_building.island_unavailable"), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PREPARING.remove(player.getUUID(), player);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { PREPARING.clear(); }

    private static boolean connected(ServerPlayer player) {
        return player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static ResourceLocation id(String path) { return new ResourceLocation("stardewcraft", path); }
}
