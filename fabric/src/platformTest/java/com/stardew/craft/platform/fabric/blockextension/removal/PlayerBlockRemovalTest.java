package com.stardew.craft.platform.fabric.blockextension.removal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerBlockRemovalTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    @Test void survivalCopiesTheOriginalToolThenChecksHarvestThenMinesRemovesDestroysAndDrops() {
        var actions = new Actions(true, true);
        assertTrue(PlayerBlockRemoval.finish(false, actions));
        assertEquals(List.of("held", "harvest", "mine", "remove:true", "destroy", "drops"), actions.log);
        assertEquals(0, actions.tool.getCount()); assertEquals(1, actions.dropTool.getCount()); assertNotSame(actions.tool, actions.dropTool);
    }
    @Test void aSurvivalVetoStillConsumesTheMiningActionButNeverDestroysOrDrops() {
        var actions = new Actions(true, false); assertTrue(PlayerBlockRemoval.finish(false, actions));
        assertEquals(List.of("held", "harvest", "mine", "remove:true"), actions.log); assertNull(actions.dropTool);
    }
    @Test void aWrongToolStillRemovesAndDestroysTheBlockWithoutHarvesting() {
        var actions = new Actions(false, true); assertTrue(PlayerBlockRemoval.finish(false, actions));
        assertEquals(List.of("held", "harvest", "mine", "remove:false", "destroy"), actions.log); assertNull(actions.dropTool);
    }
    @Test void creativeUsesWillHarvestFalseAndSkipsEveryToolAndLootAction() {
        for (boolean removed : new boolean[]{false, true}) {
            var actions = new Actions(true, removed); assertTrue(PlayerBlockRemoval.finish(true, actions));
            assertEquals(removed ? List.of("remove:false", "destroy") : List.of("remove:false"), actions.log);
            assertEquals(1, actions.tool.getCount()); assertNull(actions.dropTool);
        }
    }
    @Test void removalFailurePropagatesAndNeverRunsDestroyOrDrops() {
        var actions = new Actions(true, true) { @Override public boolean remove(boolean harvest) { super.remove(harvest); throw new IllegalStateException("fixture failure"); } };
        assertThrows(IllegalStateException.class, () -> PlayerBlockRemoval.finish(false, actions));
        assertEquals(List.of("held", "harvest", "mine", "remove:true"), actions.log);
    }
    private static class Actions implements PlayerBlockRemoval.BreakActions {
        final List<String> log = new ArrayList<>(); final boolean harvest, removed; final ItemStack tool = new ItemStack(Items.DIAMOND); ItemStack dropTool;
        Actions(boolean harvest, boolean removed) { this.harvest = harvest; this.removed = removed; }
        @Override public ItemStack heldTool() { log.add("held"); return tool; }
        @Override public boolean canHarvest() { log.add("harvest"); return harvest; }
        @Override public void mine(ItemStack tool) { log.add("mine"); tool.shrink(1); }
        @Override public boolean remove(boolean harvest) { log.add("remove:" + harvest); return removed; }
        @Override public void destroyed() { log.add("destroy"); }
        @Override public void drops(ItemStack snapshot) { log.add("drops"); dropTool = snapshot; }
    }
}
