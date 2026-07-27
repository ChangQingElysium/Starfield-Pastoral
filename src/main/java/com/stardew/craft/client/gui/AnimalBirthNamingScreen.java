package com.stardew.craft.client.gui;

import com.stardew.craft.network.payload.AnimalBirthNamingSubmitPayload;
import com.stardew.craft.network.payload.OpenAnimalBirthNamingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.Random;

/** Blocking naming step matching the result of Stardew Valley's {@code QuestionEvent(2)}. */
@SuppressWarnings("null")
public final class AnimalBirthNamingScreen extends Screen {
    private final OpenAnimalBirthNamingPayload payload;
    private final Random random = new Random();
    private EditBox nameField;

    public AnimalBirthNamingScreen(OpenAnimalBirthNamingPayload payload) {
        super(Component.translatable(
                "stardewcraft.animal.pregnancy.naming_title",
                payload.animalTypeId()
        ));
        this.payload = payload;
    }

    @Override
    protected void init() {
        int panelWidth = 260;
        int left = (width - panelWidth) / 2;
        int top = height / 2 - 45;
        nameField = new EditBox(
                font,
                left,
                top + 34,
                panelWidth,
                20,
                Component.translatable(
                        "stardewcraft.animal.pregnancy.naming_hint")
        );
        nameField.setMaxLength(48);
        reroll();
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(
                Component.translatable(
                        "stardewcraft.animal.pregnancy.random_name"),
                button -> reroll()
        ).bounds(left, top + 62, 125, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> submit()
        ).bounds(left + 135, top + 62, 125, 20).build());
        setInitialFocus(nameField);
    }

    private void reroll() {
        String language = Minecraft.getInstance()
                .getLanguageManager()
                .getSelected();
        nameField.setValue(
                SdvAnimalNameGenerator.randomName(language, random));
    }

    private void submit() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(
                new AnimalBirthNamingSubmitPayload(payload.eventId(), name));
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // The source overnight event cannot be skipped; the persisted prompt survives reconnects.
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(
            @Nonnull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "stardewcraft.animal.pregnancy.birth_notification",
                        payload.parentName()
                ),
                width / 2,
                height / 2 - 48,
                0xFFFFFF
        );
        graphics.drawCenteredString(
                font,
                title,
                width / 2,
                height / 2 - 30,
                0xFFE7A5
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
