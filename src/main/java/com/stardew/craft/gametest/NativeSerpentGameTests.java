package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineSerpentEntity;
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
public final class NativeSerpentGameTests {
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=40)
 public static void physicalFlightStopsAtWallsAndSaveKeepsMotionAndDrops(GameTestHelper h){var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));for(int y=0;y<4;y++)for(int z=-2;z<=2;z++)level.setBlock(pos.offset(2,y,z),Blocks.STONE.defaultBlockState(),3);var s=(MineSerpentEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"serpent",Vec3.atBottomCenterOf(pos),0,new MonsterSpawnContext(MonsterSpawnContext.Source.SKULL_CAVERN,135,false,null),e->{});s.setPersistenceRequired();h.assertTrue(s.getHealth()==150&&s.monsterState().stats().getDamage()==23&&!s.noPhysics,"Serpent stats or physical flight missing");s.steering().knockback(8,0);s.steering().hit();var tag=new CompoundTag();s.saveWithoutId(tag);var copy=ModEntities.SERPENT.get().create(level);copy.load(tag);h.assertTrue(s.steering().save().equals(copy.steering().save())&&s.monsterState().bornDrops().equals(copy.monsterState().bornDrops()),"Save rerolled inertia/hit turn lock/loot");copy.discard();double startY=s.getY();h.runAtTickTime(12,()->{h.assertTrue(s.getBoundingBox().maxX<=pos.getX()+2.001&&Math.abs(s.getY()-startY)<.001,"Serpent crossed a wall or fell under vanilla gravity");s.discard();h.succeed();});}
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=20)
 public static void serpentDeathIsFinalAndSourceBonusStatsRemainSeparate(GameTestHelper h){var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,5,8));var s=(MineSerpentEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"serpent",Vec3.atBottomCenterOf(pos),0,new MonsterSpawnContext(MonsterSpawnContext.Source.SKULL_CAVERN,171,true,null),e->{});h.assertTrue(s.monsterState().sourceMaxHealth()==150&&s.getHealth()>=150&&s.getHealth()<300,"Bottom-cleared progression overwrote base source health");var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(s.position().add(3,0,0));s.hurt(level.damageSources().playerAttack(player),10000);h.assertTrue(s.isDeadOrDying()&&s.monsterState().life()==MonsterState.Life.DEAD,"Serpent did not finalize death");h.assertTrue(s.deathSpin()>=3&&s.deathSpin()<=4,"Death spin outside source range");s.discard();player.discard();h.succeed();}
}
