package com.stardew.craft.platform.fabric.client.particle;

import com.stardew.craft.platform.fabric.client.model.BakedModels;
import com.stardew.craft.platform.fabric.client.model.DynamicModelEmitter;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public final class TerrainParticleSprites {
    private TerrainParticleSprites() {}

    public interface SpriteAccess {
        void stardewcraft$updateSprite(BlockState state, BlockPos sourcePos);
    }

    public static TerrainParticle update(TerrainParticle particle, BlockState state, BlockPos sourcePos) {
        // A particle's randomized xyz is not the source block. Unknown sources retain the EMPTY-data sprite.
        if (sourcePos != null) ((SpriteAccess) particle).stardewcraft$updateSprite(state, sourcePos);
        return particle;
    }

    public static TextureAtlasSprite getTexture(BlockModelShaper shaper, BlockState state,
            BlockAndTintGetter level, BlockPos sourcePos) {
        return getTexture(shaper.getBlockModel(state), state, level, sourcePos);
    }

    static TextureAtlasSprite getTexture(BakedModel model, BlockState state,
            BlockAndTintGetter level, BlockPos sourcePos) {
        Objects.requireNonNull(sourcePos, "particle source position");
        var attached = DynamicModelEmitter.attachment(level, sourcePos);
        var resolved = Objects.requireNonNull(BakedModels.getModelData(model, level, sourcePos, state, attached), "particle model data");
        return BakedModels.getParticleIcon(model, resolved);
    }
}
