package com.stardew.craft.client.gui;

import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.client.gui.common.ChestColorWheel;
import com.stardew.craft.client.gui.common.FurnitureModelPreview;
import com.stardew.craft.entity.seat.CushionEntity;
import com.stardew.craft.network.payload.ApplySofaColorPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/** Furniture has no container page; its existing paintbrush host shares the same lightweight wheel. */
@OnlyIn(Dist.CLIENT)
public class SofaColorSelectionScreen extends Screen {
    private final BlockPos targetPos;
    private final int targetEntityId;
    private final int initialColor;
    private final ChestColorWheel wheel;
    private boolean opened;
    private FurnitureModelPreview preview;

    public SofaColorSelectionScreen(BlockPos targetPos, int currentColor) {
        this(targetPos, currentColor, -1);
    }

    public SofaColorSelectionScreen(BlockPos targetPos, int currentColor, int targetEntityId) {
        super(Component.translatable("stardewcraft.furniture.color_picker"));
        this.targetPos = targetPos;
        this.targetEntityId = targetEntityId;
        int color = WoodenChestColorPalette.clampIndex(currentColor);
        this.initialColor = color < 0 ? WoodenChestColorPalette.defaultColorIndex() : color;
        this.wheel = new ChestColorWheel(ChestColorWheel.chestOptions(), selection ->
                PacketDistributor.sendToServer(new ApplySofaColorPayload(targetPos,
                        selection < 0 && (preview == null || !preview.hasAuthoredOriginal())
                                ? WoodenChestColorPalette.defaultColorIndex() : selection, targetEntityId)), this::drawPreview);
    }

    @Override protected void init() {
        wheel.center(width, height);
        if (preview == null && minecraft != null && minecraft.level != null) {
            if (targetEntityId >= 0) {
                if (minecraft.level.getEntity(targetEntityId) instanceof CushionEntity cushion)
                    preview = new FurnitureModelPreview(cushion.getRenderState());
            } else preview = new FurnitureModelPreview(minecraft.level.getBlockState(targetPos));
        }
        if (!opened) {
            wheel.open(preview == null ? initialColor : preview.initialColor(initialColor));
            opened = true;
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if (!wheel.active() || minecraft == null || minecraft.player == null || minecraft.level == null) onClose();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        wheel.render(graphics, mouseX, mouseY);
    }

    private void drawPreview(GuiGraphics graphics, int x, int y, int selection) {
        if (preview != null) preview.draw(graphics, x, y, selection);
    }

    @Override public boolean mouseClicked(double x, double y, int button) { return wheel.click(x, y, button, false); }
    @Override public boolean mouseReleased(double x, double y, int button) { return wheel.release(button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return true; }
    @Override public boolean mouseScrolled(double x, double y, double vertical) { return wheel.scroll(vertical); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return wheel.key(key); }
    @Override public boolean keyReleased(int key, int scan, int modifiers) { return wheel.keyReleased(key); }
    @Override public boolean charTyped(char character, int modifiers) { return true; }
}
