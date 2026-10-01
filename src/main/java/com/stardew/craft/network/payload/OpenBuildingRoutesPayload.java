package com.stardew.craft.network.payload;

import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenBuildingRoutesPayload(int money, int managerPrice, int width, int height, int prefabPrice, net.minecraft.network.chat.Component materials, long revision, ResourceLocation family, java.util.UUID requestId, boolean robinBusy) implements CustomPacketPayload {
    public OpenBuildingRoutesPayload(int money,int managerPrice,int width,int height,int prefabPrice,net.minecraft.network.chat.Component materials,long revision,ResourceLocation family) {
        this(money,managerPrice,width,height,prefabPrice,materials,revision,family,new java.util.UUID(0,0),false);
    }
    public OpenBuildingRoutesPayload(int money,int managerPrice,int width,int height,int prefabPrice,net.minecraft.network.chat.Component materials,long revision,ResourceLocation family,java.util.UUID requestId) {
        this(money,managerPrice,width,height,prefabPrice,materials,revision,family,requestId,false);
    }
    public static final Type<OpenBuildingRoutesPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:open_building_routes_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBuildingRoutesPayload> STREAM_CODEC = StreamCodec.of(
        (b, p) -> { b.writeInt(p.money); b.writeInt(p.managerPrice); b.writeInt(p.width); b.writeInt(p.height); b.writeInt(p.prefabPrice); com.stardew.craft.port.PortCodecs.COMPONENT.encode(b, p.materials); b.writeLong(p.revision); b.writeResourceLocation(p.family); b.writeUUID(p.requestId); b.writeBoolean(p.robinBusy); }, b -> new OpenBuildingRoutesPayload(b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), com.stardew.craft.port.PortCodecs.COMPONENT.decode(b), b.readLong(), b.readResourceLocation(), b.readUUID(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(OpenBuildingRoutesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenBuildingRoutesPayload payload) { var screen = net.minecraft.client.Minecraft.getInstance().screen;
        if (screen instanceof com.stardew.craft.client.gui.CarpenterMenuScreen catalog) catalog.openChoices(payload); }
}
