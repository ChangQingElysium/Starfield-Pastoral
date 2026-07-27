package com.stardew.craft.api.v1.npc;

import com.stardew.craft.api.v1.internal.npc.StardewNpcFriendshipRewardRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Ordered friendship reward handlers with optional core-managed one-shot completion. */
public final class StardewNpcFriendshipRewards {
    private StardewNpcFriendshipRewards() {
    }

    public static void register(ResourceLocation id, int priority, Handler handler) {
        StardewNpcFriendshipRewardRegistry.register(id, priority, handler);
    }

    @FunctionalInterface
    public interface Handler {
        Outcome apply(Context context);
    }

    public enum Outcome {
        /** Nothing was awarded; evaluate this handler again after future point changes. */
        PASS(false, false),
        /** State changed, but the handler may have more rewards to apply later. */
        CHANGED(true, false),
        /** State changed and this registration should never run again for this player. */
        COMPLETE(true, true),
        /** Mark this registration complete without reporting an external reward change. */
        COMPLETE_WITHOUT_REWARD(false, true);

        private final boolean changed;
        private final boolean complete;

        Outcome(boolean changed, boolean complete) {
            this.changed = changed;
            this.complete = complete;
        }

        public boolean changed() {
            return changed;
        }

        public boolean complete() {
            return complete;
        }
    }

    public record Context(
            ServerPlayer player,
            ResourceLocation registrationId,
            ResourceLocation npcId,
            int points,
            int hearts
    ) {
        public Context {
            player = Objects.requireNonNull(player, "player");
            registrationId = Objects.requireNonNull(registrationId, "registrationId");
            npcId = Objects.requireNonNull(npcId, "npcId");
            if (points < 0) {
                throw new IllegalArgumentException("points must be non-negative");
            }
            if (hearts < 0) {
                throw new IllegalArgumentException("hearts must be non-negative");
            }
        }
    }
}
