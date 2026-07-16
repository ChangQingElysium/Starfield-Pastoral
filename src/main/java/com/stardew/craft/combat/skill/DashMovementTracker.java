package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.DashMovementPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("null")
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class DashMovementTracker {

    private static final class State {
        private final UUID playerId;
        private final ResourceKey<Level> dimension;
        private final Vec3 end;
        private final Vec3 step;
        private final long endTick;
        private State(UUID playerId, ResourceKey<Level> dimension, Vec3 end, Vec3 step, long endTick) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.end = end;
            this.step = step;
            this.endTick = endTick;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private DashMovementTracker() {}

    @SuppressWarnings("null")
    public static void start(ServerPlayer player, long nowTick, Vec3 end, int durationTicks) {
        if (player == null || end == null || durationTicks <= 0) {
            return;
        }
        Vec3 start = player.position();
        Vec3 diff = end.subtract(start);
        Vec3 step = new Vec3(diff.x / durationTicks, 0.0, diff.z / durationTicks);
        ACTIVE.put(player.getUUID(), new State(player.getUUID(), player.level().dimension(), end, step,
            nowTick + durationTicks));
        sendClientState(player, true, durationTicks, end);
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || ACTIVE.isEmpty()) {
            return;
        }
        Iterator<State> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            State state = iterator.next();
            if (state == null) {
                sendClientState(server, state, false, 0, null);
                iterator.remove();
                continue;
            }
            ServerLevel level = server.getLevel(state.dimension);
            if (level == null) {
                sendClientState(server, state, false, 0, null);
                iterator.remove();
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (player.level() != level) {
                sendClientState(player, false, 0, null);
                iterator.remove();
                continue;
            }
            if (com.stardew.craft.time.StardewTimePauseService.shouldPauseLevel(level)) {
                continue;
            }
            long nowTick = level.getGameTime();
            if (nowTick > state.endTick) {
                sendClientState(server, state, false, 0, null);
                iterator.remove();
                continue;
            }

            Vec3 current = player.position();
            Vec3 desired = current.add(state.step);
            if (nowTick + 1 >= state.endTick) {
                desired = state.end;
            }
            desired = new Vec3(desired.x, player.getY(), desired.z);

            Vec3 safe = findSafePosition(player, adjustForCollision(player, desired));
            if (safe == null) {
                sendClientState(player, false, 0, null);
                iterator.remove();
                continue;
            }

            Vec3 desiredVel = safe.subtract(current);
            Vec3 currentVel = player.getDeltaMovement();
            Vec3 nextVel = currentVel.add(desiredVel.subtract(currentVel).scale(0.6));
            player.setDeltaMovement(nextVel.x, currentVel.y, nextVel.z);
            player.hasImpulse = true;
            player.move(net.minecraft.world.entity.MoverType.SELF, player.getDeltaMovement());
            player.fallDistance = 0.0F;

            Vec3 afterMove = player.position();
            if (afterMove.subtract(current).horizontalDistanceSqr() < 1.0e-4) {
                player.teleportTo(safe.x, safe.y, safe.z);
                player.fallDistance = 0.0F;
                afterMove = player.position();
            }

        }
    }

    @SuppressWarnings("null")
    private static void sendClientState(MinecraftServer server, State state, boolean active,
                                        int durationTicks, Vec3 end) {
        if (server == null || state == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
        if (player == null) return;
        sendClientState(player, active, durationTicks, end);
    }

    @SuppressWarnings("null")
    private static void sendClientState(ServerPlayer player, boolean active,
                                        int durationTicks, Vec3 end) {
        if (player == null) return;
        double endX = end != null ? end.x : 0.0;
        double endY = end != null ? end.y : 0.0;
        double endZ = end != null ? end.z : 0.0;
        PacketDistributor.sendToPlayer(player,
            new DashMovementPayload(active, durationTicks, endX, endY, endZ));
    }

    @SuppressWarnings("null")
    private static Vec3 adjustForCollision(ServerPlayer player, Vec3 desired) {
        Vec3 start = player.position();
        Vec3 look = desired.subtract(start);
        if (look.lengthSqr() < 1.0E-6) {
            return desired;
        }
        Vec3 dir = new Vec3(look.x, 0.0, look.z).normalize();
        HitResult hit = player.level().clip(new ClipContext(
            start.add(0, player.getBbHeight() * 0.5, 0),
            desired.add(0, player.getBbHeight() * 0.5, 0),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));

        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 hitPos = hit.getLocation();
            return hitPos.subtract(dir.scale(0.4));
        }
        return desired;
    }

    @SuppressWarnings("null")
    private static Vec3 findSafePosition(Player player, Vec3 desired) {
        if (desired == null) return null;
        AABB box = player.getBoundingBox().move(desired.x - player.getX(), desired.y - player.getY(), desired.z - player.getZ());
        if (player.level().noCollision(player, box)) {
            return desired;
        }
        Vec3 raised = desired.add(0, 0.25, 0);
        AABB boxUp = player.getBoundingBox().move(raised.x - player.getX(), raised.y - player.getY(), raised.z - player.getZ());
        if (player.level().noCollision(player, boxUp)) {
            return raised;
        }
        return null;
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
