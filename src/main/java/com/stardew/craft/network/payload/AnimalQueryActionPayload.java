package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.menu.AnimalQueryMenu;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record AnimalQueryActionPayload(Action action, boolean toggleValue) implements CustomPacketPayload {

    public enum Action {
        CLOSE,
        SELL,
        TOGGLE_REPRODUCTION,
        MOVE_HOME
    }

    @SuppressWarnings("null")
    public static final Type<AnimalQueryActionPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "animal_query_action"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, AnimalQueryActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeEnum(payload.action);
            buf.writeBoolean(payload.toggleValue);
        },
        buf -> new AnimalQueryActionPayload(buf.readEnum(Action.class), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnimalQueryActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (!(serverPlayer.containerMenu instanceof AnimalQueryMenu menu)) {
                return;
            }

            switch (payload.action) {
                case CLOSE -> serverPlayer.closeContainer();
                case SELL -> menu.handleSellAnimal();
                case TOGGLE_REPRODUCTION -> menu.handleToggleReproduction(payload.toggleValue);
                case MOVE_HOME -> menu.handleOpenMoveHomeScreen();
            }
        });
    }
}
