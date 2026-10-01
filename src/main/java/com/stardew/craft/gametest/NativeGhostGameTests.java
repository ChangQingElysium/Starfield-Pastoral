package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineGhostEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
@GameTestHolder("stardewcraft_bug")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeGhostGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=60)
    public static void phasesThroughWallAndPreservesSpeciesOnLoad(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,2,8));var at=Vec3.atBottomCenterOf(origin);
        for(int x=-3;x<=5;x++)for(int z=-2;z<=2;z++){level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<4;y++)level.setBlock(origin.offset(x,y,z),x==1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
        var ghost=(MineGhostEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"ghost",at,180,50);ghost.steering().knockback(32,0);
        h.runAtTickTime(6,()->{
            h.assertTrue(ghost.getX()>at.x+1.5,"Ghost was blocked by a source-passable wall");h.assertTrue(ghost.noPhysics&&ghost.isNoGravity(),"Ghost lost phasing");
            var tag=new net.minecraft.nbt.CompoundTag();ghost.saveWithoutId(tag);var copy=ModEntities.GHOST.get().create(level);copy.load(tag);h.assertTrue(copy.steering().save().equals(ghost.steering().save()),"Ghost reload reset velocity");
            var carbon=(MineGhostEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"carbon_ghost",at,180,130);h.assertTrue(carbon.carbon()&&carbon.getHealth()==190&&carbon.monsterState().stats().getDamage()==25,"Carbon stats/identity wrong");
            h.assertTrue(carbon.getTags().contains("sd_mob_ghost"),"Carbon lost shared ectoplasm branch");carbon.discard();ghost.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=10)
    public static void ectoplasmPickupClosesTheTeamDropBranch(GameTestHelper h){
        var level=h.getLevel();var world=com.stardew.craft.specialorder.SpecialOrderWorldData.get(level);boolean before=world.sharedSpecialDropFlags().contains("ectoplasmDrop");
        var a=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"EctoA"));
        var b=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"EctoB"));
        var newMail=level.getServer().getPlayerList().getPlayers().stream().filter(p->!com.stardew.craft.player.PlayerDataManager.getPlayerData(p).hasMailFlagForTomorrow("ectoplasmDrop")).toList();
        try{
            com.stardew.craft.specialorder.SpecialOrderManager.markSpecialDropFlag(a,"ectoplasmDrop");
            h.assertTrue(com.stardew.craft.specialorder.SpecialOrderManager.hasSpecialDropFlag(b,"ectoplasmDrop"),"Other player still eligible after a team pickup");
            var saved=world.save(new net.minecraft.nbt.CompoundTag());h.assertTrue(saved.getList("SharedSpecialDropFlags",8).contains(net.minecraft.nbt.StringTag.valueOf("ectoplasmDrop")),"Shared flag is not persisted");
        }finally{if(!before){world.sharedSpecialDropFlags().remove("ectoplasmDrop");world.setDirty();}for(var p:newMail)com.stardew.craft.player.PlayerDataManager.getPlayerData(p).removeMailFlagForTomorrow("ectoplasmDrop");}
        h.succeed();
    }

}
