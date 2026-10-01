package com.stardew.craft.client.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.tool.FishingRodItem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import software.bernie.geckolib.event.GeoRenderEvent;

/** Render-only compatibility: custom armor must not be mapped onto the fishing arm mesh. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class FishingArmorVisibility {
    private FishingArmorVisibility() {}

    public static boolean shouldHide(Entity wearer,ItemStack stack) {
        if(!(wearer instanceof AbstractClientPlayer player)
                ||!(player.getMainHandItem().getItem() instanceof FishingRodItem)
                ||stack==null||stack.isEmpty())return false;
        String namespace=BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return !namespace.equals("minecraft")&&!namespace.equals(StardewCraft.MODID);
    }

    // GeckoLib bypasses HumanoidArmorLayer.renderArmorPiece; use its cancellable render event too.
    @SubscribeEvent public static void geoArmor(GeoRenderEvent.Armor.Pre event) {
        if(event.getEntity() instanceof AbstractClientPlayer player
                &&FishingPresentationClient.worldOwned(player)
                &&shouldHide(player,event.getItemStack()))event.setCanceled(true);
    }
}
