package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.inventory.InventoryOrganizeService;
import com.stardew.craft.menu.StoneChestMenu;
import com.stardew.craft.menu.WoodenChestMenu;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record InventoryOrganizePayload(int target) implements CustomPacketPayload {
    public static final int TARGET_PLAYER_INVENTORY = 0;
    public static final int TARGET_OPEN_CONTAINER = 1;

    public static final Type<InventoryOrganizePayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "inventory_organize"));

    public static final StreamCodec<FriendlyByteBuf, InventoryOrganizePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.target),
            buf -> new InventoryOrganizePayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InventoryOrganizePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            if (payload.target == TARGET_PLAYER_INVENTORY) {
                InventoryOrganizeService.organizePlayerInventory(player);
                return;
            }

            if (payload.target != TARGET_OPEN_CONTAINER) {
                return;
            }

            if (!player.containerMenu.stillValid(player)) return;
            if (player.containerMenu instanceof WoodenChestMenu woodenChestMenu) {
                woodenChestMenu.organizeContainer();
            } else if (player.containerMenu instanceof StoneChestMenu stoneChestMenu) {
                stoneChestMenu.organizeContainer();
            }
        });
    }
}
