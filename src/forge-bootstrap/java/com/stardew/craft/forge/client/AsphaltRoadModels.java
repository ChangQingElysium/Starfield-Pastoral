package com.stardew.craft.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.block.terrain.AsphaltRoadConnections;
import com.stardew.craft.block.terrain.RoadMarkingBlock;
import com.stardew.craft.block.terrain.TerrainVariants;
import com.stardew.craft.forge.ForgeBootstrap;
import com.stardew.craft.forge.registry.ForgeBlocks;
import java.util.List;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Native cube and paint-plane quads; all 16px cells are authored, including curb clipping. */
@Mod.EventBusSubscriber(modid = ForgeBootstrap.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AsphaltRoadModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final ModelProperty<Integer> ROW = new ModelProperty<>();
    private static final ModelProperty<Integer> SEASON = new ModelProperty<>();

    private AsphaltRoadModels() {
    }

    private static ModelResourceLocation model(String path) {
        return new ModelResourceLocation(new ResourceLocation(ForgeBootstrap.MOD_ID, path), "standalone");
    }

    private static ModelResourceLocation item(String path) {
        return new ModelResourceLocation(new ResourceLocation(ForgeBootstrap.MOD_ID, path), "inventory");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (String season : SEASONS) {
            event.register(model("block/asphalt_road/" + season));
            event.register(model("block/asphalt_road/" + season + "_markings"));
            for (String style : new String[]{"dash", "double"}) {
                event.register(model("item/asphalt_road/" + season + "_" + style));
            }
        }
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        BakedModel[] roads = new BakedModel[4];
        BakedModel[] markings = new BakedModel[4];
        BakedModel[][] icons = new BakedModel[2][4];
        BakedQuad[][][] top = new BakedQuad[4][3][47];
        BakedQuad[][][] line = new BakedQuad[4][8][47];
        for (int season = 0; season < 4; season++) {
            String name = SEASONS[season];
            roads[season] = Objects.requireNonNull(event.getModels().get(model("block/asphalt_road/" + name)));
            markings[season] = Objects.requireNonNull(
                    event.getModels().get(model("block/asphalt_road/" + name + "_markings")));
            BakedQuad road = roads[season].getQuads(null, Direction.UP, RandomSource.create(0)).get(0);
            BakedQuad paint = markings[season].getQuads(null, null, RandomSource.create(0)).get(0);
            for (int row = 0; row < 47; row++) {
                for (int variant = 0; variant < 3; variant++) {
                    top[season][variant][row] = tile(road, variant, row, 48);
                }
                for (int column = 0; column < 8; column++) {
                    line[season][column][row] = tile(paint, column, row, 128);
                }
            }
            icons[0][season] = Objects.requireNonNull(
                    event.getModels().get(model("item/asphalt_road/" + name + "_dash")));
            icons[1][season] = Objects.requireNonNull(
                    event.getModels().get(model("item/asphalt_road/" + name + "_double")));
        }
        Road[] variants = new Road[3];
        for (BlockState state : ForgeBlocks.ASPHALT_ROAD.get().getStateDefinition().getPossibleStates()) {
            int variant = state.getValue(TerrainVariants.ASPHALT);
            variants[variant] = new Road(roads, top, variant);
            event.getModels().put(BlockModelShaper.stateToModelLocation(state), variants[variant]);
        }
        event.getModels().put(item("asphalt_road"), new SeasonalItem(
                Objects.requireNonNull(event.getModels().get(item("asphalt_road"))), variants, null));
        for (int style = 0; style < 2; style++) {
            var block = style == 0 ? ForgeBlocks.ROAD_DASH.get() : ForgeBlocks.ROAD_DOUBLE_LINE.get();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                event.getModels().put(BlockModelShaper.stateToModelLocation(state),
                        new Marking(markings, line, style * 4 + RoadMarkingBlock.rotationIndex(state)));
            }
            String id = style == 0 ? "road_dash" : "road_double_line";
            event.getModels().put(item(id), new SeasonalItem(
                    Objects.requireNonNull(event.getModels().get(item(id))), null, icons[style]));
        }
    }

    private static BakedQuad tile(BakedQuad source, int column, int row, int width) {
        int[] vertices = source.getVertices().clone();
        int stride = vertices.length / 4;
        TextureAtlasSprite sprite = source.getSprite();
        for (int i = 0; i < 4; i++) {
            int vertex = i * stride;
            float x = Float.intBitsToFloat(vertices[vertex]) > .5f ? 15.999f : .001f;
            float z = Float.intBitsToFloat(vertices[vertex + 2]) > .5f ? 15.999f : .001f;
            vertices[vertex + 4] = Float.floatToRawIntBits(sprite.getU((column * 16 + x) / width));
            vertices[vertex + 5] = Float.floatToRawIntBits(sprite.getV((row * 16 + z) / 752f));
        }
        return new BakedQuad(vertices, source.getTintIndex(), source.getDirection(), sprite,
                source.isShade(), source.hasAmbientOcclusion());
    }

    private static int season(ModelData data) {
        Integer value = data.get(SEASON);
        return value == null ? TerrainSeasonTextures.currentTextureSet() : value;
    }

    private static int row(ModelData data) {
        Integer value = data.get(ROW);
        return value == null ? 0 : value;
    }

    private static ModelData neighborhood(BlockAndTintGetter level, BlockPos road, ModelData data) {
        return data.derive().with(SEASON, TerrainSeasonTextures.currentTextureSet())
                .with(ROW, AsphaltRoadConnections.row(AsphaltRoadConnections.mask(level, road))).build();
    }

    private static final class Road extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] surfaces;
        private final BakedQuad[][][] tops;
        private final int variant;

        Road(BakedModel[] surfaces, BakedQuad[][][] tops, int variant) {
            super(surfaces[0]);
            this.surfaces = surfaces;
            this.tops = tops;
            this.variant = variant;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            getTransforms().getTransform(context).apply(leftHand, pose);
            return this;
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return List.of(this);
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return neighborhood(level, pos, data);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            if (side != Direction.UP) {
                return surfaces[season(data)].getQuads(state, side, random, data, renderType);
            }
            return state != null && renderType != null && renderType != RenderType.solid()
                    ? List.of()
                    : List.of(tops[season(data)][variant][row(data)]);
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return surfaces[TerrainSeasonTextures.currentTextureSet()].getParticleIcon();
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return surfaces[season(data)].getParticleIcon(data);
        }
    }

    private static final class Marking extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] surfaces;
        private final BakedQuad[][][] lines;
        private final int column;

        Marking(BakedModel[] surfaces, BakedQuad[][][] lines, int column) {
            super(surfaces[0]);
            this.surfaces = surfaces;
            this.lines = lines;
            this.column = column;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return neighborhood(level, pos.below(), data);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            if (side != null || renderType != null && renderType != RenderType.cutout()) {
                return List.of();
            }
            return List.of(lines[season(data)][column][row(data)]);
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return surfaces[TerrainSeasonTextures.currentTextureSet()].getParticleIcon();
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return surfaces[season(data)].getParticleIcon(data);
        }
    }

    private static final class SeasonalItem extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        SeasonalItem(BakedModel original, @Nullable Road[] variants, @Nullable BakedModel[] icons) {
            super(original);
            overrides = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                        @Nullable LivingEntity entity, int seed) {
                    if (icons != null) {
                        return icons[TerrainSeasonTextures.currentTextureSet()];
                    }
                    Tag raw = stack.getTagElement(BlockItem.BLOCK_STATE_TAG) == null
                            ? null
                            : stack.getTagElement(BlockItem.BLOCK_STATE_TAG).get(TerrainVariants.ASPHALT.getName());
                    int variant = raw == null
                            ? 0
                            : TerrainVariants.ASPHALT.getValue(raw.getAsString()).orElse(0);
                    return Objects.requireNonNull(variants)[variant];
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }
}
