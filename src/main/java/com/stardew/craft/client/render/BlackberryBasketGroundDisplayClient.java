package com.stardew.craft.client.render;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.cutscene.command.GroundItemCommand;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.quest.BlackberryBasketQuestService;
import com.stardew.craft.quest.network.ClientQuestData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Display;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Per-player client-side ground item display for quest #107. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class BlackberryBasketGroundDisplayClient {
    private static Display.ItemDisplay display;
    private static ClientLevel displayLevel;

    private BlackberryBasketGroundDisplayClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        boolean shouldShow = shouldShow(minecraft, level);

        if (display != null && (display.isRemoved() || level != displayLevel || !shouldShow)) {
            display.discard();
            display = null;
            displayLevel = null;
        }
        if (!shouldShow || display != null || level == null) {
            return;
        }

        display = GroundItemCommand.createClientDisplay(
                level,
                "quest_107_blackberry_basket",
                "stardewcraft:blackberry_basket",
                BlackberryBasketQuestService.BASKET_POS.getX() + 0.5D,
                BlackberryBasketQuestService.BASKET_POS.getY(),
                BlackberryBasketQuestService.BASKET_POS.getZ() + 0.5D,
                1.0F,
                0.0F);
        displayLevel = display == null ? null : level;
    }

    private static boolean shouldShow(Minecraft minecraft, ClientLevel level) {
        if (level == null
                || minecraft.player == null
                || !ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                || ClientPlayerDataCache.hasMailFlag(BlackberryBasketQuestService.PICKED_UP_FLAG)
                || minecraft.player.getInventory().countItem(ModItems.BLACKBERRY_BASKET.get()) > 0) {
            return false;
        }
        return ClientQuestData.getQuestLog().stream()
                .anyMatch(quest -> BlackberryBasketQuestService.QUEST_ID.equals(quest.getId())
                        && quest.isAccepted()
                        && !quest.isCompleted()
                        && !quest.isDestroy());
    }
}
