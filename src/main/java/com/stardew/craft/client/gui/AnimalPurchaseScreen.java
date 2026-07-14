package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.network.payload.AnimalPurchaseSubmitPayload;
import com.stardew.craft.network.payload.IncubatorClaimSubmitPayload;
import com.stardew.craft.network.payload.OpenAnimalPurchaseScreenPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

@SuppressWarnings("null")
public class AnimalPurchaseScreen extends Screen {

    private enum Stage {
        CAROUSEL,
        BUILDING
    }

    private static final int BASE_W = 677;
    private static final int BASE_H = 360;
    private static final float UI_SCALE = 0.5f;
    private static final int SHOP_FIG_MIN_X = -778;
    private static final int SHOP_FIG_MIN_Y = -434;
    private static final int SHOP_FIG_W = 1200;
    private static final int SHOP_FIG_H = 920;
    private static final float SHOP_GLOBAL_SCALE = 0.50f;
    private static final float CARD_EXTRA_SCALE = 0.90f;
    private static final float COOP_ICON_SCALE = 0.72f;

    private static final int CARD_TEXT = 0xFFF7F2DB;
    private static final int CARD_PRICE = 0xFFAD5933;
    private static final int DIM_TEXT = 0xFFB8B8B8;
    private static final int LINE = 0xA0A0A0A0;
    private static final int PORTRAIT_BG = 0xFFE3B275;

    private static final int CAROUSEL_ENTRY_MS = 320;
    private static final int STAGE_SWAP_MS = 250;

    private static final ResourceLocation GOLD_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/gold_icon.png");
    private static final ResourceLocation SELL_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/sell_icon.png");
    private static final ResourceLocation OK_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/ok_yes_tile46.png");
    private static final ResourceLocation NO_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/cancel_no_tile47.png");
    private static final ResourceLocation PAGE_LEFT = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/page_left.png");
    private static final ResourceLocation PAGE_RIGHT = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/page_right.png");
    private static final ResourceLocation PAGE_UP = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/page_up.png");
    private static final ResourceLocation PAGE_DOWN = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/page_down.png");
    private static final ResourceLocation RENAME_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/rename.png");
    private static final ResourceLocation DICE_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/dice_icon.png");

    private static final ResourceLocation ICON_WHITE_CHICKEN = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_white_chicken.png");
    private static final ResourceLocation ICON_GOLDEN_CHICKEN = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_golden_chicken.png");
    private static final ResourceLocation ICON_DUCK = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_duck.png");
    private static final ResourceLocation ICON_VOID_CHICKEN = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_void_chicken.png");
    private static final ResourceLocation ICON_RABBIT = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_rabbit.png");
    private static final ResourceLocation ICON_OSTRICH = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_ostrich.png");
    private static final ResourceLocation ICON_DINOSAUR = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_dinosaur.png");
    private static final ResourceLocation ICON_COW = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_cow.png");
    private static final ResourceLocation ICON_GOAT = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_goat.png");
    private static final ResourceLocation ICON_SHEEP = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_sheep.png");
    private static final ResourceLocation ICON_PIG = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_pig.png");

    private final OpenAnimalPurchaseScreenPayload payload;
    private final List<AnimalItem> items = new ArrayList<>();
    private final Random random = new Random();
    
    private List<OpenAnimalPurchaseScreenPayload.BuildingOption> cachedBuildings = null;
    private int lastAnimalForBuildings = -1;

    private int panelX;
    private int panelY;
    private int shopX;
    private int shopY;
    private float shopScale = 0.5f;

    private Stage stage = Stage.CAROUSEL;
    private Stage previousStage = Stage.CAROUSEL;
    private long stageChangedAtMs;
    private long openedAtMs;

    private int selectedAnimal = 0;
    private float visualAnimal = 0.0f;
    private float animalOver = 0.0f;
    private float animalOverV = 0.0f;

    private int selectedBuilding = -1;
    private float visualBuilding = 0.0f;
    private float buildingOver = 0.0f;
    private float buildingOverV = 0.0f;

    private String editName = "";
    private int editCursor = 0;
    private boolean editingName = false;

    private float okScale = 1.0f;
    private float cancelScale = 1.0f;
    private float pageLeftScale = 1.0f;
    private float pageRightScale = 1.0f;
    private float pageUpScale = 1.5f;
    private float pageDownScale = 1.5f;
    private float renameScale = 1.08f;
    private float diceScale = 1.15f;

    private int hoverHotspot = 0;

    private static final Map<String, String[]> NAME_POOL_KEYS = Map.ofEntries(
        Map.entry("white_chicken", randomNameKeys("white_chicken")),
        Map.entry("golden_chicken", randomNameKeys("golden_chicken")),
        Map.entry("void_chicken", randomNameKeys("void_chicken")),
        Map.entry("duck", randomNameKeys("duck")),
        Map.entry("rabbit", randomNameKeys("rabbit")),
        Map.entry("ostrich", randomNameKeys("ostrich")),
        Map.entry("dinosaur", randomNameKeys("dinosaur")),
        Map.entry("cow", randomNameKeys("cow")),
        Map.entry("goat", randomNameKeys("goat")),
        Map.entry("sheep", randomNameKeys("sheep")),
        Map.entry("pig", randomNameKeys("pig"))
    );

    private static String[] randomNameKeys(String animalTypeId) {
        String[] keys = new String[6];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = "stardewcraft.animal.random_name." + animalTypeId + "." + i;
        }
        return keys;
    }

    private static final class NameLayout {
        int lineX;
        int lineY;
        int lineW;
        int textX;
        int textY;
        float textScale;
        String shown;
        int renameCx;
        int renameCy;
        int diceCx;
        int diceCy;
    }

    private class AnimalItem {
        final OpenAnimalPurchaseScreenPayload.AnimalOption option;
        final String title;
        final List<String> descLines;
        final String priceString;
        final boolean isCoop;

        AnimalItem(OpenAnimalPurchaseScreenPayload.AnimalOption option) {
            this.option = option;
            this.title = trim(resolveDisplayName(option.displayName(), option.animalTypeId()), 8);
            String rawDesc = option.unlocked() ? Component.translatable(option.descriptionKey()).getString() : Component.translatable(option.lockReasonKey()).getString();
            this.descLines = splitSimple(rawDesc, 4, 10);
            this.priceString = option.unlocked() ? (option.price() + " G") : "??? G";
            this.isCoop = isCoopAnimalType(option.animalTypeId());
        }
    }

    public AnimalPurchaseScreen(OpenAnimalPurchaseScreenPayload payload) {
        super(Component.translatable("container.stardew_craft.animal_purchase"));
        this.payload = payload;
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();

        items.clear();
        for (int i = 0; i < payload.animalOptions().size(); i++) {
            items.add(new AnimalItem(payload.animalOptions().get(i)));
        }
        items.sort(Comparator
            .comparing((AnimalItem i) -> !i.option.unlocked())
            .thenComparing(i -> i.option.price())
            .thenComparing(i -> i.option.displayName()));

        selectedAnimal = Math.max(0, firstUnlockedSortedIndex());
        if (payload.incubatorMode()) {
            selectedAnimal = 0;
        }
        visualAnimal = selectedAnimal;

        resetBuildingSelection();
        rerollName();

        openedAtMs = System.currentTimeMillis();
        stageChangedAtMs = openedAtMs;
        if (payload.incubatorMode()) {
            stage = Stage.BUILDING;
            previousStage = Stage.BUILDING;
        }
        hoverHotspot = 0;
    }

    @Override
    public void tick() {
        super.tick();

        visualAnimal += (selectedAnimal + animalOver - visualAnimal) * 0.22f;
        animalOverV += (-animalOver) * 0.24f;
        animalOverV *= 0.68f;
        animalOver += animalOverV;
        if (Math.abs(animalOver) < 0.001f && Math.abs(animalOverV) < 0.001f) {
            animalOver = 0.0f;
            animalOverV = 0.0f;
        }

        visualBuilding += (selectedBuilding + buildingOver - visualBuilding) * 0.24f;
        buildingOverV += (-buildingOver) * 0.24f;
        buildingOverV *= 0.68f;
        buildingOver += buildingOverV;
        if (Math.abs(buildingOver) < 0.001f && Math.abs(buildingOverV) < 0.001f) {
            buildingOver = 0.0f;
            buildingOverV = 0.0f;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (stage == Stage.CAROUSEL) {
            scrollAnimals(scrollY > 0 ? -1 : 1);
            return true;
        }
        scrollBuildings(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (stage == Stage.CAROUSEL) {
            if (inside(mx, my, fx(-778), fy(-1), ssi(100), ssi(100))) {
                scrollAnimals(-1);
                return true;
            }
            if (inside(mx, my, fx(322), fy(7), ssi(100), ssi(100))) {
                scrollAnimals(1);
                return true;
            }

            // Check bottom actions first so card hitboxes never swallow button clicks.
            if (inside(mx, my, fx(-309), fy(412), ssi(74), ssi(74))) {
                onClose();
                playUi(ModSounds.SMALL_SELECT.get(), 0.8f, 1.0f);
                return true;
            }
            if (inside(mx, my, fx(-119), fy(412), ssi(74), ssi(74))) {
                AnimalItem item = currentAnimal();
                if (item != null && item.option.unlocked()) {
                    previousStage = stage;
                    stage = Stage.BUILDING;
                    stageChangedAtMs = System.currentTimeMillis();
                    resetBuildingSelection();
                    rerollName();
                    playUi(ModSounds.SMALL_SELECT.get(), 0.85f, 1.0f);
                } else {
                    playUi(ModSounds.SMALL_SELECT.get(), 0.65f, 0.8f);
                }
                return true;
            }

            int hit = pickAnimalAt(mx, my);
            if (hit >= 0) {
                selectedAnimal = hit;
                playUi(ModSounds.SMALL_SELECT.get(), 0.72f, 1.06f);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int upCx = bx(720);
        int upCy = by(42);
        int downCx = bx(720);
        int downCy = by(196);
        if (inside(mx, my, upCx - si(24), upCy - si(24), si(48), si(48))) {
            scrollBuildings(-1);
            return true;
        }
        if (inside(mx, my, downCx - si(24), downCy - si(24), si(48), si(48))) {
            scrollBuildings(1);
            return true;
        }

        int okCx = bx(534);
        int okCy = by(310);
        int noCx = bx(430);
        int noCy = by(310);
        if (inside(mx, my, noCx - si(28), noCy - si(28), si(56), si(56))) {
            previousStage = stage;
            stage = Stage.CAROUSEL;
            stageChangedAtMs = System.currentTimeMillis();
            playUi(ModSounds.SMALL_SELECT.get(), 0.8f, 1.0f);
            return true;
        }
        if (inside(mx, my, okCx - si(28), okCy - si(28), si(56), si(56))) {
            submitPurchase();
            return true;
        }

        NameLayout nl = computeBuildingNameLayout();
        if (inside(mx, my, nl.renameCx - si(9), nl.renameCy - si(9), si(18), si(18))
            || inside(mx, my, nl.lineX, nl.lineY - si(20), nl.lineW, si(24))) {
            editingName = true;
            playUi(ModSounds.SMALL_SELECT.get(), 0.7f, 1.05f);
            return true;
        }
        if (inside(mx, my, nl.diceCx - si(11), nl.diceCy - si(11), si(22), si(22))) {
            rerollName();
            playUi(ModSounds.SMALL_SELECT.get(), 0.75f, 1.1f);
            return true;
        }

        int hit = pickBuildingAt(mx, my);
        if (hit >= 0 && hit < filteredBuildings().size()) {
            selectedBuilding = hit;
            playUi(ModSounds.SMALL_SELECT.get(), 0.72f, 1.06f);
            return true;
        }

        if (editingName) {
            editingName = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (stage == Stage.CAROUSEL) {
            if (keyCode == InputConstants.KEY_LEFT) {
                scrollAnimals(-1);
                return true;
            }
            if (keyCode == InputConstants.KEY_RIGHT) {
                scrollAnimals(1);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == InputConstants.KEY_UP) {
            scrollBuildings(-1);
            return true;
        }
        if (keyCode == InputConstants.KEY_DOWN) {
            scrollBuildings(1);
            return true;
        }

        if (editingName) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                editingName = false;
                return true;
            }
            if (keyCode == InputConstants.KEY_ESCAPE) {
                editingName = false;
                return true;
            }
            if (keyCode == InputConstants.KEY_LEFT && editCursor > 0) {
                editCursor--;
                return true;
            }
            if (keyCode == InputConstants.KEY_RIGHT && editCursor < editName.length()) {
                editCursor++;
                return true;
            }
            if (keyCode == InputConstants.KEY_HOME) {
                editCursor = 0;
                return true;
            }
            if (keyCode == InputConstants.KEY_END) {
                editCursor = editName.length();
                return true;
            }
            if (keyCode == InputConstants.KEY_BACKSPACE && editCursor > 0) {
                editName = editName.substring(0, editCursor - 1) + editName.substring(editCursor);
                editCursor--;
                return true;
            }
            if (keyCode == InputConstants.KEY_DELETE && editCursor < editName.length()) {
                editName = editName.substring(0, editCursor) + editName.substring(editCursor + 1);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (stage == Stage.BUILDING && editingName) {
            if (codePoint >= ' ' && codePoint != '\u00A7' && editName.length() < 48) {
                editName = editName.substring(0, editCursor) + codePoint + editName.substring(editCursor);
                editCursor++;
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        computeLayout();
        updateHover(mouseX, mouseY);

        this.renderTransparentBackground(graphics);
        // Cinematic vignette overlay
        graphics.fillGradient(0, 0, this.width, this.height, 0xAA0A0A0E, 0xEA050508);

        float entry = easeOutCubic(Math.min(1.0f, (System.currentTimeMillis() - openedAtMs) / (float) CAROUSEL_ENTRY_MS));
        float swap = easeOutCubic(Math.min(1.0f, (System.currentTimeMillis() - stageChangedAtMs) / (float) STAGE_SWAP_MS));

        if (stage == Stage.CAROUSEL || (stage == Stage.BUILDING && swap < 1.0f)) {
            float alpha = stage == Stage.CAROUSEL ? 1.0f : (1.0f - swap);
            graphics.pose().pushPose();
            graphics.pose().translate(0, Math.round((1.0f - entry) * si(20)), 0);
            drawCarouselStage(graphics, mouseX, mouseY, alpha);
            graphics.pose().popPose();
        }

        if (stage == Stage.BUILDING || (stage == Stage.CAROUSEL && previousStage == Stage.BUILDING && swap < 1.0f)) {
            float alpha = stage == Stage.BUILDING ? swap : (1.0f - swap);
            graphics.pose().pushPose();
            graphics.pose().translate(Math.round((1.0f - alpha) * si(36)), 0, 0);
            drawBuildingStage(graphics, mouseX, mouseY, alpha);
            graphics.pose().popPose();
        }
    }

    private void drawCarouselStage(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        int titleX = fx(-346);
        int titleY = fy(-434);
        
        // 增加质感顶条，而非原本空洞的背景
        graphics.fillGradient(fx(-778), fy(-470), fx(346), fy(-350), alphaColor(0x2A3D3627, alpha), alphaColor(0x00000000, alpha));
        
        drawScaledBoldText(graphics, Component.translatable("stardewcraft.animal.purchase.shop_title"),
                titleX, titleY, fs(48), alphaColor(0xFFFFF6D5, alpha));
        // 玛尼商店横线（明亮分割线）
        graphics.fill(fx(-635), fy(-356), fx(279), fy(-354), alphaColor(0xFFEADB8C, alpha));

        float moneyIconScale = ssi(59) / 16.0f;
        drawScaledIcon(graphics, GOLD_ICON, fx(96), fy(-414), moneyIconScale, alphaColor(0xFFFFFFFF, alpha));
        drawScaledBoldText(graphics, payload.playerMoney() + " G", fx(120), fy(-427), fs(28), alphaColor(0xFFFFFFFF, alpha));

        float pageScale = ssi(100) / 16.0f;
        drawScaledIcon(graphics, PAGE_LEFT, fx(-728), fy(49), pageScale * pageLeftScale, alphaColor(0xFFFFFFFF, alpha));
        drawScaledIcon(graphics, PAGE_RIGHT, fx(372), fy(57), pageScale * pageRightScale, alphaColor(0xFFFFFFFF, alpha));

        drawSelectedTriangles(graphics, alpha);

        int base = (int) Math.floor(visualAnimal);
        for (int i = base - 2; i <= base + 2; i++) {
            float d = i - visualAnimal;
            float abs = Math.abs(d);
            if (abs > 1.35f) {
                continue;
            }
            float t = Math.min(1.0f, abs);
            float x = fx(lerp(-329f, d < 0 ? -616f : 6f, t));
            float y = fy(lerp(-375f, d < 0 ? -306f : -304f, t));
            float fade = alpha * (1.0f - Math.min(0.55f, abs * 0.50f));
            AnimalItem item = (i >= 0 && i < items.size()) ? items.get(i) : null;
            drawAnimalCard(graphics, item, x, y, fade, abs < 0.34f, d < 0, t);
        }

        float actionScale = ssi(74) / 16.0f;
        drawScaledIcon(graphics, NO_ICON, fx(-272), fy(449), actionScale * cancelScale, alphaColor(0xFFFFFFFF, alpha));

        AnimalItem current = currentAnimal();
        boolean canBuy = current != null && current.option.unlocked();
        drawScaledIcon(graphics, SELL_ICON, fx(-82), fy(449), actionScale * okScale, alphaColor(canBuy ? 0xFFFFFF66 : 0x77666644, alpha));
    }

    private void drawAnimalCard(GuiGraphics graphics, AnimalItem item, float x, float y, float alpha, boolean selected, boolean leftSide, float sideT) {
        if (item == null) {
            return;
        }

        OpenAnimalPurchaseScreenPayload.AnimalOption option = item.option;
        int cardW = ssi(lerp(302f, 254.2f, sideT));
        int cardH = ssi(lerp(695f, 585f, sideT));
        int cardX = Math.round(x);
        int cardY = Math.round(y);
        int cardCx = cardX + cardW / 2;
        int cardCy = cardY + cardH / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(cardCx, cardCy, 0);
        
        // 缩放缓动，被选中时有弹出的张力
        float activeScale = selected ? CARD_EXTRA_SCALE * 1.05f : CARD_EXTRA_SCALE * 0.95f;
        graphics.pose().scale(activeScale, activeScale, 1.0f);
        graphics.pose().translate(-cardCx, -cardCy, 0);

        float dim = selected ? 1.0f : 0.45f;
        if (!option.unlocked()) {
            dim *= 0.58f;
        }

        // Body rectangle follows Figma body block (not full icon bounds).
        float bx0 = lerp(-309f, leftSide ? -599.16547f : 22.83453f, sideT);
        float by0 = lerp(-188f, leftSide ? -148.5971f : -146.5971f, sideT);
        float bw = lerp(263f, 221.37413f, sideT);
        float bh = lerp(508f, 427.59717f, sideT);
        int bodyX = fx(bx0);
        int bodyY = fy(by0);
        int bodyW = ssi(bw);
        int bodyH = ssi(bh);

        // 极高质感的卡片背板渲染 (橙色调)
        if (option.unlocked()) {
            if (selected) {
                // 外层发光描边
                graphics.fill(bodyX - 2, bodyY - 2, bodyX + bodyW + 2, bodyY + bodyH + 2, alphaColor(0xFFF7F2DB, alpha));
                // 内部橙色渐变色
                graphics.fillGradient(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH, alphaColor(0xFFD16C2E, alpha), alphaColor(0xFFAB4C13, alpha));
            } else {
                // 未选中态金属感暗橙边
                graphics.fill(bodyX - 1, bodyY - 1, bodyX + bodyW + 1, bodyY + bodyH + 1, alphaColor(0xFF964B17, alpha * dim));
                graphics.fillGradient(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH, alphaColor(0xFFBA541A, alpha * dim), alphaColor(0xFF8F3B0E, alpha * dim));
            }
        } else {
            // 未解锁阴影态
            graphics.fill(bodyX - 1, bodyY - 1, bodyX + bodyW + 1, bodyY + bodyH + 1, alphaColor(0xFF4A4A4A, alpha * dim));
            graphics.fillGradient(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH, alphaColor(0xFF2E2E36, alpha * dim), alphaColor(0xFF1B1B20, alpha * dim));
        }

        // 顶部阴影过渡，增加立体感
        graphics.fillGradient(bodyX, bodyY, bodyX + bodyW, bodyY + ssi(30), alphaColor(0x22000000, alpha * dim), alphaColor(0x00000000, alpha * dim));

        float ix0 = lerp(-329f, leftSide ? -616f : 6f, sideT);
        float iy0 = lerp(-375f, leftSide ? -306f : -304f, sideT);
        int iconX = fx(ix0);
        int iconY = fy(iy0);
        int iconW = ssi(lerp(302f, 254.2f, sideT));
        int iconH = iconW;
        if (item.isCoop) {
            // Keep coop sprites slightly smaller to avoid overfilling card tops.
            int shrunkW = Math.max(1, Math.round(iconW * COOP_ICON_SCALE));
            int shrunkH = Math.max(1, Math.round(iconH * COOP_ICON_SCALE));
            iconX += (iconW - shrunkW) / 2;
            iconY += (iconH - shrunkH) / 2;
            iconW = shrunkW;
            iconH = shrunkH;
        }

        if (selected) {
            // 给选中的活物增加浮动特效
            long time = System.currentTimeMillis();
            iconY += (int)(Math.sin(time / 300.0) * ssi(5));
        }

        if (option.unlocked()) {
            drawAnimalIconBox(graphics, option.animalTypeId(), iconX, iconY, iconW, iconH, alpha * dim);
        } else {
            drawAnimalSilhouetteBox(graphics, option.animalTypeId(), iconX, iconY, iconW, iconH, alpha * dim);
        }

        int titleY = fy(lerp(-61f, leftSide ? -41.697815f : -39.697815f, sideT));
        drawScaledBoldTextCentered(graphics, item.title, bodyX, bodyY, bodyW, bodyH, titleY, fs(lerp(40f, 32f, sideT)), alphaColor(CARD_TEXT, alpha * dim));

        int descX = bodyX;
        int ly = fy(lerp(7f, leftSide ? 30f : 32f, sideT));
        int descColor = option.unlocked() ? (!selected ? 0xFFDFD1A5 : 0xFFFAEFD1) : 0xFF999999;
        
        for (String line : item.descLines) {
            drawScaledTextCentered(graphics, line, descX, bodyW, ly, fs(lerp(24f, 20f, sideT)), alphaColor(descColor, alpha * dim));
            ly += ssi(Math.round(lerp(29f, 24f, sideT)));
        }

        String price = item.priceString;
        int priceY = fy(lerp(223f, leftSide ? 198f : 200f, sideT));
        float priceScale = fs(lerp(40f, 32f, sideT));
        int textW = Math.round(this.font.width(price) * priceScale);
        int coinPx = ssi(lerp(26f, 32f, 1.0f - sideT));
        int priceBlockW = coinPx + ssi(10) + textW;
        int priceLeft = bodyX + (bodyW - priceBlockW) / 2;
        int coinCx = priceLeft + coinPx / 2;
        int coinCy = priceY + coinPx / 2;
        drawScaledIcon(graphics, GOLD_ICON, coinCx, coinCy, coinPx / 16.0f, alphaColor(0xFFFFFFFF, alpha * dim));
        drawScaledBoldText(graphics, price, priceLeft + coinPx + ssi(10), priceY, priceScale, alphaColor(selected && option.unlocked() ? 0xFF8A3010 : CARD_PRICE, alpha * dim));
        graphics.pose().popPose();
    }

    private void drawSelectedTriangles(GuiGraphics graphics, float alpha) {
        int cx = fx(-177);
        int topCy = fy(-332);
        int bottomCy = fy(367);
        
        long time = System.currentTimeMillis();
        int offset = (int)(Math.sin(time / 200.0) * ssi(3));
        
        topCy += offset;
        bottomCy -= offset;
        
        int h = ssi(15);
        int w = ssi(18);
        int color = alphaColor(0xFFEADB8C, alpha * 0.9f); // 金色高雅三角

        // 优化：采用更圆润的方法绘制或至少缩小
        for (int dy = 0; dy <= h; dy++) {
            int half = Math.round(w * (1.0f - dy / (float) h));
            graphics.fill(cx - half, topCy + dy, cx + half + 1, topCy + dy + 1, color);
            graphics.fill(cx - half, bottomCy - dy, cx + half + 1, bottomCy - dy + 1, color);
        }
    }

    private void drawBuildingStage(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        drawBuildingPortrait(graphics, alpha);
        drawNameEditor(graphics, mouseX, mouseY, alpha);
        drawBuildingRows(graphics, alpha);

        drawScaledIcon(graphics, PAGE_UP, bx(720), by(42), pageUpScale, alphaColor(0xFFFFFFFF, alpha));
        drawScaledIcon(graphics, PAGE_DOWN, bx(720), by(196), pageDownScale, alphaColor(0xFFFFFFFF, alpha));

        drawScaledIcon(graphics, NO_ICON, bx(430), by(310), cancelScale, alphaColor(0xFFFFFFFF, alpha));
        drawScaledIcon(graphics, OK_ICON, bx(534), by(310), okScale, alphaColor(canSubmit() ? 0xFFFFFFFF : 0x77FFFFFF, alpha));
    }

    private void drawBuildingPortrait(GuiGraphics graphics, float alpha) {
        int cx = bx(148);
        int cy = by(156);
        int radius = si(154);
        
        // 更高级的多层头像框底座
        drawFilledCircle(graphics, cx, cy, radius + si(5), alphaColor(0x44000000, alpha));
        drawFilledCircle(graphics, cx, cy, radius + si(2), alphaColor(0xFFEADB8C, alpha));
        drawFilledCircle(graphics, cx, cy, radius, alphaColor(PORTRAIT_BG, alpha));

        AnimalItem item = currentAnimal();
        if (item == null) {
            return;
        }
        drawAnimalIcon(graphics, item.option.animalTypeId(), cx, cy, 2.62f, alpha);
    }

    private void drawNameEditor(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        NameLayout nl = computeBuildingNameLayout();
        boolean focused = inside(mouseX, mouseY, nl.lineX, nl.lineY - si(20), nl.lineW, si(24)) || editingName;
        int underline = focused ? 0xFFEADB8C : 0xAA8B8490;
        
        if (focused) {
            graphics.fill(nl.lineX, nl.lineY, nl.lineX + nl.lineW, nl.lineY + si(2), alphaColor(underline, alpha));
            graphics.fillGradient(nl.lineX, nl.lineY + si(2), nl.lineX + nl.lineW, nl.lineY + si(8), alphaColor(0x44EADB8C, alpha), alphaColor(0x00EADB8C, alpha));
        } else {
            graphics.fill(nl.lineX, nl.lineY, nl.lineX + nl.lineW, nl.lineY + 1, alphaColor(underline, alpha));
        }

        drawScaledBoldText(graphics, nl.shown, nl.textX, nl.textY, nl.textScale, alphaColor(0xFFFFF7D0, alpha));

        if (editingName && ((System.currentTimeMillis() / 450L) & 1L) == 0L) {
            int visibleCursor = Math.max(0, Math.min(editCursor, nl.shown.length()));
            String prefix = nl.shown.substring(0, visibleCursor);
            int cx = nl.textX + Math.round(this.font.width(prefix) * nl.textScale) + 1;
            graphics.fill(cx, nl.lineY - si(17), cx + 1, nl.lineY - si(5), alphaColor(0xFFFFFFFF, alpha));
        }

        drawScaledIcon(graphics, RENAME_ICON, nl.renameCx, nl.renameCy, renameScale, alphaColor(0xFFFFFFFF, alpha));
        drawScaledIcon(graphics, DICE_ICON, nl.diceCx, nl.diceCy, diceScale, alphaColor(0xFFFFFFFF, alpha));
    }

    private void drawBuildingRows(GuiGraphics graphics, float alpha) {
        int arrowCx = bx(326);
        int arrowCy = by(156);
        drawRightTriangle(graphics, arrowCx, arrowCy, si(10), si(14), alphaColor(0xFFE5E5E5, alpha));

        List<OpenAnimalPurchaseScreenPayload.BuildingOption> list = filteredBuildings();
        int floor = (int) Math.floor(visualBuilding);
        for (int i = floor - 2; i <= floor + 2; i++) {
            float d = i - visualBuilding;
            float abs = Math.abs(d);
            if (abs > 1.35f) {
                continue;
            }
            float p = Math.max(0.0f, 1.0f - abs);
            float x = bx(355) + (bx(383) - bx(355)) * p;
            float y;
            if (d < 0) {
                y = by(156) + d * (by(156) - by(84));
            } else {
                y = by(156) + d * (by(228) - by(156));
            }
            float s = 0.84f + 0.16f * p;
            float a = (0.26f + 0.74f * p) * alpha;

            OpenAnimalPurchaseScreenPayload.BuildingOption opt = (i >= 0 && i < list.size()) ? list.get(i) : null;
            if (opt == null) {
                continue;
            }

            int rx = Math.round(x);
            int ry = Math.round(y);
            
            drawFilledCircle(graphics, rx - si(24), ry + si(2), si(i == selectedBuilding ? 4 : 3), alphaColor(0xFFFFFFFF, a));
            int lw = i == selectedBuilding ? si(449 - 150) : si(431 - 150);
            graphics.fill(rx + si(6), ry + si(8), rx + si(6) + lw, ry + si(9), alphaColor(LINE, a));

            int titleColor = i == selectedBuilding ? 0xFFFFFFFF : 0xFFAAAAAA;
            drawScaledText(graphics, trim(opt.displayName(), 12), rx, ry - si(14), 0.92f * s, alphaColor(titleColor, a));
            drawScaledText(graphics, trim(opt.buildingId(), 14), rx, ry + si(5), 0.70f * s, alphaColor(DIM_TEXT, a));
            drawScaledText(graphics, opt.animalCount() + "/" + opt.capacity(), rx + si(164), ry - si(14), 0.92f * s, alphaColor(titleColor, a));
            drawScaledText(graphics, Component.translatable("stardewcraft.ui.available").getString(), rx + si(285), ry - si(12), 0.70f * s, alphaColor(0xFF92E472, a));
        }
    }

    private void scrollAnimals(int delta) {
        if (payload.incubatorMode()) {
            return;
        }
        if (items.isEmpty()) {
            return;
        }
        int next = Math.max(0, Math.min(items.size() - 1, selectedAnimal + delta));
        if (next != selectedAnimal) {
            selectedAnimal = next;
            playUi(ModSounds.SMALL_SELECT.get(), 0.66f, delta > 0 ? 0.98f : 1.06f);
            return;
        }
        if (selectedAnimal == 0 && delta < 0) {
            animalOverV -= 0.10f;
            playUi(ModSounds.SMALL_SELECT.get(), 0.55f, 0.9f);
        } else if (selectedAnimal == items.size() - 1 && delta > 0) {
            animalOverV += 0.10f;
            playUi(ModSounds.SMALL_SELECT.get(), 0.55f, 0.9f);
        }
    }

    private void scrollBuildings(int delta) {
        if (payload.incubatorMode()) {
            return;
        }
        List<OpenAnimalPurchaseScreenPayload.BuildingOption> list = filteredBuildings();
        if (list.isEmpty()) {
            return;
        }
        int next = Math.max(0, Math.min(list.size() - 1, selectedBuilding + delta));
        if (next != selectedBuilding) {
            selectedBuilding = next;
            playUi(ModSounds.SMALL_SELECT.get(), 0.66f, delta > 0 ? 0.98f : 1.06f);
            return;
        }
        if (selectedBuilding == 0 && delta < 0) {
            buildingOverV -= 0.10f;
            playUi(ModSounds.SMALL_SELECT.get(), 0.55f, 0.9f);
        } else if (selectedBuilding == list.size() - 1 && delta > 0) {
            buildingOverV += 0.10f;
            playUi(ModSounds.SMALL_SELECT.get(), 0.55f, 0.9f);
        }
    }

    private int pickAnimalAt(int mx, int my) {
        int floor = (int) Math.floor(visualAnimal);
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = floor - 2; i <= floor + 2; i++) {
            if (i < 0 || i >= items.size()) {
                continue;
            }
            float d = i - visualAnimal;
            if (Math.abs(d) > 1.35f) {
                continue;
            }
            float t = Math.min(1.0f, Math.abs(d));
            float bx0 = lerp(-309f, d < 0 ? -599.16547f : 22.83453f, t);
            float by0 = lerp(-188f, d < 0 ? -148.5971f : -146.5971f, t);
            int x = fx(bx0);
            int y = fy(by0);
            int fullW = ssi(lerp(302f, 254.2f, t));
            int fullH = ssi(lerp(695f, 585f, t));
            int w = Math.round(fullW * CARD_EXTRA_SCALE);
            int h = Math.round(fullH * CARD_EXTRA_SCALE);
            x += (fullW - w) / 2;
            y += (fullH - h) / 2;
            if (!inside(mx, my, x, y, w, h)) {
                continue;
            }
            double dist = Math.hypot(mx - (x + w * 0.5), my - (y + h * 0.5));
            if (dist < bestD) {
                bestD = dist;
                best = i;
            }
        }
        return best;
    }

    private int pickBuildingAt(int mx, int my) {
        if (payload.incubatorMode()) {
            return -1;
        }
        List<OpenAnimalPurchaseScreenPayload.BuildingOption> list = filteredBuildings();
        int floor = (int) Math.floor(visualBuilding);
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = floor - 2; i <= floor + 2; i++) {
            if (i < 0 || i >= list.size()) {
                continue;
            }
            float d = i - visualBuilding;
            if (Math.abs(d) > 1.35f) {
                continue;
            }
            float p = Math.max(0.0f, 1.0f - Math.abs(d));
            float x = bx(355) + (bx(383) - bx(355)) * p;
            float y;
            if (d < 0) {
                y = by(156) + d * (by(156) - by(84));
            } else {
                y = by(156) + d * (by(228) - by(156));
            }
            int bx = Math.round(x) - si(22);
            int by = Math.round(y) - si(30);
            if (!inside(mx, my, bx, by, si(420), si(70))) {
                continue;
            }
            double dist = Math.hypot(mx - x, my - y);
            if (dist < bestD) {
                bestD = dist;
                best = i;
            }
        }
        return best;
    }

    private void submitPurchase() {
        AnimalItem item = currentAnimal();
        List<OpenAnimalPurchaseScreenPayload.BuildingOption> buildings = filteredBuildings();
        if (item == null || selectedBuilding < 0 || selectedBuilding >= buildings.size()) {
            playUi(ModSounds.SMALL_SELECT.get(), 0.72f, 0.84f);
            return;
        }
        OpenAnimalPurchaseScreenPayload.BuildingOption b = buildings.get(selectedBuilding);
        String finalName = editName == null ? "" : editName.trim();
        if (payload.incubatorMode()) {
            PacketDistributor.sendToServer(new IncubatorClaimSubmitPayload(BlockPos.of(payload.contextBlockPos()), finalName));
        } else {
            PacketDistributor.sendToServer(new AnimalPurchaseSubmitPayload(item.option.animalTypeId(), b.buildingId(), finalName));
        }
        playUi(ModSounds.NEW_RECIPE.get(), 0.88f, 1.0f);
        onClose();
    }

    private boolean canSubmit() {
        AnimalItem item = currentAnimal();
        List<OpenAnimalPurchaseScreenPayload.BuildingOption> b = filteredBuildings();
        if (item == null || !item.option.unlocked()) {
            return false;
        }
        if (selectedBuilding < 0 || selectedBuilding >= b.size()) {
            return false;
        }
        if (payload.incubatorMode()) {
            return true;
        }
        return payload.playerMoney() >= item.option.price();
    }

    private int firstUnlockedSortedIndex() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).option.unlocked()) {
                return i;
            }
        }
        return 0;
    }

    private AnimalItem currentAnimal() {
        if (selectedAnimal < 0 || selectedAnimal >= items.size()) {
            return null;
        }
        return items.get(selectedAnimal);
    }

    private List<OpenAnimalPurchaseScreenPayload.BuildingOption> filteredBuildings() {
        AnimalItem current = currentAnimal();
        if (current == null) {
            return List.of();
        }
        
        if (cachedBuildings != null && selectedAnimal == lastAnimalForBuildings) {
            return cachedBuildings;
        }

        List<OpenAnimalPurchaseScreenPayload.BuildingOption> out = new ArrayList<>();
        for (OpenAnimalPurchaseScreenPayload.BuildingOption b : payload.buildingOptions()) {
            if (!current.option.family().equalsIgnoreCase(b.family())) {
                continue;
            }
            if (b.tier() < current.option.requiredTier()) {
                continue;
            }
            if (b.animalCount() >= b.capacity()) {
                continue;
            }
            out.add(b);
        }
        
        cachedBuildings = out;
        lastAnimalForBuildings = selectedAnimal;
        return out;
    }

    private void resetBuildingSelection() {
        selectedBuilding = 0;
        visualBuilding = 0.0f;
        buildingOver = 0.0f;
        buildingOverV = 0.0f;
        if (filteredBuildings().isEmpty()) {
            selectedBuilding = -1;
            visualBuilding = 0.0f;
        }
    }

    private void rerollName() {
        AnimalItem item = currentAnimal();
        if (item == null) {
            editName = "";
            editCursor = 0;
            return;
        }
        String[] pool = NAME_POOL_KEYS.get(item.option.animalTypeId());
        if (pool == null || pool.length == 0) {
            editName = resolveDisplayName(item.option.displayName(), item.option.animalTypeId());
        } else {
            editName = Component.translatable(pool[random.nextInt(pool.length)]).getString();
        }
        editCursor = editName.length();
    }

    private NameLayout computeBuildingNameLayout() {
        NameLayout nl = new NameLayout();
        int portraitCx = bx(148);
        nl.lineW = si(172);
        nl.lineX = portraitCx - nl.lineW / 2;
        nl.lineY = by(334);
        nl.textScale = 0.90f;

        String raw = editName == null ? "" : editName;
        int maxFontPx = Math.max(1, (int) (nl.lineW / nl.textScale) - 2);
        nl.shown = this.font.plainSubstrByWidth(raw, maxFontPx);

        int textW = Math.round(this.font.width(nl.shown) * nl.textScale);
        nl.textX = portraitCx - textW / 2;
        nl.textY = nl.lineY - si(16);

        int lineRight = nl.lineX + nl.lineW;
        int textRight = nl.textX + textW;
        nl.renameCy = nl.lineY - si(17);
        nl.diceCy = nl.lineY + si(20);
        nl.diceCx = portraitCx;

        int minRename = textRight + si(16);
        int maxDice = lineRight - si(14);
        nl.renameCx = Math.max(minRename, maxDice);
        return nl;
    }

    private void updateHover(int mouseX, int mouseY) {
        int h = detectHotspot(mouseX, mouseY);
        if (h != hoverHotspot) {
            hoverHotspot = h;
            if (h != 0) {
                playUi(ModSounds.SMALL_SELECT.get(), 0.42f, 1.14f);
            }
        }

        if (stage == Stage.CAROUSEL) {
            pageLeftScale = approach(pageLeftScale, inside(mouseX, mouseY, fx(-778), fy(-1), ssi(100), ssi(100)) ? 1.08f : 1.0f);
            pageRightScale = approach(pageRightScale, inside(mouseX, mouseY, fx(322), fy(7), ssi(100), ssi(100)) ? 1.08f : 1.0f);
            cancelScale = approach(cancelScale, inside(mouseX, mouseY, fx(-309), fy(412), ssi(74), ssi(74)) ? 1.08f : 1.0f);
            okScale = approach(okScale, inside(mouseX, mouseY, fx(-119), fy(412), ssi(74), ssi(74)) ? 1.08f : 1.0f);
        } else {
            NameLayout nl = computeBuildingNameLayout();
            pageUpScale = approach(pageUpScale, inside(mouseX, mouseY, bx(720) - si(24), by(42) - si(24), si(48), si(48)) ? 1.62f : 1.5f);
            pageDownScale = approach(pageDownScale, inside(mouseX, mouseY, bx(720) - si(24), by(196) - si(24), si(48), si(48)) ? 1.62f : 1.5f);
            cancelScale = approach(cancelScale, inside(mouseX, mouseY, bx(430) - si(28), by(310) - si(28), si(56), si(56)) ? 1.74f : 1.6f);
            okScale = approach(okScale, inside(mouseX, mouseY, bx(534) - si(28), by(310) - si(28), si(56), si(56)) ? 1.74f : 1.6f);
            renameScale = approach(renameScale, inside(mouseX, mouseY, nl.renameCx - si(9), nl.renameCy - si(9), si(18), si(18)) ? 1.02f : 0.94f);
            diceScale = approach(diceScale, inside(mouseX, mouseY, nl.diceCx - si(11), nl.diceCy - si(11), si(22), si(22)) ? 1.26f : 1.15f);
        }
    }

    private int detectHotspot(int mx, int my) {
        if (stage == Stage.CAROUSEL) {
            if (inside(mx, my, fx(-778), fy(-1), ssi(100), ssi(100))) return 1;
            if (inside(mx, my, fx(322), fy(7), ssi(100), ssi(100))) return 2;
            if (inside(mx, my, fx(-309), fy(412), ssi(74), ssi(74))) return 3;
            if (inside(mx, my, fx(-119), fy(412), ssi(74), ssi(74))) return 4;
            int a = pickAnimalAt(mx, my);
            return a >= 0 ? 100 + a : 0;
        }
        NameLayout nl = computeBuildingNameLayout();
        if (inside(mx, my, bx(720) - si(24), by(42) - si(24), si(48), si(48))) return 11;
        if (inside(mx, my, bx(720) - si(24), by(196) - si(24), si(48), si(48))) return 12;
        if (inside(mx, my, bx(430) - si(28), by(310) - si(28), si(56), si(56))) return 13;
        if (inside(mx, my, bx(534) - si(28), by(310) - si(28), si(56), si(56))) return 14;
        if (inside(mx, my, nl.renameCx - si(9), nl.renameCy - si(9), si(18), si(18))) return 15;
        if (inside(mx, my, nl.diceCx - si(11), nl.diceCy - si(11), si(22), si(22))) return 16;
        int b = pickBuildingAt(mx, my);
        return b >= 0 ? 200 + b : 0;
    }

    private void computeLayout() {
        panelX = (this.width - si(BASE_W)) / 2;
        panelY = (this.height - si(BASE_H)) / 2;
        float fitScale = Math.min(this.width / (float) SHOP_FIG_W, this.height / (float) SHOP_FIG_H);
        shopScale = Math.max(0.20f, fitScale * SHOP_GLOBAL_SCALE);
        shopX = Math.round((this.width - SHOP_FIG_W * shopScale) / 2.0f);
        shopY = Math.round((this.height - SHOP_FIG_H * shopScale) / 2.0f);
    }

    private int bx(int figX) {
        return panelX + si(figX);
    }

    private int by(int figY) {
        return panelY + si(figY);
    }

    private int si(int value) {
        return Math.max(1, Math.round(value * UI_SCALE));
    }

    private int fx(int figAbsX) {
        return shopX + ssi(figAbsX - SHOP_FIG_MIN_X);
    }

    private int fy(int figAbsY) {
        return shopY + ssi(figAbsY - SHOP_FIG_MIN_Y);
    }

    private int fx(float figAbsX) {
        return shopX + ssi(figAbsX - SHOP_FIG_MIN_X);
    }

    private int fy(float figAbsY) {
        return shopY + ssi(figAbsY - SHOP_FIG_MIN_Y);
    }

    private int ssi(int value) {
        return Math.max(1, Math.round(value * shopScale));
    }

    private int ssi(float value) {
        return Math.max(1, Math.round(value * shopScale));
    }

    private float fs(float figmaFontPx) {
        return Math.max(0.1f, (figmaFontPx / 9.0f) * shopScale);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0.0f, Math.min(1.0f, t));
    }

    private float easeOutCubic(float t) {
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private float approach(float current, float target) {
        if (current < target) return Math.min(target, current + 0.06f);
        if (current > target) return Math.max(target, current - 0.06f);
        return current;
    }

    private String trim(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 1)) + "...";
    }

    private List<String> splitSimple(String text, int maxLines, int maxChars) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            out.add("");
            return out;
        }
        String normalized = text.replace('\n', ' ');
        int idx = 0;
        while (idx < normalized.length() && out.size() < maxLines) {
            int end = Math.min(normalized.length(), idx + maxChars);
            out.add(normalized.substring(idx, end).trim());
            idx = end;
        }
        return out;
    }

    private String resolveDisplayName(String rawName, String animalTypeId) {
        if (rawName != null && !rawName.isBlank() && !rawName.startsWith("entity.")) {
            return rawName;
        }
        String key = animalTypeId == null ? "animal" : animalTypeId.replace('_', ' ');
        String[] parts = key.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.isEmpty() ? "Animal" : out.toString();
    }

    private void drawScaledIcon(GuiGraphics graphics, ResourceLocation icon, int centerX, int centerY, float scale, int tint) {
        int a = (tint >>> 24) & 0xFF;
        int r = (tint >>> 16) & 0xFF;
        int g = (tint >>> 8) & 0xFF;
        int b = tint & 0xFF;
        graphics.setColor(r / 255f, g / 255f, b / 255f, a / 255f);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(icon, -8, -8, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();

        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private void drawScaledText(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(this.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawScaledBoldText(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(this.font, Component.literal(text).withStyle(ChatFormatting.BOLD), 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawScaledBoldText(GuiGraphics graphics, Component text, int x, int y, float scale, int color) {
        drawScaledBoldText(graphics, text.getString(), x, y, scale, color);
    }

    private void drawScaledTextCentered(GuiGraphics graphics, String text, int boxX, int boxW, int y, float scale, int color) {
        int drawX = boxX + (boxW - Math.round(this.font.width(text) * scale)) / 2;
        drawScaledText(graphics, text, drawX, y, scale, color);
    }

    private void drawScaledBoldTextCentered(GuiGraphics graphics, String text, int boxX, int boxY, int boxW, int boxH, int y, float scale, int color) {
        int clampedY = Math.max(boxY, Math.min(boxY + boxH - ssi(40), y));
        int drawX = boxX + (boxW - Math.round(this.font.width(text) * scale)) / 2;
        drawScaledBoldText(graphics, text, drawX, clampedY, scale, color);
    }

    private void drawAnimalIconBox(GuiGraphics graphics, String animalTypeId, int x, int y, int w, int h, float alpha) {
        ResourceLocation icon = resolveAnimalIcon(animalTypeId);
        float sx = w / 32.0f;
        float sy = h / 32.0f;
        graphics.setColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(sx, sy, 1.0f);
        graphics.blit(icon, 0, 0, 0, 0, 32, 32, 32, 32);
        graphics.pose().popPose();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private void drawAnimalSilhouetteBox(GuiGraphics graphics, String animalTypeId, int x, int y, int w, int h, float alpha) {
        ResourceLocation icon = resolveAnimalIcon(animalTypeId);
        float sx = w / 32.0f;
        float sy = h / 32.0f;
        graphics.setColor(0f, 0f, 0f, Math.max(0f, Math.min(1f, alpha)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(sx, sy, 1.0f);
        graphics.blit(icon, 0, 0, 0, 0, 32, 32, 32, 32);
        graphics.pose().popPose();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private int alphaColor(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private void drawAnimalIcon(GuiGraphics graphics, String animalTypeId, int centerX, int centerY, float scale, float alpha) {
        ResourceLocation icon = resolveAnimalIcon(animalTypeId);
        int src = 32;
        int drawX = centerX - Math.round(src * scale / 2f);
        int drawY = centerY - Math.round(src * scale / 2f);

        graphics.setColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));
        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(icon, 0, 0, 0, 0, src, src, src, src);
        graphics.pose().popPose();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private void drawFilledCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        if (radius <= 0) return;
        int step = Math.max(1, radius / 12);
        for (int dy = -radius; dy <= radius; dy += step) {
            double effectiveY = Math.min(Math.abs(dy) + step / 2.0, radius);
            int span = (int) Math.floor(Math.sqrt((double) radius * radius - effectiveY * effectiveY));
            int h = Math.min(step, radius - dy + 1);
            if (h > 0) {
                graphics.fill(centerX - span, centerY + dy, centerX + span + 1, centerY + dy + h, color);
            }
        }
    }

    private void drawRightTriangle(GuiGraphics graphics, int cx, int cy, int tipW, int halfH, int color) {
        int step = Math.max(1, halfH / 6);
        for (int dy = -halfH; dy <= halfH; dy += step) {
            float t = 1.0f - (Math.abs(dy + step / 2.0f) / (float) halfH);
            int w = Math.max(1, Math.round(tipW * Math.max(0, t)));
            int h = Math.min(step, halfH - dy + 1);
            if(h > 0) {
                graphics.fill(cx - tipW, cy + dy, cx - tipW + w, cy + dy + h, color);
            }
        }
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private ResourceLocation resolveAnimalIcon(String animalTypeId) {
        String key = animalTypeId == null ? "" : animalTypeId.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "white_chicken" -> ICON_WHITE_CHICKEN;
            case "golden_chicken" -> ICON_GOLDEN_CHICKEN;
            case "duck" -> ICON_DUCK;
            case "void_chicken" -> ICON_VOID_CHICKEN;
            case "rabbit" -> ICON_RABBIT;
            case "ostrich" -> ICON_OSTRICH;
            case "dinosaur" -> ICON_DINOSAUR;
            case "cow" -> ICON_COW;
            case "goat" -> ICON_GOAT;
            case "sheep" -> ICON_SHEEP;
            case "pig" -> ICON_PIG;
            default -> ICON_WHITE_CHICKEN;
        };
    }

    private boolean isCoopAnimalType(String animalTypeId) {
        String key = animalTypeId == null ? "" : animalTypeId.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "white_chicken", "golden_chicken", "duck", "void_chicken", "rabbit", "ostrich", "dinosaur" -> true;
            default -> false;
        };
    }

    private void playUi(SoundEvent event, float volume, float pitch) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(event, volume, pitch);
        }
    }
}
