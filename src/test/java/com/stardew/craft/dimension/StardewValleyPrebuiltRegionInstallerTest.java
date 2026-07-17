package com.stardew.craft.dimension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewValleyPrebuiltRegionInstallerTest {

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void parsesVerifiedCopyAndDeleteEntries() throws Exception {
        List<StardewValleyPrebuiltRegionInstaller.ManifestEntry> entries = parse("""
            # pregen manifest v2
            copy r.0.0.mca 3 %s
            delete r.0.1.mca
            """.formatted(HASH));

        assertEquals(2, entries.size());
        assertEquals(StardewValleyPrebuiltRegionInstaller.ManifestAction.COPY, entries.get(0).action());
        assertEquals(3L, entries.get(0).size());
        assertEquals(StardewValleyPrebuiltRegionInstaller.ManifestAction.DELETE, entries.get(1).action());
    }

    @Test
    void rejectsDuplicateAndProtectedRegionEntries() {
        assertThrows(IOException.class, () -> parse("""
            delete r.0.0.mca
            delete r.0.0.mca
            """));
        assertThrows(IOException.class, () -> parse("delete r.40.40.mca\n"));
        assertThrows(IOException.class, () -> parse("delete ../r.0.0.mca\n"));
    }

    @Test
    void installedStateRequiresCopiesButAllowsNormalRegionMutation(@TempDir Path regionDir) throws Exception {
        List<StardewValleyPrebuiltRegionInstaller.ManifestEntry> entries = parse("""
            copy r.0.0.mca 3 %s
            delete r.0.1.mca
            """.formatted(HASH));

        Files.write(regionDir.resolve("r.0.0.mca"), new byte[] {1, 2, 3, 4, 5});
        assertTrue(StardewValleyPrebuiltRegionInstaller.installedCopyFilesPresent(regionDir, entries));

        Files.createFile(regionDir.resolve("r.0.1.mca"));
        assertTrue(StardewValleyPrebuiltRegionInstaller.installedCopyFilesPresent(regionDir, entries));

        Files.delete(regionDir.resolve("r.0.0.mca"));
        assertFalse(StardewValleyPrebuiltRegionInstaller.installedCopyFilesPresent(regionDir, entries));
    }

    @Test
    void repairStagesOnlyMissingCopyFiles(@TempDir Path regionDir) throws Exception {
        List<StardewValleyPrebuiltRegionInstaller.ManifestEntry> entries = parse("""
            copy r.0.0.mca 3 %s
            copy r.0.1.mca 3 %s
            delete r.0.2.mca
            """.formatted(HASH, HASH));
        Files.write(regionDir.resolve("r.0.0.mca"), new byte[] {9});

        List<StardewValleyPrebuiltRegionInstaller.ManifestEntry> repair =
            StardewValleyPrebuiltRegionInstaller.copyEntriesRequired(regionDir, entries, false);
        assertEquals(List.of("r.0.1.mca"), repair.stream().map(
            StardewValleyPrebuiltRegionInstaller.ManifestEntry::fileName).toList());

        List<StardewValleyPrebuiltRegionInstaller.ManifestEntry> replacement =
            StardewValleyPrebuiltRegionInstaller.copyEntriesRequired(regionDir, entries, true);
        assertEquals(2, replacement.size());
    }

    @Test
    void bundledManifestMatchesEveryCopiedRegionResource() throws Exception {
        ClassLoader loader = StardewValleyPrebuiltRegionInstaller.class.getClassLoader();
        try (InputStream manifest = loader.getResourceAsStream("pregen/stardew_valley/region_manifest.txt")) {
            assertNotNull(manifest);
            for (StardewValleyPrebuiltRegionInstaller.ManifestEntry entry
                    : StardewValleyPrebuiltRegionInstaller.readManifest(manifest)) {
                if (entry.action() != StardewValleyPrebuiltRegionInstaller.ManifestAction.COPY) {
                    continue;
                }
                String resourcePath = "pregen/stardew_valley/region/" + entry.fileName();
                try (InputStream resource = loader.getResourceAsStream(resourcePath)) {
                    assertNotNull(resource, resourcePath);
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    long size;
                    try (DigestInputStream verified = new DigestInputStream(resource, digest)) {
                        size = verified.transferTo(OutputStream.nullOutputStream());
                    }
                    assertEquals(entry.size(), size, resourcePath);
                    assertEquals(entry.sha256(), HexFormat.of().formatHex(digest.digest()), resourcePath);
                }
            }
        }
    }

    @Test
    void prebuiltMapStateNeedsNoSchematicBoundsOrMask() {
        StardewValleyMapBootstrap.MapSavedData data = new StardewValleyMapBootstrap.MapSavedData();

        assertTrue(data.markPrebuiltInstalled(9));
        assertTrue(data.hasAppliedMap());
        assertEquals(0, data.nonAirMask().length);
        assertFalse(data.markPrebuiltInstalled(9));
        assertTrue(data.markPrebuiltInstalled(10));
    }

    private static List<StardewValleyPrebuiltRegionInstaller.ManifestEntry> parse(String manifest) throws Exception {
        return StardewValleyPrebuiltRegionInstaller.readManifest(
            new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8))
        );
    }
}
