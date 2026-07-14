package com.stardew.craft.client.gui;

import com.stardew.craft.client.route.RouteEditorClientState;
import com.stardew.craft.network.payload.RouteEditorActionPayload;
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

public class RouteEditorScreen extends Screen {
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 236;
    private static final int ROW_H = 12;

    private EditBox routeIdBox;
    private int panelX;
    private int panelY;
    private int scroll;

    public RouteEditorScreen() {
        super(Component.translatable("gui.stardewcraft.route_editor.title"));
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        routeIdBox = new EditBox(font, panelX + 74, panelY + 28, PANEL_W - 92, 18, Component.translatable("gui.stardewcraft.route_editor.route_id"));
        routeIdBox.setTextShadow(false);
        routeIdBox.setMaxLength(96);
        routeIdBox.setValue(RouteEditorClientState.routeId());
        addRenderableWidget(routeIdBox);

        int buttonY = panelY + PANEL_H - 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.route_editor.set_id"), button ->
            PacketDistributor.sendToServer(new RouteEditorActionPayload("set_route_id", routeIdBox.getValue()))
        ).bounds(panelX + 12, buttonY, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.route_editor.undo"), button ->
            PacketDistributor.sendToServer(new RouteEditorActionPayload("undo", routeIdBox.getValue()))
        ).bounds(panelX + 82, buttonY, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.route_editor.clear"), button ->
            PacketDistributor.sendToServer(new RouteEditorActionPayload("clear", routeIdBox.getValue()))
        ).bounds(panelX + 152, buttonY, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.route_editor.copy"), button -> copyExport()
        ).bounds(panelX + 222, buttonY, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.route_editor.done"), button -> onClose()
        ).bounds(panelX + PANEL_W - 78, buttonY, 66, 20).build());
    }

    public void refreshFromState() {
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
        graphics.drawString(font, Component.translatable("gui.stardewcraft.route_editor.route_id"), panelX + 12, panelY + 33, 0xFF3A2014, false);

        List<BlockPos> points = RouteEditorClientState.points();
        String count = Component.translatable("gui.stardewcraft.route_editor.count", points.size()).getString();
        graphics.drawString(font, count, panelX + 12, panelY + 54, 0xFF6B4B2C, false);
        renderPointList(graphics, points);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 3, 0, maxScroll());
        return true;
    }

    private void renderPointList(GuiGraphics graphics, List<BlockPos> points) {
        int listX = panelX + 12;
        int listY = panelY + 68;
        int listW = PANEL_W - 24;
        int listH = PANEL_H - 108;
        graphics.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0xFF8B6136);
        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFFEEC78E);

        int rows = listH / ROW_H;
        int start = scroll;
        int end = Math.min(points.size(), start + rows);
        for (int i = start; i < end; i++) {
            BlockPos p = points.get(i);
            int y = listY + (i - start) * ROW_H + 2;
            String line = String.format("%03d  %d %d %d", i + 1, p.getX(), p.getY(), p.getZ());
            graphics.drawString(font, line, listX + 6, y, 0xFF2A1A10, false);
        }
        if (points.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.stardewcraft.route_editor.empty"), listX + 6, listY + 8, 0xFF7A5A35, false);
        }
    }

    private int maxScroll() {
        int visibleRows = (PANEL_H - 108) / ROW_H;
        return Math.max(0, RouteEditorClientState.points().size() - visibleRows);
    }

    private void copyExport() {
        if (minecraft == null) {
            return;
        }
        minecraft.keyboardHandler.setClipboard(RouteEditorClientState.exportJsonLike(routeIdBox.getValue()));
        routeIdBox.setFocused(false);
    }
}
