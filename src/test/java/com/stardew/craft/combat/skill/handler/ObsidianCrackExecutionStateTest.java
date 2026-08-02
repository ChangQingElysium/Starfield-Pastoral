package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObsidianCrackExecutionStateTest {
    @Test
    void explosionFiresInclusivelyOnTheEighthTick() {
        assertTrue(ObsidianCrackExecutionState.isWaitingForExplosion(
                107L,
                108L
        ));
        assertFalse(ObsidianCrackExecutionState.isWaitingForExplosion(
                108L,
                108L
        ));
    }

    @Test
    void explosionPreservesAuthoredDamageContext() {
        SkillContext context =
                ObsidianCrackExecutionState.createExplosionContext(
                        "obsidian_crack"
                );

        assertEquals("obsidian_crack", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, context.getTier());
        assertEquals(1.6F, context.getDamageMultiplier());
        assertFalse(context.isIgnoreDefense());
        assertFalse(context.isGuaranteedCrit());
    }

    @Test
    void crackDistanceAndPullPointRemainTwoDimensional() {
        Vec3 start = new Vec3(-3.0D, 64.0D, 3.0D);
        Vec3 end = new Vec3(3.0D, 64.0D, 3.0D);

        assertEquals(
                4.0D,
                ObsidianCrackExecutionState.distanceToSegmentSqr2D(
                        new Vec3(0.0D, 80.0D, 5.0D),
                        start,
                        end
                )
        );
        assertEquals(
                1.0D,
                ObsidianCrackExecutionState.distanceToSegmentSqr2D(
                        new Vec3(4.0D, 10.0D, 3.0D),
                        start,
                        end
                )
        );
        Vec3 nearest = ObsidianCrackExecutionState.nearestPointOnSegment2D(
                new Vec3(5.0D, 90.0D, 3.0D),
                start,
                end
        );
        assertEquals(3.0D, nearest.x);
        assertEquals(64.0D, nearest.y);
        assertEquals(3.0D, nearest.z);
    }

    @Test
    void handlerPreservesPaymentPresentationAndAnimationOrder()
            throws IOException {
        String handler = Files.readString(handlerRoot().resolve(
                "ObsidianCrackSkillHandler.java"
        ));

        int energy = handler.indexOf(
                "WeaponSkillRuntime.consumeEnergyDuringBegin("
        );
        int cooldown = handler.indexOf("WeaponSkillRuntime.commitCooldown(");
        int state = handler.indexOf("new ObsidianCrackExecutionState(");
        int initialize = handler.indexOf(
                "instance.initializeExecutionState(executionState)",
                state
        );
        int presentation = handler.indexOf(
                "executionState.startPresentation(",
                initialize
        );
        int animationLock = handler.indexOf(
                "WeaponSkillAnimationLock.setLock(",
                presentation
        );
        int animation = handler.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                animationLock
        );
        assertTrue(energy >= 0);
        assertTrue(cooldown > energy);
        assertTrue(state > cooldown);
        assertTrue(initialize > state);
        assertTrue(presentation > initialize);
        assertTrue(animationLock > presentation);
        assertTrue(animation > animationLock);
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertFalse(handler.contains("ObsidianCrackTracker"));
        assertFalse(handler.contains("void finish("));
    }

    @Test
    void stateOwnsReleaseSnapshotPullAndNaturalPayloadLifetime()
            throws IOException {
        String state = Files.readString(handlerRoot().resolve(
                "ObsidianCrackExecutionState.java"
        ));

        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertTrue(state.contains(
                "WeaponDamageSnapshot weaponSnapshot = "
                        + "context.weaponSnapshot();"
        ));
        assertTrue(state.contains(
                "new ObsidianCrackPayload("
        ));
        assertEquals(1, occurrences(state, "new ObsidianCrackPayload("));
        assertFalse(state.contains("cancelPayload"));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));

        int revoke = state.indexOf(
                "WeaponSkillMovementArbiter.revokeCurrent(targetPlayer)"
        );
        int teleport = state.indexOf("target.teleportTo(", revoke);
        int slow = state.indexOf("applySlow(target);", teleport);
        int damage = state.indexOf("WeaponSkillDamage.apply(", slow);
        int bypass = state.indexOf(
                ".BYPASS_FOR_AUTHORED_SEQUENCE",
                damage
        );
        assertTrue(revoke >= 0);
        assertTrue(teleport > revoke);
        assertTrue(slow > teleport);
        assertTrue(damage > slow);
        assertTrue(bypass > damage);
        assertEquals(
                1,
                occurrences(
                        state,
                        "EquipmentNegativeStatusProtection.decide("
                )
        );
        assertTrue(state.contains(
                "ObsidianCrackSkillHandler.SLOW_DURATION_TICKS"
        ));
        assertTrue(state.contains("if (protection.resisted())"));
        assertTrue(state.contains("protection.durationTicks()"));
        assertTrue(state.contains(
                "EquipmentMobEffectHandler.addPreAdjustedEffect("
        ));
        assertFalse(state.contains("target.addEffect("));
        assertFalse(state.contains("target.invulnerableTime = 0;"));
        assertFalse(state.contains("target.hurtTime = 0;"));
    }

    @Test
    void runtimeCancelsUnavailableOrCrossDimensionCasterBeforeExplosion()
            throws IOException {
        String runtime = Files.readString(javaRoot().resolve(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        )).replaceAll("\\s+", " ");

        int unavailable = runtime.indexOf(
                "if (!player.isAlive() || player.isRemoved())"
        );
        int dimension = runtime.indexOf(
                "if (!execution.releaseDimension().equals("
                        + "player.level().dimension()))",
                unavailable
        );
        int tick = runtime.indexOf("execution.handler().tick(", dimension);
        assertTrue(unavailable >= 0);
        assertTrue(dimension > unavailable);
        assertTrue(tick > dimension);
        assertTrue(runtime.substring(unavailable, dimension).contains(
                "SkillInstance.EndReason.CASTER_UNAVAILABLE"
        ));
        assertTrue(runtime.substring(dimension, tick).contains(
                "SkillInstance.EndReason.INVALIDATED"
        ));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            count++;
            index = source.indexOf(token, index + token.length());
        }
        return count;
    }

    private static Path handlerRoot() throws IOException {
        return javaRoot().resolve("combat/skill/handler");
    }

    private static Path javaRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate Java source root");
    }
}
