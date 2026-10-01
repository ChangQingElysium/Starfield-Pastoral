package com.stardew.craft.client.model;

import com.stardew.craft.port.PortItemData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.NaturalDecorKind;
import com.stardew.craft.block.decor.NaturalPlantBlock;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

/** Native models only; season selection never rewrites a saved block state. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NaturalDecorModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static volatile Map<NaturalDecorKind, List<FloatingParts>> floating = Map.of();
    private NaturalDecorModels() {}

    public record FloatingParts(BakedModel body, List<BakedQuad> ripples) {}

    public static FloatingParts floatingParts(NaturalDecorKind kind, int season) {
        var seasons = floating.get(kind);
        return seasons == null ? null : seasons.get(season);
    }

    public static ModelResourceLocation id(NaturalDecorKind kind, int season, int variantOrFrame) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/natural/" + kind.id + "/" + SEASONS[season] + "/" + variantOrFrame), "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (var kind : NaturalDecorKind.values()) for (int season = 0; season < 4; season++)
            for (int i = 0; i < kind.variants; i++)
                event.register(id(kind, season, i));
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        Map<NaturalDecorKind, List<FloatingParts>> floatingModels = new EnumMap<>(NaturalDecorKind.class);
        for (var kind : NaturalDecorKind.values()) {
            Surface[] items = new Surface[kind.variants];
            for (int variant = 0; variant < kind.variants; variant++) {
                BakedModel[] seasons = new BakedModel[4];
                for (int s = 0; s < 4; s++) seasons[s] = Objects.requireNonNull(event.getModels().get(id(kind, s, variant)));
                items[variant] = new Surface(seasons, false);
                if (kind.habitat == NaturalDecorKind.Habitat.SURFACE) {
                    var parts = new ArrayList<FloatingParts>();
                    for (BakedModel season : seasons) {
                        var body = new ArrayList<BakedQuad>();
                        var ripples = new ArrayList<BakedQuad>();
                        for (var quad : season.getQuads(null, null, RandomSource.create(0))) {
                            if (quad.getSprite().contents().name().getPath().endsWith(kind.id + "_ripples_0"))
                                ripples.add(quad);
                            else body.add(quad);
                        }
                        parts.add(new FloatingParts(new FloatingBody(season, List.copyOf(body)), List.copyOf(ripples)));
                    }
                    floatingModels.put(kind, List.copyOf(parts));
                }
            }
            for (BlockState state : ModBlocks.NATURAL_DECOR.get(kind.id).get().getStateDefinition().getPossibleStates()) {
                var model = items[Math.min(state.getValue(NaturalPlantBlock.VARIANT), kind.variants - 1)];
                event.getModels().put(BlockModelShaper.stateToModelLocation(state),
                        state.getValue(NaturalPlantBlock.IN_PLANTER) ? new Surface(model.seasons, true) : model);
            }
            var itemId = new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID, kind.id), "inventory");
            // Dormant plants disappear only in the world; keep their items recognizable.
            if (kind.hiddenInWinter()) for (int variant = 0; variant < items.length; variant++) {
                BakedModel[] itemSeasons = items[variant].seasons.clone();
                itemSeasons[3] = itemSeasons[0];
                items[variant] = new Surface(itemSeasons, false);
            }
            event.getModels().put(itemId, new PlantItem(Objects.requireNonNull(event.getModels().get(itemId)), items));
        }
        floating = Map.copyOf(floatingModels);
    }

    private static final class FloatingBody extends BakedModelWrapper<BakedModel> {
        private final List<BakedQuad> quads;
        FloatingBody(BakedModel original, List<BakedQuad> quads) { super(original); this.quads = quads; }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return side == null ? quads : List.of();
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType type) {
            return getQuads(state, side, random);
        }
    }

    private static final class Surface extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] seasons;
        private final List<BakedQuad>[] lowered;

        @SuppressWarnings("unchecked")
        Surface(BakedModel[] seasons, boolean inPlanter) {
            super(seasons[0]); this.seasons = seasons;
            lowered = inPlanter ? new List[4] : null;
            if (lowered != null) for (int s = 0; s < 4; s++) {
                var quads = new ArrayList<BakedQuad>();
                for (var q : seasons[s].getQuads(null, null, RandomSource.create(0))) {
                    int[] vertices = q.getVertices().clone(); int stride = vertices.length / 4;
                    for (int i = 0; i < 4; i++) vertices[i * stride + 1] = Float.floatToRawIntBits(
                            Float.intBitsToFloat(vertices[i * stride + 1]) - .25f);
                    quads.add(new BakedQuad(vertices, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion()));
                }
                lowered[s] = List.copyOf(quads);
            }
        }

        @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            getTransforms().getTransform(context).apply(leftHand, pose); return this;
        }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) { return List.of(this); }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return ChunkRenderTypeSet.of(RenderType.cutout());
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType type) {
            // Item passes use entity render types; only filter chunk layers for placed blocks.
            if (side != null || (state != null && type != null && type != RenderType.cutout())) return List.of();
            int season = TerrainSeasonTextures.currentTextureSet();
            return lowered == null ? seasons[season].getQuads(state, null, random, data, type) : lowered[season];
        }
        @Override public TextureAtlasSprite getParticleIcon() { return seasons[TerrainSeasonTextures.currentTextureSet()].getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return getParticleIcon(); }
    }

    private static final class PlantItem extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;
        PlantItem(BakedModel original, Surface[] variants) {
            super(original);
            overrides = new ItemOverrides() {
                @Override public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                        @Nullable LivingEntity entity, int seed) {
                    Integer v = PortItemData.getOrDefault(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(NaturalPlantBlock.VARIANT);
                    return variants[v == null ? 0 : Math.min(v, variants.length - 1)];
                }
            };
        }
        @Override public ItemOverrides getOverrides() { return overrides; }
    }
}
