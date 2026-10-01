package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PassOutService;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/** S2C outcome shown after a combat rescue event has finished. */
public record CombatRescueOutcomePayload(
        long transactionId,
        PassOutService.PassOutType passOutType,
        int moneyLost,
        List<ItemStack> lostItems
) implements CustomPacketPayload {
    public static final Type<CombatRescueOutcomePayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "combat_rescue_outcome"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatRescueOutcomePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, CombatRescueOutcomePayload::transactionId,
                    ByteBufCodecs.VAR_INT.map(
                            PassOutService.PassOutType::fromId,
                            PassOutService.PassOutType::getId),
                    CombatRescueOutcomePayload::passOutType,
                    ByteBufCodecs.VAR_INT, CombatRescueOutcomePayload::moneyLost,
                    com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.apply(ByteBufCodecs.list()),
                    CombatRescueOutcomePayload::lostItems,
                    CombatRescueOutcomePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CombatRescueOutcomePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(CombatRescueOutcomePayload payload) {
        com.stardew.craft.client.combat.CombatCollapseClientState.outcomeReady();
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.stardew.craft.client.gui.overnight.PassOutSummaryScreen(
                        new PassOutPayload(
                                payload.transactionId(),
                                payload.passOutType(),
                                payload.moneyLost(),
                                payload.lostItems()
                        )
                )
        );
        PacketDistributor.sendToServer(
                new CombatRescueOutcomeAckPayload(payload.transactionId()));
    }
}
