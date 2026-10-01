package com.stardew.craft.network.payload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record BuildingWorkPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<BuildingWorkPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:building_work"));
    public static final StreamCodec<FriendlyByteBuf, BuildingWorkPayload> STREAM_CODEC = StreamCodec.of((b,p) -> b.writeNbt(p.data), b -> new BuildingWorkPayload(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BuildingWorkPayload payload, IPayloadContext context) { context.enqueueWork(() -> client(payload)); }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void client(BuildingWorkPayload payload) {
        if(payload.data.hasUUID("ReplySession")) {
            var mc=net.minecraft.client.Minecraft.getInstance();
            if(!(mc.screen instanceof com.stardew.craft.client.gui.BuildingLedgerScreen ledger)
                    || !ledger.acceptsReply(payload.data.getUUID("ReplySession")))return;
            if(payload.data.contains("Preview",10))
                com.stardew.craft.client.building.BuildingPlacementPreview.showUpgrade(payload.data.getCompound("Preview"));
            return;
        }
        if (payload.data.hasUUID("ReplyRequest")) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (!(mc.screen instanceof com.stardew.craft.client.building.BuildingWorkScreen work)
                    || !work.acceptsReply(payload.data.getUUID("ReplyRequest"))) return;
            if (payload.data.contains("Preview", 10))
                com.stardew.craft.client.building.BuildingPlacementPreview.showUpgrade(payload.data.getCompound("Preview"));
            else work.applyReply(payload.data);
            return;
        }
        if (payload.data.contains("Preview", 10)) com.stardew.craft.client.building.BuildingPlacementPreview.showUpgrade(payload.data.getCompound("Preview"));
        else if(payload.data.contains("Obstacles")) net.minecraft.client.Minecraft.getInstance().setScreen(new com.stardew.craft.client.building.BuildingObstaclesScreen(payload.data));
        else net.minecraft.client.Minecraft.getInstance().setScreen(new com.stardew.craft.client.building.BuildingWorkScreen(payload.data));
    }
}
