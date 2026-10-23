package com.stardew.craft.platform.fabric.event.level;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.entity.player.InteractionTestPlayer;
import com.stardew.craft.platform.fabric.extension.ItemAbility;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockToolModificationEventTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void originalStateContextAbilityAndSimulationAreRetained() throws ReflectiveOperationException {
        var player = InteractionTestPlayer.create(new ItemStack(Items.IRON_HOE));
        var context = new Context(player);
        var ability = ItemAbility.get("hoe_till");
        var original = Blocks.DIRT.defaultBlockState();
        var event = new BlockEvent.BlockToolModificationEvent(original, context, ability, true);
        assertSame(context, event.getContext());
        assertSame(player, event.getPlayer());
        assertSame(player.held, event.getHeldItemStack());
        assertSame(ability, event.getItemAbility());
        assertSame(original, event.getState());
        assertSame(original, event.getFinalState());
        assertEquals(new BlockPos(4, 5, 6), event.getPos());
        assertTrue(event.isSimulated());
        assertFalse(event.isCanceled());
        event.setFinalState(Blocks.FARMLAND.defaultBlockState());
        assertSame(Blocks.FARMLAND.defaultBlockState(), event.getFinalState());
        assertSame(original, event.getState());
        event.setCanceled(true);
        assertTrue(event.isCanceled());
        event.setFinalState(null);
        assertNull(event.getFinalState());
    }

    @Test void nullActorAndAnExplicitNullFinalStateAreValid() {
        var context = new Context(null);
        var event = new BlockEvent.BlockToolModificationEvent(Blocks.DIRT.defaultBlockState(), context,
                ItemAbility.get("shovel_flatten"), false);
        assertNull(event.getPlayer());
        assertFalse(event.isSimulated());
        event.setFinalState(null);
        assertNull(event.getFinalState());
    }

    private static final class Context extends UseOnContext {
        Context(InteractionTestPlayer player) {
            super(null, player, InteractionHand.MAIN_HAND, player == null ? ItemStack.EMPTY : player.held,
                    new BlockHitResult(Vec3.ZERO, Direction.UP, new BlockPos(4, 5, 6), false));
        }
    }
}
