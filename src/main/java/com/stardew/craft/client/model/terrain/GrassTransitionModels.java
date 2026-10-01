package com.stardew.craft.client.model.terrain;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainSoils;
import com.stardew.craft.block.terrain.TerrainVariants;
import java.util.ArrayList;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Authored seasonal surfaces and local top connections; never mutate terrain state. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GrassTransitionModels {
    private static final ModelProperty<Integer> CONNECTIONS = new ModelProperty<>();
    private static final ModelProperty<Integer> SOIL_FAMILIES = new ModelProperty<>();
    private static final ModelProperty<Integer> FARMLAND_EDGES = new ModelProperty<>();
    private static final ModelProperty<Integer> TEXTURE_SET = new ModelProperty<>();
    private static final int[][] OFFSETS = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};

    private GrassTransitionModels() {}

    private static List<Block> terrainBlocks() {
        return List.of(ModBlocks.GRASS_BLOCK.get(), ModBlocks.DIRT.get(), ModBlocks.DARK_GRASS_BLOCK.get(), ModBlocks.FARMLAND.get(), ModBlocks.SAND.get(), ModBlocks.SANDY_FARMLAND.get(), ModBlocks.HARD_SOIL.get(), ModBlocks.INFERTILE_FARMLAND.get());
    }

    private static ModelResourceLocation standalone(String path) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID, path), "standalone");
    }

    private static ModelResourceLocation overlayId(int season, int layer, int mask) {
        return standalone("block/terrain/" + TerrainSeasonTextures.directory(season)
                + (layer == 0 ? "grass_connections/" : "dark_grass_connections/") + mask);
    }

    private static ModelResourceLocation seasonalId(int season, BlockState state) {
        IntegerProperty property = variantProperty(state);
        return standalone(TerrainSeasonTextures.modelPath(season, BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(),
                property == null ? 0 : state.getValue(property), snowy(state)));
    }

    private static IntegerProperty variantProperty(BlockState state) {
        return TerrainSoils.farmland(state) ? net.minecraft.world.level.block.FarmBlock.MOISTURE : TerrainVariants.property(state);
    }

    private static ModelResourceLocation blendId(int season, boolean wet, int blend, int family) {
        return standalone(TerrainSeasonTextures.farmlandDirectory(season, family) + "blends/"
                + (wet ? "wet" : "dry") + "_" + (blend / 4) + "_" + (blend % 4));
    }

    @SubscribeEvent
    public static void registerOverlays(ModelEvent.RegisterAdditional event) {
        FertilizedSoilModels.register(event);
        FarmlandFamilyModels.register(event);
        for (int season = 0; season < 4; season++) {
            for (int layer = 0; layer < 2; layer++) for (int mask = 1; mask < 256; mask++)
                if (GrassConnectionMask.canonical(mask) == mask) event.register(overlayId(season, layer, mask));
            for (boolean wet : new boolean[]{false, true}) for (int blend = 0; blend < (wet ? 4 : 16); blend++)
                for (int family = 0; family < 3; family++) event.register(blendId(season, wet, blend, family));
            if (season > 0) for (Block block : terrainBlocks())
                for (BlockState state : block.getStateDefinition().getPossibleStates()) event.register(seasonalId(season, state));
        }
    }

    @SubscribeEvent
    public static void wrapTargets(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        TerrainFarmlandQuads[][][][] fertilizers = {FertilizedSoilModels.bake(models), FertilizedSoilModels.bakeSandy(models), FertilizedSoilModels.bakeInfertile(models)};
        ConnectedTopQuads connections = new ConnectedTopQuads();
        BakedModel[][][] overlays = new BakedModel[4][2][256];
        TerrainFarmlandQuads[][][] farmland = new TerrainFarmlandQuads[3][4][2];
        for (int season = 0; season < 4; season++) {
            for (int layer = 0; layer < 2; layer++) for (int mask = 1; mask < 256; mask++)
                overlays[season][layer][mask] = Objects.requireNonNull(models.get(overlayId(season, layer, GrassConnectionMask.canonical(mask))));
            for (int family = 0; family < 3; family++) for (int wet = 0; wet < 2; wet++) {
                BakedQuad[] quads = new BakedQuad[16];
                for (int blend = 0; blend < (wet == 1 ? 4 : 16); blend++) {
                    BakedModel model = Objects.requireNonNull(models.get(blendId(season, wet == 1, blend, family)));
                    quads[blend] = model.getQuads(null, null, RandomSource.create(0)).getFirst();
                }
                farmland[family][season][wet] = new TerrainFarmlandQuads(quads, wet == 1);
            }
        }
        FarmlandFamilyModels.bind(models, farmland, fertilizers);
        BakedModel[][][] recessedOverlays = new BakedModel[4][2][256];
        var recessedCache = new java.util.IdentityHashMap<BakedModel, BakedModel>();
        for (int season = 0; season < 4; season++) for (int layer = 0; layer < 2; layer++)
            for (int mask = 1; mask < 256; mask++)
                recessedOverlays[season][layer][mask] = recessedCache.computeIfAbsent(overlays[season][layer][mask], RecessedOverlay::new);
        for (Block block : terrainBlocks()) {
            IntegerProperty property = variantProperty(block.defaultBlockState());
            int count = property == null ? 1 : property.getPossibleValues().size();
            BakedModel[][] itemSurfaces = new BakedModel[4][count];
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation id = BlockModelShaper.stateToModelLocation(state);
                BakedModel[] surfaces = new BakedModel[4];
                surfaces[0] = Objects.requireNonNull(models.get(id), id::toString);
                for (int season = 1; season < 4; season++) surfaces[season] = Objects.requireNonNull(models.get(seasonalId(season, state)));
                if (!snowy(state)) {
                    int variant = property == null ? 0 : state.getValue(property);
                    for (int season = 0; season < 4; season++) itemSurfaces[season][variant] = surfaces[season];
                }
                boolean isFarmland = TerrainSoils.farmland(state);
                int family = TerrainSoils.family(state);
                boolean wet = isFarmland && state.getValue(net.minecraft.world.level.block.FarmBlock.MOISTURE) > 0;
                models.put(id, new ConnectedTop(surfaces, isFarmland ? recessedOverlays : overlays,
                        isFarmland ? farmland[family] : null, isFarmland ? fertilizers[family] : null, wet, connections));
            }
            ModelResourceLocation itemId = new ModelResourceLocation(BuiltInRegistries.BLOCK.getKey(block), "inventory");
            BakedModel item = Objects.requireNonNull(models.get(itemId), itemId::toString);
            models.put(itemId, new SeasonalItem(item, property, itemSurfaces));
        }
    }

    private static boolean snowy(BlockState state) {
        return state.hasProperty(BlockStateProperties.SNOWY) && state.getValue(BlockStateProperties.SNOWY);
    }

    private static boolean openTop(BlockAndTintGetter level, BlockPos pos) {
        BlockPos abovePos = pos.above();
        BlockState above = level.getBlockState(abovePos);
        return above.getFluidState().isEmpty() && !above.is(Blocks.SNOW)
                && above.getCollisionShape(level, abovePos).isEmpty();
    }

    private static int textureSet(ModelData data) {
        Integer set = data.get(TEXTURE_SET);
        return set == null ? TerrainSeasonTextures.currentTextureSet() : set;
    }

    /** Farmland's inset top is unculled like vanilla, including its lowered grass fringe. */
    private static final class RecessedOverlay extends BakedModelWrapper<BakedModel> {
        private final List<BakedQuad> top;

        RecessedOverlay(BakedModel original) {
            super(original);
            top = original.getQuads(null, Direction.UP, RandomSource.create(0)).stream()
                    .map(TerrainFarmlandQuads::lowerOverlay).toList();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return side == null ? top : List.of();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            return side == null ? top : List.of();
        }
    }

    private static final class ConnectedTop extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] surfaces;
        private final BakedModel[][][] overlays;
        @Nullable private final TerrainFarmlandQuads[][] farmland;
        @Nullable private final TerrainFarmlandQuads[][][] fertilizers;
        private final boolean wet;
        private final ConnectedTopQuads connections;

        private ConnectedTop(BakedModel[] surfaces, BakedModel[][][] overlays,
                @Nullable TerrainFarmlandQuads[][] farmland, @Nullable TerrainFarmlandQuads[][][] fertilizers, boolean wet,
                ConnectedTopQuads connections) {
            super(surfaces[0]);
            this.surfaces = surfaces;
            this.overlays = overlays;
            this.farmland = farmland;
            this.fertilizers = fertilizers;
            this.wet = wet;
            this.connections = connections;
        }

        private BakedModel surface(int set) {
            return surfaces[set];
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            int set = TerrainSeasonTextures.currentTextureSet();
            ModelData base = surface(set).getModelData(level, pos, state, data);
            int grass = 0, dark = 0, soil = 0, moisture = 0, peers = 0;
            // GrassConnectionMask uses its original three ranks; farmland receives
            // both grass types like dirt, while its inward edges are handled separately.
            int rank = state.is(ModBlocks.DARK_GRASS_BLOCK.get()) ? 2 : state.is(ModBlocks.GRASS_BLOCK.get()) ? 1 : 0;
            if (rank < 2 && !snowy(state) && openTop(level, pos)) {
                for (int i = 0; i < OFFSETS.length; i++) {
                    BlockPos neighbor = pos.offset(OFFSETS[i][0], 0, OFFSETS[i][1]);
                    BlockState adjacent = level.getBlockState(neighbor);
                    if (snowy(adjacent) || !openTop(level, neighbor)) continue;
                    if (adjacent.is(ModBlocks.GRASS_BLOCK.get())) grass |= 1 << i;
                    else if (adjacent.is(ModBlocks.DARK_GRASS_BLOCK.get())) dark |= 1 << i;
                    else if (farmland != null && TerrainSoils.bare(adjacent)) soil |= 1 << i;
                    else if (farmland != null && TerrainSoils.farmland(adjacent)) {
                        boolean neighborWet = adjacent.getValue(net.minecraft.world.level.block.FarmBlock.MOISTURE) > 0;
                        if (!wet && neighborWet) moisture |= 1 << i;
                        else if (wet == neighborWet && TerrainSoils.family(state) != TerrainSoils.family(adjacent))
                            peers |= 1 << (i + TerrainSoils.family(adjacent)*8);
                    }
                }
            }
            return base.derive().with(TEXTURE_SET, set)
                    .with(FertilizedSoilModels.FERTILIZER, farmland == null ? 0 : FertilizedSoilModels.index(pos))
                    .with(CONNECTIONS, GrassConnectionMask.connections(rank, grass, dark))
                    .with(SOIL_FAMILIES, peers)
                    .with(FARMLAND_EDGES, GrassConnectionMask.canonical(soil) | (GrassConnectionMask.canonical(moisture) << 8)).build();
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return surface(textureSet(data)).getRenderTypes(state, random, data);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            int set = textureSet(data);
            BakedModel surface = surface(set);
            boolean baseLayer = renderType == null || state == null
                    || surface.getRenderTypes(state, random, data).contains(renderType);
            List<BakedQuad> base = baseLayer ? surface.getQuads(state, side, random, data, renderType) : List.of();
            Integer edges = data.get(FARMLAND_EDGES);
            int peers = data.has(SOIL_FAMILIES) ? data.get(SOIL_FAMILIES) : 0;
            boolean topFace = farmland == null ? side == Direction.UP : side == null;
            Integer fertilizer = data.get(FertilizedSoilModels.FERTILIZER);
            boolean fertilized = fertilizers != null && fertilizer != null && fertilizer > 0;
            if (baseLayer && topFace && farmland != null && (fertilized || peers != 0 || (edges != null && edges != 0))) {
                int connections = edges == null ? 0 : edges;
                TerrainFarmlandQuads painter = fertilized ? fertilizers[set][fertilizer - 1][wet ? 1 : 0] : farmland[set][wet ? 1 : 0];
                base = painter.get(connections & 255, connections >>> 8, peers);
            }
            int mask = mask(data);
            if (!baseLayer || !topFace || mask == 0) return base;
            List<BakedQuad> paint = new ArrayList<>();
            for (int layer = 0; layer < 2; layer++) {
                int layerMask = (mask >> (8 * layer)) & 255;
                BakedModel overlay = overlays[set][layer][layerMask];
                if (overlay != null) paint.addAll(overlay.getQuads(state, farmland == null ? Direction.UP : null, random, ModelData.EMPTY, null));
            }
            return connections.compose(base, paint);
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return surface(TerrainSeasonTextures.currentTextureSet()).getParticleIcon();
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return surface(textureSet(data)).getParticleIcon(data);
        }

        private static int mask(ModelData data) {
            Integer value = data.get(CONNECTIONS);
            return value == null ? 0 : value;
        }
    }

    /** Resolve the same saved variant in the active season for GUI, hand and dropped items. */
    private static final class SeasonalItem extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        private SeasonalItem(BakedModel original, @Nullable IntegerProperty property, BakedModel[][] surfaces) {
            super(original);
            overrides = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                        @Nullable LivingEntity entity, int seed) {
                    Integer variant = property == null ? null : stack.getOrDefault(DataComponents.BLOCK_STATE,
                            BlockItemStateProperties.EMPTY).get(property);
                    return surfaces[TerrainSeasonTextures.currentTextureSet()][variant == null ? 0 : variant];
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }
}
