package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.AnimalPurchaseBuildingScreen;
import com.stardew.craft.client.gui.common.StardewNpcDialogueScreen;
import com.stardew.craft.client.hud.StardewHudMessageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative completion for the SDV animal purchase flow. */
public record AnimalPurchaseResultPayload(boolean success, String animalName, String messageKey)
    implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<AnimalPurchaseResultPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "animal_purchase_result"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, AnimalPurchaseResultPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.success());
            buf.writeUtf(payload.animalName(), 128);
            buf.writeUtf(payload.messageKey(), 256);
        },
        buf -> new AnimalPurchaseResultPayload(buf.readBoolean(), buf.readUtf(128), buf.readUtf(256))
    );

    public static AnimalPurchaseResultPayload success(String animalName) {
        return new AnimalPurchaseResultPayload(true, animalName, "");
    }

    public static AnimalPurchaseResultPayload failure(String messageKey) {
        return new AnimalPurchaseResultPayload(false, "", messageKey);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnimalPurchaseResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(AnimalPurchaseResultPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.success()) {
            if (minecraft.screen instanceof AnimalPurchaseBuildingScreen screen) {
                screen.handlePurchaseSuccess(payload.animalName());
                return;
            }
            String dialogue = Component.translatable(
                "stardewcraft.animal.purchase.marnie_success", payload.animalName()).getString();
            minecraft.setScreen(new StardewNpcDialogueScreen("marnie", dialogue, 0));
            return;
        }
        String messageKey = payload.messageKey().isBlank()
            ? "stardewcraft.animal.purchase.failed" : payload.messageKey();
        if (minecraft.screen instanceof AnimalPurchaseBuildingScreen screen) {
            screen.handlePurchaseFailure(messageKey);
        } else {
            StardewHudMessageManager.showError(Component.translatable(messageKey));
        }
    }
}
