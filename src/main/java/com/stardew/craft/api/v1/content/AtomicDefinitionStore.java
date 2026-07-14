package com.stardew.craft.api.v1.content;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Reusable atomic snapshot holder for datapack-backed definitions.
 * Candidates containing an error are rejected without changing the live snapshot.
 */
public final class AtomicDefinitionStore<T> {
    public static final String EMPTY_HASH = sha256(Map.of());

    private volatile DefinitionSnapshot<T> snapshot = DefinitionSnapshot.empty();

    public DefinitionSnapshot<T> snapshot() {
        return snapshot;
    }

    public synchronized void reset() {
        snapshot = DefinitionSnapshot.empty();
    }

    public synchronized ApplyResult<T> applyLocal(
            Map<ResourceLocation, T> definitions,
            Map<ResourceLocation, String> canonicalSources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        return apply(snapshot.version() + 1L, null, definitions, canonicalSources, diagnostics, false);
    }

    public synchronized ApplyResult<T> applyRemote(
            long version,
            String expectedHash,
            Map<ResourceLocation, T> definitions,
            Map<ResourceLocation, String> canonicalSources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        if (version < snapshot.version()) {
            return ApplyResult.rejected(snapshot, List.of(DefinitionDiagnostic.error(
                    null, null, "Received stale definition snapshot version " + version
                            + "; current version is " + snapshot.version())));
        }
        return apply(version, expectedHash, definitions, canonicalSources, diagnostics, true);
    }

    private ApplyResult<T> apply(
            long version,
            String expectedHash,
            Map<ResourceLocation, T> definitions,
            Map<ResourceLocation, String> canonicalSources,
            List<DefinitionDiagnostic> diagnostics,
            boolean remote
    ) {
        List<DefinitionDiagnostic> allDiagnostics = new ArrayList<>(diagnostics);
        if (allDiagnostics.stream().anyMatch(d -> d.severity() == DefinitionDiagnostic.Severity.ERROR)) {
            return ApplyResult.rejected(snapshot, allDiagnostics);
        }

        String actualHash = sha256(canonicalSources);
        if (remote && (expectedHash == null || !expectedHash.equals(actualHash))) {
            allDiagnostics.add(DefinitionDiagnostic.error(
                    null, null, "Definition snapshot hash mismatch: expected " + expectedHash
                            + ", got " + actualHash));
            return ApplyResult.rejected(snapshot, allDiagnostics);
        }

        if (version == snapshot.version() && actualHash.equals(snapshot.contentHash())) {
            return ApplyResult.accepted(snapshot, false);
        }
        if (remote && version == snapshot.version()) {
            allDiagnostics.add(DefinitionDiagnostic.error(
                    null, null, "Definition snapshot version " + version + " changed content hash"));
            return ApplyResult.rejected(snapshot, allDiagnostics);
        }

        DefinitionSnapshot<T> next = new DefinitionSnapshot<>(
                version, actualHash, definitions, allDiagnostics);
        snapshot = next;
        return ApplyResult.accepted(next, true);
    }

    private static String sha256(Map<ResourceLocation, String> sources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            sources.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        digest.update(entry.getKey().toString().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        digest.update(entry.getValue().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0xff);
                    });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ApplyResult<T>(
            boolean accepted,
            boolean changed,
            DefinitionSnapshot<T> snapshot,
            List<DefinitionDiagnostic> diagnostics
    ) {
        public ApplyResult {
            diagnostics = List.copyOf(diagnostics);
        }

        private static <T> ApplyResult<T> accepted(DefinitionSnapshot<T> snapshot, boolean changed) {
            return new ApplyResult<>(true, changed, snapshot, snapshot.diagnostics());
        }

        private static <T> ApplyResult<T> rejected(
                DefinitionSnapshot<T> snapshot,
                List<DefinitionDiagnostic> diagnostics
        ) {
            return new ApplyResult<>(false, false, snapshot, diagnostics);
        }
    }
}
