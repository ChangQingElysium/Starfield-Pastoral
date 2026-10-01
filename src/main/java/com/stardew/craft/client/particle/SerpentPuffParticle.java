package com.stardew.craft.client.particle;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption;
/** Five row-five green source clouds; motion is away from the killer, with per-cloud falloff. */
public final class SerpentPuffParticle extends TextureSheetParticle {
 private final SpriteSet sprites;private final int interval;
 public SerpentPuffParticle(ClientLevel l,double x,double y,double z,double dx,double dy,double dz,SpriteSet s,int interval,ColorParticleOption c){super(l,x,y,z);sprites=s;this.interval=interval;lifetime=interval*10/50;quadSize=.5F;hasPhysics=false;xd=dx;yd=dy;zd=dz;setColor(c.getRed(),c.getGreen(),c.getBlue());setAlpha(c.getAlpha());setSprite(s.get(0,9));}
 @Override public void tick(){xo=x;yo=y;zo=z;if(++age>=lifetime){remove();return;}x+=xd;y+=yd;z+=zd;}
 @Override public void render(VertexConsumer b,Camera c,float p){setSprite(sprites.get(Math.min(9,(int)((age+p)*50/interval)),9));super.render(b,c,p);}
 @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
}
