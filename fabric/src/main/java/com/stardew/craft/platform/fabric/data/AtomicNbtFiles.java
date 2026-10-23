package com.stardew.craft.platform.fabric.data;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Checked, flushed checkpoint writes used before retiring livestock projections. */
public final class AtomicNbtFiles {
    private AtomicNbtFiles() {}
    public static void writeNbtCompressed(CompoundTag root, Path target) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".stardewcraft-tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                // NbtIo closes its gzip stream. Keep the channel open until force(true).
                var stream = new FilterOutputStream(Channels.newOutputStream(channel)) {
                    @Override public void close() throws IOException { flush(); }
                };
                NbtIo.writeCompressed(root, stream);
                channel.force(true);
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException | RuntimeException | Error failure) {
            try { Files.deleteIfExists(temporary); }
            catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }
}
