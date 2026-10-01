package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.*;
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
public final class NativeBigSlimeGameTests {
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=50)
 public static void areaStatsHeldLootAndSplitChildrenSurviveReload(GameTestHelper h){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++){level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<4;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
  var big=(MineBigSlimeEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"big_slime",Vec3.atBottomCenterOf(pos),0,new MonsterSpawnContext(MonsterSpawnContext.Source.SKULL_CAVERN,140,false,null),m->{});big.setPersistenceRequired();
  h.assertTrue(big.getHealth()==240&&big.monsterState().stats().getDamage()==15&&big.monsterState().stats().getExperience()==21,"Area 121 multipliers changed");h.assertTrue(!big.getTags().contains("sd_mob_slime"),"Big Slime incorrectly counts as small-slime eradication");
  var cake=MonsterSourceLoot.item("221",1);h.assertTrue(!cake.isEmpty(),"Source cake ID unresolved");big.heldItem(cake);var extras=MonsterExtraLoot.roll(big,null,net.minecraft.util.RandomSource.create(55));h.assertTrue(extras.size()==1&&net.minecraft.world.item.ItemStack.isSameItemSameComponents(extras.getFirst(),cake),"Swallowed cake missing from extra loot");
  var saved=new CompoundTag();big.saveWithoutId(saved);var copy=ModEntities.BIG_SLIME.get().create(level);copy.load(saved);h.assertTrue(copy.color()==big.color()&&copy.getHealth()==240&&net.minecraft.world.item.ItemStack.isSameItemSameComponents(copy.heldItem(),cake),"Reload lost color/stats/held item");copy.discard();
  var before=level.getEntitiesOfClass(GreenSlimeEntity.class,big.getBoundingBox().inflate(3));big.getRandom().setSeed(0);big.setHealth(0);big.die(level.damageSources().generic());
  var children=level.getEntitiesOfClass(GreenSlimeEntity.class,big.getBoundingBox().inflate(3));children.removeAll(before);h.assertTrue(children.size()>=2&&children.size()<=4,"Accepted death did not insert source split children immediately");
  for(var child:children){child.setPersistenceRequired();h.assertTrue(child.monsterState().context().floor()==140&&child.monsterState().context().source()==MonsterSpawnContext.Source.SKULL_CAVERN&&child.firstGeneration()&&child.growth()>=.70F&&child.growth()<=.84F,"Split child became breeding baby or inherited the wrong context");}
  int count=children.size();big.die(level.damageSources().generic());h.assertTrue(level.getEntitiesOfClass(GreenSlimeEntity.class,big.getBoundingBox().inflate(3)).size()-before.size()==count,"Duplicate death split again");
  h.runAtTickTime(10,()->{for(var child:children){h.assertTrue(child.growth()>=.70F&&child.growth()<=.84F,"Breeding tick overwrote split scale");var t=new CompoundTag();child.saveWithoutId(t);var restored=ModEntities.SLUDGE.get().create(level);restored.load(t);h.assertTrue(restored.growth()==child.growth()&&restored.firstGeneration(),"Child reload lost adult scale/first generation");restored.discard();child.discard();}big.discard();h.succeed();});
 }
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=20)
 public static void normalAreasUseConstructorOrderAndIndependentSlimeRolls(GameTestHelper h){
  var level=h.getLevel();var pos=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,3,8)));int[] floors={10,45,85};int[] hp={60,120,180},damage={5,5,10},xp={7,14,21};
  for(int i=0;i<3;i++){var mob=(MineBigSlimeEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"big_slime",pos,0,new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,floors[i],false,null),m->{});h.assertTrue(mob.getHealth()==hp[i]&&mob.monsterState().stats().getDamage()==damage[i]&&mob.monsterState().stats().getExperience()==xp[i],"Wrong ordinary-area Big Slime stats");mob.discard();}
  var d=MonsterDefinitions.require(new net.minecraft.resources.ResourceLocation("stardewcraft:big_slime"));h.assertTrue(d.drops().size()==4&&d.drops().get(0).item().equals("stardewcraft:slime_item")&&d.drops().get(0).chance()==.99&&d.drops().get(1).chance()==.9&&d.drops().get(2).chance()==.4,"Source independent slime rolls changed");h.succeed();
 }
}
