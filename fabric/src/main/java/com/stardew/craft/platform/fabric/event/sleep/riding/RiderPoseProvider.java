package com.stardew.craft.platform.fabric.event.sleep.riding;

/** Common entity-side contract; its renderer consumer remains client-only. */
public interface RiderPoseProvider {
    boolean shouldRiderSit();
}
