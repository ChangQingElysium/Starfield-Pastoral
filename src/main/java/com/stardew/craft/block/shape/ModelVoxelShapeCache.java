package com.stardew.craft.block.shape;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelVoxelShapeCache {
    private static final Map<String, VoxelShape> SHAPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, GeoModelData> GEO_MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, VariantModelRef>> BLOCKSTATE_VARIANTS_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<String>> SHAPES_LOADING = ThreadLocal.withInitial(HashSet::new);

    private ModelVoxelShapeCache() {
    }

    /**
     * 清理所有缓存的 VoxelShape / GeoModel / Blockstate 数据。
     * 在客户端断开连接或资源重载时调用。
     */
    public static void clearAll() {
        SHAPE_CACHE.clear();
        GEO_MODEL_CACHE.clear();
        BLOCKSTATE_VARIANTS_CACHE.clear();
        SHAPES_LOADING.remove();
    }

    @SuppressWarnings("null")
    public static VoxelShape shapeFromModelId(String modelId) {
        VoxelShape cached = SHAPE_CACHE.get(modelId);
        if (cached != null) {
            return cached;
        }

        Set<String> loading = SHAPES_LOADING.get();
        if (!loading.add(modelId)) {
            return Shapes.empty();
        }
        try {
            // Model loading may recursively resolve a parent model. ConcurrentHashMap.computeIfAbsent
            // cannot safely update the same map from inside its mapping function and throws
            // IllegalStateException("Recursive update") while block-state caches are baked.
            VoxelShape loaded = loadShapeFromModelId(modelId);
            VoxelShape raced = SHAPE_CACHE.putIfAbsent(modelId, loaded);
            return raced == null ? loaded : raced;
        } finally {
            loading.remove(modelId);
            if (loading.isEmpty()) {
                SHAPES_LOADING.remove();
            }
        }
    }

    @SuppressWarnings("null")
    public static VoxelShape shape(String modelId) {
        return shapeFromModelId(modelId);
    }

    public static String variantModel(String blockId, String variantKey) {
        VariantModelRef ref = variantRef(blockId, variantKey);
        return ref == null ? null : ref.modelId();
    }

    @SuppressWarnings("null")
    public static VoxelShape variantShape(String blockId, String variantKey) {
        VariantModelRef ref = variantRef(blockId, variantKey);
        if (ref == null || ref.modelId() == null || ref.modelId().isBlank()) {
            return Shapes.empty();
        }

        VoxelShape shape = shapeFromModelId(ref.modelId());
        if (shape.isEmpty()) {
            return shape;
        }

        if (ref.xRotation() % 90 == 0) {
            shape = rotateX(shape, ref.xRotation() / 90);
        }
        if (ref.yRotation() % 90 == 0) {
            shape = rotateY(shape, ref.yRotation() / 90);
        }
        return shape.optimize();
    }

    private static VariantModelRef variantRef(String blockId, String variantKey) {
        Map<String, VariantModelRef> variants = BLOCKSTATE_VARIANTS_CACHE.computeIfAbsent(blockId, ModelVoxelShapeCache::loadBlockstateVariants);
        return variants.get(variantKey);
    }

    @SuppressWarnings("null")
    public static VoxelShape[] horizontalShapes(String modelId, Direction baseFacing) {
        VoxelShape[] out = new VoxelShape[4];
        VoxelShape base = shapeFromModelId(modelId);

        int baseIndex = horizontalIndex(baseFacing);
        out[baseIndex] = base;
        for (int i = 0; i < 4; i++) {
            if (out[i] != null) {
                continue;
            }
            int turns = i - baseIndex;
            if (turns < 0) {
                turns += 4;
            }
            out[i] = rotateY(base, turns).optimize();
        }
        return out;
    }

    public static int horizontalIndex(Direction direction) {
        return switch (direction) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    @SuppressWarnings("null")
    public static VoxelShape rotateY(VoxelShape shape, int quarterTurnsClockwise) {
        int turns = ((quarterTurnsClockwise % 4) + 4) % 4;
        if (turns == 0 || shape.isEmpty()) {
            return shape;
        }
        final VoxelShape[] holder = new VoxelShape[] { Shapes.empty() };
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            double rMinX = minX;
            double rMinZ = minZ;
            double rMaxX = maxX;
            double rMaxZ = maxZ;
            switch (turns) {
                case 1 -> {
                    rMinX = 1.0 - maxZ;
                    rMaxX = 1.0 - minZ;
                    rMinZ = minX;
                    rMaxZ = maxX;
                }
                case 2 -> {
                    rMinX = 1.0 - maxX;
                    rMaxX = 1.0 - minX;
                    rMinZ = 1.0 - maxZ;
                    rMaxZ = 1.0 - minZ;
                }
                case 3 -> {
                    rMinX = minZ;
                    rMaxX = maxZ;
                    rMinZ = 1.0 - maxX;
                    rMaxZ = 1.0 - minX;
                }
                default -> {
                }
            }
            holder[0] = Shapes.or(holder[0], Shapes.box(rMinX, minY, rMinZ, rMaxX, maxY, rMaxZ));
        });
        return holder[0];
    }

    @SuppressWarnings("null")
    public static VoxelShape rotateX(VoxelShape shape, int quarterTurnsClockwise) {
        int turns = ((quarterTurnsClockwise % 4) + 4) % 4;
        if (turns == 0 || shape.isEmpty()) {
            return shape;
        }
        final VoxelShape[] holder = new VoxelShape[] { Shapes.empty() };
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            double rMinY = minY;
            double rMinZ = minZ;
            double rMaxY = maxY;
            double rMaxZ = maxZ;
            switch (turns) {
                case 1 -> {
                    rMinY = 1.0 - maxZ;
                    rMaxY = 1.0 - minZ;
                    rMinZ = minY;
                    rMaxZ = maxY;
                }
                case 2 -> {
                    rMinY = 1.0 - maxY;
                    rMaxY = 1.0 - minY;
                    rMinZ = 1.0 - maxZ;
                    rMaxZ = 1.0 - minZ;
                }
                case 3 -> {
                    rMinY = minZ;
                    rMaxY = maxZ;
                    rMinZ = 1.0 - maxY;
                    rMaxZ = 1.0 - minY;
                }
                default -> {
                }
            }
            holder[0] = Shapes.or(holder[0], Shapes.box(minX, rMinY, rMinZ, maxX, rMaxY, rMaxZ));
        });
        return holder[0];
    }

    @SuppressWarnings("null")
    private static VoxelShape loadShapeFromModelId(String modelId) {
        if (isGeoAabbShapeModelId(modelId)) {
            VoxelShape geoAabbShape = loadAabbShapeFromGeoModelId(modelId);
            if (!geoAabbShape.isEmpty()) {
                return geoAabbShape.optimize();
            }
        }
        if (isGeoShapeModelId(modelId)) {
            VoxelShape geoShape = loadShapeFromGeoModelId(modelId);
            if (!geoShape.isEmpty()) {
                return geoShape.optimize();
            }
        }

        ModelResolve resolved = normalizeModelIdForShape(modelId);
        String resolvedModelId = resolved.modelId();
        JsonObject model = readModel(resolveModelPath(resolvedModelId));
        if (model == null) {
            return Shapes.empty();
        }

        String parent = model.has("parent") ? model.get("parent").getAsString() : null;
        if (isCrossParent(parent)) {
            VoxelShape crossShape = shapeFromCrossTexture(model);
            if (!crossShape.isEmpty()) {
                if (resolved.shiftDownOneBlock()) {
                    crossShape = offsetY(crossShape, -1.0);
                }
                return crossShape.optimize();
            }
        }

        VoxelShape shape = Shapes.empty();
        JsonArray elements = model.getAsJsonArray("elements");
        if ((elements == null || elements.isEmpty()) && parent != null && !isCrossParent(parent)) {
            VoxelShape parentShape = shapeFromModelId(parent);
            if (resolved.shiftDownOneBlock()) {
                parentShape = offsetY(parentShape, -1.0);
            }
            return parentShape;
        }
        if (elements != null) {
            for (JsonElement element : elements) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                JsonArray from = obj.getAsJsonArray("from");
                JsonArray to = obj.getAsJsonArray("to");
                if (from == null || to == null || from.size() < 3 || to.size() < 3) {
                    continue;
                }

                double minX = from.get(0).getAsDouble();
                double minY = from.get(1).getAsDouble();
                double minZ = from.get(2).getAsDouble();
                double maxX = to.get(0).getAsDouble();
                double maxY = to.get(1).getAsDouble();
                double maxZ = to.get(2).getAsDouble();

                JsonObject rotation = obj.getAsJsonObject("rotation");
                if (rotation != null) {
                    Bounds rotated = rotatedBounds(minX, minY, minZ, maxX, maxY, maxZ, rotation);
                    minX = rotated.minX;
                    minY = rotated.minY;
                    minZ = rotated.minZ;
                    maxX = rotated.maxX;
                    maxY = rotated.maxY;
                    maxZ = rotated.maxZ;
                }

                if (maxX <= minX) {
                    maxX = minX + 0.0625;
                }
                if (maxY <= minY) {
                    maxY = minY + 0.0625;
                }
                if (maxZ <= minZ) {
                    maxZ = minZ + 0.0625;
                }

                shape = Shapes.or(shape, Block.box(minX, minY, minZ, maxX, maxY, maxZ));
            }
        }

        if (shape.isEmpty()) {
            return Shapes.empty();
        }
        if (resolved.shiftDownOneBlock()) {
            shape = offsetY(shape, -1.0);
        }
        return shape.optimize();
    }

    private static boolean isGeoShapeModelId(String modelId) {
        return modelId != null && stripGeoShapeVariant(modelId).endsWith(".geo.json");
    }

    private static boolean isGeoAabbShapeModelId(String modelId) {
        return modelId != null && modelId.endsWith(".geo.json#aabb");
    }

    private static String stripGeoShapeVariant(String modelId) {
        return modelId != null && modelId.endsWith("#aabb") ? modelId.substring(0, modelId.length() - "#aabb".length()) : modelId;
    }

    public static GeoBounds geoBoundsFromModelId(String modelId) {
        if (!isGeoShapeModelId(modelId)) {
            return null;
        }
        GeoModelData data = GEO_MODEL_CACHE.computeIfAbsent(stripGeoShapeVariant(modelId), ModelVoxelShapeCache::loadGeoModelDataFromModelId);
        return data == null ? null : data.bounds();
    }

    @SuppressWarnings("null")
    private static VoxelShape loadAabbShapeFromGeoModelId(String modelId) {
        GeoModelData data = GEO_MODEL_CACHE.computeIfAbsent(stripGeoShapeVariant(modelId), ModelVoxelShapeCache::loadGeoModelDataFromModelId);
        if (data == null || data.bounds().isEmpty()) {
            return Shapes.empty();
        }
        GeoBounds bounds = data.bounds();
        return Block.box(bounds.minX() + 8.0D, bounds.minY(), bounds.minZ() + 8.0D,
            bounds.maxX() + 8.0D, bounds.maxY(), bounds.maxZ() + 8.0D);
    }

    @SuppressWarnings("null")
    private static VoxelShape loadShapeFromGeoModelId(String modelId) {
        GeoModelData data = GEO_MODEL_CACHE.computeIfAbsent(stripGeoShapeVariant(modelId), ModelVoxelShapeCache::loadGeoModelDataFromModelId);
        if (data == null || data.shape().isEmpty()) {
            return Shapes.empty();
        }
        return data.shape();
    }

    @SuppressWarnings("null")
    private static GeoModelData loadGeoModelDataFromModelId(String modelId) {
        JsonObject geoRoot = readGeo(resolveGeoPath(modelId));
        if (geoRoot == null) {
            return GeoModelData.empty();
        }

        JsonArray geometries = geoRoot.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty() || !geometries.get(0).isJsonObject()) {
            return GeoModelData.empty();
        }

        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonArray bonesArray = geometry.getAsJsonArray("bones");
        if (bonesArray == null || bonesArray.isEmpty()) {
            return GeoModelData.empty();
        }

        Map<String, JsonObject> bones = new ConcurrentHashMap<>();
        for (JsonElement boneEl : bonesArray) {
            if (!boneEl.isJsonObject()) {
                continue;
            }
            JsonObject bone = boneEl.getAsJsonObject();
            if (!bone.has("name")) {
                continue;
            }
            bones.put(bone.get("name").getAsString(), bone);
        }

        if (bones.isEmpty()) {
            return GeoModelData.empty();
        }

        Map<String, double[][]> worldTransformCache = new ConcurrentHashMap<>();

        boolean hasCube = false;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        Set<Long> occupiedVoxels = new HashSet<>();

        for (JsonObject bone : bones.values()) {
            String boneName = bone.get("name").getAsString();
            double[][] boneWorld = resolveBoneWorldTransform(boneName, bones, worldTransformCache);
            double[] bonePivot = jsonArrayToVec3(bone.getAsJsonArray("pivot"), 0.0, 0.0, 0.0);

            JsonArray cubes = bone.getAsJsonArray("cubes");
            if (cubes == null) {
                continue;
            }

            for (JsonElement cubeEl : cubes) {
                if (!cubeEl.isJsonObject()) {
                    continue;
                }
                JsonObject cube = cubeEl.getAsJsonObject();
                JsonArray originArray = cube.getAsJsonArray("origin");
                JsonArray sizeArray = cube.getAsJsonArray("size");
                if (originArray == null || sizeArray == null || originArray.size() < 3 || sizeArray.size() < 3) {
                    continue;
                }

                double inflate = cube.has("inflate") ? cube.get("inflate").getAsDouble() : 0.0;
                double oX = originArray.get(0).getAsDouble() - inflate;
                double oY = originArray.get(1).getAsDouble() - inflate;
                double oZ = originArray.get(2).getAsDouble() - inflate;
                double tX = originArray.get(0).getAsDouble() + sizeArray.get(0).getAsDouble() + inflate;
                double tY = originArray.get(1).getAsDouble() + sizeArray.get(1).getAsDouble() + inflate;
                double tZ = originArray.get(2).getAsDouble() + sizeArray.get(2).getAsDouble() + inflate;

                double[] cubePivot = jsonArrayToVec3(cube.getAsJsonArray("pivot"), bonePivot[0], bonePivot[1], bonePivot[2]);
                double[] cubeRotation = jsonArrayToVec3(cube.getAsJsonArray("rotation"), 0.0, 0.0, 0.0);
                double[][] cubeTransform = multiplyMatrices(boneWorld, pivotRotationMatrix(cubePivot, cubeRotation));
                double[][] inverseCubeTransform = invertAffineMatrix(cubeTransform);

                double[][] corners = new double[][] {
                    {oX, oY, oZ}, {oX, oY, tZ}, {oX, tY, oZ}, {oX, tY, tZ},
                    {tX, oY, oZ}, {tX, oY, tZ}, {tX, tY, oZ}, {tX, tY, tZ}
                };

                double cubeMinX = Double.POSITIVE_INFINITY;
                double cubeMinY = Double.POSITIVE_INFINITY;
                double cubeMinZ = Double.POSITIVE_INFINITY;
                double cubeMaxX = Double.NEGATIVE_INFINITY;
                double cubeMaxY = Double.NEGATIVE_INFINITY;
                double cubeMaxZ = Double.NEGATIVE_INFINITY;

                for (double[] c : corners) {
                    double[] world = transformPoint(cubeTransform, c[0], c[1], c[2]);
                    cubeMinX = Math.min(cubeMinX, world[0]);
                    cubeMinY = Math.min(cubeMinY, world[1]);
                    cubeMinZ = Math.min(cubeMinZ, world[2]);
                    cubeMaxX = Math.max(cubeMaxX, world[0]);
                    cubeMaxY = Math.max(cubeMaxY, world[1]);
                    cubeMaxZ = Math.max(cubeMaxZ, world[2]);
                }

                hasCube = true;
                minX = Math.min(minX, cubeMinX);
                minY = Math.min(minY, cubeMinY);
                minZ = Math.min(minZ, cubeMinZ);
                maxX = Math.max(maxX, cubeMaxX);
                maxY = Math.max(maxY, cubeMaxY);
                maxZ = Math.max(maxZ, cubeMaxZ);

                int minVX = (int) Math.floor(cubeMinX + 1.0E-6);
                int minVY = (int) Math.floor(cubeMinY + 1.0E-6);
                int minVZ = (int) Math.floor(cubeMinZ + 1.0E-6);
                int maxVX = (int) Math.ceil(cubeMaxX - 1.0E-6) - 1;
                int maxVY = (int) Math.ceil(cubeMaxY - 1.0E-6) - 1;
                int maxVZ = (int) Math.ceil(cubeMaxZ - 1.0E-6) - 1;

                for (int vx = minVX; vx <= maxVX; vx++) {
                    for (int vy = minVY; vy <= maxVY; vy++) {
                        for (int vz = minVZ; vz <= maxVZ; vz++) {
                            double[] localCenter = transformPoint(inverseCubeTransform, vx + 0.5, vy + 0.5, vz + 0.5);
                            if (localCenter[0] >= oX - 1.0E-6 && localCenter[0] <= tX + 1.0E-6
                                && localCenter[1] >= oY - 1.0E-6 && localCenter[1] <= tY + 1.0E-6
                                && localCenter[2] >= oZ - 1.0E-6 && localCenter[2] <= tZ + 1.0E-6) {
                                occupiedVoxels.add(packVoxel(vx, vy, vz));
                            }
                        }
                    }
                }
            }
        }

        if (!hasCube || occupiedVoxels.isEmpty()) {
            return GeoModelData.empty();
        }

        if (maxX <= minX) {
            maxX = minX + 0.0625D;
        }
        if (maxY <= minY) {
            maxY = minY + 0.0625D;
        }
        if (maxZ <= minZ) {
            maxZ = minZ + 0.0625D;
        }

        VoxelShape shape = Shapes.empty();
        for (long packed : occupiedVoxels) {
            int vx = unpackVoxelX(packed);
            int vy = unpackVoxelY(packed);
            int vz = unpackVoxelZ(packed);
            shape = Shapes.or(shape, Block.box(vx, vy, vz, vx + 1, vy + 1, vz + 1));
        }
        GeoBounds bounds = new GeoBounds(minX, minY, minZ, maxX, maxY, maxZ);
        return new GeoModelData(shape.optimize(), bounds);
    }

    private static long packVoxel(int x, int y, int z) {
        long ux = ((long) x - Integer.MIN_VALUE) & 0x1FFFFFL;
        long uy = ((long) y - Integer.MIN_VALUE) & 0x1FFFFFL;
        long uz = ((long) z - Integer.MIN_VALUE) & 0x1FFFFFL;
        return (ux << 42) | (uy << 21) | uz;
    }

    private static int unpackVoxelX(long packed) {
        return (int) (((packed >>> 42) & 0x1FFFFFL) + Integer.MIN_VALUE);
    }

    private static int unpackVoxelY(long packed) {
        return (int) (((packed >>> 21) & 0x1FFFFFL) + Integer.MIN_VALUE);
    }

    private static int unpackVoxelZ(long packed) {
        return (int) ((packed & 0x1FFFFFL) + Integer.MIN_VALUE);
    }

    private static double[][] invertAffineMatrix(double[][] m) {
        double r00 = m[0][0], r01 = m[0][1], r02 = m[0][2];
        double r10 = m[1][0], r11 = m[1][1], r12 = m[1][2];
        double r20 = m[2][0], r21 = m[2][1], r22 = m[2][2];
        double tx = m[0][3], ty = m[1][3], tz = m[2][3];

        double[][] inv = identityMatrix();
        inv[0][0] = r00;
        inv[0][1] = r10;
        inv[0][2] = r20;
        inv[1][0] = r01;
        inv[1][1] = r11;
        inv[1][2] = r21;
        inv[2][0] = r02;
        inv[2][1] = r12;
        inv[2][2] = r22;

        inv[0][3] = -(inv[0][0] * tx + inv[0][1] * ty + inv[0][2] * tz);
        inv[1][3] = -(inv[1][0] * tx + inv[1][1] * ty + inv[1][2] * tz);
        inv[2][3] = -(inv[2][0] * tx + inv[2][1] * ty + inv[2][2] * tz);
        return inv;
    }

    private static double[][] resolveBoneWorldTransform(String boneName,
                                                        Map<String, JsonObject> bones,
                                                        Map<String, double[][]> cache) {
        double[][] cached = cache.get(boneName);
        if (cached != null) {
            return cached;
        }

        JsonObject bone = bones.get(boneName);
        if (bone == null) {
            return identityMatrix();
        }

        double[] pivot = jsonArrayToVec3(bone.getAsJsonArray("pivot"), 0.0, 0.0, 0.0);
        double[] rotation = jsonArrayToVec3(bone.getAsJsonArray("rotation"), 0.0, 0.0, 0.0);
        double[][] local = pivotRotationMatrix(pivot, rotation);

        double[][] world;
        if (bone.has("parent")) {
            String parentName = bone.get("parent").getAsString();
            double[][] parentWorld = resolveBoneWorldTransform(parentName, bones, cache);
            world = multiplyMatrices(parentWorld, local);
        } else {
            world = local;
        }

        cache.put(boneName, world);
        return world;
    }

    private static double[] jsonArrayToVec3(JsonArray array, double fallbackX, double fallbackY, double fallbackZ) {
        if (array == null || array.size() < 3) {
            return new double[] { fallbackX, fallbackY, fallbackZ };
        }
        return new double[] { array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble() };
    }

    private static double[][] identityMatrix() {
        return new double[][] {
            {1.0, 0.0, 0.0, 0.0},
            {0.0, 1.0, 0.0, 0.0},
            {0.0, 0.0, 1.0, 0.0},
            {0.0, 0.0, 0.0, 1.0}
        };
    }

    private static double[][] multiplyMatrices(double[][] a, double[][] b) {
        double[][] out = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double sum = 0.0;
                for (int k = 0; k < 4; k++) {
                    sum += a[i][k] * b[k][j];
                }
                out[i][j] = sum;
            }
        }
        return out;
    }

    private static double[][] translationMatrix(double x, double y, double z) {
        return new double[][] {
            {1.0, 0.0, 0.0, x},
            {0.0, 1.0, 0.0, y},
            {0.0, 0.0, 1.0, z},
            {0.0, 0.0, 0.0, 1.0}
        };
    }

    private static double[][] rotationXMatrix(double angleDeg) {
        double r = Math.toRadians(angleDeg);
        double c = Math.cos(r);
        double s = Math.sin(r);
        return new double[][] {
            {1.0, 0.0, 0.0, 0.0},
            {0.0, c, -s, 0.0},
            {0.0, s, c, 0.0},
            {0.0, 0.0, 0.0, 1.0}
        };
    }

    private static double[][] rotationYMatrix(double angleDeg) {
        double r = Math.toRadians(angleDeg);
        double c = Math.cos(r);
        double s = Math.sin(r);
        return new double[][] {
            {c, 0.0, s, 0.0},
            {0.0, 1.0, 0.0, 0.0},
            {-s, 0.0, c, 0.0},
            {0.0, 0.0, 0.0, 1.0}
        };
    }

    private static double[][] rotationZMatrix(double angleDeg) {
        double r = Math.toRadians(angleDeg);
        double c = Math.cos(r);
        double s = Math.sin(r);
        return new double[][] {
            {c, -s, 0.0, 0.0},
            {s, c, 0.0, 0.0},
            {0.0, 0.0, 1.0, 0.0},
            {0.0, 0.0, 0.0, 1.0}
        };
    }

    private static double[][] pivotRotationMatrix(double[] pivot, double[] rotation) {
        double[][] translateToPivot = translationMatrix(pivot[0], pivot[1], pivot[2]);
        double[][] rotate = multiplyMatrices(
            multiplyMatrices(rotationXMatrix(rotation[0]), rotationYMatrix(rotation[1])),
            rotationZMatrix(rotation[2])
        );
        double[][] translateBack = translationMatrix(-pivot[0], -pivot[1], -pivot[2]);
        return multiplyMatrices(multiplyMatrices(translateToPivot, rotate), translateBack);
    }

    private static double[] transformPoint(double[][] matrix, double x, double y, double z) {
        double outX = matrix[0][0] * x + matrix[0][1] * y + matrix[0][2] * z + matrix[0][3];
        double outY = matrix[1][0] * x + matrix[1][1] * y + matrix[1][2] * z + matrix[1][3];
        double outZ = matrix[2][0] * x + matrix[2][1] * y + matrix[2][2] * z + matrix[2][3];
        return new double[] { outX, outY, outZ };
    }

    private static ModelResolve normalizeModelIdForShape(String modelId) {
        JsonObject direct = readModel(resolveModelPath(modelId));
        if (direct != null && modelHasUsableShape(direct)) {
            return new ModelResolve(modelId, false);
        }

        if (modelId.contains("_extension")) {
            String fallback = modelId.replace("_extension", "");
            JsonObject fallbackModel = readModel(resolveModelPath(fallback));
            if (fallbackModel != null && modelHasUsableShape(fallbackModel)) {
                return new ModelResolve(fallback, true);
            }
        }
        return new ModelResolve(modelId, false);
    }

    @SuppressWarnings("null")
    public static VoxelShape offsetY(VoxelShape shape, double blockOffsetY) {
        if (shape.isEmpty() || blockOffsetY == 0.0) {
            return shape;
        }
        final VoxelShape[] holder = new VoxelShape[] { Shapes.empty() };
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            holder[0] = Shapes.or(holder[0], Shapes.box(minX, minY + blockOffsetY, minZ, maxX, maxY + blockOffsetY, maxZ));
        });
        return holder[0].optimize();
    }

    private static boolean modelHasUsableShape(JsonObject model) {
        JsonArray elements = model.getAsJsonArray("elements");
        if (elements != null && !elements.isEmpty()) {
            return true;
        }
        String parent = model.has("parent") ? model.get("parent").getAsString() : null;
        return isCrossParent(parent) && hasRenderableTexture(model);
    }

    private static boolean isCrossParent(String parent) {
        if (parent == null || parent.isBlank()) {
            return false;
        }
        return parent.contains("cross");
    }

    private static boolean hasRenderableTexture(JsonObject model) {
        JsonObject textures = model.getAsJsonObject("textures");
        if (textures == null || textures.entrySet().isEmpty()) {
            return false;
        }
        String textureRef = textureReferenceForCross(model);
        return textureRef != null && !textureRef.isBlank();
    }

    private static VoxelShape shapeFromCrossTexture(JsonObject model) {
        String textureRef = textureReferenceForCross(model);
        if (textureRef == null || textureRef.isBlank()) {
            return Shapes.empty();
        }
        String textureId = resolveTextureReference(model.getAsJsonObject("textures"), textureRef);
        if (textureId == null || textureId.isBlank()) {
            return Shapes.empty();
        }

        BufferedImage image = readTexture(textureId);
        if (image == null) {
            return Shapes.empty();
        }

        PixelBounds bounds = findOpaqueBounds(image);
        if (bounds == null) {
            return Shapes.empty();
        }

        double widthPx = Math.max(1.0, bounds.width());
        double heightPx = Math.max(1.0, bounds.height());
        double side = Math.min(16.0, widthPx / Math.sqrt(2.0));
        double min = 8.0 - side / 2.0;
        double max = 8.0 + side / 2.0;
        double top = Math.min(16.0, heightPx);
        return Block.box(min, 0.0, min, max, top, max);
    }

    private static String textureReferenceForCross(JsonObject model) {
        JsonObject textures = model.getAsJsonObject("textures");
        if (textures == null) {
            return null;
        }
        if (textures.has("cross")) {
            return textures.get("cross").getAsString();
        }
        if (textures.has("plant")) {
            return textures.get("plant").getAsString();
        }
        for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
            String key = entry.getKey();
            if ("particle".equals(key)) {
                continue;
            }
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }
        return null;
    }

    private static String resolveTextureReference(JsonObject textures, String textureRef) {
        if (!textureRef.startsWith("#")) {
            return textureRef;
        }
        if (textures == null) {
            return null;
        }

        String key = textureRef.substring(1);
        Set<String> seen = new HashSet<>();
        while (!key.isBlank() && seen.add(key) && textures.has(key)) {
            String value = textures.get(key).getAsString();
            if (!value.startsWith("#")) {
                return value;
            }
            key = value.substring(1);
        }
        return null;
    }

    private static BufferedImage readTexture(String textureId) {
        String texturePath = resolveTexturePath(textureId);
        try (InputStream input = ModelVoxelShapeCache.class.getClassLoader().getResourceAsStream(texturePath)) {
            if (input == null) {
                return null;
            }
            return ImageIO.read(input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String resolveTexturePath(String textureId) {
        String[] split = textureId.split(":", 2);
        String namespace = split.length == 2 ? split[0] : "minecraft";
        String path = split.length == 2 ? split[1] : split[0];
        return "assets/" + namespace + "/textures/" + path + ".png";
    }

    private static PixelBounds findOpaqueBounds(BufferedImage image) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= 4) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < minX || maxY < minY) {
            return null;
        }

        double scaleX = 16.0 / Math.max(1, width);
        double scaleY = 16.0 / Math.max(1, height);
        double scaledWidth = (maxX - minX + 1) * scaleX;
        double scaledHeight = (maxY - minY + 1) * scaleY;
        return new PixelBounds(scaledWidth, scaledHeight);
    }

    private static String resolveModelPath(String modelId) {
        String[] split = modelId.split(":", 2);
        String namespace = split.length == 2 ? split[0] : "minecraft";
        String path = split.length == 2 ? split[1] : split[0];
        return "assets/" + namespace + "/models/" + path + ".json";
    }

    private static String resolveGeoPath(String modelId) {
        String[] split = modelId.split(":", 2);
        String namespace = split.length == 2 ? split[0] : "minecraft";
        String path = split.length == 2 ? split[1] : split[0];
        return "assets/" + namespace + "/" + path;
    }

    private static String resolveBlockstatePath(String blockId) {
        String[] split = blockId.split(":", 2);
        String namespace = split.length == 2 ? split[0] : "minecraft";
        String path = split.length == 2 ? split[1] : split[0];
        return "assets/" + namespace + "/blockstates/" + path + ".json";
    }

    @SuppressWarnings("null")
    private static JsonObject readModel(String classpathPath) {
        try (InputStream input = ModelVoxelShapeCache.class.getClassLoader().getResourceAsStream(classpathPath)) {
            if (input == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("null")
    private static JsonObject readGeo(String classpathPath) {
        try (InputStream input = ModelVoxelShapeCache.class.getClassLoader().getResourceAsStream(classpathPath)) {
            if (input == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, VariantModelRef> loadBlockstateVariants(String blockId) {
        Map<String, VariantModelRef> out = new ConcurrentHashMap<>();
        String blockstatePath = resolveBlockstatePath(blockId);
        try (InputStream input = ModelVoxelShapeCache.class.getClassLoader().getResourceAsStream(blockstatePath)) {
            if (input == null) {
                return out;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();

            // Standard "variants" block
            JsonObject variants = root.getAsJsonObject("variants");
            if (variants != null) {
                for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
                    VariantModelRef ref = parseVariantModelRef(entry.getValue());
                    if (ref != null) {
                        out.put(entry.getKey(), ref);
                    }
                }
            }

            // "multipart" block — synthesize pseudo-variant keys from "when" conditions
            JsonArray multipart = root.getAsJsonArray("multipart");
            if (multipart != null) {
                for (JsonElement part : multipart) {
                    if (!part.isJsonObject()) continue;
                    JsonObject partObj = part.getAsJsonObject();
                    JsonObject when = partObj.getAsJsonObject("when");
                    JsonElement apply = partObj.get("apply");
                    VariantModelRef ref = parseVariantModelRef(apply);
                    if (ref == null) continue;
                    if (when != null) {
                        // Build a canonical variant key like "age=0" or "age=0,half=lower"
                        StringBuilder keyBuilder = new StringBuilder();
                        for (Map.Entry<String, JsonElement> prop : when.entrySet()) {
                            if (keyBuilder.length() > 0) keyBuilder.append(',');
                            keyBuilder.append(prop.getKey()).append('=').append(prop.getValue().getAsString());
                        }
                        out.putIfAbsent(keyBuilder.toString(), ref);
                    }
                }
            }

            return out;
        } catch (Exception ignored) {
            return out;
        }
    }

    private static VariantModelRef parseVariantModelRef(JsonElement value) {
        if (value == null) {
            return null;
        }
        JsonObject variantObj = null;
        if (value.isJsonObject()) {
            variantObj = value.getAsJsonObject();
        } else if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (JsonElement element : array) {
                if (element != null && element.isJsonObject()) {
                    variantObj = element.getAsJsonObject();
                    break;
                }
            }
        }

        if (variantObj == null || !variantObj.has("model")) {
            return null;
        }

        String modelId = variantObj.get("model").getAsString();
        int xRotation = variantObj.has("x") ? variantObj.get("x").getAsInt() : 0;
        int yRotation = variantObj.has("y") ? variantObj.get("y").getAsInt() : 0;
        return new VariantModelRef(modelId, xRotation, yRotation);
    }

    private static Bounds rotatedBounds(double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ,
                                       JsonObject rotation) {
        String axis = rotation.get("axis").getAsString();
        double angle = Math.toRadians(rotation.get("angle").getAsDouble());
        JsonArray origin = rotation.getAsJsonArray("origin");
        double ox = origin.get(0).getAsDouble();
        double oy = origin.get(1).getAsDouble();
        double oz = origin.get(2).getAsDouble();

        double[][] corners = new double[][] {
            { minX, minY, minZ }, { minX, minY, maxZ }, { minX, maxY, minZ }, { minX, maxY, maxZ },
            { maxX, minY, minZ }, { maxX, minY, maxZ }, { maxX, maxY, minZ }, { maxX, maxY, maxZ }
        };

        double outMinX = Double.POSITIVE_INFINITY;
        double outMinY = Double.POSITIVE_INFINITY;
        double outMinZ = Double.POSITIVE_INFINITY;
        double outMaxX = Double.NEGATIVE_INFINITY;
        double outMaxY = Double.NEGATIVE_INFINITY;
        double outMaxZ = Double.NEGATIVE_INFINITY;

        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        for (double[] corner : corners) {
            double x = corner[0] - ox;
            double y = corner[1] - oy;
            double z = corner[2] - oz;
            double rx = x;
            double ry = y;
            double rz = z;

            switch (axis) {
                case "x" -> {
                    ry = y * cos - z * sin;
                    rz = y * sin + z * cos;
                }
                case "y" -> {
                    rx = x * cos + z * sin;
                    rz = -x * sin + z * cos;
                }
                case "z" -> {
                    rx = x * cos - y * sin;
                    ry = x * sin + y * cos;
                }
                default -> {
                }
            }

            rx += ox;
            ry += oy;
            rz += oz;

            outMinX = Math.min(outMinX, rx);
            outMinY = Math.min(outMinY, ry);
            outMinZ = Math.min(outMinZ, rz);
            outMaxX = Math.max(outMaxX, rx);
            outMaxY = Math.max(outMaxY, ry);
            outMaxZ = Math.max(outMaxZ, rz);
        }

        return new Bounds(outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ);
    }

    private record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    private record PixelBounds(double width, double height) {
    }

    private record VariantModelRef(String modelId, int xRotation, int yRotation) {
    }

    private record ModelResolve(String modelId, boolean shiftDownOneBlock) {
    }

    public record GeoBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private static final GeoBounds EMPTY = new GeoBounds(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

        public static GeoBounds empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return maxX <= minX || maxY <= minY || maxZ <= minZ;
        }
    }

    private record GeoModelData(VoxelShape shape, GeoBounds bounds) {
        private static final GeoModelData EMPTY = new GeoModelData(Shapes.empty(), GeoBounds.empty());

        private static GeoModelData empty() {
            return EMPTY;
        }
    }
}
