package com.stardew.craft.animal.runtime;

import net.minecraft.nbt.CompoundTag;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record LivestockShopPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<LivestockShopPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:livestock_shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LivestockShopPayload> CODEC = StreamCodec.of((b,p) -> b.writeNbt(p.data), b -> new LivestockShopPayload(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(LivestockShopPayload payload, IPayloadContext context) { context.enqueueWork(() -> client(payload)); }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void client(LivestockShopPayload payload) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (payload.data.contains("Result")) {
            if (mc.screen instanceof com.stardew.craft.client.animal.LivestockPurchaseScreen screen) screen.result(payload.data);
            else if (mc.screen instanceof com.stardew.craft.client.animal.LivestockShopScreen screen) screen.result(payload.data);
        } else if(payload.data.getString("Kind").equals("query")) {
            net.minecraft.client.gui.screens.Screen current=mc.screen;
            while(current instanceof com.stardew.craft.client.gui.FarmFolioScreen folio && !(current instanceof com.stardew.craft.client.gui.AnimalQueryScreen)) current=folio.parentScreen();
            if(current instanceof com.stardew.craft.client.gui.AnimalQueryScreen query && query.getMenu().containerId==payload.data.getInt("Container"))query.metadata(payload.data);
        } else if (payload.data.getString("Kind").equals("birth")) mc.setScreen(new com.stardew.craft.client.animal.LivestockNamingScreen(payload.data));
        else if (payload.data.getString("Kind").equals("manage")) {
            net.minecraft.client.gui.screens.Screen parent=null;
            com.stardew.craft.client.animal.LivestockManagementScreen previous=null;
            if(payload.data.hasUUID("ReplyLedgerSession")) {
                if(!(mc.screen instanceof com.stardew.craft.client.gui.BuildingLedgerScreen ledger)
                        || !ledger.acceptsReply(payload.data.getUUID("ReplyLedgerSession")))return;
                parent=ledger;
            } else if(payload.data.hasUUID("ReplyNonce")) {
                net.minecraft.client.gui.screens.Screen current=mc.screen;
                while(current instanceof com.stardew.craft.client.gui.FarmFolioScreen folio && !(current instanceof com.stardew.craft.client.animal.LivestockManagementScreen))current=folio.parentScreen();
                if(!(current instanceof com.stardew.craft.client.animal.LivestockManagementScreen management) || !management.acceptsReply(payload.data.getUUID("ReplyNonce")))return;
                previous=management;parent=management.parentScreen();
            }
            var next=new com.stardew.craft.client.animal.LivestockManagementScreen(payload.data,parent);
            if(previous!=null)next.preserveNavigation(previous);
            mc.setScreen(next);
        }
        else if (payload.data.getString("Kind").equals("incubator")) mc.setScreen(new com.stardew.craft.client.animal.LivestockNamingScreen(payload.data));
        else mc.setScreen(new com.stardew.craft.client.animal.LivestockShopScreen(payload.data));
    }
}
