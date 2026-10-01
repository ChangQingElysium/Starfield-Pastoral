package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;import com.stardew.craft.entity.monster.*;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.monster.*;
import com.stardew.craft.shop.MonsterSlayerGoalRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.gametest.*;
@GameTestHolder("stardewcraft_bug")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeMonsterSettlementGameTests {
 private static StardewMonsterEntity spawn(GameTestHelper h,String id){var m=(StardewMonsterEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(h.getLevel(),id,Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,12,8))),0,new MonsterSpawnContext(MonsterSpawnContext.Source.WORLD,135,false,null),e->{});m.setPersistenceRequired();m.setNoAi(true);m.setNoGravity(true);return m;}
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=20)
 public static void postHitSettlementWaitsForAcceptedOrCancelledDeath(GameTestHelper h){
  var killed=spawn(h,"pepper_rex");var cancelled=spawn(h,"pepper_rex");var seen=new java.util.ArrayList<MonsterState.Life>();
  java.util.function.Consumer<LivingDamageEvent.Post> post=e->{if(e.getEntity()==killed||e.getEntity()==cancelled){var m=(StardewMonsterEntity)e.getEntity();h.assertTrue(m.monsterState().life()==MonsterState.Life.ALIVE,"Death unexpectedly precedes damage Post");m.afterMortality(()->seen.add(m.monsterState().life()));}};
  java.util.function.Consumer<LivingDeathEvent> cancel=e->{if(e.getEntity()==cancelled){e.setCanceled(true);cancelled.setHealth(1);}};
  MinecraftForge.EVENT_BUS.addListener(post);MinecraftForge.EVENT_BUS.addListener(cancel);
  try{killed.hurt(h.getLevel().damageSources().generic(),10000);cancelled.hurt(h.getLevel().damageSources().generic(),10000);h.assertTrue(seen.equals(java.util.List.of(MonsterState.Life.DEAD,MonsterState.Life.ALIVE)),"Reward callback ran before mortality settled: "+seen);h.assertTrue(killed.claimSettlement(MonsterState.Settlement.WEAPON_REWARDS)&&!killed.claimSettlement(MonsterState.Settlement.WEAPON_REWARDS)&&!cancelled.claimSettlement(MonsterState.Settlement.WEAPON_REWARDS),"Duplicate or cancelled kill reward accepted");}finally{MinecraftForge.EVENT_BUS.unregister(post);MinecraftForge.EVENT_BUS.unregister(cancel);killed.discard();cancelled.discard();}h.succeed();
 }
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=20)
 public static void downedMummyCleanupNeverBecomesAKill(GameTestHelper h){var m=(MineMummyEntity)spawn(h,"mummy");m.hurt(h.getLevel().damageSources().generic(),10000);h.assertTrue(m.collapsed(),"Mummy failed to collapse");MonsterFactory.cleanup(m);h.assertTrue(m.isRemoved()&&m.monsterState().life()==MonsterState.Life.CLEANUP&&!m.claimSettlement(MonsterState.Settlement.DROPS_AND_PROGRESS),"Downed mummy survived floor cleanup or paid a kill");h.succeed();}
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=25)
 public static void nativeWeaponRecoveryExpiresAndFreezePausesAI(GameTestHelper h){var m=spawn(h,"pepper_rex");m.setNoAi(false);com.stardew.craft.combat.skill.YetiFreezeTracker.apply(m,h.getLevel().getGameTime(),6);h.assertTrue(m.isNoAi(),"Native custom AI did not freeze");m.sourceWeaponRecovery(true);var player=PortGameTests.makeMockPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);h.assertTrue(!m.hurt(h.getLevel().damageSources().playerAttack(player),1),"Dagger's 150ms recovery accepted a second hit");h.runAtTickTime(8,()->{h.assertTrue(!m.sourceHitRecoveryActive()&&!m.isNoAi(),"Recovery/freeze did not expire");h.assertTrue(m.hurt(h.getLevel().damageSources().playerAttack(player),1),"Vanilla hurt timer blocked expired source recovery");m.discard();player.discard();h.succeed();});}
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=20)
 public static void sourceSlayerGroupsIncludeBugsAndNewSkullMonsters(GameTestHelper h){h.assertTrue(MonsterSlayerGoalRegistry.getGoalKeysForTag("sd_mob_bug").contains("Insects")&&MonsterSlayerGoalRegistry.getGoalKeysForTag("sd_mob_shadow_shaman").contains("Shadows"),"Native tags missing source slayer mapping");h.assertTrue(MonsterSlayerGoalRegistry.getGoalKeysForTag("sd_mob_big_slime").isEmpty()&&MonsterSlayerGoalRegistry.getGoalKeysForTag("sd_mob_ghost").isEmpty(),"Non-source slayer goals remain");h.assertTrue(MonsterSlayerGoalRegistry.getGoal("Mummies").requiredKills()==100&&MonsterSlayerGoalRegistry.getGoal("Serpents").requiredKills()==250&&MonsterSlayerGoalRegistry.getGoal("Dinos").requiredKills()==50,"Skull slayer counts changed");h.assertTrue(MonsterSlayerGoalRegistry.getGoal("Skeletons").hasReward()&&MonsterSlayerGoalRegistry.getGoal("Duggy").hasReward(),"Existing hat rewards missing");h.succeed();}
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=25)
 public static void physicalFlyersStopAtWalls(GameTestHelper h){
  var fly=(MineFlyEntity)spawn(h,"fly");fly.setNoAi(false);fly.steering().knockback(12,0);fly.addTag("sd_focused_on_farmers");
  var bat=(MineBatEntity)spawn(h,"bat");h.assertTrue(!fly.noPhysics&&!bat.noPhysics,"Physical flyers unexpectedly phase through blocks");
  var wall=BlockPos.containing(fly.position().add(2,0,0));for(int y=-2;y<=3;y++)for(int z=-2;z<=2;z++)h.getLevel().setBlock(wall.offset(0,y,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
  h.runAtTickTime(9,()->{h.assertTrue(fly.getBoundingBox().maxX<=wall.getX()+.001,"Fly crossed a solid wall");fly.discard();bat.discard();h.succeed();});
 }
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=20)
 public static void normalWeaponAppliedFrameArmsSourceRecovery(GameTestHelper h){var m=spawn(h,"pepper_rex");var p=PortGameTests.makeMockPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(com.stardew.craft.item.ModItems.RUSTY_SWORD.get()));p.setPos(m.position().add(0,0,2));p.attack(m);h.assertTrue(m.sourceHitRecoveryActive(),"The normal applied weapon frame did not arm source recovery");float hp=m.getHealth();h.assertTrue(!m.hurt(h.getLevel().damageSources().playerAttack(p),100)&&m.getHealth()==hp,"A stronger hit bypassed source recovery through vanilla delta damage");h.runAtTickTime(7,()->{h.assertTrue(!m.sourceHitRecoveryActive(),"Source sword recovery exceeded 225ms rounded to server ticks");m.discard();p.discard();h.succeed();});}
}
