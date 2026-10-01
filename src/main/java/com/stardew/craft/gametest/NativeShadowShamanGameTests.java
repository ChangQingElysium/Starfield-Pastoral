package com.stardew.craft.gametest;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineShadowShamanEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.monster.*;
import com.stardew.craft.effect.ModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
@GameTestHolder("stardewcraft_bug")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeShadowShamanGameTests {
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=25)
    public static void healingSelectsLowestSourceRatioAndCapsSourceHealth(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));var at=Vec3.atBottomCenterOf(pos);
        var c=new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,95,false,null);
        var caster=(MineShadowShamanEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"shadow_shaman",at,180,c,m->{});
        var metal=(StardewMonsterEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"metal_head",at.add(2,0,0),0,c,m->{});
        var brute=(StardewMonsterEntity)MineMonsterSpawnHandler.spawnConfiguredMonster(level,"shadow_brute",at.add(3,0,0),0,c,m->{});
        var p=player(level,at.add(5,0,0));
        try{
            h.assertTrue(caster.getHealth()==80&&caster.monsterState().stats().getDamage()==17&&caster.monsterState().stats().getExperience()==15,"Shaman source stats mismatch");
            metal.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);metal.setHealth(10);brute.setHealth(100);
            h.assertTrue(caster.healLowest()==metal&&metal.getHealth()==40,"Healing used Minecraft maximum or chose by absolute HP");
            h.assertTrue(caster.healLowest()==brute&&brute.getHealth()==160,"Healing did not add source 60");
            var tag=new net.minecraft.nbt.CompoundTag();caster.saveWithoutId(tag);var clock=new ShamanSpellClock();clock.step(true,true,false,false,true,1);for(int i=0;i<95;i++)clock.step(true,true,false,false,true,0);tag.put("ShamanSpell",clock.save());caster.load(tag);
            h.assertTrue(caster.casting(),"Saved cast did not synchronize");var out=new net.minecraft.nbt.CompoundTag();caster.saveWithoutId(out);h.assertTrue(out.getCompound("ShamanSpell").equals(clock.save()),"Shaman clock reload drift");
            h.succeed();
        }finally{p.discard();caster.discard();metal.discard();brute.discard();}
    }
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=25)
    public static void jinxIgnoresDamageFramesButRespectsRavioli(GameTestHelper h){
        var level=h.getLevel();var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,3,8)));var p=player(level,at);var curse=ModEntities.SHAMAN_CURSE.get().create(level);curse.setPos(at);
        try{
            p.invulnerableTime=100;float health=p.getHealth();
            h.assertTrue(curse.hitPlayer(p)&&curse.isRemoved(),"I-frames incorrectly blocked status-only curse");h.assertTrue(p.getHealth()==health,"Curse directly damaged HP");
            h.assertTrue(p.hasEffect(ModMobEffects.JINXED.get())&&p.getEffect(ModMobEffects.JINXED.get()).getDuration()==160,"Jinx is not eight seconds");
            p.removeEffect(ModMobEffects.JINXED.get());p.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModMobEffects.SQUID_INK_RAVIOLI.get(),3600));
            var second=ModEntities.SHAMAN_CURSE.get().create(level);second.setPos(at);h.assertTrue(!second.hitPlayer(p)&&!second.isRemoved()&&!p.hasEffect(ModMobEffects.JINXED.get()),"Ravioli should let the curse pass through");second.discard();h.succeed();
        }finally{p.removeAllEffects();p.discard();curse.discard();}
    }
    @GameTest(templateNamespace="stardewcraft_bug",template="ring_utilities",timeoutTicks=25)
    public static void fourBouncesThenFifthCollisionDestroys(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,5,8));
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=0;y<=1;y++)level.setBlock(pos.offset(x,y,z),Blocks.STONE.defaultBlockState(),3);
        var curse=ModEntities.SHAMAN_CURSE.get().create(level);curse.setPos(Vec3.atCenterOf(pos));curse.setDeltaMovement(Vec3.ZERO);
        curse.tick();curse.tick();h.assertTrue(curse.bouncesLeft()==4,"Projectile collided before 100ms grace");curse.tick();h.assertTrue(curse.bouncesLeft()==1&&!curse.isRemoved(),"Source should consume three bounces in three substeps");curse.tick();
        h.assertTrue(curse.isRemoved()&&curse.bouncesLeft()==0&&curse.sourceFrames()==11,"Wrong four-bounce/fifth-impact boundary");h.succeed();
    }
    private static net.minecraftforge.common.util.FakePlayer player(net.minecraft.server.level.ServerLevel level,Vec3 pos){var p=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ShamanTest"));p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(pos);level.addNewPlayer(p);return p;}
}
