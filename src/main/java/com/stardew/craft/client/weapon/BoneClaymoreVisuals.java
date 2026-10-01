package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.BoneFractureTracePayload;
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
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;

@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class BoneClaymoreVisuals {
    private record Cast(int actor,String skill,long tick) {}
    private record Trace(UUID target,long serverEnd,long localEnd) {}
    private static final Map<Cast,Long> CASTS=new LinkedHashMap<>();
    private static final Map<Integer,Trace> TRACES=new LinkedHashMap<>();
    private static ClientLevel level;
    private BoneClaymoreVisuals() {}
    private static void ensureLevel() {
        var next=Minecraft.getInstance().level;
        if(next!=level) {level=next; CASTS.clear(); TRACES.clear();}
    }
    public static void start(WeaponSkillAnimPayload p) {
        ensureLevel(); var mc=Minecraft.getInstance();
        Vec3 origin=new Vec3(p.originX(),p.originY()+.8,p.originZ());
        if(level==null || mc.player==null || mc.player.distanceToSqr(origin)>48*48) return;
        if(CASTS.putIfAbsent(new Cast(p.casterEntityId(),p.skillId(),p.startGameTick()),level.getGameTime())!=null) return;
        while(CASTS.size()>64) CASTS.remove(CASTS.keySet().iterator().next());
        level.playLocalSound(origin.x,origin.y,origin.z,SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,
                BONE_FRACTURE.equals(p.skillId())?.35f:.48f, BONE_FRACTURE.equals(p.skillId())?1.05f:CLAYMORE_RETURN.equals(p.skillId())?.83f:.68f,false);
    }
    public static void trace(BoneFractureTracePayload p) {
        ensureLevel(); var mc=Minecraft.getInstance();
        if(level==null || mc.player==null || !(level.getEntity(p.target()) instanceof LivingEntity target)
                || !target.isAlive() || target.distanceToSqr(mc.player)>32*32 || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        if(p.remaining()<=0) {TRACES.remove(p.target());return;}
        Trace previous=TRACES.get(p.target());
        if(previous!=null && previous.target.equals(target.getUUID()) && p.endTick()<=previous.serverEnd) return;
        TRACES.put(p.target(),new Trace(target.getUUID(),p.endTick(),level.getGameTime()+Math.min(80,p.remaining())));
        while(TRACES.size()>48) TRACES.remove(TRACES.keySet().iterator().next());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ensureLevel(); if(level==null || Minecraft.getInstance().isPaused()) return;
        long now=level.getGameTime();
        CASTS.values().removeIf(start -> now-start>30);
        TRACES.entrySet().removeIf(e -> now>=e.getValue().localEnd || !(level.getEntity(e.getKey()) instanceof LivingEntity target)
                || !target.isAlive() || !target.getUUID().equals(e.getValue().target));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ensureLevel(); var mc=Minecraft.getInstance();
        if(level==null || TRACES.isEmpty() || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        float partial=event.getPartialTick();
        Vec3 camera=event.getCamera().getPosition(); var buffers=mc.renderBuffers().bufferSource();
        var out=buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW); var pose=event.getPoseStack().last().pose();
        for(var entry:TRACES.entrySet()) {
            if(!(level.getEntity(entry.getKey()) instanceof LivingEntity target) || !target.isAlive()
                    || !target.getUUID().equals(entry.getValue().target) || target.distanceToSqr(camera)>32*32) continue;
            var box=target.getBoundingBox().move(target.getPosition(partial).subtract(target.position()));
            Vec3 center=box.getCenter(), surface=box.clip(camera,center).orElse(center);
            Vec3 normal=camera.subtract(surface).normalize(), right=normal.cross(new Vec3(0,1,0)).normalize();
            if(right.lengthSqr()<1e-6) right=new Vec3(1,0,0);
            Vec3 up=right.cross(normal).normalize();
            BoneClaymoreGeometry.fractureTrace(out,pose,surface.add(normal.scale(.065)).subtract(camera),right,up,normal,
                    BoneClaymoreGeometry.traceOpacity(entry.getValue().localEnd-level.getGameTime()-partial));
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);
    }
}
