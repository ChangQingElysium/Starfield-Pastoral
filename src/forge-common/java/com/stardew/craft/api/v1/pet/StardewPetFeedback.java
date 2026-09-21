package com.stardew.craft.api.v1.pet;

import java.util.List;
import java.util.Map;

/** Experimental source-timed feedback. Frame times are seconds within an authored behavior, not sprite directions. */
public record StardewPetFeedback(int repeatContentTicks, Map<String, Track> states) {
    public static final StardewPetFeedback EMPTY = new StardewPetFeedback(-1, Map.of());
    public record Cue(String sound, boolean voice, int rangeFromBorder, int range) {
        public Cue { if (sound == null || sound.isBlank() || rangeFromBorder < -1 || range < -1) throw new IllegalArgumentException("Pet sound cue"); }
    }
    public record Frame(double time, Cue cue, boolean terrain) {
        public Frame { if (!Double.isFinite(time) || time < 0 || cue == null && !terrain) throw new IllegalArgumentException("Pet feedback frame"); }
    }
    public record Track(Cue start, double length, boolean loop, List<Frame> frames) {
        public Track {
            frames = List.copyOf(frames);
            if (!Double.isFinite(length) || length <= 0 || frames.stream().anyMatch(frame -> frame.time() >= length)) throw new IllegalArgumentException("Pet feedback timeline");
        }
    }
    public StardewPetFeedback {
        states = Map.copyOf(states);
        if (repeatContentTicks < -1) throw new IllegalArgumentException("Pet content repeat delay");
    }
}
