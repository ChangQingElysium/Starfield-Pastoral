package com.stardew.craft.client.gui;

import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.client.animal.LivestockPortrait;
import com.stardew.craft.client.building.BuildingPlacementPreview;
import com.stardew.craft.client.building.RobinCatalogArt;
import com.stardew.craft.network.payload.BuildingLedgerActionPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.Locale;

/** Prefab residents, self-build requirements and construction each own their page layout. */
public final class BuildingLedgerScreen extends FarmFolioScreen implements com.stardew.craft.client.gui.common.StardewGuiContentSize {
    private final CompoundTag data;
    private final BuildingRecord building;
    @Override public int minimumCanvasWidth() { return preferredWidth()+16; }
    @Override public int minimumCanvasHeight() { return preferredHeight()+20; }
    private boolean pending;
    private int page;

    private BuildingLedgerScreen(CompoundTag data, Screen parent) {
        super(BuildingRecord.load(data).title(), parent);
        this.data = data;
        building = BuildingRecord.load(data);
    }

    public static void show(CompoundTag data) {
        var mc = Minecraft.getInstance();
        Screen current = mc.screen;
        while (current instanceof FarmFolioScreen folio
                && !(current instanceof BuildingLedgerScreen)) current = folio.parentScreen();
        if(data.hasUUID("ReplySession") && (!(current instanceof BuildingLedgerScreen ledger)
                || !ledger.acceptsReply(data.getUUID("ReplySession"))))return;
        if (data.getBoolean("Close")) {
            if (current instanceof BuildingLedgerScreen) mc.setScreen(null);
            return;
        }
        mc.setScreen(
                new BuildingLedgerScreen(
                        data, current instanceof BuildingLedgerScreen old ? old.parent : null));
    }

    public boolean acceptsReply(java.util.UUID session) {
        return pending && data.getUUID("Session").equals(session);
    }
    public void returnFromAnimals() {
        pending=false;
        send("refresh");
    }

    private Component tr(String key, Object... args) {
        return Component.translatable("building.stardewcraft." + key, args);
    }

    private void send(String action) {
        send(action, "");
    }

    private void send(String action, String value) {
        if (pending) return;
        pending = true;
        init();
        PacketDistributor.sendToServer(
                new BuildingLedgerActionPayload(data.getUUID("Session"), action, value));
    }

    private boolean pond() { return com.stardew.craft.building.runtime.FishPondPrefabs.isPond(building.family()); }
    private boolean silo() { return com.stardew.craft.building.runtime.UtilityBuildings.supported(building.family()); }
    private boolean greenhouse() { return data.getBoolean("GreenhouseOnly"); }

    private boolean self() {
        return building.mode() == BuildingRecord.Mode.SELF_BUILT;
    }

    private boolean working() {
        return building.phase() == BuildingRecord.Phase.CONSTRUCTING
                || building.phase() == BuildingRecord.Phase.UPGRADING;
    }

    @Override
    protected int preferredWidth() {
        return working() || greenhouse() ? 466 : 548;
    }

    @Override
    protected int preferredHeight() {
        return working() || greenhouse() ? 281 : 340;
    }

    @Override
    protected void layout() {
        button(Component.translatable("gui.back"), x + 16, y + h - 34, 86, 24, this::onClose);
        if (greenhouse()) {
            int width = Math.min(190, w - 64);
            button(tr("move_building"), x + (w - width) / 2, y + h - 78, width, 30,
                            "button", "move", () -> send("move"))
                    .active = !pending && data.getBoolean("CanMove");
            return;
        }
        button(
                                tr("rename"),
                                x + w - 86,
                                y + 9,
                                70,
                                24,
                                "tab",
                                "pencil",
                                () ->
                                        minecraft.setScreen(
                                                new FarmRenameScreen(
                                                        this,
                                                        tr("rename"),
                                                        title.getString(),
                                                        s -> send("rename", s))))
                        .active =
                !pending;
        if (working()) {
            button(
                    tr("toggle_range"),
                    x + w - 186,
                    y + h - 34,
                    170,
                    24,
                    () -> {
                        BuildingPlacementPreview.toggleRange(
                                building.claim().min(), building.claim().maxExclusive());
                        onClose();
                    });
            if (building.phase() == BuildingRecord.Phase.UPGRADING)
                button(
                                        Component.translatable("livestock.stardewcraft.manage"),
                                        x + 16,
                                        y + h - 72,
                                        156,
                                        26,
                                        "button",
                                        "pet",
                                        () -> send("animals"))
                                .active =
                        !pending;
            return;
        }
        int side = Math.min(158, w / 3), px = x + w - side - 16;
        button(tr("move_building"), px, y + 52, side, 30, "button", "move", () -> send("move"))
                        .active =
                !pending && data.getBoolean("CanMove");
        button(
                                tr("demolish"),
                                px,
                                y + 90,
                                side,
                                30,
                                () -> minecraft.setScreen(new DemolitionScreen()))
                        .active =
                !pending && data.getBoolean("CanDemolish");
        if (pond()) return;
        if (silo()) {
            if(self()) {
                button(tr("toggle_range"),px,y+128,side,30,()->{
                    BuildingPlacementPreview.toggleRange(building.claim().min(),building.claim().maxExclusive());onClose();
                });
                if(building.phase()==BuildingRecord.Phase.WAITING)
                    button(tr("accept_build"),px,y+h-74,side,30,"button","home",()->send("accept")).active=!pending && data.getBoolean("CanAccept");
            }
            button(Component.translatable("gui.stardew_craft.fish_pond_manager.refresh"),px,y+166,side,30,()->send("refresh")).active=!pending;
            return;
        }
        if (self()) {
            int count = Math.max(1, (h - 175) / 27);
            int total = data.getList("Facilities", 10).size();
            if (total > count) {
                arrow(
                        false,
                        x + 28,
                        y + h - 72,
                        page > 0,
                        () -> {
                            page--;
                            init();
                        });
                arrow(
                        true,
                        x + 96,
                        y + h - 72,
                        (page + 1) * count < total,
                        () -> {
                            page++;
                            init();
                        });
            }
            button(
                    tr("toggle_range"),
                    px,
                    y + 128,
                    side,
                    30,
                    () -> {
                        BuildingPlacementPreview.toggleRange(
                                building.claim().min(), building.claim().maxExclusive());
                        onClose();
                    });
            button(
                                    tr(building.tier() == 0 ? "accept_build" : "accept_upgrade"),
                                    px,
                                    y + h - 74,
                                    side,
                                    30,
                                    "button",
                                    "home",
                                    () -> send("accept"))
                            .active =
                    !pending && data.getBoolean("CanAccept");
            button(
                                    Component.translatable("livestock.stardewcraft.manage"),
                                    x + 112,
                                    y + h - 34,
                                    Math.max(80, w - 128),
                                    24,
                                    "tab",
                                    "pet",
                                    () -> send("animals"))
                            .active =
                    !pending;
        } else {
            if (data.getBoolean("CanUpgrade"))
                button(
                                        tr("preview_upgrade"),
                                        px,
                                        y + 128,
                                        side,
                                        30,
                                        () -> {
                                            if (BuildingPlacementPreview.upgradeVisible(
                                                    building.id())) {
                                                BuildingPlacementPreview.closeRanges();
                                                onClose();
                                            } else send("preview");
                                        })
                                .active =
                        !pending;
            int count = Math.max(1, (h - 150) / 49);
            var animals = data.getList("Animals", 10);
            for (int i = page * count; i < Math.min(animals.size(), (page + 1) * count); i++) {
                var a = animals.getCompound(i);
                int yy = y + 84 + (i - page * count) * 49;
                tile(
                                        Component.literal(a.getString("Name")),
                                        x + 24,
                                        yy,
                                        w - side - 62,
                                        45,
                                        () -> send("animals", a.getUUID("Id").toString()),
                                        (g, b) -> {
                                            LivestockPortrait.draw(
                                                    g, a, b.getX(), b.getY() + 5, 40, 32, false);
                                            label(
                                                    g,
                                                    Component.literal(a.getString("Name")),
                                                    b.getX() + 46,
                                                    b.getY() + 5,
                                                    b.getWidth() - 54,
                                                    INK);
                                            icon(g, "feed", b.getX() + 46, b.getY() + 23, 16);
                                            icon(g, "pet", b.getX() + 66, b.getY() + 23, 16);
                                            label(
                                                    g,
                                                    ui(a.getInt("Fullness") >= 200
                                                                    ? "fed"
                                                                    : "hungry")
                                                            .copy()
                                                            .append(" · ")
                                                            .append(
                                                                    ui(
                                                                            a.getBoolean("Petted")
                                                                                    ? "petted"
                                                                                    : "unpetted")),
                                                    b.getX() + 88,
                                                    b.getY() + 27,
                                                    b.getWidth() - 90,
                                                    MUTED);
                                            rule(g, b.getX(), b.getY() + 44, b.getWidth());
                                        })
                                .active =
                        !pending;
            }
            arrow(
                    false,
                    x + 30,
                    y + h - 68,
                    page > 0,
                    () -> {
                        page--;
                        init();
                    });
            arrow(
                    true,
                    x + 100,
                    y + h - 68,
                    (page + 1) * count < animals.size(),
                    () -> {
                        page++;
                        init();
                    });
            button(
                                    Component.translatable(
                                            "building.stardewcraft.grazing_button."
                                                    + (data.getBoolean("Outdoors")
                                                            ? "open"
                                                            : "closed")),
                                    px,
                                    y + h - 74,
                                    side,
                                    30,
                                    "tab",
                                    "home",
                                    () -> send("grazing"))
                            .active =
                    !pending;
        }
    }

    @Override
    protected void paint(GuiGraphics g) {
        if (greenhouse()) {
            paper(g, x + 16, y + 42, w - 32, h - 96);
            item(g, new ItemStack(com.stardew.craft.item.ModItems.GREENHOUSE_BLUEPRINT.get()),
                    x + 32, y + 62, 3);
            label(g, building.title(), x + 92, y + 65, w - 124, INK);
            paragraph(g, tr("greenhouse_move_only"), x + 92, y + 91,
                    w - 124, y + h - 90, MUTED);
            return;
        }
        if (working()) {
            paper(g, x + 16, y + 42, w - 32, h - 130);
            int art = Math.min(126, w / 3);
            RobinCatalogArt.draw(
                    g,
                    building.family().toString()
                            + (building.tier() > 1 ? "_upgrade_" + building.tier() : ""),
                    x + 26,
                    y + 52,
                    art,
                    Math.max(42, h - 152));
            label(
                    g,
                    tr("phase." + building.phase().name().toLowerCase(Locale.ROOT)),
                    x + art + 46,
                    y + 60,
                    w - art - 76,
                    INK);
            paragraph(
                    g,
                    tr("days_remaining", data.getInt("Days")),
                    x + art + 46,
                    y + 88,
                    w - art - 76,
                    y + h - 96,
                    MUTED);
            return;
        }
        int side = Math.min(158, w / 3), pw = w - side - 46;
        paper(g, x + 16, y + 42, pw, h - 86);
        label(g, tr(self() ? "mode_self" : "mode_prefab"), x + 28, y + 55, pw - 24, INK);
        if (pond()) {
            var fish = ResourceLocation.tryParse(data.getString("PondFish"));
            if(fish!=null && BuiltInRegistries.ITEM.containsKey(fish)) {
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(fish));
                item(g,stack,x+28,y+84,2);
                label(g,stack.getHoverName(),x+72,y+85,pw-70,INK);
                label(g,Component.translatable("gui.stardew_craft.fish_pond_manager.population",data.getInt("PondPopulation"),data.getInt("PondCapacity")),x+28,y+135,pw-24,INK);
            } else paragraph(g,Component.translatable("gui.stardew_craft.fish_pond_manager.status_no_fish"),x+28,y+86,pw-24,y+140,MUTED);
            paragraph(g,Component.translatable("stardewcraft.robin.blueprint.fish_pond.desc"),x+28,y+165,pw-24,y+h-60,MUTED);
            return;
        }
        if (silo()) {
            int stored=data.getInt("Hay"), capacity=data.getInt("HayCapacity");
            item(g,new ItemStack(com.stardew.craft.item.ModItems.HAY.get()),x+28,y+82,2);
            label(g,Component.translatable("livestock.stardewcraft.silo",stored,capacity),x+72,y+91,pw-70,INK);
            SiloArt.storage(g,x+28,y+121,pw-24,stored,capacity);
            paragraph(g,Component.translatable("gui.stardew_craft.silo_manager.shared_hint"),x+28,y+144,pw-24,y+179,MUTED);
            rule(g,x+28,y+184,pw-24);
            if(self()) {
                item(g,new ItemStack(net.minecraft.world.level.block.Blocks.BRICKS),x+28,y+195,1);
                label(g,Component.literal(data.getInt("Bricks")+" / 39"),x+53,y+199,pw-70,data.getInt("Bricks")==39?INK:RED);
                paragraph(g,Component.translatable("gui.stardew_craft.silo_manager.build_hint"),x+28,y+222,pw-24,y+h-57,MUTED);
            } else paragraph(g,Component.translatable("building.stardewcraft.silo_storage_hint"),x+28,y+199,pw-24,y+h-57,MUTED);
            return;
        }
        if (self()) {
            paragraph(g, ui("facilities_in_range"), x + 28, y + 77, pw - 24, y + 111, MUTED);
            var facilities = data.getList("Facilities", 10);
            int count = Math.max(1, (h - 175) / 27);
            int yy = y + 114;
            for (int i = page * count; i < Math.min(facilities.size(), (page + 1) * count); i++) {
                var row = facilities.getCompound(i);
                var id = ResourceLocation.tryParse(row.getString("Item"));
                if (id == null) continue;
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
                item(g, stack, x + 28, yy, 1);
                label(g, stack.getHoverName(), x + 52, yy + 4, pw - 118, INK);
                label(
                        g,
                        Component.literal(row.getInt("Have") + " / " + row.getInt("Need")),
                        x + pw - 54,
                        yy + 4,
                        56,
                        row.getInt("Have") >= row.getInt("Need") ? INK : RED);
                yy += 27;
            }
        } else {
            label(
                    g,
                    Component.translatable(
                            "livestock.stardewcraft.occupancy",
                            data.getList("Animals", 10).size(),
                            data.getInt("Capacity")),
                    x + 28,
                    y + 71,
                    pw - 24,
                    MUTED);
            if (data.getList("Animals", 10).isEmpty())
                paragraph(g, tr("animals_empty"), x + 28, y + 109, pw - 24, y + h - 80, MUTED);
        }
    }

    private final class DemolitionScreen extends FarmFolioScreen implements com.stardew.craft.client.gui.common.StardewGuiContentSize {
        @Override public int minimumCanvasWidth() { return preferredWidth()+16; }
        @Override public int minimumCanvasHeight() { return preferredHeight()+20; }
        DemolitionScreen() {
            super(tr("demolish"), BuildingLedgerScreen.this);
        }

        @Override
        protected int preferredWidth() {
            return 504;
        }

        @Override
        protected int preferredHeight() {
            return 316;
        }

        @Override
        protected void layout() {
            button(
                    Component.translatable("gui.cancel"),
                    x + 16,
                    y + h - 36,
                    112,
                    24,
                    this::onClose);
            boolean occupied = !data.getList("Animals", 10).isEmpty();
            button(
                                    tr("demolish"),
                                    x + w - 174,
                                    y + h - 36,
                                    158,
                                    24,
                                    () -> {
                                        minecraft.setScreen(parent);
                                        send("demolish");
                                    })
                            .active =
                    !occupied;
            if (occupied)
                button(
                        Component.translatable("livestock.stardewcraft.manage"),
                        x + 30,
                        y + h - 92,
                        w - 60,
                        30,
                        "button",
                        "move",
                        () -> {
                            minecraft.setScreen(parent);
                            send("animals");
                        });
        }

        @Override
        protected void paint(GuiGraphics g) {
            paper(g, x + 16, y + 38, w - 32, h - 88);
            RobinCatalogArt.draw(
                    g, building.family().toString(), x + 30, y + 56, 104, Math.max(48, h - 186));
            label(g, building.title(), x + 156, y + 56, w - 188, INK);
            paragraph(
                    g,
                    tr(self() ? "demolish_self_confirm" : "demolish_prefab_confirm"),
                    x + 156,
                    y + 83,
                    w - 188,
                    y + h - 112,
                    MUTED);
            if (!data.getList("Animals", 10).isEmpty())
                label(g, tr("demolish_animals"), x + 30, y + h - 116, w - 60, RED);
        }
    }
}
