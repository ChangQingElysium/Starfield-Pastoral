package com.stardew.craft.client.gui;

import com.stardew.craft.client.building.RobinCatalogArt;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** Three bookmarks per page, without limiting the number of registered homes. */
public final class BuildingChoiceScreen extends FarmFolioScreen {
    private final List<CompoundTag> homes;
    private final Consumer<CompoundTag> choose;
    private final CompoundTag animal;
    private int selected, page;

    public BuildingChoiceScreen(
            Screen parent, List<CompoundTag> homes, Consumer<CompoundTag> choose) {
        this(parent, homes, null, choose);
    }

    public BuildingChoiceScreen(
            Screen parent,
            List<CompoundTag> homes,
            CompoundTag animal,
            Consumer<CompoundTag> choose) {
        super(ui("choose_home"), parent);
        this.homes = List.copyOf(homes);
        this.animal = animal;
        this.choose = choose;
    }

    public static net.minecraft.network.chat.MutableComponent homeName(CompoundTag home) {
        if (!home.getString("BuildingName").isBlank())
            return Component.literal(home.getString("BuildingName"));
        if (!home.getString("TitleKey").isBlank()) return Component.translatable(home.getString("TitleKey"));
        if (!home.getString("Title").isBlank()) return Component.literal(home.getString("Title"));
        var family = net.minecraft.resources.ResourceLocation.tryParse(home.getString("Family"));
        return family == null
                ? Component.literal("?")
                : com.stardew.craft.api.v1.building.StardewBuildingFamilies.title(family,home.getInt("Tier"));
    }

    @Override
    protected void layout() {
        int side = Math.min(132, w / 3), count = Math.min(3, Math.max(1, (h - 112) / 48));
        int pages = Math.max(1, (homes.size() + count - 1) / count);
        page = com.stardew.craft.port.PortJava.clamp(page, 0, pages - 1);
        selected = homes.isEmpty() ? -1 : com.stardew.craft.port.PortJava.clamp(selected, page * count, Math.min(homes.size(), (page + 1) * count) - 1);
        for (int i = page * count; i < Math.min(homes.size(), (page + 1) * count); i++) {
            int index = i;
            button(
                    homeName(homes.get(i)),
                    x + 12,
                    y + 48 + (i - page * count) * 48,
                    side,
                    42,
                    selected == i ? "packet_selected" : "tab",
                    "home",
                    () -> {
                        selected = index;
                        init();
                    });
        }
        arrow(
                false,
                x + 16,
                y + h - 66,
                page > 0,
                () -> {
                    page--;
                    init();
                });
        arrow(
                true,
                x + side - 16,
                y + h - 66,
                page + 1 < pages,
                () -> {
                    page++;
                    init();
                });
        button(Component.translatable("gui.back"), x + 16, y + h - 34, 90, 24, this::onClose);
        button(
                                Component.translatable("stardewcraft.animal.query.move_confirm"),
                                x + w - 174,
                                y + h - 34,
                                158,
                                24,
                                () -> {
                                    if (selected < 0 || selected >= homes.size()) return;
                                    var home = homes.get(selected);
                                    minecraft.setScreen(parent);
                                    choose.accept(home);
                                })
                        .active =
                !homes.isEmpty()
                        && (!homes.get(selected).contains("Selectable")
                                || homes.get(selected).getBoolean("Selectable"))
                        && homes.get(selected).getInt("Used")
                                < homes.get(selected).getInt("Capacity");
    }

    @Override
    protected void paint(GuiGraphics g) {
        int side = Math.min(132, w / 3), px = x + side + 20, pw = w - side - 36;
        paper(g, px, y + 36, pw, h - 80);
        if (homes.isEmpty()) {
            paragraph(
                    g,
                    Component.translatable("stardewcraft.animal.query.move_empty"),
                    px + 14,
                    y + 58,
                    pw - 28,
                    y + h - 54,
                    MUTED);
            return;
        }
        var home = homes.get(selected);
        label(g, homeName(home), px + 14, y + 50, pw - 28, INK);
        String family = home.getString("Family");
        int tier = home.getInt("Tier");
        int artWidth = animal == null ? pw - 28 : Math.max(96, pw - 142);
        if (!RobinCatalogArt.draw(
                g,
                family + (tier > 1 ? "_upgrade_" + tier : ""),
                px + 14,
                y + 72,
                artWidth,
                Math.max(40, h - 192))) icon(g, "home", px + artWidth / 2, y + 112, 32);
        if (animal != null) {
            int ax = px + pw - 114;
            com.stardew.craft.client.animal.LivestockPortrait.draw(
                    g, animal, ax, y + 90, 94, 64, false);
            label(
                    g,
                    animal.getString("Name").isBlank()
                            ? com.stardew.craft.client.animal.LivestockPortrait.name(animal)
                            : Component.literal(animal.getString("Name")),
                    ax,
                    y + 164,
                    94,
                    INK);
        }
        label(
                g,
                Component.translatable(
                        "livestock.stardewcraft.occupancy",
                        home.getInt("Used"),
                        home.getInt("Capacity")),
                px + 14,
                y + h - 108,
                pw - 28,
                INK);
        label(
                g,
                Component.translatable(
                        home.getInt("Used") < home.getInt("Capacity")
                                ? "stardewcraft.animal.query.move_status.available"
                                : "stardewcraft.animal.query.move_status.full"),
                px + 14,
                y + h - 86,
                pw - 28,
                MUTED);
        label(
                g,
                Component.literal(
                        home.getInt("X") + ", " + home.getInt("Y") + ", " + home.getInt("Z")),
                px + 14,
                y + h - 66,
                pw - 28,
                MUTED);
    }
}
