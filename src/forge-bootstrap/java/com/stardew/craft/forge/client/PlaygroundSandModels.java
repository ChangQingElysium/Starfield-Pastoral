package com.stardew.craft.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.block.terrain.PlaygroundSandConnections;
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

/** One solid top quad replaces the authored connected-sand atlas. */
@Mod.EventBusSubscriber(modid = ForgeBootstrap.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PlaygroundSandModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final ModelProperty<Integer> CELL = new ModelProperty<>();

    private PlaygroundSandModels() {
    }

    private static ModelResourceLocation model(int season, int variant) {
        return new ModelResourceLocation(
                new ResourceLocation(ForgeBootstrap.MOD_ID,
                        "block/playground_sand/" + SEASONS[season] + "_" + variant),
                "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (int season = 0; season < 4; season++) {
            for (int variant = 0; variant < 4; variant++) {
                event.register(model(season, variant));
            }
        }
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        BakedModel[][] surfaces = new BakedModel[4][4];
        BakedQuad[][][] tops = new BakedQuad[4][4][47];
        for (int season = 0; season < 4; season++) {
            for (int variant = 0; variant < 4; variant++) {
                surfaces[season][variant] = Objects.requireNonNull(event.getModels().get(model(season, variant)));
                BakedQuad source = surfaces[season][variant]
                        .getQuads(null, Direction.UP, RandomSource.create(0)).get(0);
                for (int row = 0; row < 47; row++) {
                    tops[season][variant][row] = tile(source, row);
                }
            }
        }

        Surface[] variants = new Surface[4];
        for (BlockState state : ForgeBlocks.PLAYGROUND_SAND.get().getStateDefinition().getPossibleStates()) {
            int variant = state.getValue(TerrainVariants.SAND);
            variants[variant] = new Surface(surfaces, tops, variant);
            event.getModels().put(BlockModelShaper.stateToModelLocation(state), variants[variant]);
        }

        ModelResourceLocation item = new ModelResourceLocation(
                new ResourceLocation(ForgeBootstrap.MOD_ID, "playground_sand"), "inventory");
        event.getModels().put(item, new SandItem(
                Objects.requireNonNull(event.getModels().get(item)), variants));
    }

    private static BakedQuad tile(BakedQuad source, int row) {
        int[] vertices = source.getVertices().clone();
        int stride = vertices.length / 4;
        TextureAtlasSprite sprite = source.getSprite();
        for (int i = 0; i < 4; i++) {
            int vertex = i * stride;
            float x = Float.intBitsToFloat(vertices[vertex]) > .5f ? 15.999f : .001f;
            float z = Float.intBitsToFloat(vertices[vertex + 2]) > .5f ? 15.999f : .001f;
            vertices[vertex + 4] = Float.floatToRawIntBits(
                    sprite.getU((row % 8 * 16 + x) / 128f));
            vertices[vertex + 5] = Float.floatToRawIntBits(
                    sprite.getV((row / 8 * 16 + z) / 96f));
        }
        return new BakedQuad(vertices, source.getTintIndex(), source.getDirection(), sprite,
                source.isShade(), source.hasAmbientOcclusion());
    }

    private static final class Surface extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[][] surfaces;
        private final BakedQuad[][][] tops;
        private final int variant;

        Surface(BakedModel[][] surfaces, BakedQuad[][][] tops, int variant) {
            super(surfaces[0][variant]);
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
            return data.derive().with(CELL, TerrainSeasonTextures.currentTextureSet() * 47
                    + PlaygroundSandConnections.row(PlaygroundSandConnections.mask(level, pos))).build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType renderType) {
            Integer cell = data.get(CELL);
            int season = cell == null ? TerrainSeasonTextures.currentTextureSet() : cell / 47;
            if (side != Direction.UP) {
                return surfaces[season][variant].getQuads(state, side, random, data, renderType);
            }
            if (state != null && renderType != null && renderType != RenderType.solid()) {
                return List.of();
            }
            return List.of(tops[season][variant][cell == null ? 0 : cell % 47]);
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return surfaces[TerrainSeasonTextures.currentTextureSet()][variant].getParticleIcon();
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            Integer cell = data.get(CELL);
            int season = cell == null ? TerrainSeasonTextures.currentTextureSet() : cell / 47;
            return surfaces[season][variant].getParticleIcon(data);
        }
    }

    private static final class SandItem extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        SandItem(BakedModel original, Surface[] variants) {
            super(original);
            overrides = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                        @Nullable LivingEntity entity, int seed) {
                    Tag raw = stack.getTagElement(BlockItem.BLOCK_STATE_TAG) == null
                            ? null
                            : stack.getTagElement(BlockItem.BLOCK_STATE_TAG)
                                    .get(TerrainVariants.SAND.getName());
                    int variant = raw == null
                            ? 0
                            : TerrainVariants.SAND.getValue(raw.getAsString()).orElse(0);
                    return variants[variant];
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }
}
