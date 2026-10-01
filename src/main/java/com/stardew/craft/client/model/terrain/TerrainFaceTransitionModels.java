package com.stardew.craft.client.model.terrain;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainSoils;
import com.stardew.craft.block.terrain.TerrainFaceConnections;
import com.stardew.craft.block.terrain.TerrainVariants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

/** Seasonal cliffs and additional native-face terrain connections, composed with existing farm/top models. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TerrainFaceTransitionModels {
    private record Faces(int season, List<List<TerrainFaceConnections.Connection>> connections) {}
    private static final ModelProperty<Faces> FACES = new ModelProperty<>();
    private TerrainFaceTransitionModels() {}

    private static List<Block> blocks() {
        return List.of(ModBlocks.CLIFF.get(), ModBlocks.DIRT.get(), ModBlocks.GRASS_BLOCK.get(),
                ModBlocks.DARK_GRASS_BLOCK.get(), ModBlocks.FARMLAND.get(), ModBlocks.SAND.get(), ModBlocks.SANDY_FARMLAND.get(), ModBlocks.HARD_SOIL.get(), ModBlocks.INFERTILE_FARMLAND.get());
    }

    private static ModelResourceLocation id(int season, BlockState state) {
        var property = TerrainVariants.property(state);
        int variant = TerrainSoils.farmland(state) ? state.getValue(FarmBlock.MOISTURE) : property == null ? 0 : state.getValue(property);
        boolean snowy = state.hasProperty(BlockStateProperties.SNOWY) && state.getValue(BlockStateProperties.SNOWY);
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                TerrainSeasonTextures.modelPath(season, BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), variant, snowy)), "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (Block block : blocks()) for (BlockState state : block.getStateDefinition().getPossibleStates())
            for (int season = 0; season < 4; season++) event.register(id(season,state));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bake(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BakedModel[]> raw = new HashMap<>();
        for (Block block : blocks()) for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            BakedModel[] seasons = new BakedModel[4];
            for (int season = 0; season < 4; season++) seasons[season] = Objects.requireNonNull(event.getModels().get(id(season,state)), id(season,state)::toString);
            raw.put(state,seasons);
        }
        var quads = new TerrainFaceQuads();
        for (Block block : blocks()) for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            var location = BlockModelShaper.stateToModelLocation(state);
            event.getModels().put(location,new Surface(Objects.requireNonNull(event.getModels().get(location)), state, raw, quads));
        }
        var itemId = new ModelResourceLocation(BuiltInRegistries.BLOCK.getKey(ModBlocks.CLIFF.get()), "inventory");
        event.getModels().put(itemId,new CliffItem(Objects.requireNonNull(event.getModels().get(itemId)),raw));
        ShapedTerrainModels.wrapFull(event.getModels());
    }

    private static BakedQuad nativeFace(BakedModel model, BlockState state, Direction face) {
        for (Direction cull : new Direction[]{face,null})
            for (BakedQuad quad : model.getQuads(state,cull,RandomSource.create(0)))
                if (quad.getDirection() == face) return quad;
        throw new IllegalStateException("Missing native terrain face " + state + " " + face);
    }

    private static final class Surface extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BlockState state;
        private final Map<BlockState,BakedModel[]> raw;
        private final TerrainFaceQuads painter;
        private final boolean cliff;
        Surface(BakedModel original, BlockState state, Map<BlockState,BakedModel[]> raw, TerrainFaceQuads painter) {
            super(original); this.state=state; this.raw=raw; this.painter=painter; this.cliff=state.is(ModBlocks.CLIFF.get());
        }
        private BakedModel surface(int season) { return cliff ? raw.get(state)[season] : originalModel; }
        private int season(ModelData data) { Faces faces=data.get(FACES); return faces == null ? TerrainSeasonTextures.currentTextureSet() : faces.season(); }

        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            int season=TerrainSeasonTextures.currentTextureSet();
            ModelData base=surface(season).getModelData(level,pos,state,data);
            List<List<TerrainFaceConnections.Connection>> connections=new ArrayList<>(6);
            for (Direction face : Direction.values()) {
                var list=TerrainFaceConnections.collect(level,pos,state,face);
                // Existing authored top masks and inset farmland finishing own coplanar top connections.
                if (!cliff && face == Direction.UP) list=list.stream()
                        .filter(c -> c.folded() || (state.is(ModBlocks.SAND.get()) && c.state().is(ModBlocks.DIRT.get()))
                                || (state.is(ModBlocks.HARD_SOIL.get()) && TerrainSoils.bare(c.state())))
                        .toList();
                connections.add(list);
            }
            return base.derive().with(FACES,new Faces(season,List.copyOf(connections))).build();
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state,side,random,ModelData.EMPTY,null);
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data, @Nullable RenderType renderType) {
            int season=season(data);
            List<BakedQuad> base=surface(season).getQuads(state,side,random,data,renderType);
            Faces faces=data.get(FACES);
            if (side == null || faces == null || faces.connections().get(side.ordinal()).isEmpty() || base.isEmpty()) return base;
            var connections=faces.connections().get(side.ordinal());
            List<TerrainFaceQuads.Paint> paints=new ArrayList<>();
            for (var connection : connections) {
                BlockState donor=connection.state();
                // Grooves and fertilizer stay on farmland; a folded farm face contributes its soil body.
                if (TerrainSoils.farmland(donor) && connection.face() == Direction.UP)
                    donor=TerrainSoils.substrate(donor).defaultBlockState();
                else if (TerrainSoils.farmland(donor) && TerrainSoils.farmland(this.state))
                    donor=this.state.setValue(FarmBlock.MOISTURE, donor.getValue(FarmBlock.MOISTURE));
                paints.add(new TerrainFaceQuads.Paint(connection,nativeFace(raw.get(donor)[season],donor,connection.face())));
            }
            BakedQuad nativeQuad=nativeFace(raw.get(this.state)[season],this.state,side);
            List<BakedQuad> result=new ArrayList<>();
            for (BakedQuad quad : base) {
                // Preserve the separate authored top fringes, including mine plank and grass corner models.
                if (quad.getDirection() == side && quad.getSprite() == nativeQuad.getSprite()) result.addAll(painter.get(quad,paints));
                else result.add(quad);
            }
            return List.copyOf(result);
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return surface(season(data)).getRenderTypes(state,random,data);
        }
        @Override public TextureAtlasSprite getParticleIcon() { return surface(TerrainSeasonTextures.currentTextureSet()).getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return surface(season(data)).getParticleIcon(data); }
    }

    private static final class CliffItem extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;
        CliffItem(BakedModel original, Map<BlockState,BakedModel[]> raw) {
            super(original);
            overrides=new ItemOverrides() {
                @Override public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                    Integer variant=stack.getOrDefault(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).get(TerrainVariants.CLIFF);
                    BlockState state=ModBlocks.CLIFF.get().defaultBlockState().setValue(TerrainVariants.CLIFF,variant == null ? 0 : variant);
                    return raw.get(state)[TerrainSeasonTextures.currentTextureSet()];
                }
            };
        }
        @Override public ItemOverrides getOverrides() { return overrides; }
    }
}
