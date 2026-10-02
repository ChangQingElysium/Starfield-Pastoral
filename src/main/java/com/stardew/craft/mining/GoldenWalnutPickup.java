package com.stardew.craft.mining;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
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
                || (com.stardew.craft.port.PortEntities.getTarget(entity) != null && !com.stardew.craft.port.PortEntities.getTarget(entity).equals(player.getUUID()))) return;
        var owner = com.stardew.craft.gingerisland.WalnutDebris.owner(entity.getItem())
                .or(() -> com.stardew.craft.gingerisland.IslandContext.farmInstance(entity.level(), entity.blockPosition()));
        if (owner.isEmpty() && com.stardew.craft.gingerisland.IslandContext.isBound(entity.level(), entity.blockPosition())) {
            event.setCanPickup(TriState.FALSE);
            return;
        }
        if (owner.isPresent() && !com.stardew.craft.gingerisland.IslandContext.canModify(player, owner.get())) {
            event.setCanPickup(TriState.FALSE);
            return;
        }
        var data = owner.map(id -> GoldenWalnutData.get(player.server, id)).orElseGet(() -> GoldenWalnutData.get(player.server));
        data.discover(entity.getItem().getCount());
        event.setCanPickup(TriState.FALSE);
        player.take(entity, entity.getItem().getCount());
        entity.discard();
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 1.0F);
        player.displayClientMessage(Component.translatable("item.stardewcraft.golden_walnut")
                .append(": " + data.balance()), true);
    }
}
