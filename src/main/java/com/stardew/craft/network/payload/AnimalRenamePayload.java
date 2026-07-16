package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.service.AnimalEntitySyncService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnimalRenamePayload(long animalId, String newName) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<AnimalRenamePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "animal_rename"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, AnimalRenamePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarLong(payload.animalId);
            buf.writeUtf(payload.newName, 128);
        },
        buf -> new AnimalRenamePayload(buf.readVarLong(), buf.readUtf(128))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(AnimalRenamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (payload.animalId <= 0L) {
                return;
            }

            String normalized = payload.newName == null ? "" : payload.newName.trim();
            AnimalWorldData data = AnimalWorldData.get(serverPlayer.serverLevel());

            var animalOpt = data.getAnimal(payload.animalId);
            if (animalOpt.isEmpty()) {
                return;
            }

            var buildingOpt = data.getBuilding(animalOpt.get().buildingId());
            if (buildingOpt.isEmpty()) {
                return;
            }

            String ownerUuid = buildingOpt.get().ownerPlayerUuid();
            if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                    .canOperateBuilding(serverPlayer.getUUID(), ownerUuid)) {
                serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.animal.query.no_permission"));
                return;
            }

            if (!normalized.isBlank() && data.hasOtherAnimalWithName(payload.animalId, normalized)) {
                serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.animal.query.rename_duplicate"));
                return;
            }
            data.renameAnimal(payload.animalId, normalized);

            // Push name change to loaded entity immediately so HUD/name queries reflect the update now.
            data.getAnimal(payload.animalId).ifPresent(record ->
                AnimalEntitySyncService.updateLoaded(serverPlayer.serverLevel(), record)
            );
        });
    }
}
