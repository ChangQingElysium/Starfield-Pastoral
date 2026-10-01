package com.stardew.craft.port;

import com.mojang.math.Transformation;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.ForgeFaceData;
import net.minecraftforge.client.model.QuadTransformers;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 1.21.1 + NeoForge {@code BlockModel#bakeFace} / {@code FaceBakery#bakeQuad}. The 1.20.1 Forge baker differs in
 * three ways that change the quad data: UVs are inset by 0.1% toward the opposite corner (removed in 1.20.2),
 * winding is always recalculated (NeoForge skips it for states that may rotate arbitrarily), and normals are the
 * face direction unless {@code calculate_normals} is set (NeoForge always derives them from the geometry).
 */
@OnlyIn(Dist.CLIENT)
public final class PortFaceBakery {
    private static final float RESCALE_22_5 = 1.0F / (float) Math.cos((float) (Math.PI / 8)) - 1.0F;
    private static final float RESCALE_45 = 1.0F / (float) Math.cos((float) (Math.PI / 4)) - 1.0F;
    private static final ResourceLocation UV_LOCK_CONTEXT = new ResourceLocation("stardewcraft", "port_face_bakery");

    private PortFaceBakery() {
    }

    /** 1.21 {@code BlockModel.bakeFace(element, face, sprite, facing, state)}. */
    public static BakedQuad bakeFace(BlockElement element, BlockElementFace face, TextureAtlasSprite sprite,
            Direction facing, ModelState state) {
        return bakeQuad(element.from, element.to, face, sprite, facing, state, element.rotation, element.shade);
    }

    /** NeoForge {@code ModelStateExtension#mayApplyArbitraryRotation}. */
    public static boolean mayApplyArbitraryRotation(ModelState state) {
        return !(state instanceof BlockModelRotation || state instanceof Variant);
    }

    public static BakedQuad bakeQuad(Vector3f posFrom, Vector3f posTo, BlockElementFace face, TextureAtlasSprite sprite,
            Direction facing, ModelState transform, @Nullable BlockElementRotation rotation, boolean shade) {
        BlockFaceUV uv = face.uv;
        if (transform.isUvLocked()) {
            uv = FaceBakery.recomputeUVs(face.uv, facing, transform.getRotation(), UV_LOCK_CONTEXT);
        }
        float[] saved = new float[uv.uvs.length];
        System.arraycopy(uv.uvs, 0, saved, 0, saved.length);
        float shrink = sprite.uvShrinkRatio();
        float centerU = (uv.uvs[0] + uv.uvs[0] + uv.uvs[2] + uv.uvs[2]) / 4.0F;
        float centerV = (uv.uvs[1] + uv.uvs[1] + uv.uvs[3] + uv.uvs[3]) / 4.0F;
        uv.uvs[0] = Mth.lerp(shrink, uv.uvs[0], centerU);
        uv.uvs[2] = Mth.lerp(shrink, uv.uvs[2], centerU);
        uv.uvs[1] = Mth.lerp(shrink, uv.uvs[1], centerV);
        uv.uvs[3] = Mth.lerp(shrink, uv.uvs[3], centerV);
        int[] vertices = makeVertices(uv, sprite, facing, setupShape(posFrom, posTo), transform.getRotation(), rotation);
        Direction direction = FaceBakery.calculateFacing(vertices);
        System.arraycopy(saved, 0, uv.uvs, 0, saved.length);
        if (rotation == null && !mayApplyArbitraryRotation(transform)) {
            recalculateWinding(vertices, direction);
        }
        ForgeHooksClient.fillNormal(vertices, direction, true);
        ForgeFaceData data = face.getFaceData();
        BakedQuad quad = new BakedQuad(vertices, face.tintIndex, direction, sprite, shade, data.ambientOcclusion());
        if (!ForgeFaceData.DEFAULT.equals(data)) {
            QuadTransformers.applyingLightmap(data.blockLight(), data.skyLight()).processInPlace(quad);
            QuadTransformers.applyingColor(data.color()).processInPlace(quad);
        }
        return quad;
    }

    private static int[] makeVertices(BlockFaceUV uvs, TextureAtlasSprite sprite, Direction orientation,
            float[] posDiv16, Transformation rotation, @Nullable BlockElementRotation partRotation) {
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            FaceInfo.VertexInfo info = FaceInfo.fromFacing(orientation).getVertexInfo(i);
            Vector3f vector = new Vector3f(posDiv16[info.xFace], posDiv16[info.yFace], posDiv16[info.zFace]);
            applyElementRotation(vector, partRotation);
            if (rotation != Transformation.identity()) {
                rotateVertexBy(vector, new Vector3f(0.5F, 0.5F, 0.5F), rotation.getMatrix(), new Vector3f(1.0F, 1.0F, 1.0F));
            }
            int at = i * 8;
            data[at] = Float.floatToRawIntBits(vector.x());
            data[at + 1] = Float.floatToRawIntBits(vector.y());
            data[at + 2] = Float.floatToRawIntBits(vector.z());
            data[at + 3] = -1;
            data[at + 4] = Float.floatToRawIntBits(PortSprites.getU(sprite, uvs.getU(i) / 16.0F));
            data[at + 5] = Float.floatToRawIntBits(PortSprites.getV(sprite, uvs.getV(i) / 16.0F));
        }
        return data;
    }

    private static float[] setupShape(Vector3f min, Vector3f max) {
        float[] shape = new float[Direction.values().length];
        shape[FaceInfo.Constants.MIN_X] = min.x() / 16.0F;
        shape[FaceInfo.Constants.MIN_Y] = min.y() / 16.0F;
        shape[FaceInfo.Constants.MIN_Z] = min.z() / 16.0F;
        shape[FaceInfo.Constants.MAX_X] = max.x() / 16.0F;
        shape[FaceInfo.Constants.MAX_Y] = max.y() / 16.0F;
        shape[FaceInfo.Constants.MAX_Z] = max.z() / 16.0F;
        return shape;
    }

    private static void applyElementRotation(Vector3f vec, @Nullable BlockElementRotation partRotation) {
        if (partRotation == null) {
            return;
        }
        Vector3f axis;
        Vector3f scale;
        switch (partRotation.axis()) {
            case X -> {
                axis = new Vector3f(1.0F, 0.0F, 0.0F);
                scale = new Vector3f(0.0F, 1.0F, 1.0F);
            }
            case Y -> {
                axis = new Vector3f(0.0F, 1.0F, 0.0F);
                scale = new Vector3f(1.0F, 0.0F, 1.0F);
            }
            case Z -> {
                axis = new Vector3f(0.0F, 0.0F, 1.0F);
                scale = new Vector3f(1.0F, 1.0F, 0.0F);
            }
            default -> throw new IllegalArgumentException("There are only 3 axes");
        }
        Quaternionf quaternion = new Quaternionf().rotationAxis(partRotation.angle() * (float) (Math.PI / 180.0), axis);
        if (partRotation.rescale()) {
            scale.mul(Math.abs(partRotation.angle()) == 22.5F ? RESCALE_22_5 : RESCALE_45);
            scale.add(1.0F, 1.0F, 1.0F);
        } else {
            scale.set(1.0F, 1.0F, 1.0F);
        }
        rotateVertexBy(vec, new Vector3f(partRotation.origin()), new Matrix4f().rotation(quaternion), scale);
    }

    private static void rotateVertexBy(Vector3f pos, Vector3f origin, Matrix4f transform, Vector3f scale) {
        Vector4f vector = transform.transform(new Vector4f(pos.x() - origin.x(), pos.y() - origin.y(), pos.z() - origin.z(), 1.0F));
        vector.mul(new Vector4f(scale, 1.0F));
        pos.set(vector.x() + origin.x(), vector.y() + origin.y(), vector.z() + origin.z());
    }

    private static void recalculateWinding(int[] vertices, Direction direction) {
        int[] copy = new int[vertices.length];
        System.arraycopy(vertices, 0, copy, 0, vertices.length);
        float[] bounds = new float[Direction.values().length];
        bounds[FaceInfo.Constants.MIN_X] = 999.0F;
        bounds[FaceInfo.Constants.MIN_Y] = 999.0F;
        bounds[FaceInfo.Constants.MIN_Z] = 999.0F;
        bounds[FaceInfo.Constants.MAX_X] = -999.0F;
        bounds[FaceInfo.Constants.MAX_Y] = -999.0F;
        bounds[FaceInfo.Constants.MAX_Z] = -999.0F;
        for (int i = 0; i < 4; i++) {
            int j = 8 * i;
            float x = Float.intBitsToFloat(copy[j]);
            float y = Float.intBitsToFloat(copy[j + 1]);
            float z = Float.intBitsToFloat(copy[j + 2]);
            if (x < bounds[FaceInfo.Constants.MIN_X]) bounds[FaceInfo.Constants.MIN_X] = x;
            if (y < bounds[FaceInfo.Constants.MIN_Y]) bounds[FaceInfo.Constants.MIN_Y] = y;
            if (z < bounds[FaceInfo.Constants.MIN_Z]) bounds[FaceInfo.Constants.MIN_Z] = z;
            if (x > bounds[FaceInfo.Constants.MAX_X]) bounds[FaceInfo.Constants.MAX_X] = x;
            if (y > bounds[FaceInfo.Constants.MAX_Y]) bounds[FaceInfo.Constants.MAX_Y] = y;
            if (z > bounds[FaceInfo.Constants.MAX_Z]) bounds[FaceInfo.Constants.MAX_Z] = z;
        }
        FaceInfo info = FaceInfo.fromFacing(direction);
        for (int i = 0; i < 4; i++) {
            int j = 8 * i;
            FaceInfo.VertexInfo vertex = info.getVertexInfo(i);
            float x = bounds[vertex.xFace];
            float y = bounds[vertex.yFace];
            float z = bounds[vertex.zFace];
            vertices[j] = Float.floatToRawIntBits(x);
            vertices[j + 1] = Float.floatToRawIntBits(y);
            vertices[j + 2] = Float.floatToRawIntBits(z);
            for (int k = 0; k < 4; k++) {
                int l = 8 * k;
                if (Mth.equal(x, Float.intBitsToFloat(copy[l])) && Mth.equal(y, Float.intBitsToFloat(copy[l + 1]))
                        && Mth.equal(z, Float.intBitsToFloat(copy[l + 2]))) {
                    vertices[j + 4] = copy[l + 4];
                    vertices[j + 5] = copy[l + 5];
                }
            }
        }
    }
}
