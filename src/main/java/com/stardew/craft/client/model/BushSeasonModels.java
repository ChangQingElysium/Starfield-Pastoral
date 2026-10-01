package com.stardew.craft.client.model;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.BerryBushBlock;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.core.ModDimensions;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

/** Seasonal materials share the existing geometry and require no saved-state migration. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BushSeasonModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final String[] SMALL = {"small_spring", "small_spring", "small_fall", "small_winter"};

    private BushSeasonModels() {}

    private static ModelResourceLocation id(String name) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/bush/seasons/" + name), "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (String name : List.of("small_spring", "small_fall", "small_winter",
                "salmonberry_spring", "blackberry_fall")) event.register(id(name));
        for (String season : SEASONS) {
            event.register(id("berry_" + season));
            event.register(id("empty_" + season));
        }
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        for (var block : List.of(ModBlocks.SMALL_BUSH.get(), ModBlocks.BERRY_BUSH.get())) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                BakedModel[] seasons = new BakedModel[4];
                for (int season = 0; season < 4; season++) {
                    String name;
                    if (!state.hasProperty(BerryBushBlock.PART)) {
                        name = SMALL[season];
                    } else if (state.getValue(BerryBushBlock.PART) == BerryBushBlock.Part.EXTENSION) {
                        name = "empty_" + SEASONS[season];
                    } else if (season == 0 && state.getValue(BerryBushBlock.BERRY) == BerryBushBlock.BerryKind.SALMONBERRY) {
                        name = "salmonberry_spring";
                    } else if (season == 2 && state.getValue(BerryBushBlock.BERRY) == BerryBushBlock.BerryKind.BLACKBERRY) {
                        name = "blackberry_fall";
                    } else {
                        name = "berry_" + SEASONS[season];
                    }
                    seasons[season] = Objects.requireNonNull(event.getModels().get(id(name)), name);
                }
                var location = BlockModelShaper.stateToModelLocation(state);
                event.getModels().put(location, new SeasonalModel(
                        Objects.requireNonNull(event.getModels().get(location)), seasons));
            }
        }
    }

    private static final class SeasonalModel extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] seasons;

        SeasonalModel(BakedModel original, BakedModel[] seasons) {
            super(original);
            this.seasons = seasons;
        }

        private BakedModel current() {
            var level = Minecraft.getInstance().level;
            if (level == null || !level.dimension().equals(ModDimensions.STARDEW_VALLEY)
                    || !StardewTimeHud.isTimeSynced()) return originalModel;
            int season = StardewTimeHud.getClientTimeCache().getCurrentSeason();
            return seasons[Math.floorMod(season, seasons.length)];
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return current().getQuads(state, side, random);
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType type) {
            return current().getQuads(state, side, random, data, type);
        }

        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return ChunkRenderTypeSet.of(RenderType.cutout());
        }

        @Override public TextureAtlasSprite getParticleIcon() { return current().getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return current().getParticleIcon(data); }
    }
}
