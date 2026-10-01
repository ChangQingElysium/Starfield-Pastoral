package com.stardew.craft.client.model.terrain;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.DesertWallConnections;
import com.stardew.craft.block.terrain.TerrainFaceConnections;
import com.stardew.craft.block.terrain.TownPavingConnections;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Selects native 16px atlas cells on all six faces, before the existing mine-soil overlay wraps them. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DesertWallModels {
    private static final ModelProperty<int[]> CELLS = new ModelProperty<>();
    private static final ModelResourceLocation ATLAS = new ModelResourceLocation(
            new ResourceLocation(StardewCraft.MODID, "block/mine_desert_wall/connected"), "standalone");

    private DesertWallModels() {}

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) { event.register(ATLAS); }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        BakedModel atlas = Objects.requireNonNull(event.getModels().get(ATLAS));
        BakedQuad[][][] quads = new BakedQuad[6][12][47];
        for (Direction face : Direction.values()) {
            BakedQuad source = com.stardew.craft.port.PortJava.getFirst(atlas.getQuads(null, face, RandomSource.create(0)));
            for (int column = 0; column < 12; column++) for (int row = 0; row < 47; row++)
                quads[face.ordinal()][column][row] = tile(source, column, row);
        }
        for (BlockState state : ModBlocks.MINE_DESERT_WALL.get().getStateDefinition().getPossibleStates()) {
            var id = BlockModelShaper.stateToModelLocation(state);
            event.getModels().put(id, new Wall(Objects.requireNonNull(event.getModels().get(id)), quads));
        }
        // Inventory retains the normal native single-block model and its opaque particle material.
    }

    private static BakedQuad tile(BakedQuad source, int column, int row) {
        int[] vertices = source.getVertices().clone();
        int stride = vertices.length / 4;
        var frame = TerrainFaceConnections.frame(source.getDirection());
        var sprite = source.getSprite();
        for (int i = 0; i < 4; i++) {
            int at = i * stride;
            Vec3 p = new Vec3(Float.intBitsToFloat(vertices[at]), Float.intBitsToFloat(vertices[at + 1]),
                    Float.intBitsToFloat(vertices[at + 2]));
            float u = frame.x(p) > .5 ? 15.999f : .001f;
            float v = frame.y(p) > .5 ? 15.999f : .001f;
            vertices[at + 4] = Float.floatToRawIntBits(com.stardew.craft.port.PortSprites.getU(sprite, (column * 16 + u) / 192f));
            vertices[at + 5] = Float.floatToRawIntBits(com.stardew.craft.port.PortSprites.getV(sprite, (row * 16 + v) / 752f));
        }
        return new BakedQuad(vertices, source.getTintIndex(), source.getDirection(), sprite,
                source.isShade(), source.hasAmbientOcclusion());
    }

    private static final class Wall extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedQuad[][][] quads;
        Wall(BakedModel original, BakedQuad[][][] quads) { super(original); this.quads = quads; }

        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            int[] cells = new int[6];
            for (Direction face : Direction.values()) {
                int column = DesertWallConnections.phase(pos, face) * 3 + DesertWallConnections.variant(pos, face);
                cells[face.ordinal()] = column * 47 + TownPavingConnections.row(DesertWallConnections.mask(level, pos, face));
            }
            return originalModel.getModelData(level, pos, state, data).derive().with(CELLS, cells).build();
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            int[] cells = data.get(CELLS);
            if (state == null || cells == null) return originalModel.getQuads(state, side, random, data, renderType);
            if (side == null || renderType != null && renderType != RenderType.solid()) return List.of();
            int cell = cells[side.ordinal()];
            return List.of(quads[side.ordinal()][cell / 47][cell % 47]);
        }
    }
}
