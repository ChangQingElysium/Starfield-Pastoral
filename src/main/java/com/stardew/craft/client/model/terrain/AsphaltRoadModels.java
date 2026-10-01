package com.stardew.craft.client.model.terrain;

import com.stardew.craft.port.PortItemData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.AsphaltRoadConnections;
import com.stardew.craft.block.terrain.RoadMarkingBlock;
import com.stardew.craft.block.terrain.TerrainVariants;
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
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Native cube and paint-plane quads; all 16px cells are authored, including curb clipping. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AsphaltRoadModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final ModelProperty<Integer> ROW = new ModelProperty<>();
    private static final ModelProperty<Integer> SEASON = new ModelProperty<>();

    private AsphaltRoadModels() {}

    private static ModelResourceLocation model(String path) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID, path), "standalone");
    }
    private static ModelResourceLocation item(String path) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID, path), "inventory");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (String season : SEASONS) {
            event.register(model("block/asphalt_road/" + season));
            event.register(model("block/asphalt_road/" + season + "_markings"));
            for (String style : new String[]{"dash", "double"}) event.register(model("item/asphalt_road/" + season + "_" + style));
        }
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        BakedModel[] roads = new BakedModel[4], markings = new BakedModel[4];
        BakedModel[][] icons = new BakedModel[2][4];
        BakedQuad[][][] top = new BakedQuad[4][3][47], line = new BakedQuad[4][8][47];
        for (int season = 0; season < 4; season++) {
            String name = SEASONS[season];
            roads[season] = Objects.requireNonNull(event.getModels().get(model("block/asphalt_road/" + name)));
            markings[season] = Objects.requireNonNull(event.getModels().get(model("block/asphalt_road/" + name + "_markings")));
            BakedQuad road = com.stardew.craft.port.PortJava.getFirst(roads[season].getQuads(null, Direction.UP, RandomSource.create(0)));
            BakedQuad paint = com.stardew.craft.port.PortJava.getFirst(markings[season].getQuads(null, null, RandomSource.create(0)));
            for (int row = 0; row < 47; row++) {
                for (int v = 0; v < 3; v++) top[season][v][row] = tile(road, v, row, 48);
                for (int c = 0; c < 8; c++) line[season][c][row] = tile(paint, c, row, 128);
            }
            icons[0][season] = Objects.requireNonNull(event.getModels().get(model("item/asphalt_road/" + name + "_dash")));
            icons[1][season] = Objects.requireNonNull(event.getModels().get(model("item/asphalt_road/" + name + "_double")));
        }
        Road[] variants = new Road[3];
        for (BlockState state : ModBlocks.ASPHALT_ROAD.get().getStateDefinition().getPossibleStates()) {
            int v = state.getValue(TerrainVariants.ASPHALT);
            variants[v] = new Road(roads, top, v);
            event.getModels().put(BlockModelShaper.stateToModelLocation(state), variants[v]);
        }
        event.getModels().put(item("asphalt_road"), new SeasonalItem(
                Objects.requireNonNull(event.getModels().get(item("asphalt_road"))), variants, null));
        for (int style = 0; style < 2; style++) {
            var block = style == 0 ? ModBlocks.ROAD_DASH.get() : ModBlocks.ROAD_DOUBLE_LINE.get();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                event.getModels().put(BlockModelShaper.stateToModelLocation(state),
                        new Marking(markings, line, style * 4 + RoadMarkingBlock.rotationIndex(state)));
            }
            String id = style == 0 ? "road_dash" : "road_double_line";
            event.getModels().put(item(id), new SeasonalItem(Objects.requireNonNull(event.getModels().get(item(id))), null, icons[style]));
        }
    }

    private static BakedQuad tile(BakedQuad source, int column, int row, int width) {
        int[] vertices = source.getVertices().clone();
        int stride = vertices.length / 4;
        TextureAtlasSprite sprite = source.getSprite();
        for (int i = 0; i < 4; i++) {
            int v = i * stride;
            float x = Float.intBitsToFloat(vertices[v]) > .5f ? 15.999f : .001f;
            float z = Float.intBitsToFloat(vertices[v + 2]) > .5f ? 15.999f : .001f;
            vertices[v + 4] = Float.floatToRawIntBits(sprite.getU((column * 16 + x) / width));
            vertices[v + 5] = Float.floatToRawIntBits(sprite.getV((row * 16 + z) / 752f));
        }
        return new BakedQuad(vertices, source.getTintIndex(), source.getDirection(), sprite, source.isShade(), source.hasAmbientOcclusion());
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
            super(surfaces[0]); this.surfaces = surfaces; this.tops = tops; this.variant = variant;
        }
        // The raw parent contains the whole atlas; item passes must retain this wrapper.
        @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            getTransforms().getTransform(context).apply(leftHand, pose);
            return this;
        }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return List.of(this);
        }
        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return neighborhood(level, pos, data);
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            if (side != Direction.UP) return surfaces[season(data)].getQuads(state, side, random, data, renderType);
            return state != null && renderType != null && renderType != RenderType.solid() ? List.of() : List.of(tops[season(data)][variant][row(data)]);
        }
        @Override public TextureAtlasSprite getParticleIcon() { return surfaces[TerrainSeasonTextures.currentTextureSet()].getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return surfaces[season(data)].getParticleIcon(data); }
    }

    private static final class Marking extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] surfaces;
        private final BakedQuad[][][] lines;
        private final int column;
        Marking(BakedModel[] surfaces, BakedQuad[][][] lines, int column) {
            super(surfaces[0]); this.surfaces = surfaces; this.lines = lines; this.column = column;
        }
        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return neighborhood(level, pos.below(), data);
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            if (side != null || renderType != null && renderType != RenderType.cutout()) return List.of();
            return List.of(lines[season(data)][column][row(data)]);
        }
        @Override public TextureAtlasSprite getParticleIcon() { return surfaces[TerrainSeasonTextures.currentTextureSet()].getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return surfaces[season(data)].getParticleIcon(data); }
    }

    private static final class SeasonalItem extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;
        SeasonalItem(BakedModel original, @Nullable Road[] variants, @Nullable BakedModel[] icons) {
            super(original);
            overrides = new ItemOverrides() {
                @Override public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                        @Nullable LivingEntity entity, int seed) {
                    if (icons != null) return icons[TerrainSeasonTextures.currentTextureSet()];
                    Integer v = PortItemData.getOrDefault(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(TerrainVariants.ASPHALT);
                    return Objects.requireNonNull(variants)[v == null ? 0 : v];
                }
            };
        }
        @Override public ItemOverrides getOverrides() { return overrides; }
    }
}
