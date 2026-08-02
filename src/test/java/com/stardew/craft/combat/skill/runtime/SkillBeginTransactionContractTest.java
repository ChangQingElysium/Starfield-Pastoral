package com.stardew.craft.combat.skill.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillBeginTransactionContractTest {
    @Test
    void committedEffectsRunOnlyAfterCommit() {
        List<String> actions = new ArrayList<>();
        SkillInstance committed = instance(1);
        committed.activate();
        committed.registerCommittedEffect(() -> actions.add("effect"));
        assertTrue(actions.isEmpty());
        committed.commitBegin();
        assertTrue(actions.isEmpty());
        committed.runCommittedEffects();
        assertEquals(List.of("effect"), actions);
    }

    @Test
    void beginRollbackDiscardsCommittedEffects() {
        List<String> actions = new ArrayList<>();
        SkillInstance failed = instance(2);
        failed.activate();
        failed.registerBeginFailureCleanup(() -> actions.add("rollback"));
        failed.registerCommittedEffect(() -> actions.add("effect"));
        failed.rollbackBeginFailure(new RuntimeException("begin"));
        assertEquals(List.of("rollback"), actions);
        assertThrows(
                IllegalStateException.class,
                failed::runCommittedEffects
        );
    }

    @Test
    void committedEffectFailureCannotReopenBeginCompensation() {
        List<String> actions = new ArrayList<>();
        SkillInstance instance = instance(3);
        instance.activate();
        instance.registerBeginFailureCleanup(() -> actions.add("rollback"));
        instance.registerCommittedEffect(() -> {
            actions.add("effect");
            throw new IllegalStateException("notification");
        });
        instance.commitBegin();

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                instance::runCommittedEffects
        );
        instance.rollbackBeginFailure(failure);

        assertEquals(List.of("effect"), actions);
        assertTrue(instance.beginCommitted());
    }

    @Test
    void detachedAndResourceOwnersRegisterBeginCompensation()
            throws IOException {
        String billet = source(
                "combat/skill/handler/TemperedBilletSkillHandler.java"
        );
        assertTrue(billet.contains("instance.registerCommittedEffect("));
        assertTrue(billet.contains(
                "TemperedFireRingTracker.beginBilletCastDuringBegin("
        ));
        assertTrue(billet.contains(
                "WeaponSkillRuntime.consumeEnergyDuringBegin("
        ));

        String insect = source(
                "combat/skill/handler/InsectDashSkillHandler.java"
        );
        String insectState = source(
                "combat/skill/handler/InsectDashExecutionState.java"
        );
        assertTrue(insectState.contains("InsectDashChainState.setStage("));
        assertTrue(insectState.contains("InsectDashChainState.clear("));
        assertFalse(insect.contains("setStageDuringBegin("));
        assertFalse(insect.contains("clearDuringBegin("));
        assertTrue(insect.contains(
                "WeaponSkillRuntime.consumeEnergyDuringBegin("
        ));

        String silver = source(
                "combat/skill/handler/SilverFoldbackSkillHandler.java"
        );
        assertTrue(silver.contains("instance.registerCommittedEffect("));
        assertFalse(silver.contains("executeReturnStrikeDuringBegin("));

        String wind = source(
                "combat/skill/handler/WindSpireThrustSkillHandler.java"
        );
        assertTrue(wind.contains("WindSpireTracker.start("));
        assertTrue(wind.contains("instance.registerCommittedEffect("));

        String tide = source(
                "combat/skill/handler/TideAnchorSkillHandler.java"
        );
        assertTrue(tide.contains("instance.registerCommittedEffect("));

        String galaxy = source(
                "combat/skill/handler/GalaxyDaggerStarleapSkillHandler.java"
        );
        assertTrue(galaxy.contains(
                "GalaxyDaggerMarkTracker.consumeDuringBegin("
        ));
        assertTrue(galaxy.contains("instance.registerCommittedEffect("));

        String infinity = source(
                "combat/skill/handler/InfinityDaggerSingularityBackstabSkillHandler.java"
        );
        assertTrue(infinity.contains(
                "InfinityDaggerMarkTracker.consumeDuringBegin("
        ));
        assertTrue(infinity.contains("instance.registerCommittedEffect("));
    }

    @Test
    void consumedResourcesRestoreThroughBeginCleanup() throws IOException {
        assertRollback(
                "combat/skill/handler/EternalCollapseSkillHandler.java",
                "SingularityTracker.setStacks("
        );
        assertRollback(
                "combat/skill/handler/GalaxyJudgementSkillHandler.java",
                "StartrailTracker.setStacks("
        );
        assertRollback(
                "combat/skill/handler/DragonBreathJudgementSkillHandler.java",
                "DragonBreathTracker.setStacks("
        );
        assertRollback(
                "combat/skill/handler/TideReelSkillHandler.java",
                "BrokenTridentCatchTracker.restore("
        );
    }

    @Test
    void runtimeHandlersCannotBypassTransactionalEnergyPayment()
            throws IOException {
        Path handlerRoot = mainJavaRoot().resolve(Path.of(
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                "handler"
        ));
        try (Stream<Path> paths = Files.walk(handlerRoot)) {
            List<Path> bypasses = paths
                    .filter(path -> path.toString().endsWith(
                            "SkillHandler.java"
                    ))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains(
                                    "PlayerStardewDataAPI.consumeEnergy("
                            );
                        } catch (IOException exception) {
                            throw new SourceReadException(exception);
                        }
                    })
                    .map(handlerRoot::relativize)
                    .sorted()
                    .toList();
            assertTrue(bypasses.isEmpty(), bypasses.toString());
        } catch (SourceReadException exception) {
            throw exception.ioException;
        }
    }

    @Test
    void irreversibleEffectsCannotExecuteDirectlyDuringBegin()
            throws IOException {
        Path handlerRoot = mainJavaRoot().resolve(Path.of(
                "com", "stardew", "craft", "combat", "skill", "handler"
        ));
        List<String> forbidden = List.of(
                "WeaponSkillDamage.apply(",
                ".addFreshEntity(",
                ".teleportTo(",
                ".setPos(",
                ".addEffect(",
                "DashMovementTracker.start(",
                "attackInitialTarget(",
                "TideMarkTracker.apply(",
                "OssifiedMarkTracker.apply(",
                "LavaKatanaMarkTracker.apply(",
                "LavaKatanaMarkTracker.ensureHeatAtLeast(",
                ".spawnSpines(",
                "PacketDistributor.sendToPlayer(",
                "DarkSwordEffects.playBloodMoonStart(",
                "HolyBladeEffects.playDomainActivate("
        );
        try (Stream<Path> paths = Files.walk(handlerRoot)) {
            List<String> violations = paths
                    .filter(path -> path.toString().endsWith(
                            "SkillHandler.java"
                    ))
                    .flatMap(path -> {
                        try {
                            String begin = beginBody(Files.readString(path));
                            String prepareOnly = withoutCommittedEffects(begin);
                            return forbidden.stream()
                                    .filter(prepareOnly::contains)
                                    .map(token -> handlerRoot.relativize(path)
                                            + " contains " + token);
                        } catch (IOException exception) {
                            throw new SourceReadException(exception);
                        }
                    })
                    .sorted()
                    .toList();
            assertTrue(violations.isEmpty(), violations.toString());
        } catch (SourceReadException exception) {
            throw exception.ioException;
        }
    }

    @Test
    void sharedPresentationUtilitiesDeferDuringPreparation()
            throws IOException {
        String dispatcher = source(
                "combat/skill/WeaponSkillAnimationDispatcher.java"
        );
        String lock = source(
                "combat/skill/WeaponSkillAnimationLock.java"
        );
        assertTrue(dispatcher.contains(
                "WeaponSkillRuntime.deferIfPreparing("
        ));
        assertTrue(lock.contains(
                "WeaponSkillRuntime.deferIfPreparing("
        ));
    }

    private static String beginBody(String source) {
        int signature = source.indexOf("void begin(");
        if (signature < 0) {
            return "";
        }
        int open = source.indexOf('{', signature);
        int close = matchingDelimiter(source, open, '{', '}');
        return source.substring(open + 1, close);
    }

    private static String withoutCommittedEffects(String beginBody) {
        String marker = "instance.registerCommittedEffect(";
        StringBuilder remaining = new StringBuilder(beginBody);
        int start = remaining.indexOf(marker);
        while (start >= 0) {
            int open = start + marker.length() - 1;
            int close = matchingDelimiter(
                    remaining.toString(),
                    open,
                    '(',
                    ')'
            );
            int end = close + 1;
            while (end < remaining.length()
                    && Character.isWhitespace(remaining.charAt(end))) {
                end++;
            }
            if (end < remaining.length()
                    && remaining.charAt(end) == ';') {
                end++;
            }
            remaining.delete(start, end);
            start = remaining.indexOf(marker);
        }
        return remaining.toString();
    }

    private static int matchingDelimiter(
            String source,
            int open,
            char opening,
            char closing
    ) {
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == opening) {
                depth++;
            } else if (current == closing && --depth == 0) {
                return index;
            }
        }
        throw new IllegalArgumentException("Unbalanced Java source");
    }

    private static void assertRollback(String relative, String restore)
            throws IOException {
        String source = source(relative);
        assertTrue(source.contains("instance.registerBeginFailureCleanup("));
        assertTrue(source.contains(restore));
    }

    private static SkillInstance instance(int suffix) {
        return new SkillInstance(
                new UUID(0L, suffix),
                new UUID(1L, suffix),
                suffix,
                id("weapon"),
                id("skill"),
                100L,
                Vec3.ZERO,
                new Vec3(0.0, 0.0, 1.0),
                suffix
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("stardewcraft", path);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(mainJavaRoot().resolve(Path.of(
                "com",
                "stardew",
                "craft"
        )).resolve(relative));
    }

    private static Path mainJavaRoot() throws IOException {
        Path relativeRoot = Path.of("src", "main", "java");
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path root = current.resolve(relativeRoot);
            if (Files.isDirectory(root)) {
                return root;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relativeRoot);
    }

    private static final class SourceReadException extends RuntimeException {
        private final IOException ioException;

        private SourceReadException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }
    }
}
