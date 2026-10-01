package com.stardew.craft.client.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.tool.FishingRodItem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Render-only compatibility: custom armor must not be mapped onto the fishing arm mesh. */
@OnlyIn(Dist.CLIENT)
public final class FishingArmorVisibility {
    private FishingArmorVisibility() {}

    public static boolean shouldHide(Entity wearer,ItemStack stack) {
        if(!(wearer instanceof AbstractClientPlayer player)
                ||!(player.getMainHandItem().getItem() instanceof FishingRodItem)
                ||stack==null||stack.isEmpty())return false;
        String namespace=BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return !namespace.equals("minecraft")&&!namespace.equals(StardewCraft.MODID);
    }

}
