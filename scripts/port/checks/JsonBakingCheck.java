import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.math.Transformation;
import com.stardew.craft.port.PortFaceBakery;
import com.stardew.craft.port.PortSprites;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraftforge.client.model.ElementsModel;
import net.minecraftforge.client.model.ForgeFaceData;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.SimpleModelState;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.UnbakedGeometryHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Run through RenderGeometryCheck: executes the actual Mixin-woven JSON baker and ElementsModel. */
public final class JsonBakingCheck {
    private static final Path MODELS = Path.of("src/main/resources/assets/stardewcraft/models");
    private static final ResourceLocation MOD_MODEL = new ResourceLocation("stardewcraft", "block/port_json_fixture");
    private static final ResourceLocation OTHER_MODEL = new ResourceLocation("othermod", "block/port_json_fixture");
    private static final String[] ROOT_MODELS = {"decor/common/hollow_log", "decor/common/street_lamp_placed",
            "item/statue_of_blessings", "item/statue_of_dwarf_king", "item/uncertainty_statue"};

    public static void main(String[] args) throws Exception {
        // ForgeHooksClient's static ChatType table requires vanilla registries even for its pure fillNormal body.
        // Use the real in-memory bootstrap: no game client, server, save or fake registry implementation is started.
        SharedConstants.tryDetectVersion();
        // Without ModLauncher's synthetic event constructor, let the real instance API create its listener list.
        new net.minecraftforge.network.NetworkEvent(() -> null).getListenerList();
        new net.minecraftforge.network.NetworkEvent.GatherLoginPayloadsEvent(new ArrayList<>(), false).getListenerList();
        Bootstrap.bootStrap();
        TextureAtlasSprite sprite = sprite();
        checkUvScope(sprite);
        checkUvLockAndElementRotation(sprite);
        checkMetadata(sprite);
        int metadataModels = checkTrackedMetadata(sprite);
        int rootQuads = checkTrackedRootTransforms(sprite);
        checkCullAndOtherNamespace(sprite);
        System.out.println("Woven JSON: UV shrink/scope/UV lock/singular fallback, rotated geometric normals, tint/color/light/AO,");
        System.out.println("" + metadataModels + " tracked metadata models and five root transforms (" + rootQuads
                + " quads), cull buckets and foreign-namespace fallback passed.");
        System.out.println("Boundary: source/deserializer/baked data, not a GPU or in-game visual acceptance.");
    }

    private static void checkUvScope(TextureAtlasSprite sprite) {
        BlockElementFace face = face(new float[]{0, 0, 16, 16});
        float[] saved = face.uv.uvs.clone();
        BakedQuad mod = bake(face, sprite, Direction.UP, BlockModelRotation.X0_Y0, null, false, MOD_MODEL);
        BakedQuad other = bake(face, sprite, Direction.UP, BlockModelRotation.X0_Y0, null, false, OTHER_MODEL);
        float lower = Mth.lerp(sprite.uvShrinkRatio(), 0, 8);
        float upper = Mth.lerp(sprite.uvShrinkRatio(), 16, 8);
        exact(minUv(mod, 4), PortSprites.getU(sprite, lower / 16), "Mod JSON retained the old extra UV inset");
        exact(minUv(other, 4), sprite.getU((double) lower * .999 + (double) upper * .001), "Foreign JSON no longer uses Forge UVs");
        require(minUv(mod, 4) != minUv(other, 4), "Namespace guard was not exercised");
        require(Arrays.equals(saved, face.uv.uvs), "Baking changed the shared authored UV rectangle");
        require(mod.getTintIndex() == 3 && !mod.isShade() && mod.hasAmbientOcclusion(), "UV bridge lost quad flags");
    }

    private static void checkUvLockAndElementRotation(TextureAtlasSprite sprite) {
        BlockElementFace face = new BlockElementFace(null, 3, "#tex", new BlockFaceUV(new float[]{1, 2, 14, 15}, 90));
        BlockElementRotation rotation = new BlockElementRotation(new Vector3f(.5F, .5F, .5F), Direction.Axis.Y, 22.5F, false);
        ModelState locked = new SimpleModelState(BlockModelRotation.X0_Y90.getRotation(), true);
        BakedQuad actual = bake(face, sprite, Direction.EAST, locked, rotation, true, MOD_MODEL);
        BakedQuad reference = PortFaceBakery.bakeQuad(new Vector3f(), new Vector3f(16), face, sprite, Direction.EAST, locked, rotation, true);
        sameQuad(actual, reference, "UV-locked rotated JSON no longer matches the 1.21 baker");
        assertGeometricNormal(actual);
        int axisNormal = 127 << 16;
        require(actual.getVertices()[7] != axisNormal, "Rotated element still has only an axis-aligned normal");

        ModelState singular = new SimpleModelState(new Transformation(new Matrix4f().scaling(0, 1, 1)), true);
        BakedQuad singularQuad = bake(face, sprite, Direction.NORTH, singular, null, true, MOD_MODEL);
        BlockFaceUV expectedUv = FaceBakery.recomputeUVs(face.uv, Direction.NORTH, Transformation.identity(), MOD_MODEL);
        BlockElementFace expectedFace = new BlockElementFace(null, face.tintIndex, face.texture, expectedUv);
        BakedQuad expected = bake(expectedFace, sprite, Direction.NORTH,
                new SimpleModelState(singular.getRotation(), false), null, true, MOD_MODEL);
        require(Arrays.equals(singularQuad.getVertices(), expected.getVertices()), "Singular UV lock must use 1.21 identity fallback");
    }

    private static void checkMetadata(TextureAtlasSprite sprite) {
        BlockModel model = BlockModel.fromString("""
                {"textures":{"tex":"stardewcraft:item/fixture","particle":"#tex"},"elements":[
                 {"from":[0,0,0],"to":[16,16,16],"shade":false,
                  "forge_data":{"color":"ff804020","block_light":5,"sky_light":9,"ambient_occlusion":false},
                  "faces":{"east":{"uv":[0,0,16,16],"texture":"#tex","tintindex":3},
                           "north":{"uv":[0,0,16,16],"texture":"#tex","tintindex":7,
                                    "forge_data":{"color":"ff102030","block_light":12,"sky_light":3,"ambient_occlusion":true}}}}]}
                """);
        BlockElement element = model.getElements().get(0);
        BakedQuad east = bakeElement(element, Direction.EAST, sprite, MOD_MODEL);
        BakedQuad north = bakeElement(element, Direction.NORTH, sprite, MOD_MODEL);
        assertMetadata(east, 0xff204080, 0x00900050, 3, false, false);
        assertMetadata(north, 0xff302010, 0x003000c0, 7, false, true);
    }

    private static int checkTrackedMetadata(TextureAtlasSprite sprite) throws Exception {
        int models = 0;
        try (var paths = Files.walk(MODELS)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String source = Files.readString(path).replace("\ufeff", "");
                if (!source.contains("\"forge_data\"")) continue;
                JsonObject json = JsonParser.parseString(source).getAsJsonObject();
                if (json.has("loader")) continue;
                require(!source.contains("\"neoforge_data\""), "Unconverted native metadata: " + path);
                BlockModel model = BlockModel.fromString(source);
                for (BlockElement element : model.getElements()) {
                    for (Direction direction : element.faces.keySet()) {
                        BlockElementFace face = element.faces.get(direction);
                        ForgeFaceData data = face.getFaceData();
                        BakedQuad quad = bakeElement(element, direction, sprite, MOD_MODEL);
                        assertMetadata(quad, abgr(data.color()), (data.skyLight() << 20) | (data.blockLight() << 4),
                                face.tintIndex, element.shade, data.ambientOcclusion());
                    }
                }
                models++;
            }
        }
        require(models >= 39, "Tracked native metadata fixtures were lost: " + models);
        BlockModel furnace = load("block/utility/furnace_working", new HashMap<>());
        require(furnace.getElements().stream().flatMap(e -> e.faces.values().stream())
                .anyMatch(face -> face.getFaceData().blockLight() == 15 && !face.getFaceData().ambientOcclusion()),
                "Production furnace face metadata was discarded by the real Forge deserializer");
        return models;
    }

    private static int checkTrackedRootTransforms(TextureAtlasSprite sprite) throws Exception {
        int checked = 0;
        for (String path : ROOT_MODELS) {
            BlockModel model = load(path, new HashMap<>());
            Transformation root = model.customData.getRootTransform();
            require(!root.isIdentity(), "Root fixture became identity: " + path);
            for (BlockModelRotation state : new BlockModelRotation[]{BlockModelRotation.X0_Y0, BlockModelRotation.X0_Y90}) {
                CaptureBuilder output = addQuads(model, state, sprite, new ResourceLocation("stardewcraft", path));
                ModelState composed = UnbakedGeometryHelper.composeRootTransformIntoModelState(state, root);
                List<BakedQuad> expected = new ArrayList<>();
                for (BlockElement element : model.getElements()) {
                    for (Direction direction : element.faces.keySet()) {
                        expected.add(PortFaceBakery.bakeFace(element, element.faces.get(direction), sprite, direction, composed));
                    }
                }
                require(output.all.size() == expected.size(), "Root bridge duplicated or lost faces: " + path);
                for (int i = 0; i < expected.size(); i++) {
                    sameQuad(output.all.get(i), expected.get(i), "Root transform differs from NF pre-bake: " + path);
                    assertGeometricNormal(output.all.get(i));
                }
                checked += expected.size();
            }
        }
        BlockModel log = load("decor/common/hollow_log", new HashMap<>());
        CaptureBuilder logOutput = addQuads(log, BlockModelRotation.X0_Y0, sprite, new ResourceLocation("stardewcraft", ROOT_MODELS[0]));
        BakedQuad crownEast = logOutput.all.stream().filter(quad -> quad.getDirection() == Direction.EAST).findFirst().orElseThrow();
        require(crownEast.getVertices()[7] == 0x00cf0075, "Hollow log root normal was truncated after packing");
        for (String path : new String[]{"item/statue_of_blessings", "item/statue_of_dwarf_king"}) {
            BlockModel model = load(path, new HashMap<>());
            CaptureBuilder output = addQuads(model, BlockModelRotation.X0_Y0, sprite, new ResourceLocation("stardewcraft", path));
            Direction firstOriginalFace = model.getElements().get(0).faces.keySet().iterator().next();
            require(output.all.get(0).getDirection() == model.customData.getRootTransform().rotateTransform(firstOriginalFace),
                    "180-degree statue root left the original baked lighting direction");
        }
        return checked;
    }

    private static void checkCullAndOtherNamespace(TextureAtlasSprite sprite) throws Exception {
        BlockModel model = BlockModel.fromString("""
                {"textures":{"tex":"stardewcraft:item/fixture","particle":"#tex"},
                 "transform":{"rotation":[0,90,0],"origin":"center"},"elements":[
                  {"from":[0,0,0],"to":[16,16,16],
                   "faces":{"north":{"uv":[0,0,16,16],"texture":"#tex","cullface":"north",
                                     "forge_data":{"calculate_normals":true}}}}]}
                """);
        CaptureBuilder mod = addQuads(model, BlockModelRotation.X0_Y0, sprite, MOD_MODEL);
        require(mod.culled.size() == 1 && mod.culled.containsKey(Direction.WEST) && mod.unculled.isEmpty(),
                "Root transform did not rotate the culled bucket with its geometry");
        CaptureBuilder other = addQuads(model, BlockModelRotation.X0_Y0, sprite, OTHER_MODEL);
        require(other.culled.size() == 1 && other.culled.containsKey(Direction.NORTH), "Foreign root cull semantics changed");
        require(other.all.get(0).getDirection() == Direction.NORTH && mod.all.get(0).getDirection() == Direction.WEST,
                "Foreign ElementsModel was replaced or mod lighting direction stayed unrotated");
    }

    private static CaptureBuilder addQuads(BlockModel model, ModelState state, TextureAtlasSprite sprite,
            ResourceLocation location) throws Exception {
        Method method = ElementsModel.class.getDeclaredMethod("addQuads", IGeometryBakingContext.class, IModelBuilder.class,
                ModelBaker.class, Function.class, ModelState.class, ResourceLocation.class);
        method.setAccessible(true);
        CaptureBuilder output = new CaptureBuilder();
        method.invoke(new ElementsModel(model.getElements()), model.customData, output, null,
                (Function<Material, TextureAtlasSprite>) material -> sprite, state, location);
        return output;
    }

    private static BlockModel load(String path, Map<String, BlockModel> cache) throws Exception {
        if (cache.containsKey(path)) return cache.get(path);
        String source = Files.readString(MODELS.resolve(path + ".json")).replace("\ufeff", "");
        JsonObject json = JsonParser.parseString(source).getAsJsonObject();
        BlockModel model = BlockModel.fromString(source);
        model.name = "stardewcraft:" + path;
        cache.put(path, model);
        if (json.has("parent") && json.get("parent").getAsString().startsWith("stardewcraft:")) {
            model.parent = load(json.get("parent").getAsString().substring("stardewcraft:".length()), cache);
        }
        return model;
    }

    private static BlockElementFace face(float[] uv) { return new BlockElementFace(null, 3, "#tex", new BlockFaceUV(uv, 0)); }

    private static BakedQuad bakeElement(BlockElement element, Direction direction, TextureAtlasSprite sprite, ResourceLocation model) {
        return new FaceBakery().bakeQuad(element.from, element.to, element.faces.get(direction), sprite, direction,
                BlockModelRotation.X0_Y0, element.rotation, element.shade, model);
    }

    private static BakedQuad bake(BlockElementFace face, TextureAtlasSprite sprite, Direction facing, ModelState state,
            BlockElementRotation rotation, boolean shade, ResourceLocation model) {
        return new FaceBakery().bakeQuad(new Vector3f(), new Vector3f(16), face, sprite, facing, state, rotation, shade, model);
    }

    private static TextureAtlasSprite sprite() throws Exception {
        // The sprite constructor and all UV arithmetic are real. Only native-image allocation is bypassed.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        SpriteContents contents = (SpriteContents) unsafeClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), SpriteContents.class);
        set(contents, "width", 16);
        set(contents, "height", 16);
        return new FixtureSprite(contents);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static final class FixtureSprite extends TextureAtlasSprite {
        private FixtureSprite(SpriteContents contents) {
            super(new ResourceLocation("minecraft", "textures/atlas/blocks.png"), contents, 256, 256, 32, 64);
        }
    }

    private static final class CaptureBuilder implements IModelBuilder<CaptureBuilder> {
        private final List<BakedQuad> all = new ArrayList<>(), unculled = new ArrayList<>();
        private final Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
        @Override public CaptureBuilder addCulledFace(Direction direction, BakedQuad quad) {
            all.add(quad);
            culled.computeIfAbsent(direction, key -> new ArrayList<>()).add(quad);
            return this;
        }
        @Override public CaptureBuilder addUnculledFace(BakedQuad quad) { all.add(quad); unculled.add(quad); return this; }
        @Override public BakedModel build() { throw new AssertionError("Fixture must not construct a client BakedModel"); }
    }

    private static void assertMetadata(BakedQuad quad, int color, int light, int tint, boolean shade, boolean ao) {
        require(quad.getTintIndex() == tint && quad.isShade() == shade && quad.hasAmbientOcclusion() == ao, "Face flags were discarded");
        for (int i = 0; i < 4; i++) {
            require(quad.getVertices()[i * 8 + 3] == color && quad.getVertices()[i * 8 + 6] == light, "Face color/light metadata was discarded");
        }
    }

    private static void assertGeometricNormal(BakedQuad quad) {
        Vector3f a = position(quad, 3).sub(position(quad, 1));
        Vector3f b = position(quad, 2).sub(position(quad, 0));
        Vector3f normal = b.cross(a).normalize();
        int packed = ((byte) Math.round(normal.x() * 127) & 255)
                | (((byte) Math.round(normal.y() * 127) & 255) << 8)
                | (((byte) Math.round(normal.z() * 127) & 255) << 16);
        for (int i = 0; i < 4; i++) require(quad.getVertices()[i * 8 + 7] == packed, "Geometry-derived packed normal changed");
    }

    private static Vector3f position(BakedQuad quad, int index) {
        int[] data = quad.getVertices();
        return new Vector3f(Float.intBitsToFloat(data[index * 8]), Float.intBitsToFloat(data[index * 8 + 1]), Float.intBitsToFloat(data[index * 8 + 2]));
    }

    private static float minUv(BakedQuad quad, int offset) {
        float result = Float.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) result = Math.min(result, Float.intBitsToFloat(quad.getVertices()[i * 8 + offset]));
        return result;
    }

    private static int abgr(int color) { return (color & 0xff00ff00) | ((color >> 16) & 255) | ((color << 16) & 0x00ff0000); }
    private static void exact(float actual, float expected, String message) { require(Float.floatToRawIntBits(actual) == Float.floatToRawIntBits(expected), message); }
    private static void sameQuad(BakedQuad actual, BakedQuad expected, String message) {
        require(Arrays.equals(actual.getVertices(), expected.getVertices()) && actual.getDirection() == expected.getDirection()
                && actual.getTintIndex() == expected.getTintIndex() && actual.isShade() == expected.isShade()
                && actual.hasAmbientOcclusion() == expected.hasAmbientOcclusion() && actual.getSprite() == expected.getSprite(), message);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
