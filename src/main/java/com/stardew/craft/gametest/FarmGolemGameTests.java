package com.stardew.craft.gametest;

import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineRockGolemEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.monster.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("stardewcraft_farm_golem")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class FarmGolemGameTests {
    @GameTest(templateNamespace="stardewcraft_farm_golem",template="ring_utilities",timeoutTicks=10)
    public static void combatLevelsAndConstructorLootPersist(GameTestHelper h) {
        for (boolean iridium : new boolean[]{false,true}) for (int combat : new int[]{0,5,9,10}) {
            var type = iridium ? ModEntities.IRIDIUM_GOLEM.get() : ModEntities.WILDERNESS_GOLEM.get();
            var golem = type.create(h.getLevel());
            golem.setFarmCombatLevel(combat);
            golem.initialize(new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,85,false,null));
            h.assertTrue(golem.getHealth()==30+2*combat*combat+(iridium?400:0),"Farm health used mine floor scaling");
            h.assertTrue(golem.monsterState().sourceMaxHealth()==30,"Lost original source maximum");
            var stats=golem.monsterState().stats();
            h.assertTrue(stats.getDamage()==5+combat+(iridium?10:0) && stats.getExperience()==5+combat+(iridium?10:0)
                    && stats.getResilience()==1,"Incorrect farm combat stats");
            h.assertTrue(golem.getAttributeValue(Attributes.MOVEMENT_SPEED)==(iridium?.5:.25),"Incorrect initial speed");
            var saved=new CompoundTag();golem.saveWithoutId(saved);
            var copy=type.create(h.getLevel());copy.load(saved);
            h.assertTrue(copy.farmCombatLevel()==combat && copy.monsterState().save().equals(golem.monsterState().save()),"Reload rerolled farm state or drops");
            h.assertTrue(copy.visualVariant().equals(golem.visualVariant()),"Reload changed visual identity");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_farm_golem",template="ring_utilities",timeoutTicks=10)
    public static void bothSpeciesUseNativeSpawnPathAndSourceItems(GameTestHelper h) {
        for(String id:new String[]{"wilderness_golem","iridium_golem"}) {
            h.assertTrue(MineMonsterSpawnHandler.getSummonableMonsterIds().contains(id),"Missing summon command");
            var mob=(MineRockGolemEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(h.getLevel(),id,
                    Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,2,8))),0,
                    new MonsterSpawnContext(MonsterSpawnContext.Source.WORLD,1,false,null),m->{},
                    m->((MineRockGolemEntity)m).setFarmCombatLevel(5));
            h.assertTrue(mob!=null && mob.farmCombatLevel()==5 && mob.isFarmGolem(),"Spawn initialized before difficulty was assigned");
            h.assertTrue(!mob.getTags().contains("sd_mob_rock_golem") && mob.getBbHeight()>=1.56,"Identity or full spawn clearance lost");
            var d=MonsterDefinitions.require(new ResourceLocation("stardewcraft:"+id));
            h.assertTrue(d.drops().stream().filter(drop->drop.item().equals("stardewcraft:fiber")).count()==2,"Independent fiber rolls were merged");
            mob.discard();
        }
        for(String item:new String[]{"CarrotSeeds","SummerSquashSeeds","BroccoliSeeds","PowdermelonSeeds","273","386","527","SkillBook_0","SkillBook_4"})
            h.assertTrue(!MonsterSourceLoot.item(item,1).isEmpty(),"Missing implemented source loot: "+item);
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_farm_golem",template="ring_utilities",timeoutTicks=10)
    public static void iridiumAcceptedHitRerollsSpeedAndReloadKeepsIt(GameTestHelper h) {
        var golem=ModEntities.IRIDIUM_GOLEM.get().create(h.getLevel());
        golem.setFarmCombatLevel(10);
        golem.initialize(new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,1,false,null));
        for(int i=0;i<12;i++) {
            golem.invulnerableTime=0;
            float health=golem.getHealth();
            golem.hurt(h.getLevel().damageSources().generic(),2);
            h.assertTrue(golem.getHealth()<health,"Test hit did not damage golem");
            double speed=golem.getAttributeValue(Attributes.MOVEMENT_SPEED)*8;
            h.assertTrue(speed>=2 && speed<=6 && speed==Math.rint(speed),"Iridium speed outside source 2..6");
        }
        var saved=new CompoundTag();golem.saveWithoutId(saved);
        var copy=ModEntities.IRIDIUM_GOLEM.get().create(h.getLevel());copy.load(saved);
        h.assertTrue(copy.getAttributeValue(Attributes.MOVEMENT_SPEED)==golem.getAttributeValue(Attributes.MOVEMENT_SPEED),"Reload reset rolled speed");
        golem.setInvulnerable(true);
        double before=golem.getAttributeValue(Attributes.MOVEMENT_SPEED);
        golem.hurt(h.getLevel().damageSources().generic(),2);
        h.assertTrue(golem.getAttributeValue(Attributes.MOVEMENT_SPEED)==before,"Rejected hit rerolled speed");
        h.succeed();
    }
}
