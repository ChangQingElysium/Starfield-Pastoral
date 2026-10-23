package com.stardew.craft.platform.fabric.extension;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.level.BlockEvent;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockActionsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void cancellationPreventsANormallyValidNativeAction() {
        var state = Blocks.OAK_LOG.defaultBlockState();
        var context = new Context(new ItemStack(Items.IRON_AXE));
        var bus = new EventBus();
        int[] calls = {0};
        bus.addListener(BlockEvent.BlockToolModificationEvent.class, event -> {
            calls[0]++;
            assertSame(state, event.getState());
            assertSame(context, event.getContext());
            assertTrue(event.isSimulated());
            event.setCanceled(true);
        });
        assertNull(BlockActions.getModifiedState(bus, state, context, ItemAbilities.AXE_STRIP, true));
        assertEquals(1, calls[0]);
    }

    @Test void eventOverridesIncludingNullTakePrecedenceOverDefaultRules() {
        var state = Blocks.OAK_LOG.defaultBlockState();
        var context = new Context(new ItemStack(Items.IRON_AXE));
        var bus = new EventBus();
        bus.addListener(BlockEvent.BlockToolModificationEvent.class,
                event -> event.setFinalState(Blocks.STONE.defaultBlockState()));
        assertSame(Blocks.STONE.defaultBlockState(),
                BlockActions.getModifiedState(bus, state, context, ItemAbilities.AXE_STRIP, false));
        var denied = new EventBus();
        denied.addListener(BlockEvent.BlockToolModificationEvent.class, event -> event.setFinalState(null));
        assertNull(BlockActions.getModifiedState(denied, state, context, ItemAbilities.AXE_STRIP, false));
    }

    @Test void untouchedEventFallsThroughOnceAndRetainsTheLogAxis() {
        var state = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        var changed = BlockActions.getModifiedState(new EventBus(), state,
                new Context(new ItemStack(Items.IRON_AXE)), ItemAbilities.AXE_STRIP, true);
        assertNotNull(changed);
        assertTrue(changed.is(Blocks.STRIPPED_OAK_LOG));
        assertEquals(Direction.Axis.X, changed.getValue(RotatedPillarBlock.AXIS));
    }

    @Test void waxAndWeatheringUseNativeBlockMappingsAndPreserveProperties() {
        var context = new Context(new ItemStack(Items.IRON_AXE));
        var waxed = Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.WEST);
        var unwaxed = BlockActions.defaultModifiedState(waxed, context, ItemAbilities.AXE_WAX_OFF, true);
        assertNotNull(unwaxed);
        assertTrue(unwaxed.is(Blocks.WEATHERED_CUT_COPPER_STAIRS));
        assertEquals(Direction.WEST, unwaxed.getValue(net.minecraft.world.level.block.StairBlock.FACING));
        var scraped = BlockActions.defaultModifiedState(unwaxed, context, ItemAbilities.AXE_SCRAPE, true);
        assertNotNull(scraped);
        assertTrue(scraped.is(Blocks.EXPOSED_CUT_COPPER_STAIRS));
        assertEquals(Direction.WEST, scraped.getValue(net.minecraft.world.level.block.StairBlock.FACING));
    }

    @Test void unsupportedToolsCannotUseDefaultActions() {
        var shovel = new Context(new ItemStack(Items.IRON_SHOVEL));
        assertNull(BlockActions.defaultModifiedState(Blocks.OAK_LOG.defaultBlockState(), shovel, ItemAbilities.AXE_STRIP, true));
        assertNull(BlockActions.defaultModifiedState(Blocks.DIRT.defaultBlockState(), shovel, ItemAbilities.HOE_TILL, true));
        assertNull(BlockActions.defaultModifiedState(Blocks.DIRT.defaultBlockState(),
                new Context(ItemStack.EMPTY), ItemAbilities.SHOVEL_FLATTEN, true));
    }

    @Test void simulatedDousingAndPlantTrimmingDoNotTouchTheWorld() {
        // No level exists in this context. A simulated action must not attempt
        // sound, drops, particles or another world mutation through a null level.
        var campfire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
        var doused = BlockActions.defaultModifiedState(campfire,
                new Context(new ItemStack(Items.IRON_SHOVEL)), ItemAbilities.SHOVEL_DOUSE, true);
        assertNotNull(doused);
        assertFalse(doused.getValue(CampfireBlock.LIT));
        assertTrue(campfire.getValue(CampfireBlock.LIT));
        var plant = Blocks.CAVE_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, 1);
        var trimmed = BlockActions.defaultModifiedState(plant,
                new Context(new ItemStack(Items.SHEARS)), ItemAbilities.SHEARS_TRIM, true);
        assertNotNull(trimmed);
        assertEquals(25, trimmed.getValue(GrowingPlantHeadBlock.AGE));
        assertEquals(1, plant.getValue(GrowingPlantHeadBlock.AGE));
    }

    @Test void firestarterLightsOnlyAnEligibleNativeState() {
        var context = new Context(new ItemStack(Items.FLINT_AND_STEEL));
        var unlit = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, false);
        // Bootstrap alone does not load vanilla tag resources. Bind this test's
        // actual vanilla candle holder, then restore every previous tag binding.
        java.util.Map<TagKey<Block>, java.util.List<Holder<Block>>> previous = new java.util.HashMap<>();
        BuiltInRegistries.BLOCK.getTags().forEach(pair -> previous.put(pair.getFirst(), pair.getSecond().stream().toList()));
        var loaded = new java.util.HashMap<>(previous);
        loaded.put(BlockTags.CANDLES, java.util.List.of(Blocks.CANDLE.builtInRegistryHolder()));
        BuiltInRegistries.BLOCK.bindTags(loaded);
        try {
            assertTrue(BlockActions.defaultModifiedState(unlit, context, ItemAbilities.FIRESTARTER_LIGHT, true)
                    .getValue(CandleBlock.LIT));
            assertFalse(unlit.getValue(CandleBlock.LIT));
            assertNull(BlockActions.defaultModifiedState(Blocks.STONE.defaultBlockState(), context,
                    ItemAbilities.FIRESTARTER_LIGHT, true));
        } finally {
            BuiltInRegistries.BLOCK.bindTags(previous);
        }
    }

    private static final class Context extends UseOnContext {
        Context(ItemStack held) {
            super(null, null, InteractionHand.MAIN_HAND, held,
                    new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false));
        }
    }
}
