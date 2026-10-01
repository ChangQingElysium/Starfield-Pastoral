package com.stardew.craft.building.runtime;

import com.stardew.craft.port.PortItemData;
import net.minecraft.core.HolderLookup;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** The physical document identifies one durable draft, including across copies and transfers. */
public final class BuildingDrafts extends SavedData {
    private final Map<UUID,CompoundTag> drafts=new HashMap<>();
    public static BuildingDrafts get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(BuildingDrafts::new,BuildingDrafts::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(BuildingDrafts::new,BuildingDrafts::load)),"stardew_building_drafts");}
    public static UUID id(ItemStack stack){var tag=BuildingBlueprintItem.draft(stack);return tag.hasUUID("DraftId")?tag.getUUID("DraftId"):BuildingBlueprintItem.permit(stack)!=null?BuildingBlueprintItem.permit(stack):tag.hasUUID("MoveBuilding")?tag.getUUID("MoveBuilding"):null;}
    public void apply(ItemStack stack){
        var id=id(stack); if(id==null)return;
        var root=BuildingBlueprintItem.draft(stack);var saved=drafts.get(id);
        root.remove("DraftAnchor");root.remove("DraftDimension");root.remove("DraftFacing");
        if(saved!=null)root.merge(saved.copy());
        PortItemData.set(stack, DataComponents.CUSTOM_DATA,CustomData.of(root));
    }
    public void write(ItemStack stack){
        var root=BuildingBlueprintItem.draft(stack);var id=id(stack);if(id==null)id=UUID.randomUUID();root.putUUID("DraftId",id);
        var saved=new CompoundTag();saved.putString("DraftFacing",BuildingBlueprintItem.facing(stack).getName());
        if(root.contains("DraftAnchor")){saved.putLong("DraftAnchor",root.getLong("DraftAnchor"));saved.putString("DraftDimension",root.getString("DraftDimension"));}
        drafts.put(id,saved);PortItemData.set(stack, DataComponents.CUSTOM_DATA,CustomData.of(root));setDirty();
    }
    public void consume(ItemStack stack){var id=id(stack);if(id!=null){drafts.remove(id);setDirty();}}
    public void synchronize(ServerPlayer player){
        var rows=new net.minecraft.nbt.ListTag();var seen=new HashSet<UUID>();
        var held = new ArrayList<ItemStack>(player.getInventory().items);
        // The offhand is outside PlayerInventory's regular item slots.  Movement
        // documents can be held there too; omitting it leaves a stale client pin
        // and lets repeated bowl clicks create another document.
        held.add(player.getOffhandItem());
        for(var stack : held){
            if(!(stack.getItem() instanceof BuildingBlueprintItem item) || id(stack)==null)continue;
            apply(stack);var anchor=BuildingBlueprintItem.pinned(stack,player.level());var identity=id(stack);
            // A duplicated move document still refers to one building.  Keep
            // one server preview row for that building so old duplicate items
            // cannot turn into a stack of identical client projections.
            UUID previewKey = BuildingBlueprintItem.isMove(stack)
                    ? BuildingBlueprintItem.draft(stack).getUUID("MoveBuilding") : identity;
            if(anchor==null || !seen.add(previewKey) || !PrefabDefinitions.available(item.family()))continue;
            var moving=BuildingBlueprintItem.isMove(stack)?BuildingBlueprintItem.moving(player.serverLevel(),stack):null;
            if(BuildingBlueprintItem.isMove(stack) && moving==null)continue;
            int tier=moving==null?1:moving.tier();
            if(moving==null)BuildingPreviewService.sendTemplate(player,item.family(),tier);else BuildingMovePreview.send(player,moving);
            var facing=BuildingBlueprintItem.facing(stack);var probe=BuildingPlacementService.probe(player.serverLevel(),player,anchor,facing,moving!=null && moving.mode()==BuildingRecord.Mode.SELF_BUILT,item.family(),moving);
            String issue=probe.issue();if(probe.valid() && moving==null && !BuildingWorldData.get(player.server).permitsPlacement(
                    BuildingBlueprintItem.permit(stack),probe.farm().getInstanceId(),item.family(),player.getUUID(),
                    com.stardew.craft.farm.FarmInstanceRegistry.get(player.server)))issue="permit";
            var outline=PrefabDefinitions.maxTier(item.family())==1?probe.claim():probe.structure();
            var row=new CompoundTag();row.putUUID("Id",identity);row.putString("Family",item.family().toString());row.putInt("Tier",tier);row.putString("Facing",facing.getName());row.putString("Issue",issue);
            row.putLong("Anchor",anchor.asLong());row.putLong("Min",probe.claim().min().asLong());row.putLong("Max",probe.claim().maxExclusive().asLong());row.putLong("InnerMin",outline.min().asLong());row.putLong("InnerMax",outline.maxExclusive().asLong());row.putLong("Manager",probe.manager().asLong());
            if(moving!=null)row.putUUID("Moving",moving.id());rows.add(row);
        }
        var tag=new CompoundTag();tag.putString("Dimension",player.level().dimension().location().toString());tag.put("Pins",rows);
        com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new com.stardew.craft.network.payload.BuildingPinnedPreviewsPayload(tag));
    }
    @Override public CompoundTag save(CompoundTag tag){ net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();var all=new CompoundTag();drafts.forEach((id,value)->all.put(id.toString(),value.copy()));tag.put("Drafts",all);return tag;}
    public static BuildingDrafts load(CompoundTag tag,HolderLookup.Provider registries){var result=new BuildingDrafts();var all=tag.getCompound("Drafts");for(var key:all.getAllKeys())result.drafts.put(UUID.fromString(key),all.getCompound(key).copy());return result;}
}
