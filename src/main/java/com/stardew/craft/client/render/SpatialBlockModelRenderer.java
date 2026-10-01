package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Renders multi-cell JSON models with block-local AO and lightmap coordinates. Vanilla AO
 * interpolates within [0, 1]; feeding it oversized vertices extrapolates brightness and can
 * wrap vertex colors. Cache cell-local fragments, then let the normal renderer light each cell.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpatialBlockModelRenderer {
    private static final double EPSILON = 1.0e-6;
    private static final Map<BakedModel, Map<BlockState, List<CellModel>>> CACHE = new IdentityHashMap<>();
    private SpatialBlockModelRenderer() {}

    @SubscribeEvent
    public static void modelsReloaded(ModelEvent.BakingCompleted event) {
        CACHE.clear();
    }

    public static void render(ModelBlockRenderer renderer, BlockAndTintGetter level, BakedModel model,
            BlockState state, BlockPos pos, PoseStack pose, VertexConsumer vertices, boolean checkSides,
            RandomSource random, long seed, int overlay) {
        // These machine renderers use static blockstate models and seed zero. Dynamic addon models
        // retain their per-render getQuads result; never cache an inventory/world-dependent result.
        List<CellModel> cells = model instanceof IDynamicBakedModel || seed != 0
                ? partition(model, state, random, seed)
                : CACHE.computeIfAbsent(model, ignored -> new IdentityHashMap<>())
                    .computeIfAbsent(state, ignored -> partition(model, state, random, seed));
        if (cells.isEmpty()) return;
        if (cells.size() == 1 && cells.getFirst().offset.equals(BlockPos.ZERO)) {
            renderer.tesselateBlock(level, model, state, pos, pose, vertices, checkSides, random, seed, overlay, ModelData.EMPTY, null);
            return;
        }
        for (CellModel cell : cells) {
            pose.pushPose();
            try {
                pose.translate(cell.offset.getX(), cell.offset.getY(), cell.offset.getZ());
                renderer.tesselateBlock(level, cell, state, pos.offset(cell.offset), pose, vertices,
                        checkSides, random, seed, overlay, ModelData.EMPTY, null);
            } finally {
                pose.popPose();
            }
        }
    }

    static List<CellModel> partition(BakedModel model, BlockState state, RandomSource random, long seed) {
        Map<BlockPos, Map<Direction, List<BakedQuad>>> cells = new LinkedHashMap<>();
        // null is the unculled face bucket. Preserve authored cull-face selection in each cell.
        for (int sideIndex = -1; sideIndex < Direction.values().length; sideIndex++) {
            Direction side = sideIndex < 0 ? null : Direction.values()[sideIndex];
            random.setSeed(seed);
            for (BakedQuad quad : model.getQuads(state, side, random, ModelData.EMPTY, null)) {
                for (Fragment fragment : split(quad)) {
                    cells.computeIfAbsent(fragment.offset, ignored -> new LinkedHashMap<>())
                            .computeIfAbsent(side, ignored -> new ArrayList<>()).add(fragment.quad);
                }
            }
        }
        List<CellModel> result = new ArrayList<>();
        cells.forEach((offset, faces) -> result.add(new CellModel(model, offset, faces)));
        return List.copyOf(result);
    }

    /** Clipping changes neither the silhouette nor UV scale; new vertices interpolate original UVs. */
    static List<Fragment> split(BakedQuad quad) {
        int[] raw = quad.getVertices();
        int stride = raw.length / 4;
        List<double[]> polygon = new ArrayList<>(4);
        double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int i = 0; i < 4; i++) {
            double[] vertex = new double[9];
            for (int axis = 0; axis < 3; axis++) {
                vertex[axis] = Float.intBitsToFloat(raw[i * stride + axis]);
                min[axis] = Math.min(min[axis], vertex[axis]);
                max[axis] = Math.max(max[axis], vertex[axis]);
            }
            vertex[3] = Float.intBitsToFloat(raw[i * stride + 4]);
            vertex[4] = Float.intBitsToFloat(raw[i * stride + 5]);
            vertex[5 + i] = 1; // Source-vertex weights for color, light and normal interpolation.
            polygon.add(vertex);
        }
        int[] lo = new int[3], hi = new int[3];
        int[] normal = {quad.getDirection().getStepX(), quad.getDirection().getStepY(), quad.getDirection().getStepZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (max[axis] - min[axis] < EPSILON) {
                lo[axis] = hi[axis] = (int)Math.floor(min[axis] - normal[axis] * EPSILON);
            } else {
                lo[axis] = (int)Math.floor(min[axis] + EPSILON);
                hi[axis] = (int)Math.ceil(max[axis] - EPSILON) - 1;
            }
        }
        List<Fragment> result = new ArrayList<>();
        for (int x = lo[0]; x <= hi[0]; x++) for (int y = lo[1]; y <= hi[1]; y++) for (int z = lo[2]; z <= hi[2]; z++) {
            BlockPos offset = new BlockPos(x, y, z);
            List<double[]> clipped = polygon;
            int[] cell = {x, y, z};
            for (int axis = 0; axis < 3 && !clipped.isEmpty(); axis++) {
                clipped = clip(clipped, axis, cell[axis], true);
                clipped = clip(clipped, axis, cell[axis] + 1, false);
            }
            if (clipped.size() < 3 || area(clipped) < EPSILON) continue;
            if (clipped.size() == 4) {
                result.add(new Fragment(offset, fragment(quad, clipped, offset)));
            } else {
                for (int i = 1; i + 1 < clipped.size(); i++) {
                    var triangle = List.of(clipped.getFirst(), clipped.get(i), clipped.get(i + 1), clipped.get(i + 1));
                    if (area(triangle) >= EPSILON) result.add(new Fragment(offset, fragment(quad, triangle, offset)));
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<double[]> clip(List<double[]> polygon, int axis, double plane, boolean above) {
        List<double[]> result = new ArrayList<>();
        if (polygon.isEmpty()) return result;
        double[] previous = polygon.getLast();
        boolean previousInside = above ? previous[axis] >= plane - EPSILON : previous[axis] <= plane + EPSILON;
        for (double[] current : polygon) {
            boolean inside = above ? current[axis] >= plane - EPSILON : current[axis] <= plane + EPSILON;
            if (inside != previousInside) {
                double t = (plane - previous[axis]) / (current[axis] - previous[axis]);
                double[] edge = new double[current.length];
                for (int i = 0; i < edge.length; i++) edge[i] = previous[i] + t * (current[i] - previous[i]);
                edge[axis] = plane;
                addDistinct(result, edge);
            }
            if (inside) addDistinct(result, current);
            previous = current;
            previousInside = inside;
        }
        if (result.size() > 1 && samePosition(result.getFirst(), result.getLast())) result.removeLast();
        return result;
    }
    private static void addDistinct(List<double[]> result, double[] vertex) {
        if (result.isEmpty() || !samePosition(result.getLast(), vertex)) result.add(vertex);
    }
    private static boolean samePosition(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < EPSILON && Math.abs(a[1] - b[1]) < EPSILON && Math.abs(a[2] - b[2]) < EPSILON;
    }
    private static double area(List<double[]> polygon) {
        double x = 0, y = 0, z = 0;
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            x += a[1] * b[2] - a[2] * b[1];
            y += a[2] * b[0] - a[0] * b[2];
            z += a[0] * b[1] - a[1] * b[0];
        }
        return Math.sqrt(x * x + y * y + z * z) * 0.5;
    }
    // Vanilla AO assigns brightness by FaceInfo corner order. Clipping can change the first
    // vertex, so restore the cyclic order without changing winding or detaching UVs.
    private static List<double[]> order(List<double[]> polygon, Direction direction) {
        var corner = FaceInfo.fromFacing(direction).getVertexInfo(0);
        int[] signs = {
            corner.xFace == FaceInfo.Constants.MIN_X ? -1 : 1,
            corner.yFace == FaceInfo.Constants.MIN_Y ? -1 : 1,
            corner.zFace == FaceInfo.Constants.MIN_Z ? -1 : 1
        };
        int first = 0;
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < polygon.size(); i++) {
            double[] vertex = polygon.get(i);
            double score = vertex[0] * signs[0] + vertex[1] * signs[1] + vertex[2] * signs[2];
            if (score > best) { best = score; first = i; }
        }
        List<double[]> ordered = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) ordered.add(polygon.get((first + i) % 4));
        return ordered;
    }

    private static int interpolatePacked(int[] raw, int stride, int attribute, double[] vertex, int bits) {
        int result = 0;
        int mask = (1 << bits) - 1;
        for (int shift = 0; shift < 32; shift += bits) {
            double channel = 0;
            for (int source = 0; source < 4; source++) {
                channel += ((raw[source * stride + attribute] >>> shift) & mask) * vertex[5 + source];
            }
            result |= ((int)Math.round(channel) & mask) << shift;
        }
        return result;
    }

    private static int interpolateNormal(int[] raw, int stride, double[] vertex) {
        if (raw[7] == raw[stride + 7] && raw[7] == raw[2 * stride + 7] && raw[7] == raw[3 * stride + 7]) return raw[7];
        double[] normal = new double[3];
        double length = 0;
        for (int axis = 0; axis < 3; axis++) {
            for (int source = 0; source < 4; source++) {
                normal[axis] += (byte)(raw[source * stride + 7] >>> (axis * 8)) * vertex[5 + source];
            }
            length += normal[axis] * normal[axis];
        }
        length = Math.sqrt(length);
        int result = 0;
        if (length > EPSILON) {
            for (int axis = 0; axis < 3; axis++) {
                result |= ((int)Math.round(normal[axis] / length * 127) & 255) << (axis * 8);
            }
        }
        return result;
    }

    private static BakedQuad fragment(BakedQuad original, List<double[]> polygon, BlockPos offset) {
        polygon = order(polygon, original.getDirection());
        int[] raw = original.getVertices();
        int stride = raw.length / 4;
        int[] data = raw.clone();
        for (int i = 0; i < 4; i++) {
            double[] vertex = polygon.get(i);
            data[i * stride] = Float.floatToRawIntBits((float)(vertex[0] - offset.getX()));
            data[i * stride + 1] = Float.floatToRawIntBits((float)(vertex[1] - offset.getY()));
            data[i * stride + 2] = Float.floatToRawIntBits((float)(vertex[2] - offset.getZ()));
            data[i * stride + 4] = Float.floatToRawIntBits((float)vertex[3]);
            data[i * stride + 5] = Float.floatToRawIntBits((float)vertex[4]);
            data[i * stride + 3] = interpolatePacked(raw, stride, 3, vertex, 8);
            data[i * stride + 6] = interpolatePacked(raw, stride, 6, vertex, 16);
            data[i * stride + 7] = interpolateNormal(raw, stride, vertex);
        }
        return new BakedQuad(data, original.getTintIndex(), original.getDirection(), original.getSprite(),
                original.isShade(), original.hasAmbientOcclusion());
    }
    record Fragment(BlockPos offset, BakedQuad quad) {}
    static final class CellModel extends BakedModelWrapper<BakedModel> {
        final BlockPos offset;
        private final Map<Direction, List<BakedQuad>> faces;
        CellModel(BakedModel original, BlockPos offset, Map<Direction, List<BakedQuad>> faces) {
            super(original);
            this.offset = offset;
            this.faces = faces;
        }
        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return faces.getOrDefault(side, List.of());
        }
        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
            return getQuads(state, side, random);
        }
    }
}
