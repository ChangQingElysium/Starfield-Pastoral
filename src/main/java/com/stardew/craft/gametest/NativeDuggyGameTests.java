package com.stardew.craft.gametest;

import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineDuggyEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.monster.MonsterDamageSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("stardewcraft_bug")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeDuggyGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=90)
    public static void hiddenSoilAmbushDamageWindowAndReload(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,2,8));var start=Vec3.atBottomCenterOf(origin);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<3;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        var duggy=(MineDuggyEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"duggy",start,180,20);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"DuggyTest"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(start.add(1,0,0));level.addNewPlayer(player);
        h.runAtTickTime(4,()->{
            h.assertTrue(duggy.isInvisible()&&!duggy.isPickable(),"Duggy emerged through stone / remained targetable");
            float hp=duggy.getHealth();duggy.hurt(level.damageSources().generic(),999);h.assertTrue(hp==duggy.getHealth(),"Hidden Duggy took damage");
            level.setBlock(origin.offset(1,-1,0),com.stardew.craft.block.ModBlocks.MINE_EARTH_SOIL.get().defaultBlockState(),3);
        });
        h.runAtTickTime(12,()->{
            h.assertTrue(!duggy.isInvisible(),"Duggy did not emerge on mine soil");
            h.assertTrue(Math.abs(duggy.getX()-player.getX())<.01,"Duggy did not move to target tile");
            var before=duggy.position();duggy.knockback(3,1,0);h.assertTrue(duggy.getDeltaMovement().lengthSqr()==0&&duggy.position().equals(before),"Duggy accepted knockback");
            var tag=new net.minecraft.nbt.CompoundTag();duggy.saveWithoutId(tag);var restored=ModEntities.DUGGY.get().create(level);restored.load(tag);
            h.assertTrue(restored.lifecycle().save().equals(duggy.lifecycle().save()),"Duggy reload restarted source frame sequence");
        });
        h.runAtTickTime(20,()->{
            h.assertTrue(MonsterDamageSource.contact(duggy).baseDamage()==8,"Active Duggy does not use source constant damage 8");
            player.setPos(start.add(8,0,0));
        });
        h.runAtTickTime(55,()->{
            h.assertTrue(duggy.isInvisible()&&duggy.monsterState().stats().getDamage()==0,"Duggy failed to retract/reset damage");
            player.discard();duggy.discard();h.succeed();
        });
    }
}
