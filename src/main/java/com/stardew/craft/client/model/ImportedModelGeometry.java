package com.stardew.craft.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import com.stardew.craft.model.ModelGeometry;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.SimpleModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.SimpleUnbakedGeometry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;

/** Static baked rest pose for generic/Geo imports; standard item display transforms apply unchanged. */
public final class ImportedModelGeometry extends SimpleUnbakedGeometry<ImportedModelGeometry> {
    public static final IGeometryLoader<ImportedModelGeometry> LOADER = ImportedModelGeometry::read;
    private final List<Part> parts;
    private final List<AuthoredQuad> quads;

    private ImportedModelGeometry(List<Part> parts, List<AuthoredQuad> quads) {
        this.parts = List.copyOf(parts);
        this.quads = List.copyOf(quads);
    }

    private static ImportedModelGeometry read(JsonObject json, JsonDeserializationContext context) {
        List<Part> parts = new ArrayList<>();
        for (var value : json.getAsJsonArray("parts")) {
            JsonObject part = value.getAsJsonObject();
            var faces = new EnumMap<Direction, BlockElementFace>(Direction.class);
            for (var face : part.getAsJsonObject("faces").entrySet()) {
                faces.put(Direction.byName(face.getKey()), context.deserialize(face.getValue(), BlockElementFace.class));
            }
            BlockElement element = new BlockElement(ModelGeometry.vector(part.getAsJsonArray("from")),
                ModelGeometry.vector(part.getAsJsonArray("to")), faces, null,
                !part.has("shade") || part.get("shade").getAsBoolean(),
                net.neoforged.neoforge.client.model.ExtraFaceData.read(part.get("neoforge_data"),
                    net.neoforged.neoforge.client.model.ExtraFaceData.DEFAULT));
            // Convert pixel-space translation to block units; rotation and scale are unchanged.
            Matrix4f transform = ModelGeometry.transform(part);
            transform.m30(transform.m30() / 16).m31(transform.m31() / 16).m32(transform.m32() / 16);
            parts.add(new Part(element, transform));
        }
        List<AuthoredQuad> quads = new ArrayList<>();
        if (json.has("quads")) for (var value : json.getAsJsonArray("quads")) {
            JsonObject quad = value.getAsJsonObject();
            var values = quad.getAsJsonArray("vertices");
            if (values.size() != 20) throw new IllegalArgumentException("Imported quad requires four xyz/uv vertices");
            float[] vertices = new float[20];
            for (int i = 0; i < vertices.length; i++) {
                vertices[i] = values.get(i).getAsFloat();
                if (!Float.isFinite(vertices[i])) throw new IllegalArgumentException("Non-finite imported vertex");
            }
            int emission = quad.has("light_emission") ? quad.get("light_emission").getAsInt() : 0;
            if (emission < 0 || emission > 15) throw new IllegalArgumentException("Invalid imported face emission");
            quads.add(new AuthoredQuad(vertices, quad.get("texture").getAsString(),
                !quad.has("shade") || quad.get("shade").getAsBoolean(), emission,
                quad.has("tintindex") ? quad.get("tintindex").getAsInt() : -1));
        }
        return new ImportedModelGeometry(parts, quads);
    }

    @Override
    protected void addQuads(IGeometryBakingContext context, IModelBuilder<?> builder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        Matrix4f outer = state.getRotation().compose(context.getRootTransform()).blockCenterToCorner().getMatrix();
        for (Part part : parts) {
            var transformer = QuadTransformers.applying(new Transformation(new Matrix4f(outer).mul(part.transform())));
            for (var entry : part.element().faces.entrySet()) {
                var face = entry.getValue();
                BakedQuad quad = BlockModel.bakeFace(part.element(), face, sprites.apply(context.getMaterial(face.texture())),
                    entry.getKey(), new SimpleModelState(Transformation.identity()));
                transformer.processInPlace(quad);
                // Recalculate the lighting direction after arbitrary bone/cube rotations.
                builder.addUnculledFace(new BakedQuad(quad.getVertices(), quad.getTintIndex(),
                    FaceBakery.calculateFacing(quad.getVertices()), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion()));
            }
        }
        for (AuthoredQuad quad : quads) {
            builder.addUnculledFace(quad.bake(sprites.apply(context.getMaterial(quad.texture())), outer));
        }
    }

    private record Part(BlockElement element, Matrix4f transform) {}

    /** Mesh faces keep their authored winding and UVs; xyz is in pixels, uv in sprite fractions. */
    private record AuthoredQuad(float[] vertices, String texture, boolean shade, int emission, int tintIndex) {
        BakedQuad bake(TextureAtlasSprite sprite, Matrix4f transform) {
            int[] data = new int[32];
            Vector3f[] points = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                int f = i * 5, v = i * 8;
                points[i] = transform.transformPosition(new Vector3f(vertices[f] / 16,
                    vertices[f + 1] / 16, vertices[f + 2] / 16));
                data[v] = Float.floatToRawIntBits(points[i].x);
                data[v + 1] = Float.floatToRawIntBits(points[i].y);
                data[v + 2] = Float.floatToRawIntBits(points[i].z);
                data[v + 3] = -1;
                data[v + 4] = Float.floatToRawIntBits(sprite.getU(vertices[f + 3]));
                data[v + 5] = Float.floatToRawIntBits(sprite.getV(vertices[f + 4]));
            }
            Vector3f normal = new Vector3f(points[1]).sub(points[0])
                .cross(new Vector3f(points[2]).sub(points[0])).normalize();
            if (!normal.isFinite()) throw new IllegalArgumentException("Degenerate imported face");
            int packed = ((int) (normal.x * 127) & 255) | (((int) (normal.y * 127) & 255) << 8)
                | (((int) (normal.z * 127) & 255) << 16);
            for (int i = 0; i < 4; i++) data[i * 8 + 7] = packed;
            BakedQuad quad = new BakedQuad(data, tintIndex, FaceBakery.calculateFacing(data), sprite, shade, false);
            QuadTransformers.settingEmissivity(emission).processInPlace(quad);
            return quad;
        }
    }
}
