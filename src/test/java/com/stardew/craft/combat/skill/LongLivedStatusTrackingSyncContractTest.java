package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongLivedStatusTrackingSyncContractTest {
    private static final List<TrackingStatus> TRACKED_STATUSES = List.of(
            new TrackingStatus("YetiFreezeTracker.java", "YetiFreezePayload"),
            new TrackingStatus("YetiToothMarkTracker.java", "YetiToothMarkPayload"),
            new TrackingStatus("ElfBladeMarkTracker.java", "ElfBladeMarkPayload"),
            new TrackingStatus("GalaxyDaggerMarkTracker.java", "GalaxyDaggerMarkPayload"),
            new TrackingStatus("InfinityDaggerMarkTracker.java", "InfinityDaggerMarkPayload"),
            new TrackingStatus("OssifiedMarkTracker.java", "OssifiedMarkPayload"),
            new TrackingStatus("LavaKatanaMarkTracker.java", "LavaKatanaMarkPayload"),
            new TrackingStatus("TideMarkTracker.java", "TideMarkPayload")
    );

    @Test
    void everyLongLivedClientStatusResyncsWhenTrackingStarts()
            throws IOException {
        for (TrackingStatus status : TRACKED_STATUSES) {
            String source = skillSource(status.file());
            String body = method(
                    source,
                    "public static void onStartTracking("
            );
            assertTrue(body.contains("PlayerEvent.StartTracking"), status.file());
            assertTrue(body.contains("PacketDistributor.sendToPlayer("), status.file());
            assertTrue(body.contains("new " + status.payload() + "("), status.file());
        }
    }

    @Test
    void directFreezeCallersDeclareTheirPresentationPolicy()
            throws IOException {
        assertAppliedFreezePolicy(
                "static void applyDragonBreathThrust(",
                "YetiFreezeTracker.applyPreAdjusted(",
                "SERVER_ONLY_STAGGER"
        );
        assertAppliedFreezePolicy(
                "static void applyGalaxyDaggerStarleap(",
                "YetiFreezeTracker.applyWithEquipmentProtection(",
                "SYNC_FREEZE_OVERLAY"
        );
        assertAppliedFreezePolicy(
                "static void applyDragontoothShivStab(",
                "YetiFreezeTracker.applyWithEquipmentProtection(",
                "SYNC_FREEZE_OVERLAY"
        );
        assertFreezePolicy(
                "handler/InfinityDaggerSingularityBackstabSkillHandler.java",
                "SYNC_FREEZE_OVERLAY"
        );
    }

    private static void assertAppliedFreezePolicy(
            String methodSignature,
            String applyCall,
            String policy
    ) throws IOException {
        String source = Files.readString(findMainJavaRoot().resolve(Path.of(
                "com",
                "stardew",
                "craft",
                "combat",
                "BuiltinSkillAppliedHitRules.java"
        )));
        String body = method(source, methodSignature);
        assertTrue(body.contains(applyCall), methodSignature);
        assertTrue(
                body.replaceAll("\\s+", "").contains(
                        "YetiFreezeTracker.PresentationPolicy." + policy
                ),
                methodSignature
        );
    }

    @Test
    void remainingTickHelpersClampAtTheStatusBoundary() {
        assertEquals(7, YetiFreezeTracker.remainingDurationTicks(13L, 20L));
        assertEquals(0, YetiFreezeTracker.remainingDurationTicks(20L, 20L));
        assertEquals(7, YetiToothMarkTracker.remainingDurationTicks(13L, 20L));
        assertEquals(7, ElfBladeMarkTracker.remainingDurationTicks(13L, 20L));
        assertEquals(7, GalaxyDaggerMarkTracker.remainingDurationTicks(13L, 20L));
        assertEquals(7, InfinityDaggerMarkTracker.remainingDurationTicks(13L, 20L));
        assertEquals(7, OssifiedMarkTracker.remainingDurationTicks(13L, 20L));
    }

    private static void assertFreezePolicy(String file, String policy)
            throws IOException {
        String source = skillSource(file);
        String compactSource = source.replaceAll("\\s+", "");
        assertEquals(
                1,
                occurrences(source, "YetiFreezeTracker.applyWithEquipmentProtection(")
        );
        assertTrue(
                compactSource.contains(
                        "YetiFreezeTracker.PresentationPolicy." + policy
                ),
                file
        );
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
        assertTrue(openingBrace > start, "Missing body " + signature);
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

    private static String skillSource(String relativeFile) throws IOException {
        return Files.readString(findMainJavaRoot().resolve(Path.of(
                "com",
                "stardew",
                "craft",
                "combat",
                "skill"
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

    private record TrackingStatus(String file, String payload) {
    }
}
