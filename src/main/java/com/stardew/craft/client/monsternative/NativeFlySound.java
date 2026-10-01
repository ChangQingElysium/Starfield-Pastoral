package com.stardew.craft.client.monsternative;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.monster.MineFlyEntity;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

/** Fly.cs has one shared buzz, set by the nearest fly rather than additive overlapping loops. */
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class NativeFlySound {
    private static Buzz buzz;
    private NativeFlySound(){}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.isPaused())return;
        if(mc.level==null||mc.player==null){if(buzz!=null)mc.getSoundManager().stop(buzz);buzz=null;return;}
        if(buzz!=null&&(buzz.level!=mc.level||buzz.isStopped()||!mc.getSoundManager().isActive(buzz))){mc.getSoundManager().stop(buzz);buzz=null;}
        if(buzz==null&&nearest(mc)!=null){buzz=new Buzz(mc.level);mc.getSoundManager().play(buzz);}
    }
    private static MineFlyEntity nearest(Minecraft mc){
        MineFlyEntity best=null;double distance=256;
        if(mc.level==null||mc.player==null)return null;
        for(var e:mc.level.entitiesForRendering())if(e instanceof MineFlyEntity fly&&fly.isAlive()&&!fly.isRemoved()){
            double d=fly.distanceToSqr(mc.player);if(d<distance){distance=d;best=fly;}
        }return best;
    }
    private static final class Buzz extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        Buzz(ClientLevel level){super(ModSounds.FLY_BUZZING.get(),SoundSource.HOSTILE,SoundInstance.createUnseededRandom());this.level=level;looping=true;delay=0;attenuation=Attenuation.NONE;volume=.01F;tick();}
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){var mc=Minecraft.getInstance();if(mc.level!=level||mc.player==null){stop();return;}
            var fly=nearest(mc);if(fly==null){stop();return;}
            x=fly.getX();y=fly.getY();z=fly.getZ();volume=(float)Math.max(0,1-Math.sqrt(fly.distanceToSqr(mc.player))/16);}
    }
}
