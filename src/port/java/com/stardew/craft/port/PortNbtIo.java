package com.stardew.craft.port;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * 1.21 {@code NbtIo} overloads missing in 1.20.1 ({@code Path} variants, {@code readCompressed} with an
 * {@link NbtAccounter}). Same stream stacking, quota accounting and SYNC/DSYNC write options as 1.21.
 */
public final class PortNbtIo {
    private static final OpenOption[] SYNC_OUTPUT_OPTIONS = new OpenOption[]{
            StandardOpenOption.SYNC, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE};

    private PortNbtIo() {
    }

    public static CompoundTag readCompressed(InputStream zippedStream, NbtAccounter accounter) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(zippedStream)))) {
            return NbtIo.read(input, accounter);
        }
    }

    public static CompoundTag readCompressed(Path path, NbtAccounter accounter) throws IOException {
        try (InputStream raw = Files.newInputStream(path); InputStream buffered = new BufferedInputStream(raw)) {
            return readCompressed(buffered, accounter);
        }
    }

    public static void writeCompressed(CompoundTag tag, Path path) throws IOException {
        try (OutputStream raw = Files.newOutputStream(path, SYNC_OUTPUT_OPTIONS);
             OutputStream buffered = new BufferedOutputStream(raw)) {
            NbtIo.writeCompressed(tag, buffered);
        }
    }

    public static void write(CompoundTag tag, Path path) throws IOException {
        try (OutputStream raw = Files.newOutputStream(path, SYNC_OUTPUT_OPTIONS);
             OutputStream buffered = new BufferedOutputStream(raw);
             DataOutputStream output = new DataOutputStream(buffered)) {
            NbtIo.write(tag, output);
        }
    }
}
