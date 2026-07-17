package com.stardew.craft.dimension;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新存档启动时安装预烘焙的星露谷区块文件（.mca）。
 *
 * 资源约定：
 * - pregen/stardew_valley/region_manifest.txt
 * - pregen/stardew_valley/region/<filename>.mca
 */
@SuppressWarnings("null")
public final class StardewValleyPrebuiltRegionInstaller {
    private StardewValleyPrebuiltRegionInstaller() {}

    private static final String MANIFEST_RESOURCE = "pregen/stardew_valley/region_manifest.txt";
    private static final String REGION_RESOURCE_PREFIX = "pregen/stardew_valley/region/";
    private static final String MARKER_FILE = "stardew_valley_pregen_installed.marker";
    private static final String RELOCATION_MARKER_FILE = "stardew_valley_pregen_relocation.marker";
    private static final String STAGING_DIRECTORY = ".stardew_valley_pregen_stage";
    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final int REGION_BLOCK_SIZE = 512;
    private static final int PROTECTED_PLAYER_REGION_MIN = 36;

    /**
     * 预置地图版本号。改动 jar 里的 .mca 文件后把这个数 +1，
     * 老存档下次启动会检测到 marker 里 version 过期 → 覆盖安装 → 镇子更新到最新版。
     *
     * 注意：覆盖会抹掉玩家在 pregen 区域内放的方块（比如摆了椅子、铺了地板之类）。
     * 所以每次 +1 都是"强制小镇重置"的操作，要和版本发布节奏绑定。
     */
    public static final int CURRENT_PREGEN_VERSION = 10;

    private static final String MARKER_VERSION_PREFIX = "version=";
    private static final String MARKER_MANIFEST_PREFIX = "manifest=";

    public enum InstallResult {
        INSTALLED,
        UPGRADED,
        REPAIRED,
        ALREADY_PRESENT,
        NO_PREBUILT,
        FAILED;

        public boolean changedWorldFiles() {
            return this == INSTALLED || this == UPGRADED || this == REPAIRED;
        }
    }

    public static InstallResult installIfAvailable(MinecraftServer server) {
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            Path marker = worldRoot.resolve(MARKER_FILE);
            Path targetDimensionDir = getTargetDimensionDir(worldRoot);
            Path targetRegionDir = targetDimensionDir.resolve("region");
            Path targetEntitiesDir = targetDimensionDir.resolve("entities");
            Path targetPoiDir = targetDimensionDir.resolve("poi");

            List<ManifestEntry> entries = readManifest();
            if (entries.isEmpty()) {
                return InstallResult.NO_PREBUILT;
            }
            String manifestHash = manifestFingerprint(entries);

            boolean hasRegions = hasAnyRegionFiles(targetRegionDir);
            boolean hasMarker = Files.exists(marker);
            MarkerState markerState = hasMarker ? readMarker(marker) : new MarkerState(0, "");
            int installedVersion = markerState.version();

            if (installedVersion > CURRENT_PREGEN_VERSION) {
                StardewCraft.LOGGER.error(
                    "[VALLEY_PREGEN] Refusing map downgrade: installed version {} is newer than bundled version {}",
                    installedVersion, CURRENT_PREGEN_VERSION);
                return InstallResult.FAILED;
            }

            boolean manifestMatches = manifestHash.equals(markerState.manifestHash());
            if (hasMarker
                    && installedVersion >= CURRENT_PREGEN_VERSION
                    && manifestMatches
                    && installedCopyFilesPresent(targetRegionDir, entries)) {
                return InstallResult.ALREADY_PRESENT;
            }

            // 到这里意味着需要（重新）安装 — 全新存档、老 marker 缺 version、或显式版本过期。
            // 无 marker 但有 region 文件 = 老存档第一次跑带版本管理的 installer，installedVersion=0 < CURRENT → 升级。
            boolean upgrading = installedVersion < CURRENT_PREGEN_VERSION && (hasMarker || hasRegions);
            boolean repairing = !upgrading && (hasMarker || hasRegions);
            boolean replaceAllManagedFiles = !hasRegions || upgrading || !manifestMatches;
            if (upgrading) {
                StardewCraft.LOGGER.warn(
                    "[VALLEY_PREGEN] Upgrading pregen regions: {} -> {}. Player changes inside pregen area will be overwritten.",
                    installedVersion, CURRENT_PREGEN_VERSION);
            } else if (repairing) {
                StardewCraft.LOGGER.warn(
                    "[VALLEY_PREGEN] Repairing incomplete pregen installation at version {} (fullReplacement={}).",
                    installedVersion, replaceAllManagedFiles);
            }

            Path stagingDir = worldRoot.resolve(STAGING_DIRECTORY);
            deleteRecursively(stagingDir);
            Files.createDirectories(stagingDir);

            List<ManifestEntry> entriesToCopy = copyEntriesRequired(
                targetRegionDir, entries, replaceAllManagedFiles);
            try {
                stageManifestEntries(stagingDir, entriesToCopy);
            } catch (Exception e) {
                cleanupStagingDirectory(stagingDir);
                StardewCraft.LOGGER.error("[VALLEY_PREGEN] Failed staging prebuilt regions: {}", e.getMessage(), e);
                return InstallResult.FAILED;
            }

            try {
                Files.createDirectories(targetRegionDir);

                // 先完整 staging，再进入破坏性替换阶段。COPY 文件留到 atomic replace；
                // DELETE tombstone 以及新 manifest 已移除的旧公共 region 会被删除。
                Set<String> copyFileNames = new HashSet<>();
                for (ManifestEntry entry : entries) {
                    if (entry.action() == ManifestAction.COPY) {
                        copyFileNames.add(entry.fileName().toLowerCase());
                    }
                }
                int deleted = 0;
                if (replaceAllManagedFiles) {
                    deleted += cleanManagedMcaFiles(targetRegionDir, copyFileNames, true);
                    deleted += cleanManagedMcaFiles(targetEntitiesDir, Set.of(), false);
                    deleted += cleanManagedMcaFiles(targetPoiDir, Set.of(), false);
                }
                if (deleted > 0) {
                    StardewCraft.LOGGER.info("[VALLEY_PREGEN] Removed {} old pregen/interior .mca files", deleted);
                }

                Set<String> stagedCopyNames = new HashSet<>();
                for (ManifestEntry entry : entriesToCopy) {
                    stagedCopyNames.add(entry.fileName().toLowerCase());
                }
                int copied = 0;
                for (ManifestEntry entry : entries) {
                    Path target = targetRegionDir.resolve(entry.fileName());
                    if (entry.action() == ManifestAction.DELETE && replaceAllManagedFiles) {
                        Files.deleteIfExists(target);
                    } else if (entry.action() == ManifestAction.COPY
                            && stagedCopyNames.contains(entry.fileName().toLowerCase())) {
                        moveReplacing(stagingDir.resolve(entry.fileName()), target);
                        copied++;
                    }
                }

                writeMarker(marker, copyFileNames.size(), CURRENT_PREGEN_VERSION, manifestHash);
                if (upgrading || repairing) {
                    writeRelocationMarker(worldRoot.resolve(RELOCATION_MARKER_FILE), CURRENT_PREGEN_VERSION);
                }

                String operation = upgrading ? "Upgraded" : repairing ? "Repaired" : "Installed";
                StardewCraft.LOGGER.info("[VALLEY_PREGEN] {} prebuilt regions (version {}): {} copied, {} managed",
                    operation, CURRENT_PREGEN_VERSION, copied, copyFileNames.size());
                return upgrading ? InstallResult.UPGRADED : repairing ? InstallResult.REPAIRED : InstallResult.INSTALLED;
            } finally {
                cleanupStagingDirectory(stagingDir);
            }
        } catch (Exception e) {
            StardewCraft.LOGGER.error("[VALLEY_PREGEN] Failed installing prebuilt regions: {}", e.getMessage(), e);
            return InstallResult.FAILED;
        }
    }

    private static void writeMarker(Path marker, int fileCount, int version, String manifestHash) throws IOException {
        Path temporary = marker.resolveSibling(marker.getFileName() + ".tmp");
        Files.writeString(temporary,
            "installed=" + fileCount + "\n"
                + MARKER_VERSION_PREFIX + version + "\n"
                + MARKER_MANIFEST_PREFIX + manifestHash + "\n",
            StandardCharsets.UTF_8);
        moveReplacing(temporary, marker);
    }

    private static void writeRelocationMarker(Path marker, int version) throws IOException {
        Path temporary = marker.resolveSibling(marker.getFileName() + ".tmp");
        Files.writeString(temporary, MARKER_VERSION_PREFIX + version + "\n", StandardCharsets.UTF_8);
        moveReplacing(temporary, marker);
    }

    public static int getRequiredRelocationVersion(MinecraftServer server) {
        try {
            Path marker = server.getWorldPath(LevelResource.ROOT).resolve(RELOCATION_MARKER_FILE);
            if (!Files.exists(marker)) {
                return 0;
            }
            return readMarker(marker).version();
        } catch (Exception e) {
            StardewCraft.LOGGER.warn("[VALLEY_PREGEN] Failed reading relocation marker: {}", e.getMessage());
            return 0;
        }
    }

    private static MarkerState readMarker(Path marker) {
        int version = 1;
        String manifestHash = "";
        try (BufferedReader r = Files.newBufferedReader(marker, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith(MARKER_VERSION_PREFIX)) {
                    try {
                        version = Integer.parseInt(trimmed.substring(MARKER_VERSION_PREFIX.length()).trim());
                    } catch (NumberFormatException ignored) {
                        version = 1;
                    }
                } else if (trimmed.startsWith(MARKER_MANIFEST_PREFIX)) {
                    manifestHash = trimmed.substring(MARKER_MANIFEST_PREFIX.length()).trim().toLowerCase();
                }
            }
        } catch (IOException ignored) {}
        return new MarkerState(version, manifestHash);
    }

    public static boolean hasInstalledPrebuilt(MinecraftServer server) {
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            Path marker = worldRoot.resolve(MARKER_FILE);
            if (!Files.exists(marker)) {
                return false;
            }
            Path targetRegionDir = getTargetRegionDir(worldRoot);
            List<ManifestEntry> entries = readManifest();
            MarkerState state = readMarker(marker);
            return !entries.isEmpty()
                && state.version() >= CURRENT_PREGEN_VERSION
                && manifestFingerprint(entries).equals(state.manifestHash())
                && installedCopyFilesPresent(targetRegionDir, entries);
        } catch (Exception e) {
            StardewCraft.LOGGER.warn("[VALLEY_PREGEN] Failed checking prebuilt state: {}", e.getMessage());
            return false;
        }
    }

    public static BlockPos getInstalledRegionCenter(MinecraftServer server) {
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            Path targetRegionDir = getTargetRegionDir(worldRoot);
            if (!Files.isDirectory(targetRegionDir)) {
                return new BlockPos(0, 66, 0);
            }

            Integer minRx = null;
            Integer maxRx = null;
            Integer minRz = null;
            Integer maxRz = null;

            try (Stream<Path> stream = Files.list(targetRegionDir)) {
                List<Path> files = stream.toList();
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    Matcher matcher = REGION_FILE_PATTERN.matcher(name);
                    if (!matcher.matches()) {
                        continue;
                    }
                    int rx = Integer.parseInt(matcher.group(1));
                    int rz = Integer.parseInt(matcher.group(2));

                    minRx = (minRx == null) ? rx : Math.min(minRx, rx);
                    maxRx = (maxRx == null) ? rx : Math.max(maxRx, rx);
                    minRz = (minRz == null) ? rz : Math.min(minRz, rz);
                    maxRz = (maxRz == null) ? rz : Math.max(maxRz, rz);
                }
            }

            if (minRx == null || maxRx == null || minRz == null || maxRz == null) {
                return new BlockPos(0, 66, 0);
            }

            int minBlockX = minRx * 512;
            int maxBlockX = (maxRx + 1) * 512 - 1;
            int minBlockZ = minRz * 512;
            int maxBlockZ = (maxRz + 1) * 512 - 1;
            int centerX = (minBlockX + maxBlockX) / 2;
            int centerZ = (minBlockZ + maxBlockZ) / 2;
            return new BlockPos(centerX, 66, centerZ);
        } catch (Exception e) {
            StardewCraft.LOGGER.warn("[VALLEY_PREGEN] Failed getting installed region center: {}", e.getMessage());
            return new BlockPos(0, 66, 0);
        }
    }

    public static List<BlockPos> getInstalledRegionSampleCenters(MinecraftServer server) {
        List<BlockPos> result = new ArrayList<>();
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            Path targetRegionDir = getTargetRegionDir(worldRoot);
            if (!Files.isDirectory(targetRegionDir)) {
                return result;
            }

            try (Stream<Path> stream = Files.list(targetRegionDir)) {
                for (Path file : stream.toList()) {
                    String name = file.getFileName().toString();
                    Matcher matcher = REGION_FILE_PATTERN.matcher(name);
                    if (!matcher.matches()) {
                        continue;
                    }
                    int rx = Integer.parseInt(matcher.group(1));
                    int rz = Integer.parseInt(matcher.group(2));
                    int centerX = rx * 512 + 256;
                    int centerZ = rz * 512 + 256;
                    result.add(new BlockPos(centerX, 66, centerZ));
                }
            }
        } catch (Exception e) {
            StardewCraft.LOGGER.warn("[VALLEY_PREGEN] Failed collecting region sample centers: {}", e.getMessage());
        }
        return result;
    }

    private static Path getTargetDimensionDir(Path worldRoot) {
        return worldRoot
            .resolve("dimensions")
            .resolve("stardewcraft")
            .resolve("stardew_valley");
    }

    private static Path getTargetRegionDir(Path worldRoot) {
        return getTargetDimensionDir(worldRoot).resolve("region");
    }

    private static boolean hasAnyRegionFiles(Path targetRegionDir) throws IOException {
        if (!Files.isDirectory(targetRegionDir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(targetRegionDir)) {
            return stream.anyMatch(path -> {
                String name = path.getFileName().toString().toLowerCase();
                return name.endsWith(".mca") && Files.isRegularFile(path);
            });
        }
    }

    private static int cleanManagedMcaFiles(Path dir, Set<String> manifestLowerCase, boolean keepManifestFiles) throws IOException {
        int deleted = 0;
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path file : stream.toList()) {
                String name = file.getFileName().toString();
                if (!name.toLowerCase().endsWith(".mca") || !Files.isRegularFile(file)) {
                    continue;
                }
                String lowerName = name.toLowerCase();
                if (isProtectedPlayerRegionFile(name)) {
                    continue;
                }
                if (keepManifestFiles && manifestLowerCase.contains(lowerName)) {
                    continue;
                }
                Files.delete(file);
                deleted++;
            }
        }
        return deleted;
    }

    private static boolean isProtectedPlayerRegionFile(String name) {
        Matcher matcher = REGION_FILE_PATTERN.matcher(name);
        if (!matcher.matches()) {
            return false;
        }
        int rx = Integer.parseInt(matcher.group(1));
        int rz = Integer.parseInt(matcher.group(2));
        int maxBlockX = (rx + 1) * REGION_BLOCK_SIZE - 1;
        int maxBlockZ = (rz + 1) * REGION_BLOCK_SIZE - 1;
        boolean farmRegion = maxBlockX >= com.stardew.craft.farm.FarmInstanceAllocator.FARM_REGION_START
            && maxBlockZ >= com.stardew.craft.farm.FarmInstanceAllocator.FARM_REGION_START;
        boolean dynamicInteriorRegion = rx >= PROTECTED_PLAYER_REGION_MIN && rz >= PROTECTED_PLAYER_REGION_MIN;
        return farmRegion || dynamicInteriorRegion;
    }

    private static List<ManifestEntry> readManifest() throws IOException {
        try (InputStream in = StardewValleyPrebuiltRegionInstaller.class.getClassLoader().getResourceAsStream(MANIFEST_RESOURCE)) {
            if (in == null) {
                return List.of();
            }
            return readManifest(in);
        }
    }

    static List<ManifestEntry> readManifest(InputStream in) throws IOException {
        List<ManifestEntry> entries = new ArrayList<>();
        Set<String> fileNames = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (firstLine) {
                    firstLine = false;
                    if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                        line = line.substring(1);
                    }
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] parts = trimmed.split("\\s+");
                ManifestEntry entry;
                if (parts.length == 2 && parts[0].equalsIgnoreCase("delete")) {
                    entry = new ManifestEntry(ManifestAction.DELETE, parts[1], 0L, "");
                } else if (parts.length == 4 && parts[0].equalsIgnoreCase("copy")) {
                    long size;
                    try {
                        size = Long.parseLong(parts[2]);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid region size at manifest line " + lineNumber, e);
                    }
                    entry = new ManifestEntry(ManifestAction.COPY, parts[1], size, parts[3].toLowerCase());
                } else {
                    throw new IOException("Invalid pregen manifest entry at line " + lineNumber + ": " + trimmed);
                }

                validateManifestEntry(entry, lineNumber);
                String lowerName = entry.fileName().toLowerCase();
                if (!fileNames.add(lowerName)) {
                    throw new IOException("Duplicate pregen manifest entry at line " + lineNumber + ": " + entry.fileName());
                }
                entries.add(entry);
            }
        }
        return List.copyOf(entries);
    }

    private static void validateManifestEntry(ManifestEntry entry, int lineNumber) throws IOException {
        if (!REGION_FILE_PATTERN.matcher(entry.fileName()).matches()) {
            throw new IOException("Invalid region filename at manifest line " + lineNumber + ": " + entry.fileName());
        }
        if (isProtectedPlayerRegionFile(entry.fileName())) {
            throw new IOException("Manifest may not modify protected player region: " + entry.fileName());
        }
        if (entry.action() == ManifestAction.COPY
                && (entry.size() <= 0L || !SHA256_PATTERN.matcher(entry.sha256()).matches())) {
            throw new IOException("COPY entry requires positive size and SHA-256 at manifest line " + lineNumber);
        }
    }

    static String manifestFingerprint(List<ManifestEntry> entries) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (ManifestEntry entry : entries) {
            String canonical = entry.action().name() + " " + entry.fileName().toLowerCase()
                + " " + entry.size() + " " + entry.sha256() + "\n";
            digest.update(canonical.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Marker 写入后，MCA 会随着正常区块保存而改变大小和内容，因此这里只检查必需文件是否仍存在。
     * 地图内容是否需要整体重置只由 pregen 版本和 manifest 指纹决定。
     */
    static boolean installedCopyFilesPresent(Path targetRegionDir, List<ManifestEntry> entries) {
        for (ManifestEntry entry : entries) {
            if (entry.action() == ManifestAction.COPY
                    && !Files.isRegularFile(targetRegionDir.resolve(entry.fileName()))) {
                return false;
            }
        }
        return true;
    }

    static List<ManifestEntry> copyEntriesRequired(
            Path targetRegionDir, List<ManifestEntry> entries, boolean replaceAllManagedFiles) {
        List<ManifestEntry> required = new ArrayList<>();
        for (ManifestEntry entry : entries) {
            if (entry.action() == ManifestAction.COPY
                    && (replaceAllManagedFiles
                        || !Files.isRegularFile(targetRegionDir.resolve(entry.fileName())))) {
                required.add(entry);
            }
        }
        return List.copyOf(required);
    }

    private static void stageManifestEntries(Path stagingDir, List<ManifestEntry> entries) throws Exception {
        for (ManifestEntry entry : entries) {
            if (entry.action() != ManifestAction.COPY) {
                continue;
            }
            String resourcePath = REGION_RESOURCE_PREFIX + entry.fileName();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path staged = stagingDir.resolve(entry.fileName());
            try (InputStream resource = StardewValleyPrebuiltRegionInstaller.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (resource == null) {
                    throw new IOException("Missing resource: " + resourcePath);
                }
                try (DigestInputStream in = new DigestInputStream(resource, digest)) {
                    Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            long actualSize = Files.size(staged);
            String actualHash = HexFormat.of().formatHex(digest.digest());
            if (actualSize != entry.size() || !actualHash.equals(entry.sha256())) {
                throw new IOException("Region verification failed: " + entry.fileName());
            }
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void cleanupStagingDirectory(Path stagingDir) {
        try {
            deleteRecursively(stagingDir);
        } catch (IOException e) {
            StardewCraft.LOGGER.warn("[VALLEY_PREGEN] Failed cleaning staging directory {}: {}", stagingDir, e.getMessage());
        }
    }

    enum ManifestAction { COPY, DELETE }

    record ManifestEntry(ManifestAction action, String fileName, long size, String sha256) {}

    private record MarkerState(int version, String manifestHash) {}
}
