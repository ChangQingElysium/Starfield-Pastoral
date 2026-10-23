import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;

/** Real production + official AttributesLib weaving and JVM execution of their actual sweep handlers. */
public final class ApothicSweepMixinCheck implements Opcodes {
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String APOTHIC = "dev/shadowsoffire/attributeslib/mixin/PlayerMixin";
    private static final String SWEEP = "com/stardew/craft/mixin/PlayerSweepAttackMixin";

    public static final class Service extends OfflineMixinApplicationCheck.Service {}

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        String order = args[0];
        Path output = Path.of(args[1]);
        boolean expectConflict = args.length > 2 && args[2].equals("expect-conflict");
        // Published third-party bytecode uses SRG members. Use the generated Forge mapping to
        // read its real handlers against the same mapped Minecraft bytecode as fresh production.
        Field names = OfflineMixinApplicationCheck.class.getDeclaredField("runtimeReadable");
        names.setAccessible(true);
        var readable = (Map<String, String>) names.get(null);
        for (String line : Files.readAllLines(Path.of("build/createSrgToMcp/output.srg"))) {
            String[] parts = line.split(" ");
            if (parts[0].equals("MD:") || parts[0].equals("FD:")) {
                String source = parts[1].substring(parts[1].lastIndexOf('/') + 1);
                String mapped = parts[0].equals("MD:") ? parts[3] : parts[2];
                mapped = mapped.substring(mapped.lastIndexOf('/') + 1);
                String previous = readable.putIfAbsent(source, mapped);
                require(previous == null || previous.equals(mapped), "Ambiguous SRG mapping " + source);
            }
        }
        writeConfig(output, "sweep-own.mixins.json", "com.stardew.craft.mixin", "PlayerSweepAttackMixin");
        writeConfig(output, "sweep-apothic.mixins.json", "dev.shadowsoffire.attributeslib.mixin", "PlayerMixin");
        MixinBootstrap.init();
        if (order.equals("apoth-first")) Mixins.addConfiguration("sweep-apothic.mixins.json");
        Mixins.addConfiguration("sweep-own.mixins.json");
        if (order.equals("stardew-first")) Mixins.addConfiguration("sweep-apothic.mixins.json");
        Field factory = OfflineMixinApplicationCheck.class.getDeclaredField("factory");
        factory.setAccessible(true);
        var transformer = ((IMixinTransformerFactory) factory.get(null)).createTransformer();
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        Method phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true);
        phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        Service provider = new Service();
        ClassNode input = provider.getClassNode(PLAYER);
        ClassWriter writer = new ClassWriter(0);
        input.accept(writer);
        byte[] woven;
        try {
            woven = transformer.transformClassBytes(PLAYER.replace('/', '.'), PLAYER.replace('/', '.'), writer.toByteArray());
        } catch (Throwable failure) {
            boolean collision = false;
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                String message = String.valueOf(cause.getMessage());
                collision |= cause.getClass().getName().endsWith("InjectionError")
                        && message.contains("Redirector") && message.contains("(0/1)")
                        && (message.contains("suppressNativeSweepHurt") || message.contains("apoth_handleKilledByAuxDmg"));
            }
            if (!expectConflict || !collision) throw failure;
            System.out.println("PASS negative control: reproduced exact sweep Redirect collision, " + order);
            return;
        }
        require(!expectConflict, "Old Redirect conflict was not reproduced");
        ClassNode result = new ClassNode();
        new ClassReader(woven).accept(result, 0);
        var reached = reachable(result, "attack(Lnet/minecraft/world/entity/Entity;)V");
        require(reached.stream().anyMatch(name -> name.contains("stardewcraft$suppressNativeSweepHurt")), "Sweep wrapper not on actual attack path");
        require(reached.stream().anyMatch(name -> name.contains("stardewcraft$authorizedPrimaryHurt")), "Primary damage authority lost");
        require(reached.stream().anyMatch(name -> name.contains("stardewcraft$singleKnockbackOwner")), "Knockback ownership lost");
        if (!order.equals("none")) {
            require(reached.stream().anyMatch(name -> name.contains("apoth_handleKilledByAuxDmg")), "Apothic redirect was bypassed");
        }
        MethodNode own = provider.getClassNode(SWEEP).methods.stream()
                .filter(method -> method.name.equals("stardewcraft$suppressNativeSweepHurt")).findFirst().orElseThrow();
        MethodNode apothic = provider.getClassNode(APOTHIC).methods.stream()
                .filter(method -> method.name.equals("apoth_handleKilledByAuxDmg")).findFirst().orElseThrow();
        exercise(own, apothic, !order.equals("none"));
        System.out.println("PASS sweep compatibility: " + order
                + ", real Player weave, primary/knockback retained, vanilla/auxiliary/Stardew handler execution");
    }

    private static void writeConfig(Path output, String file, String pkg, String mixin) throws Exception {
        var config = new JsonObject();
        config.addProperty("required", true);
        config.addProperty("minVersion", "0.8");
        config.addProperty("compatibilityLevel", "JAVA_17");
        config.addProperty("package", pkg);
        var mixins = new JsonArray();
        mixins.add(mixin);
        config.add("mixins", mixins);
        var injectors = new JsonObject();
        injectors.addProperty("defaultRequire", 1);
        config.add("injectors", injectors);
        Files.writeString(output.resolve(file), config.toString());
    }

    private static HashSet<String> reachable(ClassNode node, String start) {
        var methods = new HashMap<String, MethodNode>();
        for (var method : node.methods) methods.put(method.name + method.desc, method);
        var seen = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String key = queue.remove();
            if (!seen.add(key) || !methods.containsKey(key)) continue;
            for (var instruction : methods.get(key).instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(node.name)) queue.add(call.name + call.desc);
                if (instruction instanceof InvokeDynamicInsnNode dynamic) for (Object argument : dynamic.bsmArgs) {
                    if (argument instanceof Handle handle && handle.getOwner().equals(node.name)) queue.add(handle.getName() + handle.getDesc());
                }
            }
        }
        return seen;
    }

    // Only the receiver/data objects are CPU fixtures. Both executable handler bodies come from
    // fresh production and the pinned official jar; the real weaving above proves their call chain.
    public static class Entity {}
    public static class Damage {}
    public static class Weapon { boolean stardew; }
    public static class Data {
        boolean auxiliary;
        public boolean getBoolean(String key) { require(key.equals("apoth.killed_by_aux_dmg"), "Wrong auxiliary flag"); return auxiliary; }
    }
    public static class Living extends Entity {
        public int hits;
        public boolean result;
        public float amount;
        public final Data data = new Data();
        public boolean hurt(Damage source, float damage) { hits++; amount = damage; return result; }
        public Data getPersistentData() { return data; }
    }
    public static class Player extends Living {
        final Weapon weapon = new Weapon();
        public Weapon getMainHandItem() { return weapon; }
    }
    public static class Identity {
        public static boolean isWeapon(Weapon weapon) { return weapon.stardew; }
    }
    private static final class Loader extends ClassLoader {
        Loader() { super(ApothicSweepMixinCheck.class.getClassLoader()); }
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
    private static void exercise(MethodNode own, MethodNode apothic, boolean useApothic) throws Exception {
        String name = "ApothicSweepRuntimeFixture";
        Map<String, String> types = Map.of(
                PLAYER, Player.class.getName().replace('.', '/'),
                "net/minecraft/world/entity/Entity", Entity.class.getName().replace('.', '/'),
                "net/minecraft/world/entity/LivingEntity", Living.class.getName().replace('.', '/'),
                "net/minecraft/world/damagesource/DamageSource", Damage.class.getName().replace('.', '/'),
                "net/minecraft/world/item/ItemStack", Weapon.class.getName().replace('.', '/'),
                "net/minecraft/nbt/CompoundTag", Data.class.getName().replace('.', '/'),
                "com/stardew/craft/combat/WeaponCombatIdentity", Identity.class.getName().replace('.', '/'));
        Remapper remap = new Remapper() {
            @Override public String map(String internal) { return types.getOrDefault(internal, internal); }
        };
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String parent = Player.class.getName().replace('.', '/');
        writer.visit(V17, ACC_PUBLIC, name, null, parent, null);
        var constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false);
        constructor.visitInsn(RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        for (var entry : Map.of("sweep", own, "apothic", apothic).entrySet()) {
            MethodNode method = entry.getValue();
            var copied = new MethodNode(ASM9, ACC_PUBLIC, entry.getKey(), remap.mapMethodDesc(method.desc), null, null);
            method.accept(new MethodRemapper(copied, remap));
            copied.visibleAnnotations = null; copied.invisibleAnnotations = null;
            copied.accept(writer);
        }
        writer.visitEnd();
        Class<?> fixture = new Loader().define(writer.toByteArray());
        Player player = (Player) fixture.getConstructor().newInstance();
        Method sweep = fixture.getMethod("sweep", Living.class, Damage.class, float.class, Operation.class);
        Method apoth = fixture.getMethod("apothic", Living.class, Damage.class, float.class);
        for (boolean stardew : List.of(false, true)) for (boolean hurt : List.of(false, true)) for (boolean auxiliary : List.of(false, true)) {
            player.weapon.stardew = stardew;
            Living target = new Living(); target.result = hurt; target.data.auxiliary = auxiliary;
            int[] forwards = {0};
            Operation<Boolean> original = arguments -> {
                forwards[0]++;
                try {
                    return useApothic ? (Boolean) apoth.invoke(player, arguments)
                            : ((Living) arguments[0]).hurt((Damage) arguments[1], (Float) arguments[2]);
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            };
            boolean value = (Boolean) sweep.invoke(player, target, new Damage(), 7F, original);
            require(value == (!stardew && (hurt || useApothic && auxiliary)), "Sweep/auxiliary result changed");
            require(target.hits == (stardew ? 0 : 1) && forwards[0] == (stardew ? 0 : 1), "Original hurt chain not preserved exactly once");
            require(stardew || target.amount == 7F, "Vanilla damage changed");
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
