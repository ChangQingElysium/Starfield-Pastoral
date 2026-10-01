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
public class DesertFestivalRaceRoomListScreen extends Screen implements DesertFestivalRaceSnapshotScreen, com.stardew.craft.port.PortScreen {
    private DesertFestivalRaceSnapshot snapshot;
    private final List<DesertFestivalRaceUi.ButtonHitbox> hitboxes = new ArrayList<>();
    private String selectedRoom = "";
    private boolean enterCreatedRoom;

    public DesertFestivalRaceRoomListScreen(DesertFestivalRaceSnapshot snapshot) {
        super(Component.translatable("stardewcraft.desert_festival.race.mode_multi"));
        this.snapshot = snapshot;
        if (!snapshot.rooms().isEmpty()) {
            selectedRoom = snapshot.rooms().get(0).roomId();
        }
    }

    @Override
    public void updateSnapshot(DesertFestivalRaceSnapshot snapshot) {
        this.snapshot = snapshot;
        if (enterCreatedRoom) {
            for (DesertFestivalRaceSnapshot.RoomEntry room : snapshot.rooms()) {
                if (room.host()) {
                    enterCreatedRoom = false;
                    Minecraft.getInstance().setScreen(new DesertFestivalRaceRoomScreen(snapshot, room.roomId()));
                    return;
                }
            }
        }
        if (selectedRoom.isBlank() && !snapshot.rooms().isEmpty()) {
            selectedRoom = snapshot.rooms().get(0).roomId();
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
        DesertFestivalRaceUi.Layout layout = DesertFestivalRaceUi.layout(width, height, 520, 300);
        DesertFestivalRaceUi.panel(graphics, layout.x(), layout.y(), layout.w(), layout.h());
        DesertFestivalRaceUi.menuTitle(graphics, font, Component.translatable("stardewcraft.desert_festival.race.room_list"), layout.x(), layout.y(), layout.w());
        DesertFestivalRaceUi.drawCentered(graphics, font, Component.translatable("stardewcraft.desert_festival.race.room_list_help"),
            layout.x(28), layout.y(28), layout.w(464), layout.h(12), DesertFestivalRaceUi.DARK_TEXT, false);

        int rowY = 58;
        if (snapshot.rooms().isEmpty()) {
            Component empty = Component.translatable("stardewcraft.desert_festival.race.room_empty");
            DesertFestivalRaceUi.drawCentered(graphics, font, empty, layout.x(40), layout.y(98), layout.w(440), layout.h(32), DesertFestivalRaceUi.DARK_TEXT, false);
        } else {
            for (DesertFestivalRaceSnapshot.RoomEntry room : snapshot.rooms()) {
                if (rowY > 208) break;
                renderRoomRow(graphics, mouseX, mouseY, layout, room, 24, rowY, 472);
                rowY += 48;
            }
        }

        addButton(graphics, mouseX, mouseY, layout, 24, 266, 126, 24, "create_room", Component.translatable("stardewcraft.desert_festival.race.room_create"), snapshot.canGuess());
        addButton(graphics, mouseX, mouseY, layout, 228, 266, 150, 24, "join", Component.translatable("stardewcraft.desert_festival.race.join_room"), !selectedRoom.isBlank());
        addButton(graphics, mouseX, mouseY, layout, 388, 266, 108, 24, "back", Component.translatable("gui.back"), true);
    }

    private void renderRoomRow(GuiGraphics graphics, int mouseX, int mouseY, DesertFestivalRaceUi.Layout layout,
                               DesertFestivalRaceSnapshot.RoomEntry room, int x, int y, int w) {
        boolean selected = room.roomId().equals(selectedRoom);
        DesertFestivalRaceUi.ButtonHitbox hitbox = DesertFestivalRaceUi.hitbox(layout, x, y, w, 42, "select_room", -1, 0, room.roomId());
        DesertFestivalRaceUi.menuRow(graphics, font, 0, 0,
            hitbox, Component.empty(), true, selected || hitbox.contains(mouseX, mouseY));
        Component name = Component.literal(room.roomId() + "  " + room.hostName());
        DesertFestivalRaceUi.drawFitted(graphics, font, name, hitbox.x() + layout.w(12), hitbox.y() + layout.h(7), layout.w(158), layout.h(12), DesertFestivalRaceUi.DARK_TEXT, false, 0.68f);
        DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.room_pool", room.totalPool()), hitbox.x() + layout.w(190), hitbox.y() + layout.h(7), layout.w(104), layout.h(12), DesertFestivalRaceUi.GOLD, false, 0.68f);
        DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.room_bettors", room.bettorCount()), hitbox.x() + layout.w(190), hitbox.y() + layout.h(23), layout.w(104), layout.h(12), DesertFestivalRaceUi.DARK_TEXT, false, 0.68f);
        DesertFestivalRaceUi.drawFitted(graphics, font, roomState(room), hitbox.x() + hitbox.w() - layout.w(88), hitbox.y() + layout.h(7), layout.w(76), layout.h(12), room.open() ? 0xFF2F6F30 : 0xFF71410F, false, 0.68f);
        if (room.playerAmount() > 0) {
            DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.my_bet", room.playerAmount()), hitbox.x() + layout.w(12), hitbox.y() + layout.h(23), layout.w(156), layout.h(12), 0xFF71410F, false, 0.68f);
        } else if (DesertFestivalRaceUi.roomRewardPending(room)) {
            DesertFestivalRaceUi.drawFitted(graphics, font, Component.translatable("stardewcraft.desert_festival.race.room_reward", room.playerPayout()), hitbox.x() + layout.w(12), hitbox.y() + layout.h(23), layout.w(156), layout.h(12), DesertFestivalRaceUi.GOLD, false, 0.68f);
        }
        hitboxes.add(hitbox);
    }

    private Component roomState(DesertFestivalRaceSnapshot.RoomEntry room) {
        if (room.settled()) return Component.translatable("stardewcraft.desert_festival.race.room_settled");
        if (!room.open()) return Component.translatable("stardewcraft.desert_festival.race.room_locked");
        return Component.translatable("stardewcraft.desert_festival.race.room_open");
    }

    private void addButton(GuiGraphics graphics, int mouseX, int mouseY, DesertFestivalRaceUi.Layout layout,
                           int x, int y, int w, int h, String action, Component label, boolean enabled) {
        DesertFestivalRaceUi.ButtonHitbox hitbox = DesertFestivalRaceUi.hitbox(layout, x, y, w, h, action, -1, 0, selectedRoom);
        DesertFestivalRaceUi.button(graphics, font, mouseX, mouseY, hitbox, label, enabled);
        if (enabled) hitboxes.add(hitbox);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        for (DesertFestivalRaceUi.ButtonHitbox hitbox : hitboxes) {
            if (!hitbox.contains((int) mouseX, (int) mouseY)) continue;
            switch (hitbox.action()) {
                case "select_room" -> {
                    DesertFestivalRaceUi.playButton();
                    selectedRoom = hitbox.roomId();
                }
                case "create_room" -> {
                    DesertFestivalRaceUi.play(com.stardew.craft.sound.ModSounds.BIG_SELECT);
                    enterCreatedRoom = true;
                    PacketDistributor.sendToServer(new DesertFestivalRaceActionPayload("create_room", -1, 0, ""));
                }
                case "join" -> {
                    DesertFestivalRaceUi.playButton();
                    Minecraft.getInstance().setScreen(new DesertFestivalRaceRoomScreen(snapshot, selectedRoom));
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