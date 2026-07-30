package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.tool.PointPlanWandItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record PointPlanSyncPayload(
    String selectedPlanId,
    List<PointPlanWandItem.Plan> plans,
    List<String> npcIds,
    boolean mapInteractionEditor,
    String openMode,
    PointPlanWandItem.PendingPoint pendingPoint
) implements CustomPacketPayload {
    public static final String OPEN_NONE = "none";
    public static final String OPEN_MAIN = "main";
    public static final String OPEN_ADD_POINT = "add_point";

    public static final Type<PointPlanSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "point_plan_sync"));

    public static final StreamCodec<FriendlyByteBuf, PointPlanSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.selectedPlanId());
            buf.writeVarInt(payload.plans().size());
            for (PointPlanWandItem.Plan plan : payload.plans()) {
                buf.writeUtf(plan.id());
                buf.writeVarInt(plan.points().size());
                for (PointPlanWandItem.PointEntry point : plan.points()) {
                    buf.writeUtf(point.npcId());
                    buf.writeBlockPos(point.pos());
                    buf.writeUtf(point.direction());
                }
            }
            buf.writeVarInt(payload.npcIds().size());
            for (String npcId : payload.npcIds()) {
                buf.writeUtf(npcId);
            }
            buf.writeBoolean(payload.mapInteractionEditor());
            buf.writeUtf(payload.openMode());
            buf.writeBoolean(payload.pendingPoint() != null);
            if (payload.pendingPoint() != null) {
                buf.writeBlockPos(payload.pendingPoint().pos());
                buf.writeUtf(payload.pendingPoint().direction());
            }
        },
        buf -> {
            String selectedPlanId = buf.readUtf();
            int planCount = buf.readVarInt();
            List<PointPlanWandItem.Plan> plans = new ArrayList<>(planCount);
            for (int planIndex = 0; planIndex < planCount; planIndex++) {
                String planId = buf.readUtf();
                int pointCount = buf.readVarInt();
                List<PointPlanWandItem.PointEntry> points = new ArrayList<>(pointCount);
                for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                    points.add(new PointPlanWandItem.PointEntry(buf.readUtf(), buf.readBlockPos(), buf.readUtf()));
                }
                plans.add(new PointPlanWandItem.Plan(planId, points));
            }
            int npcCount = buf.readVarInt();
            List<String> npcIds = new ArrayList<>(npcCount);
            for (int i = 0; i < npcCount; i++) {
                npcIds.add(buf.readUtf());
            }
            boolean mapInteractionEditor = buf.readBoolean();
            String openMode = buf.readUtf();
            PointPlanWandItem.PendingPoint pendingPoint = null;
            if (buf.readBoolean()) {
                pendingPoint = new PointPlanWandItem.PendingPoint(buf.readBlockPos(), buf.readUtf());
            }
            return new PointPlanSyncPayload(
                selectedPlanId,
                plans,
                npcIds,
                mapInteractionEditor,
                openMode,
                pendingPoint
            );
        }
    );

    public PointPlanSyncPayload {
        plans = plans == null ? List.of() : List.copyOf(plans);
        npcIds = npcIds == null ? List.of() : List.copyOf(npcIds);
        openMode = openMode == null || openMode.isBlank() ? OPEN_NONE : openMode;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PointPlanSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(PointPlanSyncPayload payload) {
        com.stardew.craft.client.pointplan.PointPlanClientState.replace(
            payload.selectedPlanId(),
            payload.plans(),
            payload.npcIds(),
            payload.mapInteractionEditor(),
            payload.pendingPoint()
        );
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (OPEN_MAIN.equals(payload.openMode())) {
            minecraft.setScreen(new com.stardew.craft.client.gui.PointPlanScreen());
        } else if (OPEN_ADD_POINT.equals(payload.openMode())) {
            minecraft.setScreen(new com.stardew.craft.client.gui.PointPlanAddPointScreen());
        } else if (minecraft.screen instanceof com.stardew.craft.client.gui.PointPlanScreen screen) {
            screen.refreshFromState();
        }
    }
}
