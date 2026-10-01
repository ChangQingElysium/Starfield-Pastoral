package com.stardew.craft.client.model.terrain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import java.util.ArrayList;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

/** Retexture baked vanilla stairs: preserve every rotated UV, corner and culling face. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TownPavingStairModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private TownPavingStairModels() {}

    private static ModelResourceLocation id(String season) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/town_paving_stairs/" + season + "/stairs"), "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (String season : SEASONS) event.register(id(season));
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        TextureAtlasSprite[][] materials = new TextureAtlasSprite[4][2];
        for (int s = 0; s < 4; s++) {
            var model = Objects.requireNonNull(event.getModels().get(id(SEASONS[s])));
            materials[s][1] = model.getParticleIcon();
            materials[s][0] = model.getQuads(null, Direction.UP, RandomSource.create(0)).getFirst().getSprite();
        }
        for (BlockState state : ModBlocks.TOWN_PAVING_STAIRS.get().getStateDefinition().getPossibleStates()) {
            var key = BlockModelShaper.stateToModelLocation(state);
            event.getModels().put(key, new Seasonal(Objects.requireNonNull(event.getModels().get(key)), materials));
        }
        var item = new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID, "town_paving_stairs"), "inventory");
        event.getModels().put(item, new Seasonal(Objects.requireNonNull(event.getModels().get(item)), materials));
    }

    private static BakedQuad retexture(BakedQuad source, TextureAtlasSprite target) {
        TextureAtlasSprite from = source.getSprite();
        if (from == target) return source;
        int[] vertices = source.getVertices().clone(); int stride = vertices.length / 4;
        for (int i = 0; i < 4; i++) {
            int at = i * stride;
            float u = (Float.intBitsToFloat(vertices[at + 4]) - from.getU0()) / (from.getU1() - from.getU0());
            float v = (Float.intBitsToFloat(vertices[at + 5]) - from.getV0()) / (from.getV1() - from.getV0());
            vertices[at + 4] = Float.floatToRawIntBits(target.getU(u));
            vertices[at + 5] = Float.floatToRawIntBits(target.getV(v));
        }
        return new BakedQuad(vertices, source.getTintIndex(), source.getDirection(), target, source.isShade(), source.hasAmbientOcclusion());
    }

    private static final class Seasonal extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final TextureAtlasSprite[][] materials;
        private final List<List<List<BakedQuad>>> quads;
        Seasonal(BakedModel original, TextureAtlasSprite[][] materials) {
            super(original); this.materials = materials;
            var seasons = new ArrayList<List<List<BakedQuad>>>();
            for (int s = 0; s < 4; s++) {
                var faces = new ArrayList<List<BakedQuad>>();
                for (int d = 0; d < 7; d++) {
                    var result = new ArrayList<BakedQuad>();
                    for (var quad : original.getQuads(null, d == 6 ? null : Direction.values()[d], RandomSource.create(0))) {
                        int material = quad.getSprite().contents().name().getPath().endsWith("/tread") ? 0 : 1;
                        result.add(retexture(quad, materials[s][material]));
                    }
                    faces.add(List.copyOf(result));
                }
                seasons.add(List.copyOf(faces));
            }
            quads = List.copyOf(seasons);
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return quads.get(TerrainSeasonTextures.currentTextureSet()).get(side == null ? 6 : side.ordinal());
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType type) { return getQuads(state, side, random); }
        @Override public TextureAtlasSprite getParticleIcon() { return materials[TerrainSeasonTextures.currentTextureSet()][1]; }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return getParticleIcon(); }
        @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean left) {
            getTransforms().getTransform(context).apply(left, pose); return this;
        }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) { return List.of(this); }
    }
}
