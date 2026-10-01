package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
public record OpenFestivalConfirmPayload(Action action) implements CustomPacketPayload {
    public enum Action {
        ENTER,
        EXIT,
        START_CONTEST,
        START_DANCE,
        LUAU_ADD_SOUP,
        LUAU_START,
        MOONLIGHT_JELLIES_START,
        FESTIVAL_OF_ICE_START,
        END_CONTEST,
        FESTIVAL_END;

        static Action parse(String value) {
            try {
                return Action.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return ENTER;
            }
        }
    }

    public static final Type<OpenFestivalConfirmPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_festival_confirm"));

    public static final StreamCodec<FriendlyByteBuf, OpenFestivalConfirmPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.action().name()),
        buf -> new OpenFestivalConfirmPayload(Action.parse(buf.readUtf(32)))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFestivalConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenFestivalConfirmPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Component question = switch (payload.action()) {
            case EXIT -> Component.translatable("message.stardewcraft.festival.exit_confirm");
            case START_CONTEST -> Component.translatable("message.stardewcraft.festival.egg.start_confirm");
            case START_DANCE -> Component.translatable("message.stardewcraft.festival.flower_dance.start_confirm");
            case LUAU_ADD_SOUP -> Component.translatable("message.stardewcraft.festival.luau.add_soup_confirm");
            case LUAU_START -> Component.translatable("message.stardewcraft.festival.luau.start_confirm");
            case MOONLIGHT_JELLIES_START -> Component.translatable("message.stardewcraft.festival.moonlight_jellies.start_confirm");
            case FESTIVAL_OF_ICE_START -> Component.translatable("message.stardewcraft.festival.ice_fishing.start_confirm");
            case END_CONTEST -> Component.translatable("message.stardewcraft.festival.egg.end_contest_confirm");
            case FESTIVAL_END -> Component.translatable("message.stardewcraft.festival.egg.festival_end_confirm");
            case ENTER -> Component.translatable("message.stardewcraft.festival.enter_confirm");
        };
        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                question,
                List.of(
                    Component.translatable("message.stardewcraft.festival.confirm.yes"),
                    Component.translatable("message.stardewcraft.festival.confirm.no")
                ),
                index -> PacketDistributor.sendToServer(new FestivalConfirmPayload(payload.action(), index == 0)),
                -1
            )
        ));
    }
}
