package com.stardew.craft.interior.door;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.cutscene.server.ServerCutsceneTracker;
import com.stardew.craft.event.InteriorPortalInteractionEvents;
import com.stardew.craft.festival.FairFestivalService;
import com.stardew.craft.interior.CrossDimensionTeleporter;
import com.stardew.craft.interior.door.TownDoorDefinitions.Definition;
import com.stardew.craft.interior.door.TownDoorDefinitions.LegacyArea;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.shop.ShopHoursService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runtime for the fixed pregen town doors. Door blocks own lifetime; there are no portal entities or tickets. */
public final class TownDoorRuntime {
    private static final double SYNC_RANGE = 32.0;
    private static final double KEEP_OPEN_RANGE = 16.0;
    private static final int NPC_HOLD_OPEN_TICKS = 40;
    private static final int QUIET_BLOCK_UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Door transitions must reach both the client block map and the neighboring shape cache. */
    private static final int DOOR_BLOCK_UPDATE = Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<ServerLevel, TownDoorRuntime> LEVELS = new IdentityHashMap<>();

    private final Map<Integer, PairState> pairs = new LinkedHashMap<>();
    private final Map<UUID, TownDoorNetwork.State> sentStates = new HashMap<>();
    private final Map<UUID, Integer> sequences = new HashMap<>();

    private TownDoorRuntime() {
        for (Definition definition : TownDoorDefinitions.ALL) {
            pairs.put(definition.id(), new PairState(definition));
        }
    }

    public static void register(IEventBus bus) {
        bus.addListener(TownDoorNetwork::register);
        MinecraftForge.EVENT_BUS.addListener(TownDoorRuntime::tick);
        MinecraftForge.EVENT_BUS.addListener(TownDoorRuntime::interact);
        MinecraftForge.EVENT_BUS.addListener(TownDoorRuntime::unload);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> LEVELS.values().forEach(runtime -> {
            runtime.sentStates.remove(event.getEntity().getUUID());
            runtime.sequences.remove(event.getEntity().getUUID());
        }));
        if (FMLEnvironment.dist == Dist.CLIENT) com.stardew.craft.client.interior.TownDoorClient.register(bus);
        StardewCraft.LOGGER.info("[TOWN-DOOR] {} native doorway pairs enabled; no portal entity or external engine",
                TownDoorDefinitions.ALL.size());
    }

    private static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.STARDEW_VALLEY)) return;
        TownDoorRuntime runtime = LEVELS.computeIfAbsent(level, ignored -> new TownDoorRuntime());
        for (PairState pair : runtime.pairs.values()) runtime.tickPair(level, pair);
        if (level.getGameTime() % 5 == 0) runtime.sync(level);
    }

    private void tickPair(ServerLevel level, PairState pair) {
        boolean playerNearby = level.players().stream().anyMatch(player -> nearby(player, pair.connection, SYNC_RANGE));
        boolean npcHoldingOpen = pair.npcHoldOpenUntil >= level.getGameTime();
        if (!playerNearby && !npcHoldingOpen) {
            if (pair.prepared && pair.open) setOpen(level, pair, false);
            return;
        }
        if (!chunksLoaded(level, pair.definition)) {
            if (pair.prepared) {
                pair.prepared = false;
                pair.revision++;
            }
            return;
        }
        if (!pair.prepared) {
            pair.prepared = prepareDoorway(level, pair.definition);
            if (pair.prepared) {
                pair.connection = connection(level, pair.definition);
                pair.open = allDoorsOpen(level, pair.definition);
                pair.revision++;
            }
        }
        if (!pair.prepared) return;
        if (!validDoors(level, pair.definition)) {
            pair.prepared = false;
            pair.revision++;
            return;
        }
        if (pair.open && !npcHoldingOpen
                && level.players().stream().noneMatch(player -> nearby(player, pair.connection, KEEP_OPEN_RANGE))) {
            setOpen(level, pair, false);
        }
        if (pair.pendingClose) setOpen(level, pair, false);
        // The pair state is authoritative. Repair a missed half-door or a missed side instead
        // of deriving a new pair state from one lower half. A client can otherwise remain stale
        // forever because the server sees the two lower blocks as already open.
        else if (!allDoorHalvesMatch(level, pair.definition, pair.open)) setOpen(level, pair, pair.open);
        DoorConnection current = connection(level, pair.definition);
        if (!pair.connection.equals(current)) {
            pair.connection = current;
            pair.revision++;
        }
    }

    private static boolean chunksLoaded(ServerLevel level, Definition definition) {
        for (BlockPos pos : concat(definition.outsideDoors(), definition.insideDoors())) {
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return false;
        }
        return true;
    }

    private static boolean nearby(ServerPlayer player, DoorConnection connection, double range) {
        double rangeSqr = range * range;
        return player.position().distanceToSqr(connection.outside()) < rangeSqr
                || player.position().distanceToSqr(connection.inside()) < rangeSqr;
    }

    private void sync(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            List<TownDoorNetwork.DoorState> visible = new ArrayList<>();
            for (PairState pair : pairs.values()) {
                if (!pair.prepared || !nearby(player, pair.connection, SYNC_RANGE)) continue;
                visible.add(new TownDoorNetwork.DoorState(pair.definition.id(), pair.revision, pair.open,
                        pair.open && canTraverse(player, pair, true),
                        pair.open && canTraverse(player, pair, false), pair.connection));
            }
            TownDoorNetwork.State state = new TownDoorNetwork.State(visible);
            TownDoorNetwork.State previous = sentStates.get(player.getUUID());
            if (visible.isEmpty() && previous == null) continue;
            if (!state.equals(previous)) PacketDistributor.sendToPlayer(player, state);
            if (visible.isEmpty()) sentStates.remove(player.getUUID());
            else sentStates.put(player.getUUID(), state);
        }
    }

    private static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().dimension().equals(ModDimensions.STARDEW_VALLEY)) return;
        Definition definition = findDoor(event.getPos());
        if (definition == null || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof DoorBlock)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        TownDoorRuntime runtime = LEVELS.computeIfAbsent(player.serverLevel(), ignored -> new TownDoorRuntime());
        PairState pair = runtime.pairs.get(definition.id());
        if (!runtime.ensurePrepared(player.serverLevel(), pair) || ServerCutsceneTracker.isActive(player.getUUID())) return;
        boolean entering = definition.isOutsideDoor(event.getPos());
        if (!pair.open && !authorizeOpen(player, pair, entering, true)) return;
        runtime.setOpen(player.serverLevel(), pair, !pair.open);
        runtime.sync(player.serverLevel());
    }

    /** Converts a surviving legacy trigger interaction into opening its authored door. */
    public static void handleLegacyInteraction(ServerPlayer player, String targetId) {
        String normalized = targetId.startsWith("sdv_portal_target:")
                ? targetId.substring("sdv_portal_target:".length()) : targetId;
        Definition definition = TownDoorDefinitions.ALL.stream()
                .filter(pair -> pair.enterTarget().equals(normalized) || pair.exitTarget().equals(normalized))
                .findFirst().orElse(null);
        if (definition == null) return;
        TownDoorRuntime runtime = LEVELS.computeIfAbsent(player.serverLevel(), ignored -> new TownDoorRuntime());
        PairState pair = runtime.pairs.get(definition.id());
        boolean entering = definition.enterTarget().equals(normalized);
        if (runtime.ensurePrepared(player.serverLevel(), pair) && authorizeOpen(player, pair, entering, true)) {
            runtime.setOpen(player.serverLevel(), pair, true);
            runtime.sync(player.serverLevel());
        }
    }

    /** Lets an NPC schedule use a native town door without entering the player traversal protocol. */
    public static boolean openForNpc(ServerLevel level, BlockPos doorPos) {
        if (!level.dimension().equals(ModDimensions.STARDEW_VALLEY)) return false;
        Definition definition = findDoor(doorPos);
        if (definition == null) definition = findNearbyDoor(doorPos);
        if (definition == null) return false;
        TownDoorRuntime runtime = LEVELS.computeIfAbsent(level, ignored -> new TownDoorRuntime());
        PairState pair = runtime.pairs.get(definition.id());
        if (!runtime.ensurePrepared(level, pair)) return false;
        pair.npcHoldOpenUntil = Math.max(pair.npcHoldOpenUntil,
                level.getGameTime() + NPC_HOLD_OPEN_TICKS);
        if (!pair.open || !allDoorsOpen(level, pair.definition)) {
            runtime.setOpen(level, pair, true);
            runtime.sync(level);
        }
        return true;
    }

    private boolean ensurePrepared(ServerLevel level, PairState pair) {
        if (!pair.prepared && chunksLoaded(level, pair.definition)) {
            pair.prepared = prepareDoorway(level, pair.definition);
            if (pair.prepared) {
                pair.connection = connection(level, pair.definition);
                pair.open = allDoorsOpen(level, pair.definition);
                pair.revision++;
            }
        }
        return pair.prepared;
    }

    private static boolean canTraverse(ServerPlayer player, PairState pair, boolean entering) {
        if (!player.isAlive() || ServerCutsceneTracker.isActive(player.getUUID())
                || player.isPassenger() || player.isVehicle()) return false;
        if (entering) {
            if (FairFestivalService.isParticipant(player)) return false;
            return ShopHoursService.closedPortalReason(player, pair.definition.enterTarget()) == null;
        }
        if ("museum_exit".equals(pair.definition.exitTarget())) {
            return !com.stardew.craft.museum.MuseumDonationData.get(player.serverLevel())
                    .isDonationModeActive(player.getUUID());
        }
        if ("wizard_tower_exit".equals(pair.definition.exitTarget())) {
            var farm = com.stardew.craft.farm.FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
            return (farm == null || farm.isInitialized()) && !wizardReturnsToOverworld(player);
        }
        return true;
    }

    private static boolean authorizeOpen(ServerPlayer player, PairState pair, boolean entering, boolean notify) {
        if (entering) {
            if (FairFestivalService.isParticipant(player)) return false;
            if ("mayor_house_enter".equals(pair.definition.enterTarget())) {
                if (player.getPersistentData().getBoolean("stardewcraft_auction_enter_house_once")) {
                    player.getPersistentData().remove("stardewcraft_auction_enter_house_once");
                } else if (com.stardew.craft.auction.AuctionService.tryOpenAuctionEntryChoice(player)) {
                    return false;
                }
            }
            return notify ? !ShopHoursService.blockClosedPortal(player, pair.definition.enterTarget())
                    : ShopHoursService.closedPortalReason(player, pair.definition.enterTarget()) == null;
        }
        if ("museum_exit".equals(pair.definition.exitTarget())
                && com.stardew.craft.museum.MuseumDonationData.get(player.serverLevel())
                .isDonationModeActive(player.getUUID())) {
            if (notify) PacketDistributor.sendToPlayer(player,
                    new com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload(
                            "gunther", "stardewcraft.npc.gunther.donation_exit_blocked", 0));
            return false;
        }
        if ("wizard_tower_exit".equals(pair.definition.exitTarget())) {
            var farm = com.stardew.craft.farm.FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
            if (farm != null && !farm.isInitialized()) return false;
            if (wizardReturnsToOverworld(player)) {
                if (notify) CrossDimensionTeleporter.wizardInteriorToOverworld(player);
                return false;
            }
        }
        return true;
    }

    private static boolean wizardReturnsToOverworld(ServerPlayer player) {
        var data = PlayerDataManager.getPlayerData(player);
        return !data.isWizardQuestComplete() && Level.OVERWORLD.equals(data.getWizardSourceDimension());
    }

    public static void cross(ServerPlayer player, TownDoorNetwork.Cross request) {
        TownDoorRuntime runtime = LEVELS.get(player.serverLevel());
        PairState pair = runtime == null ? null : runtime.pairs.get(request.doorId());
        boolean accepted = pair != null && pair.prepared && pair.open
                && request.revision() == pair.revision
                && request.sequence() > runtime.sequences.getOrDefault(player.getUUID(), 0)
                && canTraverse(player, pair, request.entering())
                && validateAndMove(player, pair.connection, request);
        if (runtime != null) runtime.sequences.merge(player.getUUID(), request.sequence(), Math::max);
        PacketDistributor.sendToPlayer(player, new TownDoorNetwork.Ack(request.sequence(), accepted));
        if (accepted) {
            afterCross(player, pair, request.entering());
            if (player.connection instanceof DoorMovementQueue queue) queue.stardewcraft$flushDoorMoves();
        } else {
            if (player.connection instanceof DoorMovementQueue queue) queue.stardewcraft$discardDoorMoves();
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
    }

    private static void afterCross(ServerPlayer player, PairState pair, boolean entering) {
        if (entering) {
            InteriorPortalInteractionEvents.markInteriorEnter(player);
            if ("wizard_tower_enter".equals(pair.definition.enterTarget())) {
                PlayerDataManager.getPlayerData(player).setWizardSourceDimension(ModDimensions.STARDEW_VALLEY);
            }
        } else {
            InteriorPortalInteractionEvents.clearInteriorState(player);
        }
        player.getPersistentData().putLong("stardewcraft_last_portal_tick", player.level().getGameTime());
    }

    /** Server derives the destination and validates the real body before moving anything. */
    static boolean validateAndMove(ServerPlayer player, DoorConnection link, TownDoorNetwork.Cross request) {
        if (player.connection instanceof DoorMovementQueue queue && queue.stardewcraft$awaitingDoorCorrection()) return false;
        if (!DoorConnection.finite(request.after()) || !DoorConnection.finite(request.before())
                || player.position().distanceToSqr(request.after()) > 1.5 * 1.5
                || player.position().distanceToSqr(request.before()) > 1.5 * 1.5
                || !link.crosses(request.before(), request.after(), player.getBbWidth(), player.getBbHeight(), request.entering())) return false;
        Vec3 destination = link.movementDestination(player.serverLevel(), player, request.after(), request.entering());
        if (destination == null) return false;
        AABB target = player.getBoundingBox().move(destination.subtract(player.position()));
        if (!player.serverLevel().hasChunkAt(BlockPos.containing(destination))
                || !player.level().noCollision(player, target)) return false;
        Vec3 shift = destination.subtract(player.position());
        player.setPos(destination);
        player.xo += shift.x; player.yo += shift.y; player.zo += shift.z;
        player.xOld += shift.x; player.yOld += shift.y; player.zOld += shift.z;
        player.connection.resetPosition();
        player.serverLevel().getChunkSource().move(player);
        return true;
    }

    public static boolean isEarlyDestination(ServerPlayer player, Vec3 target) {
        TownDoorRuntime runtime = LEVELS.get(player.serverLevel());
        if (runtime == null || !DoorConnection.finite(target) || target.distanceToSqr(player.position()) < 64) return false;
        for (PairState pair : runtime.pairs.values()) {
            if (!pair.prepared || !pair.open) continue;
            for (int side = 0; side < 2; side++) {
                boolean entering = side == 0;
                Vec3 expected = pair.connection.movementDestinationBase(player.position(), entering);
                if (canTraverse(player, pair, entering)
                        && Math.abs(pair.connection.distance(player.position(), entering)) < .75
                        && target.distanceToSqr(expected) < 2.25) return true;
            }
        }
        return false;
    }

    private static boolean prepareDoorway(ServerLevel level, Definition definition) {
        removeLegacy(level, definition.outsideLegacy());
        removeLegacy(level, definition.insideLegacy());
        if (!validDoors(level, definition)) return false;
        boolean open = allDoorsOpen(level, definition);
        if (definition.outsideDoors().size() == 1 && definition.insideDoors().size() == 1) {
            BlockPos outsidePos = com.stardew.craft.port.PortJava.getFirst(definition.outsideDoors());
            BlockPos insidePos = com.stardew.craft.port.PortJava.getFirst(definition.insideDoors());
            BlockState outsideState = level.getBlockState(outsidePos);
            Direction outsideFacing = doorFacing(level, definition.outsideDoors());
            Direction insideFacing = doorFacing(level, definition.insideDoors());
            Direction leafSide = openLeafSide(outsideFacing, outsideState.getValue(DoorBlock.HINGE));
            normalizeSingleDoor(level, outsidePos, outsideFacing,
                    outsideState.getValue(DoorBlock.HINGE), open);
            normalizeSingleDoor(level, insidePos, insideFacing,
                    hingeForOpenLeafSide(insideFacing, leafSide), open);
        } else {
            normalizeDoorRow(level, definition.outsideDoors(), open);
            normalizeDoorRow(level, definition.insideDoors(), open);
        }
        return true;
    }

    private static void removeLegacy(ServerLevel level, LegacyArea area) {
        for (int dx = 0; dx < area.xSize(); dx++) {
            for (int dz = 0; dz < area.zSize(); dz++) {
                for (int dy = 0; dy < area.height(); dy++) {
                    BlockPos pos = area.base().offset(dx, dy, dz);
                    if (isTrigger(level, pos, area.targetId())) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static boolean isTrigger(ServerLevel level, BlockPos pos, String target) {
        return level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity trigger && target.equals(trigger.getTargetId());
    }

    private static boolean validDoors(ServerLevel level, Definition definition) {
        for (BlockPos pos : concat(definition.outsideDoors(), definition.insideDoors())) {
            BlockState lower = level.getBlockState(pos);
            BlockState upper = level.getBlockState(pos.above());
            if (!(lower.getBlock() instanceof DoorBlock) || !upper.is(lower.getBlock())
                    || lower.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                    || upper.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER) return false;
        }
        return true;
    }

    private static boolean allDoorsOpen(ServerLevel level, Definition definition) {
        return allDoorHalvesMatch(level, definition, true);
    }

    private static boolean allDoorHalvesMatch(ServerLevel level, Definition definition, boolean open) {
        return doorRowMatches(level, definition.outsideDoors(), open)
                && doorRowMatches(level, definition.insideDoors(), open);
    }

    private static boolean doorRowMatches(ServerLevel level, List<BlockPos> doors, boolean open) {
        for (BlockPos pos : doors) {
            BlockState lower = level.getBlockState(pos);
            BlockState upper = level.getBlockState(pos.above());
            if (!(lower.getBlock() instanceof DoorBlock) || !upper.is(lower.getBlock())
                    || lower.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                    || upper.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                    || lower.getValue(DoorBlock.OPEN) != open
                    || upper.getValue(DoorBlock.OPEN) != open
                    || lower.getValue(DoorBlock.POWERED)
                    || upper.getValue(DoorBlock.POWERED)) return false;
        }
        return true;
    }

    private static void normalizeDoorRow(ServerLevel level, List<BlockPos> doors, boolean open) {
        Direction facing = doorFacing(level, doors);
        for (int index = 0; index < doors.size(); index++) {
            BlockPos lowerPos = doors.get(index);
            DoorHingeSide hinge = doors.size() == 1
                    ? level.getBlockState(lowerPos).getValue(DoorBlock.HINGE)
                    : doubleDoorHinge(facing, index, doors.size());
            normalizeSingleDoor(level, lowerPos, facing, hinge, open);
        }
    }

    private static Direction doorFacing(ServerLevel level, List<BlockPos> doors) {
        return doors.stream()
                .map(level::getBlockState)
                .filter(state -> state.getBlock() instanceof DoorBlock)
                .map(state -> state.getValue(DoorBlock.FACING))
                .filter(direction -> direction.getAxis() == Direction.Axis.Z)
                .findFirst()
                .orElse(Direction.SOUTH);
    }

    private static void normalizeSingleDoor(ServerLevel level, BlockPos lowerPos,
                                            Direction facing, DoorHingeSide hinge, boolean open) {
        normalizeDoorHalf(level, lowerPos, DoubleBlockHalf.LOWER, facing, hinge, open);
        normalizeDoorHalf(level, lowerPos.above(), DoubleBlockHalf.UPPER, facing, hinge, open);
    }

    private static Direction openLeafSide(Direction facing, DoorHingeSide hinge) {
        if (facing == Direction.NORTH) {
            return hinge == DoorHingeSide.RIGHT ? Direction.EAST : Direction.WEST;
        }
        return hinge == DoorHingeSide.RIGHT ? Direction.WEST : Direction.EAST;
    }

    private static DoorHingeSide hingeForOpenLeafSide(Direction facing, Direction leafSide) {
        boolean rightHinge = facing == Direction.NORTH
                ? leafSide == Direction.EAST
                : leafSide == Direction.WEST;
        return rightHinge ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
    }

    private static DoorHingeSide doubleDoorHinge(Direction facing, int index, int count) {
        boolean leftLeaf = index < count / 2;
        // Match the authored double-door convention so the two leaves open toward opposite jambs.
        return leftLeaf == (facing == Direction.NORTH) ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
    }

    private static void normalizeDoorHalf(ServerLevel level, BlockPos pos, DoubleBlockHalf half,
                                          Direction facing, DoorHingeSide hinge, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock)) return;
        BlockState normalized = state.setValue(DoorBlock.HALF, half)
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HINGE, hinge)
                .setValue(DoorBlock.OPEN, open)
                .setValue(DoorBlock.POWERED, false);
        if (normalized != state) level.setBlock(pos, normalized, QUIET_BLOCK_UPDATE);
    }

    private void setOpen(ServerLevel level, PairState pair, boolean requested) {
        pair.pendingClose = !requested && (occupied(level, pair.connection.outside(), pair.connection.width())
                || occupied(level, pair.connection.inside(), pair.connection.width()));
        boolean next = requested || pair.pendingClose;
        boolean synchronizedBefore = allDoorHalvesMatch(level, pair.definition, next);
        boolean changed = next != pair.open || !synchronizedBefore;
        if (changed) pair.revision++;
        pair.open = next;
        setDoorRowOpen(level, pair.definition.outsideDoors(), next, changed);
        setDoorRowOpen(level, pair.definition.insideDoors(), next, changed);
        if (pair.prepared) pair.connection = connection(level, pair.definition);
    }

    private static void setDoorRowOpen(ServerLevel level, List<BlockPos> doors, boolean open, boolean forceResend) {
        BlockPos soundPos = doors.stream()
                .filter(pos -> level.getBlockState(pos).getBlock() instanceof DoorBlock)
                .filter(pos -> level.getBlockState(pos).getValue(DoorBlock.OPEN) != open)
                .findFirst().orElse(null);
        if (soundPos != null) {
            BlockState state = level.getBlockState(soundPos);
            // The client-side interaction is cancelled, so the opener did not already hear a
            // local vanilla sound. A null source makes the server send this transition to everyone.
            ((DoorBlock) state.getBlock()).setOpen(null, level, state, soundPos, open);
        }
        for (BlockPos lowerPos : doors) {
            setDoorHalfOpen(level, lowerPos, open, forceResend);
            setDoorHalfOpen(level, lowerPos.above(), open, forceResend);
        }
    }

    private static void setDoorHalfOpen(ServerLevel level, BlockPos pos, boolean open, boolean forceResend) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock)) return;
        BlockState updated = state.setValue(DoorBlock.OPEN, open).setValue(DoorBlock.POWERED, false);
        if (updated != state) {
            level.setBlock(pos, updated, DOOR_BLOCK_UPDATE);
        } else if (forceResend) {
            // A missed ClientboundBlockUpdatePacket is otherwise never repaired: setBlock would
            // see no server-side change and send nothing. Re-broadcast the authoritative state
            // when a transition/repair touches this half.
            level.sendBlockUpdated(pos, state, state, DOOR_BLOCK_UPDATE);
        }
    }

    static boolean occupied(ServerLevel level, Vec3 plane, double width) {
        return !level.getEntitiesOfClass(LivingEntity.class,
                new AABB(plane.x - width * .5, plane.y - 1, plane.z - .65,
                        plane.x + width * .5, plane.y + 1, plane.z + .65)).isEmpty();
    }

    static DoorConnection connection(Level level, Definition definition) {
        Opening outside = opening(level, definition.outsideDoors());
        Opening inside = opening(level, definition.insideDoors());
        double width = Math.min(outside.width(), inside.width());
        return new DoorConnection(outside.center(), inside.center(), width, -1, 1);
    }

    private static Opening opening(Level level, List<BlockPos> doors) {
        BlockPos first = com.stardew.craft.port.PortJava.getFirst(doors);
        BlockPos last = com.stardew.craft.port.PortJava.getLast(doors);
        if (doors.size() > 1) {
            double centerX = (first.getX() + last.getX() + 1) * .5;
            return new Opening(new Vec3(centerX, first.getY() + 1, first.getZ() + .5), doors.size() - 6.0 / 16.0);
        }
        BlockState state = level.getBlockState(first);
        var leaf = state.setValue(DoorBlock.OPEN, true).getCollisionShape(level, first).bounds();
        double left = leaf.maxX <= .5 ? leaf.maxX : 0;
        double right = leaf.minX >= .5 ? leaf.minX : 1;
        return new Opening(new Vec3(first.getX() + (left + right) * .5,
                first.getY() + 1, first.getZ() + .5), right - left);
    }

    private static Definition findDoor(BlockPos pos) {
        return TownDoorDefinitions.ALL.stream().filter(pair -> pair.containsDoor(pos)).findFirst().orElse(null);
    }

    private static Definition findNearbyDoor(BlockPos pos) {
        Definition nearest = null;
        int nearestDistance = 5;
        for (Definition pair : TownDoorDefinitions.ALL) {
            for (BlockPos door : pair.outsideDoors()) {
                int distance = nearbyDoorDistanceSqr(door, pos);
                if (distance < nearestDistance) {
                    nearest = pair;
                    nearestDistance = distance;
                }
            }
            for (BlockPos door : pair.insideDoors()) {
                int distance = nearbyDoorDistanceSqr(door, pos);
                if (distance < nearestDistance) {
                    nearest = pair;
                    nearestDistance = distance;
                }
            }
        }
        return nearest;
    }

    private static int nearbyDoorDistanceSqr(BlockPos door, BlockPos pos) {
        return Math.abs(door.getY() - pos.getY()) <= 1
                ? horizontalDistanceSqr(door, pos) : Integer.MAX_VALUE;
    }

    private static int horizontalDistanceSqr(BlockPos first, BlockPos second) {
        int dx = first.getX() - second.getX();
        int dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static List<BlockPos> concat(List<BlockPos> first, List<BlockPos> second) {
        List<BlockPos> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }

    private record Opening(Vec3 center, double width) {}

    private static final class PairState {
        private final Definition definition;
        private DoorConnection connection;
        private boolean prepared;
        private boolean open;
        private boolean pendingClose;
        private long npcHoldOpenUntil = Long.MIN_VALUE;
        private int revision = 1;

        private PairState(Definition definition) {
            this.definition = definition;
            this.connection = definition.authoredConnection();
        }
    }
}
