package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineSkeletonEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("stardewcraft_bug")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeSkeletonGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=80)
    public static void sourceSightThrowAndReload(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,2,8));var at=Vec3.atBottomCenterOf(origin);
        for(int x=-6;x<=6;x++)for(int z=-4;z<=4;z++){level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<4;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        var skeleton=(MineSkeletonEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"skeleton",at,180,75);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"BoneTest"));
        h.runAtTickTime(3,()->{
            h.assertTrue(!skeleton.spotted()&&skeleton.position().distanceToSqr(at)<.001,"Unaware skeleton wandered");
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(at.add(2.5,0,1.5));level.addNewPlayer(player);
        });
        h.runAtTickTime(5,()->{
            h.assertTrue(skeleton.spotted(),"Skeleton failed to notice source-visible player");skeleton.throwClock().begin();
            var tag=new net.minecraft.nbt.CompoundTag();skeleton.saveWithoutId(tag);var copy=ModEntities.SKELETON.get().create(level);copy.load(tag);h.assertTrue(copy.throwClock().save().equals(skeleton.throwClock().save())&&copy.spotted(),"Reload forgot throw/awareness");
            h.assertTrue(skeleton.getHealth()==140&&skeleton.monsterState().stats().getDamage()==10,"Source stats wrong");
        });
        h.runAtTickTime(17,()->{h.assertTrue(skeleton.releaseTime(0)<.2,"Source throw did not release its bone");var aim=player.position().subtract(skeleton.position()).multiply(1,0,1).normalize();h.assertTrue(skeleton.getLookAngle().multiply(1,0,1).normalize().dot(aim)>.99,"3D throw turned away from its diagonal target");player.discard();skeleton.discard();h.succeed();});
    }
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=15)
    public static void projectileGraceAndMeleeInterception(GameTestHelper h){
        var level=h.getLevel();var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,3,8)));
        for(int x=-3;x<=4;x++)for(int y=-2;y<=2;y++)for(int z=-3;z<=3;z++)level.setBlock(BlockPos.containing(at).offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        var bone=ModEntities.SKELETON_BONE.get().create(level);bone.setPos(at);bone.setDeltaMovement(new Vec3(.375,0,0));level.addFreshEntity(bone);
        h.runAtTickTime(2,()->{h.assertTrue(!bone.collisionReady()&&!bone.isRemoved(),"Projectile lost source 100ms grace");h.assertTrue(Math.abs(bone.getX()-at.x-.75)<.01,"Wrong source speed");});
        h.runAtTickTime(3,()->{
            h.assertTrue(bone.collisionReady(),"Projectile grace never ends");
            var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ParryBone"));player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));player.setPos(at.add(0,-.5,0));
            com.stardew.craft.event.MineBarrelBreakHandler.breakInVolume(player,new AABB(at.add(-3,-3,-3),at.add(3,3,3)),at,v->true);
            h.assertTrue(bone.isRemoved(),"Authored melee volume did not intercept bone");h.succeed();
        });
    }
}
