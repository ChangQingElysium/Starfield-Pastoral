package com.stardew.craft.platform.fabric.item.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Pins the native bytecode contract; actual Mixin application still needs the server load gate. */
class TransferTransactionHookContractTest {
    @Test
    void fapiCommitDelegatesDirectlyToCloseAndAbortUsesTheSameSnapshotLifecycle() throws IOException {
        var type = read("net/fabricmc/fabric/impl/transfer/transaction/TransactionManagerImpl$TransactionImpl");
        for (String operation : new String[] {"commit", "abort"}) {
            var method = type.methods.stream().filter(m -> m.name.equals(operation) && m.desc.equals("()V")).findFirst().orElseThrow();
            var calls = java.util.Arrays.stream(method.instructions.toArray())
                    .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
            assertEquals(1, calls.size());
            assertEquals("close", calls.getFirst().name);
            assertEquals("(Lnet/fabricmc/fabric/api/transfer/v1/transaction/TransactionContext$Result;)V", calls.getFirst().desc);
        }
        var close = type.methods.stream().filter(m -> m.name.equals("close")).findFirst().orElseThrow();
        int lifecycleWrite = -1, callback = -1, index = 0;
        for (var instruction : close.instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("lifecycle") && lifecycleWrite < 0) lifecycleWrite = index;
            if (instruction instanceof MethodInsnNode call && call.name.equals("onClose") && callback < 0) callback = index;
            index++;
        }
        assertTrue(lifecycleWrite >= 0 && callback > lifecycleWrite);
    }

    @Test
    void levelRandomIsInitializedByTheNativeLegacyRandomFactory() throws IOException {
        var level = read("net/minecraft/world/level/Level");
        boolean assignedByFactory = false;
        for (var constructor : level.methods) {
            if (!constructor.name.equals("<init>")) continue;
            MethodInsnNode previousCall = null;
            for (var instruction : constructor.instructions) {
                if (instruction instanceof MethodInsnNode call) previousCall = call;
                if (instruction instanceof FieldInsnNode field && field.name.equals("random")) {
                    assignedByFactory |= previousCall != null && previousCall.owner.equals("net/minecraft/util/RandomSource")
                            && previousCall.name.equals("create") && previousCall.desc.equals("()Lnet/minecraft/util/RandomSource;");
                }
            }
        }
        assertTrue(assignedByFactory);
        var random = read("net/minecraft/util/RandomSource");
        var factory = random.methods.stream().filter(m -> m.name.equals("create") && m.desc.equals("(J)Lnet/minecraft/util/RandomSource;")).findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(factory.instructions.toArray()).anyMatch(instruction -> instruction instanceof TypeInsnNode allocation
                && allocation.desc.equals("net/minecraft/world/level/levelgen/LegacyRandomSource")));
    }

    @Test
    void legacySourceExposesTheExpectedAtomicSeedWithoutChangingItsGaussianCache() throws IOException {
        var source = read("net/minecraft/world/level/levelgen/LegacyRandomSource");
        assertTrue(source.fields.stream().anyMatch(f -> f.name.equals("seed") && f.desc.equals("Ljava/util/concurrent/atomic/AtomicLong;")));
        assertTrue(source.fields.stream().anyMatch(f -> f.name.equals("gaussianSource")));
        // Recipe nextInt never consumes nextGaussian, so its existing native cache
        // must be preserved when only the primitive seed stream is committed.
    }

    private static ClassNode read(String name) throws IOException {
        try (var stream = TransferTransactionHookContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
