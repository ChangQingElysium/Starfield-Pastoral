package com.stardew.craft.client.gui.menu;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.NpcDisplayNames;
import com.stardew.craft.client.NpcFriendshipClientCache;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.StardewRenderMapping;
import com.stardew.craft.data.VanillaObjectCatalog;
import com.stardew.craft.npc.runtime.VanillaGiftTasteResolver;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Source-backed implementation of SDV {@code ProfileMenu}'s gift-log page. */
@SuppressWarnings("null")
public final class StardewNpcProfileScreen extends Screen {
    private static final ResourceLocation LETTER_BG = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/letter_bg.png");
    private static final ResourceLocation DAY_BG = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/daybg.png");
    private static final int[][] CATEGORY_GROUPS = {
            {}, {-75, -79}, {-6, -5, -14, -18}, {-26}, {-7},
            {-80, -81, -23, -17}, {-4}, {-27, -25}, {-15, -12, -2}, {}
    };
    private static final String[] CATEGORY_KEYS = {
            "stardewcraft.profile.gift.category.favorites",
            "stardewcraft.profile.gift.category.fruit_vegetables",
            "stardewcraft.profile.gift.category.animal_produce",
            "stardewcraft.profile.gift.category.artisan",
            "stardewcraft.profile.gift.category.cooking",
            "stardewcraft.profile.gift.category.forage",
            "stardewcraft.profile.gift.category.fish",
            "stardewcraft.profile.gift.category.ingredients",
            "stardewcraft.profile.gift.category.minerals_gems",
            "stardewcraft.profile.gift.category.misc"
    };
    private static final Map<VanillaGiftTasteResolver.Taste, String> TASTE_KEYS = Map.of(
            VanillaGiftTasteResolver.Taste.LOVED, "stardewcraft.profile.gift.loved",
            VanillaGiftTasteResolver.Taste.LIKED, "stardewcraft.profile.gift.liked",
            VanillaGiftTasteResolver.Taste.NEUTRAL, "stardewcraft.profile.gift.neutral",
            VanillaGiftTasteResolver.Taste.DISLIKED, "stardewcraft.profile.gift.disliked",
            VanillaGiftTasteResolver.Taste.HATED, "stardewcraft.profile.gift.hated"
    );
    private static final List<VanillaGiftTasteResolver.Taste> TASTE_ORDER = List.of(
            VanillaGiftTasteResolver.Taste.LOVED,
            VanillaGiftTasteResolver.Taste.LIKED,
            VanillaGiftTasteResolver.Taste.NEUTRAL,
            VanillaGiftTasteResolver.Taste.DISLIKED,
            VanillaGiftTasteResolver.Taste.HATED
    );
    private static final Set<Integer> ALL_NAMED_CATEGORIES = Set.of(
            -75, -79, -6, -5, -14, -18, -26, -7, -80, -81, -23, -17,
            -4, -27, -25, -15, -12, -2);

    private final Screen parent;
    private final List<NpcFriendshipClientCache.Entry> socialEntries;
    private int currentEntry;
    private int category;
    private int scroll;
    private StardewRenderMapping mapping;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int closeX;
    private int closeY;
    private ItemStack hoveredItem = ItemStack.EMPTY;

    public StardewNpcProfileScreen(Screen parent, NpcFriendshipClientCache.Entry subject,
                                   List<NpcFriendshipClientCache.Entry> allEntries) {
        super(Component.empty());
        this.parent = parent;
        this.socialEntries = allEntries.stream().filter(NpcFriendshipClientCache.Entry::met).toList();
        this.currentEntry = Math.max(0, this.socialEntries.indexOf(subject));
    }

    @Override
    protected void init() {
        mapping = new StardewRenderMapping(width, height, (float) minecraft.getWindow().getGuiScale());
        panelW = mapping.ui(1280);
        panelH = mapping.ui(720);
        panelX = mapping.centerX(panelW);
        panelY = (height - panelH) / 2;
        closeX = panelX + panelW - mapping.ui(36);
        closeY = panelY - mapping.ui(8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0);
        graphics.pose().scale(mapping.s4(), mapping.s4(), 1.0F);
        graphics.blit(LETTER_BG, 0, 0, 320, 0, 320, 180, 1280, 512);
        graphics.pose().popPose();

        NpcFriendshipClientCache.Entry entry = current();
        drawCharacterPane(graphics, entry);
        drawGiftLog(graphics, entry, mouseX, mouseY);
        CommonGuiTextures.drawCloseButton(graphics, closeX, closeY, mapping.s4());

        if (!hoveredItem.isEmpty()) {
            graphics.renderTooltip(font, hoveredItem, mouseX, mouseY);
        }
    }

    private void drawCharacterPane(GuiGraphics graphics, NpcFriendshipClientCache.Entry entry) {
        int leftX = panelX + mapping.ui(52);
        int leftW = mapping.ui(400);
        int sceneW = mapping.ui(128);
        int sceneH = mapping.ui(192);
        int sceneX = leftX + (leftW - sceneW) / 2;
        int sceneY = panelY + mapping.ui(64);
        graphics.blit(DAY_BG, sceneX, sceneY, sceneW, sceneH, 0, 0, 128, 192, 128, 192);

        String npcId = normalize(entry.npcId());
        ResourceLocation sprite = ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "textures/entity/npc/" + npcId + ".png");
        int spriteW = mapping.ui(64);
        int spriteH = mapping.ui(128);
        graphics.blit(sprite, sceneX + (sceneW - spriteW) / 2, sceneY + mapping.ui(48),
                spriteW, spriteH, 0, 0, 16, 32, 64, 128);

        String name = NpcDisplayNames.translated(entry.npcId());
        drawCentered(graphics, Component.literal(name), leftX + leftW / 2, panelY + mapping.ui(288), 0xFF7B3F21);
        drawHearts(graphics, entry, leftX + leftW / 2, panelY + mapping.ui(344));

        if (socialEntries.size() > 1) {
            CommonGuiTextures.drawBackArrow(graphics, sceneX - mapping.ui(64), sceneY + sceneH / 2,
                    mapping.s4());
            CommonGuiTextures.drawForwardArrow(graphics, sceneX + sceneW + mapping.ui(16), sceneY + sceneH / 2,
                    mapping.s4());
        }
    }

    private void drawHearts(GuiGraphics graphics, NpcFriendshipClientCache.Entry entry, int centerX, int y) {
        int count = 10;
        int step = mapping.ui(32);
        int x = centerX - count * step / 2;
        int full = Mth.clamp(entry.points() / 250, 0, count);
        for (int i = 0; i < count; i++) {
            CommonGuiTextures.drawSocialHeartTint(
                    graphics, x + i * step, y, i < full, mapping.s4(), 1, 1, 1, 1);
        }
    }

    private void drawGiftLog(GuiGraphics graphics, NpcFriendshipClientCache.Entry entry,
                             int mouseX, int mouseY) {
        hoveredItem = ItemStack.EMPTY;
        int contentX = panelX + mapping.ui(480);
        int contentRight = panelX + panelW - mapping.ui(76);
        int centerX = (contentX + contentRight) / 2;
        drawCentered(graphics, Component.translatable("stardewcraft.profile.gift_log"),
                centerX, panelY + mapping.ui(64), 0xFF7B3F21);

        int categoryY = panelY + mapping.ui(136);
        CommonGuiTextures.drawBackArrow(graphics, contentX + mapping.ui(32), categoryY, mapping.s4());
        CommonGuiTextures.drawForwardArrow(graphics, contentRight - mapping.ui(80), categoryY, mapping.s4());
        drawCentered(graphics,
                Component.translatable(CATEGORY_KEYS[category], NpcDisplayNames.translated(entry.npcId())),
                centerX, categoryY + mapping.ui(8), 0xFF7B3F21);

        int listTop = panelY + mapping.ui(224);
        int listBottom = panelY + panelH - mapping.ui(52);
        int listLeft = contentX + mapping.ui(28);
        int listRight = contentRight - mapping.ui(28);
        Map<VanillaGiftTasteResolver.Taste, List<GiftEntry>> groups = groupedItems(entry.npcId());
        int contentHeight = measureContentHeight(groups, listRight - listLeft);
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - (listBottom - listTop)));

        graphics.enableScissor(listLeft, listTop, listRight, listBottom);
        int y = listTop - scroll;
        for (VanillaGiftTasteResolver.Taste taste : TASTE_ORDER) {
            List<GiftEntry> items = groups.getOrDefault(taste, List.of());
            if (items.isEmpty()) continue;
            graphics.drawString(font, Component.translatable(TASTE_KEYS.get(taste)),
                    listLeft, y, 0xFF5C3520, false);
            y += mapping.ui(32);
            int columns = Math.max(1, (listRight - listLeft) / mapping.ui(68));
            for (int index = 0; index < items.size(); index++) {
                int x = listLeft + index % columns * mapping.ui(68);
                int itemY = y + index / columns * mapping.ui(68);
                GiftEntry item = items.get(index);
                CommonGuiTextures.drawItem(graphics, item.stack(), x, itemY, mapping.s4());
                if (mouseX >= x && mouseX < x + mapping.ui(64)
                        && mouseY >= itemY && mouseY < itemY + mapping.ui(64)) {
                    hoveredItem = item.stack();
                }
            }
            y += ((items.size() + columns - 1) / columns) * mapping.ui(68) + mapping.ui(20);
        }
        graphics.disableScissor();

        if (groups.values().stream().allMatch(List::isEmpty)) {
            drawCentered(graphics, Component.translatable("stardewcraft.profile.gift_log.empty"),
                    centerX, (listTop + listBottom) / 2, 0xFF5C3520);
        }
    }

    private Map<VanillaGiftTasteResolver.Taste, List<GiftEntry>> groupedItems(String npcId) {
        Map<VanillaGiftTasteResolver.Taste, List<GiftEntry>> result = new EnumMap<>(VanillaGiftTasteResolver.Taste.class);
        String prefix = normalize(npcId) + ":";
        for (String reveal : ClientPlayerDataCache.getRevealedGiftTastes()) {
            if (!reveal.startsWith(prefix)) continue;
            VanillaObjectCatalog.Entry source = VanillaObjectCatalog.entryByKey(reveal.substring(prefix.length()));
            ItemStack stack = VanillaObjectCatalog.stackFor(source);
            if (source == null || stack.isEmpty() || !matchesCategory(source, stack, npcId)) continue;
            VanillaGiftTasteResolver.Result taste = VanillaGiftTasteResolver.resolve(stack, npcId);
            if (taste == null) continue;
            result.computeIfAbsent(taste.taste(), ignored -> new ArrayList<>())
                    .add(new GiftEntry(source, stack));
        }
        result.values().forEach(items -> items.sort(
                Comparator.comparing(GiftEntry::source, VanillaObjectCatalog.sourceOrder())));
        return result;
    }

    private boolean matchesCategory(VanillaObjectCatalog.Entry source, ItemStack stack, String npcId) {
        if (category == 0) {
            VanillaGiftTasteResolver.Result result = VanillaGiftTasteResolver.resolve(stack, npcId);
            return result != null && (result.taste() == VanillaGiftTasteResolver.Taste.LOVED
                    || result.taste() == VanillaGiftTasteResolver.Taste.LIKED);
        }
        if (category == CATEGORY_GROUPS.length - 1) {
            return !ALL_NAMED_CATEGORIES.contains(source.category());
        }
        for (int valid : CATEGORY_GROUPS[category]) {
            if (source.category() == valid) return true;
        }
        return false;
    }

    private int measureContentHeight(Map<VanillaGiftTasteResolver.Taste, List<GiftEntry>> groups, int width) {
        int columns = Math.max(1, width / mapping.ui(68));
        int height = 0;
        for (VanillaGiftTasteResolver.Taste taste : TASTE_ORDER) {
            List<GiftEntry> items = groups.getOrDefault(taste, List.of());
            if (!items.isEmpty()) {
                height += mapping.ui(52)
                        + ((items.size() + columns - 1) / columns) * mapping.ui(68);
            }
        }
        return height;
    }

    private void drawCentered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private NpcFriendshipClientCache.Entry current() {
        if (socialEntries.isEmpty()) {
            return new NpcFriendshipClientCache.Entry("lewis", false, false, 0, 0, 0, false, false, 0);
        }
        return socialEntries.get(Mth.clamp(currentEntry, 0, socialEntries.size() - 1));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (contains(mouseX, mouseY, closeX, closeY, mapping.ui(48), mapping.ui(48))) {
            onClose();
            return true;
        }

        int sceneX = panelX + mapping.ui(52) + (mapping.ui(400) - mapping.ui(128)) / 2;
        int sceneY = panelY + mapping.ui(64);
        if (socialEntries.size() > 1
                && contains(mouseX, mouseY, sceneX - mapping.ui(64), sceneY + mapping.ui(72),
                        mapping.ui(48), mapping.ui(48))) {
            currentEntry = Math.floorMod(currentEntry - 1, socialEntries.size());
            scroll = 0;
            playSelect();
            return true;
        }
        if (socialEntries.size() > 1
                && contains(mouseX, mouseY, sceneX + mapping.ui(144), sceneY + mapping.ui(72),
                        mapping.ui(48), mapping.ui(48))) {
            currentEntry = (currentEntry + 1) % socialEntries.size();
            scroll = 0;
            playSelect();
            return true;
        }

        int contentX = panelX + mapping.ui(480);
        int contentRight = panelX + panelW - mapping.ui(76);
        int categoryY = panelY + mapping.ui(136);
        if (contains(mouseX, mouseY, contentX + mapping.ui(32), categoryY, mapping.ui(48), mapping.ui(48))) {
            category = Math.floorMod(category - 1, CATEGORY_KEYS.length);
            scroll = 0;
            playSelect();
            return true;
        }
        if (contains(mouseX, mouseY, contentRight - mapping.ui(80), categoryY, mapping.ui(48), mapping.ui(48))) {
            category = (category + 1) % CATEGORY_KEYS.length;
            scroll = 0;
            playSelect();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * mapping.ui(68));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void playSelect() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(), 1.0F));
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String normalize(String npcId) {
        return npcId == null ? "" : npcId.trim().toLowerCase(Locale.ROOT);
    }

    private record GiftEntry(VanillaObjectCatalog.Entry source, ItemStack stack) {
    }
}
