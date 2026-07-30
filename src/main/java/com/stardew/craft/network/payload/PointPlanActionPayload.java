package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.tool.PointPlanWandItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PointPlanActionPayload(
    String action,
    String planId,
    int pointIndex,
    String npcId,
    BlockPos pos,
    String direction
) implements CustomPacketPayload {
    public static final Type<PointPlanActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "point_plan_action"));

    public static final StreamCodec<FriendlyByteBuf, PointPlanActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.action());
            buf.writeUtf(payload.planId());
            buf.writeVarInt(payload.pointIndex());
            buf.writeUtf(payload.npcId());
            buf.writeBlockPos(payload.pos());
            buf.writeUtf(payload.direction());
        },
        buf -> new PointPlanActionPayload(
            buf.readUtf(),
            buf.readUtf(),
            buf.readVarInt(),
            buf.readUtf(),
            buf.readBlockPos(),
            buf.readUtf()
        )
    );

    public PointPlanActionPayload {
        action = action == null ? "" : action.trim();
        planId = planId == null ? "" : planId.trim();
        npcId = npcId == null ? "" : npcId.trim();
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        direction = direction == null ? "" : direction.trim();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PointPlanActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = heldEditor(player);
            if (stack.isEmpty()) {
                return;
            }
            switch (payload.action()) {
                case "select_plan" -> PointPlanWandItem.setSelectedPlanId(stack, payload.planId());
                case "create_plan" -> PointPlanWandItem.createPlan(stack, payload.planId());
                case "delete_plan" -> PointPlanWandItem.deletePlan(stack, payload.planId());
                case "clear_plan" -> PointPlanWandItem.clearPlan(stack, payload.planId());
                case "delete_point" -> PointPlanWandItem.deletePoint(stack, payload.planId(), payload.pointIndex());
                case "add_point" -> {
                    PointPlanWandItem.addPoint(stack, payload.planId(), new PointPlanWandItem.PointEntry(payload.npcId(), payload.pos(), payload.direction()));
                    PointPlanWandItem.sync(player, stack, PointPlanSyncPayload.OPEN_MAIN, null);
                    return;
                }
                default -> {
                    return;
                }
            }
            PointPlanWandItem.sync(player, stack, PointPlanSyncPayload.OPEN_NONE, null);
        });
    }

    private static ItemStack heldEditor(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof PointPlanWandItem) {
            return main;
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.getItem() instanceof PointPlanWandItem
                ? offhand : ItemStack.EMPTY;
    }
}
