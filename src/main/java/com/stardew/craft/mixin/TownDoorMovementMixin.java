package com.stardew.craft.mixin;

import com.stardew.craft.interior.door.DoorMovementQueue;
import com.stardew.craft.interior.door.TownDoorRuntime;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

/** MinecraftForge payload scheduling can deliver the destination movement before the portal request. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TownDoorMovementMixin implements DoorMovementQueue {
    @Shadow public ServerPlayer player;
    @Shadow private Vec3 awaitingPositionFromClient;
    @Unique private final ArrayDeque<ServerboundMovePlayerPacket> stardewcraft$townDoorMoves = new ArrayDeque<>();
    @Unique private int stardewcraft$townDoorMoveDeadline;
    @Unique private boolean stardewcraft$replayingTownDoorMoves;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void stardewcraft$orderTownDoorMove(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        if (stardewcraft$replayingTownDoorMoves) return;
        if (stardewcraft$townDoorMoves.size() >= 32) {
            stardewcraft$drainTownDoorMoves(true);
            return;
        }
        if (stardewcraft$townDoorMoves.isEmpty()) {
            if (!stardewcraft$isEarlyTownDoorDestination(packet)) return;
            stardewcraft$townDoorMoveDeadline = player.server.getTickCount() + 3;
        }
        stardewcraft$townDoorMoves.addLast(packet);
        callback.cancel();
    }

    @Unique
    private boolean stardewcraft$isEarlyTownDoorDestination(ServerboundMovePlayerPacket packet) {
        if (!packet.hasPosition()) return false;
        Vec3 target = new Vec3(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ()));
        return TownDoorRuntime.isEarlyDestination(player, target);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void stardewcraft$expireTownDoorMoveQueue(CallbackInfo callback) {
        if (!stardewcraft$townDoorMoves.isEmpty() && player.server.getTickCount() >= stardewcraft$townDoorMoveDeadline) {
            // No accepted portal request: let vanilla validate the original packets normally.
            stardewcraft$drainTownDoorMoves(true);
        }
    }

    @Override
    public void stardewcraft$flushDoorMoves() {
        stardewcraft$drainTownDoorMoves(false);
    }

    @Override
    public boolean stardewcraft$awaitingDoorCorrection() { return awaitingPositionFromClient != null; }

    @Override
    public void stardewcraft$discardDoorMoves() { stardewcraft$townDoorMoves.clear(); }

    @Unique
    private void stardewcraft$drainTownDoorMoves(boolean expired) {
        if (stardewcraft$replayingTownDoorMoves) return;
        stardewcraft$replayingTownDoorMoves = true;
        try {
            var listener = (ServerGamePacketListenerImpl) (Object) this;
            while (!stardewcraft$townDoorMoves.isEmpty()) {
                // A fast return trip can already be queued behind the first crossing.
                // It must wait for its own independently validated portal request.
                if (!expired && stardewcraft$isEarlyTownDoorDestination(stardewcraft$townDoorMoves.getFirst())) {
                    stardewcraft$townDoorMoveDeadline = player.server.getTickCount() + 3;
                    break;
                }
                listener.handleMovePlayer(stardewcraft$townDoorMoves.removeFirst());
            }
        } finally {
            stardewcraft$replayingTownDoorMoves = false;
        }
    }
}
