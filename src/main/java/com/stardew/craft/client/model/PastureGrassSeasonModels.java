package com.stardew.craft.client.model;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/** Selects the authored season model for SDV pasture grass; no runtime colour tinting is used. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PastureGrassSeasonModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private PastureGrassSeasonModels() {}

    private static ModelResourceLocation seasonModel(String family, int season, int variant) {
        return ModelResourceLocation.standalone(new ResourceLocation(StardewCraft.MODID,
                "block/grass/seasonal/" + family + "/" + SEASONS[season] + "/" + variant));
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (String family : new String[]{"pasture_grass", "blue_pasture_grass"})
            for (int season = 0; season < 4; season++)
                for (int variant = 0; variant < PastureGrassBlock.VISUAL_VARIANT_COUNT; variant++)
                    event.register(seasonModel(family, season, variant));
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        wrap(event, ModBlocks.PASTURE_GRASS.get(), "pasture_grass");
        wrap(event, ModBlocks.BLUE_PASTURE_GRASS.get(), "blue_pasture_grass");
    }

    private static void wrap(ModelEvent.ModifyBakingResult event, Block block, String family) {
        BakedModel[][] seasons = new BakedModel[4][PastureGrassBlock.VISUAL_VARIANT_COUNT];
        for (int season = 0; season < 4; season++)
            for (int variant = 0; variant < PastureGrassBlock.VISUAL_VARIANT_COUNT; variant++)
                seasons[season][variant] = Objects.requireNonNull(event.getModels().get(seasonModel(family, season, variant)));
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            var location = BlockModelShaper.stateToModelLocation(state);
            BakedModel original = Objects.requireNonNull(event.getModels().get(location));
            event.getModels().put(location, new SeasonalModel(original, seasons));
        }
    }

    private static final class SeasonalModel extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[][] seasons;

        SeasonalModel(BakedModel original, BakedModel[][] seasons) {
            super(original);
            this.seasons = seasons;
        }

        private BakedModel current(@Nullable BlockState state) {
            var level = Minecraft.getInstance().level;
            if (level == null || !level.dimension().equals(ModDimensions.STARDEW_VALLEY) || !StardewTimeHud.isTimeSynced())
                return originalModel;
            int season = Math.clamp(TerrainSeasonTextures.currentTextureSet(), 0, 3);
            int variant = state != null && state.hasProperty(PastureGrassBlock.VARIANT)
                    ? Math.clamp(state.getValue(PastureGrassBlock.VARIANT), 0, 3) : 0;
            return seasons[season][variant];
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return current(state).getQuads(state, side, random);
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                                   RandomSource random, ModelData data, @Nullable RenderType renderType) {
            return current(state).getQuads(state, side, random, data, renderType);
        }

        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return ChunkRenderTypeSet.of(RenderType.cutoutMipped());
        }

        @Override public TextureAtlasSprite getParticleIcon() { return current(null).getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return current(null).getParticleIcon(data); }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) { return List.of(this); }
    }
}
