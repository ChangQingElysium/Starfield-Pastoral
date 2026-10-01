package com.stardew.craft.client.gui;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.DesertFestivalRaceActionPayload;
import com.stardew.craft.network.payload.DesertFestivalRaceSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class DesertFestivalShadyGuyScreen extends Screen implements DesertFestivalRaceSnapshotScreen, com.stardew.craft.port.PortScreen {
    private DesertFestivalRaceSnapshot snapshot;
    private final List<DesertFestivalRaceUi.ButtonHitbox> hitboxes = new ArrayList<>();
    private int selectedRacer = -1;
    private int introPage;

    public DesertFestivalShadyGuyScreen(DesertFestivalRaceSnapshot snapshot) {
        super(Component.translatable("stardewcraft.location.desert_festival_shady_guy"));
        this.snapshot = snapshot;
        this.selectedRacer = snapshot.sabotageTarget() >= 0 ? snapshot.sabotageTarget()
            : snapshot.racers().isEmpty() ? -1 : snapshot.racers().get(0).racerIndex();
    }

    @Override
    public void updateSnapshot(DesertFestivalRaceSnapshot snapshot) {
        this.snapshot = snapshot;
        if (snapshot.sabotageTarget() >= 0) selectedRacer = snapshot.sabotageTarget();
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
        DesertFestivalRaceUi.Layout layout = DesertFestivalRaceUi.layout(width, height, 500, 230);
        DesertFestivalRaceUi.darkPanel(graphics, layout.x(), layout.y(), layout.w(), layout.h());
        Component title = Component.translatable("stardewcraft.location.desert_festival_shady_guy");
        DesertFestivalRaceUi.menuTitle(graphics, font, title, layout.x(), layout.y(), layout.w());
        if (introPage < 3 && snapshot.sabotageTarget() < 0) {
            Component line = Component.translatable("stardewcraft.desert_festival.race.shady_intro." + introPage);
            DesertFestivalRaceUi.drawFitted(graphics, font, line, layout.x(34), layout.y(66), layout.w(432), layout.h(48), DesertFestivalRaceUi.TEXT, false, 0.68f);
            addButton(graphics, mouseX, mouseY, layout, 34, 166, 432, 24, "next", Component.translatable("stardewcraft.desert_festival.race.continue"), true);
            addButton(graphics, mouseX, mouseY, layout, 34, 196, 432, 22, "close", Component.translatable("gui.done"), true);
            return;
        }
        graphics.renderItem(new ItemStack(ModItems.CALICO_EGG.get()), layout.x(28), layout.y(40));
        DesertFestivalRaceUi.drawFitted(graphics, font, Component.literal("1"), layout.x(50), layout.y(45), layout.w(20), layout.h(10), DesertFestivalRaceUi.TEXT, false, 0.68f);
        Component prompt = snapshot.sabotageTarget() >= 0
            ? Component.translatable("stardewcraft.desert_festival.race.shady_already")
            : Component.translatable("stardewcraft.desert_festival.race.shady_prompt");
        DesertFestivalRaceUi.drawFitted(graphics, font, prompt, layout.x(86), layout.y(43), layout.w(380), layout.h(14), DesertFestivalRaceUi.TEXT, false, 0.68f);

        int cardW = 444 / Math.max(1, snapshot.racers().size());
        int cardX = 24;
        for (DesertFestivalRaceSnapshot.RacerEntry racer : snapshot.racers()) {
            renderRacerCard(graphics, mouseX, mouseY, layout, cardX, 76, cardW - 8, 78, racer);
            cardX += cardW;
        }
        boolean canConfirm = selectedRacer >= 0 && snapshot.sabotageTarget() < 0 && snapshot.canGuess() && !DesertFestivalRaceUi.raceInProgress(snapshot);
        addButton(graphics, mouseX, mouseY, layout, 34, 166, 432, 24, "sabotage", Component.translatable("stardewcraft.desert_festival.race.shady_confirm"), canConfirm);
        addButton(graphics, mouseX, mouseY, layout, 34, 196, 432, 22, "close", Component.translatable("gui.done"), true);
    }

    private void renderRacerCard(GuiGraphics graphics, int mouseX, int mouseY, DesertFestivalRaceUi.Layout layout,
                                 int x, int y, int w, int h, DesertFestivalRaceSnapshot.RacerEntry racer) {
        boolean selected = racer.racerIndex() == selectedRacer;
        DesertFestivalRaceUi.ButtonHitbox hitbox = DesertFestivalRaceUi.hitbox(layout, x, y, w, h, "select", racer.racerIndex(), 0, "");
        DesertFestivalRaceUi.menuRow(graphics, font, mouseX, mouseY, hitbox, Component.empty(), true, selected);
        int iconSize = Math.min(layout.w(48), Math.max(16, hitbox.w() - layout.w(28)));
        DesertFestivalRaceUi.drawRacerIconSize(graphics, racer.racerIndex(), hitbox.x() + hitbox.w() / 2 - iconSize / 2, hitbox.y() + layout.h(8), iconSize);
        Component name = DesertFestivalRaceUi.racerName(racer.racerIndex());
        DesertFestivalRaceUi.drawCentered(graphics, font, name, hitbox.x() + layout.w(8), hitbox.y() + layout.h(56), hitbox.w() - layout.w(16), layout.h(14), DesertFestivalRaceUi.DARK_TEXT, false);
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
                case "next" -> {
                    DesertFestivalRaceUi.playButton();
                    introPage++;
                }
                case "select" -> {
                    DesertFestivalRaceUi.playButton();
                    selectedRacer = hitbox.racerIndex();
                }
                case "sabotage" -> {
                    DesertFestivalRaceUi.play(com.stardew.craft.sound.ModSounds.COIN);
                    PacketDistributor.sendToServer(new DesertFestivalRaceActionPayload("sabotage", selectedRacer, 0, ""));
                }
                case "close" -> onClose();
                default -> {
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        DesertFestivalRaceUi.playBack();
        super.onClose();
    }
}