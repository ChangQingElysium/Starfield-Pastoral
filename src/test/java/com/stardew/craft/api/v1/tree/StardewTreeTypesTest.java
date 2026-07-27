package com.stardew.craft.api.v1.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StardewTreeTypesTest {
    @Test
    void descriptorStateAndTapperCycleAreDefensive() {
        StardewTreeType type = new StardewTreeType(
                id("moon_tree"),
                StardewTreeType.Kind.WILD,
                "block.tree_test.moon_tree",
                21,
                6,
                true
        );
        BlockPos.MutableBlockPos mutableRoot = new BlockPos.MutableBlockPos(1, 2, 3);
        BlockPos.MutableBlockPos mutableSupport = new BlockPos.MutableBlockPos(1, 3, 3);
        StardewTreeState state = new StardewTreeState(
                type.id(),
                mutableRoot,
                StardewTreeState.Part.TRUNK,
                5,
                true,
                List.of(mutableSupport, mutableSupport)
        );
        mutableRoot.set(9, 9, 9);
        mutableSupport.set(8, 8, 8);

        assertEquals(new BlockPos(1, 2, 3), state.root());
        assertEquals(List.of(new BlockPos(1, 3, 3)), state.tapperSupports());

        ItemStack source = new ItemStack(Items.HONEY_BOTTLE, 2);
        StardewTreeRuntimeAdapter.TapperCycle cycle =
                new StardewTreeRuntimeAdapter.TapperCycle(source, 4);
        source.setCount(1);
        ItemStack firstRead = cycle.output();
        firstRead.setCount(9);
        assertEquals(2, cycle.output().getCount());
        assertNotSame(firstRead, cycle.output());
    }

    @Test
    void invalidDescriptorAndCycleAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StardewTreeType(
                id("invalid"),
                StardewTreeType.Kind.OTHER,
                "",
                -1,
                0,
                false
        ));
        assertThrows(IllegalArgumentException.class, () ->
                new StardewTreeRuntimeAdapter.TapperCycle(ItemStack.EMPTY, 0));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tree_test", path);
    }
}
