package com.stardew.craft.port.net.neoforged.neoforge.common;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.apache.commons.io.output.CloseShieldOutputStream;

/**
 * PORT(1.20.1): NeoForge's crash-safe file writes. 1.20.1 saves {@code SavedData} synchronously, so the I/O worker
 * queue only contains tasks submitted through {@link #withIOWorker(Runnable)}.
 */
public final class IOUtilities {
    private static final String TEMP_FILE_SUFFIX = ".neoforge-tmp";
    private static final OpenOption[] OPEN_OPTIONS = {StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};
    private static CompletableFuture<Void> saveDataTasks = CompletableFuture.completedFuture(null);

    private IOUtilities() {}

    public static void writeNbtCompressed(CompoundTag tag, Path path) throws IOException {
        atomicWrite(path, stream -> {
            try (var bufferedStream = new BufferedOutputStream(stream)) {
                NbtIo.writeCompressed(tag, bufferedStream);
            }
        });
    }

    public static void writeNbt(CompoundTag tag, Path path) throws IOException {
        atomicWrite(path, stream -> {
            try (var bufferedStream = new BufferedOutputStream(stream); var dataStream = new DataOutputStream(bufferedStream)) {
                NbtIo.write(tag, dataStream);
            }
        });
    }

    public static void atomicWrite(Path targetPath, WriteCallback writeCallback) throws IOException {
        final var tempPath = Files.createTempFile(targetPath.getParent(), targetPath.getFileName().toString(), TEMP_FILE_SUFFIX);
        try {
            try (var channel = FileChannel.open(tempPath, OPEN_OPTIONS)) {
                var stream = CloseShieldOutputStream.wrap(Channels.newOutputStream(channel));
                writeCallback.write(stream);
                channel.force(true);
            }
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception first) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (Exception second) {
                first.addSuppressed(second);
            }
            throw first;
        }
    }

    public static synchronized void withIOWorker(Runnable task) {
        saveDataTasks = saveDataTasks.thenRunAsync(task, Util.ioPool());
    }

    public static void waitUntilIOWorkerComplete() {
        CompletableFuture<Void> tasks;
        synchronized (IOUtilities.class) {
            tasks = saveDataTasks;
            saveDataTasks = CompletableFuture.completedFuture(null);
        }
        tasks.join();
    }

    @FunctionalInterface
    public interface WriteCallback {
        void write(OutputStream stream) throws IOException;
    }
}
