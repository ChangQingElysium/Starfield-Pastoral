package com.stardew.craft.client.model.terrain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainVariants;
import com.stardew.craft.block.terrain.TownPavingConnections;
import com.stardew.craft.block.terrain.PalePavingConnections;
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
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Selects authored 16px cells on native cube faces. Borders are already composited: no overlay z-fighting. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TownPavingModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final ModelProperty<Integer> POSITION = new ModelProperty<>();
    private static final ModelProperty<Integer> RED_TRANSITION = new ModelProperty<>();
    private static final ModelProperty<Integer> SEASON = new ModelProperty<>();
    private static final ModelProperty<Integer> PALE_MASK = new ModelProperty<>();

    private TownPavingModels() {}

    private static ModelResourceLocation id(String family, int season) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/" + family + "/" + SEASONS[season]), "standalone");
    }

    private static ModelResourceLocation paleId(String part, int season) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/pale_paving/" + SEASONS[season] + "/" + part), "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (int season = 0; season < 4; season++) {
            event.register(id("town_paving", season));
            event.register(id("plaza_red_bricks", season));
            event.register(id("plaza_red_bricks/transition", season));
            event.register(paleId("plain", season));
            for (int mask = 0; mask < 256; mask++) {
                if (PalePavingConnections.canonical(mask) == mask) {
                    event.register(paleId("grass_connections/" + mask, season));
                }
            }
        }
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        BakedQuad[][] transitions = new BakedQuad[4][47];
        for (int season = 0; season < 4; season++) {
            BakedModel model = Objects.requireNonNull(event.getModels().get(id("plaza_red_bricks/transition", season)));
            BakedQuad source = model.getQuads(null, Direction.UP, RandomSource.create(0)).getFirst();
            for (int row = 0; row < 47; row++) transitions[season][row] = tile(source, 0, row, 16);
        }
        bakeFamily(event, "town_paving", ModBlocks.TOWN_PAVING.get(), transitions);
        bakeFamily(event, "plaza_red_bricks", ModBlocks.PLAZA_RED_BRICKS.get(), null);
        bakePale(event);
    }

    private static void bakePale(ModelEvent.ModifyBakingResult event) {
        BakedModel[] plain = new BakedModel[4];
        BakedModel[][] connected = new BakedModel[4][256];
        for (int season = 0; season < 4; season++) {
            plain[season] = Objects.requireNonNull(event.getModels().get(paleId("plain", season)));
            for (int mask = 0; mask < 256; mask++) {
                if (PalePavingConnections.canonical(mask) == mask) {
                    connected[season][mask] = Objects.requireNonNull(event.getModels().get(
                            paleId("grass_connections/" + mask, season)));
                }
            }
        }
        PaleSurface[] variants = new PaleSurface[6];
        for (BlockState state : ModBlocks.PALE_PAVING.get().getStateDefinition().getPossibleStates()) {
            int variant = state.getValue(TerrainVariants.PAVING);
            variants[variant] = new PaleSurface(plain, connected);
            event.getModels().put(BlockModelShaper.stateToModelLocation(state), variants[variant]);
        }
        ModelResourceLocation item = new ModelResourceLocation(
                new ResourceLocation(StardewCraft.MODID, "pale_paving"), "inventory");
        event.getModels().put(item, new PalePavingItem(Objects.requireNonNull(event.getModels().get(item)), variants));
    }

    private static void bakeFamily(ModelEvent.ModifyBakingResult event, String family,
            net.minecraft.world.level.block.Block block, @Nullable BakedQuad[][] transitions) {
        bakeFamily(event, family, block, transitions, false);
    }
    private static void bakeFamily(ModelEvent.ModifyBakingResult event, String family,
            net.minecraft.world.level.block.Block block, @Nullable BakedQuad[][] transitions, boolean pale) {
        BakedModel[] surfaces = new BakedModel[4];
        BakedQuad[][][][] tops = new BakedQuad[4][6][4][47];
        for (int season = 0; season < 4; season++) {
            surfaces[season] = Objects.requireNonNull(event.getModels().get(id(family, season)));
            BakedQuad source = surfaces[season].getQuads(null, Direction.UP, RandomSource.create(0)).getFirst();
            for (int variant = 0; variant < 6; variant++) for (int phase = 0; phase < 4; phase++)
                for (int row = 0; row < 47; row++) tops[season][variant][phase][row] = tile(source, phase * 6 + variant, row, 384);
        }
        ConnectedTopQuads compositor = new ConnectedTopQuads();
        Surface[] variants = new Surface[6];
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            int variant = state.getValue(TerrainVariants.PAVING);
            variants[variant] = new Surface(surfaces, tops, variant, transitions, compositor, pale);
            event.getModels().put(BlockModelShaper.stateToModelLocation(state), variants[variant]);
        }
        ModelResourceLocation item = new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID, family), "inventory");
        event.getModels().put(item, new PavingItem(Objects.requireNonNull(event.getModels().get(item)), variants));
    }

    private static BakedQuad tile(BakedQuad source, int column, int row, int atlasWidth) {
        int[] vertices = source.getVertices().clone();
        int stride = vertices.length / 4;
        TextureAtlasSprite sprite = source.getSprite();
        for (int i = 0; i < 4; i++) {
            int v = i * stride;
            // A tiny inset prevents sampling the next atlas cell, including at mip level 4.
            float x = Float.intBitsToFloat(vertices[v]) > .5f ? 15.999f : .001f;
            float z = Float.intBitsToFloat(vertices[v + 2]) > .5f ? 15.999f : .001f;
            vertices[v + 4] = Float.floatToRawIntBits(sprite.getU((column * 16 + x) / atlasWidth));
            vertices[v + 5] = Float.floatToRawIntBits(sprite.getV((row * 16 + z) / 752f));
        }
        return new BakedQuad(vertices, source.getTintIndex(), source.getDirection(), sprite, source.isShade(), source.hasAmbientOcclusion());
    }

    private static final class Surface extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] surfaces;
        private final BakedQuad[][][][] tops;
        private final int variant;
        @Nullable private final BakedQuad[][] transitions;
        private final ConnectedTopQuads compositor;
        private final boolean pale;

        Surface(BakedModel[] surfaces, BakedQuad[][][][] tops, int variant,
                @Nullable BakedQuad[][] transitions, ConnectedTopQuads compositor, boolean pale) {
            super(surfaces[0]);
            this.surfaces = surfaces;
            this.tops = tops;
            this.variant = variant;
            this.transitions = transitions;
            this.compositor = compositor;
            this.pale = pale;
        }

        // Keep the atlas-cell selection through both stages of the item render pipeline.
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
            return data.derive().with(SEASON, TerrainSeasonTextures.currentTextureSet())
                    .with(RED_TRANSITION, transitions == null ? 46 : TownPavingConnections.redTransitionRow(level, pos))
                    .with(POSITION, TownPavingConnections.phase(pos) * 47
                            + TownPavingConnections.row(pale ? com.stardew.craft.block.terrain.PalePavingConnections.mask(level, pos)
                            : TownPavingConnections.mask(level, pos))).build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            Integer savedSeason = data.get(SEASON);
            int season = savedSeason == null ? TerrainSeasonTextures.currentTextureSet() : savedSeason;
            if (side != Direction.UP) return surfaces[season].getQuads(state, side, random, data, renderType);
            if (state != null && renderType != null && renderType != RenderType.solid()) return List.of();
            Integer position = data.get(POSITION);
            int cell = position == null ? 0 : position; // Inventory shows all four outer borders.
            List<BakedQuad> base = List.of(tops[season][variant][cell / 47][cell % 47]);
            Integer transition = data.get(RED_TRANSITION);
            if (transitions == null || transition == null || transition == 46) return base;
            // Clip both materials onto y=1; a raised transparent quad would cover entity shadows.
            return compositor.compose(base, List.of(transitions[season][transition]));
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return surfaces[TerrainSeasonTextures.currentTextureSet()].getParticleIcon();
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            Integer season = data.get(SEASON);
            return surfaces[season == null ? TerrainSeasonTextures.currentTextureSet() : season].getParticleIcon(data);
        }
    }

    /** Pale paving uses one authored 16px texture per connection mask, rather than an atlas. */
    private static final class PaleSurface extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] plain;
        private final BakedModel[][] connected;

        PaleSurface(BakedModel[] plain, BakedModel[][] connected) {
            super(plain[0]);
            this.plain = plain;
            this.connected = connected;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return data.derive().with(SEASON, TerrainSeasonTextures.currentTextureSet())
                    .with(PALE_MASK, PalePavingConnections.mask(level, pos)).build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            int season = data.get(SEASON) == null ? TerrainSeasonTextures.currentTextureSet() : data.get(SEASON);
            BakedModel model = plain[season];
            if (side == Direction.UP) {
                Integer savedMask = data.get(PALE_MASK);
                int mask = savedMask == null ? 0 : savedMask;
                model = connected[season][mask] == null ? plain[season] : connected[season][mask];
            }
            return model.getQuads(state, side, random, data, renderType);
        }

        @Override
        public TextureAtlasSprite getParticleIcon() { return plain[TerrainSeasonTextures.currentTextureSet()].getParticleIcon(); }
        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            Integer season = data.get(SEASON);
            return plain[season == null ? TerrainSeasonTextures.currentTextureSet() : season].getParticleIcon(data);
        }
    }

    private static final class PavingItem extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        PavingItem(BakedModel original, Surface[] variants) {
            super(original);
            overrides = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                        @Nullable LivingEntity entity, int seed) {
                    Integer variant = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(TerrainVariants.PAVING);
                    return variants[variant == null ? 0 : variant];
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }

    private static final class PalePavingItem extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;
        PalePavingItem(BakedModel original, PaleSurface[] variants) {
            super(original);
            overrides = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                        @Nullable LivingEntity entity, int seed) {
                    Integer variant = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
                            .get(TerrainVariants.PAVING);
                    return variants[variant == null ? 0 : variant];
                }
            };
        }
        @Override public ItemOverrides getOverrides() { return overrides; }
    }
}
