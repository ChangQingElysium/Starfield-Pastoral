package com.stardew.craft.client.gui;

import com.stardew.craft.client.animal.LivestockPortrait;
import com.stardew.craft.network.payload.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

public final class AnimalPurchaseBuildingScreen extends FarmFolioScreen {
    private final OpenAnimalPurchaseScreenPayload source;
    private final OpenAnimalPurchaseScreenPayload.AnimalOption animal;
    private CompoundTag home;
    private String value = "", error = "";
    private EditBox input;
    private Button confirm;
    private boolean pending;

    public AnimalPurchaseBuildingScreen(
            OpenAnimalPurchaseScreenPayload source,
            OpenAnimalPurchaseScreenPayload.AnimalOption animal) {
        this(
                null,
                source,
                animal,
                AnimalPurchaseScreen.homes(source, animal).stream()
                        .findFirst()
                        .orElse(new CompoundTag()));
    }

    public AnimalPurchaseBuildingScreen(
            Screen parent,
            OpenAnimalPurchaseScreenPayload source,
            OpenAnimalPurchaseScreenPayload.AnimalOption animal,
            CompoundTag home) {
        super(ui(source.incubatorMode() ? "newborn" : "purchase_name"), parent);
        this.source = source;
        this.animal = animal;
        this.home = home;
    }

    @Override
    protected int preferredWidth() {
        return source.incubatorMode() ? 280 : 430;
    }

    @Override
    protected int preferredHeight() {
        return 300;
    }

    @Override
    protected void layout() {
        input =
                field(
                        Component.translatable("livestock.stardewcraft.name_required"),
                        value,
                        x + 28,
                        y + h - 128,
                        w - 104,
                        32);
        input.setResponder(
                s -> {
                    value = s;
                    update();
                });
        tile(
                ui("random_name"),
                x + w - 64,
                y + h - 128,
                32,
                32,
                () -> input.setValue(FarmAnimalNames.next()),
                (g, b) -> {
                    box(g, "button", b.getX(), b.getY(), 32, 32);
                    glyph(g, "dice", b.getX() + 8, b.getY() + 8);
                });
        button(
                BuildingChoiceScreen.homeName(home),
                x + 28,
                y + h - 86,
                w - 56,
                26,
                "tab",
                "home",
                () ->
                        minecraft.setScreen(
                                new BuildingChoiceScreen(
                                        this,
                                        AnimalPurchaseScreen.homes(source, animal),
                                        AnimalPurchaseScreen.describe(animal),
                                        r -> {
                                            home = r;
                                            init();
                                        })));
        button(Component.translatable("gui.back"), x + 16, y + h - 34, 96, 24, this::onClose);
        confirm =
                button(
                        source.incubatorMode()
                                ? Component.translatable("gui.done")
                                : ui("purchase"),
                        x + w - 150,
                        y + h - 34,
                        134,
                        24,
                        () -> {
                            pending = true;
                            update();
                            if (source.incubatorMode()) {
                                PacketDistributor.sendToServer(
                                        new IncubatorClaimSubmitPayload(
                                                BlockPos.of(source.contextBlockPos()), value));
                                onClose();
                            } else
                                PacketDistributor.sendToServer(
                                        new AnimalPurchaseSubmitPayload(
                                                animal.animalTypeId(),
                                                home.getString("StableId"),
                                                value));
                        });
        update();
        setInitialFocus(input);
    }

    private void update() {
        if (confirm != null)
            confirm.active =
                    !pending
                            && FarmRenameScreen.valid(value)
                            && home.getInt("Used") < home.getInt("Capacity");
    }

    public void handlePurchaseFailure(String key) {
        pending = false;
        error = key;
        update();
    }

    public void handlePurchaseSuccess(String name) {
        minecraft.setScreen(
                new com.stardew.craft.client.gui.common.StardewNpcDialogueScreen(
                        "marnie",
                        Component.translatable("stardewcraft.animal.purchase.marnie_success", name)
                                .getString(),
                        0));
    }

    @Override
    protected void paint(GuiGraphics g) {
        paper(g, x + 16, y + 36, w - 32, h - 80);
        LivestockPortrait.draw(
                g,
                AnimalPurchaseScreen.describe(animal),
                x + 28,
                y + 48,
                100,
                Math.max(30, h - 202),
                false);
        label(g, Component.literal(animal.displayName()), x + 146, y + 56, w - 174, INK);
        if (!source.incubatorMode()) {
            money(g, animal.price(), x + 146, y + 82, INK);
            money(g, source.playerMoney(), x + w - 126, y + 8, LIGHT);
        }
        if (!error.isEmpty())
            label(g, Component.translatable(error), x + 28, y + h - 148, w - 56, RED);
    }
}
