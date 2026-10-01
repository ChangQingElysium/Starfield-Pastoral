package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.RockCrabEntity;
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
public final class NativeCrabVariantGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=100)
    public static void iridiumWaitsThenFivePickHitsExposeItAndSaveAllState(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++){level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<4;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        var crab=(RockCrabEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"iridium_crab",Vec3.atBottomCenterOf(pos),0,new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,155,false,null),m->{});crab.setPersistenceRequired();
        h.assertTrue(crab.waiter()&&crab.getHealth()==240&&crab.monsterState().stats().getResilience()==3,"Wrong Iridium Crab initialization");
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"IridiumCrabTest"));player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(Vec3.atBottomCenterOf(pos.offset(1,0,0)));level.addNewPlayer(player);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
        h.runAtTickTime(6,()->h.assertTrue(crab.disguised()&&crab.position().distanceToSqr(Vec3.atBottomCenterOf(pos))<.001,"Iridium disguise activated without a pick"));
        for(int i=0;i<5;i++){int remaining=4-i;h.runAtTickTime(10+i*10,()->{crab.pickShell(player);h.assertTrue(crab.shellHealth()==remaining&&crab.getHealth()==240,"Pick tier damaged body or skipped shell points");});}
        h.runAtTickTime(55,()->{h.assertTrue(crab.shellGone()&&!crab.waiter(),"Fifth pick did not expose body");var saved=new CompoundTag();crab.saveWithoutId(saved);var copy=ModEntities.IRIDIUM_CRAB.get().create(level);copy.load(saved);var restored=new CompoundTag();copy.saveWithoutId(restored);h.assertTrue(copy.shellGone()&&copy.shellHealth()==0&&copy.variant().equals("iridium_crab")&&saved.getCompound("CrabMovement").equals(restored.getCompound("CrabMovement")),"Reload changed variant/shell/movement");copy.discard();crab.discard();player.discard();h.succeed();});
    }
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=20)
    public static void lavaBombExposesWithoutConsumingPickDurabilityAndUsesSourceLoot(GameTestHelper h){
        var level=h.getLevel();var pos=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,3,8)));
        var crab=(RockCrabEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"lava_crab",pos,0,new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,85,false,null),m->{});
        h.assertTrue(crab.getHealth()==120&&crab.monsterState().stats().getDamage()==15&&crab.monsterState().stats().getResilience()==3,"Lava Crab used ordinary crab stats");
        crab.hurt(level.damageSources().explosion(null,null),10);h.assertTrue(crab.shellGone()&&crab.shellHealth()==5&&!crab.waiter()&&crab.getHealth()<120,"Bomb decremented shell counter or was blocked by disguise");
        var d=MonsterDefinitions.require(new net.minecraft.resources.ResourceLocation("stardewcraft:lava_crab"));h.assertTrue(d.drops().get(0).item().equals("stardewcraft:crab")&&d.drops().get(0).chance()==.25&&d.drops().get(1).item().equals("stardewcraft:bomb_item")&&d.drops().get(1).chance()==.4,"Lava source drop table changed");
        var iridium=MonsterDefinitions.require(new net.minecraft.resources.ResourceLocation("stardewcraft:iridium_crab"));h.assertTrue(iridium.drops().size()==4&&iridium.drops().subList(1,4).stream().allMatch(x->x.item().equals("stardewcraft:iridium_ore")&&x.chance()==.5),"Iridium independent ore rolls were collapsed");crab.discard();h.succeed();
    }
}
