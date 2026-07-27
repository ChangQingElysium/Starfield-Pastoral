package com.stardew.craft.world;

import com.stardew.craft.api.v1.world.StardewLocation;
import com.stardew.craft.api.v1.world.StardewLocationDefinition;
import com.stardew.craft.api.v1.world.StardewLocationEnvironmentKeys;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;

/** Parses inherited location properties into a client-safe music decision. */
public final class LocationMusicEnvironment {
    private LocationMusicEnvironment() {
    }

    public static Resolution resolve(
            StardewLocation location,
            int currentMinutes
    ) {
        if (location == null) {
            return Resolution.inherit();
        }
        Optional<String> rawProfile = location.property(
                StardewLocationEnvironmentKeys.MUSIC_PROFILE);
        if (rawProfile.isEmpty()) {
            return Resolution.inherit();
        }
        boolean hasTimeWindow = location.properties().containsKey(
                StardewLocationEnvironmentKeys.MUSIC_START_TIME)
                || location.properties().containsKey(
                        StardewLocationEnvironmentKeys.MUSIC_END_TIME);
        if (hasTimeWindow && currentMinutes < 0) {
            return Resolution.inherit();
        }
        int start = parseHhmm(location.properties().getOrDefault(
                StardewLocationEnvironmentKeys.MUSIC_START_TIME,
                "0000"));
        int end = parseHhmm(location.properties().getOrDefault(
                StardewLocationEnvironmentKeys.MUSIC_END_TIME,
                "2600"));
        if (start < 0 || end < 0
                || !includes(start, end, currentMinutes)) {
            return Resolution.inherit();
        }
        String profile = rawProfile.get().trim();
        if (StardewLocationEnvironmentKeys.MUSIC_SILENT
                .equals(profile)) {
            return Resolution.silence();
        }
        ResourceLocation sound = ResourceLocation.tryParse(profile);
        if (sound == null
                || !BuiltInRegistries.SOUND_EVENT.containsKey(sound)) {
            return Resolution.inherit();
        }
        return Resolution.track(sound);
    }

    public static Optional<String> validate(
            ResourceLocation locationId,
            StardewLocationDefinition definition
    ) {
        return validate(locationId, definition, false);
    }

    public static Optional<String> validate(
            ResourceLocation locationId,
            StardewLocationDefinition definition,
            boolean parentProvidesMusicProfile
    ) {
        String profile = definition.properties().get(
                StardewLocationEnvironmentKeys.MUSIC_PROFILE);
        if (profile == null) {
            boolean hasTimeProperty =
                    definition.properties().containsKey(
                            StardewLocationEnvironmentKeys.MUSIC_START_TIME)
                    || definition.properties().containsKey(
                            StardewLocationEnvironmentKeys.MUSIC_END_TIME);
            if (hasTimeProperty && !parentProvidesMusicProfile) {
                return Optional.of(locationId
                        + ": music time properties require "
                        + StardewLocationEnvironmentKeys.MUSIC_PROFILE);
            }
        } else {
            String normalized = profile.trim();
            ResourceLocation sound = ResourceLocation.tryParse(
                    normalized);
            if (!StardewLocationEnvironmentKeys.MUSIC_SILENT
                            .equals(normalized)
                    && (sound == null
                            || !BuiltInRegistries.SOUND_EVENT
                                    .containsKey(sound))) {
                return Optional.of(locationId
                        + ": unknown music profile/sound " + profile);
            }
        }
        for (ResourceLocation key : java.util.List.of(
                StardewLocationEnvironmentKeys.MUSIC_START_TIME,
                StardewLocationEnvironmentKeys.MUSIC_END_TIME)) {
            String value = definition.properties().get(key);
            if (value != null && parseHhmm(value) < 0) {
                return Optional.of(locationId + ": " + key
                        + " must be HHMM from 0000 through 2600");
            }
        }
        return Optional.empty();
    }

    static int parseHhmm(String raw) {
        if (raw == null || !raw.matches("\\d{1,4}")) {
            return -1;
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return -1;
        }
        int hour = value / 100;
        int minute = value % 100;
        return hour <= 26 && minute < 60
                ? hour * 60 + minute : -1;
    }

    static boolean includes(
            int startMinutes,
            int endMinutes,
            int currentMinutes
    ) {
        if (currentMinutes < 0) {
            return false;
        }
        if (startMinutes == endMinutes) {
            return true;
        }
        return startMinutes < endMinutes
                ? currentMinutes >= startMinutes
                        && currentMinutes < endMinutes
                : currentMinutes >= startMinutes
                        || currentMinutes < endMinutes;
    }

    public record Resolution(
            Decision decision,
            @Nullable ResourceLocation track
    ) {
        private static Resolution inherit() {
            return new Resolution(Decision.INHERIT, null);
        }

        private static Resolution silence() {
            return new Resolution(Decision.SILENCE, null);
        }

        private static Resolution track(ResourceLocation track) {
            return new Resolution(Decision.TRACK, track);
        }
    }

    public enum Decision {
        INHERIT,
        SILENCE,
        TRACK
    }
}
