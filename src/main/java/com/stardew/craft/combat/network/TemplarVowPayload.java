package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record TemplarVowPayload(boolean active, int durationTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<TemplarVowPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "templar_vow")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, TemplarVowPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        TemplarVowPayload::active,
        ByteBufCodecs.VAR_INT,
        TemplarVowPayload::durationTicks,
        TemplarVowPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TemplarVowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(TemplarVowPayload payload) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        long nowTick = mc.level.getGameTime();
        if (payload.active()) {
            com.stardew.craft.client.weapon.TemplarVowClientState.start(nowTick, payload.durationTicks());
        } else {
            com.stardew.craft.client.weapon.TemplarVowClientState.clear();
            com.stardew.craft.client.weapon.WeaponSkillAnimationClient.stop();
        }
    }
}
