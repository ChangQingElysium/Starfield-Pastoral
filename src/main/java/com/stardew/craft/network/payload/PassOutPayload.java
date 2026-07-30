package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PassOutService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * S→C: 晕倒/战斗死亡通知。
 * 客户端收到后播放渐黑过渡 + 显示惩罚摘要。
 */
@SuppressWarnings("null")
public record PassOutPayload(
        long transactionId,
        PassOutService.PassOutType passOutType,
        int moneyLost,
        List<ItemStack> lostItems
) implements CustomPacketPayload {

    public PassOutPayload(
            PassOutService.PassOutType passOutType,
            int moneyLost,
            List<ItemStack> lostItems
    ) {
        this(0L, passOutType, moneyLost, lostItems);
    }

    public static final Type<PassOutPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "pass_out"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PassOutPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, PassOutPayload::transactionId,
                    ByteBufCodecs.VAR_INT.map(PassOutService.PassOutType::fromId, PassOutService.PassOutType::getId),
                    PassOutPayload::passOutType,
                    ByteBufCodecs.VAR_INT, PassOutPayload::moneyLost,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), PassOutPayload::lostItems,
                    PassOutPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PassOutPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(PassOutPayload payload) {
        com.stardew.craft.client.gui.overnight.PassOutOverlayScreen.show(payload);
    }
}
