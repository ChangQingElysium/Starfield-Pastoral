package com.stardew.craft.client.particle;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
/** Source sprite timing is sampled per rendered frame, including the 40ms healing frames. */
public final class MonsterSpellParticle extends TextureSheetParticle {
    private final SpriteSet sprites;private final int count,milliseconds;
    public MonsterSpellParticle(ClientLevel level,double x,double y,double z,SpriteSet sprites,int count,int milliseconds){super(level,x,y,z);this.sprites=sprites;this.count=count;this.milliseconds=milliseconds;lifetime=(int)Math.ceil(count*milliseconds/50.);quadSize=.5F;hasPhysics=false;xd=yd=zd=0;setSprite(sprites.get(0,count-1));}
    public void tint(com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption color){setColor(color.getRed(),color.getGreen(),color.getBlue());setAlpha(color.getAlpha());}
    @Override public void tick(){xo=x;yo=y;zo=z;if(++age>=lifetime)remove();}
    @Override public void render(VertexConsumer buffer,Camera camera,float p){double time=(age+p)*50;if(time>=count*milliseconds)return;setSprite(sprites.get(Math.min(count-1,(int)(time/milliseconds)),count-1));super.render(buffer,camera,p);}
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    @Override protected int getLightColor(float p){return 15728880;}
}
