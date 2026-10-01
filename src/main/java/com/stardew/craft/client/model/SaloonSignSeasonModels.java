package com.stardew.craft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Authored seasonal sign artwork; the saved block and facing stay unchanged. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SaloonSignSeasonModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final String[] SIGNS = {"saloon_sign", "food_spirits_sign"};
    private SaloonSignSeasonModels() {}

    private static ModelResourceLocation id(String sign, String season) {
        return new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID,
                "block/decor/house/" + sign + "/" + season + "/" + sign), "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (String sign : SIGNS) for (String season : SEASONS) event.register(id(sign, season));
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        bakeSign(event, "saloon_sign", ModBlocks.SALOON_SIGN.get());
        bakeSign(event, "food_spirits_sign", ModBlocks.FOOD_SPIRITS_SIGN.get());
    }

    private static void bakeSign(ModelEvent.ModifyBakingResult event, String sign, MapDecorStaticBlock block) {
        var models = event.getModels();
        BakedModel[] seasons = new BakedModel[4];
        for (int s = 0; s < 4; s++) seasons[s] = Objects.requireNonNull(models.get(id(sign, SEASONS[s])));
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            var key = BlockModelShaper.stateToModelLocation(state);
            models.put(key, new Seasonal(Objects.requireNonNull(models.get(key)), seasons,
                    state.getValue(MapDecorStaticBlock.FACING),
                    state.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.MAIN));
        }
        var item = new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, sign), "inventory");
        models.put(item, new Seasonal(Objects.requireNonNull(models.get(item)), seasons, Direction.NORTH, true));
    }

    private static BakedQuad rotate(BakedQuad source, Direction facing) {
        int turns = switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        if (turns == 0) return source;
        int[] vertices = source.getVertices().clone();
        int stride = vertices.length / 4;
        for (int i = 0; i < 4; i++) {
            int at = i * stride;
            float x = Float.intBitsToFloat(vertices[at]), z = Float.intBitsToFloat(vertices[at + 2]);
            int packed = vertices[at + 7], nx = (byte) packed, nz = (byte) (packed >> 16);
            for (int t = 0; t < turns; t++) {
                float previousX = x; x = 1 - z; z = previousX;
                int previousNx = nx; nx = -nz; nz = previousNx;
            }
            vertices[at] = Float.floatToRawIntBits(x);
            vertices[at + 2] = Float.floatToRawIntBits(z);
            vertices[at + 7] = (packed & 0xff00ff00) | (nx & 255) | ((nz & 255) << 16);
        }
        Direction face = source.getDirection();
        if (face.getAxis().isHorizontal()) for (int t = 0; t < turns; t++) face = face.getClockWise();
        return new BakedQuad(vertices, source.getTintIndex(), face, source.getSprite(), source.isShade(), source.hasAmbientOcclusion());
    }

    private static final class Seasonal extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] seasons;
        private final List<List<BakedQuad>> quads;
        Seasonal(BakedModel original, BakedModel[] seasons, Direction facing, boolean visible) {
            super(original);
            this.seasons = seasons;
            quads = java.util.Arrays.stream(seasons).map(model -> (visible ? model.getQuads(null, null, RandomSource.create(0)) : List.<BakedQuad>of())
                    .stream().map(quad -> rotate(quad, facing)).toList()).toList();
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType type) {
            if (side != null || (state != null && type != null && type != RenderType.cutout())) return List.of();
            return quads.get(TerrainSeasonTextures.currentTextureSet());
        }
        @Override public net.neoforged.neoforge.client.ChunkRenderTypeSet getRenderTypes(
                BlockState state, RandomSource random, ModelData data) {
            return net.neoforged.neoforge.client.ChunkRenderTypeSet.of(RenderType.cutout());
        }
        @Override public TextureAtlasSprite getParticleIcon() { return seasons[TerrainSeasonTextures.currentTextureSet()].getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return getParticleIcon(); }
        @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean left) {
            getTransforms().getTransform(context).apply(left, pose); return this;
        }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) { return List.of(this); }
    }
}
