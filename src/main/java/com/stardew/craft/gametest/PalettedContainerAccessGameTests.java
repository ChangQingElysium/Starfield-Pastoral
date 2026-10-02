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
import java.util.concurrent.locks.LockSupport;

/** Exercises the actual palette implementation without changing the test world. */
@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class PalettedContainerAccessGameTests {
    private static final int STATES = 4096;
    private static final int VALUES = 512;
    private static final int ROUNDS = 8;

    private PalettedContainerAccessGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty", timeoutTicks = 400)
    public static void paletteReadersWaitForWritersAndPreserveResizedState(GameTestHelper helper) throws Exception {
        IdMapper<Integer> registry = new IdMapper<>(VALUES);
        Integer[] values = new Integer[VALUES];
        for (int i = 0; i < VALUES; i++) {
            values[i] = Integer.valueOf(i);
            registry.add(values[i]);
        }
        assertReaderWaitsForAcquire(helper, registry, values);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        for (int round = 0; round < ROUNDS; round++) {
            PalettedContainer<Integer> container = new PalettedContainer<>(
                    registry, values[0], PalettedContainer.Strategy.SECTION_STATES);
            exerciseConcurrentResize(helper, container, values, round, deadline);
            assertCopyAndSerialization(helper, container, registry, values, round);
        }
        helper.succeed();
    }

    private static void assertReaderWaitsForAcquire(GameTestHelper helper, IdMapper<Integer> registry,
                                                    Integer[] values) throws Exception {
        PalettedContainer<Integer> container = new PalettedContainer<>(
                registry, values[0], PalettedContainer.Strategy.SECTION_STATES);
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<Integer> result = new CompletableFuture<>();
        Thread reader = new Thread(() -> {
            entered.countDown();
            try {
                result.complete(container.get(0, 0, 0));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, "stardew-palette-locked-reader");
        reader.setDaemon(true);
        container.acquire();
        try {
            reader.start();
            helper.assertTrue(entered.await(2, TimeUnit.SECONDS), "Palette reader did not start");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            boolean waitingInGet = false;
            while (!result.isDone() && System.nanoTime() < deadline) {
                Thread.State state = reader.getState();
                if (state == Thread.State.WAITING || state == Thread.State.BLOCKED) {
                    for (StackTraceElement frame : reader.getStackTrace()) {
                        if (frame.getClassName().equals(PalettedContainer.class.getName())
                                && frame.getMethodName().equals("get")) {
                            waitingInGet = true;
                            break;
                        }
                    }
                }
                if (waitingInGet) break;
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            helper.assertTrue(waitingInGet && !result.isDone(),
                    "Public palette get did not wait for the acquired writer lock");
        } finally {
            container.release();
            reader.join(TimeUnit.SECONDS.toMillis(2));
            if (reader.isAlive()) {
                reader.interrupt();
                reader.join(TimeUnit.SECONDS.toMillis(2));
            }
            helper.assertTrue(!reader.isAlive(), "Locked palette reader was not joined after release");
        }
        helper.assertTrue(result.get(2, TimeUnit.SECONDS) == values[0],
                "Reader returned a different state after the writer lock was released");
    }

    private static void exerciseConcurrentResize(GameTestHelper helper, PalettedContainer<Integer> container,
                                                 Integer[] values, int round, long deadline) throws Exception {
        AtomicBoolean stop = new AtomicBoolean();
        AtomicBoolean writerActive = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch readersReady = new CountDownLatch(2);
        CountDownLatch readersDuringWrite = new CountDownLatch(2);
        long[] reads = new long[2];
        Thread[] workers = new Thread[3];
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
        helper.assertTrue(readersDuringWrite.getCount() == 0 && reads[0] > 0 && reads[1] > 0,
                "Concurrent palette readers did not execute");
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
