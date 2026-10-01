package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.decor.FarmTwigBlock;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.tool.StardewAxeItem;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.minecraftforge.event.level.BlockEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FarmTwigEvents {
    private FarmTwigEvents() {}

    public static float energyCost(ServerPlayer player, ItemStack tool) {
        if (tool.getItem() instanceof StardewAxeItem && StardewEnchantments.has(tool, StardewEnchantments.EFFICIENT)) return 0;
        return Math.max(0, 2 - PlayerStardewDataAPI.getSkillLevel(player, SkillType.FORAGING) * 0.1F);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof FarmTwigBlock)
                || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) return;
        if (!FarmTwigBlock.isAxe(player.getMainHandItem())) {
            event.setCanceled(true);
            return;
        }
        // Only check here: canceled/protected breaks must never spend energy or grant experience.
        if (!PlayerStardewDataAPI.canConsumeEnergy(player, energyCost(player, player.getMainHandItem()))) {
            event.setCanceled(true);
            com.stardew.craft.network.payload.HudHintPayload.send(player, "stardewcraft.message.player.exhausted");
        }
    }

    @SubscribeEvent
    public static void afterBreak(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof FarmTwigBlock)
                || !(event.getBreaker() instanceof ServerPlayer player) || player.isCreative()
                || !FarmTwigBlock.isAxe(event.getTool())) return;
        // Use the pre-break tool snapshot, including when a vanilla axe loses its last durability.
        if (PlayerStardewDataAPI.consumeEnergyOrNotify(player, energyCost(player, event.getTool()))) {
            PlayerStardewDataAPI.addExperience(player, SkillType.FORAGING, 1);
        }
    }
}
