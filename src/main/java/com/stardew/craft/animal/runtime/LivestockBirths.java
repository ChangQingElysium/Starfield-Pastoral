package com.stardew.craft.animal.runtime;

import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.util.StardewDeterministicRandom;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** One personal-event draw per farm/day. Pending newborns reserve a real bed until named. */
public final class LivestockBirths {
    private static final Set<UUID> prompted = new HashSet<>();
    private LivestockBirths() {}
    public static void clear() { prompted.clear(); }
    public static void onNewDay(MinecraftServer server) {
        var data=LivestockWorldData.get(server);var buildings=BuildingWorldData.get(server);int day=StardewTimeManager.get().getAbsoluteDay();
        for(var farm:FarmInstanceRegistry.get(server).getAllFarms()) {
            if(data.birthDay(farm.getInstanceId())>=day) continue;
            // A newly created ledger starts observing nights now; it does not invent years of past births.
            int previous=data.birthDay(farm.getInstanceId());data.birthDay(farm.getInstanceId(),day);
            if(previous<0 || data.all().stream().anyMatch(a->a.farm().equals(farm.getInstanceId()) && data.newborn(a.id()))) continue;
            var random=StardewDeterministicRandom.create(farm.getInstanceId().getLeastSignificantBits(),day,0);
            if(random.nextDouble()>=.5) continue; // Utility.pickPersonalFarmEvent -> QuestionEvent(2).
            for(var home:buildings.all()) {
                if(!home.farmId().equals(farm.getInstanceId()) || !LivestockHomes.allowsPregnancy(home)
                        || !LivestockHomes.accepts(home) || buildings.transfer(home.id())!=null || data.occupancy(home.id())>=LivestockHomes.capacity(LivestockService.level(server,home),home)) continue;
                var residents=data.all().stream().filter(a->a.home().equals(home.id())).toList();
                if(residents.isEmpty() || random.nextDouble()>=residents.size()*.0055) continue;
                var parent=residents.get(Math.min(residents.size()-1,(int)(random.nextDouble()*residents.size())));
                // Source chooses among all residents before checking adulthood and reproduction permission.
                if(!parent.baby() && parent.reproduction() && parent.species().known() && parent.species().pregnancy() && com.stardew.craft.api.v1.agriculture.StardewAnimalReproductionRules.allows(new com.stardew.craft.api.v1.agriculture.StardewAnimalReproductionContext(LivestockService.level(server,home),home,parent,day))) {
                    var baby=new LivestockRecord(UUID.randomUUID(),parent.owner(),parent.farm(),home.id(),"",data.allocateRandomId(),day,LivestockCare.purchased()).species(parent.species());
                    data.put(baby);data.newborn(baby.id(),true);
                }
                break;
            }
        }
    }
    public static void prompt(ServerPlayer player, UUID id) {
        var data=LivestockWorldData.get(player.serverLevel().getServer()); var animal=data.find(id);
        var farm=FarmInstanceRegistry.get(player.serverLevel().getServer()).getFarmForPlayer(player.getUUID());
        if(animal==null || farm==null || !animal.farm().equals(farm.getInstanceId()) || !data.newborn(id)) return;
        var tag=new CompoundTag();tag.putString("Kind","birth");tag.putUUID("Id",id);LivestockUiData.describe(tag,animal.species());var home=BuildingWorldData.get(player.server).find(animal.home());if(home!=null)tag.putString("BuildingName",home.title().getString());PacketDistributor.sendToPlayer(player,new LivestockShopPayload(tag));
    }
    public static void promptOnline(MinecraftServer server) {
        var data=LivestockWorldData.get(server);
        for(var animal:data.all()) if(data.newborn(animal.id()) && !prompted.contains(animal.id())) {
            var player=server.getPlayerList().getPlayer(animal.owner());
            if(player!=null) {prompt(player,animal.id());prompted.add(animal.id());}
        }
    }
    public static String name(ServerPlayer player, UUID id, String name) {
        var data=LivestockWorldData.get(player.serverLevel().getServer());var animal=data.find(id);
        var farm=FarmInstanceRegistry.get(player.serverLevel().getServer()).getFarmForPlayer(player.getUUID());
        if(animal==null || farm==null || !animal.farm().equals(farm.getInstanceId()) || !data.newborn(id)) return "permission";
        name=name.strip();if(name.isEmpty() || name.length()>32 || name.codePoints().anyMatch(c->Character.isISOControl(c)||c==0xA7))return "name_required";if(data.nameTaken(animal.farm(),name,id))return "name_taken";
        data.put(animal.rename(name));data.newborn(id,false);prompted.remove(id);LivestockService.project(player.serverLevel().getServer());return "";
    }
}
