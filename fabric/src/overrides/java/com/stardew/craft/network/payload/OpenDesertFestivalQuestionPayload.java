// Upstream-SHA256: 2569ad6e3638a7a75c940155bf5e42bff5d99307edaa3bc073361496d25bd44b
package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.StardewConfirmDialogScreen;
import com.stardew.craft.client.gui.common.StardewNpcDialogueScreen;
import com.stardew.craft.client.gui.common.StardewQuestionDialogSpec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record OpenDesertFestivalQuestionPayload(
    String context,
    int questionIndex,
    String preDialogueKey,
    String questionJson,
    List<ResponseOption> responses
) implements CustomPacketPayload {

    public record ResponseOption(String choiceId, String labelJson) {
    }

    public static final Type<OpenDesertFestivalQuestionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_desert_festival_question"));

    public static final StreamCodec<FriendlyByteBuf, OpenDesertFestivalQuestionPayload> STREAM_CODEC = StreamCodec.of(
        OpenDesertFestivalQuestionPayload::write,
        OpenDesertFestivalQuestionPayload::read
    );

    private static void write(FriendlyByteBuf buf, OpenDesertFestivalQuestionPayload payload) {
        buf.writeUtf(payload.context(), 64);
        buf.writeVarInt(payload.questionIndex());
        buf.writeUtf(payload.preDialogueKey() == null ? "" : payload.preDialogueKey(), 256);
        buf.writeUtf(payload.questionJson(), 4096);
        buf.writeVarInt(payload.responses().size());
        for (ResponseOption option : payload.responses()) {
            buf.writeUtf(option.choiceId(), 64);
            buf.writeUtf(option.labelJson(), 2048);
        }
    }

    private static OpenDesertFestivalQuestionPayload read(FriendlyByteBuf buf) {
        String context = buf.readUtf(64);
        int questionIndex = buf.readVarInt();
        String preDialogueKey = buf.readUtf(256);
        String questionJson = buf.readUtf(4096);
        int count = Math.max(0, Math.min(16, buf.readVarInt()));
        List<ResponseOption> responses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            responses.add(new ResponseOption(buf.readUtf(64), buf.readUtf(2048)));
        }
        return new OpenDesertFestivalQuestionPayload(context, questionIndex, preDialogueKey, questionJson, List.copyOf(responses));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDesertFestivalQuestionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Client.handleClient(payload));
    }

    // Keep client bytecode and its compiler-generated lambdas out of the common payload.
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    private static final class Client {
        private Client() {}

        private static void handleClient(OpenDesertFestivalQuestionPayload payload) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.level == null || payload.responses().isEmpty()) {
                return;
            }

            Runnable openQuestion = () -> {
                Component question = parseComponent(payload.questionJson(), mc);
                List<Component> labels = new ArrayList<>(payload.responses().size());
                for (ResponseOption option : payload.responses()) {
                    labels.add(parseComponent(option.labelJson(), mc));
                }
                StardewQuestionDialogSpec spec = StardewQuestionDialogSpec.of(
                    question,
                    labels,
                    index -> {
                        if (index >= 0 && index < payload.responses().size()) {
                            PacketDistributor.sendToServer(new DesertFestivalQuestionResponsePayload(
                                payload.context(), payload.questionIndex(), payload.responses().get(index).choiceId()));
                        }
                    },
                    -1
                );
                mc.setScreen(StardewConfirmDialogScreen.createQuestionDialog(spec));
            };

            if (payload.preDialogueKey() != null && !payload.preDialogueKey().isBlank()) {
                String speaker = switch (payload.context()) {
                    case "scholar", "scholar_intro" -> "scholar";
                    case "warper" -> "warper";
                    default -> "desert_festival";
                };
                StardewNpcDialogueScreen dialogue = new StardewNpcDialogueScreen(
                    speaker,
                    OpenNpcDialogueScreenPayload.rawTranslation(payload.preDialogueKey()),
                    0
                ).withAfterClose(openQuestion);
                mc.setScreen(dialogue);
                return;
            }

            openQuestion.run();
        }

        private static Component parseComponent(String json, net.minecraft.client.Minecraft mc) {
            try {
                Component component = Component.Serializer.fromJson(json, mc.level.registryAccess());
                return component == null ? Component.literal(json) : component;
            } catch (Exception ignored) {
                return Component.literal(json == null ? "" : json);
            }
        }
    }
}
