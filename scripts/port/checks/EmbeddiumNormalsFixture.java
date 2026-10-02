import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.port.PortNormalPose;
import com.stardew.craft.client.render.EmbeddiumQuadAccess;
import com.stardew.craft.client.light.EmbeddiumLightBridge;
import com.stardew.craft.client.light.RingLightRenderer;
import com.stardew.craft.client.light.ColoredLightEngine;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import me.jellysquid.mods.sodium.client.model.light.flat.FlatLightPipeline;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.render.immediate.model.BakedModelEncoder;
import me.jellysquid.mods.sodium.client.render.immediate.model.EntityRenderer;
import me.jellysquid.mods.sodium.client.render.immediate.model.ModelCuboid;
import me.jellysquid.mods.sodium.client.util.ModelQuadUtil;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Runs locked release writers after actual Mixin application, capturing their real native-memory payloads. */
public final class EmbeddiumNormalsFixture {
    public static void main(String[] args) throws Exception {
        checkLightingInterfaceBridges();
        checkActualLightingConsumers();
        checkCachedPoseTrust();
        for (float[] scale : new float[][]{{1, 1, 1}, {2, 3, 4}, {-2, 3, 4}, {2, -3, 4},
                {2, 3, -4}, {2, 2, 2}, {-2, 2, 2}, {-2, -2, -2}}) {
            PoseStack pose = new PoseStack();
            pose.mulPose(new Quaternionf().rotationXYZ(.2F, -.4F, .1F));
            pose.scale(scale[0], scale[1], scale[2]);
            checkQuadWriters(pose.last());
            checkEntityWriter(pose.last());
        }
        PoseStack history = new PoseStack();
        history.scale(2, 3, 4);
        history.scale(.5F, 1 / 3F, .25F);
        checkQuadWriters(history.last());
        checkEntityWriter(history.last());
        System.out.println("Woven Embeddium normals: both packed quad writers (including deprecated delegation), accurate face/vertex fallback,");
        System.out.println("six optimized entity faces/mirroring, negative/nonuniform/history scales, real cached PoseStack trust,");
        System.out.println("real ring-light injected handler and active RGB/moving-light consumers with loadable contracts passed.");
        System.out.println("Boundary: locked 0.3.31 release native-memory payloads after SRG-name resolution; no GL/shader/client visual acceptance.");
    }

    private static void checkLightingInterfaceBridges() {
        ProbeQuad quad = new ProbeQuad();
        var ring = (EmbeddiumQuadAccess.Coordinates) (Object) quad;
        var colored = (EmbeddiumQuadAccess.Lighting) (Object) quad;
        for (Class<?> contract : ModelQuadView.class.getInterfaces()) require(!contract.getName().startsWith("com.stardew.craft.mixin."), "Woven quad inherits a non-loadable ordinary Mixin");
        for (int vertex = 0; vertex < 4; vertex++) {
            require(ring.stardewcraft$getX(vertex) == quad.getX(vertex)
                    && ring.stardewcraft$getY(vertex) == quad.getY(vertex)
                    && ring.stardewcraft$getZ(vertex) == quad.getZ(vertex), "Ring-light interface dispatch failed");
            require(colored.stardewcraft$getLight(vertex) == quad.getLight(vertex)
                    && colored.stardewcraft$getLightFace() == quad.getLightFace(), "RGB-light interface dispatch failed");
        }
    }

    private static void checkActualLightingConsumers() throws Exception {
        // Supply published worker snapshots, not a Minecraft client/world. Sampling and injected
        // consumer bodies remain the real production code; only client-owned constructors are bypassed.
        Object unsafe = reflectedField(Class.forName("sun.misc.Unsafe"), "theUnsafe").get(null);
        var allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
        QuadLightData data = new QuadLightData();
        for (int vertex = 0; vertex < 4; vertex++) data.lm[vertex] = 0x00a00010;
        Field sources = reflectedField(RingLightRenderer.class, "sources");
        Object oldSources = sources.get(null);
        Class<?> sourceType = Class.forName("com.stardew.craft.client.light.RingLightRenderer$Source");
        var sourceConstructor = sourceType.getDeclaredConstructor(Vec3.class, int.class);
        sourceConstructor.setAccessible(true);
        sources.set(null, Map.of(1, sourceConstructor.newInstance(new Vec3(0, 0, 0), 12)));
        try {
            Object pipeline = allocate.invoke(unsafe, FlatLightPipeline.class);
            var injected = java.util.Arrays.stream(FlatLightPipeline.class.getDeclaredMethods())
                    .filter(method -> method.getName().contains("stardewcraft$ringLight")).findFirst().orElseThrow();
            injected.setAccessible(true);
            ProbeQuad quad = new ProbeQuad();
            injected.invoke(pipeline, quad, BlockPos.ZERO, data, null, Direction.NORTH, true,
                    new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("fixture", false));
            for (int vertex = 0; vertex < 4; vertex++) {
                int expected = RingLightRenderer.lightColor(quad.getX(vertex), quad.getY(vertex), quad.getZ(vertex), 0x00a00010);
                require(data.lm[vertex] == expected && data.lm[vertex] > 0x00a00010, "Actual ring-light handler lost coordinates or source light");
            }
        } finally { sources.set(null, oldSources); }

        Field samples = reflectedField(ColoredLightEngine.class, "samples");
        Object oldSamples = samples.get(null);
        Map<Long, Integer> snapshot = new HashMap<>();
        for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++)
            snapshot.put(BlockPos.asLong(x, y, z), 0xe0ff0000);
        samples.set(null, Map.copyOf(snapshot));
        try {
            BlockRenderContext context = (BlockRenderContext) allocate.invoke(unsafe, BlockRenderContext.class);
            reflectedField(BlockRenderContext.class, "pos").set(context, new BlockPos.MutableBlockPos());
            int[] vertices = new int[32];
            ProbeQuad geometry = new ProbeQuad();
            for (int vertex = 0; vertex < 4; vertex++) {
                vertices[vertex * 8] = Float.floatToRawIntBits(geometry.getX(vertex));
                vertices[vertex * 8 + 1] = Float.floatToRawIntBits(geometry.getY(vertex));
                vertices[vertex * 8 + 2] = Float.floatToRawIntBits(geometry.getZ(vertex));
                vertices[vertex * 8 + 6] = 0x00600020;
                data.lm[vertex] = 0x00a00030;
            }
            BakedQuad quad = new BakedQuad(vertices, -1, Direction.NORTH, null, true);
            for (int vertex = 0; vertex < 4; vertex++) {
                int color = EmbeddiumLightBridge.color(context, quad, data, vertex, 0x80604020);
                require(color != 0x80604020 && (color >>> 24) == 0x80 && (color & 255) == 0x20, "Actual RGB consumer did not apply red ABGR tint or changed alpha");
                int light = EmbeddiumLightBridge.light(context, quad, vertex, 0x00a00030);
                require(light == 0x00a000e0, "Actual moving-light consumer changed sky light or lost source emission");
            }
            require(EmbeddiumLightBridge.color(context, new BakedQuad(vertices, -1, Direction.NORTH, null, false), data, 0, 0x80604020) == 0x80604020,
                    "Unshaded quad must preserve its original color");
        } finally { samples.set(null, oldSamples); }
    }

    private static Field reflectedField(Class<?> owner, String name) throws Exception { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }

    private static void checkCachedPoseTrust() {
        PoseStack pose = new PoseStack();
        var cache = (org.embeddedt.embeddium.render.matrix_stack.CachingPoseStack) (Object) pose;
        cache.embeddium$setCachingEnabled(true);
        pose.pushPose();
        PoseStack.Pose cached = pose.last();
        pose.scale(2, 3, 4);
        pose.popPose();
        pose.pushPose();
        require(pose.last() == cached, "The real Embeddium cache overwrite was not executed");
        require(trusted(pose.last()), "Reused pose retained a previous child's untrusted bit");
        pose.popPose();
        pose.scale(2, 3, 4);
        pose.pushPose();
        require(pose.last() == cached && !trusted(pose.last()), "Cached push lost the parent's nonuniform normal history");
        pose.setIdentity();
        require(trusted(pose.last()), "Cached child identity did not restore trust");
        pose.popPose();
        require(!trusted(pose.last()), "Cached child's identity changed parent trust");
        cache.embeddium$setCachingEnabled(false);
    }

    private static void checkQuadWriters(PoseStack.Pose pose) {
        ProbeQuad quad = new ProbeQuad();
        require(quad.getComputedFaceNormal() != pack(0, 0, -1), "Fixture must distinguish geometric normal from lightFace");
        for (int mode = 0; mode < 3; mode++) {
            Capture writer = new Capture();
            float[] brightness = {.6F, .7F, .8F, .9F};
            int[] light = {0x00b00060, 0x00500080, 0x00f00010, 0x000000f0};
            if (mode == 0) BakedModelEncoder.writeQuadVertices(writer, pose, quad, 0xff6f4532, light[0], 4567);
            else if (mode == 1) BakedModelEncoder.writeQuadVertices(writer, pose, quad, .3F, .4F, .5F, .6F, brightness, true, light, 4567);
            else BakedModelEncoder.writeQuadVertices(writer, pose, quad, .3F, .4F, .5F, brightness, false, light, 4567);
            require(writer.pushes == 1 && writer.count == 4, "Quad writer duplicated or dropped vertices");
            for (int vertex = 0; vertex < 4; vertex++) {
                int packed = quad.getForgeNormal(vertex);
                if (packed == 0) packed = quad.getComputedFaceNormal();
                require(writer.normal(vertex) == baselinePacked(pose.normal(), trusted(pose), packed),
                        "Packed vertex normal differs from Sodium 0.6.13 in writer " + mode + ", vertex " + vertex);
                Vector3f position = pose.pose().transformPosition(new Vector3f(quad.getX(vertex), quad.getY(vertex), quad.getZ(vertex)));
                close(writer.data.getFloat(vertex * 36), position.x, "Position X changed");
                close(writer.data.getFloat(vertex * 36 + 4), position.y, "Position Y changed");
                close(writer.data.getFloat(vertex * 36 + 8), position.z, "Position Z changed");
                require(writer.data.getFloat(vertex * 36 + 16) == quad.getTexU(vertex)
                        && writer.data.getFloat(vertex * 36 + 20) == quad.getTexV(vertex), "Quad UV changed");
                require(writer.data.getInt(vertex * 36 + 24) == 4567, "Quad overlay changed");
                require(writer.data.getInt(vertex * 36 + 28) == ModelQuadUtil.mergeBakedLight(quad.getLight(vertex), light[mode == 0 ? 0 : vertex]), "Quad light merge changed");
            }
        }
    }

    private static void checkEntityWriter(PoseStack.Pose pose) throws Exception {
        EntityRenderer.prepareNormals(pose);
        int[] original = (int[]) field("CUBE_NORMALS").get(null);
        int[] mirrored = (int[]) field("CUBE_NORMALS_MIRRORED").get(null);
        Direction[] directions = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (int face = 0; face < 6; face++) {
            int expected = baselineDirection(pose.normal(), trusted(pose), directions[face]);
            require(original[face] == expected, "Optimized entity face skipped normalization: " + directions[face]);
            require(mirrored[face] == original[face == 4 ? 5 : face == 5 ? 4 : face], "Mirrored face mapping changed");
        }
        for (boolean mirror : new boolean[]{false, true}) {
            Capture writer = new Capture();
            ModelCuboid cube = new ModelCuboid(0, 0, 0, 0, 0, 16, 16, 16, 0, 0, 0, mirror, 32, 32, EnumSet.allOf(Direction.class));
            EntityRenderer.renderCuboidFast(pose, writer, cube, 1234, 5678, 0x80604020);
            require(writer.count == 24 && writer.pushes == 1, "Optimized cube lost faces");
            for (int vertex = 0; vertex < 24; vertex++) {
                require(writer.normal(vertex) == (mirror ? mirrored : original)[vertex / 4], "Entity writer did not consume the corrected face normal");
                require(writer.data.getInt(vertex * 36 + 12) == 0x80604020
                        && writer.data.getInt(vertex * 36 + 24) == 5678 && writer.data.getInt(vertex * 36 + 28) == 1234,
                        "Entity color, overlay or light changed");
            }
        }
    }

    private static Field field(String name) throws Exception { Field field = EntityRenderer.class.getDeclaredField(name); field.setAccessible(true); return field; }
    private static boolean trusted(PoseStack.Pose pose) { return ((PortNormalPose) (Object) pose).stardewcraft$trustedNormals(); }

    // Maintained reference for the official 1.21.1 Sodium 0.6.13 MatrixHelper/NormI8 body.
    // Unlike the vanilla NeoForge applyBakedNormals extension, its immediate writer normalizes packed normals.
    private static int baselinePacked(Matrix3f matrix, boolean trust, int packed) {
        float x = (byte) packed * .007874016F, y = (byte) (packed >> 8) * .007874016F, z = (byte) (packed >> 16) * .007874016F;
        return normalizeAndPack(trust, matrix.m00 * x + (matrix.m10 * y + matrix.m20 * z),
                matrix.m01 * x + (matrix.m11 * y + matrix.m21 * z), matrix.m02 * x + (matrix.m12 * y + matrix.m22 * z));
    }

    private static int baselineDirection(Matrix3f matrix, boolean trust, Direction direction) {
        return switch (direction) {
            case DOWN -> normalizeAndPack(trust, -matrix.m10, -matrix.m11, -matrix.m12);
            case UP -> normalizeAndPack(trust, matrix.m10, matrix.m11, matrix.m12);
            case NORTH -> normalizeAndPack(trust, -matrix.m20, -matrix.m21, -matrix.m22);
            case SOUTH -> normalizeAndPack(trust, matrix.m20, matrix.m21, matrix.m22);
            case WEST -> normalizeAndPack(trust, -matrix.m00, -matrix.m01, -matrix.m02);
            case EAST -> normalizeAndPack(trust, matrix.m00, matrix.m01, matrix.m02);
        };
    }

    private static int normalizeAndPack(boolean trust, float x, float y, float z) {
        if (!trust) {
            float factor = org.joml.Math.invsqrt(org.joml.Math.fma(x, x, org.joml.Math.fma(y, y, z * z)));
            x *= factor; y *= factor; z *= factor;
        }
        return pack(x, y, z);
    }

    private static int pack(float x, float y, float z) {
        return ((int) (Math.max(-1F, Math.min(1F, x)) * 127) & 255)
                | ((int) (Math.max(-1F, Math.min(1F, y)) * 127) & 255) << 8
                | ((int) (Math.max(-1F, Math.min(1F, z)) * 127) & 255) << 16;
    }

    private static final class Capture implements VertexBufferWriter {
        private ByteBuffer data;
        private int pushes, count;
        @Override public void push(MemoryStack stack, long pointer, int count, VertexFormatDescription format) {
            require(format.stride() == 36, "Locked ModelVertex stride changed");
            this.count = count; pushes++;
            byte[] copy = new byte[count * 36];
            MemoryUtil.memByteBuffer(pointer, copy.length).get(copy);
            data = ByteBuffer.wrap(copy).order(ByteOrder.nativeOrder());
        }
        private int normal(int vertex) { return data.getInt(vertex * 36 + 32); }
    }

    private static final class ProbeQuad implements ModelQuadView {
        private static final float[][] POSITIONS = {{0, 0, 0}, {0, 0, 1}, {-.70710677F, .70710677F, 1}, {-.70710677F, .70710677F, 0}};
        @Override public float getX(int i) { return POSITIONS[i][0]; }
        @Override public float getY(int i) { return POSITIONS[i][1]; }
        @Override public float getZ(int i) { return POSITIONS[i][2]; }
        @Override public int getColor(int i) { return 0x80604020 + i; }
        @Override public float getTexU(int i) { return i * .25F; }
        @Override public float getTexV(int i) { return .75F - i * .125F; }
        @Override public int getLight(int i) { return 0x00600090; }
        @Override public int getFlags() { return 0; }
        @Override public int getColorIndex() { return -1; }
        @Override public TextureAtlasSprite getSprite() { return null; }
        @Override public Direction getLightFace() { return Direction.NORTH; }
        @Override public int getForgeNormal(int i) { return switch (i) { case 0 -> 0; case 1 -> pack(1, 0, 0); case 2 -> pack(0, 1, 0); default -> pack(0, 0, 1); }; }
        @Override public int getComputedFaceNormal() { return ModelQuadUtil.calculateNormal(this); }
    }

    private static void close(float actual, float expected, String message) { require(Math.abs(actual - expected) < 2E-6F, message); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
