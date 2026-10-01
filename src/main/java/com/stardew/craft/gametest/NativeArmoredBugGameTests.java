package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineBugEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.ModItems;
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
public final class NativeArmoredBugGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=40)
    public static void onlyBugKillerMeleePenetratesArmorAndNoHitPushesIt(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));
        for(int x=-5;x<=5;x++)for(int z=-3;z<=3;z++){level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<4;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        var bug=(MineBugEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"bug",Vec3.atBottomCenterOf(pos),-90,new MonsterSpawnContext(MonsterSpawnContext.Source.SKULL_CAVERN,140,false,null),m->{});bug.setPersistenceRequired();
        h.assertTrue(bug.armored()&&bug.getHealth()==150&&bug.monsterState().stats().getDamage()==16,"Skull branch did not apply post-base armor attributes");
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ArmorTest"));player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(Vec3.atBottomCenterOf(pos.offset(0,0,3)));level.addNewPlayer(player);
        var weapon=new net.minecraft.world.item.ItemStack(ModItems.RUSTY_SWORD.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,weapon);
        h.assertTrue(!bug.hurt(level.damageSources().playerAttack(player),20)&&bug.getHealth()==150,"Unenchanted sword pierced armor");
        var enchantment=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getHolderOrThrow(StardewEnchantments.BUG_KILLER);com.stardew.craft.port.PortItemStacks.enchant(weapon, enchantment,1);
        h.assertTrue(!bug.hurt(level.damageSources().explosion(null,player),20)&&bug.getHealth()==150,"Bomb pierced armor while player held Bug Killer");
        var stick=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);com.stardew.craft.port.PortItemStacks.enchant(stick, enchantment,1);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stick);
        h.assertTrue(!bug.hurt(level.damageSources().playerAttack(player),20),"Enchant alone made a non-weapon pierce armor");player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,weapon);
        h.assertTrue(bug.hurt(level.damageSources().playerAttack(player),20)&&bug.getHealth()<150,"Bug Killer melee failed to damage armor");
        bug.knockback(2,1,0);var saved=new CompoundTag();bug.saveWithoutId(saved);h.assertTrue(saved.getDouble("BugSlideX")==0&&saved.getDouble("BugSlideZ")==0,"Armored Bug accepted knockback despite slipperiness -1");
        var copy=ModEntities.ARMORED_BUG.get().create(level);copy.load(saved);h.assertTrue(copy.armored()&&copy.getHealth()==bug.getHealth()&&copy.patrolFacing()==bug.patrolFacing()&&copy.getY()==bug.getY(),"Reload changed variant/flight lift/health");copy.discard();
        var zero=new net.minecraft.world.level.levelgen.LegacyRandomSource(0){@Override public double nextDouble(){return 0;}};
        var extra=MonsterExtraLoot.roll(bug,player,zero);h.assertTrue(extra.size()==1&&com.stardew.craft.port.PortJava.getFirst(extra).is(ModItems.BUG_STEAK.get()),"Armored Bug extra steak rule was lost");
        var at=bug.position();h.runAtTickTime(10,()->{h.assertTrue(bug.getX()>at.x+.5&&Math.abs(bug.getZ()-at.z)<.001,"Armored Bug chased or stopped after damage");bug.discard();player.discard();h.succeed();});
    }
}
