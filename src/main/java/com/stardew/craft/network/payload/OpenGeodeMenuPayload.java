package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

/**
 * Server → Client: open the geode processing screen.
 */
@SuppressWarnings("null")
public record OpenGeodeMenuPayload() implements CustomPacketPayload {

    private static final ResourceLocation ID = Objects.requireNonNull(
        new ResourceLocation(StardewCraft.MODID, "open_geode_menu"));

    public static final Type<OpenGeodeMenuPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, OpenGeodeMenuPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {},
        buf -> new OpenGeodeMenuPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenGeodeMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient());
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new com.stardew.craft.client.gui.GeodeMenuScreen());
    }
}
