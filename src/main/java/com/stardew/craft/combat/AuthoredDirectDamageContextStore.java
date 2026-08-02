package com.stardew.craft.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Exact Post identity for authored direct damage outside the weapon pipeline.
 *
 * <p>Frames intentionally contain no weapon metadata. They cannot become a
 * {@link ResolvedWeaponHit} or trigger weapon rewards.</p>
 */
public final class AuthoredDirectDamageContextStore {
    private static final FrameStack ACTIVE = new FrameStack();

    private AuthoredDirectDamageContextStore() {
    }

    public static void bind(
            ServerPlayer owner,
            LivingEntity target,
            DamageSource source,
            String authoredId,
            long expireTick
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        ACTIVE.bind(
                owner.getUUID(),
                target.getUUID(),
                source,
                authoredId,
                expireTick
        );
    }

    public static Frame consume(
            LivingEntity target,
            DamageSource source,
            long nowTick
    ) {
        if (target == null || source == null) {
            return null;
        }
        return ACTIVE.consume(
                target.getUUID(),
                source,
                nowTick
        );
    }

    public static boolean isBound(
            LivingEntity target,
            DamageSource source,
            String authoredId,
            long nowTick
    ) {
        if (target == null || source == null || authoredId == null) {
            return false;
        }
        return ACTIVE.isBound(
                target.getUUID(),
                source,
                authoredId,
                nowTick
        );
    }

    /** Removes an exact frame only when its synchronous hurt emitted no Post. */
    public static void discard(
            LivingEntity target,
            DamageSource source
    ) {
        if (target == null || source == null) {
            return;
        }
        ACTIVE.discard(target.getUUID(), source);
    }

    public static void clear(ServerPlayer owner) {
        if (owner != null) {
            ACTIVE.clear(owner.getUUID());
        }
    }

    public record Frame(UUID ownerId, String authoredId) {
        public Frame {
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(authoredId, "authoredId");
        }
    }

    /** Package-private pure state seam for identity and nesting tests. */
    static final class FrameStack {
        private final Deque<BoundFrame> frames = new ArrayDeque<>();

        synchronized void bind(
                UUID ownerId,
                UUID targetId,
                Object source,
                String authoredId,
                long expireTick
        ) {
            frames.push(new BoundFrame(
                    Objects.requireNonNull(targetId, "targetId"),
                    Objects.requireNonNull(source, "source"),
                    expireTick,
                    new Frame(ownerId, authoredId)
            ));
        }

        synchronized Frame consume(
                UUID targetId,
                Object source,
                long nowTick
        ) {
            removeExpired(nowTick);
            BoundFrame bound = frames.peek();
            if (bound == null || !bound.matches(targetId, source)) {
                return null;
            }
            frames.pop();
            return bound.frame();
        }

        synchronized boolean isBound(
                UUID targetId,
                Object source,
                String authoredId,
                long nowTick
        ) {
            removeExpired(nowTick);
            BoundFrame bound = frames.peek();
            return bound != null
                    && bound.matches(targetId, source)
                    && bound.frame().authoredId().equals(authoredId);
        }

        synchronized void discard(UUID targetId, Object source) {
            BoundFrame bound = frames.peek();
            if (bound != null && bound.matches(targetId, source)) {
                frames.pop();
            }
        }

        synchronized void clear(UUID ownerId) {
            frames.removeIf(bound -> bound.frame().ownerId().equals(ownerId));
        }

        synchronized int size() {
            return frames.size();
        }

        private void removeExpired(long nowTick) {
            while (!frames.isEmpty()
                    && frames.peek().expireTick() < nowTick) {
                frames.pop();
            }
        }
    }

    private record BoundFrame(
            UUID targetId,
            Object source,
            long expireTick,
            Frame frame
    ) {
        private boolean matches(UUID candidateTargetId, Object candidate) {
            return targetId.equals(candidateTargetId) && source == candidate;
        }
    }
}
