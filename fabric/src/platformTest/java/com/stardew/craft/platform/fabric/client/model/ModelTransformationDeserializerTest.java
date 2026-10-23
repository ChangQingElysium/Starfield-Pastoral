package com.stardew.craft.platform.fabric.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Transformation;
import io.github.fabricators_of_create.porting_lib.models.ExtraFaceData;
import io.github.fabricators_of_create.porting_lib.models.geometry.extensions.BlockElementExtension;
import io.github.fabricators_of_create.porting_lib.models.geometry.BlockGeometryBakingContext;
import io.github.fabricators_of_create.porting_lib.models.geometry.GeometryLoaderManager;
import io.github.fabricators_of_create.porting_lib.models.geometry.RegisterGeometryLoadersCallback;
import io.github.fabricators_of_create.porting_lib.models.geometry.extensions.BlockModelExtension;
import io.github.fabricators_of_create.porting_lib.models.geometry.mixin.client.BlockModelDeserializerMixin;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class ModelTransformationDeserializerTest {
    private static final Gson FIXED = ModelTransformationDeserializer.register(new GsonBuilder()).create();

    @BeforeAll static void setup() throws ReflectiveOperationException {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var imported = (io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryLoader<?>) Class
                .forName("com.stardew.craft.client.model.ImportedModelGeometry", true, new ImportedModelFixtureLoader())
                .getField("LOADER").get(null);
        RegisterGeometryLoadersCallback.EVENT.register(loaders -> {
            loaders.putIfAbsent(CompositeGeometry.LOADER_ID, CompositeGeometry.LOADER);
            loaders.putIfAbsent(ResourceLocation.fromNamespaceAndPath("stardewcraft", "geometry"), imported);
        });
        GeometryLoaderManager.init();
    }

    @Test void current46ModelFilesReproduceOldFailureThenParseThroughActualNativeAndPlDeserializers() throws Exception {
        List<Path> models = transformModels();
        assertEquals(46, models.size(), "Current tracked models with translation/matrix arrays");
        FixtureDeserializer oldDeserializer = new FixtureDeserializer();
        FixtureDeserializer fixedDeserializer = new FixtureDeserializer();
        Gson oldGson = modelGson(oldDeserializer, false);
        Gson fixedGson = modelGson(fixedDeserializer, true);
        int ginger = 0;
        int expectedModels = 0, expectedTransforms = 0;
        for (Path path : models) {
            String source = Files.readString(path);
            JsonObject authored = JsonParser.parseString(source).getAsJsonObject();
            expectedModels += countModelNodes(authored, false);
            expectedTransforms += countModelNodes(authored, true);
            JsonParseException previous = assertThrows(JsonParseException.class,
                    () -> oldGson.fromJson(source, BlockModel.class), path.toString());
            assertTrue(previous.toString().contains("Expected BEGIN_OBJECT but was BEGIN_ARRAY"), path.toString());
            FixtureModel parsed = (FixtureModel) fixedGson.fromJson(source, BlockModel.class);
            assertNotNull(parsed.custom.getRootTransform().getMatrix(), path.toString());
            if (JsonParser.parseString(source).getAsJsonObject().has("loader")) {
                assertInstanceOf(CompositeGeometry.class, parsed.custom.getCustomGeometry(), path.toString());
            }
            if (path.toString().contains("ginger_island")) ginger++;
        }
        assertEquals(43, ginger);
        assertEquals(expectedModels, fixedDeserializer.parsed, "Real PL callback must parse every authored composite child");
        assertEquals(expectedTransforms, fixedDeserializer.transforms, "Every authored child and root transformation must be consumed");
    }

    private static int countModelNodes(JsonObject model, boolean transformsOnly) {
        int count = !transformsOnly || model.has("transform") ? 1 : 0;
        if (model.has("children")) {
            for (var child : model.getAsJsonObject("children").entrySet()) {
                count += countModelNodes(child.getValue().getAsJsonObject(), transformsOnly);
            }
        }
        return count;
    }

    @Test void rotationOnlyOriginalModelsNoLongerCreateReflectivelyUninitializedTransformations() throws Exception {
        for (String name : List.of("statue_of_dwarf_king", "statue_of_blessings")) {
            JsonObject model = JsonParser.parseString(Files.readString(modelsRoot().resolve("item/" + name + ".json"))).getAsJsonObject();
            Transformation previous = new Gson().fromJson(model.get("transform"), Transformation.class);
            assertThrows(NullPointerException.class, previous::getMatrix, "Reflection ignores Neo rotation/origin keys");
            FixtureModel fixed = (FixtureModel) modelGson(new FixtureDeserializer(), true).fromJson(model, BlockModel.class);
            assertNotNull(fixed.custom.getRootTransform().getMatrix());
            assertFalse(fixed.custom.getRootTransform().equals(Transformation.identity()));
        }
    }

    @Test void affineThreeByFourMatricesPreserveRowColumnOrderTranslationAndJomlProperties() {
        Transformation result = parse("{matrix:[[-1,0,0,3],[0,1,0,0],[0,0,-1,1]]}");
        Vector3f point = result.getMatrix().transformPosition(new Vector3f(1, 2, 3));
        assertEquals(new Vector3f(2, 2, -2), point);
        assertEquals(3, result.getMatrix().m30());
        assertEquals(1, result.getMatrix().m32());
        assertEquals(1, result.getMatrix().m33());
        assertMatrix(result.getMatrix(), parse("[[-1,0,0,3],[0,1,0,0],[0,0,-1,1]]").getMatrix());
        assertMatrix(result.getMatrix(), new Matrix4f().translation(3, 0, 1).rotateY((float) Math.PI));
    }

    @Test void neoDefaultOpposingCornerAndExplicitOriginsRemainDistinct() {
        Matrix4f plain = new Matrix4f().rotateY((float) Math.PI / 2);
        assertMatrix(plain, parse("{rotation:[0,90,0],origin:'corner'}").getMatrix());
        Matrix4f opposing = new Matrix4f().translation(1, 1, 1).mul(plain).translate(-1, -1, -1);
        assertMatrix(opposing, parse("{rotation:[0,90,0]}").getMatrix());
        assertMatrix(opposing, parse("{rotation:[0,90,0],origin:'opposing-corner'}").getMatrix());
        Matrix4f centered = new Matrix4f().translation(.5F, .5F, .5F).mul(plain).translate(-.5F, -.5F, -.5F);
        assertMatrix(centered, parse("{rotation:[0,90,0],origin:'center'}").getMatrix());
        assertMatrix(centered, parse("{rotation:[0,90,0],origin:[.5,.5,.5]}").getMatrix());
    }

    @Test void translationUniformAndVectorScalesQuaternionsAndOrderedAxisRotationsMatchNativeMath() {
        assertSame(Transformation.identity(), parse("'identity'"));
        assertMatrix(new Matrix4f().translation(2, -3, 4), parse("{translation:[2,-3,4]}").getMatrix());
        assertMatrix(new Matrix4f().scaling(2), parse("{scale:2,origin:'corner'}").getMatrix());
        assertMatrix(new Matrix4f().scaling(2, 3, 4), parse("{scale:[2,3,4],origin:'corner'}").getMatrix());
        assertMatrix(new Matrix4f().rotateX((float) Math.PI / 2).rotateY((float) Math.PI / 2),
                parse("{rotation:[{x:90},{y:90}],origin:'corner'}").getMatrix());
        assertMatrix(new Matrix4f().rotateZ((float) Math.PI / 2), parse("{rotation:{z:90},origin:'corner'}").getMatrix());
        Quaternionf quaternion = new Quaternionf().rotationY((float) Math.PI / 2);
        String values = "[" + quaternion.x + "," + quaternion.y + "," + quaternion.z + "," + quaternion.w + "]";
        assertMatrix(new Matrix4f().rotate(quaternion), parse("{left_rotation:" + values + ",origin:'corner'}").getMatrix());
        assertMatrix(parse("{rotation:[10,20,30],right_rotation:[30,20,10],origin:'corner'}").getMatrix(),
                parse("{left_rotation:[10,20,30],'post-rotation':[30,20,10],origin:'corner'}").getMatrix());
    }

    @Test void malformedOrConflictingTransformsFailInsteadOfSilentlyDroppingFields() {
        for (String source : List.of("{matrix:[[1,0],[0,1],[0,0]]}", "{matrix:[],translation:[0,0,0]}",
                "{translation:[1,2]}", "{rotation:[]}", "{rotation:[0,0,0],left_rotation:[0,0,0]}",
                "{rotation:{w:1}}", "{scale:'no'}", "{origin:'unknown'}", "{unknown:1}", "'unknown'")) {
            assertThrows(JsonParseException.class, () -> parse(source), source);
        }
    }

    @Test void nativeGsonCreateHookExistsOnceAndIsRegisteredOnlyOnThePhysicalClient() throws Exception {
        AtomicInteger creation = new AtomicInteger();
        try (var bytes = BlockModel.class.getResourceAsStream("/net/minecraft/client/renderer/block/model/BlockModel.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("<clinit>")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                            if (owner.equals("com/google/gson/GsonBuilder") && method.equals("create") && desc.equals("()Lcom/google/gson/Gson;")) creation.incrementAndGet();
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(1, creation.get());
        try (var json = getClass().getResourceAsStream("/stardewcraft.fabric.mixins.json")) {
            assertNotNull(json);
            JsonObject config = JsonParser.parseString(new String(json.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String mixin = "client.model.mixin.BlockModelTransformationDeserializerMixin";
            assertEquals(1, config.getAsJsonArray("client").asList().stream().filter(value -> value.getAsString().equals(mixin)).count());
            assertFalse(config.getAsJsonArray("mixins").asList().stream().anyMatch(value -> value.getAsString().equals(mixin)));
        }
    }

    private static Transformation parse(String json) { return FIXED.fromJson(json, Transformation.class); }
    private static void assertMatrix(Matrix4f expected, Matrix4f actual) {
        assertTrue(expected.equals(actual, .00001F), () -> "Expected " + expected + " but was " + actual);
    }
    private static Path modelsRoot() {
        for (Path path : List.of(Path.of("../src/main/resources/assets/stardewcraft/models"), Path.of("src/main/resources/assets/stardewcraft/models"))) {
            if (Files.isDirectory(path.resolve("block/ginger_island"))) return path;
        }
        throw new AssertionError("Tracked shared model resources are required");
    }
    private static List<Path> transformModels() throws Exception {
        try (var paths = Files.walk(modelsRoot())) {
            List<Path> result = new ArrayList<>();
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                if (hasArrayTransform(JsonParser.parseString(Files.readString(path)))) result.add(path);
            }
            return result;
        }
    }
    private static boolean hasArrayTransform(JsonElement json) {
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("transform") && object.get("transform").isJsonObject()) {
                JsonObject transform = object.getAsJsonObject("transform");
                if (transform.has("translation") || transform.has("matrix")) return true;
            }
            return object.entrySet().stream().anyMatch(entry -> hasArrayTransform(entry.getValue()));
        }
        return json.isJsonArray() && json.getAsJsonArray().asList().stream().anyMatch(ModelTransformationDeserializerTest::hasArrayTransform);
    }
    private static Gson modelGson(FixtureDeserializer fixture, boolean adapted) throws Exception {
        Field field = BlockModel.class.getDeclaredField("GSON");
        field.setAccessible(true);
        GsonBuilder builder = ((Gson) field.get(null)).newBuilder().registerTypeAdapter(BlockModel.class, fixture);
        if (adapted) ModelTransformationDeserializer.register(builder);
        return builder.create();
    }

    /** Plain JVMs lack Mixin field storage; the actual PL deserializer method remains unmodified. */
    private static final class FixtureModel extends BlockModel implements BlockModelExtension {
        final BlockGeometryBakingContext custom = new BlockGeometryBakingContext(this);
        @SuppressWarnings("unchecked") FixtureModel(BlockModel nativeModel) {
            super(field(nativeModel, "parentLocation"), new ArrayList<>((List<BlockElement>) field(nativeModel, "elements")),
                    (Map<String, Either<Material, String>>) field(nativeModel, "textureMap"),
                    (Boolean) field(nativeModel, "hasAmbientOcclusion"), (GuiLight) field(nativeModel, "guiLight"),
                    (ItemTransforms) field(nativeModel, "transforms"), (List<ItemOverride>) field(nativeModel, "overrides"));
        }
        @Override public BlockGeometryBakingContext port_lib$getCustomData() { return custom; }
        private static <T> T field(BlockModel owner, String name) {
            try {
                Field field = BlockModel.class.getDeclaredField(name); field.setAccessible(true);
                @SuppressWarnings("unchecked") T value = (T) field.get(owner); return value;
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }
    /** The only extra storage a plain-JVM imported element needs from PL's BlockElementMixin. */
    public static final class FixtureElement extends BlockElement implements BlockElementExtension {
        private ExtraFaceData data = ExtraFaceData.DEFAULT;
        public FixtureElement(Vector3f from, Vector3f to, Map<Direction, BlockElementFace> faces,
                BlockElementRotation rotation, boolean shade) { super(from, to, faces, rotation, shade); }
        @Override public ExtraFaceData port_lib$getFaceData() { return data; }
        @Override public void port_lib$setFaceData(ExtraFaceData value) { data = java.util.Objects.requireNonNull(value); }
    }

    /** Runs the real business loader, replacing only its native element allocation with Mixin field storage. */
    private static final class ImportedModelFixtureLoader extends ClassLoader {
        private static final String OWNER = "com.stardew.craft.client.model.ImportedModelGeometry";
        ImportedModelFixtureLoader() { super(ModelTransformationDeserializerTest.class.getClassLoader()); }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(OWNER) && !name.startsWith(OWNER + "$")) return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try (var stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (stream == null) throw new ClassNotFoundException(name);
                    byte[] bytes = stream.readAllBytes();
                    if (name.equals(OWNER)) {
                        var writer = new ClassWriter(0);
                        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                                    private static final String ELEMENT = "net/minecraft/client/renderer/block/model/BlockElement";
                                    private final String fixture = FixtureElement.class.getName().replace('.', '/');
                                    @Override public void visitTypeInsn(int opcode, String type) {
                                        super.visitTypeInsn(opcode, opcode == Opcodes.NEW && type.equals(ELEMENT) ? fixture : type);
                                    }
                                    @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                                        super.visitMethodInsn(opcode, opcode == Opcodes.INVOKESPECIAL && owner.equals(ELEMENT) && method.equals("<init>") ? fixture : owner, method, desc, itf);
                                    }
                                };
                            }
                        }, 0);
                        bytes = writer.toByteArray();
                    }
                    loaded = defineClass(name, bytes, 0, bytes.length);
                } catch (java.io.IOException failure) { throw new ClassNotFoundException(name, failure); }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    private static final class PlHook extends BlockModelDeserializerMixin {}
    private static final class FixtureDeserializer extends BlockModel.Deserializer {
        private final PlHook hook = new PlHook();
        int parsed, transforms;
        @Override public BlockModel deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            FixtureModel model = new FixtureModel(super.deserialize(json, type, context));
            hook.modelLoading(json, type, context, new CallbackInfoReturnable<>("fixture", false, model));
            parsed++;
            if (json.getAsJsonObject().has("transform")) transforms++;
            return model;
        }
    }
}
