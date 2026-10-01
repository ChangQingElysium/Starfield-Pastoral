package com.stardew.craft.client.model;

import com.google.gson.JsonObject;
import com.stardew.craft.block.decor.GardenPlanterBlock;
import com.stardew.craft.block.crop.giant.GiantCropBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.SimpleUnbakedGeometry;
import org.joml.Vector3f;

/** Authored quad winding is retained, including inward shells and opposite thin-leaf faces. */
public final class CropModelGeometry extends SimpleUnbakedGeometry<CropModelGeometry> {
    public static final IGeometryLoader<CropModelGeometry> LOADER = (json, context) -> new CropModelGeometry(json);
    private final List<float[]> faces = new ArrayList<>();
    private final Map<BlockPos, CropModelGeometry> giantParts = new java.util.HashMap<>();

    private CropModelGeometry(JsonObject json) {
        for (var value : json.getAsJsonArray("quads")) {
            var values = value.getAsJsonArray();
            if (values.size() != 20) throw new IllegalArgumentException("Crop quad requires four xyz/uv vertices");
            float[] face = new float[20];
            for (int i = 0; i < face.length; i++) {
                face[i] = values.get(i).getAsFloat();
                if (!Float.isFinite(face[i])) throw new IllegalArgumentException("Non-finite crop vertex");
            }
            faces.add(face);
        }
        if (json.has("giant_parts")) {
            for (var entry : json.getAsJsonObject("giant_parts").entrySet()) {
                String[] xyz = entry.getKey().split(",");
                JsonObject part = new JsonObject();
                part.add("quads", entry.getValue());
                giantParts.put(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]),
                        Integer.parseInt(xyz[2])), new CropModelGeometry(part));
            }
        }
    }

    @Override
    protected void addQuads(IGeometryBakingContext context, IModelBuilder<?> builder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        TextureAtlasSprite sprite = sprites.apply(context.getMaterial("crop"));
        var transform = state.getRotation().compose(context.getRootTransform()).blockCenterToCorner().getMatrix();
        for (float[] face : faces) {
            int[] data = new int[32];
            Vector3f[] points = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                int f = i * 5, v = i * 8;
                points[i] = transform.transformPosition(new Vector3f(face[f] / 16, face[f + 1] / 16, face[f + 2] / 16));
                data[v] = Float.floatToRawIntBits(points[i].x);
                data[v + 1] = Float.floatToRawIntBits(points[i].y);
                data[v + 2] = Float.floatToRawIntBits(points[i].z);
                data[v + 3] = -1;
                data[v + 4] = Float.floatToRawIntBits(sprite.getU(face[f + 3]));
                data[v + 5] = Float.floatToRawIntBits(sprite.getV(face[f + 4]));
            }
            Vector3f normal = new Vector3f(points[1]).sub(points[0])
                    .cross(new Vector3f(points[2]).sub(points[0])).normalize();
            if (!normal.isFinite()) throw new IllegalArgumentException("Degenerate crop face");
            int packed = ((int) (normal.x * 127) & 255) | (((int) (normal.y * 127) & 255) << 8)
                    | (((int) (normal.z * 127) & 255) << 16);
            for (int i = 0; i < 4; i++) data[i * 8 + 7] = packed;
            // No FaceBakery bounds sorting: it would destroy the signed shell and folded-leaf UV order.
            builder.addUnculledFace(new BakedQuad(data, -1, FaceBakery.calculateFacing(data), sprite, true, false));
        }
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides) {
        Map<BlockPos, BakedModel> parts = new java.util.HashMap<>();
        giantParts.forEach((cell, geometry) -> parts.put(cell,
                geometry.bakeRaw(context, baker, sprites, state, overrides)));
        return new OnSoil(bakeRaw(context, baker, sprites, state, overrides), Map.copyOf(parts));
    }

    private BakedModel bakeRaw(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides) {
        return super.bake(context, baker, sprites, state, overrides);
    }

    private static final class OnSoil extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private static final ModelProperty<Float> OFFSET = new ModelProperty<>();
        private static final ModelProperty<BlockPos> CELL = new ModelProperty<>();
        private record Placement(BlockPos cell, float offset) {}
        private final Map<Placement, List<BakedQuad>> shifted = new ConcurrentHashMap<>();
        private final Map<BlockPos, BakedModel> parts;

        private OnSoil(BakedModel original, Map<BlockPos, BakedModel> parts) {
            super(original);
            this.parts = parts;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            BlockPos root = pos;
            BlockPos cell = null;
            if (!parts.isEmpty() && state.getBlock() instanceof GiantCropBlock giant) {
                BlockPos main = giant.findMainPos(level, pos, state);
                if (main != null) root = main;
                cell = main == null ? new BlockPos(99, 99, 99) : pos.subtract(main);
            }
            BlockPos below = root.below();
            BlockState support = level.getBlockState(below);
            float offset = 0;
            if (support.getBlock() instanceof GardenPlanterBlock) offset = -.25F;
            else if (support.getBlock() instanceof FarmBlock) {
                var shape = support.getCollisionShape(level, below);
                if (!shape.isEmpty()) offset = (float) shape.max(Direction.Axis.Y) - 1;
            }
            var builder = data.derive().with(OFFSET, offset);
            if (cell != null) builder.with(CELL, cell);
            return builder.build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                       ModelData data, @Nullable RenderType type) {
            if (side != null) return List.of();
            Float offset = data.get(OFFSET);
            BlockPos cell = data.get(CELL);
            BakedModel selected = cell == null ? originalModel : parts.get(cell);
            if (selected == null) return List.of();
            // Item/Garden Pot rendering supplies no world ModelData; the pot pose already owns its soil height.
            if (offset == null || offset == 0) return selected.getQuads(state, null, random, data, type);
            return shifted.computeIfAbsent(new Placement(cell, offset), placement -> selected.getQuads(state, null, random, ModelData.EMPTY, type)
                    .stream().map(q -> {
                        int[] vertices = q.getVertices().clone();
                        for (int i = 0; i < 4; i++) vertices[i * 8 + 1] = Float.floatToRawIntBits(
                                Float.intBitsToFloat(vertices[i * 8 + 1]) + placement.offset());
                        return new BakedQuad(vertices, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), false);
                    }).toList());
        }
    }
}
