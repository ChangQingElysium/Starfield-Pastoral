package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.CarpenterBlueprint;
import com.stardew.craft.shop.RobinService;
import com.stardew.craft.shop.WizardBuildingService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client → Server: player wants to purchase blueprint at blueprintIndex.
 */
@SuppressWarnings("null")
public record CarpenterPurchasePayload(
    String builder,
    int blueprintIndex
) implements CustomPacketPayload {

    public static final Type<CarpenterPurchasePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "carpenter_purchase"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CarpenterPurchasePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CarpenterPurchasePayload::builder,
            ByteBufCodecs.INT, CarpenterPurchasePayload::blueprintIndex,
            CarpenterPurchasePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CarpenterPurchasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            List<CarpenterBlueprint> blueprints;
            if (WizardBuildingService.BUILDER_ID.equals(payload.builder())) {
                if (!WizardBuildingService.canUse(player)) {
                    sendResult(player, false, PlayerStardewDataAPI.getMoney(player), "",
                            payload.blueprintIndex());
                    return;
                }
                blueprints = WizardBuildingService.getBlueprints(player);
            } else if ("Robin".equals(payload.builder())) {
                blueprints = RobinService.getBlueprints();
            } else {
                return;
            }
            if (payload.blueprintIndex() < 0 || payload.blueprintIndex() >= blueprints.size()) return;

            CarpenterBlueprint bp = blueprints.get(payload.blueprintIndex());

            // Check money
            int currentMoney = PlayerStardewDataAPI.getMoney(player);
            if (bp.cost() > 0 && currentMoney < bp.cost()) {
                sendResult(player, false, currentMoney, "", payload.blueprintIndex());
                return;
            }

            // Check materials
            for (CarpenterBlueprint.MaterialEntry mat : bp.materials()) {
                ResourceLocation matId;
                try {
                    matId = ResourceLocation.parse(mat.itemId());
                } catch (Exception e) {
                    sendResult(player, false, currentMoney, "", payload.blueprintIndex());
                    return;
                }
                Item matItem = BuiltInRegistries.ITEM.get(matId);
                if (matItem == null || matItem == Items.AIR) {
                    sendResult(player, false, currentMoney, "", payload.blueprintIndex());
                    return;
                }
                if (player.getInventory().countItem(matItem) < mat.count()) {
                    sendResult(player, false, currentMoney, "", payload.blueprintIndex());
                    return;
                }
            }

            // Validate result item
            ResourceLocation resultId;
            try {
                resultId = ResourceLocation.parse(bp.resultItemId());
            } catch (Exception e) {
                sendResult(player, false, currentMoney, "", payload.blueprintIndex());
                return;
            }
            Item resultItem = BuiltInRegistries.ITEM.get(resultId);
            if (resultItem == null || resultItem == Items.AIR) {
                sendResult(player, false, currentMoney, "", payload.blueprintIndex());
                return;
            }

            // Deduct money
            if (bp.cost() > 0) {
                boolean ok = PlayerStardewDataAPI.removeMoney(player, bp.cost());
                if (!ok) {
                    sendResult(player, false, PlayerStardewDataAPI.getMoney(player), "", payload.blueprintIndex());
                    return;
                }
            }

            // Consume materials
            for (CarpenterBlueprint.MaterialEntry mat : bp.materials()) {
                ResourceLocation matId = ResourceLocation.parse(mat.itemId());
                Item matItem = BuiltInRegistries.ITEM.get(matId);
                int remaining = mat.count();
                for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                    ItemStack slot = player.getInventory().getItem(i);
                    if (!slot.isEmpty() && slot.is(matItem)) {
                        int take = Math.min(remaining, slot.getCount());
                        slot.shrink(take);
                        remaining -= take;
                    }
                }
                if (remaining > 0) {
                    // Rollback money if material consumption failed
                    if (bp.cost() > 0) PlayerStardewDataAPI.addMoney(player, bp.cost());
                    sendResult(player, false, PlayerStardewDataAPI.getMoney(player), "", payload.blueprintIndex());
                    return;
                }
            }

            // Give the selected building item to the player.
            ItemStack resultStack = new ItemStack(resultItem);
            if (!player.getInventory().add(resultStack)) {
                // Drop on ground if inventory full
                player.drop(resultStack, false);
            }

            int newMoney = PlayerStardewDataAPI.getMoney(player);
            sendResult(player, true, newMoney, bp.resultItemId(), payload.blueprintIndex());
        });
    }

    private static void sendResult(ServerPlayer player, boolean success, int newMoney, String resultItemId, int blueprintIndex) {
        PacketDistributor.sendToPlayer(player,
            new CarpenterPurchaseResultPayload(success, newMoney, resultItemId, blueprintIndex));
    }
}
