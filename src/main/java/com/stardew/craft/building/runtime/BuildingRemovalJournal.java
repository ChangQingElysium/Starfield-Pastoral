package com.stardew.craft.building.runtime;

import com.stardew.craft.port.PortItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Write-ahead demolition and manager refund; a reload retries only the exact original component list. */
public final class BuildingRemovalJournal extends SavedData {
    private final Map<UUID,CompoundTag> pending=new LinkedHashMap<>();
    public static BuildingRemovalJournal get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(BuildingRemovalJournal::new,BuildingRemovalJournal::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(BuildingRemovalJournal::new,BuildingRemovalJournal::load)),"stardew_building_removals");}
    public boolean contains(UUID id){return pending.containsKey(id);}
    public void prepare(ServerPlayer player,BuildingRecord record,Collection<BlockPos> positions){
        if(pending.containsKey(record.id()))return;
        var tag=new CompoundTag();tag.put("Building",record.save());tag.putUUID("Actor",player.getUUID());tag.putLongArray("Positions",positions.stream().mapToLong(BlockPos::asLong).toArray());
        pending.put(record.id(),tag);setDirty();player.server.overworld().getDataStorage().save();
    }
    public void recover(MinecraftServer server){
        for(var entry:List.copyOf(pending.entrySet())){
            var tag=entry.getValue();var record=BuildingRecord.load(tag.getCompound("Building"));
            var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,record.dimension()));if(level==null)continue;
            if(!tag.getBoolean("Removed")){
                for(long packed:tag.getLongArray("Positions"))level.getChunkAt(BlockPos.of(packed));
                BuildingProtection.transfer(()->{for(long packed:tag.getLongArray("Positions")){var pos=BlockPos.of(packed);level.removeBlockEntity(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);}});
                FishPondPrefabs.remove(level,record);
                com.stardew.craft.pet.PetBowlBuildings.removeContents(level,record);
                level.getChunkSource().save(true);BuildingWorldData.get(server).demolish(record.id());BuildingProtection.clearMasks();
                tag.putBoolean("Removed",true);setDirty();server.overworld().getDataStorage().save();
            }
            if(record.mode()==BuildingRecord.Mode.SELF_BUILT){
                var player=server.getPlayerList().getPlayer(tag.getUUID("Actor"));if(player==null)continue;
                boolean delivered=false;
                for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
                    var data=BuildingBlueprintItem.draft(player.getInventory().getItem(slot));
                    if(data.hasUUID("BuildingRefund") && data.getUUID("BuildingRefund").equals(record.id())){delivered=true;break;}
                }
                if(!delivered){
                    var item=new ItemStack(PrefabDefinitions.managerItem(record.family()));var receipt=new CompoundTag();receipt.putUUID("BuildingRefund",record.id());PortItemData.set(item, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,com.stardew.craft.port.net.minecraft.world.item.component.CustomData.of(receipt));
                    var plan=BuildingPurchasePlan.prepare(player.getInventory(),item,List.of());if(plan==null)continue;plan.apply(player.getInventory());
                }
                server.getPlayerList().saveAll();
            }
            pending.remove(entry.getKey());setDirty();server.overworld().getDataStorage().save();
        }
    }
    @Override public CompoundTag save(CompoundTag tag){ net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();var list=new ListTag();pending.values().forEach(value->list.add(value.copy()));tag.put("Removals",list);return tag;}
    public static BuildingRemovalJournal load(CompoundTag tag,HolderLookup.Provider registries){var data=new BuildingRemovalJournal();for(var raw:tag.getList("Removals",10)){var row=(CompoundTag)raw;data.pending.put(row.getCompound("Building").getUUID("Id"),row.copy());}return data;}
}
