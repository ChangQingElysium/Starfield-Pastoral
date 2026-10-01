package com.stardew.craft.client.weapon;
import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.BloodForgeEffectPayload;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import java.util.LinkedHashMap;
import java.util.Map;
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
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;

@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class BloodForgeVisuals {
    private record StateKey(int caster,boolean moon) {}
    private record DarkState(long cast,long end,boolean active) {}
    private record EffectKey(int caster,int target,long cast) {}
    private record Effect(BloodForgeEffectPayload payload,long start) {}
    private static final Map<StateKey,DarkState> STATES=new LinkedHashMap<>();
    private static final Map<EffectKey,Effect> HEAT=new LinkedHashMap<>(), RECOVERY=new LinkedHashMap<>();
    private static ClientLevel level;
    private BloodForgeVisuals() {}
    private static void ensureLevel() {
        var next=Minecraft.getInstance().level;
        if(level!=next) {level=next;STATES.clear();HEAT.clear();RECOVERY.clear();
            DarkSwordBloodDebtClientState.clear();DarkSwordBloodMoonClientState.clear();}
    }
    public static void darkState(int caster,long cast,boolean active,int duration,boolean moon) {
        ensureLevel(); if(level==null) return;
        StateKey key=new StateKey(caster,moon);DarkState prior=STATES.get(key);
        if(prior!=null && (cast<prior.cast || (cast==prior.cast && (!prior.active || active)))) return;
        if(!active && prior!=null && prior.cast!=cast) return;
        STATES.put(key,new DarkState(cast,level.getGameTime()+com.stardew.craft.port.PortJava.clamp(duration,0,101),active));trim(STATES,128);
        var mc=Minecraft.getInstance();
        if(mc.player!=null && mc.player.getId()==caster) {
            if(moon) {if(active) DarkSwordBloodMoonClientState.start(level.getGameTime(),duration);else DarkSwordBloodMoonClientState.clear();}
            else {if(active) DarkSwordBloodDebtClientState.start(level.getGameTime(),duration);else DarkSwordBloodDebtClientState.clear();}
        }
    }
    public static boolean active(int caster,boolean moon) {
        ensureLevel();DarkState state=STATES.get(new StateKey(caster,moon));
        return level!=null&&state!=null&&state.active&&level.getGameTime()<=state.end;
    }
    public static void start(WeaponSkillAnimPayload p) {
        var mc=Minecraft.getInstance();Vec3 at=new Vec3(p.originX(),p.originY()+1,p.originZ());
        if(mc.level==null||mc.player==null||mc.player.distanceToSqr(at)>48*48) return;
        if(FORGE_QUENCH.equals(p.skillId())||FORGE_BILLET.equals(p.skillId()))
            mc.level.playLocalSound(at.x,at.y,at.z,FORGE_BILLET.equals(p.skillId())?SoundEvents.FIRECHARGE_USE:SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS,.45f,FORGE_BILLET.equals(p.skillId())?.8f:.85f,false);
    }
    public static void effect(BloodForgeEffectPayload p) {
        ensureLevel(); if(level==null) return;
        EffectKey key=new EffectKey(p.casterId(),p.targetId(),p.castTick());
        if(p.phase()==BloodForgeEffectPayload.HEAT_END) {HEAT.remove(key);return;}
        var mc=Minecraft.getInstance();Vec3 at=new Vec3(p.x(),p.y(),p.z());
        if(mc.player==null||mc.player.distanceToSqr(at)>48*48||!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        if(p.phase()==BloodForgeEffectPayload.RECOVERY) {
            boolean sound=RECOVERY.keySet().stream().noneMatch(k->k.caster==p.casterId()&&k.cast==p.castTick());
            RECOVERY.putIfAbsent(key,new Effect(p,level.getGameTime()));trim(RECOVERY,32);
            if(sound) level.playLocalSound(at.x,at.y,at.z,SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.15f,1.6f,false);
        } else if(p.phase()==BloodForgeEffectPayload.HEAT_START && p.durationTicks()>0 && p.durationTicks()<=40) {
            HEAT.putIfAbsent(key,new Effect(p,level.getGameTime()));trim(HEAT,48);
        }
    }
    private static void trim(Map<?,?> map,int limit) {while(map.size()>limit) map.remove(map.keySet().iterator().next());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ensureLevel(); if(level==null||Minecraft.getInstance().isPaused()) return;
        long now=level.getGameTime();STATES.values().removeIf(s->now>s.end+20);
        HEAT.values().removeIf(e->now-e.start>=e.payload.durationTicks() || level.getEntity(e.payload.targetId())==null);
        RECOVERY.values().removeIf(e->now-e.start>=8);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ensureLevel(); if(level==null||!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()||(HEAT.isEmpty()&&RECOVERY.isEmpty())) return;
        var mc=Minecraft.getInstance();var stack=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        Vec3 camera=event.getCamera().getPosition();float partial=event.getPartialTick();
        double now=level.getGameTime()+partial;stack.pushPose();
        var out=buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        for(Effect e:RECOVERY.values()) if(level.getEntity(e.payload.casterId()) instanceof LivingEntity caster) {
            Vec3 from=new Vec3(e.payload.x(),e.payload.y(),e.payload.z());
            Vec3 to=caster.getPosition(partial).add(0,caster.getBbHeight()*.6,0);
            if(from.distanceToSqr(to)<=16*16 && to.distanceToSqr(camera)<48*48)
                BloodForgeGeometry.recovery(out,stack.last().pose(),from.subtract(camera),to.subtract(camera),(float)(now-e.start));
        }
        for(Effect e:HEAT.values()) if(level.getEntity(e.payload.targetId()) instanceof LivingEntity target && target.isAlive()) {
            if(target.distanceToSqr(camera)>48*48) continue;
            var box=target.getBoundingBox().move(target.getPosition(partial).subtract(target.position()));
            Vec3 center=box.getCenter(),normal=camera.subtract(center).normalize();
            Vec3 p=box.clip(camera,center).orElse(center).add(normal.scale(.06));
            Vec3 right=normal.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<1e-6) right=new Vec3(1,0,0);
            BloodForgeGeometry.heat(out,stack.last().pose(),p.subtract(camera),right,right.cross(normal).normalize(),(float)((now-e.start)/e.payload.durationTicks()));
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);stack.popPose();
    }
}
