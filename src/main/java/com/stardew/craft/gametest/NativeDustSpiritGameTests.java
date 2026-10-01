package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineDustSpiritEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
@GameTestHolder("stardewcraft_bug")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeDustSpiritGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=110)
    public static void hopsWithoutTargetThenSeesAndCharges(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,2,8));var start=Vec3.atBottomCenterOf(origin);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<4;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        var dust=(MineDustSpiritEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"dust_sprite",start,180,50);
        // Other tests have remote FakePlayers; isolate this no-target test from vanilla 128-block despawn.
        dust.setPersistenceRequired();
        h.runAtTickTime(3,()->{
            h.assertTrue(dust.motion().offset()<0&&dust.getY()>start.y,"Dust spirit did not hop without target: tick="+dust.tickCount+", y="+dust.getY()+", start="+start.y+", offset="+dust.motion().offset()+", removed="+dust.isRemoved()+", hp="+dust.getHealth());
            h.assertTrue(dust.getScale()>=.75&&dust.getScale()<=1,"Source random scale outside .75..1");
            var tag=new net.minecraft.nbt.CompoundTag();dust.saveWithoutId(tag);var restored=ModEntities.DUST_SPIRIT.get().create(level);restored.load(tag);h.assertTrue(restored.motion().save().equals(dust.motion().save()),"Reload restarted hop/AI state");
        });
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"DustTest"));
        h.runAtTickTime(10,()->{player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(start.add(3,0,0));level.addNewPlayer(player);});
        h.runAtTickTime(45,()->{h.assertTrue(dust.motion().seen()&&dust.motion().running()&&dust.motion().charging(),"Sight/escape-path/charge state chain failed");h.assertTrue(dust.monsterState().stats().getExperience()==2,"Wrong source XP");player.discard();dust.discard();h.succeed();});
    }
}
