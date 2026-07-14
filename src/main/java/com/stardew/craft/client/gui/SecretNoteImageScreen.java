package com.stardew.craft.client.gui;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.StardewRenderMapping;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** SDV LetterViewerMenu image-note branch: one 64x64 atlas cell rendered at 4x. */
@SuppressWarnings("null")
public final class SecretNoteImageScreen extends Screen {
    private static final ResourceLocation LETTER_BG = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/letter_bg.png");
    private static final ResourceLocation NOTE_IMAGES = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/secret_notes_images.png");
    private static final int ATLAS_SIZE = 256;
    private static final int CELL_SIZE = 64;

    private final int noteNumber;
    private final int imageIndex;
    private StardewRenderMapping mapping;
    private int letterX;
    private int letterY;
    private int letterW;
    private int letterH;
    private int closeX;
    private int closeY;
    private int closeSize;
    private float scale;

    public SecretNoteImageScreen(int noteNumber, int imageIndex) {
        super(Component.translatable("stardewcraft.secret_note.title", noteNumber));
        this.noteNumber = noteNumber;
        this.imageIndex = imageIndex;
    }

    @Override
    protected void init() {
        mapping = new StardewRenderMapping(width, height, (float) minecraft.getWindow().getGuiScale());
        letterW = mapping.ui(1280);
        letterH = mapping.ui(720);
        letterX = mapping.centerX(letterW);
        letterY = (height - letterH) / 2;
        closeSize = mapping.ui(48);
        closeX = letterX + letterW - mapping.ui(36);
        closeY = letterY - mapping.ui(8);
        scale = 0.0F;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        scale = Math.min(1.0F, scale + partialTick * 0.18F);

        int cx = letterX + letterW / 2;
        int cy = letterY + letterH / 2;
        float paperScale = mapping.s4() * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(paperScale, paperScale, 1.0F);
        graphics.blit(LETTER_BG, -160, -90, 320, 0, 320, 180, 1280, 512);
        graphics.pose().popPose();

        if (scale >= 1.0F) {
            int sourceX = Math.floorMod(imageIndex, ATLAS_SIZE / CELL_SIZE) * CELL_SIZE;
            int sourceY = Math.floorDiv(imageIndex, ATLAS_SIZE / CELL_SIZE) * CELL_SIZE;
            float imageScale = mapping.s4();
            graphics.pose().pushPose();
            graphics.pose().translate(cx - CELL_SIZE * imageScale / 2.0F,
                    cy - CELL_SIZE * imageScale / 2.0F, 10.0F);
            graphics.pose().scale(imageScale, imageScale, 1.0F);
            graphics.blit(NOTE_IMAGES, 0, 0, sourceX, sourceY,
                    CELL_SIZE, CELL_SIZE, ATLAS_SIZE, ATLAS_SIZE);
            graphics.pose().popPose();
            CommonGuiTextures.drawCloseButton(graphics, closeX, closeY, mapping.s4());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= closeX && mouseX < closeX + closeSize
                && mouseY >= closeY && mouseY < closeY + closeSize) {
            minecraft.setScreen(null);
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    ModSounds.BIG_DESELECT.get(), 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
