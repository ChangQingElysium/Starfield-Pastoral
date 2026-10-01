package com.stardew.craft.client.model.terrain;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.terrain.TownPavingConnections;
import com.stardew.craft.client.floor.ClientSurfaceFloors;
import com.stardew.craft.floor.SurfaceFloorConnections;
import com.stardew.craft.floor.SurfaceFloorType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Replaces covered top pixels in the host mesh. No extra layer, block, collision or block entity. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SurfaceFloorModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final ModelProperty<List<BakedQuad>> FLOOR = new ModelProperty<>();
    private static final ModelProperty<Boolean> PREVIEW = new ModelProperty<>();
    private static final ModelProperty<Boolean> FLOOR_ONLY = new ModelProperty<>();
    private static final ModelProperty<TextureAtlasSprite> PARTICLE = new ModelProperty<>();
    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());

    /** Exposes the host to model initialization scans without replacing the rendered floor wrapper. */
    public static BakedModel unwrapSurface(BakedModel model) {
        while (model instanceof Surface surface) model = surface.hostModel();
        return model;
    }

    public static ModelData previewData(BakedModel model,BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData data,
            Map<BlockPos,com.stardew.craft.floor.SurfaceFloorData.Cover> covers,boolean floorOnly) {
        if(!(model instanceof Surface surface))return data;
        return surface.floorData(surface.originalData(level,pos,state,data),pos,covers::get).derive().with(PREVIEW,true).with(FLOOR_ONLY,floorOnly).build();
    }
    private SurfaceFloorModels() {}
    private static ModelResourceLocation id(String name) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/surface_floor/" + name), "standalone");
    }

    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for (var type : SurfaceFloorType.values()) {
            if (type == SurfaceFloorType.STONE_WALKWAY)
                for (String season : SEASONS) event.register(id(type.id + "_" + season));
            else event.register(id(type.id));
        }
        event.register(id("straw_hay"));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bake(ModelEvent.ModifyBakingResult event) {
        var tiles = new EnumMap<SurfaceFloorType, BakedQuad[]>(SurfaceFloorType.class);
        var particles = new EnumMap<SurfaceFloorType, TextureAtlasSprite>(SurfaceFloorType.class);
        for (var type : SurfaceFloorType.values()) {
            if (type == SurfaceFloorType.STONE_WALKWAY) continue;
            BakedModel model = event.getModels().get(id(type.id));
            int count = type == SurfaceFloorType.STEPPING_STONE_PATH ? 16 : type.masks * type.phaseX * type.phaseZ;
            tiles.put(type, cells(model, count, 16));
            particles.put(type, model.getParticleIcon());
        }
        BakedQuad[] hay = cells(event.getModels().get(id("straw_hay")), 73, 16);
        BakedQuad[][] town = new BakedQuad[4][];
        TextureAtlasSprite[] townParticles = new TextureAtlasSprite[4];
        for (int s = 0; s < 4; s++) {
            BakedModel model = event.getModels().get(id("stone_walkway_floor_" + SEASONS[s]));
            town[s] = cells(model, 47 * 24, 24);
            townParticles[s] = model.getParticleIcon();
        }
        var compositor = new ConnectedTopQuads();
        event.getModels().replaceAll((key, original) -> {
            // Only world blockstate models. Inventory and additional material models retain native transforms.
            // PORT(1.20.1): Forge keys are ResourceLocations; plain keys are additional models (1.21 "standalone").
            if (!(key instanceof net.minecraft.client.resources.model.ModelResourceLocation variant)
                    || variant.getVariant().equals("inventory") || variant.getVariant().equals("standalone")) return original;
            return new Surface(original, tiles, particles, hay, town, townParticles, compositor);
        });
    }

    private static BakedQuad[] cells(BakedModel model, int count, int columns) {
        BakedQuad source = com.stardew.craft.port.PortJava.getFirst(model.getQuads(null, Direction.UP, RandomSource.create(0)));
        BakedQuad[] result = new BakedQuad[count];
        TextureAtlasSprite sprite = source.getSprite();
        for (int index = 0; index < count; index++) {
            int[] vertices = source.getVertices().clone(); int stride = vertices.length / 4;
            for (int v = 0; v < vertices.length; v += stride) {
                float x = Float.intBitsToFloat(vertices[v]), z = Float.intBitsToFloat(vertices[v + 2]);
                vertices[v + 4] = Float.floatToRawIntBits(com.stardew.craft.port.PortSprites.getU(sprite, (index % columns * 16 + x * 16) / sprite.contents().width()));
                vertices[v + 5] = Float.floatToRawIntBits(com.stardew.craft.port.PortSprites.getV(sprite, (index / columns * 16 + z * 16) / sprite.contents().height()));
            }
            result[index] = new BakedQuad(vertices, -1, Direction.UP, sprite, true, true);
        }
        return result;
    }

    private static boolean top(BakedQuad q) {
        if (q.getDirection() != Direction.UP) return false;
        int[] v = q.getVertices(); int stride = v.length / 4;
        for (int i = 0; i < v.length; i += stride) if (Math.abs(Float.intBitsToFloat(v[i + 1]) - 1) > .0001f) return false;
        return true;
    }

    private static final class Surface extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final Map<SurfaceFloorType, BakedQuad[]> tiles;
        private final Map<SurfaceFloorType, TextureAtlasSprite> particles;
        private final BakedQuad[] hay;
        private final BakedQuad[][] town;
        private final TextureAtlasSprite[] townParticles;
        private final ConnectedTopQuads compositor;

        Surface(BakedModel original, Map<SurfaceFloorType, BakedQuad[]> tiles,
                Map<SurfaceFloorType, TextureAtlasSprite> particles, BakedQuad[] hay,
                BakedQuad[][] town, TextureAtlasSprite[] townParticles, ConnectedTopQuads compositor) {
            super(original); this.tiles = tiles; this.particles = particles; this.hay = hay;
            this.town = town; this.townParticles = townParticles; this.compositor = compositor;
        }

        private BakedModel hostModel() { return originalModel; }

        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            ModelData parent = originalModel.getModelData(level, pos, state, data);
            return floorData(parent,pos,ClientSurfaceFloors::at);
        }
        private ModelData originalData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData data){return originalModel.getModelData(level,pos,state,data);}
        private ModelData floorData(ModelData parent,BlockPos pos,java.util.function.Function<BlockPos,com.stardew.craft.floor.SurfaceFloorData.Cover> lookup){
            var cover = lookup.apply(pos);
            if (cover == null) return parent;
            SurfaceFloorType type = cover.type();
            java.util.function.Predicate<BlockPos> same = p -> {
                var neighbor = lookup.apply(p);
                return neighbor != null && neighbor.type() == type;
            };
            int mask = SurfaceFloorConnections.mask(pos, same);
            int phase = SurfaceFloorConnections.phase(type, pos);
            var overlay = new ArrayList<BakedQuad>();
            TextureAtlasSprite particle;
            if (type == SurfaceFloorType.STONE_WALKWAY) {
                int season = com.stardew.craft.port.PortJava.clamp(TerrainSeasonTextures.currentTextureSet(), 0, 3);
                int row = TownPavingConnections.row(SurfaceFloorConnections.townMask(mask));
                int roll = Math.floorMod(net.minecraft.util.Mth.getSeed(pos), 100);
                int variant = roll < 28 ? 0 : roll < 38 ? 1 : roll < 52 ? 2 : roll < 75 ? 3 : roll < 97 ? 4 : 5;
                overlay.add(town[season][row * 24 + phase * 6 + variant]);
                particle = townParticles[season];
            } else {
                int row = type.masks == 47 ? SurfaceFloorConnections.row(mask) : mask & 15;
                int index = type == SurfaceFloorType.STEPPING_STONE_PATH ? cover.variant() : row * type.phaseX * type.phaseZ + phase;
                overlay.add(tiles.get(type)[index]); particle = particles.get(type);
                if (type == SurfaceFloorType.STRAW) {
                    if (SurfaceFloorConnections.hasHay(pos, same)) {
                        int hayMask = SurfaceFloorConnections.mask(pos, p -> SurfaceFloorConnections.hasHay(p, same)) & 15;
                        overlay.add(hay[hayMask * 4 + phase]);
                    } else {
                        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                            if ((dx != 0 || dz != 0) && SurfaceFloorConnections.hasHay(pos.offset(dx, 0, dz), same))
                                overlay.add(hay[64 + (1 - dz) * 3 + 1 - dx]);
                        }
                    }
                }
            }
            return parent.derive().with(FLOOR, List.copyOf(overlay)).with(PARTICLE, particle).build();
        }

        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
            List<BakedQuad> base = Boolean.TRUE.equals(data.get(FLOOR_ONLY)) ? List.of() : originalModel.getQuads(state, side, random, data, layer);
            List<BakedQuad> floor = data.get(FLOOR);
            if (floor == null || (side != null && side != Direction.UP)) return base;
            var result = new ArrayList<BakedQuad>();
            for (BakedQuad q : base) if (!top(q)) result.add(q);
            if (side == Direction.UP && (layer == null || layer == RenderType.solid() || Boolean.TRUE.equals(data.get(PREVIEW)))) {
                // Some native models put their top in the unculled list; emit it exactly once on UP.
                var host = new ArrayList<BakedQuad>();
                for (BakedQuad q : originalModel.getQuads(state, Direction.UP, random, data, null)) if (top(q)) host.add(q);
                for (BakedQuad q : originalModel.getQuads(state, null, random, data, null)) if (top(q)) host.add(q);
                result.addAll(compositor.compose(host, floor));
            }
            return result;
        }

        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            var types = originalModel.getRenderTypes(state, random, data);
            return data.has(FLOOR) ? ChunkRenderTypeSet.union(types, SOLID) : types;
        }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) {
            return data.has(PARTICLE) ? data.get(PARTICLE) : originalModel.getParticleIcon(data);
        }
    }
}
