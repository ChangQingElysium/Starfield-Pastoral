package com.stardew.craft.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.core.IdMapper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the actual palette implementation without changing the test world. */
@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class PalettedContainerAccessGameTests {
    private static final int STATES = 4096;
    private static final int VALUES = 512;
    private static final int ROUNDS = 8;

    private PalettedContainerAccessGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty", timeoutTicks = 400)
    public static void paletteSupportsWorldGenerationHandoffAndConcurrentResize(GameTestHelper helper) throws Exception {
        IdMapper<Integer> registry = new IdMapper<>(VALUES);
        Integer[] values = new Integer[VALUES];
        for (int i = 0; i < VALUES; i++) {
            values[i] = Integer.valueOf(i);
            registry.add(values[i]);
        }
        assertGenerationCompletionRelease(helper, registry, values);
        assertWorldGenerationHandoff(helper, registry, values);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        for (int round = 0; round < ROUNDS; round++) {
            PalettedContainer<Integer> container = new PalettedContainer<>(
                    registry, values[0], PalettedContainer.Strategy.SECTION_STATES);
            exerciseConcurrentResize(helper, container, registry, values, round, deadline);
            assertCopyAndSerialization(helper, container, registry, values, round);
        }
        helper.succeed();
    }

    /** Generation completion can release a section without writing another palette slot. */
    private static void assertGenerationCompletionRelease(GameTestHelper helper, IdMapper<Integer> registry,
                                                          Integer[] values) throws Exception {
        PalettedContainer<Integer> container = new PalettedContainer<>(
                registry, values[0], PalettedContainer.Strategy.SECTION_STATES);
        CompletableFuture<Void> result = new CompletableFuture<>();
        AtomicBoolean released = new AtomicBoolean();
        Thread completion = new Thread(() -> {
            try {
                // Optimized writers can bypass slot wrappers; completion must still release safely.
                container.release();
                released.set(true);
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, "stardew-palette-generation-completion");
        completion.setDaemon(true);
        container.acquire();
        try {
            completion.start();
            result.get(2, TimeUnit.SECONDS);
        } finally {
            if (!released.get()) container.release();
            completion.join(TimeUnit.SECONDS.toMillis(2));
            helper.assertTrue(!completion.isAlive(), "Palette generation completion remained blocked");
        }
        helper.assertTrue(released.get(), "Generation completion could not release another thread's acquire");
        helper.assertTrue(container.get(0, 0, 0) == values[0], "Completion release changed an untouched state");
        // A successfully handed-off section must be available to the caller again.
        container.acquire();
        container.release();
    }

    /** NoiseBasedChunkGenerator acquires sections before handing their fill/release to a worker. */
    private static void assertWorldGenerationHandoff(GameTestHelper helper, IdMapper<Integer> registry,
                                                     Integer[] values) throws Exception {
        PalettedContainer<Integer> container = new PalettedContainer<>(
                registry, values[0], PalettedContainer.Strategy.SECTION_STATES);
        CompletableFuture<Void> result = new CompletableFuture<>();
        AtomicBoolean released = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            try {
                // Unchecked writes are the vanilla world-generation path, including palette growth.
                for (int index = 0; index < STATES; index++) {
                    int x = index & 15, y = index >> 8, z = (index >> 4) & 15;
                    container.getAndSetUnchecked(x, y, z, values[expected(index, 0)]);
                }
                container.release();
                released.set(true);
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, "stardew-palette-world-generation");
        worker.setDaemon(true);
        container.acquire();
        try {
            worker.start();
            result.get(2, TimeUnit.SECONDS);
        } finally {
            // Release on the caller only to unblock the deliberately broken implementation on failure.
            if (!released.get()) container.release();
            worker.join(TimeUnit.SECONDS.toMillis(2));
            helper.assertTrue(!worker.isAlive(), "Palette generation worker remained blocked after cleanup");
        }
        helper.assertTrue(released.get(), "Generation worker could not release the caller's acquire");
        assertAllStates(helper, container, values, 0);
    }

    private static void exerciseConcurrentResize(GameTestHelper helper, PalettedContainer<Integer> container,
                                                 IdMapper<Integer> registry, Integer[] values, int round, long deadline) throws Exception {
        AtomicBoolean stop = new AtomicBoolean();
        AtomicBoolean writerActive = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch readersReady = new CountDownLatch(3);
        CountDownLatch readersDuringWrite = new CountDownLatch(3);
        long[] reads = new long[3];
        Thread[] workers = new Thread[4];
        for (int number = 0; number < 2; number++) {
            int readerNumber = number;
            workers[number] = new Thread(() -> {
                boolean announced = false;
                boolean sampledWriter = false;
                int cursor = readerNumber * 997;
                try {
                    while (!stop.get() && !Thread.currentThread().isInterrupted()) {
                        cursor = (cursor + 37) & (STATES - 1);
                        Integer value = container.get(cursor & 15, cursor >> 8, (cursor >> 4) & 15);
                        require(value != null && value >= 0 && value < VALUES && value == values[value],
                                "Palette returned an invalid or unregistered state");
                        reads[readerNumber]++;
                        if (!announced) {
                            announced = true;
                            readersReady.countDown();
                        }
                        if (!sampledWriter && writerActive.get()) {
                            sampledWriter = true;
                            readersDuringWrite.countDown();
                        }
                    }
                } catch (Throwable caught) {
                    failure.compareAndSet(null, caught);
                    stop.set(true);
                }
            }, "stardew-palette-reader-" + round + "-" + number);
        }
        workers[2] = new Thread(() -> {
            try {
                await(readersReady, deadline, "Palette readers never reached the initial state");
                container.set(0, 0, 0, values[1]);
                writerActive.set(true);
                await(readersDuringWrite, deadline, "Palette readers never sampled the active writer");
                // Distinct registered objects force single -> linear -> hash -> global palette resizing.
                for (int value = 2; value < VALUES; value++) {
                    require(!stop.get() && System.nanoTime() < deadline, "Palette writer stopped or timed out");
                    int x = value & 15, y = value >> 8, z = (value >> 4) & 15;
                    if ((value & 1) == 0) {
                        require(container.getAndSet(x, y, z, values[value]) == values[0],
                                "Checked getAndSet changed its previous-state return");
                    } else {
                        container.set(x, y, z, values[value]);
                    }
                    require(container.get(x, y, z) == values[value], "New palette state was not readable");
                }
                for (int index = 0; index < STATES; index++) {
                    require(!stop.get() && System.nanoTime() < deadline, "Palette final fill stopped or timed out");
                    container.set(index & 15, index >> 8, (index >> 4) & 15, values[expected(index, round)]);
                }
            } catch (Throwable caught) {
                failure.compareAndSet(null, caught);
            } finally {
                stop.set(true);
            }
        }, "stardew-palette-writer-" + round);
        workers[3] = new Thread(() -> {
            boolean announced = false;
            boolean sampledWriter = false;
            try {
                while (!stop.get() && !Thread.currentThread().isInterrupted()) {
                    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                    PalettedContainer<Integer> decoded = new PalettedContainer<>(
                            registry, values[0], PalettedContainer.Strategy.SECTION_STATES);
                    try {
                        container.write(buffer);
                        decoded.read(buffer);
                        require(!buffer.isReadable(), "Concurrent serialization left unread data");
                    } finally {
                        buffer.release();
                    }
                    require(container.pack(registry, PalettedContainer.Strategy.SECTION_STATES) != null,
                            "Concurrent save returned no packed palette");
                    PalettedContainer<Integer> copy = container.copy();
                    for (int index = 0; index < STATES; index++) {
                        int x = index & 15, y = index >> 8, z = (index >> 4) & 15;
                        Integer decodedValue = decoded.get(x, y, z);
                        Integer copiedValue = copy.get(x, y, z);
                        require(decodedValue != null && decodedValue >= 0 && decodedValue < VALUES
                                        && decodedValue == values[decodedValue],
                                "Concurrent serialization produced an invalid state");
                        require(copiedValue != null && copiedValue >= 0 && copiedValue < VALUES
                                        && copiedValue == values[copiedValue],
                                "Concurrent copy produced an invalid state");
                    }
                    reads[2]++;
                    if (!announced) {
                        announced = true;
                        readersReady.countDown();
                    }
                    if (!sampledWriter && writerActive.get()) {
                        sampledWriter = true;
                        readersDuringWrite.countDown();
                    }
                }
            } catch (Throwable caught) {
                failure.compareAndSet(null, caught);
                stop.set(true);
            }
        }, "stardew-palette-serializer-" + round);
        for (Thread worker : workers) worker.setDaemon(true);
        try {
            for (Thread worker : workers) worker.start();
            joinUntil(workers[2], deadline);
        } finally {
            stop.set(true);
            for (Thread worker : workers) worker.interrupt();
            long cleanupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            for (Thread worker : workers) joinUntil(worker, cleanupDeadline);
        }
        for (Thread worker : workers) {
            helper.assertTrue(!worker.isAlive(), "Palette worker leaked after cleanup: " + worker.getName());
        }
        if (failure.get() != null) throw new AssertionError("Concurrent palette resize failed", failure.get());
        helper.assertTrue(readersDuringWrite.getCount() == 0 && reads[0] > 0 && reads[1] > 0 && reads[2] > 0,
                "Concurrent palette readers or serializer did not execute");
        assertAllStates(helper, container, values, round);
    }

    private static void assertCopyAndSerialization(GameTestHelper helper, PalettedContainer<Integer> container,
                                                   IdMapper<Integer> registry, Integer[] values, int round) {
        PalettedContainer<Integer> copy = container.copy();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        PalettedContainer<Integer> decoded = new PalettedContainer<>(
                registry, values[0], PalettedContainer.Strategy.SECTION_STATES);
        try {
            int expectedSize = container.getSerializedSize();
            container.write(buffer);
            // 1.20.1 GlobalPalette estimates one empty-palette VarInt that its write never emits.
            helper.assertTrue(buffer.readableBytes() == expectedSize - 1, "Palette serialized size changed");
            decoded.read(buffer);
            helper.assertTrue(!buffer.isReadable(), "Palette roundtrip left unread serialized data");
        } finally {
            buffer.release();
        }
        assertAllStates(helper, copy, values, round);
        assertAllStates(helper, decoded, values, round);
        int oldValue = expected(0, round);
        Integer replacement = values[(oldValue + 1) % VALUES];
        helper.assertTrue(container.getAndSet(0, 0, 0, replacement) == values[oldValue],
                "Checked getAndSet did not return the final previous state");
        helper.assertTrue(container.get(0, 0, 0) == replacement
                        && copy.get(0, 0, 0) == values[oldValue] && decoded.get(0, 0, 0) == values[oldValue],
                "Palette copy or serialized roundtrip shares mutable state with the original");
    }

    private static void assertAllStates(GameTestHelper helper, PalettedContainer<Integer> container,
                                        Integer[] values, int round) {
        for (int index = 0; index < STATES; index++) {
            helper.assertTrue(container.get(index & 15, index >> 8, (index >> 4) & 15) == values[expected(index, round)],
                    "Resized palette changed section state at index " + index);
        }
    }

    private static int expected(int index, int round) {
        return (index * 37 + round) % VALUES;
    }

    private static void await(CountDownLatch latch, long deadline, String message) throws InterruptedException {
        require(latch.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS), message);
    }

    private static void joinUntil(Thread thread, long deadline) throws InterruptedException {
        long remaining = deadline - System.nanoTime();
        if (remaining > 0) thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
