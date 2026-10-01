package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.TemperedRingPayload;
import com.stardew.craft.combat.skill.WeaponGroundContact;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** The heated front follows sampled collision surfaces and never expands beyond the damage radius. */
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class TemperedRingClient {
    private static final int SEGMENTS=32, SLICES=10;
    private record Ring(TemperedRingPayload payload,long start,Vec3[][] floor) {}
    private static final Map<Long,Ring> RINGS=new LinkedHashMap<>();
    private static ClientLevel level;
    private TemperedRingClient() {}
    private static void ensureLevel() {
        ClientLevel next=Minecraft.getInstance().level;
        if(level!=next) {level=next;RINGS.clear();}
    }
    public static void receive(TemperedRingPayload p) {
        ensureLevel(); if(!p.active()) {RINGS.remove(p.id());return;}
        var player=Minecraft.getInstance().player;
        if(level==null||player==null||!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()
                ||p.duration()<=0||p.duration()>40||!Float.isFinite(p.radius())||p.radius()<=0||p.radius()>4
                ||!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z())
                ||player.distanceToSqr(p.x(),p.y(),p.z())>48*48||RINGS.containsKey(p.id())) return;
        Vec3[][] floor=new Vec3[SLICES+1][SEGMENTS];
        for(int r=0;r<=SLICES;r++) for(int a=0;a<SEGMENTS;a++) {
            double radius=Math.max(.25,p.radius()*r/SLICES),angle=a*Math.PI*2/SEGMENTS;
            var hit=WeaponGroundContact.find(level,player,new Vec3(p.x()+Math.cos(angle)*radius,p.y(),p.z()+Math.sin(angle)*radius));
            if(hit!=null) floor[r][a]=hit.getLocation().add(0,.025,0);
        }
        RINGS.put(p.id(),new Ring(p,level.getGameTime(),floor));
        while(RINGS.size()>32) RINGS.remove(RINGS.keySet().iterator().next());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        ensureLevel();if(level==null||Minecraft.getInstance().isPaused()) return;
        RINGS.values().removeIf(r->level.getGameTime()-r.start>r.payload.duration()+6);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ensureLevel();if(level==null||RINGS.isEmpty()||!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        var mc=Minecraft.getInstance();var stack=e.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        Vec3 camera=e.getCamera().getPosition();
        double now=level.getGameTime()+e.getPartialTick();
        stack.pushPose();
        var out=buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        for(Ring ring:RINGS.values()) {
            var p=ring.payload;if(camera.distanceToSqr(p.x(),p.y(),p.z())>48*48) continue;
            double age=now-ring.start,slice=com.stardew.craft.port.PortJava.clamp(age/p.duration(),0,1)*SLICES;
            int lower=Math.min(SLICES-1,(int)slice);double t=slice-lower;
            float fade=(float)com.stardew.craft.port.PortJava.clamp(1-Math.max(0,age-p.duration())/6,0,1);
            Vec3[] points=new Vec3[SEGMENTS];
            for(int a=0;a<SEGMENTS;a++) {
                Vec3 before=ring.floor[lower][a],after=ring.floor[lower+1][a];
                if(before!=null&&after!=null&&Math.abs(before.y-after.y)<.2) points[a]=before.lerp(after,t).subtract(camera);
            }
            BloodForgeGeometry.fireFront(out,stack.last().pose(),points,fade);
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);stack.popPose();
    }
}
