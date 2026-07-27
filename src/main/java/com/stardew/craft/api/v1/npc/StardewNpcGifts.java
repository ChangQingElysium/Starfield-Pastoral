package com.stardew.craft.api.v1.npc;

import com.stardew.craft.api.v1.internal.npc.StardewNpcGiftRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;

/** Gift confirmation policy plus ordered before/after hooks. */
public final class StardewNpcGifts {
    private StardewNpcGifts() {
    }

    public static void registerConfirmationPolicy(
            ResourceLocation id,
            int priority,
            ConfirmationPolicy policy
    ) {
        StardewNpcGiftRegistry.registerConfirmationPolicy(id, priority, policy);
    }

    public static void registerBeforeHook(
            ResourceLocation id,
            int priority,
            BeforeHook hook
    ) {
        StardewNpcGiftRegistry.registerBeforeHook(id, priority, hook);
    }

    public static void registerAfterHook(
            ResourceLocation id,
            int priority,
            AfterHook hook
    ) {
        StardewNpcGiftRegistry.registerAfterHook(id, priority, hook);
    }

    public enum Confirmation {
        PASS,
        REQUIRE_CONFIRMATION,
        GIVE_IMMEDIATELY,
        SKIP_GIFT
    }

    public enum BeforeDecision {
        PASS,
        DENY
    }

    public enum Status {
        ACCEPTED,
        REJECTED_DAILY_LIMIT,
        REJECTED_WEEKLY_LIMIT
    }

    public enum Taste {
        LOVED,
        LIKED,
        NEUTRAL,
        DISLIKED,
        HATED
    }

    @FunctionalInterface
    public interface ConfirmationPolicy {
        Confirmation decide(OfferContext context);
    }

    @FunctionalInterface
    public interface BeforeHook {
        BeforeDecision before(BeforeContext context);
    }

    @FunctionalInterface
    public interface AfterHook {
        void after(Result result);
    }

    public record OfferContext(
            ServerPlayer player,
            Entity npc,
            ResourceLocation npcId,
            InteractionHand hand,
            ItemStack gift
    ) {
        public OfferContext {
            player = Objects.requireNonNull(player, "player");
            npc = Objects.requireNonNull(npc, "npc");
            npcId = Objects.requireNonNull(npcId, "npcId");
            hand = Objects.requireNonNull(hand, "hand");
            gift = copyNonEmpty(gift);
        }

        @Override
        public ItemStack gift() {
            return gift.copy();
        }
    }

    public record BeforeContext(
            ServerPlayer player,
            Entity npc,
            ResourceLocation npcId,
            ItemStack gift,
            StardewNpcFriendshipSnapshot friendship
    ) {
        public BeforeContext {
            player = Objects.requireNonNull(player, "player");
            npc = Objects.requireNonNull(npc, "npc");
            npcId = Objects.requireNonNull(npcId, "npcId");
            gift = copyNonEmpty(gift);
            friendship = Objects.requireNonNull(friendship, "friendship");
        }

        @Override
        public ItemStack gift() {
            return gift.copy();
        }
    }

    public record Result(
            ServerPlayer player,
            Entity npc,
            ResourceLocation npcId,
            ItemStack gift,
            Status status,
            @Nullable Taste taste,
            String tasteSource,
            boolean birthday,
            int friendshipDelta,
            int resultingPoints
    ) {
        public Result {
            player = Objects.requireNonNull(player, "player");
            npc = Objects.requireNonNull(npc, "npc");
            npcId = Objects.requireNonNull(npcId, "npcId");
            gift = copyNonEmpty(gift);
            status = Objects.requireNonNull(status, "status");
            tasteSource = Objects.requireNonNull(tasteSource, "tasteSource");
            if (resultingPoints < 0) {
                throw new IllegalArgumentException(
                        "resultingPoints must be non-negative");
            }
            if (status == Status.ACCEPTED && taste == null) {
                throw new IllegalArgumentException(
                        "accepted gifts require a taste");
            }
        }

        @Override
        public ItemStack gift() {
            return gift.copy();
        }
    }

    private static ItemStack copyNonEmpty(ItemStack stack) {
        Objects.requireNonNull(stack, "gift");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("gift must not be empty");
        }
        return stack.copy();
    }
}
