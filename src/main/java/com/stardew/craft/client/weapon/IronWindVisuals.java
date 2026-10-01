package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.IronWindMovePayload;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
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
public final class IronWindVisuals {
    private record Cast(int actor,String skill,long tick) {}
    private record MoveKey(int actor,long tick,IronWindMovePayload.Mode mode) {}
    private record Blink(Vec3 point,Vec3 direction,long start,boolean wind) {}
    private record Wake(Vec3 from,Vec3 to,long start) {}
    private record Dash(UUID actor,long start,Vec3 previous) {}
    private static final Map<Cast,Long> CASTS=new LinkedHashMap<>();
    private static final Map<MoveKey,Long> MOVES=new LinkedHashMap<>();
    private static final Map<Integer,Dash> DASHES=new HashMap<>();
    private static final List<Blink> BLINKS=new ArrayList<>();
    private static final List<Wake> WAKES=new ArrayList<>();
    private static ClientLevel level;
    private IronWindVisuals() {}
    public static void ensureLevel() {
        var next=Minecraft.getInstance().level;
        if(next!=level) {level=next;CASTS.clear();MOVES.clear();DASHES.clear();BLINKS.clear();WAKES.clear();WindSpireClientState.clear();}
    }
    public static void start(WeaponSkillAnimPayload p) {
        ensureLevel();var mc=Minecraft.getInstance();Vec3 point=new Vec3(p.originX(),p.originY()+.8,p.originZ());
        if(level==null || mc.player==null || mc.player.distanceToSqr(point)>48*48) return;
        if(CASTS.putIfAbsent(new Cast(p.casterEntityId(),p.skillId(),p.startGameTick()),level.getGameTime())!=null) return;
        while(CASTS.size()>64) CASTS.remove(CASTS.keySet().iterator().next());
        level.playLocalSound(point.x,point.y,point.z,SoundEvents.TRIDENT_THROW.value(),SoundSource.PLAYERS,.3f,WIND_THRUST.equals(p.skillId())?1.7f:1.35f,false);
    }
    static boolean validMove(IronWindMovePayload p) {
        return finite(p.from()) && finite(p.to()) && p.from().distanceToSqr(p.to())<=144;
    }
    private static boolean finite(Vec3 p) {return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    public static void move(IronWindMovePayload p) {
        ensureLevel();var mc=Minecraft.getInstance();
        if(level==null || mc.player==null || !validMove(p)
                || Math.min(mc.player.distanceToSqr(p.from()),mc.player.distanceToSqr(p.to()))>48*48) return;
        long now=level.getGameTime();
        if(MOVES.putIfAbsent(new MoveKey(p.caster(),p.tick(),p.mode()),now)!=null) return;
        while(MOVES.size()>64) MOVES.remove(MOVES.keySet().iterator().next());
        if(p.mode()==IronWindMovePayload.Mode.WIND_DASH) {
            if(level.getEntity(p.caster()) instanceof Player player) DASHES.put(p.caster(),new Dash(player.getUUID(),now,p.from().add(0,.65,0)));
        } else {
            boolean wind=p.mode()==IronWindMovePayload.Mode.WIND_BLINK;
            Vec3 direction=p.to().subtract(p.from());
            BLINKS.add(new Blink(p.from().add(0,.65,0),direction,now,wind));
            BLINKS.add(new Blink(p.to().add(0,.65,0),direction,now,wind));
            while(BLINKS.size()>64) BLINKS.removeFirst();
        }
    }
    /** Suppress only this wind dash's old generic particle/sound layer, not its movement. */
    public static boolean ownsWindDash(Player player) {
        ensureLevel();Dash dash=DASHES.get(player.getId());
        return level!=null && dash!=null && dash.actor.equals(player.getUUID()) && level.getGameTime()-dash.start<=5;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ensureLevel();var mc=Minecraft.getInstance();if(level==null || mc.player==null || mc.isPaused()) return;
        long now=level.getGameTime();CASTS.values().removeIf(start->now-start>30);MOVES.values().removeIf(start->now-start>30);
        BLINKS.removeIf(b->now-b.start>=6);WAKES.removeIf(w->now-w.start>=6);
        var iterator=DASHES.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();Dash dash=entry.getValue();
            if(!(level.getEntity(entry.getKey()) instanceof Player player) || !player.isAlive() || !player.getUUID().equals(dash.actor)
                    || now-dash.start>5 || player.distanceToSqr(mc.player)>32*32
                    || !(player.getMainHandItem().getItem() instanceof IStardewWeapon weapon) || !"wind_spire".equals(weapon.getWeaponId())
                    || (player==mc.player && !DashMovementClientState.isActive(player))) {iterator.remove();continue;}
            Vec3 current=player.position().add(0,.65,0);
            if(Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get() && IronWindGeometry.validSegment(dash.previous,current)
                    && level.clip(new ClipContext(dash.previous,current,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS) {
                WAKES.add(new Wake(dash.previous,current,now));while(WAKES.size()>96) WAKES.removeFirst();
            }
            entry.setValue(new Dash(dash.actor,dash.start,current));
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ensureLevel();var mc=Minecraft.getInstance();if(level==null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        float partial=event.getPartialTick();Vec3 camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);var pose=event.getPoseStack().last().pose();
        for(Blink b:BLINKS) if(b.point.distanceToSqr(camera)<=48*48) IronWindGeometry.blink(out,pose,b.point.subtract(camera),b.direction,fade(level.getGameTime()-b.start+partial),b.wind);
        for(Wake w:WAKES) if(w.from.distanceToSqr(camera)<=32*32) IronWindGeometry.wake(out,pose,w.from.subtract(camera),w.to.subtract(camera),fade(level.getGameTime()-w.start+partial));
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);
    }
    static float fade(float age) {return age<0 || age>=6?0:(float)Math.pow(1-age/6,2);}
}
