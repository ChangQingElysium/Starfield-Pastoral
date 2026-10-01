package com.stardew.craft.client.gui;

import com.stardew.craft.network.payload.DesertFestivalRaceActionPayload;
import com.stardew.craft.network.payload.DesertFestivalRaceSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class DesertFestivalRaceSingleBetScreen extends Screen implements DesertFestivalRaceSnapshotScreen {
    private DesertFestivalRaceSnapshot snapshot;
    private final List<DesertFestivalRaceUi.ButtonHitbox> hitboxes = new ArrayList<>();
    private int selectedRacer = -1;

    public DesertFestivalRaceSingleBetScreen(DesertFestivalRaceSnapshot snapshot) {
        super(Component.translatable("stardewcraft.desert_festival.race.mode_single"));
        this.snapshot = snapshot;
        this.selectedRacer = snapshot.nextGuess() >= 0 ? snapshot.nextGuess()
            : snapshot.racers().isEmpty() ? -1 : snapshot.racers().get(0).racerIndex();
    }

    @Override
    public void updateSnapshot(DesertFestivalRaceSnapshot snapshot) {
        this.snapshot = snapshot;
        if (snapshot.nextGuess() >= 0) {
            selectedRacer = snapshot.nextGuess();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        DesertFestivalRaceUi.backdrop(graphics, width, height);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hitboxes.clear();
        super.render(graphics, mouseX, mouseY, partialTick);
        DesertFestivalRaceUi.Layout layout = DesertFestivalRaceUi.layout(width, height, 470, 226);
        DesertFestivalRaceUi.panel(graphics, layout.x(), layout.y(), layout.w(), layout.h());
        DesertFestivalRaceUi.menuTitle(graphics, font, Component.translatable("stardewcraft.desert_festival.race.mode_single"), layout.x(), layout.y(), layout.w());

        int racerCount = Math.max(1, snapshot.racers().size());
        int cardW = 406 / racerCount;
        int cardX = 32;
        for (DesertFestivalRaceSnapshot.RacerEntry racer : snapshot.racers()) {
            renderRacerCard(graphics, mouseX, mouseY, layout, cardX, 52, cardW - 8, 90, racer);
            cardX += cardW;
        }

        boolean canConfirm = selectedRacer >= 0 && snapshot.nextGuess() < 0 && snapshot.canGuess() && !DesertFestivalRaceUi.raceInProgress(snapshot);
        addButton(graphics, mouseX, mouseY, layout, 32, 160, 406, 28, "guess", Component.translatable("stardewcraft.desert_festival.race.confirm_guess"), canConfirm);
        addButton(graphics, mouseX, mouseY, layout, 32, 194, 406, 24, "back", Component.translatable("gui.back"), true);
    }

    private void renderRacerCard(GuiGraphics graphics, int mouseX, int mouseY, DesertFestivalRaceUi.Layout layout,
                                 int x, int y, int w, int h, DesertFestivalRaceSnapshot.RacerEntry racer) {
        boolean selected = racer.racerIndex() == selectedRacer;
        DesertFestivalRaceUi.ButtonHitbox hitbox = DesertFestivalRaceUi.hitbox(layout, x, y, w, h, "select", racer.racerIndex(), 0, "");
        DesertFestivalRaceUi.menuRow(graphics, font, mouseX, mouseY,
            hitbox, Component.empty(), true, selected);
        int iconSize = Math.min(layout.w(48), Math.max(16, hitbox.w() - layout.w(30)));
        DesertFestivalRaceUi.drawRacerIconSize(graphics, racer.racerIndex(), hitbox.x() + hitbox.w() / 2 - iconSize / 2, hitbox.y() + layout.h(10), iconSize);
        Component name = DesertFestivalRaceUi.racerName(racer.racerIndex());
        DesertFestivalRaceUi.drawCentered(graphics, font, name, hitbox.x() + layout.w(8), hitbox.y() + layout.h(62), hitbox.w() - layout.w(16), layout.h(14), DesertFestivalRaceUi.DARK_TEXT, false);
        if (snapshot.nextGuess() == racer.racerIndex()) {
            DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.selected_badge"), hitbox.x() + layout.w(8), hitbox.y() + layout.h(8), hitbox.w() - layout.w(16), layout.h(12), DesertFestivalRaceUi.GOLD, false, 0.65f);
        }
        hitboxes.add(hitbox);
    }

    private void addButton(GuiGraphics graphics, int mouseX, int mouseY, DesertFestivalRaceUi.Layout layout,
                           int x, int y, int w, int h, String action, Component label, boolean enabled) {
        DesertFestivalRaceUi.ButtonHitbox hitbox = DesertFestivalRaceUi.hitbox(layout, x, y, w, h, action, selectedRacer, 0, "");
        DesertFestivalRaceUi.button(graphics, font, mouseX, mouseY, hitbox, label, enabled);
        if (enabled) hitboxes.add(hitbox);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        for (DesertFestivalRaceUi.ButtonHitbox hitbox : hitboxes) {
            if (!hitbox.contains((int) mouseX, (int) mouseY)) continue;
            switch (hitbox.action()) {
                case "select" -> {
                    DesertFestivalRaceUi.playButton();
                    selectedRacer = hitbox.racerIndex();
                }
                case "guess" -> {
                    DesertFestivalRaceUi.play(com.stardew.craft.sound.ModSounds.COIN);
                    PacketDistributor.sendToServer(new DesertFestivalRaceActionPayload("guess", selectedRacer, 0, ""));
                    Minecraft.getInstance().setScreen(new DesertFestivalRaceHubScreen(snapshot));
                }
                case "back" -> {
                    DesertFestivalRaceUi.playBack();
                    Minecraft.getInstance().setScreen(new DesertFestivalRaceHubScreen(snapshot));
                }
                default -> {
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}