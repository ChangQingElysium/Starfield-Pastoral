package com.stardew.craft.network.overnight;

import java.util.ArrayList;
import java.util.List;

/** Pure ordering rules from {@code Game1.showEndOfNightStuff}. */
public final class OvernightSequencePlanner {
    private OvernightSequencePlanner() {
    }

    public static List<Stage> plan(int levelUpCount, boolean hasShippedItems) {
        List<Stage> stages = new ArrayList<>(Math.max(0, levelUpCount) + 1);
        for (int i = 0; i < Math.max(0, levelUpCount); i++) {
            stages.add(Stage.LEVEL_UP);
        }
        stages.add(hasShippedItems ? Stage.SHIPPING : Stage.SAVE);
        return List.copyOf(stages);
    }

    public enum Stage {
        LEVEL_UP,
        SHIPPING,
        SAVE
    }
}
