import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;

/** Real accessor weaving and JVM override dispatch, without Minecraft initialization or a GPU. */
public final class ContainerSlotMixinCheck implements Opcodes {
    private static final String TARGET = "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen";
    private static final String ACCESSOR = "com/stardew/craft/mixin/PortContainerScreenAccessor";
    private static final String PORT = "com/stardew/craft/port/PortContainerScreen";
    private static final String SCREENS = "com/stardew/craft/port/PortScreens";
    private static final String HOOK = "port$renderSlot";
    private static final String INVOKER = "port$invokeVanillaRenderSlot";
    private static final String SLOT_DESC = "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V";
    private static final Map<String, String> METHODS = new HashMap<>();
    private static boolean srg;

    public static final class Service extends OfflineMixinApplicationCheck.Service {
        @Override public ClassNode getClassNode(String name, boolean runTransformers)
                throws ClassNotFoundException, java.io.IOException {
            ClassNode node = super.getClassNode(name, runTransformers);
            if (!srg || !node.name.equals(ACCESSOR)) return node;
            ClassWriter writer = new ClassWriter(0);
            node.accept(new ClassRemapper(writer, new Remapper() {
                @Override public String mapMethodName(String owner, String name, String descriptor) {
                    return METHODS.getOrDefault(owner + "/" + name + descriptor, name);
                }
            }));
            ClassNode mapped = new ClassNode();
            new ClassReader(writer.toByteArray()).accept(mapped, 0);
            return mapped;
        }
    }

    public static void main(String[] args) throws Throwable {
        srg = args[0].equals("srg");
        Path output = Path.of(args[1]);
        if (srg) {
            for (String line : Files.readAllLines(Path.of("build/createSrgToMcp/output.srg"))) {
                String[] parts = line.split(" ");
                if (parts[0].equals("MD:"))
                    METHODS.put(parts[3] + parts[4], parts[1].substring(parts[1].lastIndexOf('/') + 1));
            }
            Files.copy(Path.of("build/tmp/compileJava/stardewcraft.refmap.json"), output.resolve("stardewcraft.refmap.json"));
        }
        JsonObject config = new JsonObject();
        config.addProperty("required", true);
        config.addProperty("minVersion", "0.8");
        config.addProperty("compatibilityLevel", "JAVA_17");
        config.addProperty("package", "com.stardew.craft.mixin");
        if (srg) config.addProperty("refmap", "stardewcraft.refmap.json");
        JsonArray client = new JsonArray();
        client.add("PortContainerScreenAccessor");
        config.add("client", client);
        String configName = "container-slot-check.mixins.json";
        Files.writeString(output.resolve(configName), config.toString());
        MixinBootstrap.init();
        Mixins.addConfiguration(configName);
        if (srg) MixinEnvironment.getDefaultEnvironment().setObfuscationContext("searge");
        Field factory = OfflineMixinApplicationCheck.class.getDeclaredField("factory");
        factory.setAccessible(true);
        var transformer = ((IMixinTransformerFactory) factory.get(null)).createTransformer();
        Method phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true);
        phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        Service provider = new Service();
        ClassNode input = provider.getClassNode(TARGET);
        ClassWriter writer = new ClassWriter(0);
        input.accept(writer);
        byte[] woven = transformer.transformClassBytes(TARGET.replace('/', '.'), TARGET.replace('/', '.'), writer.toByteArray());
        ClassNode result = read(woven);
        String nativeSlot = METHODS.getOrDefault(TARGET + "/renderSlot" + SLOT_DESC, "renderSlot");
        MethodNode invoker = result.methods.stream().filter(m -> m.name.equals(INVOKER) && m.desc.equals(SLOT_DESC))
                .findFirst().orElseThrow(() -> new AssertionError("Production vanilla-slot invoker missing"));
        boolean callsNative = false;
        for (var instruction : invoker.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(TARGET)
                    && call.name.equals(nativeSlot) && call.desc.equals(SLOT_DESC)) callsNative = true;
        }
        require(callsNative, "Generated invoker does not resolve the actual private vanilla slot renderer");
        require(result.methods.stream().noneMatch(m -> m.name.equals(HOOK) && m.desc.equals(SLOT_DESC)),
                "Accessor collides with the overridable screen hook");
        require(result.methods.stream().anyMatch(m -> m.name.equals(nativeSlot) && m.desc.equals(SLOT_DESC)
                && (m.access & ACC_PRIVATE) != 0), "Fixture must test the actual private vanilla method");
        int screens = checkScreenDeclarations(Path.of(args[2]));
        exercise(woven, nativeSlot, false);
        // Negative control: restore the old invoker/hook name collision in memory. The exact same JVM path
        // must re-enter the override, otherwise the positive test could be passing without virtual dispatch.
        boolean reproduced = false;
        try { exercise(woven, nativeSlot, true); }
        catch (InvocationTargetException failure) {
            reproduced = failure.getCause() instanceof AssertionError
                    && "Slot override re-entered".equals(failure.getCause().getMessage());
        }
        require(reproduced, "Negative control did not reproduce the former virtual-dispatch recursion");
        System.out.println("PASS container slot Mixin: " + args[0]
                + ", " + screens + " screen declarations, real private invoker, default/super/self-drawn dispatch, collision negative control");
    }

    private static int checkScreenDeclarations(Path productionClasses) throws java.io.IOException {
        int screens = 0;
        boolean bundle = false;
        try (var files = Files.walk(productionClasses.resolve("com/stardew/craft"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassNode node = new ClassNode();
                new ClassReader(Files.readAllBytes(file)).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                if (!node.interfaces.contains(PORT)) continue;
                screens++;
                bundle |= node.name.equals("com/stardew/craft/communitycenter/client/BundleScreen");
                require(node.methods.stream().noneMatch(m -> m.name.equals(INVOKER) && m.desc.equals(SLOT_DESC)),
                        "Screen overrides the private renderer invoker: " + node.name);
            }
        }
        require(screens > 0 && bundle, "Current production container screen classes were not checked");
        return screens;
    }

    private static void exercise(byte[] woven, String nativeSlot, boolean collision) throws Throwable {
        ClassNode target = read(woven);
        // Keep the real Mixin-generated invoker untouched. Only remove unrelated Minecraft state and replace
        // GPU drawing with a counter, allowing the same JVM private/virtual calls to run without a client.
        target.superName = "java/lang/Object";
        target.signature = null;
        target.access = ACC_PUBLIC | ACC_SUPER;
        target.interfaces.clear();
        target.interfaces.add(ACCESSOR);
        target.fields.clear();
        target.fields.add(new FieldNode(ACC_PUBLIC, "vanillaCalls", "I", null, null));
        target.methods.removeIf(m -> !m.name.equals(INVOKER) || !m.desc.equals(SLOT_DESC));
        MethodNode vanilla = new MethodNode(ACC_PRIVATE, nativeSlot, SLOT_DESC, null, null);
        vanilla.visitVarInsn(ALOAD, 0);
        vanilla.visitInsn(DUP);
        vanilla.visitFieldInsn(GETFIELD, TARGET, "vanillaCalls", "I");
        vanilla.visitInsn(ICONST_1);
        vanilla.visitInsn(IADD);
        vanilla.visitFieldInsn(PUTFIELD, TARGET, "vanillaCalls", "I");
        vanilla.visitInsn(RETURN);
        target.methods.add(vanilla);
        addConstructor(target, "java/lang/Object");

        Map<String, byte[]> classes = new HashMap<>();
        classes.put(TARGET, encode(target, collision));
        for (String name : new String[]{ACCESSOR, PORT, "com/stardew/craft/port/PortScreen", SCREENS}) {
            ClassNode node = read(resource(name));
            if (name.equals(SCREENS)) {
                node.fields.clear();
                node.methods.removeIf(m -> !m.name.equals("vanillaRenderSlot"));
            }
            classes.put(name, encode(node, collision));
        }
        String child = "ContainerSlotFixture";
        classes.put(child, childClass(child, 1));
        classes.put("ContainerSlotDefaultFixture", childClass("ContainerSlotDefaultFixture", 0));
        classes.put("ContainerSlotSelfDrawnFixture", childClass("ContainerSlotSelfDrawnFixture", 2));
        ClassLoader loader = new ClassLoader(ContainerSlotMixinCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    byte[] bytes = classes.get(name.replace('.', '/'));
                    if (bytes == null) return super.loadClass(name, resolve);
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        };
        Class<?> type = loader.loadClass(child);
        Object screen = type.getConstructor().newInstance();
        Method hook = loader.loadClass(PORT.replace('/', '.')).getMethod(HOOK,
                loader.loadClass("net.minecraft.client.gui.GuiGraphics"), loader.loadClass("net.minecraft.world.inventory.Slot"));
        hook.invoke(screen, null, null);
        require(type.getField("overrideCalls").getInt(screen) == 1, "Screen override must run exactly once");
        require(type.getField("vanillaCalls").getInt(screen) == 1, "Screen super call must draw vanilla exactly once");
        if (!collision) {
            Class<?> plainType = loader.loadClass("ContainerSlotDefaultFixture");
            Object plain = plainType.getConstructor().newInstance();
            hook.invoke(plain, null, null);
            require(plainType.getField("vanillaCalls").getInt(plain) == 1, "Default slot hook must draw vanilla once");
            Class<?> customType = loader.loadClass("ContainerSlotSelfDrawnFixture");
            Object custom = customType.getConstructor().newInstance();
            hook.invoke(custom, null, null);
            require(customType.getField("overrideCalls").getInt(custom) == 1
                            && customType.getField("vanillaCalls").getInt(custom) == 0,
                    "Self-drawn/empty slot hook must not acquire vanilla drawing");
        }
    }

    private static byte[] childClass(String name, int mode) {
        ClassNode child = new ClassNode();
        child.version = V17;
        child.access = ACC_PUBLIC | ACC_SUPER;
        child.name = name;
        child.superName = TARGET;
        child.interfaces.add(PORT);
        addConstructor(child, TARGET);
        if (mode != 0) {
            child.fields.add(new FieldNode(ACC_PUBLIC, "overrideCalls", "I", null, null));
            MethodNode method = new MethodNode(ACC_PUBLIC, HOOK, SLOT_DESC, null, null);
            method.visitVarInsn(ALOAD, 0);
            method.visitInsn(DUP);
            method.visitFieldInsn(GETFIELD, name, "overrideCalls", "I");
            method.visitInsn(ICONST_1);
            method.visitInsn(IADD);
            method.visitInsn(DUP_X1);
            method.visitFieldInsn(PUTFIELD, name, "overrideCalls", "I");
            method.visitInsn(ICONST_1);
            var first = new org.objectweb.asm.Label();
            method.visitJumpInsn(IF_ICMPEQ, first);
            method.visitTypeInsn(NEW, "java/lang/AssertionError");
            method.visitInsn(DUP);
            method.visitLdcInsn("Slot override re-entered");
            method.visitMethodInsn(INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/Object;)V", false);
            method.visitInsn(ATHROW);
            method.visitLabel(first);
            if (mode == 1) {
                method.visitVarInsn(ALOAD, 0);
                method.visitVarInsn(ALOAD, 1);
                method.visitVarInsn(ALOAD, 2);
                method.visitMethodInsn(INVOKESPECIAL, PORT, HOOK, SLOT_DESC, true);
            }
            method.visitInsn(RETURN);
            child.methods.add(method);
        }
        return encode(child, false);
    }

    private static void addConstructor(ClassNode node, String parent) {
        MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        node.methods.add(constructor);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] resource(String name) throws java.io.IOException {
        try (var input = ContainerSlotMixinCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new IllegalStateException("Missing production class: " + name);
            return input.readAllBytes();
        }
    }

    private static byte[] encode(ClassNode node, boolean collision) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        node.accept(new ClassRemapper(writer, new Remapper() {
            @Override public String mapMethodName(String owner, String name, String descriptor) {
                return collision && name.equals(INVOKER) && descriptor.equals(SLOT_DESC) ? HOOK : name;
            }
        }));
        return writer.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
