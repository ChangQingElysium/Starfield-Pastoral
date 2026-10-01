package com.stardew.craft.building.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

public final class BuildingMovePreview {
    private static final Map<UUID,CompoundTag> sent=new HashMap<>();
    private BuildingMovePreview(){}
    public static void clear(){sent.clear();}
    public static void send(ServerPlayer player,BuildingRecord record){
        var lift=BuildingWorldData.get(player.server).moveLift(record.id());
        var tag=lift==null?capture(player,record):lift.preview().copy();
        if(!tag.equals(sent.get(player.getUUID()))){sent.put(player.getUUID(),tag.copy());PacketDistributor.sendToPlayer(player,new com.stardew.craft.network.payload.BuildingTemplatePreviewPayload(tag));}
    }
    public static CompoundTag capture(ServerPlayer player,BuildingRecord record){
        var level=player.serverLevel(); var bounds=BuildingTransfer.contentBounds(record);
        if(!level.hasChunksAt(bounds.min(),bounds.maxInclusive()))throw new IllegalStateException("Move preview source is not loaded");
        var inverse=PrefabDefinitions.inverse(PrefabDefinitions.rotation(record.facing()));
        boolean centered=record.mode()==BuildingRecord.Mode.SELF_BUILT;
        var tag=new CompoundTag();tag.putString("Family",record.family().toString());tag.putInt("Tier",record.tier());tag.putUUID("Moving",record.id());tag.putBoolean("Centered",centered);
        tag.putIntArray("Size",new int[]{bounds.maxExclusive().getX()-bounds.min().getX(),bounds.maxExclusive().getY()-bounds.min().getY(),bounds.maxExclusive().getZ()-bounds.min().getZ()});tag.putIntArray("Anchor",new int[]{0,0,0});
        var local=centered?UtilityBuildings.moveBounds(record,BlockPos.ZERO,net.minecraft.core.Direction.SOUTH):PrefabDefinitions.get(record.family()).tier(record.tier()).bounds();
        var reservation=centered?local:PrefabDefinitions.get(record.family()).reservation();
        tag.putLong("BoundsMin",local.min().asLong());tag.putLong("BoundsMax",local.maxExclusive().asLong());tag.putLong("ReservationMin",reservation.min().asLong());tag.putLong("ReservationMax",reservation.maxExclusive().asLong());
        tag.putLong("ManagerRelative",(centered?BlockPos.ZERO:PrefabDefinitions.rotateCell(record.manager().subtract(record.anchor()),inverse)).asLong());
        var blocks=new ListTag();
        for(var pos:BuildingTransfer.sourcePositions(level,record)){
            var at=centered?pos.subtract(record.anchor()).rotate(inverse):PrefabDefinitions.rotateCell(pos.subtract(record.anchor()),inverse);
            var row=new CompoundTag();row.putIntArray("Pos",new int[]{at.getX(),at.getY(),at.getZ()});row.put("State",NbtUtils.writeBlockState(level.getBlockState(pos).rotate(inverse)));
            var be=level.getBlockEntity(pos);if(be!=null)row.put("Appearance",be.saveWithFullMetadata(level.registryAccess()));blocks.add(row);
        }
        var covers=new ListTag();var floors=com.stardew.craft.floor.SurfaceFloorData.get(level);
        for(var pos:BlockPos.betweenClosed(bounds.min().below(centered?1:0),bounds.maxInclusive())){
            var cover=floors.at(pos);if(cover==null)continue;
            var at=centered?pos.subtract(record.anchor()).rotate(inverse):PrefabDefinitions.rotateCell(pos.subtract(record.anchor()),inverse);
            var row=new CompoundTag();row.putLong("Pos",at.asLong());row.putString("Type",cover.type().id);row.putInt("Variant",cover.variant());covers.add(row);
            if(!bounds.contains(pos)){
                var cell=new CompoundTag();cell.putIntArray("Pos",new int[]{at.getX(),at.getY(),at.getZ()});cell.put("State",NbtUtils.writeBlockState(level.getBlockState(pos).rotate(inverse)));cell.putBoolean("FloorOnly",true);blocks.add(cell);
            }
        }
        tag.put("Floors",covers);
        var canonical=new BuildingRecord(record.id(),record.farmId(),record.farmSlot(),record.family(),record.mode(),record.dimension(),BlockPos.ZERO,
                BlockPos.of(tag.getLong("ManagerRelative")),net.minecraft.core.Direction.SOUTH,reservation,BuildingRecord.Phase.READY,record.tier(),record.residence(),record.revision()+1,record.displayName());
        tag.put("Decorations",BuildingTransferExtras.capture(level,record,canonical).getList("Decorations",10));
        tag.put("Blocks",blocks);
        return tag;
    }
}
