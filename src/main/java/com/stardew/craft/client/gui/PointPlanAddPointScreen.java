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
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PointPlanAddPointScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 172;
    private static final int SUGGESTION_ROWS = 6;
    private static final int ROW_H = 13;

    private EditBox npcBox;
    private int panelX;
    private int panelY;

    public PointPlanAddPointScreen() {
        super(Component.translatable("gui.stardewcraft.point_plan.add_title"));
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        npcBox = new EditBox(font, panelX + 70, panelY + 52, PANEL_W - 92, 18, Component.translatable("gui.stardewcraft.point_plan.npc"));
        npcBox.setTextShadow(false);
        npcBox.setMaxLength(64);
        addRenderableWidget(npcBox);
        setInitialFocus(npcBox);

        int buttonY = panelY + PANEL_H - 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.point_plan.add"), button -> confirm()
        ).bounds(panelX + PANEL_W - 150, buttonY, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stardewcraft.point_plan.cancel"), button ->
            minecraft.setScreen(new PointPlanScreen())
        ).bounds(panelX + PANEL_W - 80, buttonY, 68, 20).build());
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
        graphics.drawString(font, Component.translatable("gui.stardewcraft.point_plan.plan", PointPlanClientState.selectedPlanId()),
            panelX + 12, panelY + 30, 0xFF6B4B2C, false);
        graphics.drawString(font, Component.translatable("gui.stardewcraft.point_plan.npc"), panelX + 12, panelY + 57, 0xFF3A2014, false);

        PointPlanWandItem.PendingPoint pending = pendingPoint();
        BlockPos pos = pending.pos();
        graphics.drawString(font, Component.translatable("gui.stardewcraft.point_plan.pending",
            pos.getX(), pos.getY(), pos.getZ(), pending.direction()), panelX + 12, panelY + 80, 0xFF6B4B2C, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderSuggestions(graphics);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) {
            fillFirstSuggestion();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && clickSuggestion(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderSuggestions(GuiGraphics graphics) {
        List<String> suggestions = suggestions();
        if (suggestions.isEmpty() || !npcBox.isFocused()) {
            return;
        }
        int x = npcBox.getX();
        int y = npcBox.getY() + npcBox.getHeight() + 2;
        int w = npcBox.getWidth();
        int rows = Math.min(SUGGESTION_ROWS, suggestions.size());
        graphics.fill(x - 1, y - 1, x + w + 1, y + rows * ROW_H + 1, 0xFF5A4025);
        graphics.fill(x, y, x + w, y + rows * ROW_H, 0xFFF0D19B);
        for (int i = 0; i < rows; i++) {
            String npcId = suggestions.get(i);
            int rowY = y + i * ROW_H + 3;
            graphics.drawString(font, npcId + "  " + npcName(npcId), x + 4, rowY, 0xFF2A1A10, false);
        }
    }

    private boolean clickSuggestion(double mouseX, double mouseY) {
        List<String> suggestions = suggestions();
        if (suggestions.isEmpty()) {
            return false;
        }
        int x = npcBox.getX();
        int y = npcBox.getY() + npcBox.getHeight() + 2;
        int w = npcBox.getWidth();
        int rows = Math.min(SUGGESTION_ROWS, suggestions.size());
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + rows * ROW_H) {
            return false;
        }
        int index = ((int) mouseY - y) / ROW_H;
        npcBox.setValue(suggestions.get(index));
        npcBox.setCursorPosition(npcBox.getValue().length());
        return true;
    }

    private void fillFirstSuggestion() {
        List<String> suggestions = suggestions();
        if (!suggestions.isEmpty()) {
            npcBox.setValue(suggestions.getFirst());
            npcBox.setCursorPosition(npcBox.getValue().length());
        }
    }

    private void confirm() {
        String npcId = bestNpcId(npcBox.getValue());
        if (npcId.isBlank()) {
            return;
        }
        PointPlanWandItem.PendingPoint pending = pendingPoint();
        PacketDistributor.sendToServer(new PointPlanActionPayload(
            "add_point",
            PointPlanClientState.selectedPlanId(),
            -1,
            npcId,
            pending.pos(),
            pending.direction()
        ));
    }

    private List<String> suggestions() {
        String query = npcBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String npcId : PointPlanClientState.npcIds()) {
            String name = npcName(npcId).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || npcId.toLowerCase(Locale.ROOT).contains(query) || name.contains(query)) {
                result.add(npcId);
                if (result.size() >= SUGGESTION_ROWS) {
                    break;
                }
            }
        }
        return result;
    }

    private String bestNpcId(String raw) {
        String clean = raw == null ? "" : raw.trim();
        if (clean.isBlank()) {
            return "";
        }
        String lower = clean.toLowerCase(Locale.ROOT);
        for (String npcId : PointPlanClientState.npcIds()) {
            if (npcId.equalsIgnoreCase(clean) || npcName(npcId).equalsIgnoreCase(clean)) {
                return npcId;
            }
        }
        List<String> suggestions = suggestions();
        if (!suggestions.isEmpty()) {
            return suggestions.getFirst();
        }
        return lower.replaceAll("[^a-z0-9_./-]", "_");
    }

    private String npcName(String npcId) {
        return Component.translatable("entity.stardewcraft.npc." + npcId).getString();
    }

    private PointPlanWandItem.PendingPoint pendingPoint() {
        PointPlanWandItem.PendingPoint pending = PointPlanClientState.pendingPoint();
        return pending == null ? new PointPlanWandItem.PendingPoint(BlockPos.ZERO, "south") : pending;
    }
}
