package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server -> Client: show Marlon's question dialog.
 * hasLostItems: if true, client shows the "Item Recovery" option (SDV parity).
 */
@SuppressWarnings("null")
public record OpenMarlonMenuPayload(boolean hasLostItems, boolean hasDesertFestivalRatingOption,
                                    boolean hasDesertFestivalChallengeOption,
                                    boolean desertFestivalBoothOnly) implements CustomPacketPayload {

    public static final Type<OpenMarlonMenuPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_marlon_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenMarlonMenuPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.hasLostItems());
            buf.writeBoolean(payload.hasDesertFestivalRatingOption());
            buf.writeBoolean(payload.hasDesertFestivalChallengeOption());
            buf.writeBoolean(payload.desertFestivalBoothOnly());
        },
        buf -> new OpenMarlonMenuPayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenMarlonMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenMarlonMenuPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        List<Component> options = new java.util.ArrayList<>();
        List<Integer> choices = new java.util.ArrayList<>();
        Component question = Component.translatable("stardewcraft.npc.marlon.menu.question");
        if (payload.desertFestivalBoothOnly()) {
            question = Component.translatable("stardewcraft.npc.marlon.menu.desert_question");
            options.add(Component.translatable("stardewcraft.npc.marlon.menu.desert_rating"));
            choices.add(3);
            options.add(Component.translatable("stardewcraft.npc.marlon.menu.desert_challenge"));
            choices.add(4);
        } else {
            options.add(Component.translatable("stardewcraft.npc.marlon.menu.shop"));
            choices.add(0);
            if (payload.hasLostItems()) {
                options.add(Component.translatable("stardewcraft.npc.marlon.menu.recovery"));
                choices.add(2);
            }
            if (payload.hasDesertFestivalRatingOption()) {
                options.add(Component.translatable("stardewcraft.npc.marlon.menu.desert_rating"));
                choices.add(3);
            }
            if (payload.hasDesertFestivalChallengeOption()) {
                options.add(Component.translatable("stardewcraft.npc.marlon.menu.desert_challenge"));
                choices.add(4);
            }
        }
        options.add(Component.translatable("stardewcraft.npc.marlon.menu.leave"));
        choices.add(-1);

        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                question,
                options,
                index -> {
                    if (index >= 0 && index < choices.size()) {
                        int choice = choices.get(index);
                        if (choice >= 0) {
                            PacketDistributor.sendToServer(new MarlonMenuChoicePayload(choice));
                        }
                    }
                },
                -1
            )
        ));
    }
}
