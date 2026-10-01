package com.stardew.craft.mining;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/** Currency debris bypasses inventory capacity, but still respects pickup delay, ownership and prior denial. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class GoldenWalnutPickup {
    private GoldenWalnutPickup() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        var entity = event.getItemEntity();
        if (!(event.getPlayer() instanceof ServerPlayer player) || !entity.isAlive()
                || !entity.getItem().is(ModItems.GOLDEN_WALNUT.get()) || event.canPickup() == TriState.FALSE
                || player.isSpectator() || entity.hasPickUpDelay()
                || (entity.getTarget() != null && !entity.getTarget().equals(player.getUUID()))) return;
        var data = GoldenWalnutData.get(player.server);
        data.discover(entity.getItem().getCount());
        event.setCanPickup(TriState.FALSE);
        player.take(entity, entity.getItem().getCount());
        entity.discard();
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 1.0F);
        player.displayClientMessage(Component.translatable("item.stardewcraft.golden_walnut")
                .append(": " + data.balance()), true);
    }
}
