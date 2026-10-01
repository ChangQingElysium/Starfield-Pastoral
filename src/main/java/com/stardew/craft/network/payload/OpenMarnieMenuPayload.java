package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → Client: show Marnie's question dialog. Pet adoption is only listed
 * after the source-game unlock condition has been met.
 */
@SuppressWarnings("null")
public record OpenMarnieMenuPayload(boolean canAdoptPets) implements CustomPacketPayload {

    public static final Type<OpenMarnieMenuPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_marnie_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenMarnieMenuPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBoolean(payload.canAdoptPets()),
        buf -> new OpenMarnieMenuPayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenMarnieMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenMarnieMenuPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        java.util.ArrayList<Component> choices = new java.util.ArrayList<>();
        choices.add(Component.translatable("stardewcraft.npc.marnie.menu.supplies"));
        choices.add(Component.translatable("stardewcraft.npc.marnie.menu.purchase"));
        if (payload.canAdoptPets()) choices.add(Component.translatable("pet.stardewcraft.adopt"));
        choices.add(Component.translatable("stardewcraft.npc.marnie.menu.leave"));
        int adoptionChoice = payload.canAdoptPets() ? 2 : -1;

        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                Component.translatable("stardewcraft.npc.marnie.menu.question"),
                List.copyOf(choices),
                index -> {
                    if (index == 0 || index == 1) PacketDistributor.sendToServer(new MarnieMenuChoicePayload(index));
                    else if (index == adoptionChoice) PacketDistributor.sendToServer(new MarnieMenuChoicePayload(3));
                },
                -1
            )
        ));
    }
}
