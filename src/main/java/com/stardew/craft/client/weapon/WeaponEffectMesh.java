package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import com.stardew.craft.port.PortVertex;
public final class WeaponEffectMesh {

    private WeaponEffectMesh() {}

    public static void renderRing3D(VertexConsumer consumer, Matrix4f pose, int light,
                                    int r, int g, int b, int alpha,
                                    float innerR, float outerR, float halfH,
                                    int segments, float scroll) {
        float step = (float) (Math.PI * 2.0 / segments);
        for (int i = 0; i < segments; i++) {
            float a0 = i * step;
            float a1 = (i + 1) * step;
            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);

            float u0 = wrapUv((i / (float) segments) + scroll);
            float u1 = wrapUv(((i + 1) / (float) segments) + scroll);

            float ix0 = cos0 * innerR;
            float iz0 = sin0 * innerR;
            float ix1 = cos1 * innerR;
            float iz1 = sin1 * innerR;
            float ox0 = cos0 * outerR;
            float oz0 = sin0 * outerR;
            float ox1 = cos1 * outerR;
            float oz1 = sin1 * outerR;

            float planarScale = outerR * 2.0f;
            float topU_ix0 = wrapUv(0.5f + (ix0 / planarScale) + scroll);
            float topV_iz0 = wrapUv(0.5f + (iz0 / planarScale) + scroll * 0.6f);
            float topU_ox0 = wrapUv(0.5f + (ox0 / planarScale) + scroll);
            float topV_oz0 = wrapUv(0.5f + (oz0 / planarScale) + scroll * 0.6f);
            float topU_ox1 = wrapUv(0.5f + (ox1 / planarScale) + scroll);
            float topV_oz1 = wrapUv(0.5f + (oz1 / planarScale) + scroll * 0.6f);
            float topU_ix1 = wrapUv(0.5f + (ix1 / planarScale) + scroll);
            float topV_iz1 = wrapUv(0.5f + (iz1 / planarScale) + scroll * 0.6f);

            // Top face (polar UV)
            vertex(consumer, pose, light, r, g, b, alpha, ix0, halfH, iz0, topU_ix0, topV_iz0, 0.0f, 1.0f, 0.0f);
            vertex(consumer, pose, light, r, g, b, alpha, ox0, halfH, oz0, topU_ox0, topV_oz0, 0.0f, 1.0f, 0.0f);
            vertex(consumer, pose, light, r, g, b, alpha, ox1, halfH, oz1, topU_ox1, topV_oz1, 0.0f, 1.0f, 0.0f);
            vertex(consumer, pose, light, r, g, b, alpha, ix1, halfH, iz1, topU_ix1, topV_iz1, 0.0f, 1.0f, 0.0f);

            // Bottom face (polar UV)
            vertex(consumer, pose, light, r, g, b, alpha, ix1, -halfH, iz1, topU_ix1, topV_iz1, 0.0f, -1.0f, 0.0f);
            vertex(consumer, pose, light, r, g, b, alpha, ox1, -halfH, oz1, topU_ox1, topV_oz1, 0.0f, -1.0f, 0.0f);
            vertex(consumer, pose, light, r, g, b, alpha, ox0, -halfH, oz0, topU_ox0, topV_oz0, 0.0f, -1.0f, 0.0f);
            vertex(consumer, pose, light, r, g, b, alpha, ix0, -halfH, iz0, topU_ix0, topV_iz0, 0.0f, -1.0f, 0.0f);

            // Outer wall
            vertex(consumer, pose, light, r, g, b, alpha, ox0, -halfH, oz0, u0, 0.0f, cos0, 0.0f, sin0);
            vertex(consumer, pose, light, r, g, b, alpha, ox0, halfH, oz0, u0, 1.0f, cos0, 0.0f, sin0);
            vertex(consumer, pose, light, r, g, b, alpha, ox1, halfH, oz1, u1, 1.0f, cos1, 0.0f, sin1);
            vertex(consumer, pose, light, r, g, b, alpha, ox1, -halfH, oz1, u1, 0.0f, cos1, 0.0f, sin1);

            // Inner wall
            vertex(consumer, pose, light, r, g, b, alpha, ix1, -halfH, iz1, u1, 0.0f, -cos1, 0.0f, -sin1);
            vertex(consumer, pose, light, r, g, b, alpha, ix1, halfH, iz1, u1, 1.0f, -cos1, 0.0f, -sin1);
            vertex(consumer, pose, light, r, g, b, alpha, ix0, halfH, iz0, u0, 1.0f, -cos0, 0.0f, -sin0);
            vertex(consumer, pose, light, r, g, b, alpha, ix0, -halfH, iz0, u0, 0.0f, -cos0, 0.0f, -sin0);
        }
    }

    public static void renderSphere(VertexConsumer consumer, Matrix4f pose, int light,
                                    int r, int g, int b, int alpha,
                                    float radius, int latSegments, int lonSegments,
                                    float scrollU, float scrollV) {
        for (int lat = 0; lat < latSegments; lat++) {
            float v0 = lat / (float) latSegments;
            float v1 = (lat + 1) / (float) latSegments;
            float theta0 = (v0 - 0.5f) * (float) Math.PI;
            float theta1 = (v1 - 0.5f) * (float) Math.PI;
            float y0 = (float) Math.sin(theta0);
            float y1 = (float) Math.sin(theta1);
            float r0 = (float) Math.cos(theta0);
            float r1 = (float) Math.cos(theta1);

            for (int lon = 0; lon < lonSegments; lon++) {
                float u0 = lon / (float) lonSegments;
                float u1 = (lon + 1) / (float) lonSegments;
                float phi0 = u0 * (float) (Math.PI * 2.0);
                float phi1 = u1 * (float) (Math.PI * 2.0);

                float x00 = (float) Math.cos(phi0) * r0;
                float z00 = (float) Math.sin(phi0) * r0;
                float x10 = (float) Math.cos(phi1) * r0;
                float z10 = (float) Math.sin(phi1) * r0;
                float x11 = (float) Math.cos(phi1) * r1;
                float z11 = (float) Math.sin(phi1) * r1;
                float x01 = (float) Math.cos(phi0) * r1;
                float z01 = (float) Math.sin(phi0) * r1;

                float uu0 = wrapUv(u0 + scrollU);
                float uu1 = wrapUv(u1 + scrollU);
                float vv0 = wrapUv(v0 + scrollV);
                float vv1 = wrapUv(v1 + scrollV);

                vertex(consumer, pose, light, r, g, b, alpha, x00 * radius, y0 * radius, z00 * radius, uu0, vv0, x00, y0, z00);
                vertex(consumer, pose, light, r, g, b, alpha, x10 * radius, y0 * radius, z10 * radius, uu1, vv0, x10, y0, z10);
                vertex(consumer, pose, light, r, g, b, alpha, x11 * radius, y1 * radius, z11 * radius, uu1, vv1, x11, y1, z11);
                vertex(consumer, pose, light, r, g, b, alpha, x01 * radius, y1 * radius, z01 * radius, uu0, vv1, x01, y1, z01);
            }
        }
    }

    public static void renderBox(VertexConsumer consumer, Matrix4f pose, int light,
                                 int r, int g, int b, int alpha,
                                 float halfX, float halfY, float halfZ,
                                 float scrollU, float scrollV) {
        float u0 = wrapUv(0.0f + scrollU);
        float u1 = wrapUv(1.0f + scrollU);
        float v0 = wrapUv(0.0f + scrollV);
        float v1 = wrapUv(1.0f + scrollV);

        // Top
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, halfY, -halfZ, u0, v0, 0.0f, 1.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, halfX, halfY, -halfZ, u1, v0, 0.0f, 1.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, halfX, halfY, halfZ, u1, v1, 0.0f, 1.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, halfY, halfZ, u0, v1, 0.0f, 1.0f, 0.0f);

        // Bottom
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, -halfY, halfZ, u0, v1, 0.0f, -1.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, halfX, -halfY, halfZ, u1, v1, 0.0f, -1.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, halfX, -halfY, -halfZ, u1, v0, 0.0f, -1.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, -halfY, -halfZ, u0, v0, 0.0f, -1.0f, 0.0f);

        // Front
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, -halfY, halfZ, u0, v0, 0.0f, 0.0f, 1.0f);
        vertex(consumer, pose, light, r, g, b, alpha, halfX, -halfY, halfZ, u1, v0, 0.0f, 0.0f, 1.0f);
        vertex(consumer, pose, light, r, g, b, alpha, halfX, halfY, halfZ, u1, v1, 0.0f, 0.0f, 1.0f);
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, halfY, halfZ, u0, v1, 0.0f, 0.0f, 1.0f);

        // Back
        vertex(consumer, pose, light, r, g, b, alpha, halfX, -halfY, -halfZ, u0, v0, 0.0f, 0.0f, -1.0f);
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, -halfY, -halfZ, u1, v0, 0.0f, 0.0f, -1.0f);
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, halfY, -halfZ, u1, v1, 0.0f, 0.0f, -1.0f);
        vertex(consumer, pose, light, r, g, b, alpha, halfX, halfY, -halfZ, u0, v1, 0.0f, 0.0f, -1.0f);

        // Left
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, -halfY, -halfZ, u0, v0, -1.0f, 0.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, -halfY, halfZ, u1, v0, -1.0f, 0.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, halfY, halfZ, u1, v1, -1.0f, 0.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, -halfX, halfY, -halfZ, u0, v1, -1.0f, 0.0f, 0.0f);

        // Right
        vertex(consumer, pose, light, r, g, b, alpha, halfX, -halfY, halfZ, u0, v0, 1.0f, 0.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, halfX, -halfY, -halfZ, u1, v0, 1.0f, 0.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, halfX, halfY, -halfZ, u1, v1, 1.0f, 0.0f, 0.0f);
        vertex(consumer, pose, light, r, g, b, alpha, halfX, halfY, halfZ, u0, v1, 1.0f, 0.0f, 0.0f);
    }

    public static void renderTaperedTube(VertexConsumer consumer, Matrix4f pose, int light,
                                         int r, int g, int b, int alpha,
                                         float radiusBottom, float radiusTop, float halfH,
                                         int segments, float scroll) {
        float step = (float) (Math.PI * 2.0 / segments);
        for (int i = 0; i < segments; i++) {
            float a0 = i * step;
            float a1 = (i + 1) * step;
            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);

            float u0 = wrapUv((i / (float) segments) + scroll);
            float u1 = wrapUv(((i + 1) / (float) segments) + scroll);

            float bx0 = cos0 * radiusBottom;
            float bz0 = sin0 * radiusBottom;
            float bx1 = cos1 * radiusBottom;
            float bz1 = sin1 * radiusBottom;
            float tx0 = cos0 * radiusTop;
            float tz0 = sin0 * radiusTop;
            float tx1 = cos1 * radiusTop;
            float tz1 = sin1 * radiusTop;

            vertex(consumer, pose, light, r, g, b, alpha, bx0, -halfH, bz0, u0, 0.0f, cos0, 0.0f, sin0);
            vertex(consumer, pose, light, r, g, b, alpha, tx0, halfH, tz0, u0, 1.0f, cos0, 0.0f, sin0);
            vertex(consumer, pose, light, r, g, b, alpha, tx1, halfH, tz1, u1, 1.0f, cos1, 0.0f, sin1);
            vertex(consumer, pose, light, r, g, b, alpha, bx1, -halfH, bz1, u1, 0.0f, cos1, 0.0f, sin1);
        }
    }

    @SuppressWarnings("null")
    private static void vertex(VertexConsumer consumer, Matrix4f pose, int light,
                               int r, int g, int b, int alpha,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        PortVertex.of(consumer).addVertex(pose, x, y, z)
            .setColor(r, g, b, alpha)
            .setUv(wrapUv(u), wrapUv(v))
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(nx, ny, nz).endVertex();
    }

    private static float wrapUv(float uv) {
        float wrapped = uv % 1.0f;
        return wrapped < 0.0f ? wrapped + 1.0f : wrapped;
    }
}
