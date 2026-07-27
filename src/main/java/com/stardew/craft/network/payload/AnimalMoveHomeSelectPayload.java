package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.service.AnimalHomeTransferService;
import com.stardew.craft.menu.AnimalQueryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnimalMoveHomeSelectPayload(long animalId, String targetBuildingId) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<AnimalMoveHomeSelectPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "animal_move_home_select"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, AnimalMoveHomeSelectPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarLong(payload.animalId);
            buf.writeUtf(payload.targetBuildingId, 128);
        },
        buf -> new AnimalMoveHomeSelectPayload(buf.readVarLong(), buf.readUtf(128))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(AnimalMoveHomeSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (!(serverPlayer.containerMenu instanceof AnimalQueryMenu menu)) {
                return;
            }
            if (payload.animalId <= 0L || menu.getAnimalId() != payload.animalId) {
                return;
            }

            AnimalWorldData data = AnimalWorldData.get(serverPlayer.serverLevel());

            // 权限前置检查：确认玩家可操作源建筑
            var animalOpt = data.getAnimal(payload.animalId);
            if (animalOpt.isPresent()) {
                var srcBuilding = data.getBuilding(animalOpt.get().buildingId());
                if (srcBuilding.isPresent() && !com.stardew.craft.farm.FarmInstanceRegistry.get()
                        .canOperateBuilding(serverPlayer.getUUID(), srcBuilding.get().ownerPlayerUuid())) {
                    serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.animal.query.no_permission"));
                    return;
                }
            }

            AnimalHomeTransferService.Result result =
                    AnimalHomeTransferService.transfer(
                            serverPlayer.serverLevel(),
                            payload.animalId,
                            payload.targetBuildingId,
                            serverPlayer.getUUID().toString());
            if (result == AnimalHomeTransferService.Result.MOVED) {
                serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.animal.query.move_success"));
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.animal.query.move_failed"));
            }
        });
    }
}
