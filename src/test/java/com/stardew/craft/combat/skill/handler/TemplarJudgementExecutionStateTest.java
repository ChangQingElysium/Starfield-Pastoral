package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplarJudgementExecutionStateTest {
    @Test
    void judgementUsesItsAuthoredExclusiveEndTick() {
        assertTrue(TemplarJudgementExecutionState.isWithinActiveWindow(
                199L,
                200L
        ));
        assertFalse(TemplarJudgementExecutionState.isWithinActiveWindow(
                200L,
                200L
        ));
    }

    @Test
    void settlementContextPreservesAuthoredMajorDamage() {
        SkillContext context =
                TemplarJudgementExecutionState
                        .createSettlementContext("templar_judgement");

        assertEquals("templar_judgement", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, context.getTier());
        assertEquals(1.6F, context.getDamageMultiplier());
        assertFalse(context.isIgnoreDefense());
        assertFalse(context.isGuaranteedCrit());
    }

    @Test
    void runtimeStateOwnsOrderedTargetRefsAndReleaseSnapshot()
            throws IOException {
        Path root = handlerRoot();
        String handler = Files.readString(root.resolve(
                "TemplarJudgementSkillHandler.java"
        ));
        String state = Files.readString(root.resolve(
                "TemplarJudgementExecutionState.java"
        ));

        assertTrue(handler.contains(
                "instance.initializeExecutionState("
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.TEMPLAR_JUDGEMENT"
        ));
        assertFalse(handler.contains("TemplarJudgementTracker"));
        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));
        assertTrue(state.contains(
                "private record TargetRef("
        ));
        assertTrue(state.contains(
                "target.level().dimension()"
        ));
        assertTrue(state.contains("target.getUUID()"));
        assertTrue(state.contains(
                "this.targets = List.copyOf(targetRefs);"
        ));
        assertTrue(state.contains(
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ));

        int energy = handler.indexOf(
                "WeaponSkillRuntime.consumeEnergyDuringBegin("
        );
        int targetIds = handler.indexOf(
                "instance.setTargetEntityIds(",
                energy
        );
        int cooldown = handler.indexOf(
                "WeaponSkillRuntime.commitCooldown(",
                targetIds
        );
        int initialize = handler.indexOf(
                "instance.initializeExecutionState(",
                cooldown
        );
        int presentation = handler.indexOf(
                "executionState.startPresentation(",
                initialize
        );
        assertTrue(energy >= 0);
        assertTrue(targetIds > energy);
        assertTrue(cooldown > targetIds);
        assertTrue(initialize > cooldown);
        assertTrue(presentation > initialize);
    }

    @Test
    void settlementResolvesOriginalDimensionsAndLeavesImpactToPost()
            throws IOException {
        String state = Files.readString(handlerRoot().resolve(
                "TemplarJudgementExecutionState.java"
        ));

        int targetLoop = state.indexOf(
                "for (TargetRef targetRef : targets)"
        );
        int dimension = state.indexOf(
                "server.getLevel(",
                targetLoop
        );
        int entity = state.indexOf(
                "targetLevel.getEntity(targetRef.entityId())",
                dimension
        );
        int alive = state.indexOf(
                "living.isAlive()",
                entity
        );
        assertTrue(targetLoop >= 0);
        assertTrue(dimension > targetLoop);
        assertTrue(entity > dimension);
        assertTrue(alive > entity);

        int settle = state.indexOf(
                "private void settle(SkillExecutionContext context)"
        );
        int resolved = state.indexOf(
                "for (LivingEntity target : resolveTargets(server))",
                settle
        );
        int context = state.indexOf(
                "createSettlementContext(",
                resolved
        );
        int damage = state.indexOf(
                "WeaponSkillDamage.apply(",
                context
        );
        int snapshot = state.indexOf("weaponSnapshot", damage);
        int bypass = state.indexOf(
                ".BYPASS_FOR_AUTHORED_SEQUENCE",
                snapshot
        );
        int settleEnd = state.indexOf(
                "private List<LivingEntity> resolveTargets(",
                bypass
        );
        String settlement = state.substring(settle, settleEnd);
        assertTrue(settle >= 0);
        assertTrue(resolved > settle);
        assertTrue(context > resolved);
        assertTrue(damage > context);
        assertTrue(snapshot > damage);
        assertTrue(bypass > snapshot);
        assertFalse(settlement.contains(
                "new TemplarJudgementImpactPayload("
        ));
        assertFalse(settlement.contains("PLAYER_ATTACK_CRIT"));
        assertFalse(state.contains("target.invulnerableTime = 0;"));
        assertFalse(state.contains("target.hurtTime = 0;"));
    }

    @Test
    void externalDamageSharingConsumesOnlyTypedFacades()
            throws IOException {
        Path skillRoot = handlerRoot().getParent();
        String consumer = Files.readString(skillRoot.resolve(
                "TemplarJudgementHandler.java"
        ));

        assertFalse(consumer.contains("TemplarJudgementTracker"));
        assertTrue(consumer.contains(
                "TemplarJudgementSkillHandler.isActive("
        ));
        assertTrue(consumer.contains(
                "TemplarJudgementSkillHandler.getMarkedTargets("
        ));
        assertTrue(consumer.contains(
                "TemplarJudgementSkillHandler.cappedSharedDamage("
        ));
    }

    private static Path handlerRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                "handler"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate handler source root");
    }
}
