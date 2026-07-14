package com.stardew.craft.combat;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.weapon.IStardewWeapon;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ForgeEnchantmentGuard {
    private static final ThreadLocal<Integer> FORGE_TRANSACTION_DEPTH = ThreadLocal.withInitial(() -> 0);

    private ForgeEnchantmentGuard() {
    }

    public static AutoCloseable beginForgeTransaction() {
        FORGE_TRANSACTION_DEPTH.set(FORGE_TRANSACTION_DEPTH.get() + 1);
        return ForgeEnchantmentGuard::endForgeTransaction;
    }

    public static boolean isForgeTransactionActive() {
        return FORGE_TRANSACTION_DEPTH.get() > 0;
    }

    public static boolean isForgeControlledItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof IStardewWeapon) {
            return true;
        }
        String typeKey = StardewItemDataApi.getTypeKey(stack);
        return typeKey.startsWith("stardewcraft.tool.")
                || "stardewcraft.type.tool".equals(typeKey)
                || "stardewcraft.type.fishing".equals(typeKey);
    }

    public static boolean isProtectedForgeEnchantment(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey()
                .map(key -> StardewCraft.MODID.equals(key.location().getNamespace()))
                .orElse(false);
    }

    public static boolean stackHasProtectedForgeEnchantments(ItemStack stack) {
        return componentHasProtectedForgeEnchantments(stack.get(DataComponents.ENCHANTMENTS))
                || componentHasProtectedForgeEnchantments(stack.get(DataComponents.STORED_ENCHANTMENTS));
    }

    public static boolean stripProtectedForgeEnchantments(ItemStack stack, boolean force) {
        if (!force && WeaponForgeData.hasMeaningfulForgeState(stack)) {
            return false;
        }

        boolean changed = false;
        changed |= filterEnchantments(stack, DataComponents.ENCHANTMENTS, false);
        changed |= filterEnchantments(stack, DataComponents.STORED_ENCHANTMENTS, false);
        return changed;
    }

    public static boolean keepOnlyProtectedForgeEnchantments(ItemStack stack) {
        boolean changed = false;
        changed |= filterEnchantments(stack, DataComponents.ENCHANTMENTS, true);
        changed |= filterEnchantments(stack, DataComponents.STORED_ENCHANTMENTS, true);
        return changed;
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (isForgeTransactionActive()) {
            return;
        }
        if (stackHasProtectedForgeEnchantments(event.getRight())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGrindstonePlace(GrindstoneEvent.OnPlaceItem event) {
        if (isForgeTransactionActive()) {
            return;
        }
        ItemStack protectedInput = firstStackWithProtectedForgeEnchantments(event.getTopItem(), event.getBottomItem());
        if (!protectedInput.isEmpty()) {
            ItemStack output = protectedInput.copy();
            keepOnlyProtectedForgeEnchantments(output);
            event.setOutput(output);
            event.setXp(0);
        }
    }

    @SubscribeEvent
    public static void onGrindstoneTake(GrindstoneEvent.OnTakeItem event) {
        if (isForgeTransactionActive()) {
            return;
        }
        if (stackHasProtectedForgeEnchantments(event.getTopItem()) || stackHasProtectedForgeEnchantments(event.getBottomItem())) {
            event.setNewTopItem(ItemStack.EMPTY);
            event.setNewBottomItem(ItemStack.EMPTY);
            event.setXp(0);
        }
    }

    @SubscribeEvent
    public static void onPlayerEnchantItem(PlayerEnchantItemEvent event) {
        if (isForgeTransactionActive()) {
            return;
        }
        ItemStack stack = event.getEnchantedItem();
        event.getEnchantments().removeIf(instance -> isProtectedForgeEnchantment(instance.enchantment));
        stripProtectedForgeEnchantments(stack, true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Command-applied forge enchantments are intentionally allowed for debugging.
    }

    private static ItemStack firstStackWithProtectedForgeEnchantments(ItemStack first, ItemStack second) {
        if (stackHasProtectedForgeEnchantments(first)) {
            return first;
        }
        return stackHasProtectedForgeEnchantments(second) ? second : ItemStack.EMPTY;
    }

    private static boolean componentHasProtectedForgeEnchantments(ItemEnchantments enchantments) {
        if (enchantments == null || enchantments.isEmpty()) {
            return false;
        }
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (isProtectedForgeEnchantment(enchantment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean filterEnchantments(ItemStack stack,
            net.minecraft.core.component.DataComponentType<ItemEnchantments> component,
            boolean keepProtected) {
        ItemEnchantments enchantments = stack.get(component);
        if (enchantments == null || enchantments.isEmpty()) {
            return false;
        }

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        mutable.removeIf(enchantment -> isProtectedForgeEnchantment(enchantment) != keepProtected);
        ItemEnchantments filtered = mutable.toImmutable();
        if (filtered.equals(enchantments)) {
            return false;
        }
        stack.set(component, filtered);
        return true;
    }

    private static void endForgeTransaction() {
        int depth = FORGE_TRANSACTION_DEPTH.get() - 1;
        if (depth <= 0) {
            FORGE_TRANSACTION_DEPTH.remove();
        } else {
            FORGE_TRANSACTION_DEPTH.set(depth);
        }
    }
}
