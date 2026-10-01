package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
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
public final class CrystalVenomVisuals {
    private record CastKey(int caster,long tick) {}
    private record Cast(WeaponSkillAnimPayload payload,long start) {}
    private static final Map<CastKey,Cast> CASTS=new LinkedHashMap<>();
    private static ClientLevel level;
    private static long burstTick=Long.MIN_VALUE;
    private CrystalVenomVisuals() {}
    public static void ensureLevel() {
        ClientLevel next=Minecraft.getInstance().level;
        if(next!=level) {level=next;CASTS.clear();burstTick=Long.MIN_VALUE;CrystalDaggerLayerClientState.clear();WickedKrisPoisonClientState.clearAll();}
    }
    public static void crystalBurst() {ensureLevel();if(level!=null) burstTick=level.getGameTime();}
    public static float burstAge(float partial) {return level==null||burstTick==Long.MIN_VALUE?-1:(float)(level.getGameTime()-burstTick)+partial;}
    public static void start(WeaponSkillAnimPayload p) {
        ensureLevel();var mc=Minecraft.getInstance();
        if(level==null||mc.player==null) return;
        Vec3 origin=new Vec3(p.originX(),p.originY(),p.originZ());
        if(mc.player.distanceToSqr(origin)>48*48) return;
        CastKey key=new CastKey(p.casterEntityId(),p.startGameTick());
        if(CASTS.containsKey(key)) return;
        CASTS.put(key,new Cast(p,level.getGameTime()));while(CASTS.size()>32) CASTS.remove(CASTS.keySet().iterator().next());
        level.playLocalSound(origin.x,origin.y+1,origin.z,CRYSTAL_LAYER.equals(p.skillId())?SoundEvents.TRIDENT_THROW:SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS,.3f,CRYSTAL_LAYER.equals(p.skillId())?1.6f:1.15f,false);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        ensureLevel();if(level==null||Minecraft.getInstance().isPaused()) return;
        CASTS.values().removeIf(c->level.getGameTime()-c.start>8||!(level.getEntity(c.payload.casterEntityId()) instanceof LivingEntity actor)||!actor.isAlive());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ensureLevel();var mc=Minecraft.getInstance();if(level==null||mc.player==null||!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        Vec3 camera=e.getCamera().getPosition();float partial=e.getPartialTick();
        double now=level.getGameTime()+partial;var stack=e.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        var out=buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);stack.pushPose();
        for(Cast cast:CASTS.values()) {
            var p=cast.payload;float age=(float)(now-cast.start);
            if(!(level.getEntity(p.casterEntityId()) instanceof LivingEntity actor)||actor.distanceToSqr(camera)>48*48) continue;
            if(CRYSTAL_LAYER.equals(p.skillId()) && age<6) {
                Vec3 from=new Vec3(p.originX(),p.originY()+.12,p.originZ()),to=actor.getPosition(partial).add(0,.12,0);
                if(from.distanceToSqr(to)>.04&&from.distanceToSqr(to)<36)
                    WeaponContactGeometry.blade(out,stack.last().pose(),from.subtract(camera),to.subtract(camera),
                            new Vec3(0,1,0),.045,(1-age/6)*.5f,false,105,201,255);
            }
            if(VENOM_RIPPLE.equals(p.skillId())) {
                double yaw=Math.toRadians(actor.getYRot());
                CrystalVenomGeometry.ripple(out,stack.last().pose(),actor.getPosition(partial).add(0,.65,0).subtract(camera),new Vec3(-Math.sin(yaw),0,Math.cos(yaw)),age);
            }
        }
        int rendered=0;
        if(WickedKrisPoisonClientState.trackedTargetCount()>0) for(var entity:level.entitiesForRendering()) {
            if(!(entity instanceof LivingEntity target)||!target.isAlive()||target.distanceToSqr(camera)>32*32) continue;
            var status=WickedKrisPoisonClientState.visualStatus(target.getUUID(),level.getGameTime());
            if(status==null) continue;
            var box=target.getBoundingBox().move(target.getPosition(partial).subtract(target.position()));
            Vec3 center=box.getCenter(),normal=camera.subtract(center).normalize();
            Vec3 point=box.clip(camera,center).orElse(center).add(normal.scale(.065)).subtract(camera);
            Vec3 right=normal.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<1e-6) right=new Vec3(1,0,0);
            float fuse=status.detonateTotalTicks()>0&&status.detonateEndTick()>=now
                    ?(float)com.stardew.craft.port.PortJava.clamp(1-(status.detonateEndTick()-now)/status.detonateTotalTicks(),0,1):-1;
            float visibility=(float)com.stardew.craft.port.PortJava.clamp((status.poisonEndTick()-now)/8,0,1)*.8f;
            CrystalVenomGeometry.poisonMark(out,stack.last().pose(),point,right,right.cross(normal).normalize(),normal,status.stacks(),fuse,visibility);
            if(++rendered>=32) break;
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);stack.popPose();
    }
}
