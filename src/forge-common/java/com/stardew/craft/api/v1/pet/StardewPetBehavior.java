package com.stardew.craft.api.v1.pet;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Experimental immutable 3D pet state graph. Times are seconds; directions belong to navigation. */
public record StardewPetBehavior(Map<String, Double> clips, Map<String, State> states,
                                 Map<String, String> postureExits) {
    public record Choice(String state, boolean outside, double weight) {
        public Choice { if (state == null || state.isBlank() || !Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Pet behavior choice"); }
    }
    public record State(String clip, String entry, String exit, double duration, double minDuration, double maxDuration,
                        int minLoops, int maxLoops, double chance, String sound, boolean move,
                        List<Choice> end, List<Choice> timeout, List<Choice> nearby, List<Choice> random) {
        public State {
            end = List.copyOf(end); timeout = List.copyOf(timeout); nearby = List.copyOf(nearby); random = List.copyOf(random);
            if (clip == null || clip.isBlank() || entry == null || exit == null || sound == null
                    || move && !Set.of("walk", "sprint", "pounce").contains(clip)
                    || !Double.isFinite(duration) || !Double.isFinite(minDuration) || !Double.isFinite(maxDuration)
                    || minDuration >= 0 && maxDuration < minDuration || minLoops >= 0 && maxLoops < minLoops
                    || !Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Pet behavior timing");
        }
    }
    public StardewPetBehavior {
        clips = Map.copyOf(clips); states = Map.copyOf(states); postureExits = Map.copyOf(postureExits);
        if (!clips.keySet().containsAll(Set.of("idle", "walk", "blink", "sleep_enter", "sleep", "sleep_exit"))
                || !states.containsKey("Walk")) throw new IllegalArgumentException("Pet requires locomotion and sleep clips");
        for (var clip : clips.entrySet()) if (clip.getKey().isBlank() || !Double.isFinite(clip.getValue()) || clip.getValue() <= 0) throw new IllegalArgumentException("Pet clip duration");
        for (var state : states.values()) {
            for (var clip : List.of(state.clip(), state.entry(), state.exit())) if (!clip.isEmpty() && !clips.containsKey(clip)) throw new IllegalArgumentException("Missing pet clip " + clip);
            for (var choices : List.of(state.end(), state.timeout(), state.nearby(), state.random())) for (var choice : choices)
                if (!states.containsKey(choice.state()) && !choice.state().equals("Sleep")) throw new IllegalArgumentException("Missing pet state " + choice.state());
        }
        for (var exit : postureExits.entrySet()) if (!states.containsKey(exit.getKey()) || !clips.containsKey(exit.getValue())) throw new IllegalArgumentException("Missing pet posture exit");
    }
}
