package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Contract checks against the actual mapped 1.21.1 classes, without starting a server. */
class NativeSyncHooksTest {
    @Test void loginAndReloadHaveUniqueDataPacketBoundariesInTheRequiredOrder() throws Exception {
        var login = method("net.minecraft.server.players.PlayerList", "placeNewPlayer");
        int recipes = uniqueNew(login, "ClientboundUpdateRecipesPacket");
        assertTrue(uniqueNew(login, "ClientboundSetCarriedItemPacket") < recipes);
        assertTrue(calls(login, "initInventoryMenu").getFirst() > recipes);
        var reload = method("net.minecraft.server.players.PlayerList", "reloadResources");
        int tags = uniqueNew(reload, "ClientboundUpdateTagsPacket");
        assertTrue(tags < calls(reload, "serializeTagsToNetwork").getFirst());
        assertTrue(tags < uniqueNew(reload, "ClientboundUpdateRecipesPacket"));
    }

    @Test void trackingAndChunkHooksUseCompletedSendAndThePreDropBoundary() throws Exception {
        var pairing = method("net.minecraft.server.level.ServerEntity", "addPairing");
        assertEquals(1, returns(pairing));
        assertEquals("startSeenByPlayer", lastCall(pairing));
        var unpairing = method("net.minecraft.server.level.ServerEntity", "removePairing");
        assertEquals(1, returns(unpairing));
        assertEquals("send", lastCall(unpairing));
        var send = method("net.minecraft.server.network.PlayerChunkSender", "sendChunk");
        assertTrue((send.access & Opcodes.ACC_STATIC) != 0);
        assertEquals(1, returns(send));
        assertTrue(uniqueNew(send, "ClientboundLevelChunkWithLightPacket") < calls(send, "sendPoiPacketsForChunk").getFirst());
        var drop = method("net.minecraft.server.level.ChunkMap", "dropChunk");
        assertTrue((drop.access & Opcodes.ACC_STATIC) != 0);
        assertEquals(1, calls(drop, "dropChunk").size());
        var queued = method("net.minecraft.server.level.ChunkMap", "markChunkPendingToSend",
                "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/chunk/LevelChunk;)V");
        assertTrue((queued.access & Opcodes.ACC_STATIC) != 0);
        assertEquals(1, returns(queued));
        assertEquals("markChunkPendingToSend", lastCall(queued));
    }

    @Test void blockEntityConsumersHaveUniqueLoadPointsAndRetainTheNativeTypeGuards() throws Exception {
        var chunk = method("net.minecraft.world.level.chunk.LevelChunk", "method_31716",
                "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/nbt/CompoundTag;)V");
        assertEquals(1, calls(chunk, "loadWithComponents").size());
        assertTrue(calls(chunk, "getType").getFirst() < calls(chunk, "loadWithComponents").getFirst());
        var packet = method("net.minecraft.client.multiplayer.ClientPacketListener", "method_38542",
                "(Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;Lnet/minecraft/world/level/block/entity/BlockEntity;)V");
        assertEquals(1, calls(packet, "loadWithComponents").size());
        assertTrue(calls(packet, "isEmpty").getFirst() < calls(packet, "loadWithComponents").getFirst());
        assertTrue(calls(packet, "updateGui").getFirst() > calls(packet, "loadWithComponents").getFirst());
    }

    private static MethodNode method(String owner, String name) throws Exception { return method(owner, name, null); }
    private static MethodNode method(String owner, String name, String descriptor) throws Exception {
        var type = new ClassNode();
        try (var bytes = NativeSyncHooksTest.class.getClassLoader().getResourceAsStream(owner.replace('.', '/') + ".class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(type, 0);
        }
        var methods = type.methods.stream().filter(m -> m.name.equals(name) && (descriptor == null || m.desc.equals(descriptor))).toList();
        assertEquals(1, methods.size(), owner + "." + name);
        return methods.getFirst();
    }
    private static List<Integer> calls(MethodNode method, String name) {
        return java.util.stream.IntStream.range(0, method.instructions.size())
                .filter(i -> method.instructions.get(i) instanceof MethodInsnNode call && call.name.equals(name)).boxed().toList();
    }
    private static int uniqueNew(MethodNode method, String simpleName) {
        var indices = java.util.stream.IntStream.range(0, method.instructions.size())
                .filter(i -> method.instructions.get(i) instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                        && type.desc.endsWith("/" + simpleName)).boxed().toList();
        assertEquals(1, indices.size(), simpleName);
        return indices.getFirst();
    }
    private static long returns(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(i -> i.getOpcode() == Opcodes.RETURN).count();
    }
    private static String lastCall(MethodNode method) {
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int i = instructions.length - 1; i >= 0; i--) {
            if (instructions[i] instanceof MethodInsnNode call) return call.name;
        }
        throw new AssertionError("No native method call");
    }
}
