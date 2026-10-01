package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.misc.GalaxySoulItem;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderTooltipEvent;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class GalaxySoulClientFx {
    private GalaxySoulClientFx() {
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()
            || (!stack.is(ModItems.GALAXY_SOUL.get()) && !stack.is(ModItems.MAGIC_ROCK_CANDY.get()))) {
            return;
        }

        float phase = (System.currentTimeMillis() % 60_000L) / 1000.0F / 2.2F;
        event.setBorderStart(0xFF000000 | GalaxySoulItem.prismaticRgb(phase));
        event.setBorderEnd(0xFF000000 | GalaxySoulItem.prismaticRgb(phase + 0.42F));
        event.setBackgroundStart(0xF00A0618);
        event.setBackgroundEnd(0xF002081C);
    }

    public static MutableComponent prismaticTypeLabel(String raw) {
        return GalaxySoulItem.prismaticText(raw, true, 0.58F);
    }

    public static MutableComponent prismaticDescriptionLine(String raw, float phaseOffset) {
        return GalaxySoulItem.prismaticText(raw, false, 0.58F + phaseOffset);
    }
}
