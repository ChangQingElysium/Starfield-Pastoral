package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.StardewNpcDialogueScreen;
import com.stardew.craft.cutscene.runtime.EventScreenFade;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record OpenFairFortunePayload(List<String> fortuneJsons) implements CustomPacketPayload {
    public static final Type<OpenFairFortunePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_fair_fortune"));

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
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
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

    @OnlyIn(Dist.CLIENT)
    private static Component parseComponent(String json, net.minecraft.client.Minecraft mc) {
        try {
            Component component = Component.Serializer.fromJson(json, mc.level.registryAccess());
            return component == null ? Component.literal(json) : component;
        } catch (Exception ignored) {
            return Component.literal(json == null ? "" : json);
        }
    }
}
