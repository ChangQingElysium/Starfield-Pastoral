package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.MushroomBoxBlockEntity;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.*;
import com.stardew.craft.interior.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import com.stardew.craft.port.net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("stardewcraft_farm_cave")
@PrefixGameTestTemplate(false)
public final class FarmCaveRuntimeGameTests {
    @GameTest(templateNamespace="stardewcraft_farm_cave",template="ring_utilities",timeoutTicks=800)
    public static void migrationDoorTravelTransferAndMissingFloor(GameTestHelper h) {
        ServerLevel level=h.getLevel().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        h.assertTrue(level!=null,"Valley dimension missing");
        var reg=FarmInstanceRegistry.get();UUID owner=UUID.randomUUID(),visitor=UUID.randomUUID();
        FarmInstance farm=reg.createFarm(owner,"Cave owner","Cave test",FarmType.STANDARD);
        reg.createFarm(visitor,"Cave visitor","Other farm",FarmType.STANDARD);
        farm.setCaveChoice(FarmCaveChoice.MUSHROOMS);
        BlockPos legacy=legacyOrigin(level,owner);
        // Real old template, ready produce, and a player inventory; the old room stays as backup.
        level.getStructureManager().get(new ResourceLocation("stardewcraft","farm_layouts/cave_legacy")).orElseThrow()
                .placeInWorld(level,legacy,legacy,new StructurePlaceSettings(),level.random,3);
        BlockPos oldBox=legacy.offset(com.stardew.craft.port.PortJava.getFirst(FarmCaveLayout.LEGACY_BOXES));
        level.setBlock(oldBox,ModBlocks.MUSHROOM_BOX.get().defaultBlockState(),3);
        ((MushroomBoxBlockEntity)level.getBlockEntity(oldBox)).setProductIfEmpty(new ItemStack(Items.DIAMOND));
        BlockPos oldChest=legacy.offset(4,1,4);level.setBlock(oldChest,Blocks.CHEST.defaultBlockState(),3);
        ((ChestBlockEntity)level.getBlockEntity(oldChest)).setItem(0,new ItemStack(Items.EMERALD,17));
        ServerPlayer player=player(level,visitor);BlockPos door=farm.getOrigin().offset(farm.getFarmLayout().cavePortalWall().min());
        player.setPos(door.getX()+.5,door.getY(),door.getZ()+.5);
        player.getPersistentData().putLong("stardewcraft_last_portal_tick",level.getGameTime()-20);
        FarmCaveRuntime.enter(player);
        BlockPos origin=FarmCaveRuntime.origin(level,farm);
        h.assertTrue(!player.blockPosition().equals(origin.offset(FarmCaveLayout.SPAWN)),"Cold cave teleported immediately");
        AtomicInteger daily=new AtomicInteger();FarmCaveRuntime.daily(level,farm,800000,daily::incrementAndGet);
        FarmCaveRuntime.daily(level,farm,800000,daily::incrementAndGet);
        h.startSequence().thenWaitUntil(()->h.assertTrue(FarmCaveRuntime.ready(level,farm),"Cave is not ready"))
            .thenWaitUntil(()->h.assertTrue(player.blockPosition().equals(origin.offset(FarmCaveLayout.SPAWN)),"Visitor did not enter the farm they visited"))
            .thenExecute(()-> {
                h.assertTrue(daily.get()==1,"Daily callback skipped or duplicated");
                h.assertTrue(level.getBlockEntity(origin.offset(FarmCaveLayout.EXIT)) instanceof PortalTriggerBlockEntity be && be.getTargetId().equals("farm_cave_exit"),"Missing exit portal target");
                h.assertTrue(!level.getBlockState(origin.offset(FarmCaveLayout.SPAWN).above()).is(ModBlocks.MINE_LAMP.get()),"Arrival lamp was not removed");
                var box=(MushroomBoxBlockEntity)level.getBlockEntity(origin.offset(com.stardew.craft.port.PortJava.getFirst(FarmCaveLayout.BOXES)));
                h.assertTrue(box!=null && box.isReady() && box.getProduct().is(Items.DIAMOND),"Ready mushroom output lost in migration");
                boolean chest=false;
                for(BlockPos p:BlockPos.betweenClosed(origin,origin.offset(15,12,17)))if(level.getBlockEntity(p) instanceof ChestBlockEntity c && c.getItem(0).is(Items.EMERALD) && c.getItem(0).getCount()==17)chest=true;
                h.assertTrue(chest,"Player chest contents lost");
                h.assertTrue(((ChestBlockEntity)level.getBlockEntity(oldChest)).getItem(0).getCount()==17,"Legacy backup changed");
                h.assertTrue(level.getBlockState(origin.offset(FarmCaveLayout.DEHYDRATOR)).is(ModBlocks.DEHYDRATOR.get()),"Missing one-time dehydrator");
                h.assertTrue(!com.stardew.craft.event.FarmAreaProtectionEvents.canBuildAt(player,origin.offset(7,3,8)),"Visit-only player can build");
                FarmPermissionManager.get().setPermission(owner,visitor,FarmPermissionManager.PERM_FULL);
                h.assertTrue(com.stardew.craft.event.FarmAreaProtectionEvents.canBuildAt(player,origin.offset(7,3,8)),"Full-access visitor cannot build");
                h.assertTrue(!com.stardew.craft.event.FarmAreaProtectionEvents.canBuildAt(player,origin.offset(7,2,8)),"Fixed cave floor can be broken");
                var snapshot=FarmCaveData.get(level).save(new CompoundTag(),level.registryAccess());
                h.assertTrue(FarmCaveData.load(snapshot,level.registryAccess()).find(farm.getInstanceId()).origin().equals(origin),"Cave identity not persisted");
                UUID next=UUID.randomUUID();h.assertTrue(reg.transferFarm(owner,next,"Next owner"),"Farm transfer failed");
                h.assertTrue(FarmCaveRuntime.origin(level,reg.getFarm(next)).equals(origin),"Transfer allocated a different cave");
                BlockPos exit=reg.getFarm(next).getOrigin().offset(reg.getFarm(next).getFarmLayout().caveExitSpawn());
                level.setBlock(exit.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(exit,Blocks.AIR.defaultBlockState(),3);level.setBlock(exit.above(),Blocks.AIR.defaultBlockState(),3);
                player.getPersistentData().putLong("stardewcraft_last_portal_tick",level.getGameTime()-20);
                FarmCaveRuntime.exit(player);
            })
            .thenWaitUntil(()->h.assertTrue(player.blockPosition().equals(farm.getOrigin().offset(farm.getFarmLayout().caveExitSpawn())),"Cave exit did not return to its farm"))
            .thenExecute(()-> {
                // An installed flag is insufficient: removing the landing floor must block travel.
                level.removeBlock(origin.offset(FarmCaveLayout.SPAWN).below(),false);
                level.removeBlock(origin.offset(FarmCaveLayout.DEHYDRATOR),false);
                FarmInstance current=FarmCaveRuntime.farm(farm.getInstanceId());
                h.assertTrue(!FarmCaveRuntime.ready(level,current),"Installed flag ignored the missing floor");
                // Force a fresh preparation after its prior lease finishes.
            })
            .thenIdle(65)
            .thenExecute(()->FarmCaveRuntime.request(level,FarmCaveRuntime.farm(farm.getInstanceId())))
            .thenWaitUntil(()->h.assertTrue(FarmCaveRuntime.ready(level,FarmCaveRuntime.farm(farm.getInstanceId())),"Missing floor was not repaired"))
            .thenExecute(()-> {
                h.assertTrue(((MushroomBoxBlockEntity)level.getBlockEntity(origin.offset(com.stardew.craft.port.PortJava.getFirst(FarmCaveLayout.BOXES)))).getProduct().is(Items.DIAMOND),"Repair overwrote ready produce");
                h.assertTrue(level.getBlockState(origin.offset(FarmCaveLayout.DEHYDRATOR)).isAir(),"Repair duplicated the one-time dehydrator");
                reg.deleteFarm(FarmCaveRuntime.farm(farm.getInstanceId()).getOwnerUUID());
                FarmInstance fresh=reg.createFarm(owner,"New farm","New cave",FarmType.STANDARD);
                h.assertTrue(!FarmCaveRuntime.origin(level,fresh).equals(origin),"Deleted farm reused its cave room");
                CompoundTag persisted=FarmCaveData.get(level).save(new CompoundTag(),level.registryAccess());
                var caves=persisted.getList("Caves",Tag.TAG_COMPOUND);
                for(int i=0;i<caves.size();i++)if(caves.getCompound(i).getUUID("Farm").equals(fresh.getInstanceId()))
                    h.assertTrue(!caves.getCompound(i).contains("Legacy"),"New farm reimported a retired legacy inventory");
                player.discard();
                com.stardew.craft.StardewCraft.LOGGER.info("[FARM-CAVE-TEST] PASS: cold entry, legacy inventories, visit permissions, stable transfer, exit and floor repair");
            }).thenSucceed();
    }
    @GameTest(templateNamespace="stardewcraft_farm_cave",template="ring_utilities",timeoutTicks=800)
    public static void missingLegacyRoomAndFailedArrivalRemainSafe(GameTestHelper h) {
        ServerLevel level=h.getLevel().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        UUID owner=UUID.randomUUID();FarmInstance farm=FarmInstanceRegistry.get().createFarm(owner,"Missing cave","Missing cave",FarmType.STANDARD);
        BlockPos old=legacyOrigin(level,owner);
        FarmCaveRuntime.request(level,farm);BlockPos origin=FarmCaveRuntime.origin(level,farm);
        ServerPlayer player=player(level,owner);BlockPos door=farm.getOrigin().offset(farm.getFarmLayout().cavePortalWall().min());
        player.setPos(door.getX()+.5,door.getY(),door.getZ()+.5);
        h.startSequence().thenWaitUntil(()->h.assertTrue(FarmCaveRuntime.ready(level,farm),"Missing legacy room was not recovered"))
            .thenExecute(()-> {
                h.assertTrue(level.getBlockState(old).isAir(),"Test requires old cavePlaced flag with an absent room");
                level.setBlock(origin.offset(FarmCaveLayout.SPAWN),Blocks.STONE.defaultBlockState(),3);
                player.getPersistentData().putLong("stardewcraft_last_portal_tick",level.getGameTime()-20);
                FarmCaveRuntime.enter(player);
            }).thenIdle(10).thenExecute(()-> {
                h.assertTrue(player.blockPosition().equals(door),"Failed arrival still teleported player");
                level.removeBlock(origin.offset(FarmCaveLayout.SPAWN),false);
            }).thenIdle(25).thenExecute(()->FarmCaveRuntime.enter(player))
            .thenWaitUntil(()->h.assertTrue(player.blockPosition().equals(origin.offset(FarmCaveLayout.SPAWN)),"Failure could not be retried"))
            .thenExecute(()-> {
                player.discard();
                com.stardew.craft.StardewCraft.LOGGER.info("[FARM-CAVE-TEST] PASS: missing legacy room, failed-arrival rejection and retry");
            }).thenSucceed();
    }
    private static BlockPos legacyOrigin(ServerLevel level,UUID owner) {
        var alloc=PlayerInteriorAllocator.get(level);alloc.getLegacyCaveOrigin(owner);
        CompoundTag saved=alloc.save(new CompoundTag(),level.registryAccess());int index=saved.getInt("nextIndex");
        var previous=FarmCaveData.get(level).save(new CompoundTag(),level.registryAccess()).getList("Caves",Tag.TAG_COMPOUND);
        for(int i=0;i<previous.size();i++)if(previous.getCompound(i).contains("Legacy")) {
            BlockPos origin=BlockPos.of(previous.getCompound(i).getLong("Legacy"));
            index=Math.max(index,(origin.getZ()-InteriorSubspaceManager.LEGACY_FARM_CAVE_INTERIOR_ORIGIN.getZ())/32+1);
        }
        var players=saved.getList("players",Tag.TAG_COMPOUND);
        for(int i=0;i<players.size();i++)if(players.getCompound(i).getUUID("uuid").equals(owner)) {
            players.getCompound(i).putInt("index",index);players.getCompound(i).putBoolean("cavePlaced",true);
        }
        saved.putInt("nextIndex",index+1);
        var prepared=PlayerInteriorAllocator.load(saved,level.registryAccess());prepared.setDirty();
        level.getServer().overworld().getDataStorage().set("stardew_player_interior_alloc",prepared);
        return prepared.getLegacyCaveOrigin(owner);
    }
    private static ServerPlayer player(ServerLevel level,UUID id) {
        ServerPlayer p=new ServerPlayer(level.getServer(),level,new GameProfile(id,"Cave visitor")) {
            @Override public void teleportTo(ServerLevel target,double x,double y,double z,float yaw,float pitch) {
                setServerLevel(target);setPos(x,y,z);setYRot(yaw);setXRot(pitch);
            }
        };
        p.connection=new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),p) {
            @Override public void send(Packet<?> packet) {}
        };
        return p;
    }
}
