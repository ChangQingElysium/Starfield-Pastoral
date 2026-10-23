package com.stardew.craft.platform.mixin.event.enchant.mixin;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.enchant.FabricEnchantingEvents;
import com.stardew.craft.platform.fabric.event.enchant.PlayerEnchantItemEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnchantItemBridgeTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); FabricEnchantingEvents.install(FabricEvents.GAME); }
    @Test void listWrapperRetainsTheExactMutableNativeResultAndDoesNotShareAcrossInvocations() throws Exception {
        var mixin = new EnchantItemMixin() {}; var first = new Ref<List<EnchantmentInstance>>(); var second = new Ref<List<EnchantmentInstance>>();
        var listA = new ArrayList<EnchantmentInstance>(); var listB = new ArrayList<EnchantmentInstance>(); listA.add(null);
        var hook = EnchantItemMixin.class.getDeclaredMethod("stardewcraft$rememberEnchantments", net.minecraft.world.inventory.EnchantmentMenu.class, RegistryAccess.class, ItemStack.class, int.class, int.class, Operation.class, LocalRef.class); hook.setAccessible(true);
        Operation<List<EnchantmentInstance>> operation = args -> { assertEquals(1, args[3]); assertEquals(12, args[4]); return listA; };
        assertSame(listA, hook.invoke(mixin, null, null, ItemStack.EMPTY, 1, 12, operation, first));
        assertSame(listB, hook.invoke(mixin, null, null, ItemStack.EMPTY, 1, 12, (Operation<List<EnchantmentInstance>>) a -> listB, second));
        assertSame(listA, first.get()); assertSame(listB, second.get());
    }
    @Test void eventReceivesTheFinalBookSlotAndMutableAppliedListBeforeNativeLapisConsumption() throws Exception {
        var mixin = new EnchantItemMixin() {}; var slots = new SimpleContainer(2); var enchanted = new ItemStack(Items.ENCHANTED_BOOK); enchanted.set(DataComponents.REPAIR_COST, 7); slots.setItem(0, enchanted);
        var field = EnchantItemMixin.class.getDeclaredField("enchantSlots"); field.setAccessible(true); field.set(mixin, slots);
        var player = allocate(ServerPlayer.class); var world = allocate(ServerLevel.class); var levelField = Entity.class.getDeclaredField("level"); levelField.setAccessible(true); levelField.set(player, world);
        var lapis = new ItemStack(Items.LAPIS_LAZULI, 3); var applied = new Ref<List<EnchantmentInstance>>(); applied.set(new ArrayList<>()); applied.get().add(null); var trace = new ArrayList<String>();
        Consumer<PlayerEnchantItemEvent> listener = event -> { assertSame(player, event.getEntity()); assertSame(enchanted, event.getEnchantedItem()); assertSame(applied.get(), event.getEnchantments()); assertEquals(3, lapis.getCount()); event.getEnchantments().clear(); enchanted.set(DataComponents.REPAIR_COST, 0); trace.add("event"); };
        FabricEvents.GAME.addListener(PlayerEnchantItemEvent.class, listener);
        try {
            var hook = EnchantItemMixin.class.getDeclaredMethod("stardewcraft$enchanted", ItemStack.class, int.class, LivingEntity.class, Operation.class, LocalRef.class); hook.setAccessible(true);
            Operation<Void> consume = args -> { assertSame(lapis, args[0]); assertEquals(1, args[1]); assertSame(player, args[2]); assertTrue(applied.get().isEmpty()); assertEquals(0, enchanted.get(DataComponents.REPAIR_COST)); trace.add("consume"); lapis.shrink(1); return null; };
            hook.invoke(mixin, lapis, 1, player, consume, applied);
            assertEquals(List.of("event", "consume"), trace); assertEquals(2, lapis.getCount());
        } finally { FabricEvents.GAME.unregister(listener); }
    }
    private static <T> T allocate(Class<T> type) throws Exception { var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type)); }
    private static final class Ref<T> implements LocalRef<T> { private T value; public T get() { return value; } public void set(T value) { this.value = value; } }
}
