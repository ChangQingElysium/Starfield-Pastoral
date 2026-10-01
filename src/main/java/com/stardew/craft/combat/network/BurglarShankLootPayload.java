package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record BurglarShankLootPayload() implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<BurglarShankLootPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "burglar_shank_loot")
    );

    public static final StreamCodec<ByteBuf, BurglarShankLootPayload> STREAM_CODEC = StreamCodec.unit(new BurglarShankLootPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BurglarShankLootPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(BurglarShankLootPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            com.stardew.craft.client.weapon.NeedleBurglarVisuals.lootConfirmed();
            com.stardew.craft.client.hud.StardewTimeHud.triggerMoneyShake();
        }
    }
}
