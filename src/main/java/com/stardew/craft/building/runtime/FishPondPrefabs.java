package com.stardew.craft.building.runtime;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.utility.WoodSignBlock;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.service.FishPondColorSyncService;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import java.util.*;

/** The pond is an excavated prefab; its biological state remains in FishPondWorldData. */
public final class FishPondPrefabs {
    public static final ResourceLocation FAMILY = new ResourceLocation("stardewcraft:fish_pond");
    private FishPondPrefabs() {}
    public static boolean isPond(ResourceLocation family) { return FAMILY.equals(family); }
    public static BuildingRecord at(ServerLevel level, BlockPos pos) {
        var data=BuildingWorldData.get(level.getServer());
        var id=data.occupying(level.dimension().location(),pos);
        var record=id==null?null:data.find(id);
        return record!=null && isPond(record.family()) && record.mode()==BuildingRecord.Mode.PREFAB ? record : null;
    }
    public static BlockPos signPosition(BuildingRecord record) {
        var tier=PrefabDefinitions.get(record.family()).tier(1);
        // Imported purple marker: left front corner, two blocks above the ground anchor.
        return PrefabDefinitions.world(new BlockPos(0,5,5),tier.anchor(),record.anchor(),PrefabDefinitions.rotation(record.facing()));
    }
    public static BuildingPlacementService.SpaceIssue checkSite(ServerLevel level, BuildingBounds claim,
            BlockPos anchor, Direction facing, BuildingRecord moving) {
        var tier=PrefabDefinitions.get(FAMILY).tier(1);
        var rotation=PrefabDefinitions.rotation(facing);
        Map<BlockPos,BlockState> planned=new HashMap<>();
        for(var cell:PrefabDefinitions.template(level,tier).cells())
            planned.put(PrefabDefinitions.world(cell.pos(),tier.anchor(),anchor,rotation),cell.state().rotate(rotation));
        Set<BlockPos> vacated=moving==null?Set.of():new HashSet<>(BuildingTransfer.sourcePositions(level,moving));
        var floors=com.stardew.craft.floor.SurfaceFloorData.get(level);
        for(var entry:planned.entrySet()) {
            var pos=entry.getKey();var current=level.getBlockState(pos);
            if(vacated.contains(pos))continue;
            if(pos.getY()>anchor.getY()) {
                if(!BuildingPlacementService.isClearAirVolume(level, pos, floors))
                    return new BuildingPlacementService.SpaceIssue("air",pos);
            } else if(level.getBlockEntity(pos)!=null || floors.at(pos)!=null || current.getDestroySpeed(level,pos)<0
                    || BuildingProtection.protects(level,pos)) {
                return new BuildingPlacementService.SpaceIssue("ground_contents",pos);
            }
        }
        // The ground under the stone rim remains intact and level.
        for(var entry:planned.entrySet()) if(entry.getValue().is(ModBlocks.POND_STONE.get())) {
            var pos=entry.getKey().below();
            if(vacated.contains(pos) || !level.getBlockState(pos).isFaceSturdy(level,pos,Direction.UP))
                return new BuildingPlacementService.SpaceIssue("ground",pos);
        }
        // Every exposed bottom/side of the future water volume must have a solid wall.
        // Excavation may replace solid soil or an existing cavity, but cannot spill into caves.
        for(var entry:planned.entrySet()) if(entry.getValue().is(ModBlocks.FISH_POND_WATER.get())) {
            for(var direction:Direction.values()) {
                if(direction==Direction.UP)continue;
                var pos=entry.getKey().relative(direction);var state=planned.get(pos);
                if(state!=null && state.is(ModBlocks.FISH_POND_WATER.get()))continue;
                if(state==null)state=vacated.contains(pos)?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():level.getBlockState(pos);
                if(!state.isFaceSturdy(level,pos,direction.getOpposite()))
                    return new BuildingPlacementService.SpaceIssue("pond_support",pos);
            }
        }
        return null;
    }
    public static Set<BlockPos> supports(ServerLevel level, BuildingRecord record) {
        if(!isPond(record.family()))return Set.of();
        var tier=PrefabDefinitions.get(FAMILY).tier(1);var rotation=PrefabDefinitions.rotation(record.facing());
        Set<BlockPos> water=new HashSet<>(),result=new HashSet<>();
        for(var cell:PrefabDefinitions.template(level,tier).cells()) {
            var pos=PrefabDefinitions.world(cell.pos(),tier.anchor(),record.anchor(),rotation);
            if(cell.state().is(ModBlocks.FISH_POND_WATER.get()))water.add(pos);
            if(cell.state().is(ModBlocks.POND_STONE.get()))result.add(pos.below());
        }
        for(var pos:water)for(var direction:Direction.values())
            if(direction!=Direction.UP && !water.contains(pos.relative(direction)))result.add(pos.relative(direction));
        return result;
    }

    public static void bind(ServerLevel level, BuildingRecord record) {
        if(!isPond(record.family()) || record.phase()!=BuildingRecord.Phase.READY)return;
        var data=FishPondWorldData.get(level);
        if(data.findPondByManagerAnyOwner(level.dimension().location().toString(),record.manager()).isPresent())return;
        var owner=FarmInstanceRegistry.get(level.getServer()).getOwnerBySlot(record.farmSlot());
        if(owner==null)return;
        var tier=PrefabDefinitions.get(record.family()).tier(1);var rotation=PrefabDefinitions.rotation(record.facing());
        Set<Long> water=new HashSet<>();Set<BlockPos> nets=new HashSet<>();BlockPos bucket=null;
        int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,y1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
        for(var cell:PrefabDefinitions.template(level,tier).cells()) {
            var pos=PrefabDefinitions.world(cell.pos(),tier.anchor(),record.anchor(),rotation);
            if(cell.state().is(ModBlocks.FISH_POND_BUCKET.get()))bucket=pos;
            if(cell.state().is(ModBlocks.FISH_NET.get()) && cell.state().getValue(MapDecorStaticBlock.PART)==MapDecorStaticBlock.Part.MAIN)nets.add(pos);
            if(!cell.state().is(ModBlocks.FISH_POND_WATER.get()))continue;
            water.add(pos.asLong());x0=Math.min(x0,pos.getX());y0=Math.min(y0,pos.getY());z0=Math.min(z0,pos.getZ());
            x1=Math.max(x1,pos.getX());y1=Math.max(y1,pos.getY());z1=Math.max(z1,pos.getZ());
        }
        if(bucket==null || water.isEmpty())throw new IllegalStateException("Pond template lacks water/bucket");
        data.createOrUpdatePondAtManager(level,owner,record.manager(),bucket,nets,water,x0,y0,z0,x1,y1,z1);
        com.stardew.craft.blockentity.FishPondBucketBlockEntity.syncVisualState(level,bucket);
        FishPondColorSyncService.broadcastSnapshot(level);
    }
    public static boolean interact(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return false;
        var level=player.serverLevel();var record=at(level,event.getPos());
        if(record==null || record.phase()!=BuildingRecord.Phase.READY)return false;
        var target=signPosition(record);
        boolean mounted=level.getBlockState(target).is(ModBlocks.WOOD_SIGN.get());
        // A pond's sign displays its fish; arbitrary held items cannot overwrite it.
        if(!event.getItemStack().is(ModItems.WOOD_SIGN.get())) {
            var pond=FishPondWorldData.get(level).findPondByManagerAnyOwner(level.dimension().location().toString(),record.manager()).orElse(null);
            if(pond!=null) {
                if(!BuildingService.canManage(player,record)){event.setCanceled(true);BuildingPlacementService.message(player,"permission");return true;}
                if(com.stardew.craft.fishpond.service.FishPondHusbandry.use(level,pond,player,event.getHand())) {
                    event.setCanceled(true);event.setCancellationResult(InteractionResult.CONSUME);return true;
                }
            }
            if(pond!=null && (event.getItemStack().isEmpty() || event.getPos().equals(target))) {
                player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id,inventory,who)->new com.stardew.craft.menu.FishPondManagerMenu(id,inventory,record.manager()),
                    net.minecraft.network.chat.Component.translatable("container.stardew_craft.fish_pond_manager")));
                event.setCanceled(true);event.setCancellationResult(InteractionResult.CONSUME);return true;
            }
            return false;
        }
        event.setCanceled(true);event.setCancellationResult(InteractionResult.CONSUME);
        if(!BuildingService.canManage(player,record)){BuildingPlacementService.message(player,"permission");return true;}
        if(mounted)return true;
        if(!level.getBlockState(target).isAir() || !level.getBlockState(target.above()).isAir()) {
            BuildingPlacementService.message(player,"air");return true;
        }
        BlockState state=ModBlocks.WOOD_SIGN.get().defaultBlockState().setValue(WoodSignBlock.FACING,record.facing());
        if(!state.canSurvive(level,target)){BuildingPlacementService.message(player,"ground");return true;}
        BuildingProtection.internal(()->{
            level.setBlock(target,state,Block.UPDATE_ALL);
            state.getBlock().setPlacedBy(level,target,state,player,event.getItemStack());
        });
        if(!player.isCreative())event.getItemStack().shrink(1);
        level.playSound(null,target,net.minecraft.sounds.SoundEvents.WOOD_PLACE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
        FishPondWorldData.get(level).findPondByManagerAnyOwner(level.dimension().location().toString(),record.manager())
                .ifPresent(pond->com.stardew.craft.blockentity.FishPondBucketBlockEntity.syncVisualState(level,pond.bucketPos()));
        return true;
    }
    public static void remove(ServerLevel level, BuildingRecord record) {
        if(isPond(record.family())) {
            var data=FishPondWorldData.get(level);
            data.findPondByManagerAnyOwner(level.dimension().location().toString(),record.manager()).ifPresent(pond->data.removePond(pond.pondId()));
            FishPondColorSyncService.broadcastSnapshot(level);
        }
    }
}
