package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.ObsidianCrackTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative lifecycle for Obsidian Edge's original crack line.
 */
public final class ObsidianCrackSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final float LINE_LENGTH = 6.0F;
    public static final double FORWARD_OFFSET = 3.0D;
    public static final int ANIMATION_TICKS = 12;

    record CrackLine(Vec3 start, Vec3 end, float yaw, float length) {}

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
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
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || ObsidianCrackTracker.hasState(context.player().getUUID())) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
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
        CrackLine line = createCrackLine(
                context.player().position(),
                horizontalLook(context.player())
        );
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Obsidian Crack energy is no longer available"
            );
        }
        if (!context.player().getAbilities().instabuild
                && !PlayerStardewDataAPI.consumeEnergy(
                        context.player(),
                        ENERGY_COST
                )) {
            throw new IllegalStateException(
                    "Validated Obsidian Crack energy payment failed"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );
        ObsidianCrackTracker.start(
                context.player(),
                context.nowTick(),
                line.start(),
                line.end(),
                line.yaw(),
                line.length(),
                weaponId,
                skillId
        );
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                ANIMATION_TICKS
        );
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
        if (!ObsidianCrackTracker.isBoundToCurrentDimension(
                context.player()
        )) {
            return SkillTickResult.CANCEL;
        }
        ObsidianCrackTracker.tick(context.player(), context.nowTick());
        return ObsidianCrackTracker.hasState(context.player().getUUID())
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        ObsidianCrackTracker.removePlayer(context.player().getUUID());
    }

    static CrackLine createCrackLine(
            Vec3 playerPosition,
            Vec3 horizontalLook
    ) {
        Vec3 look = horizontalLook.normalize();
        Vec3 right = new Vec3(look.z, 0.0D, -look.x).normalize();
        Vec3 center = playerPosition.add(look.scale(FORWARD_OFFSET));
        Vec3 start = center.add(right.scale(-LINE_LENGTH * 0.5D));
        Vec3 end = center.add(right.scale(LINE_LENGTH * 0.5D));
        float yaw = (float) (
                Math.atan2(-right.x, right.z) * (180.0D / Math.PI)
        );
        return new CrackLine(start, end, yaw, LINE_LENGTH);
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

    private static boolean canPayEnergy(SkillExecutionContext context) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().getAbilities().instabuild,
                context.player().hasEffect(
                        ModMobEffects.STATUE_OF_BLESSINGS_2
                )
        );
    }

    private static Vec3 horizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            float yawRadians = (float) Math.toRadians(player.getYRot());
            horizontal = new Vec3(
                    -Math.sin(yawRadians),
                    0.0D,
                    Math.cos(yawRadians)
            );
        }
        return horizontal.normalize();
    }
}
