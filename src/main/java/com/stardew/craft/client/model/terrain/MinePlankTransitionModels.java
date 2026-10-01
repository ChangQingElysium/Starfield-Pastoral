package com.stardew.craft.client.model.terrain;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineBuildingTheme;
import com.stardew.craft.block.mine.MinePlankConnections;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Composes wood edges into the receiving soil face, sharing the grass connection pipeline. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MinePlankTransitionModels {
    private static final ModelProperty<int[]> EDGES = new ModelProperty<>();
    private MinePlankTransitionModels() {}

    private static ModelResourceLocation overlay(MineBuildingTheme theme, int mask) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/mine_paving/" + theme.id() + "/connections/" + mask), "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (MineBuildingTheme theme : MineBuildingTheme.values()) for (int mask = 1; mask < 256; mask++) {
            if (MinePlankConnections.canonical(mask) == mask) event.register(overlay(theme, mask));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        BakedModel[][] edges = new BakedModel[MineBuildingTheme.values().length][256];
        ConnectedTopQuads connections = new ConnectedTopQuads();
        for (MineBuildingTheme theme : MineBuildingTheme.values()) for (int mask = 1; mask < 256; mask++) {
            edges[theme.ordinal()][mask] = Objects.requireNonNull(models.get(overlay(theme, MinePlankConnections.canonical(mask))));
        }
        // Wrap the building soil's seasonal model too; keep its model data and grass overlays.
        List<Block> soils = new ArrayList<>();
        soils.add(ModBlocks.DIRT.get());
        for (MineBuildingTheme theme : MineBuildingTheme.values()) soils.add(theme.soil());
        for (Block block : soils) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation id = BlockModelShaper.stateToModelLocation(state);
                models.put(id, new ConnectedSoil(Objects.requireNonNull(models.get(id)), edges, connections));
            }
        }

    }

    private static final class ConnectedSoil extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[][] edges;
        private final ConnectedTopQuads connections;
        ConnectedSoil(BakedModel original, BakedModel[][] edges, ConnectedTopQuads connections) {
            super(original); this.edges = edges; this.connections = connections;
        }

        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return originalModel.getModelData(level, pos, state, data).derive()
                    .with(EDGES, MinePlankConnections.themeMasks(level, pos, state)).build();
        }

        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return originalModel.getRenderTypes(state, random, data);
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            boolean baseLayer = renderType == null || state == null || originalModel.getRenderTypes(state, random, data).contains(renderType);
            List<BakedQuad> base = baseLayer ? originalModel.getQuads(state, side, random, data, renderType) : List.of();
            int[] masks = data.get(EDGES);
            if (!baseLayer || side != Direction.UP || masks == null) return base;
            List<BakedQuad> result = base;
            for (int theme = 0; theme < masks.length; theme++) {
                int mask = masks[theme];
                if (mask != 0) result = connections.compose(result, edges[theme][mask].getQuads(state, Direction.UP, random, ModelData.EMPTY, null));
            }
            return result;
        }
    }
}
