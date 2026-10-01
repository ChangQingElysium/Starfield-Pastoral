package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MinePepperRexEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.monster.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
@GameTestHolder("stardewcraft_bug")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativePepperRexGameTests {
 private static MinePepperRexEntity spawn(GameTestHelper h){var r=(MinePepperRexEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(h.getLevel(),"pepper_rex",Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,5,8))),0,new MonsterSpawnContext(MonsterSpawnContext.Source.SKULL_CAVERN,135,false,null),e->{});r.setPersistenceRequired();r.setNoAi(true);r.setNoGravity(true);return r;}
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=25)
 public static void sourceStatsAndAttackSaveDoNotReroll(GameTestHelper h){var r=spawn(h);h.assertTrue(r.getHealth()==300&&r.monsterState().stats().getDamage()==15&&r.monsterState().stats().getResilience()==5,"Rex base stats changed");var b=r.behavior();b.tick(2001,true,true,1,r.getRandom());b.tick(500,true,true,1,r.getRandom());h.assertTrue(b.phase()==PepperRexBehavior.FIRE,"Rex did not prepare and fire");var tag=new CompoundTag();r.saveWithoutId(tag);var copy=ModEntities.PEPPER_REX.get().create(h.getLevel());copy.load(tag);h.assertTrue(copy.behavior().save().equals(b.save())&&copy.monsterState().bornDrops().equals(r.monsterState().bornDrops()),"Save rerolled Rex breath clock or birth loot");copy.discard();r.discard();h.succeed();}
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=25)
 public static void breathSurvivesOwnerDeathFadesAndExpires(GameTestHelper h){var r=spawn(h);var s=ModEntities.REX_BREATH.get().create(h.getLevel());s.launch(r,r.position().add(0,20,0),0);h.getLevel().addFreshEntity(s);h.assertTrue(!s.isPickable()&&!s.hurt(h.getLevel().damageSources().generic(),100),"Breath became a weapon target");r.discard();h.runAtTickTime(5,()->{h.assertTrue(!s.isRemoved()&&s.travelled()>=120&&s.travelled()<=180,"Breath disappeared with its parent or wrong source speed: "+s.travelled()+" removed="+s.isRemoved());h.assertTrue(s.opacity(0)<=1&&s.opacity(0)>0,"Breath fade broken");});h.runAtTickTime(11,()->{h.assertTrue(s.isRemoved(),"Breath exceeded source 256-pixel range");h.succeed();});}
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=25)
 public static void breathStopsAtWallWithoutLightingIt(GameTestHelper h){var r=spawn(h);var start=r.position().add(0,1,0);var wall=BlockPos.containing(start.add(1,0,0));for(int z=-1;z<=1;z++)h.getLevel().setBlock(wall.offset(0,0,z),Blocks.STONE.defaultBlockState(),3);var s=ModEntities.REX_BREATH.get().create(h.getLevel());s.launch(r,start,0);h.getLevel().addFreshEntity(s);h.runAtTickTime(6,()->{h.assertTrue(s.isRemoved(),"Breath crossed an impassable wall");h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"Breath changed the wall");r.discard();h.succeed();});}
}
