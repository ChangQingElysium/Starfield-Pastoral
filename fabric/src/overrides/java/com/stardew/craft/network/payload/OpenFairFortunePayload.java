// Upstream-SHA256: 1216f98daec8139a7aae3c09b0982b0c3c00f2aa2b067e8ecca1ca4e0e22b2d1
package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.StardewNpcDialogueScreen;
import com.stardew.craft.cutscene.runtime.EventScreenFade;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record OpenFairFortunePayload(List<String> fortuneJsons) implements CustomPacketPayload {
    public static final Type<OpenFairFortunePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_fair_fortune"));

    public static final StreamCodec<FriendlyByteBuf, OpenFairFortunePayload> STREAM_CODEC = StreamCodec.of(
        OpenFairFortunePayload::write,
        OpenFairFortunePayload::read
    );

    private static void write(FriendlyByteBuf buf, OpenFairFortunePayload payload) {
        buf.writeVarInt(payload.fortuneJsons().size());
        for (String json : payload.fortuneJsons()) {
            buf.writeUtf(json == null ? "" : json, 4096);
        }
    }

    private static OpenFairFortunePayload read(FriendlyByteBuf buf) {
        int count = Math.max(0, Math.min(8, buf.readVarInt()));
        List<String> fortuneJsons = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            fortuneJsons.add(buf.readUtf(4096));
        }
        return new OpenFairFortunePayload(List.copyOf(fortuneJsons));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFairFortunePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Client.handleClient(payload));
    }

    // Keep client bytecode and its compiler-generated lambdas out of the common payload.
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    private static final class Client {
        private Client() {}

        private static void handleClient(OpenFairFortunePayload payload) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                return;
            }
            StringBuilder raw = new StringBuilder();
            for (String json : payload.fortuneJsons()) {
                Component component = parseComponent(json, mc);
                if (!raw.isEmpty()) {
                    raw.append("#$b#");
                }
                raw.append('%').append(component.getString());
            }
            EventScreenFade.startFadeToBlack(20);
            Thread opener = new Thread(() -> {
                try {
                    Thread.sleep(380L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                mc.execute(() -> {
                    EventScreenFade.startFadeFromBlack(20);
                    StardewNpcDialogueScreen screen = new StardewNpcDialogueScreen("fortune_teller", raw.toString(), 0)
                        .withAfterClose(EventScreenFade::clear);
                    mc.setScreen(screen);
                });
            }, "StardewCraft-FairFortuneOpen");
            opener.setDaemon(true);
            opener.start();
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
