package com.stardew.craft.gametest;

import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineBatEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.monster.MonsterState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Explicit namespace only: -PgameTestNamespaces=stardewcraft_bat. */
@GameTestHolder("stardewcraft_bat")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class NativeBatGameTests {
    @GameTest(batch="bat_idle", templateNamespace="stardewcraft_bat", template="ring_utilities", timeoutTicks=180)
    public static void allVariantsFlyWithoutPlayersAndKeepIdentityAfterReload(GameTestHelper h) {
        var level = h.getLevel();
        var bats = new java.util.ArrayList<MineBatEntity>();
        var starts = new java.util.ArrayList<Vec3>();
        var forced = new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        String[] ids = {"bat", "frost_bat", "lava_bat", "iridium_bat", "iridium_bat"};
        int[] floors = {30, 50, 90, 180, 1000}, health = {24, 36, 80, 300, 600};
        for (int i = 0; i < ids.length; i++) {
            var origin = h.absolutePos(new BlockPos(8 + i * 12, 10, 8));
            var chunk = new net.minecraft.world.level.ChunkPos(origin);
            for (int x=-1; x<=1; x++) for (int z=-1; z<=1; z++) {
                var c = new net.minecraft.world.level.ChunkPos(chunk.x+x, chunk.z+z);
                if (!level.getForcedChunks().contains(c.toLong())) { level.setChunkForced(c.x,c.z,true); forced.add(c); }
            }
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = -2; y <= 9; y++)
                level.setBlock(origin.offset(x,y,z), Blocks.AIR.defaultBlockState(), 3);
            var spawned = MineMonsterSpawnHandler.spawnConfiguredMonster(level, ids[i], Vec3.atBottomCenterOf(origin), 0, floors[i]);
            h.assertTrue(spawned instanceof MineBatEntity, ids[i] + " still uses a vanilla placeholder");
            var bat = (MineBatEntity) spawned;
            h.assertTrue(bat.variant().equals(ids[i]) && bat.getHealth() == health[i], "Wrong native variant/stats");
            h.assertTrue(bat.phase() == MineBatEntity.FLY, "Open-air bat tried to roost");
            h.assertTrue(bat.deepRed() == (floors[i] > 999), "Deep bat source tint missing");
            bats.add(bat); starts.add(bat.position());
        }
        h.runAtTickTime(80, () -> {
            for (int i = 0; i < bats.size(); i++) {
                var bat = bats.get(i);
                h.assertTrue(bat.getTarget() == null && bat.position().distanceToSqr(starts.get(i)) > .5,
                        ids[i] + " froze without a target: ticks=" + bat.tickCount + ", target=" + bat.getTarget() + ", distance=" + bat.position().distanceToSqr(starts.get(i)));
                var save = new CompoundTag(); bat.saveWithoutId(save);
                var restored = (MineBatEntity) bat.getType().create(level); restored.load(save);
                h.assertTrue(restored.variant().equals(ids[i]) && restored.deepRed() == bat.deepRed() && restored.monsterState().save().equals(bat.monsterState().save()), "Variant/drop snapshot changed on reload");
                starts.set(i, bat.position());
            }
        });
        h.runAtTickTime(150, () -> {
            for (int i = 0; i < bats.size(); i++) {
                h.assertTrue(bats.get(i).position().distanceToSqr(starts.get(i)) > .1, ids[i] + " stopped after first waypoint");
                bats.get(i).discard();
            }
            for (var c : forced) level.setChunkForced(c.x,c.z,false);
            h.succeed();
        });
    }
    @GameTest(batch="bat_contact", templateNamespace="stardewcraft_bat", template="ring_utilities", timeoutTicks=150)
    public static void nativeBatNoticesPursuesAndDealsContactDamage(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(8, 2, 8));
        for (int x = -5; x <= 7; x++) for (int z = -5; z <= 5; z++) {
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 5; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(origin.offset(x, 5, z), Blocks.STONE.defaultBlockState(), 3);
        }
        var bat = (MineBatEntity) MineMonsterSpawnHandler.spawnConfiguredMonster(level, "bat", Vec3.atBottomCenterOf(origin), 0, 30);
        var player = new net.minecraftforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "BatContactTest")) {
            // MinecraftForge's default FakePlayer is unconditionally invulnerable.
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // The minimal GameTest server has no mining dimension for a lazy first-day data sync.
        var day = com.stardew.craft.time.StardewTimeManager.get();
        com.stardew.craft.player.PlayerDataManager.getPlayerData(player).setDailyLuckForDate(0,
                ((day.getCurrentYear() * 4) + day.getCurrentSeason()) * 28 + day.getCurrentDay() - 1);
        // FakePlayer.tick() is empty, so vanilla's 60-tick spawn immunity never expires.
        var spawnImmunity = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        spawnImmunity.setAccessible(true);
        spawnImmunity.setInt(player, 0);
        player.setPos(Vec3.atBottomCenterOf(origin.offset(5, 0, 0)));
        level.addNewPlayer(player);
        double startX = bat.getX();
        h.runAtTickTime(5, () -> {
            h.assertTrue(bat.getTarget() == player && bat.phase() == MineBatEntity.AWAKE, "Nearby player did not wake and attract bat");
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(bat.getX() > startX + .5, "Flight did not pursue player's position");
            // Test contact independently of arrival timing and the source's intentional overshoot.
            bat.setPos(player.getX(), player.getY() + .85, player.getZ());
            player.invulnerableTime = 0;
            player.setHealth(player.getMaxHealth());
        });
        h.runAtTickTime(72, () -> {
            h.assertTrue(player.getHealth() < player.getMaxHealth(), "Native bat did not damage overlapping player");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.stardew.craft.effect.ModMobEffects.AVOID_MONSTERS.get(), 200));
            player.invulnerableTime = 0; player.setHealth(player.getMaxHealth());
            bat.setPos(player.getX(), player.getY() + .85, player.getZ());
        });
        h.runAtTickTime(74, () -> {
            h.assertTrue(bat.getTarget() == null && player.getHealth() == player.getMaxHealth(), "Garlic oil did not block native pursuit/contact: target="+bat.getTarget()+", effect="+player.hasEffect(com.stardew.craft.effect.ModMobEffects.AVOID_MONSTERS.get())+", HP="+player.getHealth());
            h.assertTrue(bat.phase() == MineBatEntity.AWAKE, "Losing a target reset source awareness");
            bat.discard(); player.discard(); h.succeed();
        });
    }
    @GameTest(templateNamespace="stardewcraft_bat", template="ring_utilities", timeoutTicks=120)
    public static void nativeBatRoostWakeReloadAndDeath(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(8, 2, 8));
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 5; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(origin.offset(x, 5, z), Blocks.STONE.defaultBlockState(), 3);
        }
        var spawned = MineMonsterSpawnHandler.spawnConfiguredMonster(level, "bat", Vec3.atBottomCenterOf(origin), 0, 30);
        h.assertTrue(spawned instanceof MineBatEntity, "Command still created a Phantom");
        var bat = (MineBatEntity) spawned;
        h.assertTrue(bat.initialized() && bat.phase() == MineBatEntity.ROOST, "No initialized ceiling roost");
        h.assertTrue(bat.getHealth() == 24 && bat.monsterState().stats().getDamage() == 6, "Legacy floor scaling replaced source stats");
        h.assertTrue(level.noCollision(bat, bat.getBoundingBox()), "Spawn intersects the ceiling");
        var roost = bat.position();
        var bornDrops = bat.monsterState().bornDrops();
        h.runAtTickTime(25, () -> {
            h.assertTrue(bat.position().distanceToSqr(roost) < .000001 && bat.phase() == MineBatEntity.ROOST,
                    "Unaware bat drifted or started flapping");
            bat.hurt(level.damageSources().generic(), 1);
            bat.knockback(.4, 1, 0);
            h.assertTrue(bat.phase() == MineBatEntity.AWAKE, "Damage did not wake bat");
        });
        h.runAtTickTime(42, () -> {
            h.assertTrue(bat.phase() == MineBatEntity.AWAKE && bat.animationTime(0) > .56, "Lost awareness or restarted wake");
            h.assertTrue(bat.getX() < roost.x, "Knockback was erased by travel");
            var save = new CompoundTag(); bat.saveWithoutId(save);
            var restored = ModEntities.BAT.get().create(level); restored.load(save);
            h.assertTrue(restored.phase() == bat.phase() && restored.animationTime(0) == bat.animationTime(0), "Animation phase not restored");
            h.assertTrue(restored.getHealth() == bat.getHealth() && restored.monsterState().bornDrops().equals(bornDrops), "Reload rerolled HP or loot");
            h.assertTrue(restored.monsterState().context().generation() == null, "Command acquired floor population ownership");
            var bounds = bat.getBoundingBox().inflate(4);
            int before = level.getEntitiesOfClass(ItemEntity.class, bounds).stream().mapToInt(e -> e.getItem().getCount()).sum();
            bat.hurt(level.damageSources().genericKill(), 10000);
            h.assertTrue(!bat.isAlive() && bat.monsterState().life() == MonsterState.Life.DEAD, "Native final death failed");
            int after = level.getEntitiesOfClass(ItemEntity.class, bounds).stream().mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(after - before == bornDrops.size(), "Death did not materialize the saved source drops");
            bat.die(level.damageSources().genericKill());
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, bounds).stream().mapToInt(e -> e.getItem().getCount()).sum() == after,
                    "Repeated death duplicated drops");
            h.succeed();
        });
    }
}
