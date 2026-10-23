package com.stardew.craft.platform.fabric.event.entity.player;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.util.TriState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InteractionDispatchTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void canceledPassSkipsBothActionsAndEveryVanillaGate() throws ReflectiveOperationException {
        var event = event();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.PASS);
        assertEquals(InteractionResult.PASS, InteractionDispatch.executeBlockUse(event,
                () -> fail("block gate"), () -> fail("block action"),
                () -> fail("item gate"), () -> fail("item action")));
    }

    @Test void allCancellationResultsAreReturnedWithoutRunningTheActions() throws ReflectiveOperationException {
        for (InteractionResult result : InteractionResult.values()) {
            var event = event();
            event.setCanceled(true);
            event.setCancellationResult(result);
            assertSame(result, InteractionDispatch.executeBlockUse(event,
                    () -> fail("block gate"), () -> fail("block action"),
                    () -> fail("item gate"), () -> fail("item action")));
        }
    }

    @Test void triStatesControlBlockAndItemSeparately() throws ReflectiveOperationException {
        for (TriState block : TriState.values()) for (TriState item : TriState.values()) {
            var event = event();
            event.setUseBlock(block);
            event.setUseItem(item);
            List<String> calls = new ArrayList<>();
            InteractionResult result = InteractionDispatch.executeBlockUse(event,
                    () -> { calls.add("block-gate"); return false; },
                    () -> { calls.add("block-action"); return InteractionResult.PASS; },
                    () -> { calls.add("item-gate"); return false; },
                    () -> { calls.add("item-action"); return InteractionResult.CONSUME; });
            assertEquals(block == TriState.TRUE, calls.contains("block-action"));
            assertEquals(item == TriState.TRUE, calls.contains("item-action"));
            assertEquals(block == TriState.DEFAULT, calls.contains("block-gate"));
            assertEquals(item == TriState.DEFAULT, calls.contains("item-gate"));
            assertSame(item == TriState.TRUE ? InteractionResult.CONSUME : InteractionResult.PASS, result);
        }
    }

    @Test void aConsumedBlockActionDoesNotEvaluateTheItemGate() throws ReflectiveOperationException {
        var event = event();
        assertEquals(InteractionResult.SUCCESS, InteractionDispatch.executeBlockUse(event,
                () -> true, () -> InteractionResult.SUCCESS,
                () -> fail("item gate"), () -> fail("item action")));
    }

    @Test void defaultsKeepTheVanillaOrder() throws ReflectiveOperationException {
        List<String> calls = new ArrayList<>();
        var result = InteractionDispatch.executeBlockUse(event(),
                () -> { calls.add("block-gate"); return true; },
                () -> { calls.add("block-action"); return InteractionResult.PASS; },
                () -> { calls.add("item-gate"); return true; },
                () -> { calls.add("item-action"); return InteractionResult.CONSUME; });
        assertEquals(InteractionResult.CONSUME, result);
        assertEquals(List.of("block-gate", "block-action", "item-gate", "item-action"), calls);
    }

    @Test void earlyItemUseRunsForDefaultAndTrueButNeverForDeniedOrCanceledInteractions() throws ReflectiveOperationException {
        var event = event();
        assertEquals(InteractionResult.CONSUME, InteractionDispatch.executeFirstUse(event, () -> InteractionResult.CONSUME));
        event.setUseItem(TriState.TRUE);
        assertEquals(InteractionResult.FAIL, InteractionDispatch.executeFirstUse(event, () -> InteractionResult.FAIL));
        event.setUseItem(TriState.FALSE);
        assertEquals(InteractionResult.PASS, InteractionDispatch.executeFirstUse(event, () -> fail("denied first-use")));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        assertEquals(InteractionResult.SUCCESS, InteractionDispatch.executeFirstUse(event, () -> fail("canceled first-use")));
    }

    @Test void uncancelingDoesNotInventNewTriStateDecisions() throws ReflectiveOperationException {
        var event = event();
        event.setUseBlock(TriState.TRUE);
        event.setUseItem(TriState.TRUE);
        event.setCanceled(true);
        event.setCanceled(false);
        assertEquals(TriState.FALSE, event.getUseBlock());
        assertEquals(TriState.FALSE, event.getUseItem());
        event.setUseItem(TriState.TRUE);
        assertEquals(InteractionResult.SUCCESS, InteractionDispatch.executeBlockUse(event,
                () -> fail("block gate"), () -> fail("block action"),
                () -> fail("item gate"), () -> InteractionResult.SUCCESS));
    }

    @Test void positionsAreImmutableButHeldItemReadsRemainLive() throws ReflectiveOperationException {
        var player = InteractionTestPlayer.create(new ItemStack(Items.STONE));
        var pos = new BlockPos.MutableBlockPos(1, 2, 3);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.OFF_HAND, pos,
                new BlockHitResult(Vec3.ZERO, Direction.UP, pos, false));
        pos.set(7, 8, 9);
        player.held = new ItemStack(Items.DIRT);
        assertEquals(new BlockPos(1, 2, 3), event.getPos());
        assertSame(player.held, event.getItemStack());
        assertEquals(InteractionHand.OFF_HAND, event.getHand());
        assertEquals(Direction.UP, event.getFace());
        assertThrows(NullPointerException.class, () -> event.setCancellationResult(null));
    }

    @Test void nestedLeftClickScopesRestoreTheOuterContextEvenAfterFailure() throws ReflectiveOperationException {
        var player = InteractionTestPlayer.create(ItemStack.EMPTY);
        var outer = new PlayerInteractEvent.LeftClickBlock(player, BlockPos.ZERO, Direction.UP,
                PlayerInteractEvent.LeftClickBlock.Action.START);
        var inner = new PlayerInteractEvent.LeftClickBlock(player, BlockPos.ZERO, Direction.DOWN,
                PlayerInteractEvent.LeftClickBlock.Action.ABORT);
        assertNull(InteractionDispatch.currentLeftClick());
        InteractionDispatch.withLeftClick(outer, () -> {
            assertSame(outer, InteractionDispatch.currentLeftClick());
            assertThrows(IllegalStateException.class, () -> InteractionDispatch.withLeftClick(inner, () -> {
                assertSame(inner, InteractionDispatch.currentLeftClick());
                throw new IllegalStateException("fixture");
            }));
            assertSame(outer, InteractionDispatch.currentLeftClick());
            return null;
        });
        assertNull(InteractionDispatch.currentLeftClick());
    }

    @Test void packetActionsAndClientHoldStayDistinct() {
        assertEquals(PlayerInteractEvent.LeftClickBlock.Action.START,
                PlayerInteractEvent.LeftClickBlock.Action.convert(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK));
        assertEquals(PlayerInteractEvent.LeftClickBlock.Action.STOP,
                PlayerInteractEvent.LeftClickBlock.Action.convert(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK));
        assertEquals(PlayerInteractEvent.LeftClickBlock.Action.ABORT,
                PlayerInteractEvent.LeftClickBlock.Action.convert(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK));
        assertNotEquals(PlayerInteractEvent.LeftClickBlock.Action.START, PlayerInteractEvent.LeftClickBlock.Action.CLIENT_HOLD);
    }

    private static PlayerInteractEvent.RightClickBlock event() throws ReflectiveOperationException {
        return new PlayerInteractEvent.RightClickBlock(InteractionTestPlayer.create(ItemStack.EMPTY),
                InteractionHand.MAIN_HAND, BlockPos.ZERO,
                new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false));
    }
}
