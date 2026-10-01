package com.stardew.craft.client.weapon.presentation;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Audio and confirmed-hit accents for Crescent Slash. The blade path is rendered
 * by {@code WeaponTrailClient} from the item model's captured blade anchors.
 */
final class CrescentSlashPresentation implements SkillPresentation {
    private static final int TRAIL_SETTLE_TICKS = 5;

    private final SkillPresentationContext context;
    private boolean releasePlayed;

    CrescentSlashPresentation(SkillPresentationContext context) {
        this.context = context;
    }

    @Override
    public int casterEntityId() {
        return context.payload().casterEntityId();
    }

    @Override
    public String skillId() {
        return context.payload().skillId();
    }

    @Override
    public void tick() {
        if (context.caster()==null||!context.caster().isAlive()||net.minecraft.client.Minecraft.getInstance().isPaused())return;
        var action=com.stardew.craft.client.weapon.WeaponSkillAnimationClient.getWorldAction(casterEntityId());
        if(action==null||!skillId().equals(action.skillId()))return;
        if (!releasePlayed
                && context.actionAge(0.0f) >= context.payload().activeTickOffset()) {
            releasePlayed = true;
            playReleaseSound();
        }
    }

    @Override
    public void render(RenderLevelStageEvent event) {
    }

    @Override
    public boolean isComplete() {
        return context.actionAge(0.0f)
                >= context.payload().actionDurationTicks() + TRAIL_SETTLE_TICKS;
    }

    private void playReleaseSound() {
        Player caster = context.caster();
        if (caster == null) {
            return;
        }
        caster.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.72f, 1.10f);
    }
}
