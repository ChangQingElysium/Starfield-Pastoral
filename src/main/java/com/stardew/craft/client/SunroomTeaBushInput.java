package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.nature.TeaBushBlock;
import com.stardew.craft.interior.SunroomService;
import com.stardew.craft.network.payload.SunroomTeaBushActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Converts a held attack key into exactly one central-tea-bush action per press. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class SunroomTeaBushInput {
    private static boolean attackWasDown;
    private static boolean sentForCurrentPress;

    private SunroomTeaBushInput() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide()) return;

        BlockState clicked = event.getLevel().getBlockState(event.getPos());
        if (!(clicked.getBlock() instanceof TeaBushBlock)) return;

        BlockPos lowerPos = TeaBushBlock.lowerPos(clicked, event.getPos());
        if (!SunroomService.isCentralTeaBush(event.getLevel(), lowerPos)) return;

        event.setCanceled(true);
        if (attackWasDown || sentForCurrentPress
                || !SunroomService.isPrimaryActionTool(event.getEntity().getMainHandItem())) {
            return;
        }

        sentForCurrentPress = true;
        PacketDistributor.sendToServer(new SunroomTeaBushActionPayload());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean attackDown = minecraft.player != null
                && minecraft.level != null
                && minecraft.options.keyAttack.isDown();
        if (!attackDown) {
            sentForCurrentPress = false;
        }
        attackWasDown = attackDown;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        attackWasDown = false;
        sentForCurrentPress = false;
    }
}
