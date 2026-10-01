package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record OpenTotemNamingScreenPayload(
        long blockPos,
        String currentName,
        String totemTypeId,
        int poleId
) implements CustomPacketPayload {

    public static final Type<OpenTotemNamingScreenPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "open_totem_naming_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTotemNamingScreenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, OpenTotemNamingScreenPayload::blockPos,
                    ByteBufCodecs.STRING_UTF8, OpenTotemNamingScreenPayload::currentName,
                    ByteBufCodecs.STRING_UTF8, OpenTotemNamingScreenPayload::totemTypeId,
                    ByteBufCodecs.INT, OpenTotemNamingScreenPayload::poleId,
                    OpenTotemNamingScreenPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenTotemNamingScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenTotemNamingScreenPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new com.stardew.craft.client.gui.TotemNamingScreen(payload));
    }
}
