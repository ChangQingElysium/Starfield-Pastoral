package com.stardew.craft.platform.fabric.item.repair;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.extension.ItemDurabilityProvider;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemRepairsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test
    void noRepairMutatesOnlyTheGivenPropertiesAndReturnsTheSameChainReceiver() {
        var first = new PropertiesFixture(); var second = new PropertiesFixture();
        assertSame(first, ItemRepairs.setNoRepair(first)); assertFalse(first.stardewcraft$canRepair()); assertTrue(second.stardewcraft$canRepair());
        assertSame(first, ItemRepairs.setNoRepair(first)); assertFalse(first.stardewcraft$canRepair());
        assertThrows(IllegalStateException.class, () -> ItemRepairs.setNoRepair(new Item.Properties()));
    }
    @Test
    void repairabilityUsesItemDamageabilityIncludingUnbreakableAndRespectsExistingProvider() throws Exception {
        var item = allocate(ItemFixture.class); item.repairable = true;
        var stack = new ItemStack(Items.DIAMOND_HOE); stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        assertFalse(stack.isDamageableItem()); assertTrue(ItemRepairs.isRepairable(item, stack));
        item.repairable = false; assertFalse(ItemRepairs.isRepairable(item, stack));
        item.repairable = true; stack.remove(DataComponents.MAX_DAMAGE); assertFalse(ItemRepairs.isRepairable(item, stack));
        var provider = allocate(ProviderFixture.class); provider.repairable = true;
        assertFalse(ItemRepairs.isRepairable(provider, new ItemStack(Items.DIAMOND_HOE)));
    }
    @Test
    void itemConstructorCopiesTheFlagWithoutBorrowingTheMutableProperties() throws Exception {
        var properties = new PropertiesFixture();
        var item = new com.stardew.craft.platform.mixin.item.repair.mixin.ItemRepairMixin() {};
        var method = com.stardew.craft.platform.mixin.item.repair.mixin.ItemRepairMixin.class.getDeclaredMethod("stardewcraft$repairFlag", Item.Properties.class, org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        method.setAccessible(true); method.invoke(item, properties, new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("<init>", false));
        assertTrue(item.stardewcraft$canRepair()); ItemRepairs.setNoRepair(properties); assertTrue(item.stardewcraft$canRepair());
        method.invoke(item, properties, new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("<init>", false)); assertFalse(item.stardewcraft$canRepair());
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
    static final class PropertiesFixture extends Item.Properties implements RepairableProperties {
        private boolean canRepair = true;
        @Override public boolean stardewcraft$canRepair() { return canRepair; }
        @Override public void stardewcraft$disableRepair() { canRepair = false; }
    }
    static class ItemFixture extends Item implements RepairableItem {
        boolean repairable;
        ItemFixture() { super(new Item.Properties()); }
        @Override public boolean stardewcraft$canRepair() { return repairable; }
    }
    static final class ProviderFixture extends ItemFixture implements ItemDurabilityProvider {
        @Override public boolean isDamageable(ItemStack stack) { return false; }
    }
}
