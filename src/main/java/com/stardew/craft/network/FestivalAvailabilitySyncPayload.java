package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientFestivalAvailability;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** S→C: world-level festival availability used by the client calendar. */
public record FestivalAvailabilitySyncPayload(List<String> festivalIds) implements CustomPacketPayload {
    public static final Type<FestivalAvailabilitySyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "festival_availability_sync"));

    public static final StreamCodec<ByteBuf, FestivalAvailabilitySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), FestivalAvailabilitySyncPayload::festivalIds,
            FestivalAvailabilitySyncPayload::new);

    public FestivalAvailabilitySyncPayload {
        festivalIds = festivalIds == null ? List.of() : List.copyOf(festivalIds);
    }

    public static FestivalAvailabilitySyncPayload current() {
        return new FestivalAvailabilitySyncPayload(FestivalRegistry.all().stream()
                .filter(FestivalService::passesAvailableWhen)
                .map(definition -> definition.id())
                .sorted()
                .toList());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FestivalAvailabilitySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientFestivalAvailability.replace(payload.festivalIds().stream()
                .collect(Collectors.toUnmodifiableSet())));
    }
}
