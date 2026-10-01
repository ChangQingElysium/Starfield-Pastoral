package com.stardew.craft.client.gui;

import com.stardew.craft.client.animal.LivestockPortrait;
import com.stardew.craft.network.payload.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

public final class AnimalBirthNamingScreen extends FarmFolioScreen {
    private final OpenAnimalBirthNamingPayload source;
    private final CompoundTag portrait = new CompoundTag();
    private String value = "";
    private EditBox input;
    private Button confirm;

    public AnimalBirthNamingScreen(OpenAnimalBirthNamingPayload source) {
        super(ui("newborn"), null);
        this.source = source;
        com.stardew.craft.animal.runtime.LivestockUiData.describe(portrait, source.animalTypeId());
    }

    @Override
    protected int preferredWidth() {
        return 280;
    }

    @Override
    protected int preferredHeight() {
        return 305;
    }

    @Override
    protected void layout() {
        input =
                field(
                        Component.translatable("livestock.stardewcraft.name_required"),
                        value,
                        x + 24,
                        y + h - 116,
                        w - 90,
                        32);
        input.setResponder(
                s -> {
                    value = s;
                    if (confirm != null) confirm.active = FarmRenameScreen.valid(s);
                });
        tile(
                ui("random_name"),
                x + w - 56,
                y + h - 116,
                32,
                32,
                () -> input.setValue(FarmAnimalNames.next()),
                (g, b) -> {
                    box(g, "button", b.getX(), b.getY(), 32, 32);
                    glyph(g, "dice", b.getX() + 8, b.getY() + 8);
                });
        button(ui("later"), x + 16, y + h - 38, (w - 42) / 2, 26, this::onClose);
        confirm =
                button(
                        Component.translatable("gui.done"),
                        x + w / 2 + 5,
                        y + h - 38,
                        (w - 42) / 2,
                        26,
                        () -> {
                            PacketDistributor.sendToServer(
                                    new AnimalBirthNamingSubmitPayload(
                                            source.eventId(), value.strip()));
                            onClose();
                        });
        confirm.active = FarmRenameScreen.valid(value);
        setInitialFocus(input);
    }

    @Override
    protected void paint(GuiGraphics g) {
        paper(g, x + 16, y + 36, w - 32, h - 90);
        LivestockPortrait.draw(g, portrait, x + 40, y + 50, w - 80, h - 194, false);
        label(g, Component.literal(source.parentName()), x + 24, y + h - 160, w - 48, MUTED);
        label(
                g,
                Component.translatable("livestock.stardewcraft.name_required"),
                x + 24,
                y + h - 138,
                w - 48,
                INK);
    }
}
