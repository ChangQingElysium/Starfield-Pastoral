package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.menu.StoneChestMenu;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record StoneChestColorSelectPayload(int colorSelection) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<StoneChestColorSelectPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "stone_chest_color_select"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, StoneChestColorSelectPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.colorSelection),
        buf -> new StoneChestColorSelectPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StoneChestColorSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (!(serverPlayer.containerMenu instanceof StoneChestMenu menu)) {
                return;
            }
            if (!menu.stillValid(serverPlayer)) return;
            menu.setColorSelectionFromClient(WoodenChestColorPalette.clampIndex(payload.colorSelection));
        });
    }
}
