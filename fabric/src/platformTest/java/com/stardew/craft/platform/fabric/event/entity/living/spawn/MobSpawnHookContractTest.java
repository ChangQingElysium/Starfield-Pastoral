package com.stardew.craft.platform.fabric.event.entity.living.spawn;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Reads the actual runtime native jar and maintained Mixin JSON, without world/client construction. */
class MobSpawnHookContractTest {
    private static final String MOB = "net/minecraft/world/entity/Mob";
    private static final String FINALIZE = "(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;";
    private record Site(String caller, String method, String owner, String descriptor) {}

    @Test void maintainedMixinsCoverAllThirtyVirtualSitesAndLeaveFortyFiveSuperCallsAlone() throws IOException {
        Map<Site, Integer> nativeCalls = new HashMap<>();
        int[] supers = {0};
        var location = getClass().getClassLoader().getResource(MOB + ".class");
        assertNotNull(location);
        assertInstanceOf(JarURLConnection.class, location.openConnection(), "Native jar audit requires the normal mapped Minecraft runtime jar");
        var connection = (JarURLConnection) location.openConnection();
        // JarURLConnection is shared by the class loader; do not close its JarFile.
        var jar = connection.getJarFile();
        for (var entries = jar.entries(); entries.hasMoreElements();) {
            var entry = entries.nextElement();
            if (!entry.getName().startsWith("net/minecraft/") || !entry.getName().endsWith(".class")) continue;
            ClassNode node = new ClassNode();
            try (var stream = jar.getInputStream(entry)) { new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); }
            for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || !call.name.equals("finalizeSpawn") || !call.desc.equals(FINALIZE)) continue;
                if (call.getOpcode() == Opcodes.INVOKESPECIAL) supers[0]++;
                else {
                    assertEquals(Opcodes.INVOKEVIRTUAL, call.getOpcode());
                    nativeCalls.merge(new Site(node.name, method.name + method.desc, call.owner, call.desc), 1, Integer::sum);
                }
            }
        }
        assertEquals(30, nativeCalls.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(45, supers[0]);
        Map<Site, Integer> covered = new HashMap<>();
        for (String mixin : spawnMixins()) {
            var node = node(mixin.replace('.', '/'));
            List<String> targets = mixinTargets(node);
            for (var method : node.methods) for (var annotation : annotations(method)) {
                if (!annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;")) continue;
                assertEquals(1, value(annotation, "require"));
                for (String selector : strings(value(annotation, "method"))) for (AnnotationNode at : nested(value(annotation, "at"))) {
                    String target = (String) value(at, "target");
                    assertNotNull(target); assertTrue(target.contains(";finalizeSpawn" + FINALIZE));
                    String owner = target.substring(1, target.indexOf(';'));
                    for (String caller : targets) covered.merge(new Site(caller, selector, owner, FINALIZE), 1, Integer::sum);
                }
            }
        }
        assertEquals(nativeCalls, covered, "Every outer call, including newborn jockeys inside overrides, needs exactly one hook");
        assertTrue(covered.keySet().stream().anyMatch(site -> site.caller.endsWith("/Spider") && site.method.startsWith("finalizeSpawn") && site.owner.endsWith("/Skeleton")));
        assertTrue(covered.keySet().stream().anyMatch(site -> site.caller.endsWith("/Zombie") && site.method.startsWith("finalizeSpawn") && site.owner.endsWith("/Chicken")));
    }

    @Test void spawnerFalseBranchesHaveLivePinnedMobAndBooleanLocalsBeforeEquipmentOrPersistence() throws IOException {
        String base = "net/minecraft/world/level/BaseSpawner";
        String trial = "net/minecraft/world/level/block/entity/trialspawner/TrialSpawner";
        var classic = method(base, "serverTick");
        int classicFinalize = call(classic, "finalizeSpawn"); int equipment = call(classic, "getEquipment");
        assertTrue(classicFinalize < equipment);
        assertLocal(classic, equipment, 20, "L" + MOB + ";"); assertLocal(classic, equipment, 21, "Z");
        assertTrue(hasVariable(classic, Opcodes.ISTORE, 21, classicFinalize));
        var testing = method(trial, "spawnMob");
        int trialFinalize = call(testing, "finalizeSpawn"); int persistence = call(testing, "setPersistenceRequired");
        assertTrue(trialFinalize < persistence); assertTrue(persistence < call(testing, "getEquipment"));
        assertLocal(testing, persistence, 18, "L" + MOB + ";"); assertLocal(testing, persistence, 19, "Z");
        assertTrue(hasVariable(testing, Opcodes.ISTORE, 19, trialFinalize));
    }

    @Test void placementAndFreshEntityGuardsUseRealNativeReturnAndInsertionMethods() throws IOException {
        var placement = method("net/minecraft/world/entity/SpawnPlacements", "checkSpawnRules");
        assertTrue(call(placement, "test") >= 0);
        assertTrue(java.util.stream.StreamSupport.stream(placement.instructions.spliterator(), false).anyMatch(i -> i.getOpcode() == Opcodes.IRETURN));
        var server = method("net/minecraft/server/level/ServerLevel", "addFreshEntity");
        assertTrue(call(server, "addEntity") >= 0);
        var region = method("net/minecraft/server/level/WorldGenRegion", "addFreshEntity");
        assertTrue(call(region, "addEntity") >= 0);
        Set<String> mixins = Set.copyOf(spawnMixins());
        String prefix = "com.stardew.craft.platform.mixin.event.entity.living.spawn.mixin.";
        assertTrue(mixins.contains(prefix + "SpawnPlacementCheckMixin"));
        assertTrue(mixins.contains(prefix + "ServerSpawnCancellationMixin"));
        assertTrue(mixins.contains(prefix + "WorldGenSpawnCancellationMixin"));
        assertTrue(mixins.contains(prefix + "MobSpawnCancellationStateMixin"));
        assertTrue(mixins.contains(prefix + "NativeSpawnerOwnersMixin"));
    }

    private static List<String> spawnMixins() throws IOException {
        try (var stream = MobSpawnHookContractTest.class.getClassLoader().getResourceAsStream("stardewcraft.fabric.mixins.json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            String prefix = json.get("package").getAsString() + ".";
            List<String> result = new ArrayList<>();
            json.getAsJsonArray("mixins").forEach(entry -> {
                String name = entry.getAsString();
                if (name.startsWith("event.entity.living.spawn.mixin.")) result.add(prefix + name);
            });
            assertEquals(29, result.size()); return result;
        }
    }
    private static ClassNode node(String owner) throws IOException {
        try (var stream = MobSpawnHookContractTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(stream, owner); var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
    private static MethodNode method(String owner, String name) throws IOException {
        return node(owner).methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }
    private static int call(MethodNode method, String name) {
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call && call.name.equals(name)) return i;
        fail("Missing native call " + method.name + " -> " + name); return -1;
    }
    private static void assertLocal(MethodNode method, int anchor, int index, String descriptor) {
        assertTrue(method.localVariables.stream().anyMatch(local -> local.index == index && local.desc.equals(descriptor)
                && method.instructions.indexOf(local.start) <= anchor && method.instructions.indexOf(local.end) > anchor));
    }
    private static boolean hasVariable(MethodNode method, int opcode, int index, int before) {
        for (int i = 0; i < before; i++) if (method.instructions.get(i) instanceof VarInsnNode variable
                && variable.getOpcode() == opcode && variable.var == index) return true;
        return false;
    }
    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> result = new ArrayList<>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result;
    }
    private static List<String> mixinTargets(ClassNode node) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
        var mixin = annotations.stream().filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        Object values = value(mixin, "targets");
        if (values != null) return strings(values).stream().map(name -> name.replace('.', '/')).toList();
        @SuppressWarnings("unchecked") List<Type> types = (List<Type>) value(mixin, "value");
        return types.stream().map(Type::getInternalName).toList();
    }
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }
    @SuppressWarnings("unchecked") private static List<String> strings(Object value) { return value instanceof List<?> ? (List<String>) value : List.of((String) value); }
    @SuppressWarnings("unchecked") private static List<AnnotationNode> nested(Object value) { return value instanceof List<?> ? (List<AnnotationNode>) value : List.of((AnnotationNode) value); }
}
