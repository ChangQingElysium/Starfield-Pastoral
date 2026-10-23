package com.stardew.craft.platform.fabric.event.entity.projectile;

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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Native bytecode and maintained registrations are evidence; these are not transformed-world tests. */
class ProjectileImpactHookContractTest {
    private static final String NATIVE = "net/minecraft/world/entity/projectile/";
    private static final String MIXIN = "com/stardew/craft/platform/mixin/event/entity/projectile/";
    private static final String HIT = "(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;";
    private record Site(String caller, String method, String owner) {}

    @Test void actualNativeJarHasExactlySevenImpactSitesAndTheMaintainedMixinsCoverEachOnce() throws IOException {
        Map<Site, Integer> actual = new HashMap<>();
        var location = getClass().getClassLoader().getResource(NATIVE + "Projectile.class"); assertNotNull(location);
        var connection = assertInstanceOf(JarURLConnection.class, location.openConnection());
        // The loader shares this JarFile; closing it breaks later native class loading.
        var jar = connection.getJarFile();
        for (var entries = jar.entries(); entries.hasMoreElements();) {
            var entry = entries.nextElement();
            if (!entry.getName().startsWith("net/minecraft/") || !entry.getName().endsWith(".class")) continue;
            var type = new ClassNode();
            try (var stream = jar.getInputStream(entry)) { new ClassReader(stream).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); }
            for (var method : type.methods) for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                    && call.name.equals("hitTargetOrDeflectSelf") && call.desc.equals(HIT)) {
                assertEquals(Opcodes.INVOKEVIRTUAL, call.getOpcode());
                actual.merge(new Site(type.name, method.name + method.desc, call.owner), 1, Integer::sum);
            }
        }
        assertEquals(7, actual.size()); assertEquals(7, actual.values().stream().mapToInt(Integer::intValue).sum());
        Map<Site, Integer> covered = new HashMap<>();
        var maintained = maintainedMixins();
        assertEquals(Set.of("ArrowImpactMixin", "ThrowableImpactMixin", "HurtingProjectileImpactMixin", "ShulkerBulletImpactMixin",
                "LlamaSpitImpactMixin", "FishingHookImpactMixin", "FireworkImpactMixin"), Set.copyOf(maintained));
        for (String name : maintained) {
            var mixin = node(MIXIN + name); String target = target(mixin);
            assertNotEquals(NATIVE + "Projectile", target, "A generic base hook would double-post explicit custom producers and move firework timing");
            if (name.equals("FireworkImpactMixin")) {
                covered.put(new Site(target, "tick()V", target), 1); continue;
            }
            for (var method : mixin.methods) for (var annotation : annotations(method)) {
                if (!annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;")) continue;
                assertEquals(1, value(annotation, "require"));
                for (AnnotationNode at : nested(value(annotation, "at"))) {
                    String selector = (String) value(at, "target");
                    if (!selector.contains(";hitTargetOrDeflectSelf" + HIT)) continue;
                    String owner = selector.substring(1, selector.indexOf(';'));
                    for (String enclosing : strings(value(annotation, "method"))) covered.merge(new Site(target, enclosing, owner), 1, Integer::sum);
                }
            }
        }
        assertEquals(actual, covered);
    }

    @Test void arrowPinnedLocalAndUniqueImpulseWriteLeadToLoopExitBeforeNormalMovement() throws IOException {
        var method = method(NATIVE + "AbstractArrow", "tick");
        int hit = call(method, "hitTargetOrDeflectSelf"); int impulse = -1, writes = 0;
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.PUTFIELD && field.name.equals("hasImpulse")) { impulse = i; writes++; }
        assertEquals(1, writes); assertTrue(hit < impulse);
        int pinnedHit = hit;
        assertTrue(method.localVariables.stream().anyMatch(local -> local.index == 8 && local.desc.equals("Lnet/minecraft/world/phys/EntityHitResult;")
                && method.instructions.indexOf(local.start) <= pinnedHit && method.instructions.indexOf(local.end) > pinnedHit));
        int exit = -1;
        for (int i = impulse + 1; i < method.instructions.size(); i++) {
            if (!(method.instructions.get(i) instanceof VarInsnNode variable) || variable.getOpcode() != Opcodes.ALOAD || variable.var != 8) continue;
            var next = nextCode(variable);
            if (next instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNULL) { exit = method.instructions.indexOf(jump.label); break; }
        }
        assertTrue(exit > impulse);
        int movement = callAfter(method, "getDeltaMovement", impulse);
        assertTrue(exit < movement); assertTrue(movement < callAfter(method, "setPos", movement));
        assertTrue(movement < callAfter(method, "checkInsideBlocks", movement));
        var mixin = method(MIXIN + "ArrowImpactMixin", "stardewcraft$impact");
        assertTrue(call(mixin, "allowTarget") < call(mixin, "call"));
    }

    @Test void fishingBypassesDeflectionAndFireworkGatesOnlyTheInheritedOnHitAfterNativeDeflection() throws IOException {
        var fishing = method(NATIVE + "FishingHook", "checkCollision");
        assertEquals(Opcodes.POP, nextCode(fishing.instructions.get(call(fishing, "hitTargetOrDeflectSelf"))).getOpcode());
        var fishingMixin = method(MIXIN + "FishingHookImpactMixin", "stardewcraft$impact");
        assertTrue(call(fishingMixin, "allowDirectHit") < call(fishingMixin, "onHit"));
        assertFalse(calls(fishingMixin, "call"), "NeoForge fishing uses onHit, never the replaced deflection operation");
        assertFalse(node(NATIVE + "FireworkRocketEntity").methods.stream().anyMatch(method -> method.name.equals("onHit")));
        var firework = method(MIXIN + "FireworkImpactMixin", "onHit");
        int gate = call(firework, "allowDirectHit"), delegate = call(firework, "onHit"); assertTrue(gate < delegate);
        var original = (MethodInsnNode) firework.instructions.get(delegate);
        assertEquals(Opcodes.INVOKESPECIAL, original.getOpcode()); assertEquals(NATIVE + "Projectile", original.owner);
        var nativeDispatch = method(NATIVE + "Projectile", "hitTargetOrDeflectSelf");
        assertTrue(call(nativeDispatch, "deflection") < call(nativeDispatch, "onHit"));
    }

    private static List<String> maintainedMixins() throws IOException {
        try (var stream = ProjectileImpactHookContractTest.class.getClassLoader().getResourceAsStream("stardewcraft.fabric.mixins.json")) {
            assertNotNull(stream); var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("com.stardew.craft.platform.mixin", json.get("package").getAsString());
            List<String> names = new ArrayList<>();
            json.getAsJsonArray("mixins").forEach(entry -> {
                String name = entry.getAsString(); if (name.startsWith("event.entity.projectile.")) names.add(name.substring("event.entity.projectile.".length()));
            }); return names;
        }
    }
    private static ClassNode node(String name) throws IOException {
        try (var stream = ProjectileImpactHookContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name); var node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
    private static MethodNode method(String owner, String name) throws IOException { return node(owner).methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static boolean calls(MethodNode method, String name) { return java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false).anyMatch(i -> i instanceof MethodInsnNode call && call.name.equals(name)); }
    private static int call(MethodNode method, String name) { return callAfter(method, name, -1); }
    private static int callAfter(MethodNode method, String name, int start) {
        for (int i = start + 1; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call && call.name.equals(name)) return i;
        fail(method.name + " lacks " + name); return -1;
    }
    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) { do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0); return instruction; }
    private static String target(ClassNode node) {
        var annotation = annotations(node).stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") var values = (List<Type>) value(annotation, "value"); assertEquals(1, values.size()); return values.getFirst().getInternalName();
    }
    private static List<AnnotationNode> annotations(ClassNode node) { return combine(node.visibleAnnotations, node.invisibleAnnotations); }
    private static List<AnnotationNode> annotations(MethodNode node) { return combine(node.visibleAnnotations, node.invisibleAnnotations); }
    private static List<AnnotationNode> combine(List<AnnotationNode> first, List<AnnotationNode> second) {
        List<AnnotationNode> result = new ArrayList<>(); if (first != null) result.addAll(first); if (second != null) result.addAll(second); return result;
    }
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1); return null;
    }
    @SuppressWarnings("unchecked") private static List<String> strings(Object value) { return value instanceof List<?> ? (List<String>) value : List.of((String) value); }
    @SuppressWarnings("unchecked") private static List<AnnotationNode> nested(Object value) { return value instanceof List<?> ? (List<AnnotationNode>) value : List.of((AnnotationNode) value); }
}
