package com.stardew.craft.gametest;

import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineSerpentEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.monster.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Explicit -PgameTestNamespaces=stardewcraft_monster_space; no client or broad suite needed. */
@GameTestHolder("stardewcraft_monster_space")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class MonsterSpaceGameTests {
    private static void room(GameTestHelper h) {
        for(int x=1;x<=15;x++)for(int y=0;y<=11;y++)for(int z=1;z<=15;z++)
            h.setBlock(new BlockPos(x,y,z),x==1||x==15||y==0||y==11||z==1||z==15?Blocks.STONE:Blocks.AIR);
    }
    private static MineSerpentEntity serpent(GameTestHelper h,int x,int y,int z) {
        return (MineSerpentEntity) MineMonsterSpawnHandler.spawnConfiguredMonster(h.getLevel(),"serpent",
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x,y,z))),0,
                new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,135,false,null),m->m.setPersistenceRequired());
    }
    private static void wingedRoute(GameTestHelper h,String id) {
        room(h);
        for(int y=1;y<=10;y++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(8,y,z),Blocks.STONE);
        var mob=(StardewMonsterEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(h.getLevel(),id,
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,2,4))),0,
                new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,id.equals("iridium_bat")?171:id.equals("lava_bat")?90:50,false,null),m->m.setPersistenceRequired());
        if(mob instanceof com.stardew.craft.entity.monster.MineBatEntity bat)bat.startPursuit();
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(12,6,4))));mob.setTarget(player);
        h.onEachTick(()->{if(!mob.isRemoved())h.assertTrue(h.getLevel().noCollision(mob,mob.getBoundingBox()),id+" entered wall/ceiling");});
        h.runAtTickTime(300,()->{
            h.assertTrue(!mob.noPhysics&&mob.getX()>h.absolutePos(new BlockPos(9,0,0)).getX(),id+" failed physical route: "+mob.position());
            h.assertTrue(mob.getY()>player.getY()-.8&&mob.position().distanceTo(player.position())<3,id+" failed altitude pursuit: "+mob.position());
            var tag=new net.minecraft.nbt.CompoundTag();mob.saveWithoutId(tag);
            var copy=(StardewMonsterEntity)mob.getType().create(h.getLevel());copy.load(tag);
            h.assertTrue(copy.monsterState().bornDrops().equals(mob.monsterState().bornDrops()),id+" rerolled saved drops");
            String key=id.equals("fly")?"FlyFlight":"BatFlight";
            var restored=new net.minecraft.nbt.CompoundTag();copy.saveWithoutId(restored);
            h.assertTrue(tag.getCompound(key).equals(restored.getCompound(key)),id+" lost XYZ inertia on reload");
            copy.discard();player.discard();
            h.runAtTickTime(320,()->{h.assertTrue(mob.getTarget()==null,id+" kept a removed target");mob.discard();h.succeed();});
        });
    }
    @GameTest(template="flight_room",timeoutTicks=340)
    public static void batRoutesAndClimbs(GameTestHelper h){wingedRoute(h,"bat");}
    @GameTest(template="flight_room",timeoutTicks=340)
    public static void frostBatRoutesAndClimbs(GameTestHelper h){wingedRoute(h,"frost_bat");}
    @GameTest(template="flight_room",timeoutTicks=340)
    public static void lavaBatRoutesAndClimbs(GameTestHelper h){wingedRoute(h,"lava_bat");}
    @GameTest(template="flight_room",timeoutTicks=340)
    public static void iridiumBatRoutesAndClimbs(GameTestHelper h){wingedRoute(h,"iridium_bat");}
    @GameTest(template="flight_room",timeoutTicks=340)
    public static void flyRoutesAndClimbs(GameTestHelper h){wingedRoute(h,"fly");}
    @GameTest(template="flight_room",timeoutTicks=80)
    public static void roostAnchorBreakWakesBatAndFreezeStopsFlight(GameTestHelper h){
        room(h);h.setBlock(new BlockPos(8,5,8),Blocks.STONE);
        var bat=(com.stardew.craft.entity.monster.MineBatEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(h.getLevel(),"bat",
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,1,8))),0,10);
        h.assertTrue(bat.phase()==bat.ROOST,"Bat did not find underside");Vec3 hanging=bat.position();
        h.runAtTickTime(10,()->{
            h.assertTrue(bat.position().distanceToSqr(hanging)<1e-9,"Unaware bat left roost");
            h.setBlock(new BlockPos(8,5,8),Blocks.AIR);
        });
        h.runAtTickTime(30,()->{
            h.assertTrue(bat.phase()!=bat.ROOST,"Broken anchor did not wake bat");
            bat.setNoAi(true);Vec3 frozen=bat.position();
            h.runAtTickTime(40,()->{h.assertTrue(bat.position().distanceToSqr(frozen)<1e-9,"Frozen bat moved");bat.discard();h.succeed();});
        });
    }
    @GameTest(template="flight_room",timeoutTicks=50)
    public static void flyEmergesGraduallyAndStopsBelowLowCeiling(GameTestHelper h){
        room(h);for(int x=5;x<=11;x++)for(int z=5;z<=11;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
        var fly=(com.stardew.craft.entity.monster.MineFlyEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(h.getLevel(),"fly",
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,1,8))),0,20);
        double birth=fly.getY();
        h.runAtTickTime(5,()->{h.assertTrue(fly.getY()>birth&&fly.getY()<birth+.5,"Fly teleported instead of emerging");});
        h.runAtTickTime(18,()->{
            h.assertTrue(fly.steering().spawnRemaining()>=0,"Fly ended source emergence early");
            h.assertTrue(fly.getBoundingBox().maxY<h.absolutePos(new BlockPos(0,2,0)).getY(),"Emerging fly entered ceiling");
            fly.setNoAi(true);Vec3 frozen=fly.position();
            h.runAtTickTime(28,()->{h.assertTrue(fly.position().distanceToSqr(frozen)<1e-10,"Frozen fly moved");fly.discard();h.succeed();});
        });
    }
    @GameTest(template="flight_room",timeoutTicks=40)
    public static void collisionAndSavePreserveFlightStateAndLoot(GameTestHelper h) {
        NativeSerpentGameTests.physicalFlightStopsAtWallsAndSaveKeepsMotionAndDrops(h);
    }
    @GameTest(template="flight_room",timeoutTicks=20)
    public static void sourceStatsAndFinalDeathStillApply(GameTestHelper h) {
        NativeSerpentGameTests.serpentDeathIsFinalAndSourceBonusStatsRemainSeparate(h);
    }
    @GameTest(template="flight_room",timeoutTicks=340)
    public static void serpentClimbsAndRoutesAroundSolidWall(GameTestHelper h) {
        room(h);
        for(int y=1;y<=10;y++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(8,y,z),Blocks.STONE);
        var mob=serpent(h,4,2,4);var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(12,6,4))));mob.setTarget(player);
        h.onEachTick(()->{if(!mob.isRemoved())h.assertTrue(h.getLevel().noCollision(mob,mob.getBoundingBox()),"Flight body entered wall or ceiling");});
        h.runAtTickTime(300,()->{
            h.assertTrue(mob.getX()>h.absolutePos(new BlockPos(9,0,0)).getX(),"Did not route around wall: "+mob.position());
            h.assertTrue(mob.getY()>player.getY()-.8,"Did not climb to actual player height: "+mob.position());
            h.assertTrue(mob.position().distanceTo(player.position())<3,"Failed to approach target after route");
            player.discard();
            h.runAtTickTime(320,()->{
                h.assertTrue(mob.getTarget()==null,"Removed target stayed locked");mob.discard();h.succeed();
            });
        });
    }
    @GameTest(template="flight_room",timeoutTicks=180)
    public static void idleMovesAndNoAiFreezeStopsCustomFlight(GameTestHelper h) {
        room(h);var mob=serpent(h,8,4,8);Vec3 origin=mob.position();double[] travelled={0};Vec3[] last={origin};
        h.onEachTick(()->{travelled[0]+=mob.position().distanceTo(last[0]);last[0]=mob.position();});
        h.runAtTickTime(100,()->{
            h.assertTrue(travelled[0]>1,"Idle flight never moved");mob.setNoAi(true);Vec3 frozen=mob.position();
            h.runAtTickTime(120,()->{
                h.assertTrue(mob.position().distanceToSqr(frozen)<1e-10,"Frozen custom AI continued flying");
                mob.setNoAi(false);
            });
        });
        h.runAtTickTime(160,()->{
            h.assertTrue(mob.position().distanceTo(origin)<9,"Idle flight escaped local anchor");
            h.assertTrue(h.getLevel().noCollision(mob,mob.getBoundingBox()),"Idle flight clipped room");mob.discard();h.succeed();
        });
    }
    @GameTest(template="flight_room",timeoutTicks=20)
    public static void sightAndProjectileSweepUseActualCeiling(GameTestHelper h) {
        room(h);var mob=serpent(h,6,2,6);mob.setNoAi(true);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,7,6))));
        for(int x=3;x<=10;x++)for(int z=3;z<=10;z++)h.setBlock(new BlockPos(x,5,z),Blocks.STONE);
        h.assertTrue(!MineMonsterSight.sees(mob,player,13),"Sight ignored the floor between vertically stacked bodies");
        player.setPos(mob.position().add(3,0,0));
        h.assertTrue(MineMonsterSight.sees(mob,player,13),"Clear same-level sight failed");
        var shot=ModEntities.SQUID_FIREBALL.get().create(h.getLevel());shot.setPos(mob.position());
        Vec3 velocity=new Vec3(.1,5,.15);
        var hit=MonsterProjectileMovement.step(shot,velocity,false,false,0,p->false);
        h.assertTrue(hit!=null&&hit.normal().y<0,"Swept projectile missed overhead wall");
        h.assertTrue(shot.getBoundingBox().maxY<=h.absolutePos(new BlockPos(0,5,0)).getY()+1e-6,"Projectile tunnelled through ceiling");
        Vec3 reflected=MonsterSpace.reflect(velocity,hit.normal());
        h.assertTrue(reflected.y<0&&Math.abs(reflected.length()-velocity.length())<1e-8,"Ceiling bounce erased vertical speed");
        shot.discard();mob.discard();player.discard();h.succeed();
    }
}
