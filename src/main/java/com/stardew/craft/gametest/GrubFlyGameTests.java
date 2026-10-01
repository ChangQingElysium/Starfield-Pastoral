package com.stardew.craft.gametest;

import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.*;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.monster.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("stardewcraft_bug")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class GrubFlyGameTests {
    @GameTest(templateNamespace="stardewcraft_monster_space",template="flight_room",timeoutTicks=25)
    public static void physicalSpawnMovesGrubOutOfSolidWall(GameTestHelper h){
        for(int x=1;x<15;x++)for(int z=1;z<15;z++)for(int y=0;y<5;y++)
            h.setBlock(new BlockPos(x,y,z),y==0?Blocks.STONE:Blocks.AIR);
        for(int y=1;y<4;y++)h.setBlock(new BlockPos(8,y,8),Blocks.STONE);
        var requested=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,1,8)));
        var grub=(MineGrubEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(h.getLevel(),"grub",requested,0,20);
        h.assertTrue(grub!=null,"A nearby valid spawn was not found");
        h.assertTrue(grub.position().distanceToSqr(requested)>0,"Grub stayed embedded in the requested wall");
        h.assertTrue(h.getLevel().noCollision(grub,grub.getBoundingBox()),"Relocated grub still intersects terrain");
        grub.discard();h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=230)
    public static void metamorphosisIsNotADeathAndPupaRejectsDamage(GameTestHelper h){
        var level=h.getLevel();var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,2,8)));
        var grub=(MineGrubEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"grub",at,0,20);
        // Keep a non-targetable observer nearby: concurrent tests remove their
        // FakePlayers independently, otherwise vanilla distance despawn races this test.
        var observer=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"GrubObserver"));observer.setGameMode(net.minecraft.world.level.GameType.CREATIVE);observer.setPos(at.add(0,0,7));level.addNewPlayer(observer);
        grub.setPersistenceRequired();grub.setNoGravity(true);grub.setHealth(20);
        h.runAtTickTime(20,()->{
            h.assertTrue(grub.phase()==GrubLifecycle.MOBILE,"Healthy grub started pupating without damage");
            h.assertTrue(grub.hurt(level.damageSources().generic(),11),"First nonlethal hit was rejected");
            h.assertTrue(grub.getHealth()==9,"First hit must leave the grub above its source threshold");
        });
        h.runAtTickTime(45,()->{
            h.assertTrue(grub.phase()==GrubLifecycle.MOBILE,"Grub at 9 HP began pupating");
            h.assertTrue(grub.hurt(level.damageSources().generic(),1),"Threshold-crossing hit was rejected");
            h.assertTrue(grub.getHealth()==8,"Second hit did not reach the 8 HP metamorphosis threshold");
        });
        h.runAtTickTime(65,()->h.assertTrue(grub.phase()==GrubLifecycle.RETREAT,"Wounded grub did not enter retreat"));
        h.runAtTickTime(105,()->{
            h.assertTrue(grub.phase()==GrubLifecycle.PUPA,"Grub did not become a pupa after wounded retreat");
            float hp=grub.getHealth();grub.hurt(level.damageSources().generic(),999);
            h.assertTrue(grub.getHealth()==hp,"Pupa took damage");
            var saved=new net.minecraft.nbt.CompoundTag();grub.saveWithoutId(saved);
            var restored=ModEntities.GRUB.get().create(level);restored.load(saved);
            h.assertTrue(restored.lifecycle().save().equals(grub.lifecycle().save()),"Reload restarted metamorphosis");
        });
        h.runAtTickTime(205,()->{
            h.assertTrue(grub.isRemoved(),"Old grub was not retired");
            h.assertTrue(grub.monsterState().life()==MonsterState.Life.TRANSFORMING,"Metamorphosis recorded as death");
            for(var channel:MonsterState.Settlement.values())h.assertTrue(!grub.claimSettlement(channel),"Transformation allowed reward channel "+channel);
            var flies=level.getEntitiesOfClass(MineFlyEntity.class,grub.getBoundingBox().inflate(20));
            h.assertTrue(flies.size()==1,"Expected exactly one native fly, got "+flies.size());
            var fly=flies.getFirst();h.assertTrue(fly.monsterState().context().equals(grub.monsterState().context().offspring()),"Lost floor context");
            h.assertTrue(fly.getHealth()==22,"Wrong base Fly health");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,grub.getBoundingBox().inflate(6)).isEmpty(),"Metamorphosis dropped loot");
            h.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class,grub.getBoundingBox().inflate(6)).isEmpty(),"Metamorphosis dropped XP");
            fly.discard();observer.discard();h.succeed();
        });
    }
}
