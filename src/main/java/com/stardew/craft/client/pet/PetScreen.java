package com.stardew.craft.client.pet;

import com.stardew.craft.client.gui.FarmFolioScreen;
import com.stardew.craft.client.gui.FarmRenameScreen;
import com.stardew.craft.pet.PetActionPayload;
import com.stardew.craft.pet.PetVariant;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
@com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber(modid = com.stardew.craft.StardewCraft.MODID, value = Dist.CLIENT)
public final class PetScreen extends FarmFolioScreen implements com.stardew.craft.client.gui.common.StardewGuiContentSize {
    private static CompoundTag deferredInitial;
    @Override public int minimumCanvasWidth() { return preferredWidth() + 16; }
    @Override public int minimumCanvasHeight() { return preferredHeight() + 20; }
    private final CompoundTag offer;
    private final List<CompoundTag> pets, bowls;
    private UUID selected;
    private int page, bowlPage;
    private boolean choosingBowl, pending, confirmingBowlRemoval;
    public PetScreen(CompoundTag offer) {
        super(offer.getString("Kind").equals("bowl") ? Component.translatable("block.stardewcraft.pet_bowl_" + offer.getString("BowlStyle")) : tr(offer.getString("Kind").equals("initial") ? "initial_title" : offer.getString("Kind").equals("adopt") ? "adopt" : offer.getString("Kind").equals("bowls") ? "buy_bowl" : "manage"), null); this.offer = offer;
        pets = offer.getList("Pets", 10).stream().map(t -> (CompoundTag) t).toList();
        bowls = offer.getList("Bowls", 10).stream().map(t -> (CompoundTag) t).toList();
        selected = offer.hasUUID("Selected") ? offer.getUUID("Selected") : pets.isEmpty() ? null : com.stardew.craft.port.PortJava.getFirst(pets).getUUID("Id");
    }
    public static Component tr(String key, Object... args) { return Component.translatable("pet.stardewcraft." + key, args); }
    public static ResourceLocation icon(PetVariant variant) { return variant.available() ? variant.breed().icon() : new ResourceLocation("stardewcraft:textures/gui/pet/pet_license.png"); }
    public static void receive(CompoundTag offer) {
        var mc = Minecraft.getInstance();
        if (offer.hasUUID("Reply") && (!(mc.screen instanceof PetScreen previous) || !previous.pending || !previous.offer.getUUID("Nonce").equals(offer.getUUID("Reply")))) return;
        if (offer.getString("Kind").equals("bowl_done") || offer.getString("Kind").equals("adopt_done")) { mc.setScreen(null); return; }
        if (offer.getString("Kind").equals("initial_done")) { deferredInitial = null; mc.setScreen(null); return; }
        if (offer.getString("Kind").equals("initial") && !offer.hasUUID("Reply") && mc.screen != null) { deferredInitial = offer; return; }
        if (offer.getString("Kind").equals("initial")) deferredInitial = null;
        var next = new PetScreen(offer);
        if (mc.screen instanceof PetScreen previous) next.page = previous.page;
        mc.setScreen(next);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void tick(com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (deferredInitial != null && mc.player != null && mc.level != null && mc.screen == null) {
            var offer = deferredInitial; deferredInitial = null; receive(offer);
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { deferredInitial = null; }
    private boolean initial() { return offer.getString("Kind").equals("initial"); }
    private CompoundTag pet() { return pets.stream().filter(p -> p.getUUID("Id").equals(selected)).findFirst().orElse(null); }
    private void send(String action, String value, BlockPos bowl) {
        if (pending) return;
        pending = true;
        PacketDistributor.sendToServer(new PetActionPayload(offer.getUUID("Nonce"), selected == null ? new UUID(0, 0) : selected, action, value, bowl));
        if (action.equals("remove")) onClose(); else init();
    }
    @Override protected void layout() {
        button(initial() ? tr("initial_later") : Component.translatable("gui.done"), x + 16, y + h - 38, initial() ? 164 : 96, 26, this::onClose).active = !pending;
        if (offer.getString("Kind").equals("bowl")) { bowlLayout(); return; }
        if (initial() || offer.getString("Kind").equals("adopt")) { adoption(); return; }
        if (offer.getString("Kind").equals("bowls")) {
            int i = 0;
            for (String style : List.of("wood", "stone", "hay")) {
                var item = switch (style) { case "stone" -> com.stardew.craft.item.ModItems.PET_BOWL_STONE.get(); case "hay" -> com.stardew.craft.item.ModItems.PET_BOWL_HAY.get(); default -> com.stardew.craft.item.ModItems.PET_BOWL_WOOD.get(); };
                int px = x + 18 + i++ * 172;
                tile(item.getDescription(), px, y + 84, 164, 150, () -> send("buy_bowl", style, BlockPos.ZERO), (g, b) -> {
                    box(g, "packet", px, y + 84, 164, 150);
                    item(g, new net.minecraft.world.item.ItemStack(item), px + 60, y + 110, 2);
                    label(g, item.getDescription(), px + 10, y + 159, 144, INK);
                    money(g, 5000, px + 24, y + 190, INK);
                }).active = !pending && offer.getInt("Money") >= 5000 && offer.getInt("Hardwood") >= 25;
            }
            return;
        }
        var pet = pet();
        if (offer.getString("Kind").equals("remove")) {
            button(tr("send_away"), x + w - 190, y + h - 38, 174, 26, () -> send("remove", "", BlockPos.ZERO)).active = pet != null && !pending;
            return;
        }
        int pages = Math.max(1, (pets.size() + 3) / 4); page = Math.min(page, pages - 1);
        for (int i = page * 4; i < Math.min(pets.size(), (page + 1) * 4); i++) {
            var entry = pets.get(i); int yy = y + 40 + (i % 4) * 54;
            tile(Component.literal(entry.getString("Name")), x + 16, yy, 176, 48, () -> { selected = entry.getUUID("Id"); choosingBowl = false; init(); }, (g, b) -> {
                box(g, entry.getUUID("Id").equals(selected) ? "packet_selected" : "packet", b.getX(), b.getY(), b.getWidth(), b.getHeight());
                image(g, icon(PetVariant.fromSaved(entry.getString("Variant"))), 16, 16, b.getX() + 8, b.getY() + 8, 32, 32, false);
                label(g, Component.literal(entry.getString("Name")), b.getX() + 48, b.getY() + 18, 118, INK);
            }).active = !pending;
        }
        arrow(false, x + 16, y + 264, page > 0 && !pending, () -> { page--; init(); });
        arrow(true, x + 168, y + 264, page + 1 < pages && !pending, () -> { page++; init(); });
        if (pet == null || !pet.getBoolean("Available")) return;
        if (choosingBowl) {
            var available = bowls.stream().filter(b -> !b.hasUUID("Pet") || b.getUUID("Pet").equals(selected)).toList();
            // Four at a time; position order is stable across server snapshots.
            int bowlPages = Math.max(1, (available.size() + 3) / 4); bowlPage = Math.min(bowlPage, bowlPages - 1);
            for (int i = bowlPage * 4; i < Math.min((bowlPage + 1) * 4, available.size()); i++) {
                var bowl = available.get(i); var pos = BlockPos.of(bowl.getLong("Position"));
                button(tr("bowl_position", pos.getX(), pos.getY(), pos.getZ()), x + 208, y + 86 + i % 4 * 38, w - 224, 30,
                        () -> send("bind", "", pos)).active = !pending;
            }
            arrow(false, x + 208, y + 248, bowlPage > 0 && !pending, () -> { bowlPage--; init(); });
            arrow(true, x + w - 40, y + 248, bowlPage + 1 < bowlPages && !pending, () -> { bowlPage++; init(); });
            button(Component.translatable("gui.back"), x + 242, y + 246, w - 292, 28, () -> { choosingBowl = false; init(); });
        } else {
            button(tr("rename"), x + 208, y + 198, 146, 28, () -> minecraft.setScreen(new FarmRenameScreen(this, tr("name"), pet.getString("Name"), 24, tr("name_required"), value -> send("rename", value, BlockPos.ZERO)))).active = !pending;
            button(tr("assign_bowl"), x + 366, y + 198, w - 382, 28, () -> { choosingBowl = true; init(); }).active = !pending;
            if (pet.getBoolean("HasHat")) button(tr("remove_hat"), x + 208, y + 238, w - 224, 28, () -> send("unhat", "", BlockPos.ZERO)).active = !pending;
        }
    }
    private net.minecraft.world.item.ItemStack bowlItem() {
        return new net.minecraft.world.item.ItemStack(switch (offer.getString("BowlStyle")) {
            case "stone" -> com.stardew.craft.item.ModItems.PET_BOWL_STONE.get();
            case "hay" -> com.stardew.craft.item.ModItems.PET_BOWL_HAY.get();
            default -> com.stardew.craft.item.ModItems.PET_BOWL_WOOD.get();
        });
    }
    private void bowlLayout() {
        if (confirmingBowlRemoval) {
            button(Component.translatable("gui.back"), x + 208, y + h - 38, 146, 26, () -> { confirmingBowlRemoval = false; init(); }).active = !pending;
            button(Component.translatable("building.stardewcraft.demolish"), x + 366, y + h - 38, w - 382, 26,
                    () -> send("bowl_demolish", "", BlockPos.ZERO)).active = !pending;
            return;
        }
        boolean initialChoice = offer.getBoolean("CanChooseInitial");
        button(tr(initialChoice ? "initial_title" : "manage"), x + 208, y + 178, w - 224, 28,
                () -> send(initialChoice ? "bowl_initial" : "bowl_manage", "", BlockPos.ZERO)).active = !pending && (initialChoice || !pets.isEmpty());
        button(Component.translatable("building.stardewcraft.move_building"), x + 208, y + 218, 146, 28,
                () -> send("bowl_move", "", BlockPos.ZERO)).active = !pending;
        button(Component.translatable("building.stardewcraft.demolish"), x + 366, y + 218, w - 382, 28,
                () -> { confirmingBowlRemoval = true; init(); }).active = !pending;
    }
    private void paintBowl(GuiGraphics g) {
        item(g, bowlItem(), x + 54, y + 78, 5);
        if (confirmingBowlRemoval) { paragraph(g, tr("bowl_demolish_confirm"), x + 208, y + 64, w - 228, y + h - 64, CANVAS_INK); return; }
        var pos = BlockPos.of(offer.getLong("BowlPosition"));
        var bowl = bowls.stream().filter(row -> row.getLong("Position") == pos.asLong()).findFirst().orElse(new CompoundTag());
        label(g, tr(bowl.getBoolean("Full") ? "bowl_watered" : "bowl_empty"), x + 208, y + 58, w - 228, CANVAS_INK);
        var occupant = bowl.hasUUID("Pet") ? pets.stream().filter(row -> row.getUUID("Id").equals(bowl.getUUID("Pet"))).findFirst().orElse(null) : null;
        if (occupant != null) {
            image(g, icon(PetVariant.fromSaved(occupant.getString("Variant"))), 16, 16, x + 208, y + 90, 32, 32, false);
            label(g, Component.literal(occupant.getString("Name")), x + 250, y + 100, w - 270, CANVAS_INK);
        } else label(g, tr("bowl_unassigned"), x + 208, y + 100, w - 228, CANVAS_MUTED);
        label(g, tr("bowl_position", pos.getX(), pos.getY(), pos.getZ()), x + 208, y + 140, w - 228, CANVAS_MUTED);
    }
    private void adoption() {
        int width = (w - 44) / 4;
        var window = com.stardew.craft.pet.PetChoicePage.of(initial(), page);
        page = window.page(); int pages = window.pages();
        for (int i = 0; i < window.entries().size(); i++) {
            var variant = window.entries().get(i);
            int px = x + 16 + i % 4 * (width + 4), py = y + 66 + i / 4 * 72;
            boolean allowed = !pending && (initial() || offer.getBoolean("Unlocked") && offer.getInt("Money") >= variant.price());
            var tile = tile(variant.label(), px, py, width, 68,
                    () -> minecraft.setScreen(new FarmRenameScreen(this, tr("name"), "", 24, tr("name_required"), name -> send(initial() ? "initial" : "adopt", PetActionPayload.selection(variant.id(), name), BlockPos.ZERO))), (g, b) -> {
                        box(g, allowed ? "packet" : "tab", px, py, width, 68);
                        image(g, icon(variant), 16, 16, px + 7, py + 5, 32, 32, false);
                        label(g, variant.label(), px + 44, py + 15, width - 49, INK);
                        if (!initial()) money(g, variant.price(), px + 8, py + 43, offer.getInt("Money") >= variant.price() ? INK : RED);
                    });
            tile.active = allowed;
            if (!allowed && !initial()) disabledReason(tile, tr(!offer.getBoolean("Unlocked") ? "locked" : "money_required"));
        }
        if (pages > 1) {
            arrow(false, x + w - 84, y + h - 36, page > 0 && !pending, () -> { page--; init(); });
            arrow(true, x + w - 40, y + h - 36, page + 1 < pages && !pending, () -> { page++; init(); });
        }
    }
    @Override protected void paint(GuiGraphics g) {
        if (offer.getString("Kind").equals("bowl")) { paintBowl(g); return; }
        if (initial()) { label(g, tr("initial_hint"), x + 18, y + 36, w - 36, CANVAS_INK); return; }
        if (offer.getString("Kind").equals("bowls")) { paragraph(g, tr("bowl_cost"), x + 20, y + 42, w - 40, y + 80, CANVAS_MUTED); return; }
        if (offer.getString("Kind").equals("adopt")) {
            label(g, tr(!offer.getBoolean("Unlocked") ? "locked" : bowls.stream().noneMatch(b -> !b.hasUUID("Pet")) ? "adoption_no_bowl_hint" : "adoption_hint"), x + 18, y + 36, w - 36, CANVAS_INK); return;
        }
        var pet = pet();
        if (pet == null) { paragraph(g, tr("no_pets"), x + 210, y + 50, w - 228, y + 200, CANVAS_MUTED); return; }
        if (offer.getString("Kind").equals("remove")) {
            PetVariant.find(pet.getString("Variant")).ifPresent(v -> image(g, icon(v), 16, 16, x + 26, y + 72, 80, 80, false));
            paragraph(g, tr("remove_question", pet.getString("Name")), x + 130, y + 66, w - 152, y + h - 60, CANVAS_INK); return;
        }
        label(g, Component.literal(pet.getString("Name")), x + 212, y + 48, w - 232, CANVAS_INK);
        if (!pet.getBoolean("Available")) { paragraph(g, tr("unavailable"), x + 212, y + 82, w - 232, y + 190, CANVAS_MUTED); return; }
        if (choosingBowl) { label(g, tr("choose_bowl"), x + 212, y + 66, w - 232, CANVAS_MUTED); return; }
        label(g, tr("friendship", pet.getInt("Friendship")), x + 212, y + 82, w - 232, CANVAS_INK);
        label(g, tr(pet.getBoolean("Petted") ? "petted" : "not_petted"), x + 212, y + 112, w - 232, CANVAS_MUTED);
        if (pet.contains("Bowl")) { var pos = BlockPos.of(pet.getLong("Bowl")); label(g, tr("bowl_position", pos.getX(), pos.getY(), pos.getZ()), x + 212, y + 142, w - 232, CANVAS_MUTED); }
        else label(g, tr("no_bowl"), x + 212, y + 142, w - 232, CANVAS_RED);
        if (pet.contains("Position")) { var pos = BlockPos.of(pet.getLong("Position")); label(g, tr("position", pos.getX(), pos.getY(), pos.getZ()), x + 212, y + 170, w - 232, CANVAS_MUTED); }
    }
}
