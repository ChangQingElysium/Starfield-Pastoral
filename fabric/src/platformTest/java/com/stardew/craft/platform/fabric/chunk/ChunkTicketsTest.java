package com.stardew.craft.platform.fabric.chunk;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class ChunkTicketsTest {
    private static final ResourceLocation NPC = ResourceLocation.parse("stardewcraft:npc_navigation"), PORTAL = ResourceLocation.parse("stardewcraft:portal_placement"), FOREIGN = ResourceLocation.parse("othermod:leases");
    private static final UUID OWNER = UUID.fromString("7f5b27e6-a32e-41f0-aec5-766e84d4c531");
    private static final BlockPos BLOCK = new BlockPos(-4, 61, 123);
    private static final ChunkPos CHUNK = new ChunkPos(-3, 7);

    @Test void registeredControllersAreDeliveredThroughTheRealModBusAndLateRegistrationIsRejected() {
        var bus = new EventBus(); var retained = new AtomicReference<RegisterTicketControllersEvent>();
        var controller = new TicketController(NPC);
        bus.addListener(RegisterTicketControllersEvent.class, event -> { retained.set(event); event.register(controller); });
        assertEquals(Map.of(NPC, controller), TicketControllers.collectControllers(bus));
        assertThrows(IllegalStateException.class, () -> retained.get().register(new TicketController(PORTAL)));
    }

    @Test void duplicateControllerIdsFailRegistrationAndTheFailedEventAlsoCloses() {
        var bus = new EventBus(); var retained = new AtomicReference<RegisterTicketControllersEvent>();
        bus.addListener(RegisterTicketControllersEvent.class, event -> { retained.set(event); event.register(new TicketController(NPC)); event.register(new TicketController(NPC)); });
        assertThrows(IllegalArgumentException.class, () -> TicketControllers.collectControllers(bus));
        assertThrows(IllegalStateException.class, () -> retained.get().register(new TicketController(PORTAL)));
    }

    @Test void theOriginalTwoCallbacksDiscardTheirOwnersWithoutChangingForeignControllerNbtOrVanillaForced() {
        var foreign = foreignEntry();
        var input = saved(controllerEntry(NPC, "TickingEntities", NbtUtils.createUUID(OWNER)), controllerEntry(PORTAL, "Blocks", block(BLOCK)), foreign);
        input.putString("OtherRootMetadata", "preserve this too");
        var data = new ChunkTicketData(input); var dirty = new AtomicInteger(); var callbacks = new AtomicInteger();
        var registered = Map.of(NPC, discarding(NPC, callbacks), PORTAL, discarding(PORTAL, callbacks));
        TicketControllers.validate(data, registered, null, dirty::incrementAndGet);
        assertFalse(data.hasTickets()); assertFalse(data.shouldForceTicks(CHUNK.toLong())); assertEquals(2, callbacks.get()); assertEquals(2, dirty.get());
        var output = new CompoundTag(); output.putLongArray("Forced", new long[]{new ChunkPos(98, 99).toLong()}); data.saveInto(output);
        assertEquals(1, output.getList("ModForced", Tag.TAG_COMPOUND).size()); assertEquals(foreign, output.getList("ModForced", Tag.TAG_COMPOUND).getCompound(0));
        assertEquals("preserve this too", output.getString("OtherRootMetadata"));
        assertArrayEquals(new long[]{new ChunkPos(98, 99).toLong()}, output.getLongArray("Forced"));
        assertEquals(3, input.getList("ModForced", Tag.TAG_COMPOUND).size());
        TicketControllers.validate(data, registered, null, dirty::incrementAndGet); assertEquals(2, callbacks.get());
    }

    @Test void controllersWithoutTicketsDoNotReceiveAValidationCallback() {
        var count = new AtomicInteger(); var data = new ChunkTicketData(new CompoundTag());
        TicketControllers.validate(data, Map.of(NPC, discarding(NPC, count)), null, () -> fail("empty data became dirty"));
        assertEquals(0, count.get());
    }

    @Test void helpersExposeImmutableDetachedOwnerSnapshotsWhileRemovalsStayControllerScoped() {
        var data = prepared(); var npc = new TicketOwner(NPC, OWNER, true); var portal = new TicketOwner(PORTAL, OWNER, true);
        data.add(npc, CHUNK.toLong()); data.add(new TicketOwner(NPC, OWNER, false), CHUNK.toLong()); data.add(portal, CHUNK.toLong());
        var dirty = new AtomicInteger(); var helper = new TicketHelper(data, NPC, dirty::incrementAndGet);
        var snapshot = helper.getEntityTickets(); assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get(OWNER).ticking().clear());
        snapshot.keySet().forEach(helper::removeAllTickets);
        assertEquals(1, dirty.get()); assertFalse(data.contains(npc, CHUNK.toLong())); assertTrue(data.contains(portal, CHUNK.toLong()));
        assertTrue(snapshot.get(OWNER).ticking().contains(CHUNK.toLong())); assertTrue(data.shouldForceTicks(CHUNK.toLong()));
    }

    @Test void aSpecificRemovalPreservesOtherChunksAndTheSameOwnersOtherTickingMode() {
        var data = prepared(); var ticking = new TicketOwner(NPC, BLOCK, true); var regular = new TicketOwner(NPC, BLOCK, false);
        data.add(ticking, CHUNK.toLong()); data.add(ticking, CHUNK.toLong() + 1); data.add(regular, CHUNK.toLong());
        var helper = new TicketHelper(data, NPC, () -> {}); helper.removeTicket(BLOCK, CHUNK.toLong(), true);
        assertFalse(data.contains(ticking, CHUNK.toLong())); assertTrue(data.contains(ticking, CHUNK.toLong() + 1)); assertTrue(data.contains(regular, CHUNK.toLong()));
        assertFalse(data.shouldForceTicks(CHUNK.toLong())); assertTrue(data.hasTickets());
    }

    @Test void nativeTicketIdentityIncludesControllerOwnerKindValueAndTickingModeAndFreezesBlockPositions() {
        var mutable = BLOCK.mutable(); var blockOwner = new TicketOwner(NPC, mutable, true); mutable.move(5, 0, 0);
        assertEquals(BLOCK, blockOwner.owner());
        var owners = List.of(blockOwner, new TicketOwner(NPC, OWNER, true), new TicketOwner(PORTAL, BLOCK, true), new TicketOwner(NPC, BLOCK, false), new TicketOwner(NPC, BLOCK.above(), true));
        for (int a = 0; a < owners.size(); a++) for (int b = 0; b < owners.size(); b++) assertEquals(a == b, owners.get(a).compareTo(owners.get(b)) == 0);
        assertThrows(IllegalArgumentException.class, () -> new TicketOwner(NPC, "not an owner", true));
    }

    @Test void tickingCountsRemainActiveUntilEveryIndependentTickingLeaseHasBeenReleased() {
        var data = prepared(); var first = new TicketOwner(NPC, OWNER, true); var second = new TicketOwner(NPC, UUID.randomUUID(), true); var other = new TicketOwner(PORTAL, OWNER, true);
        data.add(first, CHUNK.toLong()); data.add(second, CHUNK.toLong()); data.add(other, CHUNK.toLong());
        data.remove(first, CHUNK.toLong()); data.remove(second, CHUNK.toLong()); assertTrue(data.shouldForceTicks(CHUNK.toLong()));
        data.remove(other, CHUNK.toLong()); assertFalse(data.shouldForceTicks(CHUNK.toLong())); assertFalse(data.hasTickets());
    }

    @Test void forceAddsSynchronouslyLoadBeforeTheNativeTicketAndDuplicateLeasesHaveNoSideEffects() {
        var data = prepared(); var backend = new RecordingBackend(); var owner = new TicketOwner(NPC, OWNER, true);
        assertTrue(TicketControllers.apply(data, owner, CHUNK, true, backend)); assertEquals(List.of("load", "add", "dirty"), backend.actions);
        assertTrue(data.contains(owner, CHUNK.toLong())); assertTrue(data.shouldForceTicks(CHUNK.toLong()));
        backend.actions.clear(); assertFalse(TicketControllers.apply(data, owner, CHUNK, true, backend)); assertTrue(backend.actions.isEmpty());
        assertTrue(TicketControllers.apply(data, owner, CHUNK, false, backend)); assertEquals(List.of("remove", "dirty"), backend.actions);
        backend.actions.clear(); assertFalse(TicketControllers.apply(data, owner, CHUNK, false, backend)); assertTrue(backend.actions.isEmpty());
    }

    @Test void aFailedSynchronousLoadDoesNotLeaveAnActiveOrPersistentLease() {
        var data = prepared(); var owner = new TicketOwner(NPC, OWNER, true);
        var backend = new RecordingBackend() { @Override public void load(ChunkPos pos) { super.load(pos); throw new IllegalStateException("chunk load failed"); } };
        assertThrows(IllegalStateException.class, () -> TicketControllers.apply(data, owner, CHUNK, true, backend));
        assertEquals(List.of("load", "dirty"), backend.actions); assertFalse(data.hasTickets()); assertFalse(data.shouldForceTicks(CHUNK.toLong()));
    }

    @Test void aFailedNativeRemovalRestoresTheLeaseAndTickingCount() {
        var data = prepared(); var owner = new TicketOwner(NPC, OWNER, true); data.add(owner, CHUNK.toLong());
        var backend = new RecordingBackend() { @Override public void remove(ChunkPos pos, TicketOwner owner) { throw new IllegalStateException("removal failed"); } };
        assertThrows(IllegalStateException.class, () -> TicketControllers.apply(data, owner, CHUNK, false, backend));
        assertTrue(data.contains(owner, CHUNK.toLong())); assertTrue(data.shouldForceTicks(CHUNK.toLong()));
    }

    @Test void blockAndEntityTicketsInBothModesRoundTripWithoutChangingOpaqueForeignRecords() {
        var foreign = foreignEntry(); var data = new ChunkTicketData(saved(foreign)); data.prepare(Set.of(NPC, PORTAL));
        for (Object owner : List.of(BLOCK, OWNER)) for (boolean ticking : new boolean[]{true, false}) data.add(new TicketOwner(NPC, owner, ticking), CHUNK.toLong());
        var output = new CompoundTag(); output.putLongArray("Forced", new long[]{1, 2}); data.saveInto(output);
        var reload = new ChunkTicketData(output); reload.prepare(Set.of(NPC, PORTAL)); assertEquals(data.snapshot(), reload.snapshot());
        assertEquals(foreign, output.getList("ModForced", Tag.TAG_COMPOUND).getCompound(0)); assertArrayEquals(new long[]{1, 2}, output.getLongArray("Forced"));
    }

    @Test void unknownAndMalformedForeignControllerIdsArePreservedAndNeverBecomeActiveTickets() {
        var foreign = foreignEntry(); var malformed = foreign.copy(); malformed.putString("Controller", "bad::id");
        var legacy = foreign.copy(); legacy.remove("Controller"); legacy.putString("Mod", "foreignlegacy");
        var input = saved(foreign, malformed, legacy); var data = new ChunkTicketData(input); data.prepare(Set.of(NPC, PORTAL));
        assertFalse(data.hasTickets()); var output = new CompoundTag(); data.saveInto(output); assertEquals(input.get("ModForced"), output.get("ModForced"));
    }

    @Test void malformedOwnedDataFailsClosedAndRetainsTheCompleteOriginalNbt() {
        var malformed = controllerEntry(NPC, "TickingEntities", NbtUtils.createUUID(OWNER));
        malformed.getList("ModForced", Tag.TAG_COMPOUND).getCompound(0).putString("TickingEntities", "invalid owner list");
        var input = saved(malformed, foreignEntry()); var data = new ChunkTicketData(input);
        assertThrows(IllegalArgumentException.class, () -> TicketControllers.validate(data, Map.of(NPC, new TicketController(NPC)), null, () -> {}));
        assertThrows(IllegalStateException.class, data::requireHealthy);
        var output = new CompoundTag(); data.saveInto(output); assertEquals(input.get("ModForced"), output.get("ModForced"));
    }

    @Test void aThrowingValidationCallbackDoesNotSaveItsPartiallyRemovedLegacyState() {
        var input = saved(controllerEntry(NPC, "TickingEntities", NbtUtils.createUUID(OWNER))); var data = new ChunkTicketData(input);
        var controller = new TicketController(NPC, (level, helper) -> { helper.getEntityTickets().keySet().forEach(helper::removeAllTickets); throw new IllegalStateException("invalid callback"); });
        assertThrows(IllegalStateException.class, () -> TicketControllers.validate(data, Map.of(NPC, controller), null, () -> {}));
        var output = new CompoundTag(); data.saveInto(output); assertEquals(input.get("ModForced"), output.get("ModForced"));
    }

    @Test void failedNativeAddsAreRemovedAndRollbackFailuresFailClosed() {
        var data = prepared(); var owner = new TicketOwner(NPC, OWNER, true);
        var backend = new RecordingBackend() {
            @Override public void add(ChunkPos pos, TicketOwner owner) { super.add(pos, owner); throw new IllegalStateException("native add failed"); }
        };
        assertThrows(IllegalStateException.class, () -> TicketControllers.apply(data, owner, CHUNK, true, backend));
        assertEquals(List.of("load", "add", "remove", "dirty"), backend.actions); assertFalse(data.hasTickets());
        var badRollback = new RecordingBackend() {
            @Override public void add(ChunkPos pos, TicketOwner owner) { throw new IllegalStateException("native add failed"); }
            @Override public void remove(ChunkPos pos, TicketOwner owner) { throw new IllegalStateException("native rollback failed"); }
        };
        var failure = assertThrows(IllegalStateException.class, () -> TicketControllers.apply(data, owner, CHUNK, true, badRollback));
        assertEquals(1, failure.getSuppressed().length); assertThrows(IllegalStateException.class, data::requireHealthy);
    }

    @Test void encodedNbtRoundTripPreservesForeignTagTypesAndUnpreparedDataNeverDropsOpaqueValues() throws Exception {
        var input = saved(foreignEntry()); var data = new ChunkTicketData(input);
        var output = new CompoundTag(); output.putLongArray("Forced", input.getLongArray("Forced")); data.saveInto(output);
        var bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.write(output, new java.io.DataOutputStream(bytes));
        var decoded = net.minecraft.nbt.NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(input, decoded);
    }

    private static ChunkTicketData prepared() { var data = new ChunkTicketData(new CompoundTag()); data.prepare(Set.of(NPC, PORTAL)); return data; }
    private static TicketController discarding(ResourceLocation id, AtomicInteger count) { return new TicketController(id, (level, helper) -> { count.incrementAndGet(); helper.getEntityTickets().keySet().forEach(helper::removeAllTickets); helper.getBlockTickets().keySet().forEach(helper::removeAllTickets); }); }
    private static CompoundTag saved(CompoundTag... controllers) { var tag = new CompoundTag(); tag.putLongArray("Forced", new long[]{CHUNK.toLong()}); var list = new ListTag(); for (var entry : controllers) list.add(entry); tag.put("ModForced", list); return tag; }
    private static CompoundTag controllerEntry(ResourceLocation id, String kind, Tag owner) { var controller = new CompoundTag(); controller.putString("Controller", id.toString()); var chunks = new ListTag(); var chunk = new CompoundTag(); chunk.putLong("Chunk", CHUNK.toLong()); var owners = new ListTag(); owners.add(owner); chunk.put(kind, owners); chunks.add(chunk); controller.put("ModForced", chunks); return controller; }
    private static CompoundTag block(BlockPos pos) { var tag = new CompoundTag(); tag.putInt("X", pos.getX()); tag.putInt("Y", pos.getY()); tag.putInt("Z", pos.getZ()); return tag; }
    private static CompoundTag foreignEntry() { var entry = controllerEntry(FOREIGN, "TickingEntities", NbtUtils.createUUID(OWNER)); entry.putByteArray("OtherModOpaquePayload", new byte[]{1, -5, 20}); var unknown = new ListTag(); unknown.add(StringTag.valueOf("preserve nested types")); entry.put("ForeignUnknownList", unknown); return entry; }
    private static class RecordingBackend implements TicketControllers.Backend {
        final List<String> actions = new ArrayList<>();
        @Override public void load(ChunkPos pos) { assertEquals(CHUNK, pos); actions.add("load"); }
        @Override public void add(ChunkPos pos, TicketOwner owner) { assertEquals(CHUNK, pos); actions.add("add"); }
        @Override public void remove(ChunkPos pos, TicketOwner owner) { assertEquals(CHUNK, pos); actions.add("remove"); }
        @Override public void dirty() { actions.add("dirty"); }
    }
}
