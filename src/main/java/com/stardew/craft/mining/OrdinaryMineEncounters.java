package com.stardew.craft.mining;

import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.network.MineFogPacket;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** MineShaft ten-minute flying encounters and 30..40-second swarm fog. */
@EventBusSubscriber(modid="stardewcraft")
public final class OrdinaryMineEncounters {
    private record Swarm(MineFloorData generation,long endsAt) {}
    private static final Map<ServerLevel,Map<Integer,Swarm>> swarms=new WeakHashMap<>();
    private static final Map<ServerLevel,Long> lastTicks=new WeakHashMap<>();

    public static void performTenMinuteUpdate(MinecraftServer server) {
        var level=server.getLevel(ModMiningDimensions.STARDEW_MINING);if(level==null)return;
        var manager=MineFloorDataManager.get(level);
        boolean garlic=server.getPlayerList().getPlayers().stream().anyMatch(p->p.hasEffect(ModMobEffects.AVOID_MONSTERS));
        var current=swarms.computeIfAbsent(level,k->new HashMap<>());
        for(int floor:manager.floorNumbers()) {
            if(floor<=0 || floor==120)continue;
            var data=manager.getFloorData(floor);
            if(data==null || data.getGenerationVersion()!=OrdinaryMineRuntime.VERSION || (floor<=120 && floor%5==0) || data.isTreasureRoom() || current.containsKey(floor))continue;
            if(!MineMonsterSpawnHandler.isImplemented(flyingMonster(floor))
                    && !(floor>=171 && MineMonsterSpawnHandler.isImplemented("iridium_bat")))continue;
            if(level.random.nextDouble()>=.1 || (garlic && floor<=120))continue;
            if(floor>10 && level.random.nextDouble()<.11) {
                long end=StardewTimeManager.get().getSimulationGameTime()+(35+level.random.nextInt(11)-5)*20L;
                current.put(floor,new Swarm(data,end));
            } else spawnFlying(level,floor,data);
        }
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.dimension()!=ModMiningDimensions.STARDEW_MINING)return;
        long now=StardewTimeManager.get().getSimulationGameTime();
        Long previous=lastTicks.put(level,now);if(previous==null || previous==now)return;
        var current=swarms.computeIfAbsent(level,k->new HashMap<>());
        var manager=MineFloorDataManager.get(level);
        for(var iterator=current.entrySet().iterator();iterator.hasNext();) {
            var entry=iterator.next();var swarm=entry.getValue();int floor=entry.getKey();
            long remaining=swarm.endsAt()-now;
            if(remaining<=0 || manager.getFloorData(floor)!=swarm.generation() || swarm.generation().getGenerationVersion()!=OrdinaryMineRuntime.VERSION) { iterator.remove();continue; }
            // Original crosses a four-second boundary, stopping new spawns for its last five seconds.
            if(remaining>100 && (swarm.endsAt()-previous)/80 != remaining/80) spawnFlying(level,floor,swarm.generation());
        }
        if(now%20==0) for(var p:level.players()) {
            int floor=OrdinaryMineRuntime.floorAt(p.blockPosition());var swarm=current.get(floor);
            PacketDistributor.sendToPlayer(p,new MineFogPacket(floor,swarm==null?0:(int)Math.max(0,swarm.endsAt()-now)));
        }
    }

    public static String flyingMonster(int floor) {
        if(floor>120)return "serpent";
        if(floor<11 || floor==120)return "";
        if(floor<30)return "fly";
        if(floor<40)return floor>30?"bat":"";
        return floor<80?"frost_bat":"lava_bat";
    }
    private static void spawnFlying(ServerLevel level,int floor,MineFloorData data) {
        String kind=flyingMonster(floor);if(kind.isEmpty())return;
        if(floor>120)kind=floor>=171 && level.random.nextBoolean()?"iridium_bat":"serpent";
        if(!MineMonsterSpawnHandler.isImplemented(kind))return;
        var layout=OrdinaryMineLayout.load(level,floor);
        int x=0,z=0;
        switch(level.random.nextInt(4)) {
            case 0->x=level.random.nextInt(layout.width);
            case 1->{x=layout.width-1;z=level.random.nextInt(layout.depth);}
            case 2->{z=layout.depth-1;x=level.random.nextInt(layout.width);}
            case 3->z=level.random.nextInt(layout.depth);
        }
        // Preserve the source edge choice; physical fliers resolve a loaded interior air point at spawn.
        var p=layout.position(floor,x,z);
        var mob=MineMonsterSpawnHandler.spawnConfiguredMonster(level,kind,Vec3.atBottomCenterOf(p),0,com.stardew.craft.monster.MonsterSpawnContext.mine(level,floor,data),
                m->{m.setPersistenceRequired();m.addTag(OrdinaryMineRuntime.MOB_TAG);m.addTag("sd_focused_on_farmers");});
        if(mob!=null) {
            data.addGeneratedMonster(mob.getUUID());MineFloorDataManager.get(level).setFloorData(floor,data);
            if (mob instanceof com.stardew.craft.entity.monster.MineBatEntity bat) {
                bat.startPursuit();
                bat.playSound(com.stardew.craft.sound.ModSounds.BAT_SCREECH.get(), 1, 1);
            }
            if (mob instanceof com.stardew.craft.entity.monster.MineSerpentEntity) mob.playSound(com.stardew.craft.sound.ModSounds.SERPENT_DIE.get(), 1, 1);
            var target=level.getNearestPlayer(mob,64);if(target!=null)mob.setTarget(target);
        }
    }
}
