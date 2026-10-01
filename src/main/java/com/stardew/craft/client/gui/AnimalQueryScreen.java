package com.stardew.craft.client.gui;

import com.stardew.craft.client.animal.LivestockPortrait;
import com.stardew.craft.client.gui.common.BuildingUiIcons;
import com.stardew.craft.menu.AnimalQueryMenu;
import com.stardew.craft.network.payload.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.network.PacketDistributor;

/** Addon-backed animal queries retain their menu protocol and use the reviewed detail layout. */
public final class AnimalQueryScreen extends FarmFolioScreen
        implements MenuAccess<AnimalQueryMenu> {
    private final AnimalQueryMenu menu;
    private String name;
    private CompoundTag portrait = new CompoundTag();

    public AnimalQueryScreen(AnimalQueryMenu menu, Inventory inventory, Component title) {
        super(title, null);
        this.menu = menu;
        name = title.getString();
    }

    @Override
    public AnimalQueryMenu getMenu() {
        return menu;
    }

    public void metadata(CompoundTag data) {
        portrait = data.copy();
    }

    @Override
    protected void layout() {
        int right = x + w - 156;
        button(Component.translatable("gui.back"), x + 16, y + h - 34, 94, 24, this::onClose);
        button(
                Component.translatable("building.stardewcraft.rename"),
                right,
                y + 48,
                136,
                26,
                "tab",
                "pencil",
                () ->
                        minecraft.setScreen(
                                new FarmRenameScreen(
                                        this,
                                        Component.translatable("building.stardewcraft.rename"),
                                        name,
                                        value -> {
                                            name = value;
                                            PacketDistributor.sendToServer(
                                                    new AnimalRenamePayload(
                                                            menu.getAnimalId(), value));
                                        })));
        button(
                Component.translatable("stardewcraft.animal.query.hover.move"),
                right,
                y + 84,
                136,
                30,
                "button",
                "move",
                () ->
                        PacketDistributor.sendToServer(
                                new AnimalQueryActionPayload(
                                        AnimalQueryActionPayload.Action.MOVE_HOME, false)));
        if (menu.canToggleReproduction())
            button(
                    Component.translatable("stardewcraft.animal.query.hover.repro")
                            .append(" · ")
                            .append(
                                    Component.translatable(
                                            menu.allowReproduction()
                                                    ? "options.on"
                                                    : "options.off")),
                    right,
                    y + 126,
                    136,
                    40,
                    menu.allowReproduction() ? "packet_selected" : "tab",
                    null,
                    () -> {
                        boolean allow = !menu.allowReproduction();
                        PacketDistributor.sendToServer(
                                new AnimalQueryActionPayload(
                                        AnimalQueryActionPayload.Action.TOGGLE_REPRODUCTION,
                                        allow));
                        menu.setAllowReproductionValue(allow);
                        init();
                    });
        button(ui("sell"), right, y + h - 76, 136, 28, () -> minecraft.setScreen(new Sale()));
    }

    private boolean reproduction;

    @Override
    public void tick() {
        if (reproduction != menu.canToggleReproduction()) {
            reproduction = menu.canToggleReproduction();
            init();
        }
    }

    @Override
    protected void paint(GuiGraphics g) {
        int pw = w - 192;
        paper(g, x + 16, y + 36, pw, h - 80);
        LivestockPortrait.draw(g, portrait, x + 30, y + 50, pw - 28, Math.max(32, h - 214), false);
        label(g, Component.literal(name), x + 30, y + h - 146, pw - 28, INK);
        BuildingUiIcons.hearts(g, x + 30, y + h - 126, menu.getFriendship());
        label(
                g,
                Component.translatable("stardewcraft.animal.query.age", menu.getAgeDays()),
                x + 30,
                y + h - 106,
                pw - 28,
                MUTED);
        label(
                g,
                ui(menu.wasFedToday() ? "fed" : "hungry")
                        .copy()
                        .append(" · ")
                        .append(ui(menu.wasPetToday() ? "petted" : "unpetted")),
                x + 30,
                y + h - 86,
                pw - 28,
                INK);
        label(
                g,
                Component.translatable(menu.getMoodTranslationKey(), name),
                x + 30,
                y + h - 66,
                pw - 28,
                MUTED);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(
                new AnimalQueryActionPayload(AnimalQueryActionPayload.Action.CLOSE, false));
        minecraft.setScreen(null);
    }

    private final class Sale extends FarmFolioScreen {
        Sale() {
            super(ui("sell"), AnimalQueryScreen.this);
        }

        @Override
        protected int preferredWidth() {
            return 332;
        }

        @Override
        protected int preferredHeight() {
            return 249;
        }

        @Override
        protected void layout() {
            button(ui("keep"), x + 16, y + h - 40, (w - 42) / 2, 26, this::onClose);
            button(
                    ui("sell"),
                    x + w / 2 + 5,
                    y + h - 40,
                    (w - 42) / 2,
                    26,
                    () -> {
                        minecraft.setScreen(parent);
                        PacketDistributor.sendToServer(
                                new AnimalQueryActionPayload(
                                        AnimalQueryActionPayload.Action.SELL, false));
                    });
        }

        @Override
        protected void paint(GuiGraphics g) {
            paper(g, x + 16, y + 36, w - 32, h - 92);
            LivestockPortrait.draw(g, portrait, x + 30, y + 50, 80, 72, false);
            label(g, Component.literal(name), x + 126, y + 54, w - 152, INK);
            paragraph(g, ui("sell_warning"), x + 126, y + 82, w - 152, y + h - 110, MUTED);
            rule(g, x + 30, y + h - 106, w - 60);
            money(g, menu.getEstimatedSellPrice(), x + w - 124, y + h - 92, INK);
        }
    }
}
