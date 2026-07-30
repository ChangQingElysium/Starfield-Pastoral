package com.stardew.craft.client.pointplan;

import com.stardew.craft.item.tool.PointPlanWandItem;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class PointPlanClientState {
    private static String selectedPlanId = "festival_of_ice_npcs";
    private static List<PointPlanWandItem.Plan> plans = List.of(new PointPlanWandItem.Plan(selectedPlanId, List.of()));
    private static List<String> npcIds = List.of();
    private static boolean mapInteractionEditor;
    private static PointPlanWandItem.PendingPoint pendingPoint;

    private PointPlanClientState() {
    }

    public static void replace(
        String newSelectedPlanId,
        List<PointPlanWandItem.Plan> newPlans,
        List<String> newNpcIds,
        boolean newMapInteractionEditor,
        PointPlanWandItem.PendingPoint newPendingPoint
    ) {
        selectedPlanId = newSelectedPlanId == null || newSelectedPlanId.isBlank() ? "festival_of_ice_npcs" : newSelectedPlanId;
        plans = List.copyOf(newPlans == null || newPlans.isEmpty()
            ? List.of(new PointPlanWandItem.Plan(selectedPlanId, List.of()))
            : newPlans);
        npcIds = List.copyOf(newNpcIds == null ? List.of() : newNpcIds);
        mapInteractionEditor = newMapInteractionEditor;
        pendingPoint = newPendingPoint;
    }

    public static String selectedPlanId() {
        return selectedPlanId;
    }

    public static List<PointPlanWandItem.Plan> plans() {
        return plans;
    }

    public static PointPlanWandItem.Plan selectedPlan() {
        for (PointPlanWandItem.Plan plan : plans) {
            if (plan.id().equals(selectedPlanId)) {
                return plan;
            }
        }
        return plans.isEmpty() ? new PointPlanWandItem.Plan(selectedPlanId, List.of()) : plans.getFirst();
    }

    public static List<String> npcIds() {
        return npcIds;
    }

    public static PointPlanWandItem.PendingPoint pendingPoint() {
        return pendingPoint;
    }

    public static boolean isMapInteractionEditor() {
        return mapInteractionEditor;
    }

    public static String exportSelected(String overridePlanId) {
        String id = overridePlanId == null || overridePlanId.isBlank() ? selectedPlan().id() : overridePlanId.trim();
        return exportPlan(id, selectedPlan().points());
    }

    public static String exportAll() {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        for (int i = 0; i < plans.size(); i++) {
            PointPlanWandItem.Plan plan = plans.get(i);
            out.append(indent(exportPlan(plan.id(), plan.points()), "  "));
            if (i + 1 < plans.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append('}');
        return out.toString();
    }

    private static String exportPlan(String planId, List<PointPlanWandItem.PointEntry> points) {
        StringBuilder out = new StringBuilder();
        out.append("\"").append(escape(planId)).append("\": [\n");
        List<PointPlanWandItem.PointEntry> copy = new ArrayList<>(points);
        for (int i = 0; i < copy.size(); i++) {
            PointPlanWandItem.PointEntry point = copy.get(i);
            BlockPos pos = point.pos();
            if (mapInteractionEditor) {
                out.append("  { \"name\": \"")
                    .append(escape(point.npcId()))
                    .append("\", \"x\": ").append(pos.getX())
                    .append(", \"y\": ").append(pos.getY())
                    .append(", \"z\": ").append(pos.getZ())
                    .append(" }");
            } else {
                out.append("  { \"npc\": \"")
                    .append(escape(point.npcId()))
                    .append("\", \"x\": ").append(pos.getX())
                    .append(", \"y\": ").append(pos.getY())
                    .append(", \"z\": ").append(pos.getZ())
                    .append(", \"direction\": \"")
                    .append(escape(point.direction())).append("\" }");
            }
            if (i + 1 < copy.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append(']');
        return out.toString();
    }

    private static String indent(String text, String prefix) {
        return prefix + text.replace("\n", "\n" + prefix);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
