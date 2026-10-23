package com.stardew.craft.platform.fabric.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.math.Transformation;
import io.github.fabricators_of_create.porting_lib.models.geometry.BlockGeometryBakingContext;
import io.github.fabricators_of_create.porting_lib.models.geometry.IUnbakedGeometry;
import io.github.fabricators_of_create.porting_lib.models.geometry.extensions.BlockModelExtension;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

class OrdinaryModelTransformsTest {
    private static Gson gson;
    private static TextureAtlasSprite sprite;
    private static Object nativeBakery;
    private static Method nativeBake;

    @BeforeAll static void setup() throws Exception {
        Method setup = ModelTransformationDeserializerTest.class.getDeclaredMethod("setup");
        setup.setAccessible(true); setup.invoke(null);
        Class<?> fixture = Class.forName(ModelTransformationDeserializerTest.class.getName() + "$FixtureDeserializer");
        var constructor = fixture.getDeclaredConstructor(); constructor.setAccessible(true);
        Method makeGson = ModelTransformationDeserializerTest.class.getDeclaredMethod("modelGson", fixture, boolean.class);
        makeGson.setAccessible(true); gson = (Gson) makeGson.invoke(null, constructor.newInstance(), true);
        Method makeSprite = CompositeGeometryTest.class.getDeclaredMethod("sprite");
        makeSprite.setAccessible(true); sprite = (TextureAtlasSprite) makeSprite.invoke(null);
        Class<?> bakery = new NativeBakeryFixture().define();
        nativeBakery = bakery.getConstructor().newInstance();
        nativeBake = bakery.getMethod("bakeQuad", Vector3f.class, Vector3f.class, BlockElementFace.class,
                TextureAtlasSprite.class, Direction.class, ModelState.class, BlockElementRotation.class, boolean.class);
    }

    @Test void fiveExistingOrdinaryModelsNowBakeTheirAuthoredTransformInsteadOfIdenticalUntransformedVertices() throws Exception {
        for (String name : List.of("item/statue_of_dwarf_king", "item/statue_of_blessings", "item/uncertainty_statue",
                "decor/common/street_lamp_placed", "decor/common/hollow_log")) {
            Map<ResourceLocation, BlockModel> models = new HashMap<>();
            BlockModel model = read(ResourceLocation.fromNamespaceAndPath("stardewcraft", name), models);
            model.resolveParents(id -> read(id, models));
            var context = ((BlockModelExtension) (Object) model).port_lib$getCustomData();
            assertFalse(context.hasCustomGeometry(), name);
            ModelState combined = OrdinaryModelTransforms.forBake(model, BlockModelRotation.X0_Y0);
            assertInstanceOf(OrdinaryModelTransforms.RootTransformState.class, combined, name);
            int changed = 0, checked = 0;
            for (BlockElement element : model.getElements()) for (var face : element.faces.entrySet()) {
                BakedQuad previous = BlockModel.bakeFace(element, face.getValue(), sprite, face.getKey(), BlockModelRotation.X0_Y0);
                BakedQuad fixed = bake(element, face.getValue(), face.getKey(), combined);
                if (!points(previous, new Matrix4f()).equals(points(fixed, new Matrix4f()))) changed++;
                // Current resources have no element rotations; compare each actual baked corner with
                // the authored root matrix, independent of FaceBakery's vertex ordering.
                if (element.rotation == null) {
                    assertPoints(points(previous, context.getRootTransform().getMatrix()), points(fixed, new Matrix4f()), name);
                    checked++;
                }
            }
            assertTrue(checked > 0, name); assertTrue(changed > 0, name);
        }
    }

    @Test void parentRootIsInheritedButAnExplicitChildRootOrIdentityReplacesIt() {
        ElementModel parent = new ElementModel();
        parent.custom.setRootTransform(new Transformation(new Matrix4f().translation(2, 0, 0)));
        ElementModel child = new ElementModel(); child.parent = parent;
        assertEquals(2F, OrdinaryModelTransforms.forBake(child, BlockModelRotation.X0_Y0).getRotation().getMatrix().m30());
        child.custom.setRootTransform(new Transformation(new Matrix4f().translation(0, 3, 0)));
        Matrix4f result = OrdinaryModelTransforms.forBake(child, BlockModelRotation.X0_Y0).getRotation().getMatrix();
        assertEquals(0F, result.m30()); assertEquals(3F, result.m31());
        child.custom.setRootTransform(Transformation.identity());
        assertSame(BlockModelRotation.X0_Y0, OrdinaryModelTransforms.forBake(child, BlockModelRotation.X0_Y0));
    }

    @Test void cullDirectionAndUvLockUseTheSameCombinedStateAsNativeFaceBakery() throws Exception {
        ElementModel model = new ElementModel();
        model.custom.setRootTransform(new Transformation(new Matrix4f().translation(.5F, .5F, .5F)
                .rotateY((float) Math.PI / 2).translate(-.5F, -.5F, -.5F)));
        ModelState original = state(BlockModelRotation.X0_Y90.getRotation(), true);
        ModelState combined = OrdinaryModelTransforms.forBake(model, original);
        assertTrue(combined.isUvLocked());
        Matrix4f expected = new Matrix4f(original.getRotation().getMatrix()).translate(-.5F, -.5F, -.5F)
                .mul(model.custom.getRootTransform().getMatrix()).translate(.5F, .5F, .5F);
        Direction cull = Direction.rotate(combined.getRotation().getMatrix(), Direction.NORTH);
        assertEquals(Direction.rotate(expected, Direction.NORTH), cull);
        assertNotEquals(Direction.rotate(original.getRotation().getMatrix(), Direction.NORTH), cull);
        BlockElement element = element(); BlockElementFace face = element.faces.get(Direction.NORTH);
        BakedQuad fixed = bake(element, face, Direction.NORTH, combined);
        BakedQuad oracle = new FaceBakery().bakeQuad(element.from, element.to, face, sprite, Direction.NORTH,
                state(new Transformation(expected), true), null, true);
        assertArrayEquals(oracle.getVertices(), fixed.getVertices());
        assertEquals(cull, fixed.getDirection());
    }

    @Test void arbitraryRootRotationKeepsAllFourCornersInsteadOfVanillaAxisAlignedWinding() throws Exception {
        ElementModel model = new ElementModel();
        model.custom.setRootTransform(new Transformation(new Matrix4f().translation(.5F, .5F, .5F)
                .rotateY((float) Math.toRadians(22.5)).translate(-.5F, -.5F, -.5F)));
        BlockElement element = element(); BlockElementFace face = element.faces.get(Direction.UP);
        ModelState combined = OrdinaryModelTransforms.forBake(model, BlockModelRotation.X0_Y0);
        BakedQuad fixed = bake(element, face, Direction.UP, combined);
        BakedQuad raw = BlockModel.bakeFace(element, face, sprite, Direction.UP, BlockModelRotation.X0_Y0);
        assertPoints(points(raw, model.custom.getRootTransform().getMatrix()), points(fixed, new Matrix4f()), "arbitrary rotation");
        BakedQuad oldWinding = new FaceBakery().bakeQuad(element.from, element.to, face, sprite, Direction.UP,
                state(combined.getRotation(), false), null, true);
        assertNotEquals(points(oldWinding, new Matrix4f()), points(fixed, new Matrix4f()));
        // Composite outer transforms enter the same FaceBakery boundary, and need the same marker.
        assertInstanceOf(OrdinaryModelTransforms.RootTransformState.class,
                CompositeGeometry.composeRootTransform(BlockModelRotation.X0_Y0, model.custom.getRootTransform()));
    }

    @Test void customGeometryInheritedGeometryAndBuiltinGeneratedBranchesKeepTheirExistingState() {
        ElementModel model = new ElementModel();
        model.custom.setRootTransform(new Transformation(new Matrix4f().translation(1, 2, 3)));
        model.custom.setCustomGeometry(new CustomGeometry());
        assertSame(BlockModelRotation.X0_Y0, OrdinaryModelTransforms.forBake(model, BlockModelRotation.X0_Y0));
        ElementModel child = new ElementModel(); child.parent = model;
        assertSame(BlockModelRotation.X0_Y0, OrdinaryModelTransforms.forBake(child, BlockModelRotation.X0_Y0));
        for (BlockModel marker : List.of(ModelBakery.BLOCK_ENTITY_MARKER, ModelBakery.GENERATION_MARKER)) {
            ElementModel special = new ElementModel(); special.parent = marker;
            special.custom.setRootTransform(new Transformation(new Matrix4f().translation(1, 2, 3)));
            assertSame(BlockModelRotation.X0_Y0, OrdinaryModelTransforms.forBake(special, BlockModelRotation.X0_Y0));
        }
    }

    @Test void nativeExpressionCountsAndPhysicalClientConfigurationMatchTheStrictMixinContract() throws Exception {
        int[] counts = new int[2];
        try (var stream = BlockModel.class.getResourceAsStream("BlockModel.class")) {
            new ClassReader(stream).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("bake") || !descriptor.contains("Lnet/minecraft/client/renderer/block/model/BlockModel;")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                            if (owner.equals("net/minecraft/client/renderer/block/model/BlockModel") && name.equals("bakeFace")) counts[0]++;
                            if (owner.equals("net/minecraft/client/resources/model/ModelState") && name.equals("getRotation")) counts[1]++;
                        }
                    };
                }
            }, 0);
        }
        assertArrayEquals(new int[]{2, 1}, counts);
        try (var stream = getClass().getClassLoader().getResourceAsStream("stardewcraft.fabric.mixins.json")) {
            var config = JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject();
            for (String name : List.of("OrdinaryBlockModelTransformMixin", "RootTransformWindingMixin")) {
                String relative = "client.model.mixin." + name;
                assertTrue(config.getAsJsonArray("client").asList().stream().anyMatch(v -> v.getAsString().equals(relative)));
                assertTrue(config.getAsJsonArray("mixins").asList().stream().noneMatch(v -> v.getAsString().equals(relative)));
            }
        }
    }

    private static ModelState state(Transformation rotation, boolean uvLock) {
        return new ModelState() {
            @Override public Transformation getRotation() { return rotation; }
            @Override public boolean isUvLocked() { return uvLock; }
        };
    }
    private static BlockElement element() {
        var faces = new java.util.EnumMap<Direction, BlockElementFace>(Direction.class);
        for (Direction direction : List.of(Direction.NORTH, Direction.UP)) faces.put(direction,
                new BlockElementFace(direction, -1, "#stone", new BlockFaceUV(new float[]{1, 2, 13, 15}, 0)));
        return new BlockElement(new Vector3f(2, 1, 3), new Vector3f(12, 11, 15), faces, null, true);
    }
    private static BakedQuad bake(BlockElement element, BlockElementFace face, Direction direction, ModelState state) throws Exception {
        return (BakedQuad) nativeBake.invoke(nativeBakery, element.from, element.to, face, sprite, direction, state, element.rotation, element.shade);
    }
    private static List<Vector3f> points(BakedQuad quad, Matrix4f transform) {
        var result = new ArrayList<Vector3f>(); int[] vertices = quad.getVertices();
        for (int i = 0; i < 4; i++) result.add(transform.transformPosition(new Vector3f(Float.intBitsToFloat(vertices[i * 8]),
                Float.intBitsToFloat(vertices[i * 8 + 1]), Float.intBitsToFloat(vertices[i * 8 + 2]))));
        return result;
    }
    private static void assertPoints(List<Vector3f> expected, List<Vector3f> actual, String description) {
        for (Vector3f point : expected) assertTrue(actual.stream().anyMatch(v -> v.equals(point, .0001F)), description + ": " + point + " in " + actual);
    }
    private static BlockModel read(ResourceLocation id, Map<ResourceLocation, BlockModel> models) {
        return models.computeIfAbsent(id, key -> {
            try {
                Path path = modelsRoot().resolve(key.getPath() + ".json");
                return gson.fromJson(Files.exists(path) ? Files.readString(path) : "{}", BlockModel.class);
            } catch (Exception failure) { throw new AssertionError(key.toString(), failure); }
        });
    }
    private static Path modelsRoot() {
        for (Path root : List.of(Path.of("../src/main/resources/assets/stardewcraft/models"), Path.of("src/main/resources/assets/stardewcraft/models")))
            if (Files.isDirectory(root)) return root;
        throw new AssertionError("Tracked model fixtures are required");
    }
    private static final class ElementModel extends BlockModel implements BlockModelExtension {
        final BlockGeometryBakingContext custom = new BlockGeometryBakingContext(this);
        ElementModel() { super(null, List.of(), Map.of(), true, GuiLight.FRONT, ItemTransforms.NO_TRANSFORMS, List.of()); }
        @Override public BlockGeometryBakingContext port_lib$getCustomData() { return custom; }
    }
    private static final class CustomGeometry implements IUnbakedGeometry<CustomGeometry> {
        @Override public net.minecraft.client.resources.model.BakedModel bake(
                io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryBakingContext context,
                net.minecraft.client.resources.model.ModelBaker baker,
                java.util.function.Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> textures,
                ModelState state, net.minecraft.client.renderer.block.model.ItemOverrides overrides) {
            throw new AssertionError("Ordinary bridge must not invoke a custom geometry");
        }
    }

    /** Runs all real native FaceBakery code; only inserts the production winding predicate into its sole invocation. */
    private static final class NativeBakeryFixture extends ClassLoader {
        Class<?> define() throws Exception {
            String owner = "net/minecraft/client/renderer/block/model/FaceBakery";
            String renamed = getClass().getPackageName().replace('.', '/') + "/RootTransformNativeBakeryFixture";
            var names = new SimpleRemapper(Map.of(owner, renamed, owner + "$1", renamed + "$1"));
            // The native element-rotation switch uses a package-private compiler helper.
            // Keep that unmodified helper in the fixture's same runtime package/loader too.
            try (var stream = FaceBakery.class.getResourceAsStream("FaceBakery$1.class")) {
                ClassWriter helper = new ClassWriter(0);
                new ClassReader(stream).accept(new ClassRemapper(helper, names), 0);
                byte[] bytes = helper.toByteArray();
                defineClass((renamed + "$1").replace('/', '.'), bytes, 0, bytes.length);
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            try (var stream = FaceBakery.class.getResourceAsStream("FaceBakery.class")) {
                new ClassReader(stream).accept(new ClassRemapper(writer, names) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (!name.equals("bakeQuad")) return base;
                        return new MethodVisitor(Opcodes.ASM9, base) {
                            @Override public void visitMethodInsn(int opcode, String target, String method, String desc, boolean itf) {
                                if (target.equals(owner) && method.equals("recalculateWinding")) {
                                    Label call = new Label(), done = new Label();
                                    visitVarInsn(Opcodes.ALOAD, 6);
                                    visitTypeInsn(Opcodes.INSTANCEOF, OrdinaryModelTransforms.RootTransformState.class.getName().replace('.', '/'));
                                    visitJumpInsn(Opcodes.IFEQ, call);
                                    visitInsn(Opcodes.POP); visitInsn(Opcodes.POP); visitInsn(Opcodes.POP);
                                    visitJumpInsn(Opcodes.GOTO, done);
                                    visitLabel(call); super.visitMethodInsn(opcode, target, method, desc, itf); visitLabel(done);
                                } else super.visitMethodInsn(opcode, target, method, desc, itf);
                            }
                        };
                    }
                }, ClassReader.EXPAND_FRAMES);
            }
            byte[] bytes = writer.toByteArray();
            return defineClass(renamed.replace('/', '.'), bytes, 0, bytes.length);
        }
    }
}
