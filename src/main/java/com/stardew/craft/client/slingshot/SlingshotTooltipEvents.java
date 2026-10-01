package com.stardew.craft.client.slingshot;

import com.stardew.craft.item.weapon.SlingshotItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid="stardewcraft", value=Dist.CLIENT)
public final class SlingshotTooltipEvents {
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        var bow = event.getItemStack();
        var player = Minecraft.getInstance().player;
        if (!(bow.getItem() instanceof SlingshotItem) || player == null) return;
        var carried = player.containerMenu.getCarried();
        if (SlingshotItem.accepts(carried)) event.getToolTip().add(Component.translatable(
                "tooltip.stardewcraft.slingshot.load", bow.getHoverName(), carried.getHoverName()));
        else if (carried.isEmpty() && !SlingshotItem.ammunition(bow).isEmpty()) event.getToolTip().add(
                Component.translatable("tooltip.stardewcraft.slingshot.unload", SlingshotItem.ammunition(bow).getHoverName()));
    }
}
