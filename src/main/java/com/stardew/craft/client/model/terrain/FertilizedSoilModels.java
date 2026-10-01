package com.stardew.craft.client.model.terrain;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.client.ClientFertilizerCache;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Fertilizer is part of the native soil surface, not a second surface or world render pass. */
public final class FertilizedSoilModels {
    static final ModelProperty<Integer> FERTILIZER = new ModelProperty<>();
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static BakedModel[][] pots = new BakedModel[2][FertilizerType.values().length];

    private FertilizedSoilModels() {}

    private static ModelResourceLocation id(String path) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/terrain/fertilized/" + path), "standalone");
    }

    private static String path(String target, FertilizerType type, int wet) {
        return target + "/" + type.getSerializedName() + (wet == 0 ? "_dry" : "_wet");
    }

    static void register(ModelEvent.RegisterAdditional event) {
        for (String season : SEASONS) { event.register(id(season)); event.register(id("sandy/" + season)); event.register(id("infertile/" + season)); }
        for (int wet = 0; wet < 2; wet++) for (FertilizerType type : FertilizerType.values()) {
            event.register(id(path("vanilla", type, wet)));
            event.register(id(path("pot", type, wet)));
        }
    }

    static TerrainFarmlandQuads[][][] bake(Map<ModelResourceLocation, BakedModel> models) {
        int count = FertilizerType.values().length;
        TerrainFarmlandQuads[][][] result = bakeFamily(models, "");
        BakedModel[][] vanilla = new BakedModel[2][count];
        BakedModel[][] nextPots = new BakedModel[2][count];
        for (int wet = 0; wet < 2; wet++) for (FertilizerType type : FertilizerType.values()) {
            vanilla[wet][type.ordinal()] = Objects.requireNonNull(models.get(id(path("vanilla", type, wet))));
            nextPots[wet][type.ordinal()] = Objects.requireNonNull(models.get(id(path("pot", type, wet))));
        }
        pots = nextPots;
        for (BlockState state : Blocks.FARMLAND.getStateDefinition().getPossibleStates()) {
            var key = BlockModelShaper.stateToModelLocation(state);
            models.put(key, new VanillaSurface(Objects.requireNonNull(models.get(key)), vanilla[state.getValue(FarmBlock.MOISTURE) > 0 ? 1 : 0]));
        }
        return result;
    }

    static TerrainFarmlandQuads[][][] bakeSandy(Map<ModelResourceLocation, BakedModel> models) {
        return bakeFamily(models, "sandy/");
    }

    static TerrainFarmlandQuads[][][] bakeInfertile(Map<ModelResourceLocation, BakedModel> models) {
        return bakeFamily(models, "infertile/");
    }

    private static TerrainFarmlandQuads[][][] bakeFamily(Map<ModelResourceLocation, BakedModel> models, String prefix) {
        int count = FertilizerType.values().length;
        TerrainFarmlandQuads[][][] result = new TerrainFarmlandQuads[4][count][2];
        for (int season = 0; season < 4; season++) {
            BakedQuad atlas = Objects.requireNonNull(models.get(id(prefix + SEASONS[season])))
                    .getQuads(null, null, RandomSource.create(0)).getFirst();
            for (int fertilizer = 0; fertilizer < count; fertilizer++) for (int wet = 0; wet < 2; wet++) {
                BakedQuad[] blends = new BakedQuad[16];
                for (int blend = 0; blend < (wet == 0 ? 16 : 4); blend++)
                    blends[blend] = tile(atlas, fertilizer, wet == 0 ? blend : 16 + blend);
                result[season][fertilizer][wet] = new TerrainFarmlandQuads(blends, wet == 1);
            }
        }
        return result;
    }

    static int index(BlockPos pos) {
        FertilizerType type = ClientFertilizerCache.getFertilizer(pos);
        return type == null ? 0 : type.ordinal() + 1;
    }

    public static BakedModel gardenPot(BakedModel original, BlockState state, @Nullable FertilizerType type) {
        return type == null ? original : pots[state.getValue(FarmBlock.MOISTURE) > 0 ? 1 : 0][type.ordinal()];
    }

    private static BakedQuad tile(BakedQuad source, int column, int row) {
        int[] vertices = source.getVertices().clone();
        int stride = vertices.length / 4;
        var sprite = source.getSprite();
        for (int v = 0; v < vertices.length; v += stride) {
            float x = Float.intBitsToFloat(vertices[v]) > .5f ? 15.999f : .001f;
            float z = Float.intBitsToFloat(vertices[v + 2]) > .5f ? 15.999f : .001f;
            vertices[v + 4] = Float.floatToRawIntBits(sprite.getU((column * 16 + x) / 144f));
            vertices[v + 5] = Float.floatToRawIntBits(sprite.getV((row * 16 + z) / 320f));
        }
        return new BakedQuad(vertices, source.getTintIndex(), source.getDirection(), sprite, source.isShade(), source.hasAmbientOcclusion());
    }

    private static final class VanillaSurface extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] fertilized;

        VanillaSurface(BakedModel original, BakedModel[] fertilized) {
            super(original);
            this.fertilized = fertilized;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return originalModel.getModelData(level, pos, state, data).derive().with(FERTILIZER, index(pos)).build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            Integer fertilizer = data.get(FERTILIZER);
            BakedModel model = side == null && fertilizer != null && fertilizer > 0 ? fertilized[fertilizer - 1] : originalModel;
            return model.getQuads(state, side, random, data, renderType);
        }
    }
}
