package com.stardew.craft.client.gui;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.DesertFestivalRaceActionPayload;
import com.stardew.craft.network.payload.DesertFestivalRaceSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class DesertFestivalRaceHubScreen extends Screen implements DesertFestivalRaceSnapshotScreen {
    private DesertFestivalRaceSnapshot snapshot;
    private final List<DesertFestivalRaceUi.ButtonHitbox> hitboxes = new ArrayList<>();

    public DesertFestivalRaceHubScreen(DesertFestivalRaceSnapshot snapshot) {
        super(Component.translatable("stardewcraft.desert_festival.race.title"));
        this.snapshot = snapshot;
    }

    @Override
    public void updateSnapshot(DesertFestivalRaceSnapshot snapshot) {
        this.snapshot = snapshot;
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
        DesertFestivalRaceUi.Layout layout = DesertFestivalRaceUi.layout(width, height, 430, 244);
        DesertFestivalRaceUi.panel(graphics, layout.x(), layout.y(), layout.w(), layout.h());
        DesertFestivalRaceUi.menuTitle(graphics, font, Component.translatable("stardewcraft.desert_festival.race.title"), layout.x(), layout.y(), layout.w());

        int topY = layout.y(42);
        graphics.renderItem(new ItemStack(ModItems.CALICO_EGG.get()), layout.x(28), topY + layout.h(4));
        DesertFestivalRaceUi.menuStat(graphics, font, Component.translatable("stardewcraft.desert_festival.race.eggs"),
            Component.literal(String.valueOf(snapshot.eggCount())), layout.x(52), topY, layout.w(70));
        DesertFestivalRaceUi.menuStat(graphics, font, Component.translatable("stardewcraft.desert_festival.race.status"),
            Component.translatable("stardewcraft.desert_festival.race.state." + snapshot.raceState()), layout.x(142), topY, layout.w(140));
        Component pick = snapshot.nextGuess() >= 0 ? DesertFestivalRaceUi.racerName(snapshot.nextGuess()) : Component.literal("--");
        DesertFestivalRaceUi.menuStat(graphics, font, Component.translatable("stardewcraft.desert_festival.race.ticket"),
            pick, layout.x(300), topY, layout.w(98));

        boolean canBet = snapshot.canGuess() && !DesertFestivalRaceUi.raceInProgress(snapshot);
        addRow(graphics, mouseX, mouseY, layout, 32, 86, 366, 34, "single",
            Component.translatable("stardewcraft.desert_festival.race.mode_single"), canBet);
        addRow(graphics, mouseX, mouseY, layout, 32, 126, 366, 34, "rooms",
            Component.translatable("stardewcraft.desert_festival.race.mode_multi"), canBet);
        addRow(graphics, mouseX, mouseY, layout, 32, 166, 366, 34, "watch",
            Component.translatable("stardewcraft.desert_festival.race.watch"), true);
        boolean hasReward = snapshot.rewardClaims() > 0 || snapshot.specialRewardPending() || hasBetReward();
        addRow(graphics, mouseX, mouseY, layout, 32, 208, 366, 28, "claim",
            Component.translatable("stardewcraft.desert_festival.race.claim"), hasReward);
    }

    private void addRow(GuiGraphics graphics, int mouseX, int mouseY, DesertFestivalRaceUi.Layout layout,
                        int x, int y, int w, int h, String action,
                        Component label, boolean enabled) {
        DesertFestivalRaceUi.ButtonHitbox hitbox = DesertFestivalRaceUi.hitbox(layout, x, y, w, h, action, -1, 0, "");
        DesertFestivalRaceUi.menuRow(graphics, font, mouseX, mouseY, hitbox, label, enabled, false);
        if (enabled) {
            hitboxes.add(hitbox);
        }
    }

    private boolean hasBetReward() {
        for (DesertFestivalRaceSnapshot.RoomEntry room : snapshot.rooms()) {
            if (DesertFestivalRaceUi.roomRewardPending(room)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        for (DesertFestivalRaceUi.ButtonHitbox hitbox : hitboxes) {
            if (!hitbox.contains((int) mouseX, (int) mouseY)) continue;
            switch (hitbox.action()) {
                case "single" -> {
                    DesertFestivalRaceUi.playButton();
                    Minecraft.getInstance().setScreen(new DesertFestivalRaceSingleBetScreen(snapshot));
                }
                case "rooms" -> {
                    DesertFestivalRaceUi.playButton();
                    Minecraft.getInstance().setScreen(new DesertFestivalRaceRoomListScreen(snapshot));
                }
                case "watch" -> {
                    DesertFestivalRaceUi.playButton();
                    Minecraft.getInstance().setScreen(new DesertFestivalRaceWatchScreen(snapshot));
                }
                case "claim" -> {
                    DesertFestivalRaceUi.play(com.stardew.craft.sound.ModSounds.REWARD);
                    PacketDistributor.sendToServer(new DesertFestivalRaceActionPayload("claim", -1, 0, ""));
                }
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