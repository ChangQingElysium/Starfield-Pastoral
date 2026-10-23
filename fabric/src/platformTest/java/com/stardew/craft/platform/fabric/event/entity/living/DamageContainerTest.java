package com.stardew.craft.platform.fabric.event.entity.living;

import static org.junit.jupiter.api.Assertions.*;
import com.stardew.craft.platform.fabric.event.entity.living.damage.DamageContainer;
import com.stardew.craft.platform.fabric.event.entity.living.damage.DamageFrames;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DamageContainerTest {
    @Test
    void reductionsRetainOriginalDamageAndRunModifiersInInsertionOrder() {
        var damage = new DamageContainer(null, 20);
        List<Float> inputs = new ArrayList<>();
        damage.addModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
            assertSame(damage, container); inputs.add(reduction); return reduction * 2;
        });
        damage.addModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> { inputs.add(reduction); return reduction + 1; });
        damage.setReduction(DamageContainer.Reduction.ARMOR, 3);
        assertEquals(List.of(3F, 6F), inputs);
        assertEquals(7, damage.getReduction(DamageContainer.Reduction.ARMOR));
        assertEquals(13, damage.getNewDamage());
        assertEquals(20, damage.getOriginalDamage());
        assertEquals(0, damage.getReduction(DamageContainer.Reduction.MOB_EFFECTS));
        damage.recordBlock(2, 5);
        assertEquals(11, damage.getNewDamage());
        assertEquals(2, damage.getBlockedDamage());
        assertEquals(5, damage.getShieldDamage());
    }

    @Test
    void absorptionConsumesOnlyTheReducedAmountAndPreservesModifierBoundsFromNeo() {
        var damage = new DamageContainer(null, 10);
        damage.addModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> reduction / 2);
        var result = FabricLivingDamageEvents.absorb(damage, 8);
        assertEquals(6, result.damage());
        assertEquals(4, result.absorbed());
        assertEquals(4, result.remaining());
        assertEquals(4, damage.getReduction(DamageContainer.Reduction.ABSORPTION));
        var over = new DamageContainer(null, 10);
        over.addModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> 15);
        var overResult = FabricLivingDamageEvents.absorb(over, 8);
        assertEquals(-5, overResult.damage()); // Neo does not silently clamp plugin-modified final damage.
        assertEquals(10, overResult.absorbed());
        assertEquals(0, overResult.remaining());
    }

    @Test
    void resistanceAndEnchantmentsKeepTheOriginalVanillaMathLocalWhileTrackingTheLedger() {
        var damage = new DamageContainer(null, 10);
        damage.addModifier(DamageContainer.Reduction.MOB_EFFECTS, (container, reduction) -> 1);
        damage.setReduction(DamageContainer.Reduction.MOB_EFFECTS, 2); // vanilla resistance local is still 8
        assertEquals(9, damage.getNewDamage());
        // Neo computes enchantment reduction as ledger current minus vanilla's resulting local.
        damage.setReduction(DamageContainer.Reduction.ENCHANTMENTS, damage.getNewDamage() - 6.4F);
        assertEquals(6.4F, damage.getNewDamage());
        assertEquals(2.6F, damage.getReduction(DamageContainer.Reduction.ENCHANTMENTS), 0.0001F);
    }

    @Test
    void nestedAndExceptionalHurtFramesCannotReplaceOrLeakTheirParents() {
        var firstEntity = new DamageFrames();
        var secondEntity = new DamageFrames();
        assertNull(firstEntity.currentOrNull());
        firstEntity.within(() -> {
            var parent = firstEntity.current();
            parent.container = new DamageContainer(null, 10);
            firstEntity.within(() -> {
                assertNotSame(parent, firstEntity.current());
                firstEntity.current().container = new DamageContainer(null, 30);
                assertThrows(IllegalStateException.class, () -> secondEntity.current());
                return null;
            });
            assertSame(parent, firstEntity.current());
            assertEquals(10, parent.container.getOriginalDamage());
            assertThrows(IllegalArgumentException.class, () -> firstEntity.within(() -> { throw new IllegalArgumentException("listener failed"); }));
            assertSame(parent, firstEntity.current());
            return false;
        });
        assertNull(firstEntity.currentOrNull());
        assertThrows(IllegalStateException.class, firstEntity::current);
    }
}
