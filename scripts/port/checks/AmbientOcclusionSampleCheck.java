import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Standalone bytecode regression check; no Minecraft client, registry bootstrap or local asset inputs. */
public final class AmbientOcclusionSampleCheck {
    private static final String CALCULATE = "(Lnet/minecraft/world/level/BlockAndTintGetter;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/core/Direction;[FLjava/util/BitSet;Z)V";
    private static final String OWNER = "net/minecraft/core/BlockPos$MutableBlockPos";
    private static final String MOVE = "(Lnet/minecraft/core/Direction;)L" + OWNER + ";";
    private static final String TARGET = "L" + OWNER + ";move" + MOVE;

    public static void main(String[] args) throws Exception {
        ClassNode target = read("net/minecraft/client/renderer/block/ModelBlockRenderer$AmbientOcclusionFace");
        MethodNode calculate = target.methods.stream()
                .filter(m -> m.name.equals("calculate") && m.desc.equals(CALCULATE)).findFirst().orElseThrow();
        List<MethodInsnNode> moves = new ArrayList<>();
        for (AbstractInsnNode instruction : calculate.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(OWNER)
                    && call.name.equals("move") && call.desc.equals(MOVE)) moves.add(call);
        }
        require(moves.size() == 8, "Expected four side and four diagonal move calls, found " + moves.size());
        for (int i = 0; i < moves.size(); i++) {
            AbstractInsnNode argument = previous(moves.get(i));
            boolean faceNormal = argument instanceof VarInsnNode load
                    && load.getOpcode() == Opcodes.ALOAD && load.var == 4;
            require(faceNormal == (i < 4), "move ordinal " + i + " no longer has the expected argument");
            if (i >= 4) require(argument.getOpcode() == Opcodes.AALOAD,
                    "Diagonal ordinal " + i + " must keep its corner direction");
        }

        ClassNode mixin = read("com/stardew/craft/mixin/PortAmbientOcclusionSampleMixin");
        Set<Integer> patched = new HashSet<>();
        for (MethodNode method : mixin.methods) {
            if (method.visibleAnnotations == null) continue;
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")) continue;
                require(value(annotation, "method").equals(List.of("calculate" + CALCULATE)), "Wrong AO target selector");
                require(value(annotation, "require").equals(1) && value(annotation, "expect").equals(1)
                        && value(annotation, "allow").equals(1), "Every side probe must match exactly once");
                AnnotationNode at = (AnnotationNode) value(annotation, "at");
                require(value(at, "value").equals("INVOKE") && value(at, "target").equals(TARGET), "Wrong move target");
                int ordinal = (Integer) value(at, "ordinal");
                require(ordinal >= 0 && ordinal < 4 && patched.add(ordinal), "Duplicate/diagonal redirect " + ordinal);
                require((method.access & Opcodes.ACC_STATIC) == (calculate.access & Opcodes.ACC_STATIC),
                        "Probe handler static modifier must match the instance AO calculation");
                List<AbstractInsnNode> code = new ArrayList<>();
                for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) code.add(instruction);
                require(code.size() == 2 && code.get(0) instanceof VarInsnNode load
                        && load.getOpcode() == Opcodes.ALOAD && load.var == 1
                        && code.get(1).getOpcode() == Opcodes.ARETURN, "Probe handler must only return the same position");
            }
        }
        require(patched.equals(Set.of(0, 1, 2, 3)), "All four side probes must be patched; diagonals must remain untouched");
        checkProbeFixtures();
        checkSpatialForwarding();
        System.out.println("AO sampling parity: four normal oversamples removed; four diagonal moves preserved.");
    }

    private static void checkSpatialForwarding() throws Exception {
        String spatialName = "com/stardew/craft/client/render/SpatialBlockModelRenderer";
        ClassNode spatial = read(spatialName);
        int checks = 0;
        for (MethodNode method : spatial.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (method.name.equals("partition") && call.name.equals("getQuads")) {
                    // Static partition overload: ModelData local 5, RenderType local 6.
                    require(loads(previous(call), 6) && loads(previous(previous(call)), 5), "Partition dropped render layer/data");
                    checks++;
                } else if (method.name.equals("renderCells") && call.name.equals("tesselateBlock")) {
                    require(loads(previous(call), 13) && loads(previous(previous(call)), 12), "Cell render dropped render layer/data");
                    checks++;
                }
            }
        }
        require(checks == 2, "Missing production data/layer forwarding checks");
        ClassNode bridge = read("com/stardew/craft/mixin/PortMapDecorSpatialLightingMixin");
        int guard = 0, forwarded = 0;
        for (MethodNode method : bridge.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                    && type.desc.equals("com/stardew/craft/block/decor/MapDecorStaticBlock")) guard++;
            if (instruction instanceof MethodInsnNode call && call.owner.equals(spatialName) && call.name.equals("renderMapDecor")) {
                require(loads(previous(call), 13) && loads(previous(previous(call)), 12), "Mixin dropped render layer/data");
                forwarded++;
            }
        }
        require(guard == 1 && forwarded == 1, "Spatial correction must be guarded by MapDecorStaticBlock");
    }

    private static boolean loads(AbstractInsnNode instruction, int variable) {
        return instruction instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == variable;
    }

    /** Apply the same-position handler contract verified above to all six face orientations. */
    private static void checkProbeFixtures() {
        int[][] directions = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
        int checks = 0;
        for (int[] normal : directions) for (int[] side : directions) {
            if (normal[0] * side[0] + normal[1] * side[1] + normal[2] * side[2] != 0) continue;
            String adjacent = key(side[0], side[1], side[2]);
            String oldProbe = key(side[0] + normal[0], side[1] + normal[1], side[2] + normal[2]);
            // Open air: both versions already take the same branch. This patch must not promise
            // to fix oversized-quad interpolation or every visible shadow in an open scene.
            require(!Set.of().contains(adjacent) && !Set.of().contains(oldProbe), "Open fixture changed");
            // Wall on the adjacent side: NeoForge sees it; Forge's extra normal step misses it.
            Set<String> sideWall = Set.of(adjacent);
            require(sideWall.contains(adjacent) && !sideWall.contains(oldProbe), "Side-wall oversample fixture failed");
            // Wall only one cell farther forward: Forge incorrectly suppresses the diagonal.
            Set<String> frontWall = Set.of(oldProbe);
            require(!frontWall.contains(adjacent) && frontWall.contains(oldProbe), "Forward-wall oversample fixture failed");
            checks++;
        }
        require(checks == 24, "Expected four adjacent probes for each of six faces");
        System.out.println("Probe fixtures: 24 orientations, open air unchanged; adjacent/forward wall sampling matches NeoForge.");
    }

    private static String key(int x, int y, int z) { return x + "," + y + "," + z; }

    private static ClassNode read(String name) throws Exception {
        try (InputStream in = AmbientOcclusionSampleCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) throw new IllegalStateException("Missing compiled class " + name);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        }
        throw new IllegalStateException("Missing annotation value " + name);
    }

    private static AbstractInsnNode previous(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        if (node == null) throw new IllegalStateException("Missing move argument");
        return node;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
