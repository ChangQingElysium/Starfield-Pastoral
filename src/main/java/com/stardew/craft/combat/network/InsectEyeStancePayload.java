package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record InsectEyeStancePayload(boolean active, int durationTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<InsectEyeStancePayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "insect_eye_stance_state")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, InsectEyeStancePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        InsectEyeStancePayload::active,
        ByteBufCodecs.VAR_INT,
        InsectEyeStancePayload::durationTicks,
        InsectEyeStancePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InsectEyeStancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(InsectEyeStancePayload payload) {
        com.stardew.craft.client.weapon.ShadowInsectVisuals.ensureLevel();
        if (payload.active()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            long nowTick = mc.level != null ? mc.level.getGameTime() : 0L;
            com.stardew.craft.client.weapon.InsectEyeStanceClientState.start(nowTick, payload.durationTicks());
        } else {
            com.stardew.craft.client.weapon.InsectEyeStanceClientState.clear();
        }
    }
}