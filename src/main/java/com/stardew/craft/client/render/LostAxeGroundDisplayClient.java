package com.stardew.craft.client.render;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.cutscene.command.GroundItemCommand;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.quest.LostAxeQuestService;
import com.stardew.craft.quest.network.ClientQuestData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Display;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Per-player client-side ground item display for quest #100. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class LostAxeGroundDisplayClient {
    private static Display.ItemDisplay display;
    private static ClientLevel displayLevel;

    private LostAxeGroundDisplayClient() {
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
                "quest_100_lost_axe",
                "stardewcraft:lost_axe",
                LostAxeQuestService.AXE_POS.getX() + 0.5D,
                LostAxeQuestService.AXE_POS.getY(),
                LostAxeQuestService.AXE_POS.getZ() + 0.5D,
                1.0F,
                0.0F);
        displayLevel = display == null ? null : level;
    }

    private static boolean shouldShow(Minecraft minecraft, ClientLevel level) {
        if (level == null
                || minecraft.player == null
                || !ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                || ClientPlayerDataCache.hasMailFlag(LostAxeQuestService.PICKED_UP_FLAG)
                || minecraft.player.getInventory().countItem(ModItems.LOST_AXE.get()) > 0) {
            return false;
        }
        return ClientQuestData.getQuestLog().stream()
                .anyMatch(quest -> LostAxeQuestService.QUEST_ID.equals(quest.getId())
                        && quest.isAccepted()
                        && !quest.isCompleted()
                        && !quest.isDestroy());
    }
}
