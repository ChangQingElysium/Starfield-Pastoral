package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineMummyEntity;
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
public final class NativeMummyGameTests {
 private static MineMummyEntity spawn(GameTestHelper h,int offset,boolean bottom){var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8+offset,3,8));for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<4;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}var m=(MineMummyEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"mummy",Vec3.atBottomCenterOf(pos),0,new MonsterSpawnContext(MonsterSpawnContext.Source.SKULL_CAVERN,150,bottom,null),e->{});m.setPersistenceRequired();return m;}
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=240)
 public static void collapseIsNotDeathAndRevivalKeepsOriginalBornLoot(GameTestHelper h){var m=spawn(h,0,true);var drops=m.monsterState().bornDrops();m.hurt(h.getLevel().damageSources().generic(),100000);h.assertTrue(m.collapsed()&&m.isAlive()&&m.getHealth()==260&&m.monsterState().life()==MonsterState.Life.DOWNED&&m.monsterState().stats().getDamage()==0,"Lethal hit emitted death or used scaled health for revival");h.assertTrue(!m.claimSettlement(MonsterState.Settlement.DROPS_AND_PROGRESS)&&!m.claimSettlement(MonsterState.Settlement.WEAPON_REWARDS),"Collapsed mummy can award kills/drops");h.assertTrue(!m.hurt(h.getLevel().damageSources().generic(),100000),"Ordinary hit kills pile");
  var saved=new CompoundTag();m.saveWithoutId(saved);var copy=ModEntities.MUMMY.get().create(h.getLevel());copy.load(saved);h.assertTrue(copy.collapsed()&&copy.reviveRemaining()==10000&&copy.monsterState().life()==MonsterState.Life.DOWNED,"Reload revived mummy early");copy.discard();
  h.runAtTickTime(195,()->h.assertTrue(m.phase()==MummyLifecycle.DOWNED&&m.monsterState().stats().getDamage()==0&&m.reviveRemaining()>0,"Ten-second timer started before crumble ended"));
  h.runAtTickTime(210,()->h.assertTrue(m.phase()==MummyLifecycle.REVIVE&&m.monsterState().stats().getDamage()>=30&&m.monsterState().life()==MonsterState.Life.ALIVE,"Contact damage was delayed until revival animation ended"));
  h.runAtTickTime(220,()->{h.assertTrue(m.phase()==MummyLifecycle.WALK&&m.monsterState().bornDrops().equals(drops)&&m.getHealth()==260,"Revival rerolled stats or loot");m.discard();h.succeed();});
 }
 @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=30)
 public static void standingBombOnlyCollapsesButDownedBombFinishesAndCrusaderNeedsMelee(GameTestHelper h){var level=h.getLevel();var m=spawn(h,0,false);m.hurt(level.damageSources().explosion(null,null),100000);h.assertTrue(m.collapsed()&&m.isAlive(),"Standing bomb skipped collapse");m.hurt(level.damageSources().explosion(null,null),0);h.assertTrue(m.isDeadOrDying()&&m.monsterState().life()==MonsterState.Life.DEAD,"Downed bomb did not finish independently of input damage");
  // A plain GameTest player exercises melee qualification without the absent
  // Stardew world required by a ServerPlayer's book/progression sync.
  var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(m.position().add(0,0,3));
  var enchant=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getHolderOrThrow(com.stardew.craft.enchantment.StardewEnchantments.CRUSADER);
  var sword=new net.minecraft.world.item.ItemStack(com.stardew.craft.item.ModItems.RUSTY_SWORD.get());sword.enchant(enchant,1);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,sword);var crusader=spawn(h,2,false);crusader.setHealth(1);crusader.hurt(level.damageSources().playerAttack(player),20);h.assertTrue(crusader.isDeadOrDying()&&crusader.monsterState().life()==MonsterState.Life.DEAD,"Crusader lethal melee collapsed instead of killed");
  var stick=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);stick.enchant(enchant,1);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stick);var ordinary=spawn(h,-2,false);ordinary.setHealth(1);ordinary.hurt(level.damageSources().playerAttack(player),20);h.assertTrue(ordinary.collapsed()&&ordinary.isAlive(),"Non-melee enchanted item permanently killed mummy");player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,sword);h.assertTrue(!ordinary.hurt(level.damageSources().playerAttack(player),20)&&ordinary.isAlive(),"Crusader melee killed already collapsed pile");
  m.discard();crusader.discard();ordinary.discard();player.discard();h.succeed();
 }
}
