import com.stardew.craft.client.interior.EmbeddiumRenderLists;
import com.stardew.craft.client.interior.TownDoorIrisPipeline;
import com.stardew.craft.client.interior.TownDoorShaderPatcher;
import com.stardew.craft.client.light.EmbeddiumLightBridge;
import com.stardew.craft.client.render.AnvilBlockEntityRenderer;
import com.stardew.craft.port.PortBlockEntityRenderBounds;
import com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Release-bytecode target audit plus real snapshot/GLSL helpers. No client, registry or GPU. */
public final class OptionalIntegrationCheck {
    private static final String EMB = "me/jellysquid/mods/sodium/client/";
    private static final String IRIS = "net/irisshaders/iris/";
    private static final String[] MIXINS = {
        "SodiumRingLightQuadAccessor", "SodiumRingLightDataAccessor", "SodiumRingLightPipelineMixin",
        "SodiumColoredLightPositionAccessor", "SodiumColoredLightQuadAccessor", "SodiumColoredLightMixin", "SodiumBlockEntityBoundsMixin",
        "SodiumImmediateQuadNormalAccessor", "SodiumImmediateNormalsMixin", "SodiumEntityNormalsMixin",
        "TownDoorSodiumOcclusionCullerMixin", "TownDoorSodiumViewportMixin", "TownDoorSodiumShaderLoaderMixin",
        "TownDoorSodiumShaderMixin", "TownDoorIrisTransformMixin", "TownDoorIrisSodiumShaderMixin",
        "TownDoorIrisPipelineManagerMixin", "TownDoorOculusProgramOverridesMixin",
        "IrisMineLampActivePathMixin", "IrisMineLampPropertiesMixin", "IrisMineLampIncludeMixin",
        "CtmModelInitializationMixin", "Ae2FacadeItemMixin", "XaeroMinimapWriterMixin",
        "XaeroWorldMapWriterMixin", "XaeroWorldMapSettingsMixin"
    };

    public static void main(String[] args) throws Exception {
        for (String name : MIXINS) checkMixin(name);
        checkRendererVersionContract();
        checkRendererFields();
        checkColorPacking();
        checkAoDomain();
        checkBlockEntityBounds();
        checkListSnapshots();
        checkVanillaPipelineSettings();
        checkShaderSources();
        System.out.println("Optional integrations: " + MIXINS.length + " mixin classes' targets, selectors, calls and handler signatures match locked Forge jars.");
        System.out.println("Embeddium ABGR/light merge, bounded AO coordinates, renderer-owned BE bounds, independent visibility snapshots,");
        System.out.println("Oculus settings restoration and compatibility/core terrain GLSL passed.");
        System.out.println("Boundary: not a full Mixin transformer/GL/client run; unknown renderer versions and optional FRAPI emitters are not certified.");
    }

    private static void checkMixin(String simpleName) throws Exception {
        ClassNode mixin = node("com/stardew/craft/mixin/" + simpleName);
        AnnotationNode binding = annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        require(binding != null, "Missing Mixin annotation: " + simpleName);
        for (Object targetName : (List<?>) value(binding, "targets")) {
            ClassNode target = node(targetName.toString().replace('.', '/'));
            for (MethodNode handler : mixin.methods) {
                for (AnnotationNode entry : annotations(handler)) {
                    if (entry.desc.endsWith("/Shadow;")) {
                        require(findMethod(target, handler.name, handler.desc) != null, "Missing interface shadow " + target.name + '.' + handler.name + handler.desc);
                    } else if (entry.desc.endsWith("/Invoker;")) {
                        String method = (String) value(entry, "value");
                        require(findMethod(target, method, handler.desc) != null, "Missing invoker " + target.name + '.' + method + handler.desc);
                    } else if (entry.desc.endsWith("/Accessor;")) {
                        String field = (String) value(entry, "value");
                        require(target.fields.stream().anyMatch(f -> f.name.equals(field)
                                && f.desc.equals(Type.getReturnType(handler.desc).getDescriptor())), "Missing accessor " + target.name + '.' + field);
                    } else if (value(entry, "method") instanceof List<?> selectors) {
                        for (Object selectorValue : selectors) {
                            String selector = selectorValue.toString();
                            List<MethodNode> selected = new ArrayList<>();
                            for (MethodNode method : target.methods) if (selector.equals(method.name) || selector.equals(method.name + method.desc)) selected.add(method);
                            require(!selected.isEmpty(), "Missing selector " + target.name + '.' + selector);
                            Object at = value(entry, "at");
                            List<?> points = at instanceof List<?> list ? list : at == null ? List.of() : List.of(at);
                            for (Object point : points) checkAt(target, selected, (AnnotationNode) point);
                            if (entry.desc.endsWith("/Inject;")) for (MethodNode method : selected) checkHandler(target, handler, method);
                        }
                    }
                }
            }
        }
    }

    private static void checkHandler(ClassNode target, MethodNode handler, MethodNode method) {
        Type[] parameters = Type.getArgumentTypes(handler.desc), actual = Type.getArgumentTypes(method.desc);
        int count = parameters.length - 1;
        require(count <= actual.length, "Too many handler arguments: " + target.name + '.' + handler.name);
        for (int i = 0; i < count; i++) require(parameters[i].equals(actual[i])
                || parameters[i].equals(Type.getType(Object.class)) && actual[i].getSort() == Type.OBJECT,
                "Handler argument mismatch: " + target.name + '.' + handler.name + " parameter " + i);
    }

    private static void checkAt(ClassNode target, List<MethodNode> selected, AnnotationNode at) {
        if (!"INVOKE".equals(value(at, "value"))) return;
        String reference = (String) value(at, "target");
        int semicolon = reference.indexOf(';'), bracket = reference.indexOf('(');
        String owner = reference.substring(1, semicolon), name = reference.substring(semicolon + 1, bracket), descriptor = reference.substring(bracket);
        int count = 0;
        for (MethodNode method : selected) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.desc.equals(descriptor)
                    && (call.name.equals(name) || name.equals("getBlockState") && call.name.equals("m_8055_"))) count++;
        }
        int ordinal = value(at, "ordinal") instanceof Integer number ? number : -1;
        require(count > ordinal && count > 0, "Missing invocation: " + target.name + " -> " + reference);
    }

    private static void checkRendererFields() throws Exception {
        ClassNode block = node(EMB + "render/chunk/compile/pipeline/BlockRenderer");
        MethodNode geometry = block.methods.stream().filter(m -> m.name.equals("writeGeometry")).findFirst().orElseThrow();
        require(geometry.localVariables.stream().anyMatch(v -> v.index == 12 && v.name.equals("srcIndex") && v.desc.equals("I")),
                "Colored-light source vertex slot changed");
        MethodNode minimap = node("xaero/common/minimap/write/MinimapWriter").methods.stream().filter(m -> m.name.equals("findBlock")).findFirst().orElseThrow();
        Type[] args = Type.getArgumentTypes(minimap.desc);
        List<Integer> mutable = new ArrayList<>();
        for (int i = 0; i < args.length; i++) if (args[i].getDescriptor().equals("Lnet/minecraft/core/BlockPos$MutableBlockPos;")) mutable.add(i);
        require(mutable.equals(List.of(8, 9, 19)) && args[0].getDescriptor().equals("Lnet/minecraft/world/level/Level;"), "Xaero argument layout changed");
        require(findMethod(node(EMB + "world/WorldRendererExtended"), "sodium$getWorldRenderer", "()L" + EMB + "render/SodiumWorldRenderer;") != null, "Missing world renderer extension");
        require(findMethod(node(EMB + "render/SodiumWorldRenderer"), "scheduleTerrainUpdate", "()V") != null, "Missing terrain dirty hook");
        field(node(EMB + "render/SodiumWorldRenderer"), "renderSectionManager", "L" + EMB + "render/chunk/RenderSectionManager;");
        field(node(EMB + "render/chunk/RenderSectionManager"), "renderLists", "L" + EMB + "render/chunk/lists/SortedRenderLists;");
        field(node(IRIS + "pipeline/PipelineManager"), "pipeline", "L" + IRIS + "pipeline/WorldRenderingPipeline;");
    }

    private static void checkRendererVersionContract() throws Exception {
        ClassNode plugin = node("com/stardew/craft/mixin/StardewCraftMixinPlugin");
        for (String[] expected : new String[][]{{"hasAuditedEmbeddium", "embeddium", "0.3.31+mc1.20.1", EMB + "render/SodiumWorldRenderer"},
                {"hasAuditedOculus", "oculus", "1.8.0", IRIS + "pipeline/PipelineManager"}}) {
            MethodNode gate = findMethod(plugin, expected[0], "()Z");
            List<String> constants = new ArrayList<>();
            int exactChecks = 0;
            for (AbstractInsnNode instruction : gate.instructions) {
                if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String text) constants.add(text);
                if (instruction instanceof MethodInsnNode call && call.name.equals("hasExactModVersion")) exactChecks++;
            }
            require(constants.equals(List.of(expected[1], expected[2])) && exactChecks == 1, "Renderer gate does not use the locked mod id/version");
            var resource = OptionalIntegrationCheck.class.getClassLoader().getResource(expected[3] + ".class");
            var connection = (java.net.JarURLConnection) resource.openConnection();
            String metadata;
            try (var stream = connection.getJarFile().getInputStream(connection.getJarFile().getJarEntry("META-INF/mods.toml"))) {
                metadata = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replaceAll("[ \\t]", "");
            }
            require(metadata.contains("modId=\"" + expected[1] + "\"\nversion=\"" + expected[2] + "\""), "Gate does not match the actual release metadata");
        }
        MethodNode exact = plugin.methods.stream().filter(m -> m.name.startsWith("lambda$hasExactModVersion$")).findFirst().orElseThrow();
        int equality = 0;
        for (AbstractInsnNode instruction : exact.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/String")) {
            require(!call.name.equals("startsWith"), "Unknown renderer variants must not pass a prefix match");
            if (call.name.equals("equals")) equality++;
        }
        require(equality == 2, "Renderer id and version must both use exact equality");
        ClassNode core = node(IRIS + "pipeline/transform/transformer/SodiumCoreTransformer");
        List<String> expressions = new ArrayList<>();
        for (MethodNode method : core.methods) for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String text) expressions.add(text);
        require(expressions.contains("_vert_position + _get_draw_translation(_draw_id)")
                && expressions.contains("iris_ModelViewMatrix") && expressions.contains("u_RegionOffset"), "Audited Oculus core coordinate generator changed");
    }

    private static void checkColorPacking() throws Exception {
        require(EmbeddiumLightBridge.multiplyAbgr(0x7f806040, 0xff0000) == 0x7f000040, "Red tint changed blue or alpha");
        require(EmbeddiumLightBridge.multiplyAbgr(0x7f806040, 0x0000ff) == 0x7f800000, "Blue tint changed red or alpha");
        var merge = Class.forName(EMB.replace('/', '.') + "util.ModelQuadUtil").getMethod("mergeBakedLight", int.class, int.class);
        for (int baked : new int[]{0, 0x00f00030, 0x002000f0}) for (int calculated : new int[]{0, 0x00b00070, 0x00f000f0}) {
            require(EmbeddiumLightBridge.mergeLight(baked, calculated) == (int) merge.invoke(null, baked, calculated), "Baked/sky light merge changed");
        }
    }

    private static void checkAoDomain() throws Exception {
        String name = EMB + "model/light/smooth/SmoothLightPipeline";
        ClassNode pipeline = node(name);
        for (String method : List.of("applyAlignedPartialFace", "applyParallelFace", "applyNonParallelFace")) {
            MethodNode selected = pipeline.methods.stream().filter(m -> m.name.equals(method)).findFirst().orElseThrow();
            int clamps = 0;
            for (AbstractInsnNode instruction : selected.instructions) if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(name) && call.name.equals("clamp") && call.desc.equals("(F)F")) clamps++;
            require(clamps == 3, "Every AO X/Y/Z coordinate must be clamped before interpolation: " + method);
        }
        var clamp = Class.forName(name.replace('/', '.')).getDeclaredMethod("clamp", float.class);
        clamp.setAccessible(true);
        for (float coordinate : new float[]{-0.8125f, 0, 0.4f, 1, 1.8125f}) {
            float result = (float) clamp.invoke(null, coordinate);
            require(result == Math.max(0, Math.min(1, coordinate)), "Embeddium extrapolated an oversized vertex");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void checkBlockEntityBounds() throws Exception {
        ClassNode mixin = node("com/stardew/craft/mixin/SodiumBlockEntityBoundsMixin");
        MethodNode handler = mixin.methods.stream().filter(m -> m.name.equals("stardewcraft$rendererBounds")).findFirst().orElseThrow();
        AnnotationNode operation = annotations(handler).stream().filter(a -> a.desc.endsWith("/WrapOperation;")).findFirst().orElseThrow();
        require(value(operation, "require").equals(2) && value(operation, "expect").equals(2)
                && value(operation, "allow").equals(2), "Both local and global BE cull paths must be wrapped exactly once");
        ClassNode renderer = node(EMB + "render/SodiumWorldRenderer");
        for (String selector : (List<String>) value(operation, "method")) {
            int bracket = selector.indexOf('(');
            String name = bracket < 0 ? selector : selector.substring(0, bracket);
            MethodNode selected = renderer.methods.stream().filter(m -> m.name.equals(name)
                    && (bracket < 0 || (m.name + m.desc).equals(selector))).findFirst().orElseThrow();
            long count = java.util.stream.StreamSupport.stream(selected.instructions.spliterator(), false)
                    .filter(i -> i instanceof MethodInsnNode call && call.name.equals("getRenderBoundingBox")).count();
            require(count == 1, "BE bounding-box call count changed: " + selector);
        }
        // Only construction is bypassed: Forge's capability constructor requires a launched mod loader.
        // The actual bounds helper, dispatcher lookup, renderer extension and fallback are all executed.
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Object unsafe = reflectiveField(unsafeType, "theUnsafe").get(null);
        ProbeBlockEntity blockEntity = (ProbeBlockEntity) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, ProbeBlockEntity.class);
        ProbeDispatcher dispatcher = new ProbeDispatcher();
        dispatcher.renderer = new ExtendedBoundsRenderer();
        require(PortBlockEntityRenderBounds.bounds(dispatcher, blockEntity).equals(RENDERER_BOUNDS), "Large renderer bounds were replaced by the anchor cell");
        dispatcher.renderer = (BlockEntityRenderer) new AnvilBlockEntityRenderer(null);
        require(PortBlockEntityRenderBounds.bounds(dispatcher, blockEntity).equals(new AABB(BOUNDS_POS)), "Mod renderer default must match NeoForge unit-cell bounds");
        dispatcher.renderer = new BlockEntityRenderer<BlockEntity>() {
            @Override public void render(BlockEntity be, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {}
        };
        require(PortBlockEntityRenderBounds.bounds(dispatcher, blockEntity).equals(FORGE_BOUNDS), "Unrelated renderer lost its Forge bounds");
        dispatcher.renderer = null;
        require(PortBlockEntityRenderBounds.bounds(dispatcher, blockEntity).equals(FORGE_BOUNDS), "Unregistered renderer fallback changed");
    }

    private static final BlockPos BOUNDS_POS = new BlockPos(10, 20, 30);
    private static final AABB FORGE_BOUNDS = new AABB(9, 19, 29, 12, 23, 32);
    private static final AABB RENDERER_BOUNDS = new AABB(7, 20, 27, 14, 26, 34);
    private static final class ProbeBlockEntity extends BlockEntity {
        private ProbeBlockEntity() { super(null, null, null); }
        @Override public BlockPos getBlockPos() { return BOUNDS_POS; }
        @Override public AABB getRenderBoundingBox() { return FORGE_BOUNDS; }
    }
    private static final class ProbeDispatcher extends BlockEntityRenderDispatcher {
        private BlockEntityRenderer<BlockEntity> renderer;
        private ProbeDispatcher() { super(null, null, null, null, null); }
        @Override @SuppressWarnings("unchecked") public <E extends BlockEntity> BlockEntityRenderer<E> getRenderer(E be) { return (BlockEntityRenderer<E>) renderer; }
    }
    private static final class ExtendedBoundsRenderer implements BlockEntityRenderer<BlockEntity>, IBlockEntityRendererExtension<BlockEntity> {
        @Override public AABB getRenderBoundingBox(BlockEntity be) { return RENDERER_BOUNDS; }
        @Override public void render(BlockEntity be, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {}
    }

    private static void checkListSnapshots() throws Exception {
        ClassLoader loader = OptionalIntegrationCheck.class.getClassLoader();
        Class<?> region = Class.forName(EMB.replace('/', '.') + "render.chunk.region.RenderRegion");
        Class<?> staging = Class.forName(EMB.replace('/', '.') + "gl.arena.staging.StagingBuffer");
        Object regionObject = region.getConstructor(int.class, int.class, int.class, staging).newInstance(1, 2, 3, null);
        Object chunk = region.getMethod("getRenderList").invoke(regionObject);
        for (String name : List.of("sectionsWithGeometry", "sectionsWithSprites", "sectionsWithEntities")) {
            Arrays.fill((byte[]) reflectiveField(chunk.getClass(), name).get(chunk), (byte) 42);
        }
        for (String name : List.of("sectionsWithGeometryCount", "sectionsWithSpritesCount", "sectionsWithEntitiesCount", "size", "lastVisibleFrame")) reflectiveField(chunk.getClass(), name).setInt(chunk, 3);
        Class<?> lists = Class.forName(EMB.replace('/', '.') + "render.chunk.lists.SortedRenderLists");
        var constructor = lists.getDeclaredConstructor(ObjectArrayList.class); constructor.setAccessible(true);
        Object original = constructor.newInstance(ObjectArrayList.of(chunk));
        Object copy = new EmbeddiumRenderLists(loader).copy(original);
        Object copied = ((List<?>) reflectiveField(lists, "lists").get(copy)).get(0);
        require(copied != chunk && reflectiveField(chunk.getClass(), "region").get(copied) == regionObject, "Snapshot duplicated mesh/region or shared mutable list");
        for (String name : List.of("sectionsWithGeometry", "sectionsWithSprites", "sectionsWithEntities")) {
            byte[] before = (byte[]) reflectiveField(chunk.getClass(), name).get(chunk), after = (byte[]) reflectiveField(chunk.getClass(), name).get(copied);
            before[0] = 9; require(after[0] == 42 && before != after, "Nested view overwrote outer visibility array");
        }
        for (String name : List.of("sectionsWithGeometryCount", "sectionsWithSpritesCount", "sectionsWithEntitiesCount", "size", "lastVisibleFrame")) {
            reflectiveField(chunk.getClass(), name).setInt(chunk, 7);
            require(reflectiveField(chunk.getClass(), name).getInt(copied) == 3, "Nested view overwrote outer visibility counters");
        }
    }

    private static void checkVanillaPipelineSettings() throws Exception {
        Class<?> type = Class.forName(IRIS.replace('/', '.') + "shaderpack.materialmap.WorldRenderingSettings");
        Object settings = type.getField("INSTANCE").get(null);
        String[] names = {"disableDirectionalShading", "useSeparateAo", "separateEntityDraws", "ambientOcclusionLevel",
                "useExtendedVertexFormat", "voxelizeLightBlocks", "blockTypeIds", "reloadRequired"};
        Object[] values = {true, true, true, 0.35f, true, true, Map.of(), true};
        for (int i = 0; i < names.length; i++) reflectiveField(type, names[i]).set(settings, values[i]);
        require(TownDoorIrisPipeline.vanilla() != null, "No Oculus vanilla nested pipeline");
        for (int i = 0; i < names.length; i++) require(reflectiveField(type, names[i]).get(settings).equals(values[i]), "Vanilla constructor changed outer settings: " + names[i]);
        require(TownDoorIrisPipeline.vanilla() == TownDoorIrisPipeline.vanilla(), "Nested pipeline is not reused");
    }

    private static void checkShaderSources() throws Exception {
        // This check exercises the production GLSL transform, not mod-registration logging.
        // StardewCraft.LOGGER initializes the registry-owning entrypoint in a plain JVM.
        reflectiveField(TownDoorShaderPatcher.class, "loggedSodiumPatch").setBoolean(null, true);
        String path = "assets/sodium/shaders/blocks/block_layer_opaque.vsh";
        String source;
        try (var stream = OptionalIntegrationCheck.class.getClassLoader().getResourceAsStream(path)) { source = new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
        String patched = TownDoorShaderPatcher.transformSodiumVertex(source);
        require(patched.contains("gl_ClipDistance[0] = dot((u_ModelViewMatrix * vec4(position, 1.0)).xyz"), "Embeddium terrain source not clipped");
        require(TownDoorShaderPatcher.transformSodiumVertex(patched).equals(patched), "Terrain clipping patched twice");
        String irisSource = "#version 330\nuniform mat4 iris_ModelViewMatrix;\nvec4 getVertexPosition() { return vec4(0,0,0,1); }\nvoid main() { gl_Position = getVertexPosition(); }";
        String irisPatched = TownDoorShaderPatcher.transformIrisVertex(irisSource);
        require(irisPatched.contains("gl_ClipDistance[0] = dot((iris_ModelViewMatrix * getVertexPosition()).xyz"), "Oculus terrain source not clipped");
        require(TownDoorShaderPatcher.transformIrisVertex(irisPatched).equals(irisPatched), "Oculus compatibility source patched twice");
        String core = "#version 330\nuniform mat4 iris_ModelViewMatrix; uniform vec3 u_RegionOffset;\n"
                + "vec3 _vert_position; uint _draw_id; vec3 _get_draw_translation(uint i) { return vec3(0); }\n"
                + "void main() { /* } */ if (true) { _vert_position = vec3(0); } // }\n"
                + "gl_Position = iris_ModelViewMatrix * vec4(_vert_position + u_RegionOffset + _get_draw_translation(_draw_id), 1); }\n"
                + "void helper_after_main() { }\n";
        String corePatched = TownDoorShaderPatcher.transformIrisVertex(core);
        require(corePatched.contains("gl_ClipDistance[0] = dot((iris_ModelViewMatrix * vec4(_vert_position + u_RegionOffset + _get_draw_translation(_draw_id), 1.0)).xyz"), "Oculus core terrain source not clipped");
        require(corePatched.indexOf("gl_ClipDistance[0]") < corePatched.indexOf("void helper_after_main"), "Clipping escaped main into a later function");
        require(TownDoorShaderPatcher.transformIrisVertex(corePatched).equals(corePatched), "Oculus core source patched twice");
        require(TownDoorShaderPatcher.transformIrisVertex("uniform mat4 iris_ModelViewMatrix; void main() { }")
                .equals("uniform mat4 iris_ModelViewMatrix; void main() { }"), "Unknown vertex generators must stay unchanged");
    }

    private static ClassNode node(String name) throws Exception {
        var node = new ClassNode();
        try (var stream = OptionalIntegrationCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            require(stream != null, "Missing class: " + name); new ClassReader(stream).accept(node, 0);
        }
        return node;
    }
    private static MethodNode findMethod(ClassNode node, String name, String descriptor) throws Exception {
        for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        for (String parent : node.interfaces) { MethodNode found = findMethod(node(parent), name, descriptor); if (found != null) return found; }
        return null;
    }
    private static List<AnnotationNode> annotations(MethodNode method) {
        var result = new ArrayList<AnnotationNode>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result;
    }
    private static AnnotationNode annotation(List<AnnotationNode> values, String descriptor) {
        if (values != null) for (AnnotationNode annotation : values) if (annotation.desc.equals(descriptor)) return annotation;
        return null;
    }
    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        return null;
    }
    private static void field(ClassNode type, String name, String descriptor) { require(type.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor)), "Missing renderer field: " + type.name + '.' + name); }
    private static Field reflectiveField(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
