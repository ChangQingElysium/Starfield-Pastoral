package com.stardew.craft.client.model;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.MetalSidingConnections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
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
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Selects one opaque native face per direction, without coplanar overlays or extra geometry. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MetalSidingModels {
    private record Surface(long masks, int variants) {
        int mask(Direction face) { return (int) ((masks >>> (face.ordinal() * 8)) & 255); }
        int variant(Direction face) { return (variants >>> (face.ordinal() * 2)) & 3; }
    }
    private static final ModelProperty<Surface> SURFACE = new ModelProperty<>();
    private MetalSidingModels() {}

    private static ModelResourceLocation id(int variant, int mask) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/gray_metal_siding/" + variant + "_" + mask), "standalone");
    }

    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for (int v = 0; v < 4; v++) for (int mask = 0; mask < 256; mask++)
            if (MetalSidingConnections.canonical(mask) == mask) event.register(id(v, mask));
    }

    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        var key = BlockModelShaper.stateToModelLocation(ModBlocks.GRAY_METAL_SIDING.get().defaultBlockState());
        event.getModels().put(key, new Connected(Objects.requireNonNull(event.getModels().get(key)), event));
        // The item keeps its ordinary isolated cube model, including GUI and dropped-item rendering.
    }

    private static final class Connected extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final List<BakedQuad>[][][] faces;
        private final TextureAtlasSprite particle;

        @SuppressWarnings("unchecked")
        Connected(BakedModel original, ModelEvent.ModifyBakingResult event) {
            super(original);
            faces = new List[4][256][6];
            for (int v = 0; v < 4; v++) for (int mask = 0; mask < 256; mask++) {
                if (MetalSidingConnections.canonical(mask) != mask) continue;
                var model = Objects.requireNonNull(event.getModels().get(id(v, mask)));
                for (Direction face : Direction.values())
                    faces[v][mask][face.ordinal()] = List.copyOf(model.getQuads(null, face, RandomSource.create(0)));
            }
            particle = Objects.requireNonNull(event.getModels().get(id(0, 255))).getParticleIcon();
        }

        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            long masks = 0;
            int variants = 0;
            for (Direction face : Direction.values()) {
                masks |= (long) MetalSidingConnections.mask(level::getBlockState, pos, state.getBlock(), face) << (face.ordinal() * 8);
                variants |= MetalSidingConnections.variant(pos, face) << (face.ordinal() * 2);
            }
            return data.derive().with(SURFACE, new Surface(masks, variants)).build();
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType type) {
            if (side == null || (type != null && type != RenderType.solid())) return List.of();
            Surface surface = data.get(SURFACE);
            return faces[surface == null ? 0 : surface.variant(side)][surface == null ? 0 : surface.mask(side)][side.ordinal()];
        }

        @Override public TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return particle; }
    }
}
