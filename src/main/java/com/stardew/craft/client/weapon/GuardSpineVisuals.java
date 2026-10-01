package com.stardew.craft.client.weapon;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.GuardSpineStatePayload;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;

@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class GuardSpineVisuals {
    private record State(UUID owner,int phase,long end){}
    private record Cast(int actor,String skill,long tick){}
    private static final Map<Integer,State> STATES=new LinkedHashMap<>();
    private static final Map<Cast,Long> CASTS=new LinkedHashMap<>();
    private static ClientLevel level;
    private GuardSpineVisuals(){}
    public static void ensureLevel(){var next=Minecraft.getInstance().level;if(level!=next){level=next;STATES.clear();CASTS.clear();SteelSpineFuryClientState.clear();}}
    public static void state(GuardSpineStatePayload p){
        ensureLevel();if(level==null)return;
        if(p.phase()==GuardSpineStatePayload.GUARD_END){WeaponSkillAnimationClient.stopMatching(p.actor(),LIGHT_GUARD);return;}
        if(p.phase()==GuardSpineStatePayload.CLEAR){STATES.remove(p.actor());return;}
        if(p.phase()<0||p.phase()>2||!(level.getEntity(p.actor()) instanceof LivingEntity actor))return;
        STATES.put(p.actor(),new State(actor.getUUID(),p.phase(),p.phase()==0?level.getGameTime()+Math.clamp(p.duration(),1,80):Long.MAX_VALUE));
        while(STATES.size()>64)STATES.remove(STATES.keySet().iterator().next());
    }
    public static int bladePhase(LivingEntity actor){ensureLevel();State s=STATES.get(actor.getId());return s!=null&&s.owner.equals(actor.getUUID())&&level.getGameTime()<=s.end?s.phase:-1;}
    public static void start(WeaponSkillAnimPayload p){
        ensureLevel();if(level==null)return;
        if(CASTS.putIfAbsent(new Cast(p.casterEntityId(),p.skillId(),p.startGameTick()),level.getGameTime())!=null)return;
        while(CASTS.size()>64)CASTS.remove(CASTS.keySet().iterator().next());
        if(SPINE_STRIKE.equals(p.skillId())||SPINE_WEAK.equals(p.skillId()))STATES.remove(p.casterEntityId());
        if(!LIGHT_GUARD.equals(p.skillId())&&!SPINE_ENTER.equals(p.skillId()))
            level.playLocalSound(p.originX(),p.originY()+.8,p.originZ(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.32f,LIGHT_COUNTER.equals(p.skillId())?1.4f:.75f,false);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        ensureLevel();if(level==null)return;long now=level.getGameTime();CASTS.values().removeIf(t->now-t>24);
        STATES.entrySet().removeIf(e->now>e.getValue().end||!(level.getEntity(e.getKey()) instanceof LivingEntity actor)||!actor.isAlive()||!actor.getUUID().equals(e.getValue().owner));
    }
}
