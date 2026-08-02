package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.combat.skill.DashMovementTracker;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeginFailureCleanupContractTest {
    private static final List<String> IMMEDIATE_DASH_HANDLERS = List.of(
            "CrystalDaggerLayerSkillHandler.java",
            "InsectDashSkillHandler.java",
            "SilverFoldbackSkillHandler.java",
            "StartrailRiftSkillHandler.java",
            "WindSpireThrustSkillHandler.java"
    );

    @Test
    void legacyDashEntryPointsRetainTheirBinaryDescriptors()
            throws NoSuchMethodException {
        assertPublicStaticVoid(DashMovementTracker.class.getDeclaredMethod(
                "start",
                ServerPlayer.class,
                long.class,
                Vec3.class,
                int.class
        ));
        assertPublicStaticVoid(DashMovementTracker.class.getDeclaredMethod(
                "onServerTick",
                ServerTickEvent.Post.class
        ));
        assertPublicStaticVoid(DashMovementTracker.class.getDeclaredMethod(
                "removePlayer",
                UUID.class
        ));
    }

    private static void assertPublicStaticVoid(Method method) {
        assertEquals(void.class, method.getReturnType());
        assertTrue(
                Modifier.isPublic(method.getModifiers()),
                method.toString()
        );
        assertTrue(
                Modifier.isStatic(method.getModifiers()),
                method.toString()
        );
    }

    @Test
    void runtimeCommitsSuccessfulBeginAndRollsBackFailedBeginFirst()
            throws IOException {
        String runtime = source("combat/skill/runtime/WeaponSkillRuntime.java");
        String execute = method(
                runtime,
                "private static InteractionResultHolder<ItemStack> execute("
        );

        int begin = execute.indexOf("handler.begin(context, instance)");
        int commit = execute.indexOf("instance.commitBegin()");
        int rollback = execute.indexOf("instance.rollbackBeginFailure(exception)");
        int end = execute.indexOf(
                "RuntimeException cleanupFailure = endExecution("
        );

        assertTrue(begin >= 0);
        assertTrue(commit > begin);
        assertTrue(rollback > commit);
        assertTrue(end > rollback);
    }

    @Test
    void committedEffectFailureStaysAcceptedAndNeverReopensRollback()
            throws IOException {
        String runtime = source("combat/skill/runtime/WeaponSkillRuntime.java");
        String execute = method(
                runtime,
                "private static InteractionResultHolder<ItemStack> execute("
        );
        int apply = execute.indexOf(
                "handler.applyCommittedEffects(context, instance)"
        );
        int committedCatch = execute.indexOf(
                "catch (RuntimeException exception)", apply
        );
        int acceptedReturn = execute.indexOf(
                "return InteractionResultHolder.sidedSuccess(stack, false)",
                committedCatch
        );
        assertTrue(apply >= 0);
        assertTrue(committedCatch > apply);
        assertTrue(acceptedReturn > committedCatch);

        String committedFailurePath = execute.substring(
                committedCatch,
                acceptedReturn
        );
        assertTrue(committedFailurePath.contains("endExecution("));
        assertFalse(committedFailurePath.contains("rollbackBeginFailure("));
        assertFalse(committedFailurePath.contains("sendFailure("));
        assertFalse(committedFailurePath.contains(
                "InteractionResultHolder.fail("
        ));
    }

    @Test
    void everyImmediateSharedDashRunsOnlyAfterCommit()
            throws IOException {
        for (String handlerFile : IMMEDIATE_DASH_HANDLERS) {
            String handler = source(
                    "combat/skill/handler/" + handlerFile
            );
            assertTrue(
                    handler.contains("instance.registerCommittedEffect("),
                    handlerFile
            );
            assertTrue(
                    handler.contains("DashMovementTracker.start("),
                    handlerFile
            );
            assertFalse(handler.contains(
                    "DashMovementTracker.startDuringBegin("
            ));
        }

        String dash = source("combat/skill/DashMovementTracker.java");
        assertTrue(dash.contains("instance.registerBeginFailureCleanup("));
        assertTrue(dash.contains("() -> cancel(player, handle)"));
    }

    @Test
    void exactDashStartsHaveExplicitExecutionOrFailureOwnership()
            throws IOException {
        Path mainJava = findMainJavaRoot();
        List<Path> exactCallers = callers(
                mainJava,
                "DashMovementTracker.startExact("
        );
        List<Path> legacyCallers = callers(
                mainJava,
                "DashMovementTracker.start("
        );

        assertEquals(
                List.of(
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "DragonBreathThrustExecutionState.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "SingularityEvolveExecutionState.java")
                ),
                exactCallers
        );
        assertEquals(
                List.of(
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "CrystalDaggerLayerSkillHandler.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "InsectDashSkillHandler.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "SilverFoldbackSkillHandler.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "StartrailRiftSkillHandler.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "WindSpireThrustSkillHandler.java")
                ),
                legacyCallers
        );

        String dragon = source(
                "combat/skill/handler/DragonBreathThrustExecutionState.java"
        );
        assertTrue(dragon.contains("DashMovementTracker.Handle movement"));
        assertTrue(dragon.contains("DashMovementTracker.cancel(player, movement)"));

        String singularity = source(
                "combat/skill/handler/SingularityEvolveExecutionState.java"
        );
        String rift = source(
                "combat/skill/RiftPathDamageTracker.java"
        );
        assertTrue(singularity.contains(
                "private DashMovementTracker.Handle movementHandle;"
        ));
        assertTrue(singularity.contains(
                "DashMovementTracker.cancel(player, movementHandle)"
        ));
        assertTrue(singularity.contains(
                "private RiftPathDamageTracker.Handle riftHandle;"
        ));
        assertTrue(singularity.contains(
                "RiftPathDamageTracker.cancel(player, riftHandle)"
        ));
        assertTrue(rift.contains("public record Handle("));
        assertTrue(rift.contains("state.pathId.equals(handle.pathId())"));
        assertTrue(rift.contains(
                "ACTIVE.remove(handle.playerId(), state)"
        ));

        String settlement = method(
                singularity,
                "private void resolveAndReleaseDetachedResources("
        );
        int movementStart = settlement.indexOf(
                "movementHandle = dashForward(player, nowTick)"
        );
        int riftStart = settlement.indexOf(
                "startRift(player, level)"
        );
        int slash = settlement.indexOf("applySlashAfterDash(");
        int release = settlement.indexOf(
                "detachedResourcesReleased = true"
        );
        int failure = settlement.indexOf(
                "catch (RuntimeException exception)"
        );
        int cleanup = settlement.indexOf(
                "cancelOwnedDetachedResources(player, exception)"
        );
        assertTrue(movementStart >= 0);
        assertTrue(riftStart > movementStart);
        assertTrue(slash > riftStart);
        assertTrue(release > slash);
        assertTrue(failure > slash);
        assertTrue(cleanup > failure);
        assertTrue(singularity.contains(
                "riftHandle = RiftPathDamageTracker.startExact("
        ));

        String detachedCleanup = method(
                singularity,
                "private void cancelOwnedDetachedResources("
        );
        assertTrue(detachedCleanup.indexOf(
                "RiftPathDamageTracker.cancel(player, riftHandle)"
        ) < detachedCleanup.indexOf(
                "DashMovementTracker.cancel(player, movementHandle)"
        ));

        String riftTick = method(rift, "public static void tick(");
        String conditionalRemove =
                "ACTIVE.remove(player.getUUID(), state)";
        assertEquals(2, occurrences(riftTick, conditionalRemove));
        int finalBurst = riftTick.indexOf("applyFinalBurst(");
        int finalFinally = riftTick.indexOf("finally", finalBurst);
        int finalRemove = riftTick.indexOf(
                conditionalRemove,
                finalFinally
        );
        assertTrue(finalBurst >= 0);
        assertTrue(finalFinally > finalBurst);
        assertTrue(finalRemove > finalFinally);
    }

    @Test
    void singularityEvolutionUsesExactStateOwnershipAtEveryBoundary()
            throws IOException {
        String handler = source(
                "combat/skill/handler/SingularityEvolveSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/SingularityEvolveExecutionState.java"
        );

        assertTrue(handler.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertTrue(handler.contains(
                "SingularityEvolveExecutionState executionState"
        ));
        String handlerTick = method(handler, "public SkillTickResult tick(");
        assertTrue(handlerTick.contains(
                "SingularityEvolveExecutionState.class"
        ));
        assertTrue(handler.contains(
                "instance.executionState(SingularityEvolveExecutionState.class)"
        ));
        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertTrue(handler.contains("instance.registerCommittedEffect("));
        assertFalse(state.contains("registerBeginFailureCleanup("));
        assertTrue(state.contains(
                "if (!detachedResourcesReleased)"
        ));
        assertTrue(state.contains(
                "cancelOwnedDetachedResources(player, null)"
        ));
    }

    private static List<Path> callers(Path mainJava, String token)
            throws IOException {
        try (var sources = Files.walk(mainJava)) {
            return sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("DashMovementTracker.java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains(token);
                        } catch (IOException exception) {
                            throw new SourceReadException(exception);
                        }
                    })
                    .map(mainJava::relativize)
                    .sorted()
                    .toList();
        } catch (SourceReadException exception) {
            throw exception.ioException;
        }
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int openingBrace = source.indexOf('{', start);
        assertTrue(openingBrace > start, "Missing method body " + signature);

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

    private static String source(String relativeFile) throws IOException {
        return Files.readString(findMainJavaRoot().resolve(Path.of(
                "com",
                "stardew",
                "craft"
        )).resolve(relativeFile));
    }

    private static Path findMainJavaRoot() throws IOException {
        Path relative = Path.of("src", "main", "java");
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }

    private static final class SourceReadException extends RuntimeException {
        private final IOException ioException;

        private SourceReadException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }
    }
}
