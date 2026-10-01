package com.stardew.craft.client.interior;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.interior.door.TownDoorDefinitions;
import com.stardew.craft.interior.door.TownDoorNetwork;
import net.minecraft.client.Camera;
import com.stardew.craft.port.net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TownDoorClient {
    private static ClientLevel level;
    private static List<TownDoorNetwork.DoorState> doors = List.of();
    private static Vec3 previousFeet;
    private static int sequence;
    private static boolean awaitingCorrection;
    private static int suppressBlockOverlayFrames;
    private static final ArrayDeque<Integer> PENDING = new ArrayDeque<>();
    private static final Map<Integer, Integer> LAST_REVISIONS = new HashMap<>();
    private static final TownDoorRenderer RENDERER = new TownDoorRenderer();

    private TownDoorClient() {}

    public static void register(IEventBus bus) {
        TownDoorNetwork.receiveState = state -> {
            ClientLevel current = Minecraft.getInstance().level;
            if (current != level) resetLevel(current);

            // Payloads are ordered on the play connection, but keeping the revision guard here
            // protects the render state if a reconnect or another client-side queue replays an
            // older snapshot. A stale snapshot must not undo a newer open/closed transition.
            for (TownDoorNetwork.DoorState door : state.doors()) {
                Integer last = LAST_REVISIONS.get(door.id());
                if (last != null && door.revision() < last) return;
            }
            Map<Integer, TownDoorNetwork.DoorState> previous = new HashMap<>();
            for (TownDoorNetwork.DoorState door : doors) previous.put(door.id(), door);
            Map<Integer, TownDoorNetwork.DoorState> incoming = new HashMap<>();
            for (TownDoorNetwork.DoorState door : state.doors()) incoming.put(door.id(), door);

            for (TownDoorNetwork.DoorState old : doors) {
                TownDoorNetwork.DoorState next = incoming.get(old.id());
                if (next == null || !old.equals(next)) RENDERER.invalidateDoor(old.id());
            }
            for (TownDoorNetwork.DoorState door : state.doors()) {
                TownDoorNetwork.DoorState old = previous.get(door.id());
                LAST_REVISIONS.merge(door.id(), door.revision(), Math::max);
                if (!door.equals(old)) {
                    RENDERER.invalidateDoor(door.id());
                }
                // Re-apply even when the logical payload is unchanged. Another chunk update may
                // have arrived while this doorway was unloaded, leaving the client mesh stale.
                applyDoorBlockState(current, door);
            }
            doors = state.doors();
            if (doors.stream().noneMatch(TownDoorNetwork.DoorState::open)) previousFeet = null;
        };
        TownDoorNetwork.receiveAck = ack -> {
            PENDING.removeIf(id -> id <= ack.sequence());
            if (!ack.accepted()) {
                PENDING.clear();
                previousFeet = null;
                awaitingCorrection = true;
            }
        };
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            resetLevel(null);
            sequence = 0;
        });
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() == level) resetLevel(null);
        });
    }

    private static void resetLevel(ClientLevel next) {
        level = next;
        doors = List.of();
        LAST_REVISIONS.clear();
        previousFeet = null;
        awaitingCorrection = false;
        suppressBlockOverlayFrames = 0;
        PENDING.clear();
        RENDERER.close();
    }

    /** Applies the authoritative pair state immediately, closing the gap before block packets arrive. */
    private static void applyDoorBlockState(ClientLevel current, TownDoorNetwork.DoorState door) {
        if (current == null) return;
        TownDoorDefinitions.Definition definition = TownDoorDefinitions.ALL.stream()
                .filter(candidate -> candidate.id() == door.id())
                .findFirst().orElse(null);
        if (definition == null) return;
        for (net.minecraft.core.BlockPos pos : definition.outsideDoors()) applyDoorHalf(current, pos, door.open());
        for (net.minecraft.core.BlockPos pos : definition.insideDoors()) applyDoorHalf(current, pos, door.open());
    }

    private static void applyDoorHalf(ClientLevel level, net.minecraft.core.BlockPos lowerPos, boolean open) {
        applyDoorState(level, lowerPos, open);
        applyDoorState(level, lowerPos.above(), open);
    }

    private static void applyDoorState(ClientLevel level, net.minecraft.core.BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock)) return;
        BlockState updated = state.setValue(DoorBlock.OPEN, open).setValue(DoorBlock.POWERED, false);
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** Runs before the outer render updates its camera and visibility state. */
    public static void beforeRender(DeltaTracker delta) {
        if (suppressBlockOverlayFrames > 0) suppressBlockOverlayFrames--;
        beforeCamera(delta);
    }

    /** Allocates and clears the stencil immediately before the outer world starts drawing. */
    public static void prepareFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (!doors.isEmpty() && mc.player != null && mc.level != null
                && mc.level.dimension().equals(ModDimensions.STARDEW_VALLEY)) {
            RENDERER.prepareFrame(doors);
        }
    }

    /** Pre-camera traversal order adapted from Immersive Portals (Apache-2.0). */
    private static void beforeCamera(DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) resetLevel(mc.level);
        if (mc.player == null || doors.isEmpty() || awaitingCorrection
                || mc.isPaused() || mc.getCameraEntity() != mc.player || mc.player.isPassenger() || mc.player.isVehicle()
                || !mc.level.dimension().equals(ModDimensions.STARDEW_VALLEY)) {
            previousFeet = null;
            return;
        }
        var player = mc.player;
        Vec3 physical = player.position();
        if (previousFeet != null && PENDING.size() < 8) {
            crossing:
            for (TownDoorNetwork.DoorState door : doors) {
                if (!door.open()) continue;
                for (int side = 0; side < 2; side++) {
                    boolean entering = side == 0;
                    if (!(entering ? door.enter() : door.exit())) continue;
                    if (!door.connection().crosses(previousFeet, physical,
                            player.getBbWidth(), player.getBbHeight(), entering)) continue;
                    Vec3 destination = door.connection().movementDestination(mc.level, player, physical, entering);
                    if (destination == null) continue;
                    Vec3 shift = destination.subtract(physical);
                    int id = ++sequence;
                    PacketDistributor.sendToServer(new TownDoorNetwork.Cross(id, door.id(), door.revision(),
                            entering, previousFeet, physical));
                    PENDING.addLast(id);
                    player.setPos(physical.add(shift));
                    player.xo += shift.x; player.yo += shift.y; player.zo += shift.z;
                    player.xOld += shift.x; player.yOld += shift.y; player.zOld += shift.z;
                    suppressBlockOverlayFrames = 5;
                    RENDERER.onClientTeleport(door.id());
                    physical = destination;
                    break crossing;
                }
            }
        }
        previousFeet = physical;
    }

    public static void corrected() {
        previousFeet = null;
        awaitingCorrection = false;
        PENDING.clear();
    }

    /** Avoids the vanilla full-screen in-wall texture while the eye crosses a doorway. */
    public static boolean suppressBlockOverlay() {
        if (suppressBlockOverlayFrames > 0) return true;
        Minecraft mc = Minecraft.getInstance();
        if (doors.isEmpty() || mc.player == null || mc.level == null
                || !mc.level.dimension().equals(ModDimensions.STARDEW_VALLEY)) return false;
        Vec3 eye = mc.player.getEyePosition(mc.getTimer().getGameTimeDeltaPartialTick(true));
        for (TownDoorNetwork.DoorState door : doors) {
            if (!door.open()) continue;
            for (int side = 0; side < 2; side++) {
                boolean entering = side == 0;
                Vec3 center = door.connection().origin(entering);
                if (Math.abs(door.connection().distance(eye, entering)) <= .35
                        && Math.abs(eye.x - center.x) <= door.connection().width() * .5 + .2
                        && Math.abs(eye.y - center.y) <= 1.2) return true;
            }
        }
        return false;
    }

    /** Third-person camera rays continue through the closest crossed doorway. */
    public static net.minecraft.world.phys.BlockHitResult clipCamera(net.minecraft.world.level.BlockGetter world,
                                                                    net.minecraft.world.level.ClipContext context) {
        var hit = world.clip(context);
        CameraCrossing portal = nearestCameraCrossing(context.getFrom(), context.getTo());
        if (portal == null) return hit;
        Vec3 from = context.getFrom(), to = context.getTo();
        Vec3 crossing = from.lerp(to, portal.fraction());
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                && hit.getLocation().distanceToSqr(from) < crossing.distanceToSqr(from) - 1e-5) return hit;
        Vec3 shift = portal.door().connection().translation(portal.entering());
        Vec3 start = crossing.add(shift).add(to.subtract(from).normalize().scale(.001));
        var remote = world.clip(new net.minecraft.world.level.ClipContext(start, to.add(shift),
                net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE,
                Minecraft.getInstance().player));
        Vec3 translated = remote.getLocation().subtract(shift);
        return remote.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? net.minecraft.world.phys.BlockHitResult.miss(translated, remote.getDirection(), remote.getBlockPos())
                : new net.minecraft.world.phys.BlockHitResult(translated, remote.getDirection(), remote.getBlockPos(), remote.isInside());
    }

    public static Vec3 cameraPosition(Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        if (doors.isEmpty() || mc.player == null || mc.options.getCameraType().isFirstPerson()
                || mc.getCameraEntity() != mc.player) return camera;
        Vec3 eye = mc.player.getEyePosition(mc.getTimer().getGameTimeDeltaPartialTick(true));
        CameraCrossing portal = nearestCameraCrossing(eye, camera);
        return portal == null ? camera : camera.add(portal.door().connection().translation(portal.entering()));
    }

    private static CameraCrossing nearestCameraCrossing(Vec3 from, Vec3 to) {
        CameraCrossing best = null;
        for (TownDoorNetwork.DoorState door : doors) {
            if (!door.open()) continue;
            for (int side = 0; side < 2; side++) {
                boolean entering = side == 0;
                double fraction = cameraCrossing(door, from, to, entering);
                if (!Double.isNaN(fraction) && (best == null || fraction < best.fraction())) {
                    best = new CameraCrossing(door, entering, fraction);
                }
            }
        }
        return best;
    }

    private static double cameraCrossing(TownDoorNetwork.DoorState door, Vec3 from, Vec3 to, boolean entering) {
        double before = door.connection().distance(from, entering);
        double after = door.connection().distance(to, entering);
        if (before <= 0 || after >= 0) return Double.NaN;
        double fraction = before / (before - after);
        Vec3 point = from.lerp(to, fraction), center = door.connection().origin(entering);
        return Math.abs(point.x - center.x) <= door.connection().width() * .5
                && Math.abs(point.y - center.y) <= 1 ? fraction : Double.NaN;
    }

    public static void renderPortal(DeltaTracker delta, Camera camera, Matrix4f view, Matrix4f projection) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) resetLevel(mc.level);
        if (!doors.isEmpty() && mc.player != null && mc.level != null
                && mc.level.dimension().equals(ModDimensions.STARDEW_VALLEY)) {
            RENDERER.render(delta, camera, view, projection);
        }
    }

    private record CameraCrossing(TownDoorNetwork.DoorState door, boolean entering, double fraction) {}
}
