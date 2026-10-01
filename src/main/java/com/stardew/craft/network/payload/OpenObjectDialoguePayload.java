package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.chat.ComponentSerialization;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record OpenObjectDialoguePayload(
        List<Component> messages,
        String afterCloseItemId
) implements CustomPacketPayload {
    public static final Type<OpenObjectDialoguePayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "open_object_dialogue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenObjectDialoguePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.messages().size());
                for (Component message : payload.messages()) {
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, message);
                }
                buf.writeUtf(payload.afterCloseItemId(), 256);
            },
            buf -> {
                int count = buf.readVarInt();
                List<Component> messages = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    messages.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
                }
                return new OpenObjectDialoguePayload(
                        messages, buf.readUtf(256));
            });

    public OpenObjectDialoguePayload(List<Component> messages) {
        this(messages, "");
    }

    public OpenObjectDialoguePayload(Component message) {
        this(List.of(message), "");
    }

    public OpenObjectDialoguePayload(Component message, String afterCloseItemId) {
        this(List.of(message), afterCloseItemId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenObjectDialoguePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenObjectDialoguePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        var screen = new com.stardew.craft.client.gui.common
                .StardewObjectDialogueScreen(payload.messages());
        String afterItemId = payload.afterCloseItemId();
        if (afterItemId != null && !afterItemId.isEmpty()) {
            screen.withAfterClose(() -> {
                com.stardew.craft.client.hud.HoldUpItemHandler.play(afterItemId);
                try {
                    ResourceLocation id = new ResourceLocation(afterItemId);
                    net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        com.stardew.craft.client.hud.StardewHudMessageManager.showItemPickup(
                                new net.minecraft.world.item.ItemStack(item), 1, false);
                    }
                } catch (Exception ignored) {
                }
            });
        }
        mc.setScreen(screen);
    }
}
