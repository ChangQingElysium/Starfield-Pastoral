package com.stardew.craft.gametest;

import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.bomb.StardewBombEntity;
import com.stardew.craft.entity.monster.MineMummyEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.item.bomb.BombType;
import com.stardew.craft.monster.MonsterFactory;
import com.stardew.craft.monster.MonsterSpawnContext;
import com.stardew.craft.monster.MonsterState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Repeated explosions must not resurrect terminal mummy state or repeat its settlement. */
@GameTestHolder("stardewcraft_mummy_bombs")
@PrefixGameTestTemplate(false)
public final class MummyBombRegressionGameTests {
    private static MineMummyEntity spawn(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            level.setBlock(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 4; y++) level.setBlock(pos.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        var mummy = (MineMummyEntity) MineMonsterSpawnHandler.spawnConfiguredMonster(level, "mummy",
                Vec3.atBottomCenterOf(pos), 0,
                new MonsterSpawnContext(MonsterSpawnContext.Source.SKULL_CAVERN, 150, false, null), entity -> {});
        mummy.setPersistenceRequired();
        mummy.setNoAi(true);
        mummy.setNoGravity(true);
        return mummy;
    }

    @GameTest(templateNamespace = "stardewcraft_mummy_bombs", template = "ring_utilities")
    public static void repeatedExplosionDamageCannotReviveOrResettleCorpse(GameTestHelper helper) {
        var mummy = spawn(helper);
        int[] deaths = {0};
        Consumer<LivingDeathEvent> onDeath = event -> { if (event.getEntity() == mummy) deaths[0]++; };
        NeoForge.EVENT_BUS.addListener(onDeath);
        try {
            var explosion = helper.getLevel().damageSources().explosion(null, null);
            mummy.hurt(explosion, 100000);
            helper.assertTrue(mummy.collapsed() && mummy.isAlive()
                    && mummy.monsterState().life() == MonsterState.Life.DOWNED && deaths[0] == 0,
                    "Standing explosion did not collapse without a kill");
            mummy.hurt(explosion, 0);
            helper.assertTrue(mummy.monsterState().life() == MonsterState.Life.DEAD && deaths[0] == 1,
                    "Collapsed explosion did not produce exactly one final death");
            var terminal = mummy.monsterState().save();
            int drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, mummy.getBoundingBox().inflate(3)).size();
            for (int i = 0; i < 4; i++) {
                helper.assertTrue(!mummy.hurt(explosion, 100000), "Corpse accepted another explosion");
            }
            helper.assertTrue(mummy.monsterState().save().equals(terminal) && deaths[0] == 1,
                    "Corpse changed terminal state, loot or settlement claims");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, mummy.getBoundingBox().inflate(3)).size() == drops,
                    "Repeated explosion duplicated drops");
        } finally {
            NeoForge.EVENT_BUS.unregister(onDeath);
            mummy.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_mummy_bombs", template = "ring_utilities")
    public static void loadedDeadAndCleanupMummiesRejectDamageBeforeTransitions(GameTestHelper helper) {
        var mummy = spawn(helper);
        var copies = new ArrayList<MineMummyEntity>();
        try {
            var explosion = helper.getLevel().damageSources().explosion(null, null);
            mummy.hurt(explosion, 100000);
            mummy.hurt(explosion, 0);
            var saved = new CompoundTag();
            mummy.saveWithoutId(saved);
            var corpse = ModEntities.MUMMY.get().create(helper.getLevel());
            copies.add(corpse);
            corpse.load(saved);
            var terminal = corpse.monsterState().save();
            helper.assertTrue(corpse.collapsed() && corpse.monsterState().life() == MonsterState.Life.DEAD,
                    "Fixture lost the saved corpse's collapsed visual phase");
            helper.assertTrue(!corpse.hurt(explosion, 999) && corpse.monsterState().save().equals(terminal),
                    "Saved corpse restarted a terminal lifecycle");

            mummy.discard();
            var downed = spawn(helper);
            copies.add(downed);
            downed.hurt(helper.getLevel().damageSources().generic(), 100000);
            MonsterFactory.cleanup(downed);
            var cleaned = new CompoundTag();
            downed.saveWithoutId(cleaned);
            var stale = ModEntities.MUMMY.get().create(helper.getLevel());
            copies.add(stale);
            stale.load(cleaned);
            helper.assertTrue(stale.getHealth() > 0 && !stale.isRemoved()
                    && stale.monsterState().life() == MonsterState.Life.CLEANUP,
                    "Fixture must reload a positive-health terminal cleanup state");
            helper.assertTrue(!stale.hurt(explosion, 999)
                    && stale.monsterState().life() == MonsterState.Life.CLEANUP,
                    "Loaded cleanup state was damaged or revived");
        } finally {
            mummy.discard();
            copies.forEach(MineMummyEntity::discard);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_mummy_bombs", template = "ring_utilities")
    public static void restoredExpiredBombTicksFinishOnceAndIgnoreCorpse(GameTestHelper helper) {
        var mummy = spawn(helper);
        List<StardewBombEntity> bombs = new ArrayList<>();
        int[] deaths = {0};
        Consumer<LivingDeathEvent> onDeath = event -> { if (event.getEntity() == mummy) deaths[0]++; };
        NeoForge.EVENT_BUS.addListener(onDeath);
        try {
            mummy.hurt(helper.getLevel().damageSources().generic(), 100000);
            for (int i = 0; i < 3; i++) {
                var bomb = new StardewBombEntity(ModEntities.STARDEW_BOMB.get(), helper.getLevel());
                bombs.add(bomb);
                bomb.setBombType(BombType.CHERRY_BOMB);
                bomb.setPos(mummy.position());
                var saved = new CompoundTag();
                bomb.saveWithoutId(saved);
                saved.putInt("Fuse", 0);
                bomb.load(saved);
                bomb.setOnGround(true);
                // Tick the actual restored bomb, including entity selection and post-blast discard.
                // It is not inserted into the level, so a failing regression cannot poison this world.
                bomb.tick();
                helper.assertTrue(bomb.isRemoved(), "Expired bomb did not finish its tick");
                helper.assertTrue(mummy.monsterState().life() == MonsterState.Life.DEAD && deaths[0] == 1,
                        "Bomb chain changed final death count/state");
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(onDeath);
            bombs.forEach(StardewBombEntity::discard);
            mummy.discard();
        }
        helper.succeed();
    }
}
