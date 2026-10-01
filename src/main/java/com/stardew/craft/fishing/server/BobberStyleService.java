package com.stardew.craft.fishing.server;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.fishing.BobberStyles;
import com.stardew.craft.fishing.network.*;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@EventBusSubscriber(modid=StardewCraft.MODID)
public final class BobberStyleService {
    private record Open(UUID token,ResourceKey<Level> dimension,BlockPos pos,long expires) {}
    private static final class State {
        final Map<UUID,Open> menus=new HashMap<>();
        final Map<UUID,Integer> styles=new HashMap<>();
        final Map<UUID,UUID> casts=new HashMap<>();
        final RandomSource random=RandomSource.create(); // Cosmetic selection must not consume fishing RNG.
    }
    private static final Map<MinecraftServer,State> SERVERS=new WeakHashMap<>();
    private static State state(ServerPlayer p){return SERVERS.computeIfAbsent(p.server,k->new State());}
    public static void open(ServerPlayer p,BlockPos pos){
        if(!p.isAlive()||p.isSpectator()||p.distanceToSqr(pos.getCenter())>64||!p.serverLevel().hasChunkAt(pos)||!p.serverLevel().getBlockState(pos).is(ModBlocks.BOBBER_STYLE_MACHINE.get()))return;
        FishingSessionManager.get(p.server).cancel(p);
        var menu=new Open(UUID.randomUUID(),p.level().dimension(),pos.immutable(),p.level().getGameTime()+6000);
        state(p).menus.put(p.getUUID(),menu);snapshot(p,menu,true);
    }
    private static void snapshot(ServerPlayer p,Open menu,boolean open){
        var data=PlayerStardewDataAPI.getData(p);
        int selected=BobberStyles.canSelect(data.getBobberStyle(),data.getDistinctFishCaughtCount())?data.getBobberStyle():0;
        PacketDistributor.sendToPlayer(p,new BobberMenuPayload(menu.token,selected,data.getDistinctFishCaughtCount(),open));
    }
    public static void select(ServerPlayer p,BobberSelectPayload packet){
        var s=state(p);var menu=s.menus.get(p.getUUID());
        if(menu==null||!menu.token.equals(packet.token()))return;
        if(packet.selected()==-1){s.menus.remove(p.getUUID());return;}
        if(!p.isAlive()||p.isSpectator()||p.level().dimension()!=menu.dimension||p.level().getGameTime()>menu.expires||p.distanceToSqr(menu.pos.getCenter())>64||!p.serverLevel().hasChunkAt(menu.pos)||!p.serverLevel().getBlockState(menu.pos).is(ModBlocks.BOBBER_STYLE_MACHINE.get())){s.menus.remove(p.getUUID());return;}
        var data=PlayerStardewDataAPI.getData(p);
        if(BobberStyles.canSelect(packet.selected(),data.getDistinctFishCaughtCount())){
            data.setBobberStyle(packet.selected());
            // Choosing random does not re-roll repeatedly while inspecting the menu.
            if(packet.selected()!=BobberStyles.RANDOM)s.styles.put(p.getUUID(),packet.selected());
            broadcast(p);
        }
        snapshot(p,menu,false);
    }
    public static void beginCast(ServerPlayer p,UUID use){
        if(use.equals(state(p).casts.put(p.getUUID(),use)))return;
        var data=PlayerStardewDataAPI.getData(p);
        state(p).styles.put(p.getUUID(),BobberStyles.resolve(data.getBobberStyle(),data.getDistinctFishCaughtCount(),state(p).random));
        broadcast(p);
    }
    private static BobberStyleStatePayload display(ServerPlayer p){
        var data=PlayerStardewDataAPI.getData(p);
        int current=state(p).styles.getOrDefault(p.getUUID(),data.getBobberStyle()==BobberStyles.RANDOM?0:data.getBobberStyle());
        return new BobberStyleStatePayload(p.getUUID(),BobberStyles.canSelect(current,data.getDistinctFishCaughtCount())?current:0);
    }
    private static void broadcast(ServerPlayer p){PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,display(p));}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)broadcast(p);}
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){state(p).menus.remove(p.getUUID());broadcast(p);}}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p){state(p).menus.remove(p.getUUID());broadcast(p);}}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){if(e.getEntity() instanceof ServerPlayer observer&&e.getTarget() instanceof ServerPlayer actor)PacketDistributor.sendToPlayer(observer,display(actor));}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){state(p).menus.remove(p.getUUID());state(p).styles.remove(p.getUUID());state(p).casts.remove(p.getUUID());}}
}
