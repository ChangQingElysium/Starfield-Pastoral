package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineSquidKidEntity;
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
public final class NativeSquidKidGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=25)
    public static void oneHealthFloaterBouncesAgainstGroundObjectsAndSaves(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));var at=Vec3.atBottomCenterOf(pos);
        for(int x=-4;x<=4;x++)for(int z=-2;z<=2;z++){level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<4;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        level.setBlock(pos.offset(1,0,0),Blocks.STONE.defaultBlockState(),3);
        var squid=(MineSquidKidEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"squid_kid",at,0,new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,95,false,null),m->{});
        h.assertTrue(squid.getHealth()==1&&squid.monsterState().stats().getDamage()==18&&squid.monsterState().stats().getResilience()==2,"Wrong Squid Kid source stats");
        var t=new net.minecraft.nbt.CompoundTag();squid.saveWithoutId(t);var brain=new SquidKidBehavior();brain.knockback(32,0);t.put("SquidBehavior",brain.save());squid.load(t);
        h.runAtTickTime(8,()->{h.assertTrue(squid.getX()<at.x+.55,"Grounded glider went through a floor obstacle");h.assertTrue(squid.getY()>=at.y+28./64&&squid.getY()<=at.y+43./64,"Squid sank into floor or used wrong lift");var saved=new net.minecraft.nbt.CompoundTag();squid.saveWithoutId(saved);var copy=ModEntities.SQUID_KID.get().create(level);copy.load(saved);var out=new net.minecraft.nbt.CompoundTag();copy.saveWithoutId(out);h.assertTrue(saved.getCompound("SquidBehavior").equals(out.getCompound("SquidBehavior")),"Squid trajectory/cooldown reload changed");copy.discard();squid.discard();h.succeed();});
    }
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=25)
    public static void fireballHasThreeBouncesAndStaysAtItsLaunchHeight(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,5,8));for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=0;y<=1;y++)level.setBlock(pos.offset(x,y,z),Blocks.STONE.defaultBlockState(),3);
        var ball=ModEntities.SQUID_FIREBALL.get().create(level);ball.setPos(Vec3.atCenterOf(pos));ball.setDeltaMovement(new Vec3(.375,0,0));double y=ball.getY();ball.tick();ball.tick();h.assertTrue(ball.bouncesLeft()==3,"Fireball lost 100ms grace");ball.tick();h.assertTrue(ball.bouncesLeft()==0&&!ball.isRemoved(),"Fireball did not reflect three times");ball.tick();h.assertTrue(ball.isRemoved()&&ball.sourceFrames()==10&&ball.getY()==y,"Fireball has gravity or wrong fourth-impact boundary");h.succeed();
    }
}
