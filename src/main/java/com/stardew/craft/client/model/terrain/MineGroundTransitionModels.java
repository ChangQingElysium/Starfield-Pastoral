package com.stardew.craft.client.model.terrain;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.mine.MineBuildingTheme;
import com.stardew.craft.block.mine.MineGroundConnections;
import com.stardew.craft.block.terrain.TerrainFaceConnections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

/** Authored top masks plus native soil pixels folded onto exposed mine wall faces. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MineGroundTransitionModels {
    private static final ModelProperty<List<List<TerrainFaceConnections.Connection>>> FACES = new ModelProperty<>();
    private static final ModelProperty<Integer> EDGES = new ModelProperty<>();
    private MineGroundTransitionModels() {}

    private static ModelResourceLocation overlay(MineBuildingTheme theme, int material, int mask) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/mine_ground/" + (theme == MineBuildingTheme.EARTH ? "" : theme.id() + "/") + material + "/" + mask), "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (MineBuildingTheme theme : MineBuildingTheme.values())
            for (int material = 1; material <= 2; material++)
                for (int mask = 1; mask < 256; mask++) event.register(overlay(theme, material, mask));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        Map<BlockState, BakedModel> sources = new HashMap<>();
        for (MineBuildingTheme theme : MineBuildingTheme.values())
            for (Block block : List.of(theme.soil(), theme.looseSoil()))
                for (BlockState state : block.getStateDefinition().getPossibleStates())
                    sources.put(state, Objects.requireNonNull(models.get(BlockModelShaper.stateToModelLocation(state))));
        TerrainFaceQuads painter = new TerrainFaceQuads();
        for (MineBuildingTheme theme : MineBuildingTheme.values()) {
            BakedModel[][] edges = new BakedModel[3][256];
            for (int material = 1; material <= 2; material++) for (int mask = 1; mask < 256; mask++)
                edges[material][mask] = Objects.requireNonNull(models.get(overlay(theme, material, mask)));
            for (Block block : List.of(theme.soil(), theme.wall())) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    ModelResourceLocation id = BlockModelShaper.stateToModelLocation(state);
                    models.put(id, new ConnectedSoil(Objects.requireNonNull(models.get(id)), edges, sources, painter));
                }
            }
        }
    }

    private static final class ConnectedSoil extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[][] edges;
        private final Map<BlockState, BakedModel> sources;
        private final TerrainFaceQuads painter;
        ConnectedSoil(BakedModel original, BakedModel[][] edges, Map<BlockState, BakedModel> sources, TerrainFaceQuads painter) {
            super(original); this.edges = edges; this.sources = sources; this.painter = painter;
        }

        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            List<List<TerrainFaceConnections.Connection>> faces = new ArrayList<>(6);
            for (Direction face : Direction.values()) faces.add(MineGroundConnections.faceConnections(level, pos, state, face));
            return originalModel.getModelData(level, pos, state, data).derive()
                    .with(FACES, List.copyOf(faces))
                    .with(EDGES, MineGroundConnections.mask(level, pos, state, 1)
                            | (MineGroundConnections.mask(level, pos, state, 2) << 8)).build();
        }

        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            ChunkRenderTypeSet base = originalModel.getRenderTypes(state, random, data);
            return mask(data) == 0 ? base : ChunkRenderTypeSet.union(base, ChunkRenderTypeSet.of(RenderType.cutout()));
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            boolean baseLayer = renderType == null || state == null || originalModel.getRenderTypes(state, random, data).contains(renderType);
            List<BakedQuad> base = baseLayer ? originalModel.getQuads(state, side, random, data, renderType) : List.of();
            var faces = data.get(FACES);
            if (baseLayer && side != null && faces != null && !faces.get(side.ordinal()).isEmpty() && !base.isEmpty()) {
                List<TerrainFaceQuads.Paint> paints = new ArrayList<>();
                for (var connection : faces.get(side.ordinal())) {
                    BakedModel source = sources.get(connection.state());
                    for (Direction cull : new Direction[]{connection.face(), null}) {
                        for (BakedQuad quad : source.getQuads(connection.state(), cull, RandomSource.create(0), ModelData.EMPTY, null))
                            if (quad.getDirection() == connection.face()) paints.add(new TerrainFaceQuads.Paint(connection, quad));
                    }
                }
                List<BakedQuad> painted = new ArrayList<>();
                for (BakedQuad quad : base) {
                    if (quad.getDirection() == side) painted.addAll(painter.get(quad, paints));
                    else painted.add(quad);
                }
                base = List.copyOf(painted);
            }
            int mask = mask(data);
            if (side != Direction.UP || mask == 0 || (renderType != null && renderType != RenderType.cutout())) return base;
            List<BakedQuad> result = new ArrayList<>(base);
            for (int material = 1; material <= 2; material++) {
                int part = (mask >> ((material - 1) * 8)) & 255;
                if (part != 0) result.addAll(edges[material][part].getQuads(state, Direction.UP, random, ModelData.EMPTY, renderType));
            }
            return result;
        }

        private static int mask(ModelData data) { Integer mask = data.get(EDGES); return mask == null ? 0 : mask; }
    }

}
