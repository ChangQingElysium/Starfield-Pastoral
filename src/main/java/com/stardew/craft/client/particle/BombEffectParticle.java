package com.stardew.craft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Small, hard-edged fuse sparks and single-pass ground bursts/dust. */
@OnlyIn(Dist.CLIENT)
public final class BombEffectParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final int frames;
    private final double frameMillis;
    private final double delayMillis;
    private final boolean luminous;

    public BombEffectParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites,
                              int frames, double frameMillis, double delayMillis,
                              float size, boolean luminous) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.frames = frames;
        this.frameMillis = Math.max(20, frameMillis);
        this.delayMillis = Math.max(0, delayMillis);
        this.luminous = luminous;
        this.lifetime = (int) Math.ceil((this.delayMillis + frames * this.frameMillis) / 50);
        this.quadSize = size / 2;
        this.hasPhysics = false;
        this.xd = this.yd = this.zd = 0;
        setSprite(sprites.get(0, frames - 1));
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (++age >= lifetime) remove();
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        double time = (age + partialTick) * 50 - delayMillis;
        if (time < 0 || time >= frames * frameMillis) return;
        setSprite(sprites.get(Math.min(frames - 1, (int) (time / frameMillis)), frames - 1));
        super.render(buffer, camera, partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return luminous ? 15728880 : super.getLightColor(partialTick);
    }
}
