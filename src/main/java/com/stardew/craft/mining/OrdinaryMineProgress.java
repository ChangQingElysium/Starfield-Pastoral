package com.stardew.craft.mining;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** MineShaft.permanentMineChanges: platform caches survive floor regeneration. */
public final class OrdinaryMineProgress extends SavedData {
    private final Map<Integer,Integer> platforms = new HashMap<>();
    private final Set<String> caches = new HashSet<>();
    private int year;
    private record DisconnectedFloor(int floor,int day) {}
    private final Map<UUID,DisconnectedFloor> disconnected=new HashMap<>();
    public void disconnect(UUID player,int floor,int day) { disconnected.put(player,new DisconnectedFloor(floor,day));setDirty(); }
    public void reconnect(UUID player) { if(disconnected.remove(player)!=null)setDirty(); }
    public int deepestDisconnected(int day) {
        if(disconnected.values().removeIf(v->v.day()!=day))setDirty();
        return disconnected.values().stream().mapToInt(DisconnectedFloor::floor).max().orElse(0);
    }
    public static OrdinaryMineProgress get(ServerLevel level) {
        var data = level.getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(OrdinaryMineProgress::new, OrdinaryMineProgress::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(OrdinaryMineProgress::new, OrdinaryMineProgress::load)), "stardew_ordinary_mine_progress");
        int current = com.stardew.craft.time.StardewTimeManager.get().getCurrentYear();
        if (data.year != current) {
            data.platforms.keySet().removeIf(f -> f % 5 != 0);
            for (int f : new int[]{5,45,85}) if (data.platforms.containsKey(f)) data.platforms.put(f,6);
            data.caches.removeIf(k -> Integer.parseInt(k.substring(0,k.indexOf(':'))) % 5 != 0);
            data.year = current; data.setDirty();
        }
        return data;
    }
    public int platformLimit(int floor) { return platforms.getOrDefault(floor,-1); }
    public void initializePlatforms(int floor, int count) { if (!platforms.containsKey(floor)) { platforms.put(floor,count); setDirty(); } }
    public void breakPlatform(int floor) { platforms.computeIfPresent(floor,(f,n)->Math.max(0,n-1)); setDirty(); }
    public boolean cacheEmpty(String key) { return caches.contains(key); }
    public void emptyCache(String key) { caches.add(key); setDirty(); }
    public static OrdinaryMineProgress load(CompoundTag tag, HolderLookup.Provider provider) {
        var d = new OrdinaryMineProgress(); d.year=tag.getInt("year");
        var p=tag.getCompound("platforms"); for(String f:p.getAllKeys()) d.platforms.put(Integer.parseInt(f),p.getInt(f));
        var c=tag.getCompound("caches"); d.caches.addAll(c.getAllKeys());
        var leases=tag.getCompound("disconnected");for(String id:leases.getAllKeys()) {
            var lease=leases.getCompound(id);d.disconnected.put(UUID.fromString(id),new DisconnectedFloor(lease.getInt("floor"),lease.getInt("day")));
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("year",year); var p=new CompoundTag(); platforms.forEach((f,n)->p.putInt(f.toString(),n)); tag.put("platforms",p);
        var c=new CompoundTag(); caches.forEach(k->c.putBoolean(k,true)); tag.put("caches",c);
        var leases=new CompoundTag();disconnected.forEach((id,v)->{var lease=new CompoundTag();lease.putInt("floor",v.floor());lease.putInt("day",v.day());leases.put(id.toString(),lease);});tag.put("disconnected",leases);
        return tag;
    }
}
