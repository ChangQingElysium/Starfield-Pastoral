package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Authoritative farm-animal snapshot for Stardew Valley's AnimalPage. */
public record SyncAnimalOverviewPayload(List<Entry> entries) implements CustomPacketPayload {
    public record Entry(
            long animalId,
            String animalTypeId,
            String customName,
            String displayNameKey,
            String baseType,
            String sourceType,
            int friendship,
            int petStatus,
            boolean receivedAnimalCracker,
            String textureId,
            int textureWidth,
            int textureHeight
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, entry) -> {
                    buf.writeVarLong(entry.animalId());
                    buf.writeUtf(entry.animalTypeId());
                    buf.writeUtf(entry.customName());
                    buf.writeUtf(entry.displayNameKey());
                    buf.writeUtf(entry.baseType());
                    buf.writeUtf(entry.sourceType());
                    buf.writeVarInt(entry.friendship());
                    buf.writeVarInt(entry.petStatus());
                    buf.writeBoolean(entry.receivedAnimalCracker());
                    buf.writeUtf(entry.textureId());
                    buf.writeVarInt(entry.textureWidth());
                    buf.writeVarInt(entry.textureHeight());
                },
                buf -> new Entry(
                        buf.readVarLong(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readBoolean(),
                        buf.readUtf(),
                        buf.readVarInt(),
                        buf.readVarInt())
        );
    }

    public static final Type<SyncAnimalOverviewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "sync_animal_overview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAnimalOverviewPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    SyncAnimalOverviewPayload::entries,
                    SyncAnimalOverviewPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncAnimalOverviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.AnimalOverviewClientCache.update(
                payload.entries().stream()
                        .map(entry -> new com.stardew.craft.client.AnimalOverviewClientCache.Entry(
                                entry.animalId(),
                                entry.animalTypeId(),
                                entry.customName(),
                                entry.displayNameKey(),
                                entry.baseType(),
                                entry.sourceType(),
                                entry.friendship(),
                                entry.petStatus(),
                                entry.receivedAnimalCracker(),
                                entry.textureId(),
                                entry.textureWidth(),
                                entry.textureHeight()))
                        .toList()));
    }
}
