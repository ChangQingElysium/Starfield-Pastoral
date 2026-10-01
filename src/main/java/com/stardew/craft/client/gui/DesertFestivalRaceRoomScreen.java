package com.stardew.craft.client.gui;

import com.stardew.craft.network.payload.DesertFestivalRaceActionPayload;
import com.stardew.craft.network.payload.DesertFestivalRaceSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class DesertFestivalRaceRoomScreen extends Screen implements DesertFestivalRaceSnapshotScreen {
    private DesertFestivalRaceSnapshot snapshot;
    private final List<DesertFestivalRaceUi.ButtonHitbox> hitboxes = new ArrayList<>();
    private final String roomId;
    private int selectedRacer = -1;
    private int betAmount = 1;

    public DesertFestivalRaceRoomScreen(DesertFestivalRaceSnapshot snapshot, String roomId) {
        super(Component.translatable("stardewcraft.desert_festival.race.room_detail"));
        this.snapshot = snapshot;
        this.roomId = roomId;
        if (!snapshot.racers().isEmpty()) {
            selectedRacer = snapshot.racers().get(0).racerIndex();
        }
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
        DesertFestivalRaceSnapshot.RoomEntry room = room();
        DesertFestivalRaceUi.Layout layout = DesertFestivalRaceUi.layout(width, height, 560, 344);
        DesertFestivalRaceUi.panel(graphics, layout.x(), layout.y(), layout.w(), layout.h());
        Component title = room == null ? Component.translatable("stardewcraft.desert_festival.race.room_missing")
            : Component.literal(room.roomId() + "  " + room.hostName());
        DesertFestivalRaceUi.menuTitle(graphics, font, title, layout.x(), layout.y(), layout.w());
        if (room == null) {
            addButton(graphics, mouseX, mouseY, layout, 32, 310, 496, 24, "back", Component.translatable("gui.back"), true);
            return;
        }
        DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.room_pool", room.totalPool()), layout.x(32), layout.y(42), layout.w(104), layout.h(12), DesertFestivalRaceUi.GOLD, false, 0.68f);
        DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.room_bettors", room.bettorCount()), layout.x(144), layout.y(42), layout.w(104), layout.h(12), DesertFestivalRaceUi.DARK_TEXT, false, 0.68f);
        DesertFestivalRaceUi.drawFitted(graphics, font, room.open()
            ? Component.translatable("stardewcraft.desert_festival.race.room_open")
            : Component.translatable("stardewcraft.desert_festival.race.room_locked"), layout.x(468), layout.y(42), layout.w(66), layout.h(12), room.open() ? 0xFF2F6F30 : 0xFF71410F, false, 0.68f);
        DesertFestivalRaceUi.drawCentered(graphics, font, Component.translatable("stardewcraft.desert_festival.race.room_bet_help"),
            layout.x(32), layout.y(58), layout.w(496), layout.h(12), DesertFestivalRaceUi.DARK_TEXT, false);

        int cardY = 76;
        for (DesertFestivalRaceSnapshot.RacerEntry racer : snapshot.racers()) {
            int pool = poolFor(room, racer.racerIndex());
            int odds = oddsFor(room, racer.racerIndex());
            renderRacerCard(graphics, mouseX, mouseY, layout, 28, cardY, 504, 42, racer, pool, odds, room.playerRacer() == racer.racerIndex());
            cardY += 48;
        }

        int stepY = cardY + 8;
        DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.bet_amount"), layout.x(36), layout.y(stepY + 6), layout.w(54), layout.h(12), DesertFestivalRaceUi.GOLD, false, 0.68f);
        addButton(graphics, mouseX, mouseY, layout, 84, stepY, 28, 24, "amount_down", Component.literal("-"), true);
        DesertFestivalRaceUi.drawCentered(graphics, font, Component.literal(String.valueOf(betAmount)), layout.x(116), layout.y(stepY), layout.w(38), layout.h(24), DesertFestivalRaceUi.DARK_TEXT, false);
        addButton(graphics, mouseX, mouseY, layout, 158, stepY, 28, 24, "amount_up", Component.literal("+"), true);
        boolean canBet = room.open() && room.playerAmount() <= 0 && snapshot.canGuess();
        addButton(graphics, mouseX, mouseY, layout, 210, stepY, 128, 24, "bet", Component.translatable("stardewcraft.desert_festival.race.bet"), canBet);
        addButton(graphics, mouseX, mouseY, layout, 354, stepY, 112, 24, "watch", Component.translatable("stardewcraft.desert_festival.race.watch"), true);
        if (room.host()) {
            addButton(graphics, mouseX, mouseY, layout, 34, stepY + 34, 144, 24, "lock_room", Component.translatable("stardewcraft.desert_festival.race.lock"), room.open());
            addButton(graphics, mouseX, mouseY, layout, 194, stepY + 34, 164, 24, "start_room", Component.translatable("stardewcraft.desert_festival.race.start"), snapshot.canGuess());
        }
        if (DesertFestivalRaceUi.roomRewardPending(room)) {
            DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.room_reward", room.playerPayout()), layout.x(402), layout.y(stepY + 40), layout.w(130), layout.h(12), DesertFestivalRaceUi.GOLD, false, 0.68f);
        }
        addButton(graphics, mouseX, mouseY, layout, 192, 310, 176, 24, "claim", Component.translatable("stardewcraft.desert_festival.race.claim"), DesertFestivalRaceUi.roomRewardPending(room));
        addButton(graphics, mouseX, mouseY, layout, 426, 310, 104, 24, "back", Component.translatable("gui.back"), true);
    }

    private void renderRacerCard(GuiGraphics graphics, int mouseX, int mouseY, DesertFestivalRaceUi.Layout layout,
                                 int x, int y, int w, int h, DesertFestivalRaceSnapshot.RacerEntry racer, int pool, int odds, boolean mine) {
        boolean selected = selectedRacer == racer.racerIndex();
        DesertFestivalRaceUi.ButtonHitbox hitbox = DesertFestivalRaceUi.hitbox(layout, x, y, w, h, "select", racer.racerIndex(), 0, roomId);
        DesertFestivalRaceUi.menuRow(graphics, font, mouseX, mouseY,
            hitbox, Component.empty(), true, selected);
        int iconSize = Math.min(layout.w(32), hitbox.h() - layout.h(10));
        DesertFestivalRaceUi.drawRacerIconSize(graphics, racer.racerIndex(), hitbox.x() + layout.w(12), hitbox.y() + hitbox.h() / 2 - iconSize / 2, iconSize);
        Component name = DesertFestivalRaceUi.racerName(racer.racerIndex());
        DesertFestivalRaceUi.drawFitted(graphics, font, name, hitbox.x() + layout.w(54), hitbox.y() + layout.h(8), layout.w(210), layout.h(26), DesertFestivalRaceUi.DARK_TEXT, false, 0.68f);
        DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.room_pool", pool), hitbox.x() + hitbox.w() - layout.w(196), hitbox.y() + layout.h(8), layout.w(86), layout.h(14), DesertFestivalRaceUi.GOLD, false, 0.68f);
        DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.odds", odds <= 0 ? "--" : "x" + odds), hitbox.x() + hitbox.w() - layout.w(104), hitbox.y() + layout.h(8), layout.w(84), layout.h(14), DesertFestivalRaceUi.DARK_TEXT, false, 0.68f);
        if (mine) {
            DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.my_mark"), hitbox.x() + hitbox.w() - layout.w(104), hitbox.y() + layout.h(25), layout.w(84), layout.h(12), DesertFestivalRaceUi.GOLD, false, 0.68f);
        }
        hitboxes.add(hitbox);
    }

    private DesertFestivalRaceSnapshot.RoomEntry room() {
        for (DesertFestivalRaceSnapshot.RoomEntry room : snapshot.rooms()) {
            if (room.roomId().equals(roomId)) return room;
        }
        return null;
    }

    private int poolFor(DesertFestivalRaceSnapshot.RoomEntry room, int racerIndex) {
        for (DesertFestivalRaceSnapshot.OddsEntry odds : room.odds()) {
            if (odds.racerIndex() == racerIndex) return odds.pool();
        }
        return 0;
    }

    private int oddsFor(DesertFestivalRaceSnapshot.RoomEntry room, int racerIndex) {
        for (DesertFestivalRaceSnapshot.OddsEntry odds : room.odds()) {
            if (odds.racerIndex() == racerIndex) return odds.projectedPayout();
        }
        return 0;
    }

    private void addButton(GuiGraphics graphics, int mouseX, int mouseY, DesertFestivalRaceUi.Layout layout,
                           int x, int y, int w, int h, String action, Component label, boolean enabled) {
        DesertFestivalRaceUi.ButtonHitbox hitbox = DesertFestivalRaceUi.hitbox(layout, x, y, w, h, action, selectedRacer, betAmount, roomId);
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
                case "amount_down" -> {
                    DesertFestivalRaceUi.playButton();
                    betAmount = Math.max(1, betAmount - 1);
                }
                case "amount_up" -> {
                    DesertFestivalRaceUi.playButton();
                    betAmount = Math.min(999, betAmount + 1);
                }
                case "bet" -> {
                    DesertFestivalRaceUi.play(com.stardew.craft.sound.ModSounds.COIN);
                    PacketDistributor.sendToServer(new DesertFestivalRaceActionPayload(hitbox.action(), selectedRacer, betAmount, roomId));
                }
                case "lock_room", "start_room" -> {
                    DesertFestivalRaceUi.play(com.stardew.craft.sound.ModSounds.BIG_SELECT);
                    PacketDistributor.sendToServer(new DesertFestivalRaceActionPayload(hitbox.action(), selectedRacer, betAmount, roomId));
                }
                case "claim" -> {
                    DesertFestivalRaceUi.play(com.stardew.craft.sound.ModSounds.REWARD);
                    PacketDistributor.sendToServer(new DesertFestivalRaceActionPayload("claim", -1, 0, ""));
                }
                case "watch" -> {
                    DesertFestivalRaceUi.playButton();
                    Minecraft.getInstance().setScreen(new DesertFestivalRaceWatchScreen(snapshot));
                }
                case "back" -> {
                    DesertFestivalRaceUi.playBack();
                    Minecraft.getInstance().setScreen(new DesertFestivalRaceRoomListScreen(snapshot));
                }
                default -> {
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}