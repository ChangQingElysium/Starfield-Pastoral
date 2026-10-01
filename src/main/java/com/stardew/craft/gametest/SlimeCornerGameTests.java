package com.stardew.craft.gametest;

import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.monster.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("stardewcraft_slime_corner")
@PrefixGameTestTemplate(false)
public final class SlimeCornerGameTests {
    private static void verify(GameTestHelper h,String id) {
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<7;y++)
            h.setBlock(new BlockPos(x,y,z),y==0||x==8||z==8?Blocks.STONE:Blocks.AIR);
        var mob=(StardewMonsterEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(h.getLevel(),id,
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5,1,5))),0,
                new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,90,false,null),m->m.setPersistenceRequired());
        mob.setNoGravity(true);mob.setNoAi(true);
        h.runAtTickTime(2,()->{
            var box=mob.getBoundingBox();mob.setPos(mob.position());
            h.assertTrue(box.equals(mob.getBoundingBox()),id+" movement rebuilt a different box");
            for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1}) {
                mob.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(sx>0?5:11,1,sz>0?5:11))));
                mob.setYRot(0);
                for(int n=0;n<12;n++)mob.move(MoverType.SELF,new Vec3(.6*sx,0,.6*sz));
                h.assertTrue(h.getLevel().noCollision(mob,mob.getBoundingBox()),id+" entered corner during repeated movement");
                for(int yaw=0;yaw<360;yaw+=15){mob.setYRot(yaw);h.assertTrue(h.getLevel().noCollision(mob,mob.getBoundingBox()),id+" turned into corner at "+yaw);}
            }
            mob.setNoGravity(false);mob.setOnGround(true);mob.setNoAi(false);mob.knockback(2,-1,-1);
        });
        h.onEachTick(()->h.assertTrue(h.getLevel().noCollision(mob,mob.getBoundingBox()),id+" became embedded during knockback/AI"));
        h.runAtTickTime(45,()->{
            var before=mob.position();mob.move(MoverType.SELF,new Vec3(-1.5,0,-1.5));
            h.assertTrue(mob.position().distanceTo(before)>1,id+" could not leave the corner");
            mob.discard();h.succeed();
        });
    }
    @GameTest(template="flight_room",timeoutTicks=65)
    public static void greenSlime(GameTestHelper h){verify(h,"green_slime");}
    @GameTest(template="flight_room",timeoutTicks=65)
    public static void frostJelly(GameTestHelper h){verify(h,"frost_jelly");}
    @GameTest(template="flight_room",timeoutTicks=65)
    public static void sludge(GameTestHelper h){verify(h,"sludge");}
    @GameTest(template="flight_room",timeoutTicks=65)
    public static void bigSlime(GameTestHelper h){verify(h,"big_slime");}
}
