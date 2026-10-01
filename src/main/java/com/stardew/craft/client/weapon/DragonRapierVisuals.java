package com.stardew.craft.client.weapon;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.Config;
import com.stardew.craft.combat.network.DragonRapierFxPayload;
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
public final class DragonRapierVisuals {
    private record Effect(DragonRapierFxPayload data,long born) {}
    private static final List<Effect> EFFECTS=new ArrayList<>();
    private static final Map<String,Long> SEEN=new LinkedHashMap<>();
    private static final Map<Integer,Long> SOUNDS=new HashMap<>();
    private static ClientLevel level;
    private DragonRapierVisuals() {}
    private static void ensureLevel(){var next=Minecraft.getInstance().level;if(next!=level){level=next;EFFECTS.clear();SEEN.clear();SOUNDS.clear();}}
    public static void start(WeaponSkillAnimPayload p) {
        ensureLevel();var mc=Minecraft.getInstance();if(level==null||mc.player==null||mc.player.distanceToSqr(p.originX(),p.originY(),p.originZ())>48*48)return;
        boolean rapier="rapier".equals(p.weaponId());
        level.playLocalSound(p.originX(),p.originY()+1,p.originZ(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,rapier?.25f:.55f,rapier?1.35f:.65f,false);
    }
    public static void receive(DragonRapierFxPayload p) {
        ensureLevel();var mc=Minecraft.getInstance();if(level==null||mc.player==null||p.phase()<0||p.phase()>4||!Double.isFinite(p.center().lengthSqr())||!Float.isFinite(p.yaw())||mc.player.distanceToSqr(p.center())>48*48)return;
        String base=DragonRapierGeometry.base(p.skill());if(base.isEmpty())return;
        boolean rapier=base.equals("rapier_riposte"),guard=p.skill().endsWith("_guard"),breath=base.equals("dragontooth_club_breath")&&p.phase()>0;
        String key=p.caster()+":"+p.tick()+":"+p.skill()+":"+p.phase()+":"+p.target();if(SEEN.putIfAbsent(key,level.getGameTime())!=null)return;
        while(SEEN.size()>256)SEEN.remove(SEEN.keySet().iterator().next());
        if(Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()){EFFECTS.add(new Effect(p,level.getGameTime()));while(EFFECTS.size()>96)com.stardew.craft.port.PortJava.removeFirst(EFFECTS);}
        if(p.target()<0)level.playLocalSound(p.center().x,p.center().y+.6,p.center().z,guard?SoundEvents.SHIELD_BLOCK:breath?SoundEvents.FIRECHARGE_USE:SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,breath?.22f:guard?.5f:.5f,rapier?1.2f:breath?.75f:.6f,false);
        else if(!Long.valueOf(p.tick()).equals(SOUNDS.put(p.caster(),p.tick()))) {
            level.playLocalSound(p.center().x,p.center().y,p.center().z,rapier?SoundEvents.TRIDENT_HIT:SoundEvents.PLAYER_ATTACK_STRONG,SoundSource.PLAYERS,rapier?.4f:breath?.3f:.65f,rapier?1.25f:breath?.9f:.55f,false);
            if(p.caster()==mc.player.getId()){CameraShakeState.kick(rapier?.04f:breath?.03f:.12f,rapier?2:3,0);
                MeleeWeaponVisuals.authoredHeavyContact(p.skill(),rapier?.35f:breath?0:.85f);
            }
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){ensureLevel();if(level==null||Minecraft.getInstance().isPaused())return;long now=level.getGameTime();EFFECTS.removeIf(e->now-e.born>=8);SEEN.values().removeIf(t->now-t>40);SOUNDS.values().removeIf(t->now-t>40);}
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;ensureLevel();if(level==null||!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get())return;
        var mc=Minecraft.getInstance();double now=level.getGameTime()+e.getPartialTick();Vec3 camera=e.getCamera().getPosition();var stack=e.getPoseStack();stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
        var pose=stack.last().pose();var buffers=mc.renderBuffers().bufferSource();
        for(int pass=0;pass<2;pass++) {
            boolean edge=pass==0;var type=edge?WeaponEffectRenderTypes.IMPACT_EDGE:WeaponEffectRenderTypes.MOLTEN_GLOW;var out=buffers.getBuffer(type);
            for(Effect effect:EFFECTS) {
                var p=effect.data;float age=(float)(now-effect.born);if(age<0||age>=8||p.center().distanceToSqr(camera)>48*48)continue;
                if(p.target()<0)DragonRapierGeometry.draw(out,pose,p.center(),p.skill(),p.phase(),age,p.yaw(),edge);
                else {
                    Vec3 point=p.center();if(level.getEntity(p.target()) instanceof LivingEntity target)point=target.getBoundingBox().clip(camera,target.getBoundingBox().getCenter()).orElse(point);
                    Vec3 normal=camera.subtract(point).normalize(),right=normal.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<1e-6)right=new Vec3(1,0,0);Vec3 up=right.cross(normal).normalize();point=point.add(normal.scale(.05));
                    DragonRapierGeometry.contact(out,pose,point,right,up,normal,p.skill(),p.phase(),age,edge);
                }
            }
            buffers.endBatch(type);
        }
        stack.popPose();
    }
}
