package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.StardewConfirmDialogScreen;
import com.stardew.craft.client.gui.common.StardewQuestionDialogSpec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server to client: vanilla yes/no prompt from secret note 20's truck driver. */
@SuppressWarnings("null")
public record OpenSecretNote20QuestionPayload() implements CustomPacketPayload {
    public static final Type<OpenSecretNote20QuestionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_secret_note_20_question"));
    public static final StreamCodec<FriendlyByteBuf, OpenSecretNote20QuestionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> { }, buf -> new OpenSecretNote20QuestionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSecretNote20QuestionPayload payload, IPayloadContext context) {
        context.enqueueWork(OpenSecretNote20QuestionPayload::openClientScreen);
    }

    @OnlyIn(Dist.CLIENT)
    private static void openClientScreen() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.setScreen(StardewConfirmDialogScreen.createQuestionDialog(
                StardewQuestionDialogSpec.of(
                        Component.translatable("stardewcraft.secret_note.20.driver.question"),
                        List.of(Component.translatable("gui.yes"), Component.translatable("gui.no")),
                        answer -> PacketDistributor.sendToServer(new SecretNote20QuestionResponsePayload(answer == 0)),
                        -1)));
    }
}
