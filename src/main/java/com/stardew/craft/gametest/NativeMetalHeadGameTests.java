package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineMetalHeadEntity;
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
public final class NativeMetalHeadGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=35)
    public static void ordinaryMetalHeadWalksAndPersists(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,2,8));var at=Vec3.atBottomCenterOf(origin);
        for(int x=-4;x<=5;x++)for(int z=-3;z<=3;z++){level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<3;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        var metal=(MineMetalHeadEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"metal_head",at,180,new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,85,false,null),m->{});
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"MetalTest"));player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(at.add(3,0,0));level.addNewPlayer(player);
        h.runAtTickTime(15,()->{h.assertTrue(metal.getX()>at.x+.6,"Metal Head is not pursuing at source speed");var tag=new net.minecraft.nbt.CompoundTag();metal.saveWithoutId(tag);var copy=ModEntities.METAL_HEAD.get().create(level);copy.load(tag);h.assertTrue(copy.getHealth()==120&&copy.color()==0xffffff,"Metal Head lost constructor state");var copyTag=new net.minecraft.nbt.CompoundTag();copy.saveWithoutId(copyTag);h.assertTrue(copyTag.getCompound("GroundMovement").equals(tag.getCompound("GroundMovement")),"Reload lost chase/trajectory");player.discard();metal.discard();h.succeed();});
    }
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=10)
    public static void constructorUsesAreaNotDepthDamageScaling(GameTestHelper h){
        var level=h.getLevel();var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,2,8)));
        int[] floors={15,55,85,125},hp={40,80,120,40},colors={0xffffff,0x40e0d0,0xffffff,0xffffff};
        for(int i=0;i<4;i++){var metal=(MineMetalHeadEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"metal_head",at,180,new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,floors[i],false,null),m->{});h.assertTrue(metal.getHealth()==hp[i]&&metal.color()==colors[i],"Wrong MetalHead mine-area constructor");h.assertTrue(metal.monsterState().stats().getDamage()==15&&metal.monsterState().stats().getResilience()==8&&metal.monsterState().stats().getExperience()==6,"Wrong ordinary Metal Head stats");metal.discard();}
        var d=MonsterDefinitions.require(new net.minecraft.resources.ResourceLocation("stardewcraft:metal_head"));h.assertTrue(d.drops().stream().filter(x->x.item().equals("stardewcraft:copper_ore")).count()==2,"Collapsed independent copper rolls");h.assertTrue(com.stardew.craft.item.ModItems.SQUIRES_HELMET.get() instanceof com.stardew.craft.item.cosmetic.StardewHatItem,"Helmet missing its wearable item");
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"MetalLootTest"));var data=com.stardew.craft.player.PlayerDataManager.getPlayerData(player);int seed=(int)level.getServer().overworld().getSeed();int count=Math.floorMod(-seed,100);while(!MetalHeadLoot.hasHelmet(count,seed))count++;
        data.addMonsterKills("source:Metal Head",count);var metal=(MineMetalHeadEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"metal_head",at,180,new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,85,false,null),m->{});
        for(int roll=0;roll<2;roll++)h.assertTrue(MonsterExtraLoot.roll(metal,player,net.minecraft.util.RandomSource.create(1)).stream().anyMatch(s->s.is(com.stardew.craft.item.ModItems.SQUIRES_HELMET.get())),"Burglar reroll lost deterministic helmet");metal.discard();h.succeed();
    }
}
