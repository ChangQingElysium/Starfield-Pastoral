import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;

/** Applies the real stack-count Mixin to official-name and SRG GuiGraphics, without launching a client. */
public final class GuiStackCountMixinCheck {
    private static final String MIXIN = "com/stardew/craft/mixin/GuiGraphicsStackCountScaleMixin";
    private static final String GUI = "net/minecraft/client/gui/GuiGraphics";
    private static final Map<String, String> METHODS = new HashMap<>();
    private static final Map<String, String> FIELDS = new HashMap<>();
    private static boolean srg;

    public static final class Service extends OfflineMixinApplicationCheck.Service {
        @Override public ClassNode getClassNode(String name, boolean runTransformers)
                throws ClassNotFoundException, java.io.IOException {
            ClassNode node = super.getClassNode(name, runTransformers);
            if (!srg || !node.name.equals(MIXIN)) return node;
            // Model ForgeGradle's reobfuscation of normal Java references, keeping the actual
            // generated refmap responsible for injection selectors. No Minecraft bytecode is changed.
            ClassWriter writer = new ClassWriter(0);
            node.accept(new ClassRemapper(writer, new Remapper() {
                @Override public String mapMethodName(String owner, String name, String descriptor) {
                    return METHODS.getOrDefault(owner + "/" + name + descriptor, name);
                }
                @Override public String mapFieldName(String owner, String name, String descriptor) {
                    return FIELDS.getOrDefault(owner + "/" + name, name);
                }
            }));
            ClassNode remapped = new ClassNode();
            new ClassReader(writer.toByteArray()).accept(remapped, ClassReader.EXPAND_FRAMES);
            return remapped;
        }
    }

    public static void main(String[] args) throws Throwable {
        srg = args[0].equals("srg");
        Path output = Path.of(args[1]);
        if (srg) {
            for (String line : Files.readAllLines(Path.of("build/createSrgToMcp/output.srg"))) {
                String[] parts = line.split(" ");
                if (parts[0].equals("MD:")) {
                    METHODS.put(parts[3] + parts[4], parts[1].substring(parts[1].lastIndexOf('/') + 1));
                } else if (parts[0].equals("FD:")) {
                    FIELDS.put(parts[2], parts[1].substring(parts[1].lastIndexOf('/') + 1));
                }
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
        client.add("GuiGraphicsStackCountScaleMixin");
        config.add("client", client);
        String configName = "gui-stack-count-check.mixins.json";
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
        ClassNode target = provider.getClassNode(GUI);
        ClassWriter input = new ClassWriter(0);
        target.accept(input);
        byte[] transformed = transformer.transformClassBytes(GUI.replace('/', '.'), GUI.replace('/', '.'), input.toByteArray());
        ClassNode result = new ClassNode();
        new ClassReader(transformed).accept(result, 0);
        int handlers = 0;
        boolean poseAccessor = false;
        for (var method : result.methods) {
            if (!method.name.contains("stardewcraft$scaleBigStackCount")) continue;
            handlers++;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && relevant(call.owner)) {
                    var owner = call.owner.equals(GUI) ? result : provider.getClassNode(call.owner);
                    require(owner.methods.stream().anyMatch(m -> m.name.equals(call.name) && m.desc.equals(call.desc)),
                            "Unresolved method after Mixin application: " + call.owner + "/" + call.name + call.desc);
                    if (call.owner.equals(GUI) && call.name.equals(srg ? "m_280168_" : "pose")) poseAccessor = true;
                } else if (instruction instanceof FieldInsnNode access && relevant(access.owner)) {
                    var owner = access.owner.equals(GUI) ? result : provider.getClassNode(access.owner);
                    require(owner.fields.stream().anyMatch(f -> f.name.equals(access.name) && f.desc.equals(access.desc)),
                            "Unresolved field after Mixin application: " + access.owner + "/" + access.name);
                }
            }
            require(!method.tryCatchBlocks.isEmpty(), "Scaled drawing must restore the pose on failure");
        }
        require(handlers == 1 && poseAccessor, "Stack-count redirect did not merge with a valid pose accessor");
        System.out.println("PASS stack-count Mixin: " + args[0] + " target, real injection and member references resolve");
    }

    private static boolean relevant(String owner) {
        return owner.equals(GUI) || owner.equals("net/minecraft/client/gui/Font")
                || owner.equals("com/mojang/blaze3d/vertex/PoseStack");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
