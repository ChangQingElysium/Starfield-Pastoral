package com.stardew.craft.client.weapon.presentation;

import com.stardew.craft.Config;
import com.stardew.craft.combat.network.WeaponSkillImpactPayload;
import com.stardew.craft.weather.ModParticles;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * State-driven Forest Blessing presentation. The short strike uses the shared
 * weapon trail; the four-second blessing uses custom leaves and one opening pulse.
 */
final class ForestBlessingPresentation implements SkillPresentation {
    private static final int HEAL_PULSE_TICKS = 10;

    private final SkillPresentationContext context;
    private boolean releasePlayed;
    private boolean blessingActive;
    private boolean complete;
    private int blessingAge;
    private int blessingDuration;

    ForestBlessingPresentation(SkillPresentationContext context) {
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
        if (!blessingActive) {
            if (context.actionAge(0.0f) > context.payload().actionDurationTicks() + 2) {
                complete = true;
            }
            return;
        }

        blessingAge++;
        if (isHealingBeat(blessingAge, blessingDuration)) {
            emitHealingBeat(0x4845414CL + blessingAge);
        }
        if (blessingAge >= blessingDuration) {
            complete = true;
        }
    }

    @Override
    public void render(RenderLevelStageEvent event) {
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public void setPersistentState(
            boolean active,
            int durationTicks,
            boolean completedCycle
    ) {
        if (!active) {
            if (completedCycle
                    && blessingActive
                    && blessingAge < blessingDuration) {
                emitHealingBeat(0x46494E414CL);
            }
            blessingActive = false;
            complete = true;
            return;
        }
        blessingActive = true;
        complete = false;
        blessingAge = 0;
        blessingDuration = Math.max(1, durationTicks);
        playBlessingSound();
        emitBlessingWisp(0x4F50454E494E47L);
        emitLeaves(5, 0x4F50454E494E47L);
    }

    @Override
    public void onImpact(WeaponSkillImpactPayload payload) {
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
            return;
        }
        RandomSource random = RandomSource.create(payload.seed() ^ 0x464F52455354L);
        for (int targetId : payload.targetEntityIds()) {
            Entity entity = context.level().getEntity(targetId);
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }
            Vec3 point = target.position().add(0.0, target.getBbHeight() * 0.52, 0.0);
            int leafCount = 2 + random.nextInt(3);
            for (int i = 0; i < leafCount; i++) {
                context.level().addParticle(
                        ModParticles.FOREST_LEAF.get(),
                        point.x + (random.nextDouble() - 0.5) * target.getBbWidth() * 0.55,
                        point.y + (random.nextDouble() - 0.5) * target.getBbHeight() * 0.30,
                        point.z + (random.nextDouble() - 0.5) * target.getBbWidth() * 0.55,
                        (random.nextDouble() - 0.5) * 0.025,
                        0.018 + random.nextDouble() * 0.022,
                        (random.nextDouble() - 0.5) * 0.025
                );
            }
        }
    }

    private void emitBlessingWisp(long salt) {
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
            return;
        }
        Vec3 anchor = context.anchor();
        RandomSource random = context.random(salt);
        context.level().addParticle(
                ModParticles.FOREST_WISP.get(),
                anchor.x + (random.nextDouble() - 0.5) * 0.22,
                anchor.y + 0.22,
                anchor.z + (random.nextDouble() - 0.5) * 0.22,
                0.0,
                0.018,
                0.0
        );
    }

    private void emitHealingBeat(long salt) {
        emitBlessingWisp(salt);
        emitLeaves(2, salt ^ 0x53454544L);
    }

    private void emitLeaves(int count, long salt) {
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
            return;
        }
        Vec3 anchor = context.anchor();
        RandomSource random = context.random(salt);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 0.18 + random.nextDouble() * 0.42;
            context.level().addParticle(
                    ModParticles.FOREST_LEAF.get(),
                    anchor.x + Math.cos(angle) * radius,
                    anchor.y + 0.12 + random.nextDouble() * 0.78,
                    anchor.z + Math.sin(angle) * radius,
                    -Math.sin(angle) * 0.006,
                    0.010 + random.nextDouble() * 0.012,
                    Math.cos(angle) * 0.006
            );
        }
    }

    private void playBlessingSound() {
        Player caster = context.caster();
        if (caster == null) {
            return;
        }
        caster.playSound(SoundEvents.AZALEA_LEAVES_PLACE, 0.42f, 0.96f);
        caster.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.13f, 1.28f);
    }

    private void playReleaseSound() {
        Player caster = context.caster();
        if (caster != null) {
            caster.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.56f, 1.04f);
        }
    }

    static boolean isHealingBeat(int age, int duration) {
        return age > 0
                && age <= duration
                && age % HEAL_PULSE_TICKS == 0;
    }
}
