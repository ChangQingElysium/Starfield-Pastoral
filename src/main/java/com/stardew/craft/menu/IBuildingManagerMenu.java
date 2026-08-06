package com.stardew.craft.menu;

/**
 * Common getter interface for CoopManagerMenu and BarnManagerMenu,
 * allowing a single unified Screen class to display both.
 */
public interface IBuildingManagerMenu {

    int ACTION_BUILD_OR_UPGRADE = 0;
    int ACTION_DEMOLISH = 1;
    int ACTION_RELOCATE = 2;

    int getCurrentTier();
    int getTargetTier();
    boolean canBuildOrUpgrade();

    int getReqFeedTrough();
    int getCurFeedTrough();
    int getReqAutoFeedTrough();
    int getCurAutoFeedTrough();
    int getReqHayHopper();
    int getCurHayHopper();
    int getReqIncubator();
    int getCurIncubator();

    int getReqInteriorBlocks();
    int getCurInteriorBlocks();
    int getCurWidth();
    int getCurLength();
    int getCurHeight();

    boolean isEnclosedRequired();
    boolean isEnclosed();
    boolean isDoorRequired();
    int getReqDoorCount();
    int getCurDoorCount();
    boolean hasInteriorSpace();

    boolean isAtMaxTier();
    boolean hasExistingBuilding();
    int getBoundAnimalCount();

    default boolean isRevalidating() {
        return hasExistingBuilding()
                && getTargetTier() > 0
                && getTargetTier() == getCurrentTier();
    }

    /** Returns "coop" or "barn". */
    String buildingFamily();
}
