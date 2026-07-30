package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.entity.projectile.TideAnchorProjectileEntity;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Server-authoritative lifecycle for Neptune's Glaive's original Tide Anchor.
 */
public final class TideAnchorSkillHandler implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final float PROJECTILE_SPEED = 1.25F;
    public static final float PROJECTILE_INACCURACY = 0.8F;
    public static final int PROJECTILE_RUNTIME_TICKS =
            TideAnchorProjectileEntity.MAX_LIFETIME_TICKS + 1;
    public static final int ANIMATION_TICKS = 12;

    private final Map<UUID, State> states = new HashMap<>();

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.COOLDOWN
            );
        }
        return canPayEnergy(context)
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Tide Anchor energy is no longer available"
            );
        }

        ServerLevel level = context.player().serverLevel();
        TideAnchorProjectileEntity projectile =
                new TideAnchorProjectileEntity(
                        level,
                        context.player(),
                        context.skillData().getId(),
                        context.skillData().getDamagePercent() / 100.0F,
                        context.weaponSnapshot()
                );
        projectile.shootFromRotation(
                context.player(),
                context.player().getXRot(),
                context.player().getYRot(),
                0.0F,
                PROJECTILE_SPEED,
                PROJECTILE_INACCURACY
        );
        if (!level.addFreshEntity(projectile)) {
            throw new IllegalStateException(
                    "Failed to add a Tide Anchor projectile"
            );
        }

        try {
            if (!context.player().getAbilities().instabuild
                    && !PlayerStardewDataAPI.consumeEnergy(
                            context.player(),
                            ENERGY_COST
                    )) {
                throw new IllegalStateException(
                        "Validated Tide Anchor energy payment failed"
                );
            }
        } catch (RuntimeException exception) {
            projectile.discard();
            throw exception;
        }

        states.put(
                instance.instanceId(),
                new State(
                        level.dimension(),
                        projectile,
                        context.nowTick() + PROJECTILE_RUNTIME_TICKS
                )
        );

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );

        // Preserve the authored presentation-only notification.
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                ANIMATION_TICKS
        );
    }

    @Override
    public boolean completesImmediately() {
        return false;
    }

    @Override
    public SkillTickResult tick(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        State state = states.get(instance.instanceId());
        if (state == null) {
            return SkillTickResult.CANCEL;
        }
        if (!isSameDimension(
                state.dimension,
                context.player().level().dimension()
        ) || !isSameDimension(
                state.dimension,
                state.projectile.level().dimension()
        )) {
            return SkillTickResult.CANCEL;
        }
        if (context.nowTick() >= state.endTick) {
            return SkillTickResult.COMPLETE;
        }
        return state.projectile.isRemoved()
                ? SkillTickResult.COMPLETE
                : SkillTickResult.CONTINUE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        State state = states.remove(instance.instanceId());
        if (state != null && !state.projectile.isRemoved()) {
            state.projectile.discard();
        }
    }

    static boolean canPayEnergy(
            float currentEnergy,
            boolean creativeMode,
            boolean freeEnergyBlessing
    ) {
        return creativeMode
                || freeEnergyBlessing
                || currentEnergy >= ENERGY_COST;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    private static boolean canPayEnergy(SkillExecutionContext context) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().getAbilities().instabuild,
                context.player().hasEffect(
                        ModMobEffects.STATUE_OF_BLESSINGS_2
                )
        );
    }

    private static final class State {
        private final ResourceKey<Level> dimension;
        private final TideAnchorProjectileEntity projectile;
        private final long endTick;

        private State(
                ResourceKey<Level> dimension,
                TideAnchorProjectileEntity projectile,
                long endTick
        ) {
            this.dimension = dimension;
            this.projectile = projectile;
            this.endTick = endTick;
        }
    }
}
