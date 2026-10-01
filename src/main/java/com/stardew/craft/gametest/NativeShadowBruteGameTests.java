package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineShadowBruteEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.monster.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
@GameTestHolder("stardewcraft_bug")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeShadowBruteGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=35)
    public static void sourcePursuitPausesForStunAndSurvivesSave(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,2,8));var at=Vec3.atBottomCenterOf(origin);
        for(int x=-4;x<=5;x++)for(int z=-3;z<=3;z++){level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<4;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        var brute=(MineShadowBruteEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"shadow_brute",at,180,new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,95,false,null),m->{});
        h.assertTrue(brute.getHealth()==160&&brute.monsterState().stats().getDamage()==18&&brute.monsterState().stats().getResilience()==2&&brute.monsterState().stats().getExperience()==15,"Wrong source Shadow Brute stats");
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"BruteTest"));player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(at.add(3.5,0,0));level.addNewPlayer(player);double[] stopped={0};
        h.runAtTickTime(10,()->{h.assertTrue(brute.getX()>at.x+.7,"Brute did not use speed 3 pursuit");stopped[0]=brute.getX();brute.stunFor(240);});
        h.runAtTickTime(13,()->{h.assertTrue(brute.getX()==stopped[0],"Brute ignored source stun");var tag=new net.minecraft.nbt.CompoundTag();brute.saveWithoutId(tag);var copy=ModEntities.SHADOW_BRUTE.get().create(level);copy.load(tag);var out=new net.minecraft.nbt.CompoundTag();copy.saveWithoutId(out);h.assertTrue(tag.getCompound("GroundMovement").equals(out.getCompound("GroundMovement"))&&tag.getInt("ShadowStun")==out.getInt("ShadowStun"),"Reload lost pursuit/stun state");});
        h.runAtTickTime(24,()->{h.assertTrue(brute.getX()>stopped[0],"Brute never resumed pursuit");player.discard();brute.discard();h.succeed();});
    }
}
