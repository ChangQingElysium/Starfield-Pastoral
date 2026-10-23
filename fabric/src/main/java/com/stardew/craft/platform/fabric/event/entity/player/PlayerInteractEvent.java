package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.PlayerEvent;
import com.stardew.craft.platform.fabric.util.TriState;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Interaction payloads produced at the corresponding native action, on both logical sides. */
public abstract class PlayerInteractEvent extends PlayerEvent {
    private final InteractionHand hand;
    private final BlockPos pos;
    private final Direction face;

    protected PlayerInteractEvent(Player player, InteractionHand hand, BlockPos pos, Direction face) {
        super(Objects.requireNonNull(player, "player"), true);
        this.hand = Objects.requireNonNull(hand, "hand");
        this.pos = Objects.requireNonNull(pos, "pos").immutable();
        this.face = face;
    }

    public InteractionHand getHand() { return hand; }
    public BlockPos getPos() { return pos; }
    public Direction getFace() { return face; }
    public ItemStack getItemStack() { return getEntity().getItemInHand(hand); }
    public Level getLevel() { return getEntity().level(); }

    public abstract static class CancellableInteraction extends PlayerInteractEvent {
        private InteractionResult cancellationResult = InteractionResult.PASS;

        protected CancellableInteraction(Player player, InteractionHand hand, BlockPos pos, Direction face) {
            super(player, hand, pos, face);
        }

        public InteractionResult getCancellationResult() { return cancellationResult; }
        public void setCancellationResult(InteractionResult result) {
            cancellationResult = Objects.requireNonNull(result, "result");
        }
    }

    public static final class RightClickBlock extends CancellableInteraction {
        private TriState useBlock = TriState.DEFAULT;
        private TriState useItem = TriState.DEFAULT;
        private final BlockHitResult hitVec;

        public RightClickBlock(Player player, InteractionHand hand, BlockPos pos, BlockHitResult hitVec) {
            super(player, hand, pos, hitVec.getDirection());
            this.hitVec = hitVec;
        }

        public TriState getUseBlock() { return useBlock; }
        public TriState getUseItem() { return useItem; }
        public void setUseBlock(TriState value) { useBlock = Objects.requireNonNull(value, "useBlock"); }
        public void setUseItem(TriState value) { useItem = Objects.requireNonNull(value, "useItem"); }
        public BlockHitResult getHitVec() { return hitVec; }

        @Override
        protected void onCancellationChanged(boolean canceled) {
            if (canceled) { useBlock = TriState.FALSE; useItem = TriState.FALSE; }
        }
    }

    public static final class RightClickItem extends CancellableInteraction {
        public RightClickItem(Player player, InteractionHand hand) {
            super(player, hand, player.blockPosition(), null);
        }
    }

    public static final class EntityInteract extends CancellableInteraction {
        private final Entity target;

        public EntityInteract(Player player, InteractionHand hand, Entity target) {
            super(player, hand, target.blockPosition(), null);
            this.target = target;
        }

        public Entity getTarget() { return target; }
    }

    public static final class EntityInteractSpecific extends CancellableInteraction {
        private final Entity target;
        private final net.minecraft.world.phys.Vec3 localPos;
        public EntityInteractSpecific(Player player, InteractionHand hand, Entity target, net.minecraft.world.phys.Vec3 localPos) {
            super(player, hand, target.blockPosition(), null);
            this.target = Objects.requireNonNull(target, "target");
            this.localPos = Objects.requireNonNull(localPos, "localPos");
        }
        public Entity getTarget() { return target; }
        public net.minecraft.world.phys.Vec3 getLocalPos() { return localPos; }
    }

    public static final class LeftClickBlock extends PlayerInteractEvent {
        private TriState useBlock = TriState.DEFAULT;
        private TriState useItem = TriState.DEFAULT;
        private final Action action;

        public LeftClickBlock(Player player, BlockPos pos, Direction face, Action action) {
            super(player, InteractionHand.MAIN_HAND, pos, face);
            this.action = Objects.requireNonNull(action, "action");
        }

        public Action getAction() { return action; }
        public TriState getUseBlock() { return useBlock; }
        public TriState getUseItem() { return useItem; }
        public void setUseBlock(TriState value) { useBlock = Objects.requireNonNull(value, "useBlock"); }
        public void setUseItem(TriState value) { useItem = Objects.requireNonNull(value, "useItem"); }

        @Override
        protected void onCancellationChanged(boolean canceled) {
            if (canceled) { useBlock = TriState.FALSE; useItem = TriState.FALSE; }
        }

        public enum Action {
            START, STOP, ABORT, CLIENT_HOLD;

            public static Action convert(ServerboundPlayerActionPacket.Action action) {
                return switch (action) {
                    case STOP_DESTROY_BLOCK -> STOP;
                    case ABORT_DESTROY_BLOCK -> ABORT;
                    default -> START;
                };
            }
        }
    }
}
