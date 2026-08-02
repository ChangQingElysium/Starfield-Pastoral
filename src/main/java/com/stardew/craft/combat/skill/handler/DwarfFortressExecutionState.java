package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.FireRingEffectPayload;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.effect.ModMobEffects;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One Ley Fortress activation, including its reactive shocks and echo. */
final class DwarfFortressExecutionState
        implements SkillInstance.ExecutionState {
    private static final ResourceLocation KNOCKBACK_RESISTANCE_ID =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "skill.dwarf_fortress_knockback_resistance"
            );

    private final ResourceKey<Level> dimension;
    private final long endTick;
    private final WeaponDamageSnapshot weaponSnapshot;
    private int shocks;
    private long lastShockTick;
    private boolean settled;
    private boolean cancelled;
    private boolean advancing;

    DwarfFortressExecutionState(
            ResourceKey<Level> dimension,
            long nowTick,
            int durationTicks,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Dwarf Fortress duration must be positive"
            );
        }
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.endTick = nowTick + durationTicks;
        this.weaponSnapshot = Objects.requireNonNull(
                weaponSnapshot,
                "weaponSnapshot"
        );
    }

    @SuppressWarnings("null")
    void start(
            ServerPlayer player,
            int durationTicks,
            long nowTick,
            float initialDamageMultiplier
    ) {
        applyKnockbackGuard(player);

        MobEffect shelter = Objects.requireNonNull(
                ModMobEffects.SHELTER.get(),
                "shelter"
        );
        player.addEffect(new MobEffectInstance(
                Holder.direct(shelter),
                durationTicks,
                DwarfFortressSkillHandler.SHELTER_AMPLIFIER,
                false,
                true,
                true
        ));

        triggerShockwave(
                player,
                nowTick,
                DwarfFortressSkillHandler.INITIAL_SHOCK_RADIUS,
                initialDamageMultiplier,
                false
        );
    }

    void onDamageTaken(ServerPlayer player, long nowTick) {
        if (settled) {
            return;
        }
        if (!isSameDimension(dimension, player.level().dimension())
                || !isWithinActiveWindow(nowTick, endTick)) {
            cancel(player);
            return;
        }
        if (!canTriggerReactiveShock(shocks, lastShockTick, nowTick)) {
            return;
        }

        lastShockTick = nowTick;
        shocks++;
        triggerShockwave(
                player,
                nowTick,
                DwarfFortressSkillHandler.REACTIVE_SHOCK_RADIUS,
                DwarfFortressSkillHandler.REACTIVE_DAMAGE_MULTIPLIER,
                false
        );
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return cancelled
                    ? SkillTickResult.CANCEL
                    : SkillTickResult.COMPLETE;
        }
        if (advancing) {
            return SkillTickResult.CONTINUE;
        }
        ServerPlayer player = context.player();
        if (!isSameDimension(dimension, player.level().dimension())) {
            cancel(player);
            return SkillTickResult.CANCEL;
        }
        if (isWithinActiveWindow(context.nowTick(), endTick)) {
            return SkillTickResult.CONTINUE;
        }

        advancing = true;
        try {
            if (shouldTriggerEcho(shocks)) {
                triggerShockwave(
                        player,
                        context.nowTick(),
                        DwarfFortressSkillHandler.ECHO_RADIUS,
                        DwarfFortressSkillHandler.ECHO_DAMAGE_MULTIPLIER,
                        true
                );
            }
            return SkillTickResult.COMPLETE;
        } finally {
            settled = true;
            removeKnockbackGuard(player);
            advancing = false;
        }
    }

    void cancel(ServerPlayer player) {
        if (!settled) {
            settled = true;
            cancelled = true;
        }
        removeKnockbackGuard(player);
    }

    static SkillContext createShockContext(float damageMultiplier) {
        return SkillContext.builder()
                .skillId("dwarf_fortress")
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(damageMultiplier)
                .build();
    }

    static boolean isWithinActiveWindow(long nowTick, long endTick) {
        return nowTick <= endTick;
    }

    static boolean canTriggerReactiveShock(
            int completedShocks,
            long lastShockTick,
            long nowTick
    ) {
        return completedShocks
                < DwarfFortressSkillHandler.MAX_REACTIVE_SHOCKS
                && lastShockTick != nowTick;
    }

    static boolean shouldTriggerEcho(int completedShocks) {
        return completedShocks
                >= DwarfFortressSkillHandler.MAX_REACTIVE_SHOCKS;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    static double knockbackResistanceBonus() {
        return DwarfFortressSkillHandler.KNOCKBACK_RESISTANCE_BONUS;
    }

    private static void applyKnockbackGuard(ServerPlayer player) {
        AttributeInstance knockbackResistance =
                player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackResistance == null) {
            return;
        }
        knockbackResistance.addOrUpdateTransientModifier(
                new AttributeModifier(
                        KNOCKBACK_RESISTANCE_ID,
                        DwarfFortressSkillHandler
                                .KNOCKBACK_RESISTANCE_BONUS,
                        AttributeModifier.Operation.ADD_VALUE
                )
        );
    }

    private static void removeKnockbackGuard(ServerPlayer player) {
        AttributeInstance knockbackResistance =
                player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackResistance != null
                && knockbackResistance.hasModifier(
                        KNOCKBACK_RESISTANCE_ID
                )) {
            knockbackResistance.removeModifier(
                    KNOCKBACK_RESISTANCE_ID
            );
        }
    }

    private void triggerShockwave(
            ServerPlayer player,
            long nowTick,
            float radius,
            float damageMultiplier,
            boolean echo
    ) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 center = player.position();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new FireRingEffectPayload(
                        (float) center.x,
                        (float) center.y,
                        (float) center.z,
                        radius,
                        DwarfFortressSkillHandler.RING_DURATION_TICKS
                )
        );

        spawnShockwaveEffects(serverLevel, center, radius, echo);

        List<LivingEntity> targets = getTargetsInRadius(
                serverLevel,
                center,
                radius,
                player
        );
        for (LivingEntity target : targets) {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    createShockContext(damageMultiplier),
                    weaponSnapshot,
                    nowTick
                            + DwarfFortressSkillHandler
                            .HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
            );
        }
    }

    @SuppressWarnings("null")
    private static void spawnShockwaveEffects(
            ServerLevel level,
            Vec3 center,
            float radius,
            boolean echo
    ) {
        int smokeCount = echo ? 28 : 18;
        int critCount = echo ? 26 : 16;
        int dustCount = echo ? 36 : 24;

        level.sendParticles(
                ParticleTypes.EXPLOSION,
                center.x,
                center.y + 0.1,
                center.z,
                echo ? 2 : 1,
                0.2,
                0.05,
                0.2,
                0.0
        );
        level.sendParticles(
                ParticleTypes.SMOKE,
                center.x,
                center.y + 0.1,
                center.z,
                smokeCount,
                radius * 0.35,
                0.12,
                radius * 0.35,
                0.02
        );
        level.sendParticles(
                ParticleTypes.CRIT,
                center.x,
                center.y + 0.25,
                center.z,
                critCount,
                radius * 0.4,
                0.2,
                radius * 0.4,
                0.12
        );
        level.sendParticles(
                new BlockParticleOption(
                        ParticleTypes.BLOCK,
                        Blocks.STONE.defaultBlockState()
                ),
                center.x,
                center.y + 0.05,
                center.z,
                dustCount,
                radius * 0.45,
                0.18,
                radius * 0.45,
                0.12
        );

        BlockPos pos = BlockPos.containing(center);
        level.playSound(
                null,
                pos,
                SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS,
                0.8F,
                echo ? 0.7F : 0.9F
        );
        level.playSound(
                null,
                pos,
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS,
                echo ? 0.9F : 0.6F,
                echo ? 0.8F : 1.1F
        );
    }

    private static List<LivingEntity> getTargetsInRadius(
            ServerLevel level,
            Vec3 center,
            float radius,
            ServerPlayer owner
    ) {
        AABB box = new AABB(
                center.x - radius,
                center.y - radius * 0.6,
                center.z - radius,
                center.x + radius,
                center.y + radius * 0.6,
                center.z + radius
        );
        return level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isPickable()
                        && entity.isAlive()
                        && entity != owner
        );
    }
}
