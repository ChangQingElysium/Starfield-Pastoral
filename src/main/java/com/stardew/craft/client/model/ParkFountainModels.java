package com.stardew.craft.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.ParkFountainBlock;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

/** Water quads are baked from ordinary native JSON, then use the shared streaming texture. */
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ParkFountainModels {
    public static final int WATER_PART_COUNT = 53;
    private static volatile List<WaterPart> parts = List.of();
    private static volatile int generation;
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private ParkFountainModels() {}

    public record WaterPart(String kind, int side, int index, double x, double y, double z, List<BakedQuad> quads) {}
    public static List<WaterPart> parts() { return parts; }
    public static int generation() { return generation; }

    public static JsonObject description() {
        return description("spring");
    }

    private static JsonObject description(String file) {
        var id = new ResourceLocation(StardewCraft.MODID, "fountain/" + file + ".json");
        try (var reader = new InputStreamReader(Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(id).open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load fountain geometry manifest", exception);
        }
    }

    private static ModelResourceLocation id(String name) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/park_fountain/" + name), "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (int i = 0; i < WATER_PART_COUNT; i++) event.register(id("water_" + i));
        for (String season : SEASONS) {
            event.register(id(season + "/empty"));
            for (var cell : description("seasons").getAsJsonArray("cells"))
                event.register(id(season + "/cell_" + cell.getAsInt()));
        }
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        List<WaterPart> result = new ArrayList<>();
        for (var entry : description().getAsJsonArray("parts")) {
            var part = entry.getAsJsonObject();
            var model = Objects.requireNonNull(event.getModels().get(id(part.get("model").getAsString())));
            List<BakedQuad> quads = new ArrayList<>();
            for (var quad : model.getQuads(null, null, RandomSource.create(0))) {
                int[] vertices = quad.getVertices().clone();
                int stride = vertices.length / 4;
                var sprite = quad.getSprite();
                for (int i = 0; i < 4; i++) {
                    int at = i * stride;
                    vertices[at + 4] = Float.floatToRawIntBits((Float.intBitsToFloat(vertices[at + 4]) - sprite.getU0())
                            / (sprite.getU1() - sprite.getU0()));
                    vertices[at + 5] = Float.floatToRawIntBits((Float.intBitsToFloat(vertices[at + 5]) - sprite.getV0())
                            / (sprite.getV1() - sprite.getV0()));
                }
                quads.add(new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), sprite,
                        quad.isShade(), quad.hasAmbientOcclusion()));
            }
            var offset = part.getAsJsonArray("offset");
            result.add(new WaterPart(part.get("kind").getAsString(), part.has("side") ? part.get("side").getAsInt() : 0,
                    part.has("index") ? part.get("index").getAsInt() : 0,
                    offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), offset.get(2).getAsDouble(), List.copyOf(quads)));
        }
        parts = List.copyOf(result);
        generation++;
        var visibleCells = new java.util.HashSet<Integer>();
        description("seasons").getAsJsonArray("cells").forEach(cell -> visibleCells.add(cell.getAsInt()));
        for (BlockState state : ModBlocks.PARK_FOUNTAIN.get().getStateDefinition().getPossibleStates()) {
            int cell = state.getValue(ParkFountainBlock.CELL);
            var seasonal = new BakedModel[4];
            for (int s = 0; s < 4; s++) seasonal[s] = Objects.requireNonNull(event.getModels().get(
                    id(SEASONS[s] + "/" + (visibleCells.contains(cell) ? "cell_" + cell : "empty"))));
            var key = BlockModelShaper.stateToModelLocation(state);
            event.getModels().put(key, new SeasonalCell(Objects.requireNonNull(event.getModels().get(key)),
                    seasonal, state.getValue(ParkFountainBlock.FACING)));
        }
    }

    private static BakedQuad rotate(BakedQuad source, Direction facing) {
        int turns = switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        if (turns == 0) return source;
        int[] vertices = source.getVertices().clone();
        int stride = vertices.length / 4;
        for (int i = 0; i < 4; i++) {
            int at = i * stride;
            float x = Float.intBitsToFloat(vertices[at]), z = Float.intBitsToFloat(vertices[at + 2]);
            int packed = vertices[at + 7], nx = (byte) packed, nz = (byte) (packed >> 16);
            for (int t = 0; t < turns; t++) {
                float oldX = x; x = 1 - z; z = oldX;
                int oldNx = nx; nx = -nz; nz = oldNx;
            }
            vertices[at] = Float.floatToRawIntBits(x);
            vertices[at + 2] = Float.floatToRawIntBits(z);
            vertices[at + 7] = (packed & 0xff00ff00) | (nx & 255) | ((nz & 255) << 16);
        }
        Direction face = source.getDirection();
        if (face.getAxis().isHorizontal()) for (int t = 0; t < turns; t++) face = face.getClockWise();
        return new BakedQuad(vertices, source.getTintIndex(), face, source.getSprite(), source.isShade(), source.hasAmbientOcclusion());
    }

    private static final class SeasonalCell extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private static final ChunkRenderTypeSet LAYERS = ChunkRenderTypeSet.of(RenderType.cutout());
        private final BakedModel[] seasons;
        private final List<List<BakedQuad>> quads;
        SeasonalCell(BakedModel original, BakedModel[] seasons, Direction facing) {
            super(original);
            this.seasons = seasons;
            quads = java.util.Arrays.stream(seasons).map(model -> model.getQuads(null, null, RandomSource.create(0))
                    .stream().map(quad -> rotate(quad, facing)).toList()).toList();
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                                                 ModelData data, RenderType type) {
            if (side != null || type != null && type != RenderType.cutout()) return List.of();
            return quads.get(TerrainSeasonTextures.currentTextureSet());
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) { return LAYERS; }
        @Override public TextureAtlasSprite getParticleIcon() { return seasons[TerrainSeasonTextures.currentTextureSet()].getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return getParticleIcon(); }
    }
}
