package com.stardew.craft.client.weapon.presentation;

import com.stardew.craft.combat.network.WeaponSkillImpactPayload;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * One client-side presentation instance. Gameplay never depends on this state.
 */
interface SkillPresentation {
    int casterEntityId();

    String skillId();

    void tick();

    void render(RenderLevelStageEvent event);

    boolean isComplete();

    default void onImpact(WeaponSkillImpactPayload payload) {
    }

    default void setPersistentState(
            boolean active,
            int durationTicks,
            boolean completedCycle
    ) {
    }
}
