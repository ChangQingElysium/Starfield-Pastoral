package com.stardew.craft.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Asynchronous, process-wide Modrinth version check used by login announcements. */
public final class ModUpdateChecker {
    static final String MODRINTH_URL = "https://modrinth.com/mod/starfield-pastoral";
    private static final String MODRINTH_API =
            "https://api.modrinth.com/v2/project/starfield-pastoral/version";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final long SUCCESS_CACHE_NANOS = TimeUnit.HOURS.toNanos(6);
    private static final long FAILURE_CACHE_NANOS = TimeUnit.MINUTES.toNanos(15);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile CompletableFuture<VersionStatus> cachedCheck;
    private static volatile long cacheExpiresAtNanos;

    private ModUpdateChecker() {
    }

    public static CompletableFuture<VersionStatus> checkAsync() {
        long now = System.nanoTime();
        CompletableFuture<VersionStatus> current = cachedCheck;
        if (current != null && (!current.isDone() || now < cacheExpiresAtNanos)) {
            return current;
        }
        synchronized (ModUpdateChecker.class) {
            now = System.nanoTime();
            current = cachedCheck;
            if (current != null && (!current.isDone() || now < cacheExpiresAtNanos)) {
                return current;
            }
            String installedVersion = installedVersion();
            HttpRequest request = HttpRequest.newBuilder(versionApiUri())
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "StarfieldPastoral/" + installedVersion
                            + " (" + MODRINTH_URL + ")")
                    .GET()
                    .build();
            cachedCheck = HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> parseResponse(installedVersion, response.statusCode(), response.body()))
                    .exceptionally(exception -> {
                        StardewCraft.LOGGER.warn(
                                "Could not check Starfield Pastoral updates on Modrinth: {}",
                                exception.getMessage());
                        return VersionStatus.unavailable(installedVersion);
                    });
            cachedCheck.thenAccept(status -> cacheExpiresAtNanos = System.nanoTime()
                    + (status.state() == State.UNAVAILABLE
                    ? FAILURE_CACHE_NANOS : SUCCESS_CACHE_NANOS));
            return cachedCheck;
        }
    }

    static VersionStatus parseResponse(String installedVersion, int statusCode, String responseBody) {
        if (statusCode != 200 || responseBody == null || responseBody.isBlank()) {
            return VersionStatus.unavailable(installedVersion);
        }
        try {
            JsonArray versions = JsonParser.parseString(responseBody).getAsJsonArray();
            JsonObject latest = versions.asList().stream()
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .filter(ModUpdateChecker::isPublishedVersion)
                    .max(Comparator.comparing(ModUpdateChecker::publishedAt))
                    .orElse(null);
            if (latest == null) {
                return VersionStatus.unavailable(installedVersion);
            }
            String latestVersion = latest.get("version_number").getAsString();
            return VersionStatus.compare(installedVersion, latestVersion);
        } catch (RuntimeException exception) {
            return VersionStatus.unavailable(installedVersion);
        }
    }

    private static boolean isPublishedVersion(JsonObject version) {
        if (!version.has("version_number") || !version.has("date_published")) {
            return false;
        }
        return !version.has("status") || "listed".equals(version.get("status").getAsString());
    }

    private static Instant publishedAt(JsonObject version) {
        try {
            return Instant.parse(version.get("date_published").getAsString());
        } catch (DateTimeParseException exception) {
            return Instant.EPOCH;
        }
    }

    private static URI versionApiUri() {
        String gameVersion = SharedConstants.getCurrentVersion().getName();
        String loaders = URLEncoder.encode("[\"neoforge\"]", StandardCharsets.UTF_8);
        String gameVersions = URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8);
        return URI.create(MODRINTH_API
                + "?loaders=" + loaders
                + "&game_versions=" + gameVersions
                + "&include_changelog=false");
    }

    private static String installedVersion() {
        return ModList.get().getModContainerById(StardewCraft.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    static VersionStatus unavailableStatus() {
        return VersionStatus.unavailable(installedVersion());
    }

    public enum State {
        UP_TO_DATE,
        OUTDATED,
        AHEAD,
        UNAVAILABLE
    }

    public record VersionStatus(String installedVersion, String latestVersion, State state) {
        private static VersionStatus compare(String installedVersion, String latestVersion) {
            try {
                int comparison = new ComparableVersion(normalize(installedVersion))
                        .compareTo(new ComparableVersion(normalize(latestVersion)));
                State state = comparison < 0
                        ? State.OUTDATED
                        : comparison > 0 ? State.AHEAD : State.UP_TO_DATE;
                return new VersionStatus(installedVersion, latestVersion, state);
            } catch (RuntimeException exception) {
                return unavailable(installedVersion);
            }
        }

        private static VersionStatus unavailable(String installedVersion) {
            return new VersionStatus(installedVersion, "", State.UNAVAILABLE);
        }

        public boolean isOutdated() {
            return state == State.OUTDATED;
        }

        private static String normalize(String version) {
            String normalized = version == null ? "" : version.trim();
            return normalized.startsWith("v") || normalized.startsWith("V")
                    ? normalized.substring(1) : normalized;
        }
    }
}
