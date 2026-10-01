package com.stardew.craft.client;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.ForgeEnchantmentGuard;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import com.stardew.craft.port.net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class StardewEnchantmentTooltipFx {
    private StardewEnchantmentTooltipFx() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        Map<String, Component> protectedLabels = new HashMap<>();
        collectProtectedLabels(PortItemData.get(stack, DataComponents.ENCHANTMENTS), protectedLabels);
        collectProtectedLabels(PortItemData.get(stack, DataComponents.STORED_ENCHANTMENTS), protectedLabels);
        if (protectedLabels.isEmpty()) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        for (int i = 0; i < tooltip.size(); i++) {
            Component replacement = protectedLabels.get(tooltip.get(i).getString());
            if (replacement != null) {
                tooltip.set(i, replacement);
            }
        }
    }

    private static void collectProtectedLabels(ItemEnchantments enchantments, Map<String, Component> labels) {
        if (enchantments == null || enchantments.isEmpty()) {
            return;
        }
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            if (!ForgeEnchantmentGuard.isProtectedForgeEnchantment(enchantment)) {
                continue;
            }
            if (!enchantment.isBound()) {
                continue; // PORT(1.20.1): id not registered on 1.20.1, vanilla shows no tooltip line for it
            }
            Component label = enchantment.value().getFullname(entry.getIntValue())
                    .copy()
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            labels.put(label.getString(), label);
        }
    }
}
