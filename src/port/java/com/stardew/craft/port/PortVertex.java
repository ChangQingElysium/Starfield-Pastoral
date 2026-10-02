package com.stardew.craft.port;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;
import net.minecraft.util.FastColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/**
 * 1.21.1 vertex/pose API on top of the 1.20.1 {@link VertexConsumer}.
 *
 * <p>1.21 {@code BufferBuilder} accepts vertex attributes in any order and ends a vertex implicitly; 1.20.1
 * {@code BufferBuilder} only writes the attribute that is the format's current element (anything else is
 * silently skipped) and needs an explicit {@code endVertex()}. {@link #of(VertexConsumer)} records a 1.21
 * attribute chain and replays it on {@link Builder#endVertex()} in an order that fills every vanilla format
 * (position, color, uv0, color, uv1, uv2, color, normal): an attribute is only written when it is the
 * current element, so the repeated color call covers BLOCK/NEW_ENTITY ordering as well as PARTICLE,
 * POSITION_TEX_COLOR, POSITION_TEX_LIGHTMAP_COLOR and POSITION_TEX_COLOR_NORMAL ordering. Attributes the
 * format does not contain are ignored, exactly like 1.21. Like a 1.21 chain, each call goes to the consumer
 * returned by the previous call. The first value set for an attribute wins (1.21 {@code BufferBuilder}).
 */
@OnlyIn(Dist.CLIENT)
public final class PortVertex {
    private static final float ORTHONORMAL_EPSILON = 1.0E-4F;

    private PortVertex() {
    }

    public static Builder of(VertexConsumer consumer) {
        return new Builder(consumer);
    }

    /** 1.21 {@code VertexConsumer#addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz)}. */
    public static void addVertex(VertexConsumer consumer, float x, float y, float z, int color, float u, float v,
            int packedOverlay, int packedLight, float normalX, float normalY, float normalZ) {
        of(consumer).addVertex(x, y, z).setColor(color).setUv(u, v).setOverlay(packedOverlay).setLight(packedLight)
                .setNormal(normalX, normalY, normalZ).endVertex();
    }

    /**
     * 1.21 {@code PoseStack.Pose#transformNormal}: 1.21 normalizes when the pose is not "trusted" (a non-uniform
     * scale or non-orthonormal matrix was applied). The runtime pose Mixin preserves that history;
     * an untransformed development JVM can infer the ordinary case from the matrix instead.
     */
    public static Vector3f transformNormal(PoseStack.Pose pose, float x, float y, float z, Vector3f destination) {
        Matrix3f normal = pose.normal();
        Vector3f result = normal.transform(x, y, z, destination);
        boolean trusted = (Object) pose instanceof PortNormalPose state
                ? state.stardewcraft$trustedNormals() : isOrthonormal(normal);
        return trusted ? result : result.normalize();
    }

    public static Vector3f transformNormal(PoseStack.Pose pose, Vector3f vector, Vector3f destination) {
        return transformNormal(pose, vector.x, vector.y, vector.z, destination);
    }

    static boolean isOrthonormal(Matrix3f m) {
        float c0 = m.m00 * m.m00 + m.m01 * m.m01 + m.m02 * m.m02;
        float c1 = m.m10 * m.m10 + m.m11 * m.m11 + m.m12 * m.m12;
        float c2 = m.m20 * m.m20 + m.m21 * m.m21 + m.m22 * m.m22;
        float d01 = m.m00 * m.m10 + m.m01 * m.m11 + m.m02 * m.m12;
        float d02 = m.m00 * m.m20 + m.m01 * m.m21 + m.m02 * m.m22;
        float d12 = m.m10 * m.m20 + m.m11 * m.m21 + m.m12 * m.m22;
        return Math.abs(c0 - 1.0F) <= ORTHONORMAL_EPSILON && Math.abs(c1 - 1.0F) <= ORTHONORMAL_EPSILON
                && Math.abs(c2 - 1.0F) <= ORTHONORMAL_EPSILON && Math.abs(d01) <= ORTHONORMAL_EPSILON
                && Math.abs(d02) <= ORTHONORMAL_EPSILON && Math.abs(d12) <= ORTHONORMAL_EPSILON;
    }

    /**
     * 1.21 {@code PoseStack#mulPose(Matrix4f)}. 1.20.1 {@code mulPoseMatrix} leaves the normal matrix untouched;
     * 1.21 rotates it for orthonormal matrices and recomputes it (inverse transpose, normalized per vertex)
     * otherwise. Keep the exact inverse transpose: baked normals use it unchanged in NeoForge too.
     */
    public static void mulPose(PoseStack stack, Matrix4f matrix) {
        PoseStack.Pose pose = stack.last();
        pose.pose().mul(matrix);
        int properties = matrix.properties();
        if ((properties & Matrix4fc.PROPERTY_TRANSLATION) != 0) {
            return;
        }
        if ((properties & Matrix4fc.PROPERTY_ORTHONORMAL) != 0) {
            pose.normal().mul(new Matrix3f(matrix));
        } else {
            Matrix3f normal = pose.normal();
            normal.set(pose.pose()).invert().transpose();
            trust(pose, false);
        }
    }

    /** 1.21 {@code PoseStack.Pose#copy()}: an independent pose with copied matrices. */
    public static PoseStack.Pose copy(PoseStack.Pose pose) {
        PoseStack holder = new PoseStack();
        PoseStack.Pose copy = holder.last();
        copy.pose().set(pose.pose());
        copy.normal().set(pose.normal());
        if ((Object) pose instanceof PortNormalPose state) trust(copy, state.stardewcraft$trustedNormals());
        return copy;
    }

    /** Exact 1.21 PoseStack.scale, including mixed-sign uniform reflections. */
    public static void scale(PoseStack.Pose pose, float x, float y, float z) {
        pose.pose().scale(x, y, z);
        if (Math.abs(x) == Math.abs(y) && Math.abs(y) == Math.abs(z)) {
            if (x < 0 || y < 0 || z < 0) pose.normal().scale(Math.signum(x), Math.signum(y), Math.signum(z));
        } else {
            pose.normal().scale(1.0F / x, 1.0F / y, 1.0F / z);
            trust(pose, false);
        }
    }

    private static void trust(PoseStack.Pose pose, boolean trusted) {
        if ((Object) pose instanceof PortNormalPose state) state.stardewcraft$trustedNormals(trusted);
    }

    /**
     * 1.21 {@code VertexConsumer#putBulkData(Pose, BakedQuad, r, g, b, a, light, overlay)} (no per-vertex
     * brightness, quad colors ignored): integer colors computed as in 1.21, positions via
     * {@code transformPosition}, normals via {@link #transformNormal} plus NeoForge baked-normal/lighting rules.
     */
    public static void putBulkData(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, float red,
            float green, float blue, float alpha, int packedLight, int packedOverlay) {
        putBulkData(consumer, pose, quad, new float[]{1, 1, 1, 1}, red, green, blue, alpha,
                new int[]{packedLight, packedLight, packedLight, packedLight}, packedOverlay, false);
    }

    /** Exact 1.21 bulk body; does not call the overwritten VertexConsumer default method. */
    public static void putBulkData(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, float[] brightness,
            float red, float green, float blue, float alpha, int[] lights, int packedOverlay, boolean readColor) {
        int[] vertices = quad.getVertices();
        Vec3i direction = quad.getDirection().getNormal();
        Matrix4f matrix = pose.pose();
        Vector3f normal = transformNormal(pose, direction.getX(), direction.getY(), direction.getZ(), new Vector3f());
        int count = vertices.length / 8;
        int a = (int) (alpha * 255.0F);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buffer = stack.malloc(DefaultVertexFormat.BLOCK.getVertexSize());
            IntBuffer ints = buffer.asIntBuffer();
            for (int i = 0; i < count; i++) {
                ints.clear();
                ints.put(vertices, i * 8, 8);
                float x = buffer.getFloat(0);
                float y = buffer.getFloat(4);
                float z = buffer.getFloat(8);
                float r = readColor ? (buffer.get(12) & 255) * brightness[i] * red : brightness[i] * red * 255.0F;
                float g = readColor ? (buffer.get(13) & 255) * brightness[i] * green : brightness[i] * green * 255.0F;
                float b = readColor ? (buffer.get(14) & 255) * brightness[i] * blue : brightness[i] * blue * 255.0F;
                int vertexAlpha = readColor ? (int) ((alpha * (buffer.get(15) & 255) / 255.0F) * 255) : a;
                int color = FastColor.ARGB32.color(vertexAlpha, (int) r, (int) g, (int) b);
                int light = consumer.applyBakedLighting(lights[i], buffer);
                float u = buffer.getFloat(16);
                float v = buffer.getFloat(20);
                Vector3f position = matrix.transformPosition(x, y, z, new Vector3f());
                consumer.applyBakedNormals(normal, buffer, pose.normal());
                // Bulk quads use BLOCK/NEW_ENTITY's fixed attribute order. Emit each attribute once,
                // just like the native default, rather than the generic any-format Builder replay.
                VertexConsumer vertex = consumer.vertex(position.x(), position.y(), position.z()).color(color)
                        .uv(u, v).overlayCoords(packedOverlay).uv2(light)
                        .normal(normal.x(), normal.y(), normal.z());
                vertex.endVertex();
            }
        }
    }

    /** Records one 1.21-style vertex chain; {@link #endVertex()} writes it to the 1.20.1 consumer. */
    public static final class Builder {
        private final VertexConsumer consumer;
        private boolean hasPosition;
        private float x;
        private float y;
        private float z;
        private boolean hasColor;
        private int red;
        private int green;
        private int blue;
        private int alpha;
        private boolean hasUv;
        private float u;
        private float v;
        private boolean hasUv1;
        private int u1;
        private int v1;
        private boolean hasUv2;
        private int u2;
        private int v2;
        private boolean hasNormal;
        private float normalX;
        private float normalY;
        private float normalZ;

        Builder(VertexConsumer consumer) {
            this.consumer = consumer;
        }

        public Builder addVertex(float x, float y, float z) {
            if (hasPosition) {
                throw new IllegalStateException("PortVertex chain already has a vertex; call endVertex() first");
            }
            hasPosition = true;
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder addVertex(Vector3f position) {
            return addVertex(position.x(), position.y(), position.z());
        }

        public Builder addVertex(PoseStack.Pose pose, Vector3f position) {
            return addVertex(pose, position.x(), position.y(), position.z());
        }

        public Builder addVertex(PoseStack.Pose pose, float x, float y, float z) {
            return addVertex(pose.pose(), x, y, z);
        }

        public Builder addVertex(Matrix4f pose, float x, float y, float z) {
            Vector3f position = pose.transformPosition(x, y, z, new Vector3f());
            return addVertex(position.x(), position.y(), position.z());
        }

        public Builder setColor(int red, int green, int blue, int alpha) {
            if (!hasColor) {
                hasColor = true;
                this.red = red;
                this.green = green;
                this.blue = blue;
                this.alpha = alpha;
            }
            return this;
        }

        public Builder setColor(float red, float green, float blue, float alpha) {
            return setColor((int) (red * 255.0F), (int) (green * 255.0F), (int) (blue * 255.0F),
                    (int) (alpha * 255.0F));
        }

        public Builder setColor(int color) {
            return setColor(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color),
                    FastColor.ARGB32.alpha(color));
        }

        public Builder setWhiteAlpha(int alpha) {
            return setColor(alpha << 24 | 0xFFFFFF); // 1.21 FastColor.ARGB32.color(alpha, -1)
        }

        public Builder setUv(float u, float v) {
            if (!hasUv) {
                hasUv = true;
                this.u = u;
                this.v = v;
            }
            return this;
        }

        public Builder setUv1(int u, int v) {
            if (!hasUv1) {
                hasUv1 = true;
                this.u1 = u;
                this.v1 = v;
            }
            return this;
        }

        public Builder setUv2(int u, int v) {
            if (!hasUv2) {
                hasUv2 = true;
                this.u2 = u;
                this.v2 = v;
            }
            return this;
        }

        public Builder setOverlay(int packedOverlay) {
            return setUv1(packedOverlay & 65535, packedOverlay >> 16 & 65535);
        }

        public Builder setLight(int packedLight) {
            return setUv2(packedLight & 65535, packedLight >> 16 & 65535);
        }

        public Builder setNormal(float x, float y, float z) {
            if (!hasNormal) {
                hasNormal = true;
                this.normalX = x;
                this.normalY = y;
                this.normalZ = z;
            }
            return this;
        }

        public Builder setNormal(PoseStack.Pose pose, float x, float y, float z) {
            Vector3f normal = transformNormal(pose, x, y, z, new Vector3f());
            return setNormal(normal.x(), normal.y(), normal.z());
        }

        public void endVertex() {
            if (!hasPosition) {
                throw new IllegalStateException("PortVertex chain has no addVertex");
            }
            VertexConsumer current = consumer.vertex(x, y, z);
            if (hasColor) {
                current = current.color(red, green, blue, alpha);
            }
            if (hasUv) {
                current = current.uv(u, v);
            }
            if (hasColor) {
                current = current.color(red, green, blue, alpha);
            }
            if (hasUv1) {
                current = current.overlayCoords(u1, v1);
            }
            if (hasUv2) {
                current = current.uv2(u2, v2);
            }
            if (hasColor) {
                current = current.color(red, green, blue, alpha);
            }
            if (hasNormal) {
                current = current.normal(normalX, normalY, normalZ);
            }
            current.endVertex();
        }
    }
}
