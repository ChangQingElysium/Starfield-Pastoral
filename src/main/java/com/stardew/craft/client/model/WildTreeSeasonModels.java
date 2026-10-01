package com.stardew.craft.client.model;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.tree.ForestCanopySnow;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Authored foliage replaces multiply tinting; the saved prefab and its variant never change. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WildTreeSeasonModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final ModelProperty<Boolean> SNOW_EXPOSED = new ModelProperty<>();

    private WildTreeSeasonModels() {}

    private static ModelResourceLocation id(String species, int season, String part) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/tree/" + species + "/" + SEASONS[season] + "/" + part), "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        PineCanopyModels.register(event);
        for (String species : List.of("blossom", "fine", "pointed"))
            for (int s = 0; s < 4; s++) event.register(id(species, s, "leaves"));
        for (String species : List.of("forest", "broadleaf")) {
            for (int s = 0; s < 4; s++) event.register(id(species, s, "leaves"));
            event.register(id(species, 3, "leaves_sheltered"));
        }
        for (var tree : WildTrees.ALL) {
            for (int s = 0; s < 4; s++) event.register(id(tree.id(), s, "leaves"));
            if (tree != WildTrees.MYSTIC_TREE) event.register(id(tree.id(), 3, "branch"));
        }
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        var decorative = List.of(ModBlocks.BLOSSOM_LEAVES, ModBlocks.FINE_LEAVES, ModBlocks.POINTED_LEAVES);
        var names = List.of("blossom", "fine", "pointed");
        for (int i = 0; i < decorative.size(); i++) {
            BakedModel[] models = new BakedModel[4];
            for (int s = 0; s < 4; s++) models[s] = Objects.requireNonNull(event.getModels().get(id(names.get(i), s, "leaves")));
            wrap(event, decorative.get(i).get(), models, true);
        }
        var canopyBlocks = List.of(ModBlocks.FOREST_LEAVES, ModBlocks.BROADLEAF_LEAVES);
        var canopyNames = List.of("forest", "broadleaf");
        for (int i = 0; i < canopyBlocks.size(); i++) {
            BakedModel[] models = new BakedModel[4];
            for (int s = 0; s < 4; s++) models[s] = Objects.requireNonNull(event.getModels().get(id(canopyNames.get(i), s, "leaves")));
            BakedModel sheltered = Objects.requireNonNull(event.getModels().get(id(canopyNames.get(i), 3, "leaves_sheltered")));
            wrap(event, canopyBlocks.get(i).get(), models, true, sheltered);
        }
        for (var tree : WildTrees.ALL) {
            BakedModel[] leaves = new BakedModel[4];
            for (int s = 0; s < 4; s++) leaves[s] = Objects.requireNonNull(event.getModels().get(id(tree.id(), s, "leaves")));
            if (tree != WildTrees.PINE) wrap(event, tree.modernLeaves().get(), leaves, true);
            if (tree == WildTrees.OAK) wrap(event, ModBlocks.OAK_LEAVES_QUESTION.get(), leaves, true);
            if (tree != WildTrees.MYSTIC_TREE) {
                BakedModel base = Objects.requireNonNull(event.getModels().get(
                        BlockModelShaper.stateToModelLocation(tree.modernBranch().get().defaultBlockState())));
                wrap(event, tree.modernBranch().get(), new BakedModel[]{base, base, base,
                        Objects.requireNonNull(event.getModels().get(id(tree.id(), 3, "branch")))}, false);
            }
        }
        // Inventory models retain their normal appearance, including in winter.
        PineCanopyModels.bake(event);
    }

    private static void wrap(ModelEvent.ModifyBakingResult event, Block block, BakedModel[] seasons, boolean leaves) {
        wrap(event, block, seasons, leaves, null);
    }

    private static void wrap(ModelEvent.ModifyBakingResult event, Block block, BakedModel[] seasons,
                             boolean leaves, @Nullable BakedModel shelteredWinter) {
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            var location = BlockModelShaper.stateToModelLocation(state);
            event.getModels().put(location, new SeasonalModel(Objects.requireNonNull(event.getModels().get(location)), seasons, leaves, shelteredWinter));
        }
    }

    private static final class SeasonalModel extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] seasons;
        private final boolean leaves;
        @Nullable private final BakedModel shelteredWinter;

        SeasonalModel(BakedModel original, BakedModel[] seasons, boolean leaves, @Nullable BakedModel shelteredWinter) {
            super(original);
            this.seasons = seasons;
            this.leaves = leaves;
            this.shelteredWinter = shelteredWinter;
        }

        private BakedModel current() {
            var level = Minecraft.getInstance().level;
            return level != null && level.dimension().equals(ModDimensions.STARDEW_VALLEY) && StardewTimeHud.isTimeSynced()
                    ? seasons[TerrainSeasonTextures.currentTextureSet()] : originalModel;
        }

        private BakedModel current(ModelData data) {
            BakedModel model = current();
            return shelteredWinter != null && model == seasons[3] && Boolean.FALSE.equals(data.get(SNOW_EXPOSED))
                    ? shelteredWinter : model;
        }

        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return shelteredWinter == null ? super.getModelData(level, pos, state, data)
                    : data.derive().with(SNOW_EXPOSED, ForestCanopySnow.exposed(level, pos)).build();
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return current().getQuads(state, side, random);
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType type) {
            return current(data).getQuads(state, side, random, data, type);
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return leaves ? ChunkRenderTypeSet.of(RenderType.cutoutMipped()) : current().getRenderTypes(state, random, data);
        }
        @Override public TextureAtlasSprite getParticleIcon() { return current().getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return current().getParticleIcon(data); }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) { return List.of(this); }
    }
}
