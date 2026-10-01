package com.stardew.craft.client.model;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.tree.PineCanopyConnections;
import com.stardew.craft.tree.PineCanopyConnections.Canopy;
import java.util.ArrayList;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Native Java face models composed from real neighbours, with no coplanar snow overlay. */
@SuppressWarnings("removal")
public final class PineCanopyModels {
    public static final ModelProperty<Canopy> CANOPY = new ModelProperty<>();
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private PineCanopyModels() {}

    private static ModelResourceLocation id(String name) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/tree/pine/connected/" + name), "standalone");
    }

    public static void register(ModelEvent.RegisterAdditional event) {
        for (String season : SEASONS) for (Direction face : Direction.values()) event.register(id(season + "_" + face.getName()));
        for (int v = 0; v < 3; v++) {
            for (Direction face : Direction.Plane.HORIZONTAL) event.register(id("snow_" + v + "_" + face.getName()));
            for (int mask = 0; mask < 256; mask++) if (PineCanopyConnections.canonical(mask) == mask)
                event.register(id("top_" + v + "_" + mask));
        }
    }

    public static void bake(ModelEvent.ModifyBakingResult event) {
        var block = ModBlocks.PINE_LEAVES.get();
        var base = Objects.requireNonNull(event.getModels().get(BlockModelShaper.stateToModelLocation(block.defaultBlockState())));
        var model = new Connected(base, event);
        for (BlockState state : block.getStateDefinition().getPossibleStates())
            event.getModels().put(BlockModelShaper.stateToModelLocation(state), model);
    }

    private static final class Connected extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final List<BakedQuad>[][] bare;
        private final List<BakedQuad>[][] snowySides;
        private final List<BakedQuad>[][] tops;
        private final TextureAtlasSprite[] particles = new TextureAtlasSprite[4];

        @SuppressWarnings("unchecked")
        Connected(BakedModel original, ModelEvent.ModifyBakingResult event) {
            super(original);
            bare = new List[4][6]; snowySides = new List[3][6]; tops = new List[3][256];
            for (int s = 0; s < 4; s++) for (Direction face : Direction.values()) {
                var model = Objects.requireNonNull(event.getModels().get(id(SEASONS[s] + "_" + face.getName())));
                bare[s][face.ordinal()] = List.copyOf(model.getQuads(null, null, RandomSource.create(0)));
                particles[s] = model.getParticleIcon();
            }
            for (int v = 0; v < 3; v++) {
                for (Direction face : Direction.Plane.HORIZONTAL)
                    snowySides[v][face.ordinal()] = quads(event, "snow_" + v + "_" + face.getName());
                for (int mask = 0; mask < 256; mask++) if (PineCanopyConnections.canonical(mask) == mask)
                    tops[v][mask] = quads(event, "top_" + v + "_" + mask);
            }
        }

        private static List<BakedQuad> quads(ModelEvent.ModifyBakingResult event, String name) {
            return List.copyOf(Objects.requireNonNull(event.getModels().get(id(name))).getQuads(null, null, RandomSource.create(0)));
        }

        private boolean valley() {
            var level = Minecraft.getInstance().level;
            return level != null && level.dimension().equals(ModDimensions.STARDEW_VALLEY) && StardewTimeHud.isTimeSynced();
        }

        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return data.derive().with(CANOPY, PineCanopyConnections.inspect(level::getBlockState, pos)).build();
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType type) {
            if (!valley()) return originalModel.getQuads(state, side, random, data, type);
            if (side != null || (type != null && state != null && type != RenderType.cutoutMipped())) return List.of();
            Canopy canopy = data.get(CANOPY);
            if (canopy == null) canopy = new Canopy(0, 0, 0, true);
            int season = TerrainSeasonTextures.currentTextureSet();
            boolean snow = season == 3 && canopy.snowExposed();
            var result = new ArrayList<BakedQuad>();
            for (Direction face : Direction.values()) {
                if (canopy.hidden(face)) continue;
                if (snow && face == Direction.UP) result.addAll(tops[canopy.variant()][canopy.snowMask()]);
                else if (snow && face.getAxis().isHorizontal()) result.addAll(snowySides[canopy.variant()][face.ordinal()]);
                else result.addAll(bare[season][face.ordinal()]);
            }
            return result;
        }

        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return ChunkRenderTypeSet.of(RenderType.cutoutMipped());
        }
        @Override public TextureAtlasSprite getParticleIcon() { return valley() ? particles[TerrainSeasonTextures.currentTextureSet()] : originalModel.getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return getParticleIcon(); }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) { return List.of(this); }
    }
}
