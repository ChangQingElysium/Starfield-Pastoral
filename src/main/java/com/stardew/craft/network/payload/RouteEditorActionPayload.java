package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.tool.RouteEditorWandItem;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record RouteEditorActionPayload(String action, String routeId) implements CustomPacketPayload {
    public static final Type<RouteEditorActionPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "route_editor_action"));

    public static final StreamCodec<FriendlyByteBuf, RouteEditorActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.action());
            buf.writeUtf(payload.routeId());
        },
        buf -> new RouteEditorActionPayload(buf.readUtf(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RouteEditorActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = heldEditor(player);
            if (stack.isEmpty()) {
                return;
            }
            RouteEditorWandItem.setRouteId(stack, payload.routeId());
            switch (payload.action()) {
                case "undo" -> RouteEditorWandItem.undoLastPoint(stack);
                case "clear" -> RouteEditorWandItem.clearPoints(stack);
                case "set_route_id" -> {
                }
                default -> {
                    return;
                }
            }
            RouteEditorWandItem.sync(player, stack, false);
        });
    }

    private static ItemStack heldEditor(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.ROUTE_EDITOR_WAND.get())) {
            return main;
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(ModItems.ROUTE_EDITOR_WAND.get()) ? offhand : ItemStack.EMPTY;
    }
}
