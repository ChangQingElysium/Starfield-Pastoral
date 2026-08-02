package com.stardew.craft.combat;

import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Authorizes one exact ordinary {@link Player#attack} damage invocation.
 *
 * <p>The frame exists only around the synchronous primary-target
 * {@code hurt} call. Matching the exact {@link DamageSource} instance prevents
 * unrelated code that merely uses {@code playerAttack} from borrowing ordinary
 * weapon provenance.</p>
 */
public final class OrdinaryWeaponAttackFrameStore {
    private static final FrameStack ACTIVE = new FrameStack();

    private OrdinaryWeaponAttackFrameStore() {
    }

    public static void bind(
            Player attacker,
            LivingEntity target,
            DamageSource source,
            WeaponDamageSnapshot weaponSnapshot,
            long gameTick
    ) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(weaponSnapshot, "weaponSnapshot");
        ACTIVE.bind(
                attacker.getUUID(),
                target.getUUID(),
                source,
                weaponSnapshot,
                gameTick
        );
    }

    public static Frame claim(
            Player attacker,
            LivingEntity target,
            DamageSource source,
            long gameTick
    ) {
        if (attacker == null || target == null || source == null) {
            return null;
        }
        return ACTIVE.claim(
                attacker.getUUID(),
                target.getUUID(),
                source,
                gameTick
        );
    }

    /** Removes a frame when the wrapped hurt call emitted no damage event. */
    public static void discard(
            Player attacker,
            LivingEntity target,
            DamageSource source
    ) {
        if (attacker == null || target == null || source == null) {
            return;
        }
        ACTIVE.discard(attacker.getUUID(), target.getUUID(), source);
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            ACTIVE.clear(playerId);
        }
    }

    public record Frame(WeaponDamageSnapshot weaponSnapshot) {
        public Frame {
            Objects.requireNonNull(weaponSnapshot, "weaponSnapshot");
        }
    }

    /** Package-private pure state seam for exact identity and nesting tests. */
    static final class FrameStack {
        private final Map<UUID, Deque<BoundFrame>> frames = new HashMap<>();

        synchronized void bind(
                UUID playerId,
                UUID targetId,
                Object source,
                WeaponDamageSnapshot weaponSnapshot,
                long gameTick
        ) {
            frames.computeIfAbsent(
                    Objects.requireNonNull(playerId, "playerId"),
                    ignored -> new ArrayDeque<>()
            ).push(new BoundFrame(
                    Objects.requireNonNull(targetId, "targetId"),
                    Objects.requireNonNull(source, "source"),
                    gameTick,
                    new Frame(weaponSnapshot)
            ));
        }

        synchronized Frame claim(
                UUID playerId,
                UUID targetId,
                Object source,
                long gameTick
        ) {
            Deque<BoundFrame> playerFrames = frames.get(playerId);
            removeExpired(playerId, playerFrames, gameTick);
            playerFrames = frames.get(playerId);
            if (playerFrames == null) {
                return null;
            }
            BoundFrame bound = playerFrames.peek();
            if (bound == null
                    || bound.gameTick() != gameTick
                    || !bound.matches(targetId, source)) {
                return null;
            }
            playerFrames.pop();
            removeEmpty(playerId, playerFrames);
            return bound.frame();
        }

        synchronized void discard(
                UUID playerId,
                UUID targetId,
                Object source
        ) {
            Deque<BoundFrame> playerFrames = frames.get(playerId);
            if (playerFrames == null) {
                return;
            }
            BoundFrame bound = playerFrames.peek();
            if (bound != null && bound.matches(targetId, source)) {
                playerFrames.pop();
                removeEmpty(playerId, playerFrames);
            }
        }

        synchronized void clear(UUID playerId) {
            frames.remove(playerId);
        }

        synchronized int size(UUID playerId) {
            Deque<BoundFrame> playerFrames = frames.get(playerId);
            return playerFrames == null ? 0 : playerFrames.size();
        }

        private void removeExpired(
                UUID playerId,
                Deque<BoundFrame> playerFrames,
                long gameTick
        ) {
            while (playerFrames != null
                    && !playerFrames.isEmpty()
                    && playerFrames.peek().gameTick() < gameTick) {
                playerFrames.pop();
            }
            removeEmpty(playerId, playerFrames);
        }

        private void removeEmpty(
                UUID playerId,
                Deque<BoundFrame> playerFrames
        ) {
            if (playerFrames != null && playerFrames.isEmpty()) {
                frames.remove(playerId);
            }
        }
    }

    private record BoundFrame(
            UUID targetId,
            Object source,
            long gameTick,
            Frame frame
    ) {
        private boolean matches(UUID candidateTargetId, Object candidate) {
            return targetId.equals(candidateTargetId) && source == candidate;
        }
    }
}
