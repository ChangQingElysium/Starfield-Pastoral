package com.stardew.craft.client.weapon.presentation;

import com.stardew.craft.Config;
import com.stardew.craft.client.weapon.CameraShakeState;
import com.stardew.craft.combat.network.WeaponSkillImpactPayload;
import com.stardew.craft.weather.ModParticles;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

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

    @Override
    public void onImpact(WeaponSkillImpactPayload payload) {
        if (payload.targetEntityIds().isEmpty()) {
            return;
        }
        if (context.isLocalCaster()) {
            CameraShakeState.kick(0.07f, 2, 0.72f);
        }
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
            return;
        }

        Vec3 forward = context.forward();
        for (int targetId : payload.targetEntityIds()) {
            Entity entity = context.level().getEntity(targetId);
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }
            Vec3 point = target.position().add(0.0, target.getBbHeight() * 0.56, 0.0);
            context.level().addParticle(
                    ModParticles.CRESCENT_IMPACT.get(),
                    point.x,
                    point.y,
                    point.z,
                    forward.x,
                    0.0,
                    forward.z
            );
        }
        Entity firstTarget = context.level().getEntity(payload.targetEntityIds().getFirst());
        Vec3 soundPoint = firstTarget != null ? firstTarget.position() : context.anchor();
        context.level().playLocalSound(
                soundPoint.x,
                soundPoint.y,
                soundPoint.z,
                SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS,
                0.34f,
                1.08f,
                false
        );
    }

    private void playReleaseSound() {
        Player caster = context.caster();
        if (caster == null) {
            return;
        }
        caster.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.72f, 1.10f);
    }
}
