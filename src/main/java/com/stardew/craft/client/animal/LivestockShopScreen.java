package com.stardew.craft.client.animal;

import com.stardew.craft.animal.runtime.*;
import com.stardew.craft.client.gui.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** Selection is the main action; housing and payment have dedicated following pages. */
public class LivestockShopScreen extends FarmFolioScreen {
    private final CompoundTag offer;
    private List<CompoundTag> catalog;
    private String selected = "", search = "", category = "";
    private int page;
    private boolean available;

    public LivestockShopScreen(CompoundTag offer) {
        super(Component.translatable("livestock.stardewcraft.shop"), null);
        this.offer = offer;
        catalog =
                offer.getList("Catalog", Tag.TAG_COMPOUND).stream()
                        .map(t -> (CompoundTag) t)
                        .toList();
        if (!catalog.isEmpty()) selected = catalog.getFirst().getString("Species");
    }

    private List<CompoundTag> entries() {
        return catalog.stream()
                .filter(r -> category.isEmpty() || r.getString("Family").equals(category))
                .filter(r -> !available || r.getBoolean("Available"))
                .filter(
                        r ->
                                search.isBlank()
                                        || (r.getBoolean("Available")
                                                        ? LivestockPortrait.name(r).getString()
                                                        : "？？？")
                                                .toLowerCase(Locale.ROOT)
                                                .contains(search.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private CompoundTag selected() {
        return catalog.stream()
                .filter(r -> r.getString("Species").equals(selected))
                .findFirst()
                .orElse(null);
    }

    private int columns() {
        return Math.max(2, Math.min(4, (w - 52) / 106));
    }

    private int rows() {
        return h < 280 ? 1 : 2;
    }

    @Override
    protected void layout() {
        button(
                category.isEmpty()
                        ? ui("all_animals")
                        : Component.translatable(
                                "stardewcraft.manager.building."
                                        + new net.minecraft.resources.ResourceLocation(category)
                                                .getPath()),
                x + 20,
                y + 40,
                112,
                24,
                () ->
                        minecraft.setScreen(
                                new FarmCategoryScreen(
                                        this,
                                        catalog.stream()
                                                .map(r -> r.getString("Family"))
                                                .distinct()
                                                .toList(),
                                        s -> {
                                            category = s;
                                            page = 0;
                                            init();
                                        })));
        button(
                ui("available"),
                x + 140,
                y + 40,
                112,
                24,
                available ? "packet_selected" : "tab",
                null,
                () -> {
                    available = !available;
                    page = 0;
                    init();
                });
        if (w > 380) {
            var searchBox = field(ui("search"), search, x + w - 170, y + 36, 150, 64);
            searchBox.setHint(ui("search"));
            searchBox.setResponder(
                    s -> {
                        search = s;
                        page = 0;
                        rebuildCatalog();
                    });
        }
        buildTiles();
        button(Component.translatable("gui.back"), x + 16, y + h - 34, 84, 24, this::onClose);
        if (offer.hasUUID("Nonce"))
            button(
                    Component.translatable("livestock.stardewcraft.manage"),
                    x + 110,
                    y + h - 34,
                    112,
                    24,
                    () ->
                            PacketDistributor.sendToServer(
                                    new LivestockManagePayload(
                                            offer.getUUID("Nonce"),
                                            new UUID(0, 0),
                                            new UUID(0, 0),
                                            "open",
                                            "")));
    }

    private final List<Button> catalogButtons = new ArrayList<>();

    private void rebuildCatalog() {
        for (var b : catalogButtons) removeWidget(b);
        catalogButtons.clear();
        buildTiles();
    }

    private void buildTiles() {
        catalogButtons.clear();
        var entries = entries();
        int count = columns() * rows(),
                cw = (w - 48) / columns(),
                ch = Math.max(58, (h - 170) / rows());
        page = Math.clamp(page, 0, Math.max(0, (entries.size() - 1) / count));
        for (int i = page * count; i < Math.min(entries.size(), (page + 1) * count); i++) {
            var row = entries.get(i);
            int j = i - page * count,
                    px = x + 24 + j % columns() * cw,
                    py = y + 78 + j / columns() * ch;
            var text =
                    row.getBoolean("Available")
                            ? LivestockPortrait.name(row)
                            : Component.literal("？？？");
            catalogButtons.add(
                    tile(
                            text,
                            px,
                            py,
                            cw - 8,
                            ch - 8,
                            () -> {
                                selected = row.getString("Species");
                                rebuildCatalog();
                            },
                            (g, b) -> {
                                box(
                                        g,
                                        selected.equals(row.getString("Species"))
                                                ? "packet_selected"
                                                : "packet",
                                        b.getX(),
                                        b.getY(),
                                        b.getWidth(),
                                        b.getHeight());
                                LivestockPortrait.draw(
                                        g,
                                        row,
                                        b.getX() + 6,
                                        b.getY() + 5,
                                        b.getWidth() - 12,
                                        Math.max(16, b.getHeight() - 30),
                                        !row.getBoolean("Available"));
                                label(
                                        g,
                                        text,
                                        b.getX() + 8,
                                        b.getY() + b.getHeight() - 17,
                                        b.getWidth() - 16,
                                        INK);
                            }));
        }
        catalogButtons.add(
                arrow(
                        false,
                        x + w / 2 - 60,
                        y + h - 91,
                        page > 0,
                        () -> {
                            page--;
                            rebuildCatalog();
                        }));
        catalogButtons.add(
                arrow(
                        true,
                        x + w / 2 + 36,
                        y + h - 91,
                        (page + 1) * count < entries.size(),
                        () -> {
                            page++;
                            rebuildCatalog();
                        }));
        var next =
                button(
                        ui("choose_home"),
                        x + w - 158,
                        y + h - 34,
                        142,
                        24,
                        () -> {
                            var row = selected();
                            if (row != null) choose(row);
                        });
        next.active =
                !offer.getBoolean("ClientPending")
                        && selected() != null
                        && selected().getBoolean("Available");
        catalogButtons.add(next);
    }

    protected void choose(CompoundTag row) {
        minecraft.setScreen(
                new BuildingChoiceScreen(
                        this,
                        homes(row),
                        row,
                        home ->
                                minecraft.setScreen(
                                        new LivestockPurchaseScreen(this, offer, row, home))));
    }

    List<CompoundTag> homes(CompoundTag type) {
        return offer.getList("Homes", 10).stream()
                .map(t -> (CompoundTag) t)
                .filter(
                        r ->
                                com.stardew.craft.animal.runtime.LivestockHomes.offered(r,type))
                .toList();
    }

    public void refresh(CompoundTag snapshot) {
        offer.merge(snapshot);
        catalog = offer.getList("Catalog", 10).stream().map(t -> (CompoundTag) t).toList();
    }

    public void result(CompoundTag snapshot) {
        if (!offer.hasUUID("Nonce")
                || !snapshot.hasUUID("RequestNonce")
                || !offer.getUUID("Nonce").equals(snapshot.getUUID("RequestNonce"))) return;
        offer.putBoolean("ClientPending", false);
        if (snapshot.getString("Result").isEmpty()) onClose();
        else {
            refresh(snapshot);
            init();
        }
    }

    @Override
    protected void paint(GuiGraphics g) {
        money(g, offer.getInt("Money"), x + w - 126, y + 8, LIGHT);
        paper(g, x + 16, y + 72, w - 32, h - 118);
        if (entries().isEmpty())
            paragraph(g, ui("no_results"), x + 36, y + 98, w - 72, y + h - 112, MUTED);
        var row = selected();
        if (row != null) {
            label(
                    g,
                    row.getBoolean("Available")
                            ? LivestockPortrait.name(row)
                            : Component.literal("？？？"),
                    x + 30,
                    y + h - 60,
                    w - 180,
                    INK);
            money(g, row.getInt("Price"), x + w - 130, y + h - 68, INK);
            if (!row.getBoolean("Available"))
                label(
                        g,
                        Component.translatable(
                                row.getString("ReasonKey").isEmpty()
                                        ? "livestock.stardewcraft." + row.getString("Reason")
                                        : row.getString("ReasonKey")),
                        x + 30,
                        y + h - 78,
                        Math.max(80, w / 2 - 100),
                        RED);
        }
        int count = columns() * rows();
        label(
                g,
                Component.literal(
                        (page + 1) + " / " + Math.max(1, (entries().size() + count - 1) / count)),
                x + w / 2 - 17,
                y + h - 83,
                50,
                MUTED);
    }
}
