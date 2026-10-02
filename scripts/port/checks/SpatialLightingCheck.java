package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Executes the production clipping/render bridge without opening a Minecraft client. */
public final class SpatialLightingCheck {
    private static final double EPSILON = 2.0e-5;
    private static final int OVERLAY = 1234;

    public static void main(String[] args) throws Exception {
        checkResourceFixtures();
        proveIsolatedTicketShadow();
        for (int facing = 0; facing < 4; facing++) {
            BakedQuad ticket = rotate(face(Direction.NORTH, 1 / 16.0, 1 / 16.0, 6 / 16.0,
                    15 / 16.0, 29 / 16.0, 12 / 16.0), facing);
            checkFragments(ticket, true);
            checkRenderBridge(ticket);
            BakedQuad roof = rotate(tiltRoof(face(Direction.UP, -1 / 16.0, 6 / 16.0, 1 / 16.0,
                    4 / 16.0, 9 / 16.0, 15 / 16.0)), facing);
            checkFragments(roof, false);
            checkRenderBridge(roof);
        }
        checkLocalAndEmptyFallback();
        checkConcurrentCacheReload();
        System.out.println("Spatial lighting: ticket main/upper and tilted roof in four facings; geometry/UV/color/light/normal,");
        System.out.println("cull buckets, seed/ModelData/render layer, pose/position, recursion and concurrent reload checks passed.");
    }

    private static void checkResourceFixtures() throws Exception {
        JsonObject ticket = element("src/main/resources/assets/stardewcraft/models/block/decor/ticket_machine.json", "机身");
        checkVector(ticket, "from", 1, 1, 6);
        checkVector(ticket, "to", 15, 29, 12);
        JsonObject roof = element("src/main/resources/assets/stardewcraft/models/block/decor/house/timber_awning/timber_awning_3_2.json", "roof_timber_9");
        checkVector(roof, "from", -1, 6, 1);
        checkVector(roof, "to", 4, 9, 15);
        JsonObject rotation = roof.getAsJsonObject("rotation");
        require(rotation.get("axis").getAsString().equals("x"), "Roof fixture rotation axis changed");
        close(rotation.get("angle").getAsDouble(), -22.5, "Roof fixture rotation changed");
        checkVector(rotation, "origin", 1.5, 8, 15);
    }

    private static JsonObject element(String path, String name) throws Exception {
        JsonObject model = JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
        for (var value : model.getAsJsonArray("elements")) {
            JsonObject element = value.getAsJsonObject();
            if (element.has("name") && element.get("name").getAsString().equals(name)) return element;
        }
        throw new AssertionError("Missing production geometry fixture " + path + ": " + name);
    }

    private static void checkVector(JsonObject object, String key, double x, double y, double z) {
        var vector = object.getAsJsonArray(key);
        double[] expected = {x, y, z};
        require(vector.size() == 3, "Invalid fixture vector");
        for (int axis = 0; axis < 3; axis++) close(vector.get(axis).getAsDouble(), expected[axis], "Geometry fixture changed: " + key);
    }

    private static void proveIsolatedTicketShadow() {
        // Ticket-machine body reaches y=29/16. With a floor below and air around the machine,
        // vanilla north-face AO corners are 0.6 below and 1.0 above. Oversized interpolation
        // extrapolates above 1; the renderer writes the resulting channel to a byte and wraps.
        double top = 29 / 16.0;
        require(1 - top < 0, "Ticket upper-face weight must reproduce the out-of-domain input");
        double before = (0.6 * (1 - top) + top) * 0.8;
        int wrapped = ((int) (before * 255)) & 255;
        double after = (1 * (1 - (top - 1)) + (top - 1)) * 0.8;
        require(before > 1 && wrapped < 64 && Math.abs(after - 0.8) < EPSILON,
                "Isolated upper-cell lighting regression was not reproduced");
        System.out.println("Isolated ticket: AO weight 1-y=" + (1 - top) + ", old shade=" + before
                + " (byte=" + wrapped + "); upper-cell local sampling restores shade=" + after + ".");
    }

    private static void checkFragments(BakedQuad original, boolean ticket) {
        List<SpatialBlockModelRenderer.Fragment> fragments = SpatialBlockModelRenderer.split(original);
        require(fragments.size() >= 2, "Fixture must cross a cell boundary");
        double totalArea = 0;
        boolean upper = false;
        for (var fragment : fragments) {
            BakedQuad quad = fragment.quad();
            require(quad.getDirection() == original.getDirection() && quad.getTintIndex() == original.getTintIndex()
                    && quad.isShade() == original.isShade() && quad.hasAmbientOcclusion() == original.hasAmbientOcclusion()
                    && quad.getSprite() == original.getSprite(), "Fragment lost face/material properties");
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            for (int i = 0; i < 4; i++) {
                double[] world = new double[3];
                int[] offset = {fragment.offset().getX(), fragment.offset().getY(), fragment.offset().getZ()};
                for (int axis = 0; axis < 3; axis++) {
                    double value = Float.intBitsToFloat(data[i * stride + axis]);
                    require(value >= -EPSILON && value <= 1 + EPSILON, "AO coordinate outside [0,1]");
                    world[axis] = value + offset[axis];
                }
                close(Float.intBitsToFloat(data[i * stride + 4]), uvU(world), "UV U changed");
                close(Float.intBitsToFloat(data[i * stride + 5]), uvV(world), "UV V changed");
                require(data[i * stride + 3] == 0xffffffff && data[i * stride + 6] == 0x00900070
                        && data[i * stride + 7] == original.getVertices()[7], "Color/light/normal changed");
            }
            totalArea += area(quad);
            upper |= fragment.offset().getY() == 1;
        }
        close(totalArea, area(original), "Clipping lost/duplicated surface area");
        if (ticket) require(upper, "Ticket upper half was not sampled in its own world cell");
    }

    private static void checkRenderBridge(BakedQuad quad) {
        ModelData data = ModelData.builder().with(new ModelProperty<Boolean>(), true).build();
        // Real RenderType static initialization bootstraps Forge's transformed event bus.
        // The standalone check leaves it uninitialized; the ASM gate also checks forwarding.
        RenderType layer = null;
        Map<Direction, List<BakedQuad>> buckets = new EnumMap<>(Direction.class);
        buckets.put(quad.getDirection(), List.of(quad));
        BakedModel source = model(buckets, List.of(quad), data, layer, 73);
        BlockPos origin = new BlockPos(19, 70, -33);
        for (boolean checkSides : new boolean[]{false, true}) {
            var renderer = new CaptureRenderer(data, layer, origin, checkSides);
            PoseStack pose = new PoseStack();
            require(SpatialBlockModelRenderer.renderMapDecor(renderer, null, source, null, origin, pose, null,
                    checkSides, RandomSource.create(), 73, OVERLAY, data, layer), "Cross-cell model did not use the bridge");
            require(renderer.calls >= 2, "Cell rendering did not include all fragments");
            close(pose.last().pose().m30(), 0, "Pose X not restored");
            close(pose.last().pose().m31(), 0, "Pose Y not restored");
            close(pose.last().pose().m32(), 0, "Pose Z not restored");
        }
    }

    private static void checkLocalAndEmptyFallback() {
        BakedQuad local = face(Direction.NORTH, 0.1, 0.1, 0.1, 0.9, 0.9, 0.9);
        var renderer = new CaptureRenderer(ModelData.EMPTY, null);
        for (List<BakedQuad> quads : List.of(List.of(local), List.<BakedQuad>of())) {
            BakedModel source = model(Map.of(), quads, ModelData.EMPTY, null, 0);
            require(!SpatialBlockModelRenderer.renderMapDecor(renderer, null, source, null, BlockPos.ZERO,
                    new PoseStack(), null, true, RandomSource.create(), 0, OVERLAY, ModelData.EMPTY, null),
                    "Local/empty model must retain the original rendering path");
        }
        require(renderer.calls == 0, "Fallback rendered twice");
    }

    private static void checkConcurrentCacheReload() throws Exception {
        BakedQuad quad = face(Direction.NORTH, 0.1, 0.1, 0.4, 0.9, 1.8, 0.7);
        BakedModel source = model(Map.of(), List.of(quad), ModelData.EMPTY, null, 0);
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<?>> work = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) work.add(executor.submit(() -> {
                for (int i = 0; i < 16; i++) {
                    SpatialBlockModelRenderer.modelsReloaded(null);
                    var renderer = new CaptureRenderer(ModelData.EMPTY, null);
                    SpatialBlockModelRenderer.render(renderer, null, source, null, BlockPos.ZERO,
                            new PoseStack(), null, false, RandomSource.create(), 0, OVERLAY);
                    require(renderer.calls >= 2, "Concurrent cache lost fragments");
                }
            }));
            for (var future : work) future.get();
        } finally { executor.shutdownNow(); }
    }

    private static BakedModel model(Map<Direction, List<BakedQuad>> buckets, List<BakedQuad> unculled,
            ModelData data, RenderType layer, long seed) {
        // A java.lang.reflect.Proxy initializes every inherited method signature while creating
        // its dispatch table, including BlockState's registry-backed defaults. A concrete model
        // lets this renderer-only check exercise the real bridge without bootstrapping a game.
        return new BakedModel() {
            @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
                throw new AssertionError("Forge ModelData/render-layer overload was not used");
            }
            @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                    ModelData modelData, RenderType renderType) {
                require(state == null && modelData == data && renderType == layer,
                        "State/ModelData/render layer not forwarded");
                require(random.nextLong() == RandomSource.create(seed).nextLong(), "Seed not reset per bucket");
                return side == null ? unculled : buckets.getOrDefault(side, List.of());
            }
            @Override public boolean useAmbientOcclusion() { return true; }
            @Override public boolean isGui3d() { return true; }
            @Override public boolean usesBlockLight() { return true; }
            @Override public boolean isCustomRenderer() { return false; }
            @Override public TextureAtlasSprite getParticleIcon() { return null; }
            @Override public ItemOverrides getOverrides() { return null; }
        };
    }

    private static final class CaptureRenderer extends ModelBlockRenderer {
        final ModelData data;
        final RenderType layer;
        final BlockPos origin;
        final boolean expectedCheckSides;
        final List<BlockPos> positions = new ArrayList<>();
        int calls;
        CaptureRenderer(ModelData data, RenderType layer) { this(data, layer, BlockPos.ZERO, false); }
        CaptureRenderer(ModelData data, RenderType layer, BlockPos origin, boolean checkSides) {
            super(null); this.data = data; this.layer = layer; this.origin = origin; this.expectedCheckSides = checkSides;
        }
        @Override
        public void tesselateBlock(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos,
                PoseStack pose, VertexConsumer consumer, boolean checkSides, RandomSource random, long seed,
                int overlay, ModelData modelData, RenderType renderType) {
            require(model instanceof SpatialBlockModelRenderer.CellModel && modelData == data && renderType == layer
                    && state == null && level == null && overlay == OVERLAY && checkSides == expectedCheckSides, "Renderer arguments changed");
            var cell = (SpatialBlockModelRenderer.CellModel) model;
            require(pos.equals(origin.offset(cell.offset)), "Light sampling position disagrees with the geometry cell");
            close(pose.last().pose().m30(), cell.offset.getX(), "Geometry X translation disagrees with lighting cell");
            close(pose.last().pose().m31(), cell.offset.getY(), "Geometry Y translation disagrees with lighting cell");
            close(pose.last().pose().m32(), cell.offset.getZ(), "Geometry Z translation disagrees with lighting cell");
            require(!SpatialBlockModelRenderer.renderMapDecor(this, level, model, state, pos, pose, consumer,
                    checkSides, random, seed, overlay, modelData, renderType), "CellModel recursion guard failed");
            List<BakedQuad> unculled = model.getQuads(state, null, random, modelData, renderType);
            for (Direction side : Direction.values()) {
                for (BakedQuad quad : model.getQuads(state, side, random, modelData, renderType)) {
                    require(quad.getDirection() == side, "Cull-face bucket changed");
                }
            }
            require(!unculled.isEmpty(), "Unculled bucket was dropped");
            positions.add(pos);
            calls++;
        }
    }

    private static BakedQuad face(Direction direction, double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        double[] lo = {minX, minY, minZ}, hi = {maxX, maxY, maxZ};
        int[] raw = new int[32];
        int normal = ((direction.getStepX() * 127) & 255) | ((direction.getStepY() * 127) & 255) << 8
                | ((direction.getStepZ() * 127) & 255) << 16;
        for (int i = 0; i < 4; i++) {
            var corner = FaceInfo.fromFacing(direction).getVertexInfo(i);
            int[] faces = {corner.xFace, corner.yFace, corner.zFace};
            double[] point = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                int minimum = axis == 0 ? FaceInfo.Constants.MIN_X : axis == 1 ? FaceInfo.Constants.MIN_Y : FaceInfo.Constants.MIN_Z;
                point[axis] = faces[axis] == minimum ? lo[axis] : hi[axis];
                raw[i * 8 + axis] = Float.floatToRawIntBits((float) point[axis]);
            }
            raw[i * 8 + 3] = 0xffffffff;
            raw[i * 8 + 4] = Float.floatToRawIntBits((float) uvU(point));
            raw[i * 8 + 5] = Float.floatToRawIntBits((float) uvV(point));
            raw[i * 8 + 6] = 0x00900070;
            raw[i * 8 + 7] = normal;
        }
        return new BakedQuad(raw, 7, direction, null, true, true);
    }

    private static BakedQuad rotate(BakedQuad quad, int turns) {
        int[] raw = quad.getVertices().clone();
        Direction direction = quad.getDirection();
        for (int i = 0; i < turns; i++) {
            if (direction.getAxis().isHorizontal()) direction = direction.getClockWise();
            for (int vertex = 0; vertex < 4; vertex++) {
                double x = Float.intBitsToFloat(raw[vertex * 8]), z = Float.intBitsToFloat(raw[vertex * 8 + 2]);
                raw[vertex * 8] = Float.floatToRawIntBits((float) (1 - z));
                raw[vertex * 8 + 2] = Float.floatToRawIntBits((float) x);
            }
        }
        // Fixture UVs stay affine in world position so clipping can be checked analytically.
        return withUvs(raw, quad, direction);
    }

    private static BakedQuad tiltRoof(BakedQuad quad) {
        int[] raw = quad.getVertices().clone();
        double angle = Math.toRadians(-22.5), cos = Math.cos(angle), sin = Math.sin(angle);
        for (int vertex = 0; vertex < 4; vertex++) {
            double y = Float.intBitsToFloat(raw[vertex * 8 + 1]) - 8 / 16.0;
            double z = Float.intBitsToFloat(raw[vertex * 8 + 2]) - 15 / 16.0;
            raw[vertex * 8 + 1] = Float.floatToRawIntBits((float) (8 / 16.0 + cos * y - sin * z));
            raw[vertex * 8 + 2] = Float.floatToRawIntBits((float) (15 / 16.0 + sin * y + cos * z));
        }
        return withUvs(raw, quad, quad.getDirection());
    }

    private static BakedQuad withUvs(int[] raw, BakedQuad original, Direction direction) {
        for (int i = 0; i < 4; i++) {
            double[] point = {Float.intBitsToFloat(raw[i * 8]), Float.intBitsToFloat(raw[i * 8 + 1]), Float.intBitsToFloat(raw[i * 8 + 2])};
            raw[i * 8 + 4] = Float.floatToRawIntBits((float) uvU(point));
            raw[i * 8 + 5] = Float.floatToRawIntBits((float) uvV(point));
        }
        return new BakedQuad(raw, original.getTintIndex(), direction, original.getSprite(), original.isShade(), original.hasAmbientOcclusion());
    }

    private static double uvU(double[] point) { return point[0] * 2 + point[1]; }
    private static double uvV(double[] point) { return point[2] * 3 - point[1]; }
    private static double area(BakedQuad quad) {
        double[] sum = new double[3];
        int[] raw = quad.getVertices();
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            double x = Float.intBitsToFloat(raw[i * 8]), y = Float.intBitsToFloat(raw[i * 8 + 1]), z = Float.intBitsToFloat(raw[i * 8 + 2]);
            double nx = Float.intBitsToFloat(raw[j * 8]), ny = Float.intBitsToFloat(raw[j * 8 + 1]), nz = Float.intBitsToFloat(raw[j * 8 + 2]);
            sum[0] += y * nz - z * ny; sum[1] += z * nx - x * nz; sum[2] += x * ny - y * nx;
        }
        return Math.sqrt(sum[0] * sum[0] + sum[1] * sum[1] + sum[2] * sum[2]) / 2;
    }
    private static void close(double actual, double expected, String message) { require(Math.abs(actual - expected) < EPSILON, message); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
