package com.stardew.craft.animal.rule;

/** Source eligibility plus the explicit 3D farm-boundary adaptation. */
public final class DuckSwimmingRules {
    private DuckSwimmingRules() {
    }

    public static boolean canSeekWater(
            boolean definitionCanSwim,
            boolean wasPetToday,
            boolean outdoors,
            boolean beforeEveningReturn,
            boolean sameFarm
    ) {
        return definitionCanSwim
                && wasPetToday
                && outdoors
                && beforeEveningReturn
                && sameFarm;
    }
}
