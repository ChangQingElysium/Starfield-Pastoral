import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.port.PortNormalPose;
import com.stardew.craft.port.PortVertex;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Executes the actual Mixin-woven PoseStack, Cube and default VertexConsumer bodies. */
public final class RenderNormalsFixture {
    private static final float EPSILON = 2.0E-6F;

    public static void main(String[] args) {
        checkScaleAndTrust();
        checkModelPart();
        checkBulkData();
        System.out.println("Woven normals: exact 1.21 scale/trust/copy/reset, native Cube.compile and bulk default; baked-normal extension unchanged.");
    }

    private static PortNormalPose trust(PoseStack.Pose pose) { return (PortNormalPose) (Object) pose; }

    private static void checkScaleAndTrust() {
        for (float[] scale : new float[][]{{2, 3, 4}, {-2, 3, 4}, {2, -3, 4}, {2, 3, -4},
                {2, 2, 2}, {-2, -2, -2}, {-2, 2, 2}, {2, -2, -2}, {0, 0, 0}}) {
            PoseStack stack = new PoseStack();
            stack.mulPose(new Quaternionf().rotationXYZ(.2F, -.4F, .1F));
            Matrix4f position = new Matrix4f(stack.last().pose()).scale(scale[0], scale[1], scale[2]);
            Matrix3f normal = new Matrix3f(stack.last().normal());
            boolean uniform = Math.abs(scale[0]) == Math.abs(scale[1]) && Math.abs(scale[1]) == Math.abs(scale[2]);
            if (uniform) {
                if (scale[0] < 0 || scale[1] < 0 || scale[2] < 0)
                    normal.scale(Math.signum(scale[0]), Math.signum(scale[1]), Math.signum(scale[2]));
            } else normal.scale(1 / scale[0], 1 / scale[1], 1 / scale[2]);
            stack.scale(scale[0], scale[1], scale[2]);
            require(stack.last().pose().equals(position, EPSILON) && stack.last().normal().equals(normal, EPSILON), "Pose matrices differ from the 1.21 algorithm");
            require(trust(stack.last()).stardewcraft$trustedNormals() == uniform, "Scale trust differs from 1.21");
        }
        PoseStack stack = new PoseStack();
        stack.scale(2, 3, 4);
        stack.scale(.5F, 1 / 3F, .25F);
        require(!trust(stack.last()).stardewcraft$trustedNormals(), "Restoring the matrix must not erase untrusted history");
        close(PortVertex.transformNormal(stack.last(), 2, 0, 0, new Vector3f()).length(), 1, "Historical trust not used");
        PoseStack.Pose copy = PortVertex.copy(stack.last());
        require(!trust(copy).stardewcraft$trustedNormals() && copy.normal() != stack.last().normal(), "Pose copy lost trust or shared a matrix");
        stack.pushPose();
        require(!trust(stack.last()).stardewcraft$trustedNormals(), "pushPose lost trust");
        stack.setIdentity();
        require(trust(stack.last()).stardewcraft$trustedNormals(), "Identity must restore trust");
        stack.popPose();
        require(!trust(stack.last()).stardewcraft$trustedNormals(), "Child identity changed the parent trust");
        Matrix4f shear = new Matrix4f().m10(.35F).m21(-.2F);
        PortVertex.mulPose(stack, shear);
        require(stack.last().normal().equals(new Matrix3f(stack.last().pose()).invert().transpose(), EPSILON), "mulPose rescaled the inverse transpose");
        require(!trust(stack.last()).stardewcraft$trustedNormals(), "Shear must distrust normals");
    }

    private static void checkModelPart() {
        PoseStack stack = new PoseStack();
        stack.scale(-2, 3, 4);
        Recorder output = new Recorder();
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, 0, 0, 0, 16, 16, 16, 0, 0, 0,
                false, 32, 32, EnumSet.allOf(Direction.class));
        cube.compile(stack.last(), output, 1234, 5678, .25F, .5F, .75F, 1);
        require(output.normals.size() == 24, "Native cube did not emit all six faces");
        for (Vector3f normal : output.normals) close(normal.length(), 1, "Native cube used an unnormalized scaled normal");
        require(output.normals.stream().anyMatch(n -> n.x() == -1) && output.normals.stream().anyMatch(n -> n.x() == 1), "Negative scale reversed or erased cube faces");
    }

    private static void checkBulkData() {
        PoseStack stack = new PoseStack();
        stack.scale(2, 3, 4);
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            data[i * 8] = Float.floatToRawIntBits(i * .25F);
            data[i * 8 + 1] = Float.floatToRawIntBits(.5F);
            data[i * 8 + 2] = Float.floatToRawIntBits(.75F);
            data[i * 8 + 3] = 0x80604020;
            data[i * 8 + 4] = Float.floatToRawIntBits(.25F);
            data[i * 8 + 5] = Float.floatToRawIntBits(.75F);
        }
        BakedQuad generated = new BakedQuad(data, -1, Direction.EAST, null, true);
        Recorder output = new Recorder();
        float[] brightness = {.6F, .7F, .8F, .9F};
        output.putBulkData(stack.last(), generated, brightness, .3F, .4F, .5F, .6F,
                new int[]{0, 1, 2, 3}, 4567, true);
        require(output.normals.size() == 4 && output.lightingCalls == 4 && output.bakedNormalCalls == 4, "Bulk body duplicated transforms or lost extension callbacks");
        for (int i = 0; i < 4; i++) {
            close(output.normals.get(i).x(), 1, "Generated face normal not normalized");
            int[] color = output.colors.get(i);
            require(color[0] == (int) (32 * brightness[i] * .3F) && color[1] == (int) (64 * brightness[i] * .4F)
                    && color[2] == (int) (96 * brightness[i] * .5F)
                    && color[3] == (int) ((.6F * 128 / 255F) * 255), "NF integer color/alpha order changed");
            close(output.positions.get(i).x(), i * .5F, "Position transformed more than once");
        }
        // NeoForge's extension intentionally does NOT normalize packed baked normals after applying the matrix.
        // Preserve that source behavior; do not normalize every output vertex indiscriminately.
        int[] baked = data.clone();
        for (int i = 0; i < 4; i++) baked[i * 8 + 7] = 127;
        output = new Recorder();
        output.putBulkData(stack.last(), new BakedQuad(baked, -1, Direction.EAST, null, true), 1, 1, 1, 0, 0);
        for (Vector3f normal : output.normals) close(normal.x(), .5F, "NeoForge baked-normal extension was altered");
    }

    private static final class Recorder implements VertexConsumer {
        private final List<Vector3f> normals = new ArrayList<>(), positions = new ArrayList<>();
        private final List<int[]> colors = new ArrayList<>();
        private int lightingCalls, bakedNormalCalls;
        @Override public VertexConsumer vertex(double x, double y, double z) { positions.add(new Vector3f((float) x, (float) y, (float) z)); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { colors.add(new int[]{r, g, b, a}); return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { normals.add(new Vector3f(x, y, z)); return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
        @Override public int applyBakedLighting(int light, java.nio.ByteBuffer data) { lightingCalls++; return VertexConsumer.super.applyBakedLighting(light, data); }
        @Override public void applyBakedNormals(Vector3f normal, java.nio.ByteBuffer data, Matrix3f matrix) {
            bakedNormalCalls++; VertexConsumer.super.applyBakedNormals(normal, data, matrix);
        }
    }

    private static void close(float actual, float expected, String message) { require(Math.abs(actual - expected) <= EPSILON, message + ": " + actual + " != " + expected); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
