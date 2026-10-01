package com.stardew.craft.client.animal;

import com.stardew.craft.animal.runtime.LivestockPurchasePayload;
import com.stardew.craft.client.gui.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

public final class LivestockPurchaseScreen extends FarmFolioScreen {
    private final CompoundTag offer, type;
    private CompoundTag home;
    private String name = "", failure = "";
    private EditBox input;
    private Button confirm, homeButton, randomButton;
    private java.util.UUID submittedNonce;
    private boolean pending;

    public LivestockPurchaseScreen(
            LivestockShopScreen parent, CompoundTag offer, CompoundTag type, CompoundTag home) {
        super(ui("purchase_name"), parent);
        this.offer = offer;
        this.type = type;
        this.home = home;
    }

    @Override
    protected int preferredWidth() {
        return 430;
    }

    @Override
    protected int preferredHeight() {
        return 300;
    }

    @Override
    protected void layout() {
        input =
                field(
                        Component.translatable("livestock.stardewcraft.name_required"),
                        name,
                        x + 30,
                        y + h - 128,
                        w - 108,
                        32);
        input.setResponder(
                s -> {
                    name = s;
                    update();
                });
        randomButton =
                tile(
                        ui("random_name"),
                        x + w - 66,
                        y + h - 128,
                        34,
                        32,
                        () -> input.setValue(FarmAnimalNames.next()),
                        (g, b) -> {
                            box(g, "button", b.getX(), b.getY(), 34, 32);
                            glyph(g, "dice", b.getX() + 9, b.getY() + 8);
                        });
        homeButton =
                button(
                        BuildingChoiceScreen.homeName(home),
                        x + 30,
                        y + h - 86,
                        w - 60,
                        26,
                        "tab",
                        "home",
                        () ->
                                minecraft.setScreen(
                                        new BuildingChoiceScreen(
                                                this,
                                                ((LivestockShopScreen) parent).homes(type),
                                                type,
                                                r -> {
                                                    home = r;
                                                    init();
                                                })));
        button(Component.translatable("gui.back"), x + 16, y + h - 34, 100, 24, this::onClose);
        confirm =
                button(
                        ui("purchase"),
                        x + w - 164,
                        y + h - 34,
                        148,
                        24,
                        () -> {
                            if (pending) return;
                            pending = true;
                            submittedNonce = offer.getUUID("Nonce");
                            offer.putBoolean("ClientPending", true);
                            update();
                            PacketDistributor.sendToServer(
                                    new LivestockPurchasePayload(
                                            offer.getUUID("Nonce"),
                                            home.getUUID("Id"),
                                            home.getLong("Revision"),
                                            name,
                                            type.getString("Species")));
                        });
        update();
        setInitialFocus(input);
    }

    private void update() {
        if (input != null) {
            input.setEditable(!pending);
            input.active = !pending;
        }
        if (homeButton != null) homeButton.active = !pending;
        if (randomButton != null) randomButton.active = !pending;

        if (confirm != null)
            confirm.active =
                    !pending
                            && FarmRenameScreen.valid(name)
                            && home.getInt("Used") < home.getInt("Capacity")
                            && offer.getInt("Money") >= type.getInt("Price");
    }

    public void result(CompoundTag snapshot) {
        if (!pending
                || !snapshot.hasUUID("RequestNonce")
                || !snapshot.getUUID("RequestNonce").equals(submittedNonce)) return;
        offer.putBoolean("ClientPending", false);
        String code = snapshot.getString("Result");
        if (snapshot.contains("Homes")) {
            ((LivestockShopScreen) parent).refresh(snapshot);
            var previous = home;
            home =
                    offer.getList("Homes", 10).stream()
                            .map(t -> (CompoundTag) t)
                            .filter(r -> r.getUUID("Id").equals(previous.getUUID("Id")))
                            .findFirst()
                            .orElseGet(
                                    () -> {
                                        var unavailable = previous.copy();
                                        unavailable.putInt("Capacity", 0);
                                        return unavailable;
                                    });
        }
        pending = false;
        if (code.isEmpty()) {
            minecraft.setScreen(
                    new com.stardew.craft.client.gui.common.StardewNpcDialogueScreen(
                            "marnie",
                            Component.translatable(
                                            "stardewcraft.animal.purchase.marnie_success",
                                            snapshot.getString("PurchasedName"))
                                    .getString(),
                            0));
            return;
        }
        failure = code;
        update();
    }

    @Override
    protected void paint(GuiGraphics g) {
        paper(g, x + 16, y + 36, w - 32, h - 80);
        LivestockPortrait.draw(g, type, x + 32, y + 48, 96, Math.max(30, h - 190), false);
        label(g, LivestockPortrait.name(type), x + 148, y + 54, w - 182, INK);
        money(g, type.getInt("Price"), x + 148, y + 80, INK);
        money(g, offer.getInt("Money"), x + w - 128, y + 9, LIGHT);
        if (!failure.isEmpty())
            label(
                    g,
                    Component.translatable("livestock.stardewcraft." + failure),
                    x + 30,
                    y + h - 148,
                    w - 60,
                    RED);
    }
}
