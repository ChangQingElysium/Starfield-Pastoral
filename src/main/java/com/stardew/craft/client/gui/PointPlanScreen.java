package com.stardew.craft.client.gui;

import com.stardew.craft.client.pointplan.PointPlanClientState;
import com.stardew.craft.item.tool.PointPlanWandItem;
import com.stardew.craft.network.payload.PointPlanActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.List;

public class PointPlanScreen extends Screen {
    private static final int PANEL_W = 520;
    private static final int PANEL_H = 276;
    private static final int ROW_H = 14;

    private EditBox planIdBox;
    private int panelX;
    private int panelY;
    private int scroll;
    private int selectedPoint = -1;

    public PointPlanScreen() {
        super(Component.translatable(
            PointPlanClientState.isMapInteractionEditor()
                ? "gui.stardewcraft.point_plan.map_title"
                : "gui.stardewcraft.point_plan.title"));
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        planIdBox = new EditBox(font, panelX + 70, panelY + 27, 220, 18, Component.translatable("gui.stardewcraft.point_plan.plan_id"));
        planIdBox.setTextShadow(false);
        planIdBox.setMaxLength(96);
        planIdBox.setValue(PointPlanClientState.selectedPlanId());
        addRenderableWidget(planIdBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.point_plan.select"), button ->
            send("select_plan", planIdBox.getValue(), -1)
        ).bounds(panelX + 296, panelY + 26, 58, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.point_plan.new"), button ->
            send("create_plan", planIdBox.getValue(), -1)
        ).bounds(panelX + 360, panelY + 26, 50, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.point_plan.delete_plan"), button ->
            send("delete_plan", planIdBox.getValue(), -1)
        ).bounds(panelX + 416, panelY + 26, 92, 20).build());

        int buttonY = panelY + PANEL_H - 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.point_plan.delete_point"), button ->
            send("delete_point", PointPlanClientState.selectedPlanId(), selectedPoint)
        ).bounds(panelX + 12, buttonY, 84, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.point_plan.clear"), button ->
            send("clear_plan", PointPlanClientState.selectedPlanId(), -1)
        ).bounds(panelX + 102, buttonY, 58, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.point_plan.copy"), button -> copySelected()
        ).bounds(panelX + 166, buttonY, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.point_plan.copy_all"), button -> copyAll()
        ).bounds(panelX + 250, buttonY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.point_plan.done"), button -> onClose()
        ).bounds(panelX + PANEL_W - 78, buttonY, 66, 20).build());
    }

    public void refreshFromState() {
        if (planIdBox != null && !planIdBox.isFocused()) {
            planIdBox.setValue(PointPlanClientState.selectedPlanId());
        }
        selectedPoint = Mth.clamp(selectedPoint, -1, PointPlanClientState.selectedPlan().points().size() - 1);
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + PANEL_H + 1, 0xFF5A4025);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFFF6D8A8);
        graphics.drawString(font, title, panelX + 12, panelY + 10, 0xFF3A2014, false);
        graphics.drawString(font, Component.translatable("gui.stardewcraft.point_plan.plan_id"), panelX + 12, panelY + 32, 0xFF3A2014, false);

        PointPlanWandItem.Plan plan = PointPlanClientState.selectedPlan();
        graphics.drawString(font, Component.translatable("gui.stardewcraft.point_plan.count", plan.points().size()),
            panelX + 12, panelY + 54, 0xFF6B4B2C, false);
        graphics.drawString(font, Component.translatable("gui.stardewcraft.point_plan.selected", plan.id()),
            panelX + 170, panelY + 54, 0xFF6B4B2C, false);
        renderPointList(graphics, plan.points());
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 3, 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && selectRow(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean selectRow(double mouseX, double mouseY) {
        int listX = panelX + 12;
        int listY = panelY + 72;
        int listW = PANEL_W - 24;
        int listH = PANEL_H - 112;
        if (mouseX < listX || mouseX >= listX + listW || mouseY < listY + ROW_H || mouseY >= listY + listH) {
            return false;
        }
        int row = ((int) mouseY - listY - ROW_H) / ROW_H;
        int index = scroll + row;
        if (index >= 0 && index < PointPlanClientState.selectedPlan().points().size()) {
            selectedPoint = index;
            return true;
        }
        return false;
    }

    private void renderPointList(GuiGraphics graphics, List<PointPlanWandItem.PointEntry> points) {
        int listX = panelX + 12;
        int listY = panelY + 72;
        int listW = PANEL_W - 24;
        int listH = PANEL_H - 112;
        graphics.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0xFF8B6136);
        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFFEEC78E);
        graphics.drawString(font, "#", listX + 6, listY + 3, 0xFF3A2014, false);
        graphics.drawString(
            font,
            Component.translatable(
                PointPlanClientState.isMapInteractionEditor()
                    ? "gui.stardewcraft.point_plan.name"
                    : "gui.stardewcraft.point_plan.npc"),
            listX + 42, listY + 3, 0xFF3A2014, false);
        int coordinateX = PointPlanClientState.isMapInteractionEditor()
            ? 310 : 188;
        graphics.drawString(font, "x y z", listX + coordinateX, listY + 3, 0xFF3A2014, false);
        if (!PointPlanClientState.isMapInteractionEditor()) {
            graphics.drawString(font, "direction", listX + 330, listY + 3, 0xFF3A2014, false);
        }

        int rows = (listH - ROW_H) / ROW_H;
        int end = Math.min(points.size(), scroll + rows);
        for (int i = scroll; i < end; i++) {
            PointPlanWandItem.PointEntry point = points.get(i);
            BlockPos pos = point.pos();
            int y = listY + ROW_H + (i - scroll) * ROW_H + 3;
            if (i == selectedPoint) {
                graphics.fill(listX + 1, y - 2, listX + listW - 1, y + ROW_H - 2, 0x55FFFFFF);
            }
            graphics.drawString(font, String.format("%03d", i + 1), listX + 6, y, 0xFF2A1A10, false);
            graphics.drawString(font, point.npcId(), listX + 42, y, 0xFF2A1A10, false);
            graphics.drawString(font, String.format("%d %d %d", pos.getX(), pos.getY(), pos.getZ()), listX + coordinateX, y, 0xFF2A1A10, false);
            if (!PointPlanClientState.isMapInteractionEditor()) {
                graphics.drawString(font, point.direction(), listX + 330, y, 0xFF2A1A10, false);
            }
        }
        if (points.isEmpty()) {
            graphics.drawString(
                font,
                Component.translatable(
                    PointPlanClientState.isMapInteractionEditor()
                        ? "gui.stardewcraft.point_plan.map_empty"
                        : "gui.stardewcraft.point_plan.empty"),
                listX + 6, listY + ROW_H + 6, 0xFF7A5A35, false);
        }
    }

    private int maxScroll() {
        int visibleRows = (PANEL_H - 112 - ROW_H) / ROW_H;
        return Math.max(0, PointPlanClientState.selectedPlan().points().size() - visibleRows);
    }

    private void send(String action, String planId, int pointIndex) {
        PacketDistributor.sendToServer(new PointPlanActionPayload(action, planId, pointIndex, "", BlockPos.ZERO, ""));
    }

    private void copySelected() {
        if (minecraft == null) {
            return;
        }
        minecraft.keyboardHandler.setClipboard(PointPlanClientState.exportSelected(planIdBox.getValue()));
        planIdBox.setFocused(false);
    }

    private void copyAll() {
        if (minecraft == null) {
            return;
        }
        minecraft.keyboardHandler.setClipboard(PointPlanClientState.exportAll());
        planIdBox.setFocused(false);
    }
}
