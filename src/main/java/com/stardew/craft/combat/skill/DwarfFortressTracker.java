package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.FireRingEffectPayload;
import com.stardew.craft.effect.ModMobEffects;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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

public final class DwarfFortressTracker {
    public enum Status {
        ACTIVE,
        COMPLETED,
        INVALIDATED
    }

    public static final int ACTIVE_DURATION_TICKS = 80;
    public static final int MAX_REACTIVE_SHOCKS = 4;
    public static final int SHELTER_AMPLIFIER = 1;
    public static final double KNOCKBACK_RESISTANCE_BONUS = 1.0D;
    public static final float INITIAL_SHOCK_RADIUS = 3.5F;
    public static final float REACTIVE_SHOCK_RADIUS = 3.0F;
    public static final float REACTIVE_DAMAGE_MULTIPLIER = 1.0F;
    public static final float ECHO_RADIUS = 4.0F;
    public static final float ECHO_DAMAGE_MULTIPLIER = 1.2F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int RING_DURATION_TICKS = 12;

    private static final ResourceLocation KNOCKBACK_RESISTANCE_ID =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "skill.dwarf_fortress_knockback_resistance"
            );
    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private DwarfFortressTracker() {}

    @SuppressWarnings("null")
    public static void start(
            ServerPlayer player,
            long nowTick,
            int durationTicks,
            float initialDamageMultiplier
    ) {
        if (player == null || durationTicks <= 0) {
            return;
        }

        applyKnockbackGuard(player);

        MobEffect shelter = Objects.requireNonNull(
                ModMobEffects.SHELTER.get(),
                "shelter"
        );
        player.addEffect(new MobEffectInstance(
                Holder.direct(shelter),
                durationTicks,
                SHELTER_AMPLIFIER,
                false,
                true,
                true
        ));

        ACTIVE.put(
                player.getUUID(),
                new State(
                        player.level().dimension(),
                        nowTick + durationTicks
                )
        );
        triggerShockwave(
                player,
                nowTick,
                INITIAL_SHOCK_RADIUS,
                initialDamageMultiplier,
                false
        );
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return false;
        }
        if (!isSameDimension(
                state.dimension,
                player.level().dimension()
        ) || !isWithinActiveWindow(nowTick, state.endTick)) {
            stop(player);
            return false;
        }
        return true;
    }

    public static boolean hasState(ServerPlayer player) {
        return player != null && ACTIVE.containsKey(player.getUUID());
    }

    public static void onDamageTaken(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (!isSameDimension(
                state.dimension,
                player.level().dimension()
        ) || !isWithinActiveWindow(nowTick, state.endTick)) {
            stop(player);
            return;
        }
        if (!canTriggerReactiveShock(
                state.shocks,
                state.lastShockTick,
                nowTick
        )) {
            return;
        }
        state.lastShockTick = nowTick;
        state.shocks++;
        triggerShockwave(
                player,
                nowTick,
                REACTIVE_SHOCK_RADIUS,
                REACTIVE_DAMAGE_MULTIPLIER,
                false
        );
    }

    public static Status tick(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return Status.INVALIDATED;
        }
        if (!isSameDimension(
                state.dimension,
                player.level().dimension()
        )) {
            stop(player);
            return Status.INVALIDATED;
        }
        if (isWithinActiveWindow(nowTick, state.endTick)) {
            return Status.ACTIVE;
        }
        if (shouldTriggerEcho(state.shocks)) {
            triggerShockwave(
                    player,
                    nowTick,
                    ECHO_RADIUS,
                    ECHO_DAMAGE_MULTIPLIER,
                    true
            );
        }
        stop(player);
        return Status.COMPLETED;
    }

    public static void stop(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ACTIVE.remove(player.getUUID());
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
        return completedShocks < MAX_REACTIVE_SHOCKS
                && lastShockTick != nowTick;
    }

    static boolean shouldTriggerEcho(int completedShocks) {
        return completedShocks >= MAX_REACTIVE_SHOCKS;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    static double knockbackResistanceBonus() {
        return KNOCKBACK_RESISTANCE_BONUS;
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
                        KNOCKBACK_RESISTANCE_BONUS,
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

    private static void triggerShockwave(
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
                        RING_DURATION_TICKS
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
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            WeaponSkillDamage.apply(
                    player,
                    target,
                    createShockContext(damageMultiplier),
                    nowTick + HIT_CONTEXT_LIFETIME_TICKS
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

    /**
     * Logout destroys transient attribute modifiers with the player entity.
     */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    private static final class State {
        private final ResourceKey<Level> dimension;
        private final long endTick;
        private int shocks;
        private long lastShockTick;

        private State(
                ResourceKey<Level> dimension,
                long endTick
        ) {
            this.dimension = dimension;
            this.endTick = endTick;
        }
    }
}
