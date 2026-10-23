package com.stardew.craft.platform.fabric.event.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.stardew.craft.platform.fabric.event.ChunkEvent;
import com.stardew.craft.platform.fabric.event.Event;
import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.LevelEvent;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class FabricClientWorldEventsTest {
    private static final String MIXINS = "com/stardew/craft/platform/mixin/event/client/world/";
    private static final String MC = "net/minecraft/client/Minecraft";
    private static final String CACHE = "net/minecraft/client/multiplayer/ClientChunkCache";
    private static final String LEVEL = "Lnet/minecraft/client/multiplayer/ClientLevel;";
    private static final String SCREEN = "Lnet/minecraft/client/gui/screens/Screen;";
    private static final String CHUNK = "Lnet/minecraft/world/level/chunk/LevelChunk;";

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void everyClientLifecyclePayloadReachesBaseSubscribersAndRetainsRealLevelAndChunk() {
        var bus = new EventBus(); var levels = new ArrayList<LevelEvent>(); var chunks = new ArrayList<ChunkEvent>();
        bus.addListener(LevelEvent.class, levels::add); bus.addListener(ChunkEvent.class, chunks::add);
        var level = level(); var chunk = allocate(LevelChunk.class);
        FabricClientWorldEvents.levelLoaded(bus, level);
        FabricClientWorldEvents.chunkLoaded(bus, level, chunk);
        FabricClientWorldEvents.chunkUnloading(bus, level, chunk);
        FabricClientWorldEvents.levelUnloading(bus, level);
        assertEquals(List.of(LevelEvent.Load.class, ChunkEvent.Load.class, ChunkEvent.Unload.class, LevelEvent.Unload.class),
                levels.stream().map(Object::getClass).toList());
        assertEquals(2, chunks.size()); assertSame(chunk, chunks.getFirst().getChunk());
        for (var event : levels) { assertSame(level, event.getLevel()); assertFalse(event.isCancelable()); }
        assertFalse(((ChunkEvent.Load) chunks.getFirst()).isNewChunk());
        assertThrows(IllegalStateException.class, () -> chunks.getFirst().setCanceled(true));
    }

    @Test void rejectedPacketAndAbsentOldWorldDoNotPublishWhileFullUpdatesOfExistingChunksStillDo() {
        var bus = new EventBus(); var events = new ArrayList<Event>(); bus.addListener(Event.class, events::add);
        var level = level(); var chunk = allocate(LevelChunk.class);
        FabricClientWorldEvents.levelUnloading(bus, null);
        FabricClientWorldEvents.chunkLoaded(bus, level, null);
        assertTrue(events.isEmpty());
        FabricClientWorldEvents.chunkLoaded(bus, level, chunk);
        FabricClientWorldEvents.chunkLoaded(bus, level, chunk);
        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(e -> e instanceof ChunkEvent.Load loaded && !loaded.isNewChunk()));
    }

    @Test void dimensionReplacementInvalidatesChunkOnlyClientCacheWithoutRequiringNetworkLogout() {
        var bus = new EventBus(); var cache = new HashMap<Long, String>(); var oldLevel = level(); var newLevel = level();
        cache.put(0L, "old dimension floor");
        bus.addListener(LevelEvent.Unload.class, event -> { if (event.getLevel().isClientSide()) cache.clear(); });
        FabricClientWorldEvents.levelUnloading(bus, oldLevel);
        FabricClientWorldEvents.levelLoaded(bus, newLevel);
        assertNull(cache.get(0L));
    }

    @Test void worldHooksMatchNativeConstructorAndReplacementDisconnectOrderingWithoutTransferHelperDuplicates() throws Exception {
        var loaded = hook(MIXINS + "ClientLevelLoadEventMixin", "stardewcraft$clientLevelLoaded");
        assertEquals(List.of("<init>"), value(inject(loaded), "method")); assertEquals("RETURN", value(at(loaded), "value"));
        assertEquals("prepareWeather", calls(method(node("net/minecraft/client/multiplayer/ClientLevel"), "<init>")).getLast().name);
        var replacing = hook(MIXINS + "MinecraftLevelUnloadEventMixin", "stardewcraft$clientLevelReplacing");
        assertEquals(List.of("setLevel(" + LEVEL + "Lnet/minecraft/client/gui/screens/ReceivingLevelScreen$Reason;)V"), value(inject(replacing), "method"));
        assertEquals("HEAD", value(at(replacing), "value"));
        var disconnect = hook(MIXINS + "MinecraftLevelUnloadEventMixin", "stardewcraft$clientLevelDisconnecting");
        assertEquals(List.of("disconnect(" + SCREEN + "Z)V"), value(inject(disconnect), "method"));
        assertEquals("L" + MC + ";updateScreenAndTick(" + SCREEN + ")V", value(at(disconnect), "target"));
        assertEquals("AFTER", ((String[]) value(at(disconnect), "shift"))[1]);
        var nativeDisconnect = method(node(MC), "disconnect", "(" + SCREEN + "Z)V");
        int update = index(nativeDisconnect, i -> i instanceof MethodInsnNode c && c.name.equals("updateScreenAndTick"));
        int clear = index(nativeDisconnect, i -> i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD && f.name.equals("level"));
        assertTrue(update >= 0 && clear > update);
        var hooks = node(MIXINS + "MinecraftLevelUnloadEventMixin");
        assertEquals(2, hooks.methods.stream().filter(m -> inject(m) != null).count());
        assertTrue(hooks.methods.stream().noneMatch(m -> inject(m) != null && value(inject(m), "method").toString().contains("clearClientLevel")));
    }

    @Test void chunkHooksPublishOnlyAtNeoPacketReturnAndGuardedDropBeforeStorageMutation() throws Exception {
        var loaded = hook(MIXINS + "ClientChunkLifecycleEventMixin", "stardewcraft$clientChunkLoaded");
        assertEquals(List.of("replaceWithPacketData"), value(inject(loaded), "method")); assertEquals("RETURN", value(at(loaded), "value"));
        var nativeLoad = method(node(CACHE), "replaceWithPacketData");
        assertEquals("onChunkLoaded", calls(nativeLoad).getLast().name);
        var unload = hook(MIXINS + "ClientChunkLifecycleEventMixin", "stardewcraft$clientChunkUnloading");
        assertEquals(List.of("drop"), value(inject(unload), "method"));
        String target = "L" + CACHE + "$Storage;replace(I" + CHUNK + CHUNK + ")" + CHUNK;
        assertEquals(target, value(at(unload), "target")); assertEquals("INVOKE", value(at(unload), "value"));
        assertNull(value(at(unload), "shift"));
        var nativeDrop = calls(method(node(CACHE), "drop"));
        int range = callIndex(nativeDrop, "inRange"), valid = callIndex(nativeDrop, "isValidChunk"), replace = callIndex(nativeDrop, "replace");
        assertTrue(range >= 0 && valid > range && replace > valid);
        assertEquals("(I" + CHUNK + CHUNK + ")" + CHUNK, nativeDrop.get(replace).desc);
        assertTrue(unload.desc.contains(CHUNK));
        var local = unload.visibleParameterAnnotations == null ? unload.invisibleParameterAnnotations : unload.visibleParameterAnnotations;
        assertNotNull(local); assertTrue(java.util.Arrays.stream(local).filter(java.util.Objects::nonNull).flatMap(List::stream)
                .anyMatch(a -> a.desc.equals("Lcom/llamalad7/mixinextras/sugar/Local;")));
    }

    @Test void lifecycleMixinsAreRestrictedToTheClientConfiguration() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("stardewcraft.fabric.mixins.json")) {
            assertNotNull(stream); var config = JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (String name : List.of("ClientLevelLoadEventMixin", "MinecraftLevelUnloadEventMixin", "ClientChunkLifecycleEventMixin")) {
                String relative = "event.client.world." + name;
                assertTrue(config.getAsJsonArray("client").asList().stream().anyMatch(e -> e.getAsString().equals(relative)));
                assertFalse(config.getAsJsonArray("mixins").asList().stream().anyMatch(e -> e.getAsString().equals(relative)));
            }
        }
    }

    private static LevelAccessor level() {
        return (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(), new Class<?>[]{LevelAccessor.class},
                (proxy, method, args) -> { if (method.getName().equals("isClientSide")) return true; throw new AssertionError(method); });
    }
    private static ClassNode node(String name) throws IOException {
        try (var stream = FabricClientWorldEventsTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name); var node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static MethodNode method(ClassNode node, String name, String descriptor) { return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst().orElseThrow(); }
    private static MethodNode hook(String type, String name) throws IOException { var method = method(node(type), name); assertEquals(1, value(inject(method), "require")); return method; }
    private static AnnotationNode inject(MethodNode method) {
        return method.visibleAnnotations == null ? null : method.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElse(null);
    }
    private static AnnotationNode at(MethodNode method) { return (AnnotationNode) ((List<?>) value(inject(method), "at")).getFirst(); }
    private static Object value(AnnotationNode annotation, String name) { if (annotation == null || annotation.values == null) return null; int i = annotation.values.indexOf(name); return i < 0 ? null : annotation.values.get(i + 1); }
    private static List<MethodInsnNode> calls(MethodNode method) { return java.util.Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode).map(i -> (MethodInsnNode) i).toList(); }
    private static int callIndex(List<MethodInsnNode> calls, String name) { for (int i = 0; i < calls.size(); i++) if (calls.get(i).name.equals(name)) return i; return -1; }
    private static int index(MethodNode method, java.util.function.Predicate<AbstractInsnNode> condition) { int index = 0; for (var instruction : method.instructions) { if (condition.test(instruction)) return index; index++; } return -1; }
    private static <T> T allocate(Class<T> type) {
        try { var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type)); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
