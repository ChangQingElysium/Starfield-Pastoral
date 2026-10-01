package com.stardew.craft.client.weapon;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.Config;
import com.stardew.craft.combat.network.IronClubFxPayload;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class IronClubVisuals {
    private record Effect(IronClubFxPayload data,long born) {}
    private static final List<Effect> EFFECTS=new ArrayList<>();
    private static final Map<String,Long> SEEN=new LinkedHashMap<>();
    private static final Map<Integer,Long> SOUNDS=new HashMap<>();
    private static ClientLevel level;
    private IronClubVisuals() {}
    private static void ensureLevel(){var next=Minecraft.getInstance().level;if(next!=level){level=next;EFFECTS.clear();SEEN.clear();SOUNDS.clear();}}
    public static void start(WeaponSkillAnimPayload p) {
        ensureLevel();var mc=Minecraft.getInstance();if(level==null||mc.player==null||mc.player.distanceToSqr(p.originX(),p.originY(),p.originZ())>48*48)return;
        level.playLocalSound(p.originX(),p.originY()+1,p.originZ(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.55f,.65f,false);
    }
    public static void receive(IronClubFxPayload p) {
        ensureLevel();var mc=Minecraft.getInstance();if(level==null||mc.player==null||p.phase()<0||p.phase()>3||!Double.isFinite(p.center().lengthSqr())||!Float.isFinite(p.yaw())||mc.player.distanceToSqr(p.center())>48*48)return;
        String key=p.caster()+":"+p.tick()+":"+p.phase()+":"+p.target();if(SEEN.putIfAbsent(key,level.getGameTime())!=null)return;
        while(SEEN.size()>256)SEEN.remove(SEEN.keySet().iterator().next());
        if(Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()){EFFECTS.add(new Effect(p,level.getGameTime()));while(EFFECTS.size()>96)com.stardew.craft.port.PortJava.removeFirst(EFFECTS);}
        if(p.target()<0)level.playLocalSound(p.center().x,p.center().y+.6,p.center().z,SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.55f,p.phase()==0?.6f:.75f,false);
        else if(!Long.valueOf(p.tick()).equals(SOUNDS.put(p.caster(),p.tick()))) {
            level.playLocalSound(p.center().x,p.center().y,p.center().z,p.phase()==2?SoundEvents.ANVIL_HIT:SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,p.phase()==2?.36f:.65f,p.phase()==2?.55f:.65f,false);
            if(p.caster()==mc.player.getId()){CameraShakeState.kick(p.phase()==2?.105f:.085f,3,0);
                MeleeWeaponVisuals.authoredHeavyContact(p.skill(),.9f);
            }
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){ensureLevel();if(level==null||Minecraft.getInstance().isPaused())return;long now=level.getGameTime();EFFECTS.removeIf(e->now-e.born>=7);SEEN.values().removeIf(t->now-t>40);SOUNDS.values().removeIf(t->now-t>40);}
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;ensureLevel();if(level==null||!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get())return;
        var mc=Minecraft.getInstance();double now=level.getGameTime()+e.getPartialTick();Vec3 camera=e.getCamera().getPosition();var stack=e.getPoseStack();stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
        var pose=stack.last().pose();var buffers=mc.renderBuffers().bufferSource();
        for(int pass=0;pass<2;pass++) {
            boolean edge=pass==0;var type=edge?WeaponEffectRenderTypes.IMPACT_EDGE:WeaponEffectRenderTypes.MOLTEN_GLOW;var out=buffers.getBuffer(type);
            for(Effect effect:EFFECTS) {
                var p=effect.data;float age=(float)(now-effect.born);if(age<0||age>=7||p.center().distanceToSqr(camera)>48*48)continue;
                if(p.target()<0)IronClubGeometry.draw(out,pose,p.center(),p.phase(),age,p.yaw(),edge);
                else {
                    Vec3 point=p.center();if(level.getEntity(p.target()) instanceof LivingEntity target)point=target.getBoundingBox().clip(camera,target.getBoundingBox().getCenter()).orElse(point);
                    Vec3 normal=camera.subtract(point).normalize(),right=normal.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<1e-6)right=new Vec3(1,0,0);Vec3 up=right.cross(normal).normalize();point=point.add(normal.scale(.05));
                    IronClubGeometry.contact(out,pose,point,right,up,normal,p.phase()==2,age,edge);
                }
            }
            buffers.endBatch(type);
        }
        stack.popPose();
    }
}
