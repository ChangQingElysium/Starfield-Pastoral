"""Exercise the production subscriber bridge with Forge's real event bus, without starting Minecraft.

Run this port-specific test explicitly, not through the dependency-free compatibility suite.
Supply resolved Forge compilation dependencies in PORT_TEST_CLASSPATH and a Java 17
JDK in JAVA17_HOME (or JAVA_HOME). To resolve the dependencies locally, first run
./gradlew printCompileClasspath, then set PORT_TEST_CLASSPATH from build/port-classpath.txt;
an external build environment may provide the same classpath directly. The test compiles tracked sources into its own
temporary directory; only MinecraftForge's global-bus holder is replaced to avoid
bootstrapping the game. No item tooltip text is filtered or deduplicated.
"""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
BRIDGE = ROOT / "src/port/java/com/stardew/craft/port/event/PortEventSubscribers.java"
ANNOTATION = ROOT / "src/port/java/com/stardew/craft/port/net/neoforged/fml/common/EventBusSubscriber.java"

BUS_HOLDER = """
package net.minecraftforge.common;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.IEventBus;
public final class MinecraftForge {
    public static final IEventBus EVENT_BUS = BusBuilder.builder().build();
}
"""

HARNESS = """
package com.stardew.craft.compatibility;
import com.stardew.craft.port.event.PortEventSubscribers;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;

public final class TooltipSubscriptionRegression {
    public static final class TooltipEvent extends Event {
        final List<String> lines = new ArrayList<>();
    }
    public static final class TooltipSubscriber {
        @SubscribeEvent public static void describe(TooltipEvent event) {
            event.lines.add("original description");
        }
    }
    public static final class ModEvent extends Event implements IModBusEvent {}
    public static final class PublicModSubscriber {
        static int calls;
        @SubscribeEvent public static void setup(ModEvent event) { calls++; }
    }
    public static final class MixedSubscriber {
        static int gameCalls, modCalls;
        @SubscribeEvent public static void game(TooltipEvent event) { gameCalls++; }
        @SubscribeEvent private static void mod(ModEvent event) { modCalls++; }
    }
    public static final class PrivateSubscriber {
        static int calls;
        @SubscribeEvent private static void game(TooltipEvent event) { calls++; }
    }
    @Cancelable public static final class CancelledEvent extends Event {
        final List<String> calls = new ArrayList<>();
    }
    public static final class CancellationSubscriber {
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void last(CancelledEvent event) { event.calls.add("last"); }
        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public static void first(CancelledEvent event) { event.calls.add("first"); }
        @SubscribeEvent public static void excluded(CancelledEvent event) { event.calls.add("excluded"); }
    }
    public static final class TypedEvent<T> extends GenericEvent<T> {
        public TypedEvent() { super(null); }
        public TypedEvent(Class<T> type) { super(type); }
    }
    public static final class GenericSubscriber {
        static int calls;
        @SubscribeEvent public static void string(TypedEvent<String> event) { calls++; }
    }
    public static final class PrivateGenericSubscriber {
        static int calls;
        @SubscribeEvent private static void string(TypedEvent<String> event) { calls++; }
    }
    public static final class PrivateCancellationSubscriber {
        @SubscribeEvent(receiveCanceled = true)
        private static void allowed(CancelledEvent event) { event.calls.add("private"); }
    }
    public static final class InvalidSubscriber {
        @SubscribeEvent public void instance(TooltipEvent event) {}
    }
    public static final class UnloadSubscriber {
        static int levels, chunks;
        @SubscribeEvent public static void unloaded(LevelEvent.Unload event) { levels++; }
        @SubscribeEvent public static void unloaded(ChunkEvent.Unload event) { chunks++; }
    }
    public static final class NativeUnloadCollision {
        @SubscribeEvent public static void unloaded(LevelEvent.Unload event) {}
        @SubscribeEvent public static void unloaded(ChunkEvent.Unload event) {}
    }
    private static void prepareNativeEventList(Class<?> type) {
        if (type == Event.class) return;
        prepareNativeEventList(type.getSuperclass());
        try {
            // ModLauncher's event transformer normally supplies these per-event listener lists. The headless test
            // has no ModLauncher: ask the real Forge helper for the same parent-linked list without a no-arg ctor.
            var method = EventListenerHelper.class.getDeclaredMethod("getListenerListInternal", Class.class, boolean.class);
            method.setAccessible(true);
            method.invoke(null, type, true);
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
    }
    // These event handlers only count delivery. Allocate the actual event classes without creating a world/chunk;
    // this keeps the real Forge event types/listener lists but does not bootstrap Minecraft just for constructors.
    private static <T extends Event> T eventWithoutWorld(Class<T> type) {
        try {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) field.get(null);
            return type.cast(unsafe.allocateInstance(type));
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
    }
    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
    public static void main(String[] args) {
        IEventBus gameBus = MinecraftForge.EVENT_BUS;
        IEventBus modBus = BusBuilder.builder().markerType(IModBusEvent.class).build();

        // StardewCraft manually registers ModClientEvents before automatic injection.
        gameBus.register(TooltipSubscriber.class);
        gameBus.addListener(EventPriority.NORMAL, false, TooltipEvent.class,
                event -> event.lines.add("later listener"));
        PortEventSubscribers.register(TooltipSubscriber.class, modBus);
        PortEventSubscribers.register(TooltipSubscriber.class, modBus);
        TooltipEvent tooltip = new TooltipEvent();
        gameBus.post(tooltip);
        check(tooltip.lines.equals(List.of("original description", "later listener")),
                "Tooltip listener duplicated or manual registration order changed: " + tooltip.lines);

        modBus.register(PublicModSubscriber.class);
        PortEventSubscribers.register(PublicModSubscriber.class, modBus);
        PortEventSubscribers.register(MixedSubscriber.class, modBus);
        PortEventSubscribers.register(PrivateSubscriber.class, modBus);
        PortEventSubscribers.register(MixedSubscriber.class, modBus);
        PortEventSubscribers.register(PrivateSubscriber.class, modBus);
        gameBus.post(new TooltipEvent());
        modBus.post(new ModEvent());
        check(PublicModSubscriber.calls == 1, "Single-bus mod subscriber duplicated");
        check(MixedSubscriber.gameCalls == 1 && MixedSubscriber.modCalls == 1,
                "Mixed-bus routing or private mod listener changed");
        check(PrivateSubscriber.calls == 1, "Private game listener was lost");

        prepareNativeEventList(LevelEvent.Unload.class);
        prepareNativeEventList(ChunkEvent.Unload.class);
        boolean nativeCollision = false;
        try {
            gameBus.register(NativeUnloadCollision.class);
            gameBus.post(eventWithoutWorld(LevelEvent.Unload.class));
            gameBus.post(eventWithoutWorld(ChunkEvent.Unload.class));
        } catch (ClassCastException expected) {
            nativeCollision = true;
        } catch (LinkageError expected) {
            // Standalone ClassLoaderFactory rejects the duplicate generated class at registration; under
            // ModLauncher the name cache can instead reuse it, producing the event cast failure seen in-game.
            check(expected.getMessage().contains("__NativeUnloadCollision_unloaded_Unload"),
                    "Unexpected native registration failure: " + expected);
            nativeCollision = true;
        } finally {
            gameBus.unregister(NativeUnloadCollision.class);
        }
        check(nativeCollision, "Fixture did not reproduce Forge's same-simple-name ASM handler collision");
        PortEventSubscribers.register(UnloadSubscriber.class, modBus);
        PortEventSubscribers.register(UnloadSubscriber.class, modBus);
        gameBus.post(eventWithoutWorld(LevelEvent.Unload.class));
        // LevelEvent.Unload and ChunkEvent.Unload are separate subclasses: each overload must keep its complete
        // parameter type. Same simple names must not share Forge's generated ASM listener class.
        gameBus.post(eventWithoutWorld(ChunkEvent.Unload.class));
        check(UnloadSubscriber.levels == 1 && UnloadSubscriber.chunks == 1,
                "Same-name Unload overloads collided or fallback registration duplicated: "
                        + UnloadSubscriber.levels + "/" + UnloadSubscriber.chunks);

        PortEventSubscribers.register(CancellationSubscriber.class, modBus);
        CancelledEvent cancelled = new CancelledEvent();
        cancelled.setCanceled(true);
        gameBus.post(cancelled);
        check(cancelled.calls.equals(List.of("first", "last")),
                "Priority/receiveCanceled changed: " + cancelled.calls);
        PortEventSubscribers.register(PrivateCancellationSubscriber.class, modBus);
        cancelled = new CancelledEvent();
        cancelled.setCanceled(true);
        gameBus.post(cancelled);
        check(cancelled.calls.equals(List.of("first", "private", "last")),
                "Fallback priority/receiveCanceled changed: " + cancelled.calls);

        PortEventSubscribers.register(GenericSubscriber.class, modBus);
        PortEventSubscribers.register(PrivateGenericSubscriber.class, modBus);
        gameBus.post(new TypedEvent<>(Integer.class));
        gameBus.post(new TypedEvent<>(String.class));
        check(GenericSubscriber.calls == 1, "Generic-event type filter changed");
        check(PrivateGenericSubscriber.calls == 1, "Fallback generic-event type filter changed");
        try {
            PortEventSubscribers.register(InvalidSubscriber.class, modBus);
            throw new AssertionError("Non-static @SubscribeEvent method was accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("static"), "Static-method validation changed");
        }
        gameBus.unregister(TooltipSubscriber.class);
        TooltipEvent unregistered = new TooltipEvent();
        gameBus.post(unregistered);
        check(unregistered.lines.equals(List.of("later listener")), "Native class unregistration changed");
        System.out.println("PASS: single tooltip delivery/order, class deduplication, mixed/private routing, "
                + "priority, cancellation, generic filter, static validation and Level/Chunk Unload overloads");
    }
}
"""


class ForgeTooltipSubscriptionTest(unittest.TestCase):
    def test_production_bridge_preserves_native_subscription_semantics(self) -> None:
        classpath = os.environ.get("PORT_TEST_CLASSPATH")
        self.assertTrue(classpath, "Set PORT_TEST_CLASSPATH to the resolved Forge compile classpath")
        java_home = os.environ.get("JAVA17_HOME") or os.environ.get("JAVA_HOME")
        javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
        java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
        self.assertTrue(javac and java, "A Java 17 JDK is required")
        with tempfile.TemporaryDirectory(prefix="stardew-tooltip-subscription-") as temporary:
            directory = Path(temporary)
            holder = directory / "MinecraftForge.java"
            harness = directory / "TooltipSubscriptionRegression.java"
            holder.write_text(BUS_HOLDER, encoding="utf-8")
            harness.write_text(HARNESS, encoding="utf-8")
            compile_result = subprocess.run(
                [javac, "-proc:none", "-encoding", "UTF-8", "-cp", classpath,
                 "-d", str(directory), str(holder), str(harness), str(BRIDGE), str(ANNOTATION)],
                capture_output=True, text=True, check=False,
            )
            self.assertEqual(0, compile_result.returncode, compile_result.stdout + compile_result.stderr)
            run_result = subprocess.run(
                [java, "-cp", str(directory) + os.pathsep + classpath,
                 "com.stardew.craft.compatibility.TooltipSubscriptionRegression"],
                capture_output=True, text=True, check=False,
            )
            self.assertEqual(0, run_result.returncode, run_result.stdout + run_result.stderr)
            self.assertIn("PASS: single tooltip delivery/order", run_result.stdout)


if __name__ == "__main__":
    unittest.main()
