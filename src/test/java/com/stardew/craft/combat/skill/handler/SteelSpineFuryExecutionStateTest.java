package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteelSpineFuryExecutionStateTest {
    @Test
    void strongChargeRoundsUpFortyPercentAndCapsAtTwelve() {
        assertEquals(1, SteelSpineFuryExecutionState.calculateBonusDamage(1));
        assertEquals(1, SteelSpineFuryExecutionState.calculateBonusDamage(2));
        assertEquals(2, SteelSpineFuryExecutionState.calculateBonusDamage(3));
        assertEquals(12, SteelSpineFuryExecutionState.calculateBonusDamage(30));
        assertEquals(12, SteelSpineFuryExecutionState.calculateBonusDamage(100));
    }

    @Test
    void strongAndFallbackStrikesPreserveTheirDamageModels() {
        SteelSpineFurySkillHandler.AttackBoost strong =
                SteelSpineFuryExecutionState.createAttackBoost(false, 7);
        SteelSpineFurySkillHandler.AttackBoost fallback =
                SteelSpineFuryExecutionState.createAttackBoost(true, 7);

        assertTrue(strong.strong());
        assertEquals(7, strong.bonusDamage());
        assertEquals(1.0F, strong.damageMultiplier());
        assertFalse(fallback.strong());
        assertEquals(0, fallback.bonusDamage());
        assertEquals(1.4F, fallback.damageMultiplier());
    }

    @Test
    void bothExternalEventsUseTheExactTypedExecutionFacade()
            throws IOException {
        String handler = handlerSource("SteelSpineFurySkillHandler.java");
        String state = handlerSource("SteelSpineFuryExecutionState.java");
        String combatEvents = source("combat/WeaponCombatEvents.java");
        String preparation = source("combat/WeaponHitPreparation.java");
        String playerEvents = source("player/PlayerDataEventHandler.java");

        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertTrue(state.contains(
                "private final DeferredSkillCooldown cooldown;"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));
        assertFalse(handler.contains("SteelSpineFuryState"));
        assertFalse(combatEvents.contains("SteelSpineFuryState"));
        assertFalse(playerEvents.contains("SteelSpineFuryState"));
        assertEquals(
                3,
                occurrences(
                        handler,
                        "WeaponSkillRuntime.activeExecutionState("
                )
        );
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.STEEL_SPINE_FURY"
        ));
        assertTrue(preparation.contains(
                "SteelSpineFurySkillHandler.consumeAttack("
        ));
        assertTrue(preparation.contains(
                "SteelSpineFurySkillHandler.reserveAttack("
        ));
        assertTrue(handler.contains("public record AttackReservation("));
        assertTrue(playerEvents.contains(
                ".SteelSpineFurySkillHandler.onDamageTaken("
        ));
    }

    @Test
    void cooldownAndPresentationOrderRemainAuthored()
            throws IOException {
        String handler = handlerSource("SteelSpineFurySkillHandler.java");
        String state = handlerSource("SteelSpineFuryExecutionState.java");

        int defer = handler.indexOf("WeaponSkillRuntime.deferCooldown(");
        int initialize = handler.indexOf(
                "instance.initializeExecutionState(executionState)",
                defer
        );
        int start = handler.indexOf("executionState.start(", initialize);
        assertTrue(defer >= 0);
        assertTrue(initialize > defer);
        assertTrue(start > initialize);

        String startPresentation = method(
                state,
                "void start(ServerPlayer player, int durationTicks)"
        );
        int active = startPresentation.indexOf(
                "new SteelSpineFuryPayload(true, durationTicks)"
        );
        int enter = startPresentation.indexOf(
                "new SteelSpineFuryEnterPayload()",
                active
        );
        assertTrue(active >= 0);
        assertTrue(enter > active);

        String damageTaken = method(
                state,
                "void onDamageTaken("
        );
        int mutate = damageTaken.indexOf("tookHit = true;");
        int cooldown = damageTaken.indexOf(
                "commitCooldownAndClose(player, nowTick)",
                mutate
        );
        int sound = damageTaken.indexOf("player.playSound(", cooldown);
        int hit = damageTaken.indexOf(
                "new SteelSpineFuryHitPayload()",
                sound
        );
        assertTrue(mutate >= 0);
        assertTrue(cooldown > mutate);
        assertTrue(sound > cooldown);
        assertTrue(hit > sound);
    }

    @Test
    void reservationAndForcedInterruptionKeepTheirAuthoredTransactions()
            throws IOException {
        String state = handlerSource("SteelSpineFuryExecutionState.java");
        String consume = method(
                state,
                "SteelSpineFurySkillHandler.AttackBoost consumeAttack("
        );
        int reserveFromConsume = consume.indexOf(
                "reserveAttack(player, nowTick)"
        );
        int commitFromConsume = consume.indexOf(
                "reservation.commit().run()",
                reserveFromConsume
        );
        int returnBoost = consume.indexOf(
                "return reservation.boost();",
                commitFromConsume
        );
        assertTrue(reserveFromConsume >= 0);
        assertTrue(commitFromConsume > reserveFromConsume);
        assertTrue(returnBoost > commitFromConsume);

        String reserve = method(
                state,
                "SteelSpineFurySkillHandler.AttackReservation reserveAttack("
        );
        int boost = reserve.indexOf("createAttackBoost(weak, bonusDamage)");
        int reservationId = reserve.indexOf(
                "long reservationId = ++nextReservationId;",
                boost
        );
        int hold = reserve.indexOf(
                "activeReservation = reservationId;",
                reservationId
        );
        int reservation = reserve.indexOf(
                "new SteelSpineFurySkillHandler.AttackReservation(",
                hold
        );
        assertTrue(boost >= 0);
        assertTrue(reservationId > boost);
        assertTrue(hold > reservationId);
        assertTrue(reservation > hold);

        String commit = method(state, "private void commitReservation(");
        assertTrue(commit.contains("activeReservation = null;"));
        assertTrue(commit.contains("ready = false;"));
        assertTrue(commit.contains("consumed = true;"));

        String release = method(state, "private void releaseReservation(");
        assertTrue(release.contains("activeReservation = null;"));
        assertFalse(release.contains("ready = false;"));
        assertFalse(release.contains("consumed = true;"));

        String interrupt = method(state, "void interrupt(");
        assertTrue(interrupt.contains(
                "commitCooldownAndClose(player, nowTick)"
        ));
        assertFalse(interrupt.contains("SteelSpineFuryPayload"));
        assertFalse(interrupt.contains("abandonDeferredCooldown"));
    }

    @Test
    void externalDamageAndStrikePresentationOrderRemainUnchanged()
            throws IOException {
        String combatEvents = source("combat/WeaponCombatEvents.java");
        String preparation = source("combat/WeaponHitPreparation.java");
        String assembly = source("combat/WeaponDamageAssemblyRules.java");
        String evaluated = source(
                "combat/BuiltinSkillEvaluatedHitRules.java"
        );
        String coordinator = source(
                "combat/WeaponEvaluatedHitCoordinator.java"
        );
        String playerEvents = source("player/PlayerDataEventHandler.java");

        int reserve = preparation.indexOf(
                "SteelSpineFurySkillHandler.reserveAttack("
        );
        String evaluateHit = method(
                combatEvents,
                "private static IncomingWeaponResolution evaluateWeaponHit("
        );
        int prepare = evaluateHit.indexOf("WeaponHitPreparation.reserve(");
        int assemble = evaluateHit.indexOf(
                "WeaponDamageAssemblyRules.apply(",
                prepare
        );
        int flatBonus = assembly.indexOf(
                "\"steel_spine_bonus\"",
                0
        );
        int flatDamage = assembly.indexOf(
                "spineBoost.bonusDamage()",
                flatBonus
        );
        int evaluate = evaluateHit.indexOf(
                "DamagePipeline.evaluate(damageRequest.build())",
                prepare
        );
        int resolution = evaluateHit.indexOf(
                "new IncomingWeaponResolution(hit, outcome.getFinalDamage())",
                evaluate
        );
        String pre = method(
                combatEvents,
                "public static void onLivingHurt(LivingDamageEvent.Pre event)"
        );
        int applyDamage = pre.indexOf(
                "event.setNewDamage(resolution.authoritativeDamage())"
        );
        int commitReservation = pre.indexOf(
                "hit.preparationReservation().commit()",
                applyDamage
        );
        int coordinate = pre.indexOf(
                "WeaponEvaluatedHitCoordinator.apply(",
                commitReservation
        );
        String strike = method(
                evaluated,
                "static void emitSteelSpineStrike(EvaluatedWeaponHit hit)"
        );
        int strikePayload = strike.indexOf(
                "new SteelSpineFuryStrikePayload("
        );
        int strong = strike.indexOf(
                "hit.steelSpineBoost().strong()",
                strikePayload
        );
        int emit = coordinator.indexOf(
                "BuiltinSkillEvaluatedHitRules.emitSteelSpineStrike(hit)"
        );
        int nextRule = coordinator.indexOf(
                "CommonWeaponEvaluatedHitRules.bindAppliedHitFrame(hit)"
        );
        assertTrue(reserve >= 0);
        assertTrue(flatBonus >= 0);
        assertTrue(flatDamage > flatBonus);
        assertTrue(prepare >= 0 && assemble > prepare);
        assertTrue(evaluate > assemble);
        assertTrue(resolution > evaluate);
        assertTrue(applyDamage >= 0);
        assertTrue(commitReservation > applyDamage);
        assertTrue(coordinate > commitReservation);
        assertTrue(strikePayload >= 0 && strong > strikePayload);
        assertTrue(emit >= 0 && nextRule > emit);

        int trinket = playerEvents.indexOf(
                "TrinketEffectHandler.onReceiveDamage(player, sdDamage)"
        );
        int charge = playerEvents.indexOf(
                ".SteelSpineFurySkillHandler.onDamageTaken(",
                trinket
        );
        int fortress = playerEvents.indexOf(
                ".DwarfFortressSkillHandler.onDamageTaken(player, nowTick)",
                charge
        );
        assertTrue(trinket >= 0);
        assertTrue(charge > trinket);
        assertTrue(fortress > charge);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            return "";
        }
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
        return "";
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

    private static String handlerSource(String fileName)
            throws IOException {
        return Files.readString(javaRoot().resolve(
                "combat/skill/handler/" + fileName
        ));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(javaRoot().resolve(relative));
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
