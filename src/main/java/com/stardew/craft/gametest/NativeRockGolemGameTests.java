package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineRockGolemEntity;
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
public final class NativeRockGolemGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=50)
    public static void dormantHeapAwakensAndKeepsProgressOnReload(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,2,8));var at=Vec3.atBottomCenterOf(origin);
        for(int x=-5;x<=5;x++)for(int z=-4;z<=4;z++){level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<4;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        var golem=(MineRockGolemEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"rock_golem",at,180,new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,35,false,null),m->{});
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"GolemTest"));
        h.runAtTickTime(3,()->{h.assertTrue(!golem.awakening().seen()&&golem.getBbHeight()<1.2,"Dormant state missing");player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(at.add(2.5,0,0));level.addNewPlayer(player);});
        h.runAtTickTime(7,()->{h.assertTrue(golem.awakening().seen()&&!golem.awakening().awake(),"Skipped source emergence frames");var tag=new net.minecraft.nbt.CompoundTag();golem.saveWithoutId(tag);var copy=ModEntities.ROCK_GOLEM.get().create(level);copy.load(tag);h.assertTrue(copy.awakening().save().equals(golem.awakening().save()),"Reload restarted emergence");});
        h.runAtTickTime(24,()->{h.assertTrue(golem.awakening().awake()&&golem.awakening().walking(),"Golem never finished eight source frames");h.assertTrue(golem.getX()>at.x,"Awakened golem did not pursue");h.assertTrue(golem.getBbHeight()>1.4,"Upright collision height did not grow");player.discard();golem.discard();h.succeed();});
    }
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=10)
    public static void floorConstructorModifiersAndModDrops(GameTestHelper h){
        var level=h.getLevel();var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,2,8)));
        int[] floors={35,55,85},hp={45,78,112},damage={5,7,10};
        for(int i=0;i<3;i++){
            var golem=(MineRockGolemEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"rock_golem",at,180,new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,floors[i],false,null),m->{});
            h.assertTrue(golem.getHealth()==hp[i]&&golem.monsterState().stats().getDamage()==damage[i],"Wrong RockGolem mine constructor at "+floors[i]);h.assertTrue(golem.monsterState().stats().getResilience()==5&&golem.monsterState().stats().getExperience()==5,"Tier changed armor/experience");golem.discard();
        }
        var d=MonsterDefinitions.require(new net.minecraft.resources.ResourceLocation("stardewcraft:rock_golem"));h.assertTrue(d.drops().stream().noneMatch(drop->drop.item().startsWith("minecraft:")),"Golem drops vanilla stand-ins");h.succeed();
    }
}
