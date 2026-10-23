package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.platform.fabric.entity.EntityPresence;
import com.stardew.craft.platform.fabric.event.entity.EntityDispatch;
import com.stardew.craft.platform.fabric.event.entity.EntityJoinLevelEvent;
import com.stardew.craft.platform.fabric.event.entity.EntityLeaveLevelEvent;
import com.stardew.craft.platform.fabric.event.entity.EntityTravelToDimensionEvent;
import com.stardew.craft.platform.fabric.event.entity.living.LivingChangeTargetEvent;
import com.stardew.craft.platform.fabric.event.level.ChunkDataEvent;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkType;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class TargetPresenceAndChunkDataTest {
    @Test void entityPresenceOnlyChangesAfterInsertionAndBeforeTheLeaveEvent() throws Exception {
        var entity = EventTestObjects.allocate(PresencePlayer.class);
        var level = EventTestObjects.allocate(ServerLevel.class);
        assertFalse(EntityPresence.isAdded(entity));
        Consumer<EntityJoinLevelEvent> cancel = event -> { assertFalse(EntityPresence.isAdded(entity)); event.setCanceled(true); };
        FabricEvents.GAME.addListener(EntityJoinLevelEvent.class, cancel);
        try { assertFalse(EntityDispatch.join(entity, level, false, () -> fail("canceled insertion"))); }
        finally { FabricEvents.GAME.unregister(cancel); }
        assertFalse(EntityDispatch.join(entity, level, false, () -> false));
        assertFalse(EntityPresence.isAdded(entity));
        assertTrue(EntityDispatch.join(entity, level, true, () -> { assertFalse(EntityPresence.isAdded(entity)); return true; }));
        assertTrue(EntityPresence.isAdded(entity));
        Consumer<EntityLeaveLevelEvent> leave = event -> assertFalse(EntityPresence.isAdded(entity));
        FabricEvents.GAME.addListener(EntityLeaveLevelEvent.class, leave);
        try { EntityDispatch.leave(entity, level); }
        finally { FabricEvents.GAME.unregister(leave); }
        assertFalse(EntityPresence.isAdded(entity));
    }

    @Test void targetEventPreservesOriginalCandidateSupportsNullReplacementAndIndependentType() throws Exception {
        var owner = EventTestObjects.allocate(Pig.class);
        var target = EventTestObjects.allocate(ServerPlayer.class);
        var event = new LivingChangeTargetEvent(owner, target, LivingChangeTargetEvent.LivingTargetType.BEHAVIOR_TARGET);
        assertSame(target, event.getOriginalAboutToBeSetTarget());
        event.setNewAboutToBeSetTarget(null); event.setCanceled(true);
        assertSame(target, event.getOriginalAboutToBeSetTarget()); assertNull(event.getNewAboutToBeSetTarget());
        assertTrue(event.isCanceled()); assertSame(LivingChangeTargetEvent.LivingTargetType.BEHAVIOR_TARGET, event.getTargetType());
    }

    @Test void chunkLoadRetainsOriginalMutableNbtAndProtoTypeAndDimensionPermissionCanVeto() throws Exception {
        var owner = EventTestObjects.allocate(ServerPlayer.class);
        var event = new EntityTravelToDimensionEvent(owner, Level.NETHER);
        assertSame(Level.NETHER, event.getDimension()); event.setCanceled(true); assertTrue(event.isCanceled());
        var chunk = EventTestObjects.allocate(ProtoChunk.class);
        var level = EventTestObjects.allocate(ServerLevel.class);
        var nbt = new CompoundTag(); nbt.putString("source", "legacy weed palette");
        var load = new ChunkDataEvent.Load(chunk, level, nbt, ChunkType.PROTOCHUNK);
        assertSame(nbt, load.getData()); assertSame(chunk, load.getChunk()); assertSame(level, load.getLevel());
        assertEquals(ChunkType.PROTOCHUNK, load.getType());
        assertThrows(IllegalStateException.class, () -> load.setCanceled(true));
    }

    @Test void nativeTargetAndChunkAndDimensionEntryPointsMatchTheirActualTypes() throws Exception {
        var brain = nativeType("net/minecraft/world/entity/ai/behavior/StartAttacking");
        var setters = brain.methods.stream().filter(m -> java.util.Arrays.stream(m.instructions.toArray()).anyMatch(i ->
                i instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/entity/ai/behavior/declarative/MemoryAccessor") && call.name.equals("set"))).toList();
        assertEquals(1, setters.size());
        var method = setters.getFirst(); assertTrue((method.access & Opcodes.ACC_STATIC) != 0);
        assertTrue(method.desc.contains("Lnet/minecraft/world/entity/Mob;")); assertTrue(method.desc.endsWith(")Z"));
        assertEquals(1, method.localVariables.stream().filter(l -> l.desc.equals("Lnet/minecraft/world/entity/LivingEntity;")).count());
        var calls = java.util.Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode).map(i -> ((MethodInsnNode) i).name).toList();
        assertTrue(calls.indexOf("canAttack") < calls.indexOf("set")); assertTrue(calls.indexOf("set") < calls.indexOf("erase"));
        var reader = nativeType("net/minecraft/world/level/chunk/storage/ChunkSerializer").methods.stream().filter(m -> m.name.equals("read")).findFirst().orElseThrow();
        assertEquals(2, java.util.Arrays.stream(reader.instructions.toArray()).filter(i -> i.getOpcode() == Opcodes.ARETURN).count());
        var readHook = nativeType("com/stardew/craft/platform/mixin/event/level/mixin/ChunkDataReadMixin")
                .methods.stream().filter(m -> m.name.equals("stardewcraft$loadedData")).findFirst().orElseThrow();
        assertEquals("(" + org.objectweb.asm.Type.getReturnType(reader.desc).getDescriptor() + reader.desc.substring(1),
                readHook.desc, "The post-read hook must retain every native argument, including RegionStorageInfo");
        for (String type : java.util.List.of("net/minecraft/world/entity/Entity", "net/minecraft/server/level/ServerPlayer")) {
            var travel = nativeType(type).methods.stream().filter(m -> m.name.equals("changeDimension")).findFirst().orElseThrow();
            assertEquals("(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;", travel.desc);
        }
    }

    private static final class PresencePlayer extends ServerPlayer implements EntityPresence.State {
        boolean added;
        PresencePlayer() { super(null, null, new GameProfile(new UUID(0, 1), "Fixture"), ClientInformation.createDefault()); }
        @Override public boolean stardewcraft$isAddedToLevel() { return added; }
        @Override public void stardewcraft$setAddedToLevel(boolean added) { this.added = added; }
    }
    private static ClassNode nativeType(String name) throws Exception {
        var result = new ClassNode();
        try (var input = TargetPresenceAndChunkDataTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input); new ClassReader(input).accept(result, 0);
        }
        return result;
    }
}
