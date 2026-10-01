package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record DesertFestivalRaceStatePayload(DesertFestivalRaceSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<DesertFestivalRaceStatePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "desert_festival_race_state"));

    public static final StreamCodec<FriendlyByteBuf, DesertFestivalRaceStatePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> DesertFestivalRaceSnapshot.write(buf, payload.snapshot()),
        buf -> new DesertFestivalRaceStatePayload(DesertFestivalRaceSnapshot.read(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DesertFestivalRaceStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(DesertFestivalRaceStatePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.stardew.craft.client.gui.DesertFestivalRaceSnapshotScreen screen) {
            screen.updateSnapshot(payload.snapshot());
        }
    }
}