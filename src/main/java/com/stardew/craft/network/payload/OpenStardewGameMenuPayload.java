package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.menu.StardewGameMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client request to open the server-authoritative Stardew game menu. */
public record OpenStardewGameMenuPayload() implements CustomPacketPayload {
    public static final Type<OpenStardewGameMenuPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_stardew_game_menu"));
    public static final StreamCodec<ByteBuf, OpenStardewGameMenuPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenStardewGameMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenStardewGameMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new StardewGameMenu(containerId, inventory),
                    Component.translatable("stardewcraft.game_menu.title")));
        });
    }
}
