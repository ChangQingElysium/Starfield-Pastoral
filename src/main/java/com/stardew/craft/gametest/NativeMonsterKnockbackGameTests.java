package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.entity.monster.MineBatEntity;
import com.stardew.craft.entity.monster.MineFlyEntity;
import com.stardew.craft.entity.monster.MineSerpentEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.monster.MonsterFlightMotion;
import com.stardew.craft.monster.MonsterSpawnContext;
import com.stardew.craft.monster.StardewMonsterEntity;
import com.stardew.craft.port.PortGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Real normal-weapon damage followed by several active server-AI ticks. */
@GameTestHolder("stardewcraft_monster_knockback")
@PrefixGameTestTemplate(false)
public final class NativeMonsterKnockbackGameTests {
    private NativeMonsterKnockbackGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_monster_knockback", template = "flight_room", timeoutTicks = 40)
    public static void serpentKeepsNormalWeaponKnockbackThroughRecovery(GameTestHelper h) {
        flyingHit(h, "serpent", 7);
    }

    @GameTest(templateNamespace = "stardewcraft_monster_knockback", template = "flight_room", timeoutTicks = 40)
    public static void batKeepsNormalWeaponKnockbackThroughRecovery(GameTestHelper h) {
        flyingHit(h, "bat", 7);
    }

    @GameTest(templateNamespace = "stardewcraft_monster_knockback", template = "flight_room", timeoutTicks = 40)
    public static void emergingFlyKeepsItsWeakerAcceptedTrajectoryThroughRecovery(GameTestHelper h) {
        // Fly's existing /9 conversion stays unchanged; its emerging velocity is still below this impulse.
        flyingHit(h, "fly", 20);
    }

    private static void flyingHit(GameTestHelper h, String id, int warmup) {
        floor(h);
        var mob = spawn(h, id, 5);
        var player = player(h, id, mob.position().add(0, 0, -4));
        mob.setTarget(player);
        h.runAtTickTime(warmup, () -> {
            player.setPos(mob.position().add(0, 0, -4));
            Vec3 origin = mob.position();
            hit(h, mob, player);
            h.assertTrue(motion(mob).velocity().z > 0, id + " did not accept the backward trajectory");
            h.runAfterDelay(1, () -> {
                Vec3 first = mob.position();
                h.assertTrue(first.z > origin.z + .01, id + " lost knockback on its first active AI tick");
                h.runAfterDelay(2, () -> {
                    h.assertTrue(mob.getTarget() == player && !mob.isNoAi(), id + " stopped AI instead of retaining inertia");
                    h.assertTrue(mob.getZ() > first.z + .02 && motion(mob).velocity().z > 0,
                            id + " chase acceleration erased the hit trajectory within three ticks");
                    mob.discard();
                    player.discard();
                    h.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "stardewcraft_monster_knockback", template = "flight_room", timeoutTicks = 30)
    public static void groundMonstersRetainTheirOwnHitAndWalkingRules(GameTestHelper h) {
        floor(h);
        var skeleton = spawn(h, "skeleton", 1);
        var player = player(h, "ground", skeleton.position().add(0, 0, -4));
        h.runAtTickTime(5, () -> {
            player.setPos(skeleton.position().add(0, 0, -4));
            Vec3 origin = skeleton.position();
            hit(h, skeleton, player);
            h.runAfterDelay(1, () -> {
                h.assertTrue(skeleton.getZ() > origin.z + .04, "Ground AI erased the skeleton's hit trajectory");
                h.runAfterDelay(1, () -> {
                    // Source ground monsters may walk during sliding; retain that species behavior.
                    h.assertTrue(skeleton.getTarget() == player && skeleton.getZ() > origin.z + .02,
                            "Skeleton had no backward displacement after multiple active AI ticks");
                    skeleton.discard();
                    player.discard();
                    h.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "stardewcraft_monster_knockback", template = "flight_room", timeoutTicks = 20)
    public static void armoredBugAndDuggyKeepTheirSourceKnockbackImmunity(GameTestHelper h) {
        floor(h);
        var armored = spawn(h, "armored_bug", 3);
        var duggy = spawn(h, "duggy", 1);
        armored.setNoAi(true);
        duggy.setNoAi(true);
        var player = player(h, "immunity", armored.position().add(0, 0, -3));
        float health = armored.getHealth();
        player.attack(armored);
        h.assertTrue(armored.getHealth() == health, "An ordinary sword bypassed armored-bug immunity");
        armored.knockback(.4, 0, -1);
        duggy.knockback(.4, 0, -1);
        Vec3 armorPos = armored.position(), duggyPos = duggy.position();
        h.runAfterDelay(3, () -> {
            h.assertTrue(armored.position().distanceToSqr(armorPos) < 1e-9
                    && duggy.position().distanceToSqr(duggyPos) < 1e-9,
                    "A source-immune monster acquired knockback");
            armored.discard();
            duggy.discard();
            player.discard();
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "stardewcraft_monster_knockback", template = "flight_room", timeoutTicks = 40)
    public static void existingSerpentWallAndMotionSaveRegression(GameTestHelper h) {
        NativeSerpentGameTests.physicalFlightStopsAtWallsAndSaveKeepsMotionAndDrops(h);
    }

    @GameTest(templateNamespace = "stardewcraft_monster_knockback", template = "flight_room", timeoutTicks = 340)
    public static void existingBatWallAndAltitudeRegression(GameTestHelper h) {
        MonsterSpaceGameTests.batRoutesAndClimbs(h);
    }

    @GameTest(templateNamespace = "stardewcraft_monster_knockback", template = "flight_room", timeoutTicks = 65)
    public static void existingSlimeCornerRegression(GameTestHelper h) {
        SlimeCornerGameTests.greenSlime(h);
    }

    private static void floor(GameTestHelper h) {
        PortGameTests.allowSkyAccess(h);
        for (int x = 1; x <= 15; x++) for (int z = 1; z <= 15; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    private static StardewMonsterEntity spawn(GameTestHelper h, String id, int y) {
        return (StardewMonsterEntity) MineMonsterSpawnHandler.spawnConfiguredMonster(h.getLevel(), id,
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, y, 8))), 0,
                new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND, 135, false, null), m -> m.setPersistenceRequired());
    }

    private static FakePlayer player(GameTestHelper h, String name, Vec3 position) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Knockback-" + name));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(position);
        player.setNoGravity(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RUSTY_SWORD.get()));
        h.getLevel().addNewPlayer(player);
        return player;
    }

    private static void hit(GameTestHelper h, StardewMonsterEntity mob, FakePlayer player) {
        float health = mob.getHealth();
        player.attack(mob);
        h.assertTrue(mob.getHealth() < health && mob.isAlive(), "The normal sword attack did not deal nonlethal damage");
        h.assertTrue(mob.sourceHitRecoveryActive(), "The applied normal weapon hit did not arm source recovery");
    }

    private static MonsterFlightMotion motion(StardewMonsterEntity mob) {
        if (mob instanceof MineSerpentEntity serpent) return serpent.steering();
        if (mob instanceof MineBatEntity bat) return bat.flight();
        return ((MineFlyEntity) mob).steering();
    }
}
