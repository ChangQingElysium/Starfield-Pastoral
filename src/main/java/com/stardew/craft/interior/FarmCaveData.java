package com.stardew.craft.interior;

import com.stardew.craft.farm.FarmInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Stable farm identity, independent of owner transfer and legacy player interior slots. */
public final class FarmCaveData extends SavedData {
    public static final class Entry {
        final UUID farmId;
        final BlockPos origin;
        final BlockPos legacy;
        int version;
        int lastDay=-1;
        String appliedChoice="";
        boolean dehydratorGranted;
        Entry(UUID id, BlockPos origin, BlockPos legacy) { this.farmId=id;this.origin=origin;this.legacy=legacy; }
        public BlockPos origin() { return origin; }
        public boolean installed() { return version==FarmCaveLayout.VERSION; }
    }
    private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    private final Map<BlockPos,Entry> byOrigin=new HashMap<>();
    private int nextSlot;
    public Entry atColumn(BlockPos pos) {
        int x=pos.getX()-FarmCaveLayout.BASE.getX(),z=pos.getZ()-FarmCaveLayout.BASE.getZ();
        if(x<0 || x>=16 || z<0 || z%32>=18)return null;
        return byOrigin.get(FarmCaveLayout.BASE.offset(0,0,(z/32)*32));
    }
    public static FarmCaveData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new Factory<>(FarmCaveData::new,FarmCaveData::load)), com.stardew.craft.port.PortSavedData.constructor(new Factory<>(FarmCaveData::new,FarmCaveData::load)),"stardew_farm_caves");
    }
    public Entry find(UUID id) { return entries.get(id); }
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(entries.values()); }
    public BlockPos remove(UUID id) {
        Entry removed=entries.remove(id);if(removed==null)return null;
        byOrigin.remove(removed.origin);setDirty();return removed.origin;
    }
    public Entry allocate(ServerLevel level,FarmInstance farm) {
        Entry existing=entries.get(farm.getInstanceId());if(existing!=null)return existing;
        var legacy=PlayerInteriorAllocator.get(level);
        BlockPos source=legacy.isLegacyCavePlaced(farm.getOwnerUUID())?legacy.getLegacyCaveOrigin(farm.getOwnerUUID()):null;
        if(source!=null)for(Entry e:entries.values())if(source.equals(e.legacy)){source=null;break;}
        Entry entry=new Entry(farm.getInstanceId(),FarmCaveLayout.BASE.offset(0,0,nextSlot++*32),source);
        entries.put(entry.farmId,entry);byOrigin.put(entry.origin,entry);setDirty();return entry;
    }
    public static FarmCaveData load(CompoundTag tag,HolderLookup.Provider provider) {
        FarmCaveData data=new FarmCaveData();data.nextSlot=tag.getInt("NextSlot");
        ListTag list=tag.getList("Caves",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++) {
            CompoundTag t=list.getCompound(i);
            Entry e=new Entry(t.getUUID("Farm"),BlockPos.of(t.getLong("Origin")),t.contains("Legacy")?BlockPos.of(t.getLong("Legacy")):null);
            e.version=t.getInt("Version");e.lastDay=t.contains("LastDay")?t.getInt("LastDay"):-1;e.appliedChoice=t.getString("AppliedChoice");e.dehydratorGranted=t.getBoolean("DehydratorGranted");data.entries.put(e.farmId,e);data.byOrigin.put(e.origin,e);
            data.nextSlot=Math.max(data.nextSlot,(e.origin.getZ()-FarmCaveLayout.BASE.getZ())/32+1);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("NextSlot",nextSlot);ListTag list=new ListTag();
        for(Entry e:entries.values()) {
            CompoundTag t=new CompoundTag();t.putUUID("Farm",e.farmId);t.putLong("Origin",e.origin.asLong());
            if(e.legacy!=null)t.putLong("Legacy",e.legacy.asLong());
            t.putInt("Version",e.version);t.putInt("LastDay",e.lastDay);t.putString("AppliedChoice",e.appliedChoice);t.putBoolean("DehydratorGranted",e.dehydratorGranted);list.add(t);
        }
        tag.put("Caves",list);return tag;
    }
}
