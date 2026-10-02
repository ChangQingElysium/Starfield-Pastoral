package com.stardew.craft.client.animal;

import com.stardew.craft.animal.runtime.LivestockManagePayload;
import com.stardew.craft.client.gui.*;
import com.stardew.craft.client.gui.common.BuildingUiIcons;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public final class LivestockManagementScreen extends FarmFolioScreen {
    private final CompoundTag offer;
    private final List<CompoundTag> animals;
    private String selected = "", search = "", homeFilter = "";
    private int page;
    private boolean pending;

    public LivestockManagementScreen(CompoundTag offer) {
        this(offer,null);
    }
    public LivestockManagementScreen(CompoundTag offer, net.minecraft.client.gui.screens.Screen parent) {
        super(Component.translatable("livestock.stardewcraft.manage"), parent);
        this.offer = offer;
        animals = offer.getList("Animals", 10).stream().map(t -> (CompoundTag) t).toList();
        selected = offer.getString("Selected");
    }

    @Override public void onClose() {
        super.onClose();
        if(parent instanceof BuildingLedgerScreen ledger)ledger.returnFromAnimals();
    }
    public void preserveNavigation(LivestockManagementScreen previous) {
        search=previous.search;homeFilter=previous.homeFilter;page=previous.page;
    }
    public boolean acceptsReply(UUID nonce) {
        return pending && offer.getUUID("Nonce").equals(nonce);
    }

    private List<CompoundTag> entries() {
        return animals.stream()
                .filter(
                        r ->
                                homeFilter.isEmpty()
                                        || r.getUUID("Home").toString().equals(homeFilter))
                .filter(
                        r ->
                                search.isBlank()
                                        || r.getString("Name")
                                                .toLowerCase(Locale.ROOT)
                                                .contains(search.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private CompoundTag current() {
        return animals.stream()
                .filter(r -> r.getUUID("Id").toString().equals(selected))
                .findFirst()
                .orElse(null);
    }

    private Component homeName(CompoundTag animal) {
        if (animal.contains("HomeIssue")) return Component.translatable(animal.getString("HomeIssue"));
        return offer.getList("Homes", 10).stream()
                .map(t -> (CompoundTag) t)
                .filter(h -> h.getUUID("Id").equals(animal.getUUID("Home")))
                .findFirst()
                .map(BuildingChoiceScreen::homeName)
                .orElseGet(() -> Component.translatable("livestock.stardewcraft.no_home"));
    }

    private void send(String action, String value, UUID home) {
        var a = current();
        if (a == null || pending) return;
        pending = true;
        init();
        PacketDistributor.sendToServer(
                new LivestockManagePayload(
                        offer.getUUID("Nonce"), a.getUUID("Id"), home, action, value));
    }

    @Override
    protected void layout() {
        button(
                Component.translatable("gui.back"),
                x + 16,
                y + h - 34,
                92,
                24,
                () -> {
                    if (!selected.isEmpty()) {
                        selected = "";
                        init();
                    } else onClose();
                });
        var a = current();
        if (a != null) {
            int right = x + w - 148;
            button(
                                    Component.translatable("building.stardewcraft.rename"),
                                    right,
                                    y + 48,
                                    128,
                                    26,
                                    "tab",
                                    "pencil",
                                    () ->
                                            minecraft.setScreen(
                                                    new FarmRenameScreen(
                                                            this,
                                                            Component.translatable(
                                                                    "building.stardewcraft.rename"),
                                                            a.getString("Name"),
                                                            value ->
                                                                    send(
                                                                            "rename",
                                                                            value,
                                                                            new UUID(0, 0)))))
                            .active =
                    !pending;
            button(
                                    Component.translatable("stardewcraft.animal.query.hover.move"),
                                    right,
                                    y + 82,
                                    128,
                                    30,
                                    "button",
                                    "move",
                                    () -> {
                                        var homes =
                                                offer.getList("Homes", 10).stream()
                                                        .map(t -> (CompoundTag) t)
                                                        .filter(
                                                                r ->
                                                                        com.stardew.craft.animal.runtime.LivestockHomes.offered(r,a)
                                                                                && !r.getUUID("Id")
                                                                                        .equals(
                                                                                                a
                                                                                                        .getUUID(
                                                                                                                "Home")))
                                                        .toList();
                                        minecraft.setScreen(
                                                new BuildingChoiceScreen(
                                                        this,
                                                        homes,
                                                        a,
                                                        home ->
                                                                send(
                                                                        "move",
                                                                        "",
                                                                        home.getUUID("Id"))));
                                    })
                            .active =
                    !pending;
            if (a.getBoolean("CanReproduce"))
                button(
                                        Component.translatable(
                                                        "stardewcraft.animal.query.hover.repro")
                                                .append(" · ")
                                                .append(
                                                        Component.translatable(
                                                                a.getBoolean("Reproduction")
                                                                        ? "options.on"
                                                                        : "options.off")),
                                        right,
                                        y + 122,
                                        128,
                                        40,
                                        a.getBoolean("Reproduction") ? "packet_selected" : "tab",
                                        null,
                                        () ->
                                                send(
                                                        a.getBoolean("Reproduction")
                                                                ? "disable"
                                                                : "enable",
                                                        "",
                                                        new UUID(0, 0)))
                                .active =
                        !pending;
            button(
                                    ui("sell"),
                                    right,
                                    y + h - 74,
                                    128,
                                    26,
                                    () -> minecraft.setScreen(new SellScreen()))
                            .active =
                    !pending;
        } else {
            var labels = new LinkedHashMap<String, Component>();
            for (var raw : offer.getList("Homes", 10)) {
                var home = (CompoundTag) raw;
                labels.put(home.getUUID("Id").toString(), BuildingChoiceScreen.homeName(home));
            }
            button(
                    labels.getOrDefault(homeFilter, ui("all_homes")),
                    x + 24,
                    y + 36,
                    156,
                    28,
                    "tab",
                    "home",
                    () ->
                            minecraft.setScreen(
                                    new FarmCategoryScreen(
                                            this,
                                            labels,
                                            ui("all_homes"),
                                            value -> {
                                                homeFilter = value;
                                                page = 0;
                                                init();
                                            })));
            var input = field(ui("search"), search, x + w - 182, y + 32, 160, 64);
            input.setHint(ui("search"));
            input.setResponder(
                    s -> {
                        search = s;
                        page = 0;
                        rebuildRows();
                    });
            buildRows();
        }
    }

    private final List<net.minecraft.client.gui.components.Button> rowButtons = new ArrayList<>();

    private void rebuildRows() {
        for (var b : rowButtons) removeWidget(b);
        rowButtons.clear();
        buildRows();
    }

    private int rows() {
        return Math.max(1, (h - 130) / 60);
    }

    private int columns() {
        return w < 400 ? 1 : 2;
    }

    private void buildRows() {
        rowButtons.clear();
        var list = entries();
        int count = rows() * columns(), cw = (w - 48) / columns();
        page = com.stardew.craft.port.PortJava.clamp(page, 0, Math.max(0, (list.size() - 1) / count));
        for (int i = page * count; i < Math.min(list.size(), (page + 1) * count); i++) {
            var a = list.get(i);
            int j = i - page * count;
            rowButtons.add(
                    tile(
                            Component.literal(a.getString("Name")),
                            x + 24 + j % columns() * cw,
                            y + 80 + j / columns() * 60,
                            cw - 10,
                            56,
                            () -> {
                                selected = a.getUUID("Id").toString();
                                init();
                            },
                            (g, b) -> {
                                LivestockPortrait.draw(
                                        g, a, b.getX() + 2, b.getY() + 8, 40, 32, false);
                                label(
                                        g,
                                        Component.literal(a.getString("Name")),
                                        b.getX() + 48,
                                        b.getY() + 8,
                                        b.getWidth() - 130,
                                        INK);
                                label(
                                        g,
                                        homeName(a),
                                        b.getX() + 48,
                                        b.getY() + 29,
                                        b.getWidth() - 130,
                                        MUTED);
                                icon(g, "feed", b.getX() + b.getWidth() - 74, b.getY() + 3, 16);
                                label(
                                        g,
                                        ui(a.getInt("Fullness") >= 200 ? "fed" : "hungry"),
                                        b.getX() + b.getWidth() - 52,
                                        b.getY() + 7,
                                        50,
                                        INK);
                                icon(g, "pet", b.getX() + b.getWidth() - 74, b.getY() + 25, 16);
                                label(
                                        g,
                                        ui(a.getBoolean("Petted") ? "petted" : "unpetted"),
                                        b.getX() + b.getWidth() - 52,
                                        b.getY() + 29,
                                        50,
                                        MUTED);
                                rule(g, b.getX(), b.getY() + 54, b.getWidth());
                            }));
        }
        rowButtons.add(
                arrow(
                        false,
                        x + w / 2 - 60,
                        y + h - 70,
                        page > 0,
                        () -> {
                            page--;
                            rebuildRows();
                        }));
        rowButtons.add(
                arrow(
                        true,
                        x + w / 2 + 36,
                        y + h - 70,
                        (page + 1) * count < list.size(),
                        () -> {
                            page++;
                            rebuildRows();
                        }));
    }

    @Override
    protected void paint(GuiGraphics g) {
        var a = current();
        if (a == null) {
            paper(g, x + 16, y + 72, w - 32, h - 114);
            if (entries().isEmpty())
                paragraph(g, ui("no_results"), x + 36, y + 98, w - 72, y + h - 82, MUTED);
            int count = rows() * columns();
            label(
                    g,
                    Component.literal(
                            (page + 1)
                                    + " / "
                                    + Math.max(1, (entries().size() + count - 1) / count)),
                    x + w / 2 - 15,
                    y + h - 62,
                    50,
                    MUTED);
            return;
        }
        int pw = w - 182;
        paper(g, x + 16, y + 36, pw, h - 80);
        LivestockPortrait.draw(
                g, a, x + 32, y + 56, Math.min(110, pw - 32), Math.max(32, h - 230), false);
        label(g, Component.literal(a.getString("Name")), x + 32, y + h - 164, pw - 32, INK);
        BuildingUiIcons.hearts(g, x + 32, y + h - 144, a.getInt("Friendship"));
        label(
                g,
                Component.translatable("stardewcraft.animal.query.age", a.getInt("Age")),
                x + 32,
                y + h - 124,
                pw - 32,
                MUTED);
        label(g, homeName(a), x + 32, y + h - 104, pw - 32, MUTED);
        label(
                g,
                ui(a.getInt("Fullness") >= 200 ? "fed" : "hungry")
                        .copy()
                        .append(" · ")
                        .append(ui(a.getBoolean("Petted") ? "petted" : "unpetted")),
                x + 32,
                y + h - 84,
                pw - 32,
                INK);
        paragraph(
                g,
                Component.translatable(moodKey(a.getInt("MoodMessage"), a.getBoolean("Baby")), a.getString("Name")),
                x + 32,
                y + h - 64,
                pw - 32,
                y + h - 46,
                MUTED);
    }

    private static String moodKey(int message, boolean baby) {
        return switch (message) {
            case 0 -> baby ? "stardewcraft.animal.query.mood.0_baby" : "stardewcraft.animal.query.mood.0_adult";
            case 1, 3, 4, 5, 6 -> "stardewcraft.animal.query.mood." + message;
            default -> "stardewcraft.animal.query.mood.2";
        };
    }

    private final class SellScreen extends FarmFolioScreen {
        SellScreen() {
            super(ui("sell"), LivestockManagementScreen.this);
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
                        send("sell", "", new UUID(0, 0));
                    });
        }

        @Override
        protected void paint(GuiGraphics g) {
            var a = current();
            paper(g, x + 16, y + 36, w - 32, h - 92);
            LivestockPortrait.draw(g, a, x + 32, y + 50, 80, 70, false);
            label(g, Component.literal(a.getString("Name")), x + 130, y + 58, w - 160, INK);
            paragraph(g, ui("sell_warning"), x + 130, y + 82, w - 160, y + h - 112, MUTED);
            rule(g, x + 28, y + h - 106, w - 56);
            money(g, a.getInt("SellPrice"), x + w - 124, y + h - 92, INK);
        }
    }
}
