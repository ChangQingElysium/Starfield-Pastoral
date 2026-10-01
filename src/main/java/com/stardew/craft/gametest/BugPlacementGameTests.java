package com.stardew.craft.gametest;

import com.stardew.craft.entity.monster.MineBugEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("stardewcraft_bug_placement")
@PrefixGameTestTemplate(false)
public final class BugPlacementGameTests {
    private static void room(GameTestHelper h) {
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<6;y++)
            h.setBlock(new BlockPos(x,y,z),y==0?Blocks.STONE:Blocks.AIR);
    }
    private static MineBugEntity spawn(GameTestHelper h,String id,int x,int z) {
        return (MineBugEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(h.getLevel(),id,
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x,1,z))),-90,20);
    }
    private static void clippedSpawn(GameTestHelper h,String id) {
        room(h);for(int z=2;z<13;z++)for(int y=1;y<4;y++)h.setBlock(new BlockPos(6,y,z),Blocks.STONE);
        var bug=spawn(h,id,5,6);
        h.runAtTickTime(3,()->h.assertTrue(!bug.isRemoved()&&h.getLevel().noCollision(bug,bug.getBoundingBox()),"One-tile spawn left wide bug inside side wall: removed="+bug.isRemoved()+" hp="+bug.getHealth()+" pos="+bug.position()));
        h.runAtTickTime(18,()->{h.assertTrue(bug.getX()<h.absolutePos(new BlockPos(5,1,6)).getX(),"Bug did not patrol away from spawn wall");bug.discard();h.succeed();});
    }
    @GameTest(template="flight_room",timeoutTicks=35)
    public static void ordinaryBirthBesideWall(GameTestHelper h){clippedSpawn(h,"bug");}
    @GameTest(template="flight_room",timeoutTicks=35)
    public static void armoredBirthBesideWall(GameTestHelper h){clippedSpawn(h,"armored_bug");}
    @GameTest(template="flight_room",timeoutTicks=55)
    public static void trappedPatrolWaitsWithoutFlippingThenResumes(GameTestHelper h){
        room(h);var bug=spawn(h,"bug",6,6);bug.setInvulnerable(true);
        h.runAtTickTime(2,()->{
            // Terrain added after birth obstructs the body on both sides.
            for(int y=1;y<4;y++){h.setBlock(new BlockPos(5,y,6),Blocks.STONE);h.setBlock(new BlockPos(7,y,6),Blocks.STONE);}
            bug.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,1,6))).add(0,MineBugEntity.FLIGHT_LIFT,0));
            int facing=bug.patrolFacing();
            h.runAtTickTime(3,()->h.assertTrue(bug.patrolFacing()==facing,"Trapped bug flipped in place"));
            h.runAtTickTime(14,()->{h.assertTrue(bug.patrolFacing()==facing,"Trapped bug keeps alternating");for(int y=1;y<4;y++){h.setBlock(new BlockPos(5,y,6),Blocks.AIR);h.setBlock(new BlockPos(7,y,6),Blocks.AIR);}});
            h.runAtTickTime(35,()->{h.assertTrue(bug.getX()>h.absolutePos(new BlockPos(7,1,6)).getX(),"Bug did not resume after opening corridor: "+bug.position()+" alive="+bug.isAlive());bug.discard();h.succeed();});
        });
    }
    @GameTest(template="flight_room",timeoutTicks=15)
    public static void impossibleCellCleansUpWithoutLoot(GameTestHelper h){
        room(h);
        for(int y=1;y<4;y++){h.setBlock(new BlockPos(5,y,6),Blocks.STONE);h.setBlock(new BlockPos(7,y,6),Blocks.STONE);}
        var bug=spawn(h,"bug",6,6);
        h.runAtTickTime(3,()->{
            h.assertTrue(bug.isRemoved()&&bug.monsterState().life()==com.stardew.craft.monster.MonsterState.Life.CLEANUP,"Invalid birth was not retired as cleanup");
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bug.getBoundingBox().inflate(2)).isEmpty(),"Invalid birth paid death loot");
            h.succeed();
        });
    }
    @GameTest(template="flight_room",timeoutTicks=120)
    public static void sourcePatrolWaterFarmerAndSave(GameTestHelper h) throws Exception {
        NativeBugGameTests.patrolWallFarmerReloadAndSourceLoot(h);
    }
    @GameTest(template="flight_room",timeoutTicks=45)
    public static void armoredImmunityAndKnockback(GameTestHelper h){
        NativeArmoredBugGameTests.onlyBugKillerMeleePenetratesArmorAndNoHitPushesIt(h);
    }
}
