package com.stardew.craft.fishing.server;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishing.FishingPresentationPhase;
import com.stardew.craft.fishing.network.FishingMotionInputPayload;
import com.stardew.craft.fishing.network.FishingPresentationPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Observer synchronization and water-constrained cosmetic movement; no gameplay RNG or hook relocation. */
@EventBusSubscriber(modid=StardewCraft.MODID)
public final class FishingPresentationEvents {
    private static final Map<MinecraftServer,Map<UUID,State>> SERVERS=new WeakHashMap<>();
    private static final class State {
        FishingPresentationPayload value;Vec3 origin,velocity=Vec3.ZERO;long lastInput=-10;
        State(FishingPresentationPayload value){this.value=value;origin=value.bobber();}
    }
    private static Map<UUID,State> states(ServerPlayer p){return SERVERS.computeIfAbsent(p.server,k->new HashMap<>());}
    public static void phase(ServerPlayer player,UUID id,FishingPresentationPhase phase,int hook,Vec3 origin,ItemStack stack,boolean fish) {
        if(phase==FishingPresentationPhase.CHARGE)BobberStyleService.beginCast(player,id);
        if(phase==FishingPresentationPhase.STOP){states(player).remove(player.getUUID());}
        var packet=new FishingPresentationPayload(player.getUUID(),id,phase,player.level().getGameTime(),hook,origin,stack.copy(),fish,.927f,.3f,0,false,false);
        if(phase!=FishingPresentationPhase.STOP)states(player).put(player.getUUID(),new State(packet));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,packet);
    }
    public static void phase(ServerPlayer p,UUID id,FishingPresentationPhase phase,int hook){
        var entity=p.serverLevel().getEntity(hook);phase(p,id,phase,hook,entity==null?p.position():entity.position(),ItemStack.EMPTY,false);
    }
    public static void launched(ServerPlayer player, net.minecraft.world.entity.projectile.FishingHook hook) {
        var s=states(player).get(player.getUUID());if(s==null)return;var v=s.value;
        s.value=new FishingPresentationPayload(v.actor(),v.session(),v.phase(),v.started(),hook.getId(),hook.position(),v.stack(),v.fish(),v.fishPosition(),v.progress(),v.velocity(),v.held(),v.controlled());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,s.value);
    }
    public static Vec3 position(ServerPlayer player,Vec3 fallback){var s=states(player).get(player.getUUID());return s!=null&&s.value.phase()==FishingPresentationPhase.MINIGAME?s.value.bobber():fallback;}
    public static void input(ServerPlayer player,FishingMotionInputPayload p) {
        var manager=FishingSessionManager.get(player.server);var s=states(player).get(player.getUUID());long tick=player.level().getGameTime();
        if(s==null||!manager.hasValidUse(player)||!manager.useId(player).equals(p.session())||manager.getState(player)!=FishingSession.State.MINIGAME||tick-s.lastInput<2)return;
        if(!Float.isFinite(p.position())||!Float.isFinite(p.progress())||!Float.isFinite(p.velocity()))return;
        s.lastInput=tick;var v=s.value;s.value=new FishingPresentationPayload(v.actor(),v.session(),v.phase(),v.started(),v.hook(),v.bobber(),v.stack(),v.fish(),Mth.clamp(p.position(),0,1),Mth.clamp(p.progress(),0,1),Mth.clamp(p.velocity(),-40,40),p.held(),p.controlled());
    }
    public static void tick(ServerPlayer player) {
        var s=states(player).get(player.getUUID());if(s==null)return;var v=s.value;
        Vec3 next=v.bobber();var hook=player.serverLevel().getEntity(v.hook());
        if(v.phase()==FishingPresentationPhase.MINIGAME) {
            Vec3 radial=s.origin.subtract(player.position()).multiply(1,0,1);double distance=radial.length();
            if(distance>1e-4) {
                Vec3 direction=radial.scale(1/distance);double span=Math.min(2.5,distance*.22);
                double offset=span*(.5-v.fishPosition())-Math.min(1,distance*.12)*v.progress();
                offset=Math.max(1.5-distance,offset);Vec3 target=s.origin.add(direction.scale(offset));
                s.velocity=s.velocity.scale(.62).add(target.subtract(next).scale(.12));double speed=s.velocity.length();if(speed>.14)s.velocity=s.velocity.scale(.14/speed);
                Vec3 proposed=next.add(s.velocity);
                // Test the swept path too; never jump across a thin bank into another pool.
                boolean water=true;for(int i=1;i<=4;i++)if(!water(player,next.lerp(proposed,i/4.0))){water=false;break;}
                if(water)next=proposed;else s.velocity=Vec3.ZERO;
            }
        } else if(hook!=null && (v.phase()==FishingPresentationPhase.CAST||v.phase()==FishingPresentationPhase.BITE||v.phase()==FishingPresentationPhase.HOOK))next=hook.position();
        s.value=new FishingPresentationPayload(v.actor(),v.session(),v.phase(),v.started(),v.hook(),next,v.stack(),v.fish(),v.fishPosition(),v.progress(),v.velocity(),v.held(),v.controlled());
        if(player.level().getGameTime()%2==0 && (hook!=null||v.phase()==FishingPresentationPhase.MINIGAME))PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,s.value);
    }
    private static boolean water(ServerPlayer p,Vec3 position){return p.serverLevel().getFluidState(BlockPos.containing(position.add(0,-.08,0))).is(FluidTags.WATER);}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event){if(event.getEntity() instanceof ServerPlayer observer&&event.getTarget() instanceof ServerPlayer actor){var s=states(actor).get(actor.getUUID());if(s!=null)PacketDistributor.sendToPlayer(observer,s.value);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)states(p).remove(p.getUUID());}
}
