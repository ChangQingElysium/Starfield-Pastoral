package com.stardew.craft.gametest;

import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.monster.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.*;
import java.util.List;
import java.util.UUID;

@GameTestHolder("stardewcraft_bug")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeOnlyMineGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=20)
    public static void unfinishedSpeciesLeaveNoSubstituteAndOldEntitiesAreRejected(GameTestHelper h) {
        var level=h.getLevel();var pos=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,2,8)));
        for(String id:List.of("royal_serpent", "skeleton_mage")) {
            h.assertTrue(!MineMonsterSpawnHandler.isImplemented(id),"Unfinished species advertised: "+id);
            h.assertTrue(MineMonsterSpawnHandler.spawnConfiguredMonster(level,id,pos,0,20)==null,"Vanilla substitute survived: "+id);
        }
        var skull=new MonsterSpawnContext(MonsterSpawnContext.Source.SKULL_CAVERN,130,false,null);
        var armored=MineMonsterSpawnHandler.spawnConfiguredMonster(level,"bug",pos,0,skull,m->{});
        h.assertTrue(armored instanceof com.stardew.craft.entity.monster.MineBugEntity bug&&bug.armored(),"Skull Cavern Bug did not become Armored Bug");armored.discard();
        for(String id:MineMonsterSpawnHandler.getSummonableMonsterIds()) {
            var mob=MineMonsterSpawnHandler.spawnConfiguredMonster(level,id,pos,0,20);
            h.assertTrue(mob instanceof StardewMonsterEntity,"Native species unavailable: "+id);
            h.assertTrue(!MineMonsterSpawnHandler.isRetiredMineMob(mob),"Native species marked for cleanup: "+id);
            mob.discard();
        }
        for(var type:List.of(EntityType.ENDERMITE,EntityType.SILVERFISH,EntityType.SPIDER,EntityType.DROWNED)) {
            var mob=type.create(level);
            h.assertTrue(MineMonsterSpawnHandler.isRetiredMineMob(mob),"Vanilla hostile escaped cleanup policy: "+type);
            var outsideEvent=new EntityJoinLevelEvent(mob,level,true);
            MineMonsterSpawnHandler.onEntityJoinLevel(outsideEvent);
            h.assertTrue(!outsideEvent.isCanceled(),"Other dimensions lost vanilla monsters");
        }
        var sheep=EntityType.SHEEP.create(level);
        h.assertTrue(!MineMonsterSpawnHandler.isRetiredMineMob(sheep),"Unrelated passive animal removed");
        sheep.addTag("sd_mob_shadow_brute");
        h.assertTrue(MineMonsterSpawnHandler.isRetiredMineMob(sheep),"Tagged passive stand-in escaped cleanup");
        h.succeed();
    }
}
