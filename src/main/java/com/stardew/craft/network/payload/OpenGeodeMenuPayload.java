package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

/**
 * Server → Client: open the geode processing screen.
 */
@SuppressWarnings("null")
public record OpenGeodeMenuPayload(java.util.List<ResourceLocation> inputs) implements CustomPacketPayload {

    public OpenGeodeMenuPayload {
        inputs = java.util.List.copyOf(inputs);
    }

    private static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_geode_menu"));

    public static final Type<OpenGeodeMenuPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, OpenGeodeMenuPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeCollection(payload.inputs(), FriendlyByteBuf::writeResourceLocation),
        buf -> new OpenGeodeMenuPayload(buf.readList(FriendlyByteBuf::readResourceLocation))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenGeodeMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenGeodeMenuPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new com.stardew.craft.client.gui.GeodeMenuScreen(payload.inputs()));
    }
}
