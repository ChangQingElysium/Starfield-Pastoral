package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YetiToothSpineExecutionStateTest {
    @Test
    void fiveSpinesFillTheInclusiveOneHundredTwentyDegreeFan() {
        float baseYaw = 15.0F;

        assertEquals(
                -45.0F,
                YetiToothSpineExecutionState.angleForIndex(baseYaw, 0)
        );
        assertEquals(
                15.0F,
                YetiToothSpineExecutionState.angleForIndex(baseYaw, 2)
        );
        assertEquals(
                75.0F,
                YetiToothSpineExecutionState.angleForIndex(baseYaw, 4)
        );
        Vec3 forward = YetiToothSpineExecutionState.directionForAngle(
                0.0F
        );
        assertEquals(0.0D, forward.x, 1.0E-9D);
        assertEquals(0.0D, forward.y, 1.0E-9D);
        assertEquals(1.0D, forward.z, 1.0E-9D);
    }

    @Test
    void verticalAimFallsBackToThePlayersYaw() {
        Vec3 direction = YetiToothSpineExecutionState.horizontalLook(
                new Vec3(0.0D, 1.0D, 0.0D),
                90.0F
        );

        assertEquals(-1.0D, direction.x, 1.0E-9D);
        assertEquals(0.0D, direction.y, 1.0E-9D);
        assertEquals(0.0D, direction.z, 1.0E-9D);
    }

    @Test
    void beginPreservesEnergyCooldownSpawnAndAnimationOrder()
            throws IOException {
        String handler = source(
                "combat/skill/handler/YetiToothSpineSkillHandler.java"
        );

        int energy = handler.indexOf(
                "WeaponSkillRuntime.consumeEnergyDuringBegin("
        );
        int cooldown = handler.indexOf("WeaponSkillRuntime.commitCooldown(");
        int state = handler.indexOf("new YetiToothSpineExecutionState(");
        int initialize = handler.indexOf(
                "instance.initializeExecutionState(executionState)",
                state
        );
        int spawn = handler.indexOf(
                "executionState.spawnSpines(",
                initialize
        );
        int animation = handler.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                spawn
        );
        int lock = handler.indexOf(
                "WeaponSkillAnimationLock.setLock(",
                animation
        );
        assertTrue(energy >= 0);
        assertTrue(cooldown > energy);
        assertTrue(state > cooldown);
        assertTrue(initialize > state);
        assertTrue(spawn > initialize);
        assertTrue(animation > spawn);
        assertTrue(lock > animation);
        assertFalse(handler.contains("YetiToothSpineTracker"));
    }

    @Test
    void eachSuccessfulSpawnGetsExactLifoFailureOwnership()
            throws IOException {
        String state = source(
                "combat/skill/handler/YetiToothSpineExecutionState.java"
        );
        String handler = source(
                "combat/skill/handler/YetiToothSpineSkillHandler.java"
        );

        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertTrue(state.contains(
                "private final Set<UUID> spineIds = "
                        + "new LinkedHashSet<>()"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));

        int create = state.indexOf("new IceSpineEffectEntity(");
        int snapshot = state.indexOf("context.weaponSnapshot()", create);
        int add = state.indexOf("level.addFreshEntity(spine)", snapshot);
        int id = state.indexOf("UUID spineId = spine.getUUID()", add);
        int own = state.indexOf("spineIds.add(spineId)", id);
        int exactDiscard = state.indexOf(
                "discardSpine(level, spineId)",
                own
        );
        assertTrue(create >= 0);
        assertTrue(snapshot > create);
        assertTrue(add > snapshot);
        assertTrue(id > add);
        assertTrue(own > id);
        assertTrue(exactDiscard > own);
        assertFalse(state.contains("registerBeginFailureCleanup("));
        assertTrue(handler.contains("instance.registerCommittedEffect("));
    }

    @Test
    void tickAndEveryFinishPathUseTheTypedExactOwner()
            throws IOException {
        String handler = source(
                "combat/skill/handler/YetiToothSpineSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/YetiToothSpineExecutionState.java"
        );

        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(handler.contains(
                "YetiToothSpineExecutionState.class"
        ));
        assertTrue(handler.contains(
                ").isActive(context.player())"
        ));
        assertTrue(handler.contains(
                "instance.executionState(YetiToothSpineExecutionState.class)"
        ));
        assertTrue(handler.contains(
                "state.discardSpines("
        ));
        assertFalse(handler.contains(
                "reason == SkillInstance.EndReason"
        ));

        String discardAll = method(
                state,
                "void discardSpines(MinecraftServer server)"
        );
        assertTrue(discardAll.contains(
                "server.getLevel(dimension)"
        ));
        assertTrue(discardAll.contains(
                "for (UUID spineId : Set.copyOf(spineIds))"
        ));
        assertTrue(discardAll.contains(
                "discardSpine(level, spineId)"
        ));

        String discardOne = method(
                state,
                "private void discardSpine(ServerLevel level, UUID spineId)"
        );
        assertTrue(discardOne.contains("spineIds.remove(spineId)"));
        assertTrue(discardOne.contains("level.getEntity(spineId)"));
        assertTrue(discardOne.contains(
                "instanceof IceSpineEffectEntity spine"
        ));
        assertTrue(discardOne.contains("spine.discard()"));
    }

    @Test
    void runtimeEndsDeathAndDimensionChangeThroughFinishBeforeTick()
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
        assertTrue(runtime.substring(unavailable, tick).contains(
                "endExecution("
        ));
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
