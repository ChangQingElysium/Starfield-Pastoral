package com.stardew.craft.gametest;

import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.RockCrabEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("stardewcraft_crab")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeRockCrabGameTests {
    @GameTest(templateNamespace="stardewcraft_crab",template="ring_utilities",timeoutTicks=160)
    public static void shellToolsExplosionReloadAndFlee(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,2,8));
        for(int x=-7;x<=12;x++)for(int z=-7;z<=7;z++) {
            level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<4;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        var crab=(RockCrabEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"rock_crab",Vec3.atBottomCenterOf(origin),0,20);
        var save=new CompoundTag();crab.saveWithoutId(save);save.putBoolean("CrabWaiter",true);crab.load(save);
        h.assertTrue(crab.getHealth()==30&&crab.monsterState().stats().getResilience()==1,"Legacy stats overwrote source definition");
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"CrabTest"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var day=com.stardew.craft.time.StardewTimeManager.get();
        com.stardew.craft.player.PlayerDataManager.getPlayerData(player).setDailyLuckForDate(0,((day.getCurrentYear()*4)+day.getCurrentSeason())*28+day.getCurrentDay()-1);
        player.setPos(Vec3.atBottomCenterOf(origin.offset(2,0,0)));level.addNewPlayer(player);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
        h.runAtTickTime(8,()->{
            h.assertTrue(crab.disguised()&&crab.position().distanceToSqr(Vec3.atBottomCenterOf(origin))<.001,"Waiter revealed itself without a pick");
            h.assertTrue(!crab.hurt(level.damageSources().generic(),100)&&crab.getHealth()==30,"Camouflaged crab took weapon damage");
        });
        for(int i=0;i<5;i++) {
            int n=i;
            h.runAtTickTime(10+i*10,()->{
                crab.hurt(level.damageSources().playerAttack(player),100);
                h.assertTrue(crab.shellHealth()==4-n&&crab.getHealth()==30,"Pick did not remove exactly one shell health without body damage");
                if(n<4)crab.hurt(level.damageSources().playerAttack(player),100);
                if(n<4)h.assertTrue(crab.shellHealth()==4-n,"Duplicate swing removed two shell points");
                if(n==2) {
                    var tag=new CompoundTag();crab.saveWithoutId(tag);var restored=ModEntities.ROCK_CRAB.get().create(level);restored.load(tag);
                    h.assertTrue(restored.shellHealth()==2&&!restored.waiter()&&restored.monsterState().save().equals(crab.monsterState().save()),"Reload rerolled shell or loot");
                }
            });
        }
        final double[] fleeDistance={0};
        h.runAtTickTime(52,()->{
            h.assertTrue(crab.shellGone()&&!crab.waiter(),"Fifth hit did not break shell");
            crab.setPos(Vec3.atBottomCenterOf(origin));player.setPos(Vec3.atBottomCenterOf(origin.offset(2,0,0)));fleeDistance[0]=crab.distanceToSqr(player);
        });
        h.runAtTickTime(90,()->{
            h.assertTrue(crab.distanceToSqr(player)>fleeDistance[0]+1,"Bare crab approached player or froze");
            var bombCrab=(RockCrabEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"rock_crab",Vec3.atBottomCenterOf(origin.offset(5,0,3)),0,20);
            bombCrab.hurt(level.damageSources().explosion(null,player),5);
            h.assertTrue(bombCrab.shellGone()&&bombCrab.getHealth()<30,"Explosion was blocked or did not break shell");
            var bounds=bombCrab.getBoundingBox().inflate(2);
            int before=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds).stream().mapToInt(e->e.getItem().getCount()).sum();
            int expected=bombCrab.monsterState().bornDrops().size();
            bombCrab.hurt(level.damageSources().genericKill(),10000);
            int after=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds).stream().mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(after-before==expected,"Source born drops not materialized once");
            bombCrab.die(level.damageSources().genericKill());
            h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds).stream().mapToInt(e->e.getItem().getCount()).sum()==after,"Duplicate death rerolled loot");
            crab.discard();player.discard();h.succeed();
        });
    }
}
