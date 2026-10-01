package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.nature.TeaBushBlock;
import com.stardew.craft.interior.SunroomService;
import com.stardew.craft.network.payload.SunroomTeaBushActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

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
