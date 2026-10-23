package com.stardew.craft.platform.fabric.data;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class AtomicNbtFilesTest {
    @TempDir Path directory;

    @Test void checkedCheckpointCanReplaceAndReloadCompleteNbtWithoutLeavingTemporaryFiles() throws Exception {
        var target = directory.resolve("livestock.dat");
        for (int generation : new int[] {1, 2}) {
            var root = new CompoundTag(); var data = new CompoundTag();
            data.putInt("generation", generation); data.putString("receipt", "animal-owned-data");
            root.put("data", data); root.putInt("DataVersion", 3955);
            AtomicNbtFiles.writeNbtCompressed(root, target);
            assertEquals(root, NbtIo.readCompressed(target, NbtAccounter.unlimitedHeap()));
        }
        try (var files = Files.list(directory)) { assertEquals(java.util.List.of(target), files.toList()); }
    }

    @Test void failedRenameKeepsOriginalTargetAndRemovesItsTemporaryOutput() throws Exception {
        var target = directory.resolve("occupied.dat"); Files.createDirectory(target);
        Files.writeString(target.resolve("original"), "keep");
        assertThrows(java.io.IOException.class, () -> AtomicNbtFiles.writeNbtCompressed(new CompoundTag(), target));
        assertEquals("keep", Files.readString(target.resolve("original")));
        try (var files = Files.list(directory)) { assertEquals(java.util.List.of(target), files.toList()); }
    }

    @Test void nativeSavedDataWriteIsSynchronousAndDoesNotUseAnAsyncIoQueue() throws Exception {
        var type = new ClassNode();
        try (var bytes = getClass().getClassLoader().getResourceAsStream("net/minecraft/world/level/saveddata/SavedData.class")) {
            assertNotNull(bytes); new ClassReader(bytes).accept(type, 0);
        }
        var method = type.methods.stream().filter(m -> m.name.equals("save") && m.desc.startsWith("(Ljava/io/File;")).findFirst().orElseThrow();
        var calls = java.util.Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode)
                .map(i -> (MethodInsnNode) i).toList();
        assertEquals(1, calls.stream().filter(c -> c.owner.equals("net/minecraft/nbt/NbtIo") && c.name.equals("writeCompressed")).count());
        assertTrue(calls.stream().noneMatch(c -> c.owner.equals("java/util/concurrent/CompletableFuture") || c.name.equals("ioPool")));
    }
}
