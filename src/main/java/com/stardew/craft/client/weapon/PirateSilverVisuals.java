package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.PirateSilverEffectPayload;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.combat.skill.WeaponGroundContact;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;

@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class PirateSilverVisuals{
    private record Cast(int actor,String skill,long tick){}
    private record Event(int actor,long tick,PirateSilverEffectPayload.Phase phase){}
    private record Anchor(UUID actor,long end,List<Vec3[]> paths){}
    private record Wake(Vec3 from,Vec3 to,long start){}
    private record Dash(UUID actor,long start,Vec3 previous){}
    private static final Map<Cast,Long> CASTS=new LinkedHashMap<>();
    private static final Map<Event,Long> EVENTS=new LinkedHashMap<>();
    private static final Map<Integer,Anchor> ANCHORS=new LinkedHashMap<>();
    private static final Map<Integer,Dash> DASHES=new LinkedHashMap<>();
    private static final List<Wake> WAKES=new ArrayList<>();
    private static ClientLevel level;private static long healedAt=-100;
    private PirateSilverVisuals(){}
    public static void ensureLevel(){var next=Minecraft.getInstance().level;if(next!=level){level=next;CASTS.clear();EVENTS.clear();ANCHORS.clear();DASHES.clear();WAKES.clear();healedAt=-100;SilverSaberFoldbackClientState.clear();}}
    public static void start(WeaponSkillAnimPayload p){
        ensureLevel();var mc=Minecraft.getInstance();Vec3 point=new Vec3(p.originX(),p.originY()+.8,p.originZ());
        if(level==null||mc.player==null||mc.player.distanceToSqr(point)>48*48)return;
        if(CASTS.putIfAbsent(new Cast(p.casterEntityId(),p.skillId(),p.startGameTick()),level.getGameTime())!=null)return;
        while(CASTS.size()>64)CASTS.remove(CASTS.keySet().iterator().next());
        level.playLocalSound(point.x,point.y,point.z,SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.34f,PIRATE_PLUNDER.equals(p.skillId())?.8f:1.2f,false);
    }
    public static float healAge(float partial){return level==null?-1:level.getGameTime()-healedAt+partial;}
    public static boolean recentPlunder(int actor){return level!=null&&CASTS.entrySet().stream().anyMatch(e->e.getKey().actor==actor&&PIRATE_PLUNDER.equals(e.getKey().skill)&&level.getGameTime()-e.getValue()<60);}
    public static void effect(PirateSilverEffectPayload p){
        ensureLevel();var mc=Minecraft.getInstance();if(level==null||mc.player==null||!valid(p))return;
        if(p.phase()==PirateSilverEffectPayload.Phase.END){ANCHORS.remove(p.caster());return;}
        if(Math.min(mc.player.distanceToSqr(p.from()),mc.player.distanceToSqr(p.to()))>48*48)return;
        long now=level.getGameTime();if(EVENTS.putIfAbsent(new Event(p.caster(),p.tick(),p.phase()),now)!=null)return;
        while(EVENTS.size()>96)EVENTS.remove(EVENTS.keySet().iterator().next());
        if(p.phase()==PirateSilverEffectPayload.Phase.HEAL){if(p.caster()==mc.player.getId())healedAt=now;return;}
        if(!(level.getEntity(p.caster()) instanceof Player actor))return;
        if(p.phase()==PirateSilverEffectPayload.Phase.ANCHOR){
            List<Vec3[]> paths=new ArrayList<>();
            for(Vec3[] path:PirateSilverGeometry.anchorPaths(p.from())){
                boolean valid=true;double y=Double.NaN;
                for(int i=0;i<path.length;i++){var hit=WeaponGroundContact.find(level,actor,path[i]);
                    if(hit==null||(!Double.isNaN(y)&&Math.abs(y-hit.getLocation().y)>.26)){valid=false;break;}
                    path[i]=hit.getLocation().add(0,.028,0);y=path[i].y;}
                if(valid)paths.add(path);
            }
            ANCHORS.put(p.caster(),new Anchor(actor.getUUID(),now+Math.min(20,p.duration()),paths));
            while(ANCHORS.size()>32)ANCHORS.remove(ANCHORS.keySet().iterator().next());
        }else if(p.phase()==PirateSilverEffectPayload.Phase.DASH){
            DASHES.put(p.caster(),new Dash(actor.getUUID(),now,p.from().add(0,.65,0)));
            while(DASHES.size()>32)DASHES.remove(DASHES.keySet().iterator().next());
        }else if(p.phase()==PirateSilverEffectPayload.Phase.BLINK){
            Vec3 forward=p.to().subtract(p.from()).multiply(1,0,1).normalize();if(forward.lengthSqr()<1e-6)forward=new Vec3(0,0,1);
            for(Vec3 endpoint:new Vec3[]{p.from(),p.to()})addWake(endpoint.add(0,.65,0).subtract(forward.scale(.38)),endpoint.add(0,.65,0).add(forward.scale(.38)),now);
        }
    }
    static boolean valid(PirateSilverEffectPayload p){return finite(p.from())&&finite(p.to())&&p.duration()>=0&&p.duration()<=60;}
    private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    public static boolean ownsDash(Player player){ensureLevel();var dash=DASHES.get(player.getId());return level!=null&&dash!=null&&dash.actor.equals(player.getUUID())&&level.getGameTime()-dash.start<=5;}
    private static void addWake(Vec3 from,Vec3 to,long now){WAKES.add(new Wake(from,to,now));while(WAKES.size()>96)WAKES.removeFirst();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        ensureLevel();var mc=Minecraft.getInstance();if(level==null||mc.player==null||mc.isPaused())return;long now=level.getGameTime();
        CASTS.values().removeIf(t->now-t>=60);EVENTS.values().removeIf(t->now-t>30);WAKES.removeIf(w->now-w.start>=6);
        ANCHORS.entrySet().removeIf(e->now>=e.getValue().end||!(level.getEntity(e.getKey()) instanceof Player actor)||!actor.isAlive()||!actor.getUUID().equals(e.getValue().actor));
        var it=DASHES.entrySet().iterator();while(it.hasNext()){
            var e=it.next();Dash dash=e.getValue();
            if(!(level.getEntity(e.getKey()) instanceof Player player)||!player.isAlive()||!player.getUUID().equals(dash.actor)||now-dash.start>5
                    ||player.distanceToSqr(mc.player)>32*32||!(player.getMainHandItem().getItem() instanceof IStardewWeapon weapon)||!"silver_saber".equals(weapon.getWeaponId())
                    ||(player==mc.player&&!DashMovementClientState.isActive(player))){it.remove();continue;}
            Vec3 current=player.position().add(0,.65,0);
            if(Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()&&PirateSilverGeometry.validSegment(dash.previous,current)
                    &&level.clip(new ClipContext(dash.previous,current,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS)addWake(dash.previous,current,now);
            e.setValue(new Dash(dash.actor,dash.start,current));
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;ensureLevel();var mc=Minecraft.getInstance();
        if(level==null||!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get())return;
        float partial=event.getPartialTick();Vec3 camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);var pose=event.getPoseStack().last().pose();
        for(Anchor a:ANCHORS.values())for(Vec3[] path:a.paths)if(path[0].distanceToSqr(camera)<32*32){
            Vec3[] local=Arrays.stream(path).map(p->p.subtract(camera)).toArray(Vec3[]::new);
            PirateSilverGeometry.anchorStroke(out,pose,local,Math.clamp((a.end-level.getGameTime()-partial)/5,0,1));}
        for(Wake w:WAKES)if(w.from.distanceToSqr(camera)<32*32)PirateSilverGeometry.wake(out,pose,w.from.subtract(camera),w.to.subtract(camera),fade(level.getGameTime()-w.start+partial));
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);
    }
    static float fade(float age){return age<0||age>=6?0:(float)Math.pow(1-age/6,2);}
}
