package com.stardew.craft.gametest;

import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineBugEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("stardewcraft_bug")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeBugGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=120)
    public static void patrolWallFarmerReloadAndSourceLoot(GameTestHelper h) throws Exception {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,2,8));
        for(int x=-7;x<=12;x++)for(int z=-7;z<=7;z++) {
            level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<4;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        var start=Vec3.atBottomCenterOf(origin);
        var bug=(MineBugEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"bug",start,-90,20);
        h.assertTrue(bug.getHealth()==1&&bug.monsterState().stats().getDamage()==8,"Old floor multiplier overwrote Bug source stats");
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"BugTest")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        // Default FakePlayer is invulnerable and does not tick away its spawn immunity.
        var immunity=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        immunity.setAccessible(true);immunity.setInt(player,0);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(start.add(0,0,3));level.addNewPlayer(player);
        var day=com.stardew.craft.time.StardewTimeManager.get();
        com.stardew.craft.player.PlayerDataManager.getPlayerData(player).setDailyLuckForDate(0,((day.getCurrentYear()*4)+day.getCurrentSeason())*28+day.getCurrentDay()-1);
        final float[] healthBefore={0};
        final MineBugEntity[] waterBug={null};
        final Vec3[] before={start};
        h.runAtTickTime(5,()->before[0]=bug.position());
        h.runAtTickTime(15,()->{
            h.assertTrue(Math.abs(bug.getX()-before[0].x-30*MineBugEntity.STEP)<.0001,"Source speed is not 2 pixels per 60 Hz update");
            h.assertTrue(Math.abs(bug.getZ()-start.z)<.0001&&bug.getTarget()==null,"Bug chased player");
            h.assertTrue(Math.abs(bug.getY()-start.y-MineBugEntity.FLIGHT_LIFT)<.0001,"Bug inherited gravity");
            var saved=new CompoundTag();bug.saveWithoutId(saved);var restored=ModEntities.BUG.get().create(level);restored.load(saved);
            h.assertTrue(restored.patrolFacing()==1&&restored.monsterState().save().equals(bug.monsterState().save()),"Reload rerolled heading/stats/loot");
            h.assertTrue(Math.abs(restored.getY()-bug.getY())<.0001,"Reload applied flight lift twice");
            h.assertTrue(Math.abs(bug.getBoundingBox().minY-bug.getY())<.0001,"Hit box did not move with flying body");
            var lowSave=saved.copy();lowSave.remove("BugFlightLift");
            // Recreate the previously saved low altitude and check the one-time height correction.
            var positions=lowSave.getList("Pos",net.minecraft.nbt.Tag.TAG_DOUBLE);
            positions.set(1,net.minecraft.nbt.DoubleTag.valueOf(bug.getY()-MineBugEntity.FLIGHT_LIFT));
            var formerlyLow=ModEntities.BUG.get().create(level);formerlyLow.load(lowSave);
            h.assertTrue(Math.abs(formerlyLow.getY()-bug.getY())<.0001,"Previously spawned Bug remained too low");
            bug.setPos(start.add(0,MineBugEntity.FLIGHT_LIFT,0));bug.setPatrolFacing(1);player.setPos(start);
            healthBefore[0]=player.getHealth();
        });
        h.runAtTickTime(17,()->{
            h.assertTrue(bug.patrolFacing()==1&&bug.getX()>start.x,"Farmer overlap reversed/stopped patrol");
            // This test world uses MC health; the mine dimension uses the Stardew damage adapter.
            h.assertTrue(player.getHealth()<healthBefore[0],"Farmer overlap caused no contact damage");
            h.assertTrue(com.stardew.craft.monster.MonsterDamageSource.contact(bug).baseDamage()==8,"Wrong authoritative contact damage");
            player.setPos(start.add(0,0,4));
            level.setBlock(origin.offset(2,-2,-4),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(origin.offset(2,-1,-4),Blocks.WATER.defaultBlockState(),3);
            waterBug[0]=(MineBugEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"bug",start.add(0,0,-4),-90,20);
            bug.setPos(start.add(0,MineBugEntity.FLIGHT_LIFT,0));bug.setPatrolFacing(1);
            for(int z=-2;z<=2;z++)for(int y=0;y<3;y++)level.setBlock(origin.offset(2,y,z),Blocks.STONE.defaultBlockState(),3);
        });
        h.runAtTickTime(36,()->{
            h.assertTrue(bug.patrolFacing()==3&&bug.getX()<start.x+1,"Wall did not reverse Bug");
            h.assertTrue(Math.abs(bug.getZ()-start.z)<.0001,"Bug routed around wall");
            h.assertTrue(waterBug[0].patrolFacing()==3&&waterBug[0].getX()<start.x+1.5,"Source non-glider Bug crossed water");
            waterBug[0].discard();
            var box=bug.getBoundingBox().inflate(3);
            int beforeDrops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).stream().mapToInt(e->e.getItem().getCount()).sum();
            int expected=bug.monsterState().bornDrops().size();
            bug.hurt(level.damageSources().genericKill(),10000);
            int after=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).stream().mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(!bug.isAlive()&&after-beforeDrops==expected,"Bug born drops not materialized once");
            bug.die(level.damageSources().genericKill());
            h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).stream().mapToInt(e->e.getItem().getCount()).sum()==after,"Death duplicated loot");
            player.discard();h.succeed();
        });
    }
}
