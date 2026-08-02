package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FemurSlamExecutionStateTest {
    @Test
    void fireBoundaryWinsOverItemRelease() {
        assertEquals(
                FemurSlamExecutionState.TickDecision.WAIT,
                FemurSlamExecutionState.tickDecision(
                        120L,
                        119L,
                        true
                )
        );
        assertEquals(
                FemurSlamExecutionState.TickDecision.CANCEL,
                FemurSlamExecutionState.tickDecision(
                        120L,
                        119L,
                        false
                )
        );
        assertEquals(
                FemurSlamExecutionState.TickDecision.FIRE,
                FemurSlamExecutionState.tickDecision(
                        120L,
                        120L,
                        false
                )
        );
        assertEquals(
                FemurSlamExecutionState.TickDecision.FIRE,
                FemurSlamExecutionState.tickDecision(
                        120L,
                        120L,
                        true
                )
        );
        assertEquals(
                FemurSlamExecutionState.TickDecision.FIRE,
                FemurSlamExecutionState.tickDecision(
                        120L,
                        121L,
                        false
                )
        );
        assertEquals(
                FemurSlamExecutionState.TickDecision.FIRE,
                FemurSlamExecutionState.tickDecision(
                        120L,
                        121L,
                        true
                )
        );
    }

    @Test
    void hitUsesOriginalMinorDamageContext() {
        SkillContext hit = FemurSlamExecutionState.createHitContext(
                "femur_slam",
                1.20F
        );

        assertEquals("femur_slam", hit.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, hit.getTier());
        assertEquals(1.20F, hit.getDamageMultiplier());
        assertFalse(hit.isGuaranteedCrit());
        assertFalse(hit.isIgnoreDefense());
    }

    @Test
    void beginFailureCompensationIsRegisteredInLifoOrder()
            throws IOException {
        String handler = source(
                "combat/skill/handler/FemurSlamSkillHandler.java"
        );
        String runtime = source(
                "combat/skill/runtime/SkillInstance.java"
        );
        String cooldownRuntime = source(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        );

        int startUsing = handler.indexOf(
                "context.player().startUsingItem(context.hand())"
        );
        int stopCleanup = handler.indexOf(
                "context.player()::stopUsingItem",
                startUsing
        );
        int defer = handler.indexOf(
                "WeaponSkillRuntime.deferCooldown(",
                stopCleanup
        );
        int initialize = handler.indexOf(
                "instance.initializeExecutionState(",
                defer
        );
        assertTrue(startUsing >= 0);
        assertTrue(stopCleanup > startUsing);
        assertTrue(defer > stopCleanup);
        assertTrue(initialize > defer);
        assertTrue(cooldownRuntime.contains(
                "instance.registerBeginFailureCleanup(cooldown::abandon)"
        ));
        assertTrue(runtime.contains(
                "for (int index = cleanups.size() - 1; "
                        + "index >= 0; index--)"
        ));
    }

    @Test
    void stateOwnsSettlementReentryAndFinishCancellation()
            throws IOException {
        String handler = source(
                "combat/skill/handler/FemurSlamSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/FemurSlamExecutionState.java"
        );

        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertTrue(state.contains(
                "private final long fireTick;"
        ));
        assertTrue(state.contains(
                "private final DeferredSkillCooldown cooldown;"
        ));
        assertTrue(state.contains("private boolean settled;"));
        assertTrue(state.contains("private boolean advancing;"));
        assertTrue(state.contains("if (advancing)"));
        assertTrue(state.contains("if (settled)"));
        assertFalse(state.contains("static final Map"));
        assertTrue(state.contains(
                "Map<UUID, LivingEntity> appliedTargets"
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(handler.contains(
                "instance.executionState(FemurSlamExecutionState.class)"
        ));
        assertTrue(handler.contains(
                "state -> state.cancel(context.player())"
        ));
        assertFalse(handler.contains("FemurSlamTracker"));
        assertFalse(handler.contains("WeaponSkillAnimationLock"));
        assertFalse(handler.contains("WeaponSkillAnimationDispatcher"));

        String cancel = method(state, "void cancel(ServerPlayer player)");
        int settle = cancel.indexOf("settled = true;");
        int abandon = cancel.indexOf(
                "WeaponSkillRuntime.abandonDeferredCooldown(cooldown)",
                settle
        );
        int stop = cancel.indexOf("player.stopUsingItem()", abandon);
        assertTrue(settle >= 0);
        assertTrue(abandon > settle);
        assertTrue(stop > abandon);
    }

    @Test
    void fireCommitsAndStopsEvenWhenTheSlamThrows() throws IOException {
        String state = source(
                "combat/skill/handler/FemurSlamExecutionState.java"
        );
        String fire = method(
                state,
                "private SkillTickResult fire(SkillExecutionContext context)"
        );

        int settle = fire.indexOf("settled = true;");
        int outerTry = fire.indexOf("try {", settle);
        int innerTry = fire.indexOf("try {", outerTry + 1);
        int slam = fire.indexOf("handleSlam(context)", innerTry);
        int innerFinally = fire.indexOf("finally", slam);
        int commit = fire.indexOf(
                "WeaponSkillRuntime.commitDeferredCooldown(",
                innerFinally
        );
        int outerFinally = fire.indexOf("finally", commit);
        int stop = fire.indexOf(
                "context.player().stopUsingItem()",
                outerFinally
        );
        assertTrue(settle >= 0);
        assertTrue(outerTry > settle);
        assertTrue(innerTry > outerTry);
        assertTrue(slam > innerTry);
        assertTrue(innerFinally > slam);
        assertTrue(commit > innerFinally);
        assertTrue(outerFinally > commit);
        assertTrue(stop > outerFinally);
    }

    @Test
    void damageControlAndPresentationOrderRemainAuthored()
            throws IOException {
        String state = source(
                "combat/skill/handler/FemurSlamExecutionState.java"
        );
        String slam = method(
                state,
                "private void handleSlam(SkillExecutionContext context)"
        );

        assertTrue(slam.contains(
                "WeaponDamageSnapshot weaponSnapshot = "
                        + "context.weaponSnapshot();"
        ));
        assertTrue(slam.contains(
                "WeaponSkillDamage.AttackGatePolicy"
        ));
        assertTrue(slam.contains(".RESPECT_AT_IMPACT"));
        assertTrue(slam.contains(
                "findTargetsInArc("
        ));

        String targeting = method(
                state,
                "private static List<LivingEntity> findTargetsInArc("
        );
        assertTrue(targeting.contains(
                "player.getBoundingBox().inflate("
        ));
        assertTrue(targeting.contains(
                "distanceSquared > range * range"
        ));
        assertTrue(targeting.contains(
                "flatToTarget.normalize().dot(flatLook) < minimumDot"
        ));
        assertTrue(targeting.contains(
                "targets.sort((first, second) -> Double.compare("
        ));

        int beginCollection = slam.indexOf(
                "beginAppliedHitCollection(targets)"
        );
        int damage = slam.indexOf(
                "WeaponSkillDamage.apply(",
                beginCollection
        );
        int settleControls = slam.indexOf(
                "settleAppliedControls(player)",
                damage
        );
        int clear = slam.indexOf(
                "clearAppliedHitCollection()",
                settleControls
        );
        int quake = slam.indexOf("spawnQuakeImpact(", clear);
        int tremor = slam.indexOf("spawnTremorBurst(", quake);
        int golemSound = slam.indexOf("SoundEvents.IRON_GOLEM_ATTACK", tremor);
        int explosionSound = slam.indexOf(
                "SoundEvents.GENERIC_EXPLODE.value()",
                golemSound
        );
        assertTrue(beginCollection >= 0);
        assertTrue(damage > beginCollection);
        assertTrue(settleControls > damage);
        assertTrue(clear > settleControls);
        assertTrue(quake > clear);
        assertTrue(tremor > quake);
        assertTrue(golemSound > tremor);
        assertTrue(explosionSound > golemSound);

        assertFalse(slam.contains("MobEffects.MOVEMENT_SLOWDOWN"));
        assertFalse(slam.contains("MobEffects.DIG_SLOWDOWN"));
        assertFalse(slam.contains("applyKnockback("));

        String record = method(
                state,
                "boolean recordAppliedHit(LivingEntity target)"
        );
        assertTrue(record.contains("collectingAppliedHits"));
        assertTrue(record.contains(
                "eligibleHitTargets.contains(target.getUUID())"
        ));
        assertTrue(record.contains("appliedTargets.putIfAbsent("));
        assertFalse(record.contains("EquipmentNegativeStatusProtection"));

        String settleApplied = method(
                state,
                "private void settleAppliedControls(ServerPlayer player)"
        );
        int appliedCount = settleApplied.indexOf(
                "appliedTargets.size() == 1"
        );
        int appliedLoop = settleApplied.indexOf(
                "for (LivingEntity target : appliedTargets.values())",
                appliedCount
        );
        int control = settleApplied.indexOf(
                "applyControl(player, target, singleAppliedTarget)",
                appliedLoop
        );
        assertTrue(appliedCount >= 0);
        assertTrue(appliedLoop > appliedCount);
        assertTrue(control > appliedLoop);
        assertFalse(slam.contains("targets.size() == 1"));

        String appliedControl = method(
                state,
                "private static void applyControl("
        );
        int decision = appliedControl.indexOf(
                "EquipmentNegativeStatusProtection.decide("
        );
        int slow = appliedControl.indexOf(
                "MobEffects.MOVEMENT_SLOWDOWN",
                decision
        );
        int stagger = appliedControl.indexOf(
                "MobEffects.DIG_SLOWDOWN",
                slow
        );
        int knockback = appliedControl.indexOf(
                "applyKnockback(",
                stagger
        );
        int hitParticles = appliedControl.indexOf(
                "spawnHitParticles(",
                knockback
        );
        assertTrue(decision >= 0);
        assertTrue(slow > decision);
        assertTrue(stagger > slow);
        assertTrue(knockback > stagger);
        assertTrue(hitParticles > knockback);
    }

    @Test
    void runtimeCancelsDeathAndDimensionChangeBeforeStateTick()
            throws IOException {
        String runtime = source(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        ).replaceAll("\\s+", " ");

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
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int openingBrace = source.indexOf('{', start);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new AssertionError("Unclosed method " + signature);
    }

    private static String source(String relative) throws IOException {
        Path root = javaRoot();
        return Files.readString(root.resolve(relative));
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
