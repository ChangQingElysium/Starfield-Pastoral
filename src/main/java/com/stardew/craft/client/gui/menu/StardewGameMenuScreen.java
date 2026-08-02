package com.stardew.craft.client.gui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.ClientMailIndex;
import com.stardew.craft.client.ClientMuseumDonationCache;
import com.stardew.craft.client.LeaderboardClientCache;
import com.stardew.craft.client.ModKeyMappings;
import com.stardew.craft.client.NpcDisplayNames;
import com.stardew.craft.client.NpcFriendshipClientCache;
import com.stardew.craft.client.PlayerGenderText;
import com.stardew.craft.client.gui.StardewCollectivePauseScreen;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.StardewRenderMapping;
import com.stardew.craft.client.gui.overnight.LevelUpMenuTextures;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.client.hud.StardewHudLayoutEditorScreen;
import com.stardew.craft.communitycenter.network.BundleClientData;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.api.v1.secretnote.StardewSecretNoteDefinition;
import com.stardew.craft.data.VanillaObjectCatalog;
import com.stardew.craft.item.SecretNoteItem;
import com.stardew.craft.item.misc.StardropItem;
import com.stardew.craft.inventory.InventoryTrashPolicy;
import com.stardew.craft.leaderboard.LeaderboardMetric;
import com.stardew.craft.leaderboard.LeaderboardPeriod;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.equipment.CombinedRingData;
import com.stardew.craft.mastery.MasteryProgress;
import com.stardew.craft.mail.MailTextSyntax;
import com.stardew.craft.menu.StardewGameMenu;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.network.payload.LeaderboardSyncPayload;
import com.stardew.craft.network.payload.RequestNpcFriendshipOverviewPayload;
import com.stardew.craft.network.payload.RequestLeaderboardPayload;
import com.stardew.craft.network.payload.CraftingMenuCraftSubmitPayload;
import com.stardew.craft.network.payload.CraftingMenuInventoryActionPayload;
import com.stardew.craft.network.payload.InventoryOrganizePayload;
import com.stardew.craft.network.payload.OpenSeenMailPayload;
import com.stardew.craft.player.RecipeCatalogData;
import com.stardew.craft.player.StardewCraftingRecipeData;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.secretnote.SecretNoteRegistry;
import com.stardew.craft.secretnote.SecretNote23Service;
import com.stardew.craft.secretnote.SecretNoteStoryFlags;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

@SuppressWarnings({"null", "unused"})
public class StardewGameMenuScreen extends AbstractContainerScreen<StardewGameMenu>
        implements StardewCollectivePauseScreen {
    public interface QuickCraftStateAccess {
        int stardewcraft$quickCraftingType();

        int stardewcraft$quickCraftingRemainder();
    }

    private static final int STD_TILE_SIZE = 16;
    private static final float ITEM_VISUAL_SCALE = 1.0f;
    private static final int CARRIED_ITEM_OFFSET_SDV = 16;
    private static final int INVENTORY_COLS = 9;
    private static final int INVENTORY_ROWS = 4;
    private static final int INVENTORY_SLOT_SDV = 64;
    private static final int INVENTORY_GAP_SDV = 4;
    private static final int INVENTORY_TOP_SDV = 432;

    private static final int CRAFTING_GRID_X_SDV = 64;
    private static final int CRAFTING_GRID_Y_SDV = 104;
    private static final int CRAFTING_RECIPE_SLOT_SDV = 64;
    private static final int CRAFTING_RECIPE_STEP_SDV = 72;
    private static final int CRAFTING_RECIPE_ITEM_SDV = 64;
    private static final int CRAFTING_RECIPE_COLUMNS = 10;
    private static final int CRAFTING_RECIPE_ROWS = 4;
    private static final int CRAFTING_RECIPES_PER_PAGE = CRAFTING_RECIPE_COLUMNS * CRAFTING_RECIPE_ROWS;
    private static final int CRAFTING_PARTITION_Y_SDV = 384;
    private static final ResourceLocation VANILLA_BIG_CRAFTABLES = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/crafting/vanilla_big_craftables.png");
    private static final int VANILLA_BIG_CRAFTABLES_WIDTH = 128;
    private static final int VANILLA_BIG_CRAFTABLES_HEIGHT = 1472;
    private static final int VANILLA_BIG_CRAFTABLE_COLUMNS = 8;
    private static final Map<String, Integer> VANILLA_BIG_CRAFTABLE_SPRITES = Map.ofEntries(
            Map.entry("anvil", 293),
            Map.entry("bait_maker", 285),
            Map.entry("bee_house", 10),
            Map.entry("bone_mill", 90),
            Map.entry("cask", 163),
            Map.entry("charcoal_kiln", 114),
            Map.entry("cheese_press", 16),
            Map.entry("crystalarium", 21),
            Map.entry("dehydrator", 286),
            Map.entry("deluxe_worm_bin", 298),
            Map.entry("farm_computer", 239),
            Map.entry("fish_smoker", 296),
            Map.entry("furnace", 13),
            Map.entry("geode_crusher", 182),
            Map.entry("heavy_furnace", 288),
            Map.entry("keg", 12),
            Map.entry("lightning_rod", 9),
            Map.entry("loom", 17),
            Map.entry("mayonnaise_machine", 24),
            Map.entry("mine_ladder", 71),
            Map.entry("mini_forge", 294),
            Map.entry("mini_jukebox", 209),
            Map.entry("mini_obelisk", 238),
            Map.entry("oil_maker", 19),
            Map.entry("preserves_jar", 15),
            Map.entry("recycling_machine", 20),
            Map.entry("scarecrow", 8),
            Map.entry("seed_maker", 25),
            Map.entry("solar_panel", 231),
            Map.entry("statue_of_blessings", 290),
            Map.entry("statue_of_dwarf_king", 292),
            Map.entry("stone_chest", 232),
            Map.entry("tapper", 105),
            Map.entry("wooden_chest", 130),
            Map.entry("worm_bin", 154));
    private static final List<String> VANILLA_CRAFTING_DISPLAY_ORDER = List.of(
            "grass_starter", "wooden_chest", "stone_chest", "torch", "scarecrow",
            "bee_house", "keg", "cask", "dehydrator", "furnace", "heavy_furnace",
            "anvil", "mini_forge", "cheese_press", "mayonnaise_machine", "seed_maker",
            "loom", "oil_maker", "recycling_machine", "worm_bin", "deluxe_worm_bin",
            "bait_maker", "fish_smoker", "preserves_jar", "charcoal_kiln", "tapper",
            "lightning_rod", "crystalarium", "mini_jukebox", "sprinkler",
            "quality_sprinkler", "iridium_sprinkler", "mine_ladder", "basic_fertilizer",
            "tree_fertilizer", "mystic_tree_seed", "basic_retaining_soil",
            "quality_retaining_soil", "speed_gro", "deluxe_speed_gro", "deluxe_fertilizer",
            "deluxe_retaining_soil", "cherry_bomb", "bomb_item", "mega_bomb", "iron_bar",
            "gold_bar", "ancient_fruit_seeds", "wild_seeds_spring", "wild_seeds_summer",
            "wild_seeds_fall", "wild_seeds_winter", "fiber_seeds", "warp_totem_farm",
            "warp_totem_mountain", "warp_totem_beach", "warp_totem_desert", "rain_totem",
            "treasure_totem", "field_snack", "statue_of_blessings", "statue_of_dwarf_king",
            "spirit_eve_jack_o_lantern", "bait", "deluxe_bait", "challenge_bait", "magnet",
            "spinner", "dressed_spinner", "trap_bobber", "sonar_bobber", "cork_bobber",
            "treasure_hunter", "barbed_hook", "oil_of_garlic", "life_elixir", "crab_pot",
            "iridium_band", "ring_of_yoba", "sturdy_ring", "warrior_ring", "fairy_dust",
            "bug_steak", "quality_bobber", "monster_musk", "mini_obelisk", "farm_computer",
            "geode_crusher", "solar_panel", "bone_mill", "thorns_ring", "glowstone_ring");
    private static final Map<String, Integer> VANILLA_CRAFTING_DISPLAY_RANKS =
            createVanillaCraftingDisplayRanks();

    private static final int BORDER_WIDTH = 32;
    private static final int MENU_WIDTH_SDV = 800 + BORDER_WIDTH * 2;
    private static final int MENU_HEIGHT_SDV = 700 + BORDER_WIDTH * 2;
    private static final int SKILLS_PAGE_HEIGHT_SDV = 600 + BORDER_WIDTH * 2;
    private static final int TAB_Y_OFFSET_SDV = -56;
    private static final int TAB_START_X_SDV = 64;
    private static final int TAB_STEP_SDV = 64;
    private static final int TAB_SIZE_SDV = 64;

    private static final int CLOSE_X_OFFSET_SDV = 36;
    private static final int CLOSE_Y_OFFSET_SDV = 8;
    private static final int CLOSE_SIZE_SDV = 48;

    private static final int TAB_COUNT = 10;
    private static final int TAB_SOCIAL = 2;
    private static final int TAB_POWERS = 6;
    private static final int TAB_COLLECTIONS = 7;
    private static final int TAB_OPTIONS = 8;
    private static final int OPTIONS_PAGE_SETTINGS = 0;
    private static final int OPTIONS_PAGE_LEADERBOARD = 1;
    private static final int LEADERBOARD_PAGE_SIZE = 10;
    private static final LeaderboardMetric[] LEADERBOARD_METRICS = LeaderboardMetric.values();
    private static final int LEADERBOARD_TAB_SIZE_SDV = 56;
    private static final int LEADERBOARD_TAB_GAP_SDV = 4;
    private static final int LEADERBOARD_TAB_ACTIVE_OFFSET_SDV = 8;
    private static final int LEADERBOARD_METRIC_SCROLL_HINT_SDV = 18;
    private static final int LEADERBOARD_TAB_ACT = 0xFFF0D880;
    private static final int LEADERBOARD_TAB_HOV = 0xFFE8D8B0;
    private static final int LEADERBOARD_TAB_NRM = 0xFFC8A860;
    private static final int LEADERBOARD_TAB_BDR = 0xFF907030;
    private static final int LEADERBOARD_GOLD = 0xFFB08830;

    private static final int COLLECTION_SHIPPED = 0;
    private static final int COLLECTION_FISH = 1;
    private static final int COLLECTION_ARTIFACTS = 2;
    private static final int COLLECTION_MINERALS = 3;
    private static final int COLLECTION_COOKING = 4;
    private static final int COLLECTION_ACHIEVEMENTS = 5;
    private static final int COLLECTION_SECRET_NOTES = 6;
    private static final int COLLECTION_LETTERS = 7;
    private static final int COLLECTION_COLUMNS = 10;
    private static final int COLLECTION_PAGE_SIZE = 80;
    private static final ResourceLocation SECRET_NOTE_IMAGES = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/secret_notes_images.png");

    private static final int SOCIAL_ROW_HEIGHT_SDV = 112;
    private static final int SOCIAL_MAX_VISIBLE = 5;
    private static final int[][] SOCIAL_HEART_FILL_PATTERN = new int[][] {
        {1, 1, 0, 1, 1},
        {1, 1, 1, 1, 1},
        {0, 1, 1, 1, 0},
        {0, 0, 1, 0, 0}
    };
    private static final Set<String> DATEABLE_NPCS = Set.of(
        "abigail", "alex", "elliott", "emily", "haley", "harvey", "leah", "maru", "penny", "sam", "sebastian", "shane"
    );
    private static final Map<ResourceLocation, PortraitResource> SOCIAL_PORTRAIT_CACHE = new HashMap<>();

    public static void clearPortraitCache() {
        SOCIAL_PORTRAIT_CACHE.clear();
    }

    private static final String[] TAB_KEYS = new String[] {
            "stardewcraft.game_menu.tab.inventory",
            "stardewcraft.game_menu.tab.skills",
            "stardewcraft.game_menu.tab.social",
            "stardewcraft.game_menu.tab.map",
            "stardewcraft.game_menu.tab.crafting",
            "stardewcraft.game_menu.tab.animals",
            "stardewcraft.game_menu.tab.powers",
            "stardewcraft.game_menu.tab.collections",
            "stardewcraft.game_menu.tab.options",
            "stardewcraft.game_menu.tab.exit"
    };

    private StardewRenderMapping mapping;
    private int menuX;
    private int menuY;
    private int menuWidth;
    private int menuHeight;
    private int currentTab;

    private int currentCraftingPage;
    private int currentCollectionTab = COLLECTION_SHIPPED;
    private int currentCollectionPage;
    private int currentOptionsPage = OPTIONS_PAGE_SETTINGS;
    private final float[] collectionHoverScale = new float[COLLECTION_PAGE_SIZE];
    private int selectedCraftingIndex = -1;
    private boolean craftingKeyboardFocus;

    // ---- Farm Management Tab (tab 3) ----
    private int farmMgmtScroll = 0;
    private int farmMgmtSelectedPlayer = -1;  // 选中的在线玩家索引
    private int farmMgmtVisibleRows = 5;      // 动态计算的可见行数
    private List<ItemStack> craftingRecipeStacks = List.of();
    private List<String> craftingRecipeIds = List.of();
    private List<List<RecipeCell>> craftingPages = List.of();
    private final float[] recipeHoverScale = new float[CRAFTING_RECIPES_PER_PAGE];
    private int hoveredCraftingIndex = -1;
    private float upButtonScale = 1.0f;
    private float downButtonScale = 1.0f;
    private float trashCanLidRotation;
    private boolean trashCanLidSoundPlayed;
    private int socialScroll;
    private boolean socialScrolling;
    private LeaderboardMetric leaderboardMetric = LeaderboardMetric.MONEY;
    private LeaderboardPeriod leaderboardPeriod = LeaderboardPeriod.TOTAL;
    private int leaderboardPage;
    private int leaderboardScroll;
    private int leaderboardMetricTabScroll;
    private int leaderboardVisibleRows;

    // Social tuning is intentionally disabled for strict vanilla parity.
    private static final boolean socialTuneMode = false;
    private int socialTuneTarget = -1;
    private int socialTuneStep = 1;
    private boolean socialTuneDragging;
    private final int[] socialTuneOffsetX = new int[SOCIAL_TUNE_TARGET_COUNT];
    private final int[] socialTuneOffsetY = new int[SOCIAL_TUNE_TARGET_COUNT];

    private static final int SOCIAL_TUNE_TARGET_VLINE_LEFT = 0;
    private static final int SOCIAL_TUNE_TARGET_VLINE_MIDDLE = 1;
    private static final int SOCIAL_TUNE_TARGET_VLINE_RIGHT = 2;
    private static final int SOCIAL_TUNE_TARGET_HLINE_1 = 3;
    private static final int SOCIAL_TUNE_TARGET_HLINE_2 = 4;
    private static final int SOCIAL_TUNE_TARGET_HLINE_3 = 5;
    private static final int SOCIAL_TUNE_TARGET_HLINE_4 = 6;
    private static final int SOCIAL_TUNE_TARGET_PORTRAIT = 7;
    private static final int SOCIAL_TUNE_TARGET_NAME = 8;
    private static final int SOCIAL_TUNE_TARGET_HEARTS = 9;
    private static final int SOCIAL_TUNE_TARGET_GIFT_ICON = 10;
    private static final int SOCIAL_TUNE_TARGET_GIFT_BOX_1 = 11;
    private static final int SOCIAL_TUNE_TARGET_GIFT_BOX_2 = 12;
    private static final int SOCIAL_TUNE_TARGET_TALK_ICON = 13;
    private static final int SOCIAL_TUNE_TARGET_TALK_BOX = 14;
    private static final int SOCIAL_TUNE_TARGET_COUNT = 15;

    private static final String[] SOCIAL_TUNE_TARGET_LABELS = new String[] {
        "VLINE_LEFT", "VLINE_MIDDLE", "VLINE_RIGHT",
        "HLINE_1", "HLINE_2", "HLINE_3", "HLINE_4",
        "PORTRAIT", "NAME", "HEARTS",
        "GIFT_ICON", "GIFT_BOX_1", "GIFT_BOX_2",
        "TALK_ICON", "TALK_BOX"
    };

    private record RecipeRequirement(Ingredient ingredient, ItemStack icon, Component name, int need) {
    }

    private record CollectionEntry(ItemStack stack, boolean discovered, boolean known,
                                   VanillaObjectCatalog.Entry source) {
    }

    private record LetterCollectionEntry(String mailId, String title) {
    }

    private record RecipeCell(int recipeIndex, int x, int y, boolean bigCraftable) {
    }

    private record LeaderboardLayout(
            int contentX,
            int contentY,
            int contentW,
            int titleY,
            int metricY,
            int headerY,
            int listY,
            int listBottom,
            int selfY,
            int rowH,
            int visibleRows,
            int refreshX,
            int refreshY,
            int refreshW,
            int refreshH
    ) {
    }

    private record LeaderboardPageControls(int prevX, int labelX, int nextX, int y, int buttonW, int labelW, int h) {
    }

    private record LeaderboardMetricButtonBounds(LeaderboardMetric metric, int x, int y, int w, int h) {
    }

    private record LeaderboardPeriodButtonBounds(LeaderboardPeriod period, int x, int y, int w, int h) {
    }

    public StardewGameMenuScreen(StardewGameMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        this.mapping = new StardewRenderMapping(this.width, this.height, guiScale());
        this.imageWidth = ui(MENU_WIDTH_SDV);
        this.imageHeight = ui(MENU_HEIGHT_SDV);
        super.init();
        recalcLayout();
        rebuildCraftingEntries();
        playUiSound(ModSounds.BIG_SELECT.get(), 1.0f, 1.0f);
    }

    private float guiScale() {
        return this.minecraft == null ? 1.0f : (float) this.minecraft.getWindow().getGuiScale();
    }

    private int ui(int stardewPixels) {
        return mapping == null ? Math.round(stardewPixels / guiScale()) : mapping.ui(stardewPixels);
    }

    private void recalcLayout() {
        this.mapping = new StardewRenderMapping(this.width, this.height, guiScale());
        this.menuWidth = ui(MENU_WIDTH_SDV);
        this.menuHeight = ui(MENU_HEIGHT_SDV);
        this.menuX = mapping.centerX(this.menuWidth);
        this.menuY = this.height / 2 - this.menuHeight / 2;
        this.leftPos = this.menuX;
        this.topPos = this.menuY;
        this.imageWidth = this.menuWidth;
        this.imageHeight = this.menuHeight;
        positionInventorySlots();
    }

    private void positionInventorySlots() {
        int visualSize = ui(INVENTORY_SLOT_SDV);
        int itemOffset = Math.max(0, (visualSize - 16) / 2);

        boolean visible = currentTab == 0 || currentTab == 4;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < INVENTORY_COLS; col++) {
                int menuSlot = row * INVENTORY_COLS + col;
                int x = currentTab == 0 ? invPageSlotX(col) : inventorySlotX(col);
                int y = currentTab == 0
                        ? menuY + ui(INV_PAGE_ROW0_Y + row * INV_PAGE_ROW_STEP)
                        : inventorySlotY(row);
                positionInventorySlot(menuSlot, x, y, itemOffset, visible);
            }
        }

        int hotbarY = currentTab == 0 ? menuY + ui(INV_PAGE_HOTBAR_Y) : inventoryHotbarY();
        for (int col = 0; col < INVENTORY_COLS; col++) {
            int x = currentTab == 0 ? invPageSlotX(col) : inventorySlotX(col);
            positionInventorySlot(StardewGameMenu.MAIN_INVENTORY_SLOT_COUNT + col,
                    x, hotbarY, itemOffset, visible);
        }
    }

    private void positionInventorySlot(int menuSlotIndex, int screenX, int screenY,
                                       int itemOffset, boolean visible) {
        Slot slot = menu.slots.get(menuSlotIndex);
        if (!visible) {
            slot.x = -10_000;
            slot.y = -10_000;
            return;
        }
        slot.x = screenX - leftPos + itemOffset;
        slot.y = screenY - topPos + itemOffset;
    }

    private void playUiSound(SoundEvent sound, float volume, float pitch) {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, pitch));
        }
    }

    private int tabX(int index) {
        return menuX + ui(TAB_START_X_SDV + index * TAB_STEP_SDV);
    }

    private int tabY() {
        return menuY + ui(TAB_Y_OFFSET_SDV);
    }

    private int tabSize() {
        return ui(TAB_SIZE_SDV);
    }

    private boolean tabContains(int index, double mouseX, double mouseY) {
        int x = tabX(index);
        int y = tabY();
        int s = tabSize();
        return mouseX >= x && mouseX < x + s && mouseY >= y && mouseY < y + s;
    }

    private boolean closeContains(double mouseX, double mouseY) {
        int x = menuX + activeMenuWidth() - ui(CLOSE_X_OFFSET_SDV);
        int y = menuY - ui(CLOSE_Y_OFFSET_SDV);
        int s = ui(CLOSE_SIZE_SDV);
        return mouseX >= x && mouseX < x + s && mouseY >= y && mouseY < y + s;
    }

    private int activeMenuWidth() {
        if (currentTab == 1 && usesWideSkillsLayout()) {
            return skillsPageWidth();
        }
        if (currentTab == TAB_SOCIAL) {
            return socialPageWidth();
        }
        if (showingLeaderboardPage()) {
            return leaderboardPageWidth();
        }
        return menuWidth;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recalcLayout();
        this.hoveredSlot = activeMenuSlotAt(mouseX, mouseY);
        graphics.fill(0, 0, this.width, this.height, 0x66000000);

        if (currentTab == 4) {
            updateCraftingHoverState(mouseX, mouseY);
        }

        StardewGuiUtil.drawDialogueBoxFrame(graphics, menuX, menuY, activeMenuWidth(), menuHeight);
        drawTabs(graphics);
        drawCloseButton(graphics);
        drawCurrentPage(graphics, mouseX, mouseY);

        if (currentTab == 0) {
            drawInventoryPageTooltips(graphics, mouseX, mouseY);
            ItemStack carried = displayedCarriedItem();
            if (!carried.isEmpty()) {
                int itemSize = CommonGuiTextures.itemSize(mapping.s4());
                int drawX = mouseX - itemSize / 2;
                int drawY = mouseY - itemSize / 2;
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 500);
                CommonGuiTextures.drawItemWithDecorations(graphics, this.font, carried, drawX, drawY, mapping.s4());
                graphics.pose().popPose();
            }
        }

        if (currentTab == 4) {
            if (craftingPages.size() > 1) {
                drawPageArrows(graphics, mouseX, mouseY);
            }
            drawCraftingTooltips(graphics, mouseX, mouseY);
            ItemStack carried = displayedCarriedItem();
            if (!carried.isEmpty()) {
                int itemSize = CommonGuiTextures.itemSize(mapping.s4());
                int drawX = mouseX - itemSize / 2;
                int drawY = mouseY - itemSize / 2;
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 500);
                CommonGuiTextures.drawItemWithDecorations(graphics, this.font, carried, drawX, drawY, mapping.s4());
                graphics.pose().popPose();
            }
        }

        int hoveredTab = hoveredTab(mouseX, mouseY);
        if (hoveredTab >= 0) {
            graphics.renderTooltip(this.font, Component.translatable(TAB_KEYS[hoveredTab]), mouseX, mouseY);
        }

        if (currentTab == TAB_SOCIAL && socialTuneMode) {
            drawSocialTuneOverlay(graphics);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // This screen keeps its existing Stardew rendering pipeline in render().
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        // Player slots are rendered by drawInvPageGrid/drawPlayerInventory with SDV assets.
    }

    private int socialTuneUiX(int target) {
        return 0;
    }

    private int socialTuneUiY(int target) {
        return 0;
    }

    private int socialTuneHorizontalLineY(int lineIndex) {
        int target = SOCIAL_TUNE_TARGET_HLINE_1 + lineIndex;
        return socialVerticalStartY() + ui(lineIndex * SOCIAL_ROW_HEIGHT_SDV) + socialTuneUiY(target);
    }

    private int socialTuneHorizontalLineX(int lineIndex) {
        return menuX;
    }

    private int socialPageWidth() {
        return menuWidth + ui(36);
    }

    private int leaderboardPageWidth() {
        return menuWidth + ui(112);
    }

    private int socialPageRightX() {
        return menuX + socialPageWidth();
    }

    private int socialTuneVerticalLineX(int target) {
        return switch (target) {
            case SOCIAL_TUNE_TARGET_VLINE_LEFT -> menuX + ui(268);
            case SOCIAL_TUNE_TARGET_VLINE_MIDDLE -> menuX + ui(620);
            case SOCIAL_TUNE_TARGET_VLINE_RIGHT -> menuX + ui(752);
            default -> menuX;
        };
    }

    private int socialTuneVerticalLineStartY(int target) {
        // Align social columns with the first content row to avoid excess top blank space.
        return menuY + ui(64);
    }

    private int socialTuneTargetAt(double mouseX, double mouseY) {
        for (int target = 0; target < SOCIAL_TUNE_TARGET_COUNT; target++) {
            if (socialTuneTargetContains(target, mouseX, mouseY)) {
                return target;
            }
        }

        // Fallback: when clicking in social table area, snap to nearest separator target.
        int contentTop = menuY;
        int contentBottom = menuY + menuHeight;
        if (mouseX >= menuX && mouseX <= socialPageRightX() && mouseY >= contentTop && mouseY <= contentBottom) {
            int nearest = nearestSeparatorTarget(mouseX, mouseY);
            if (nearest >= 0) {
                return nearest;
            }
        }

        return -1;
    }

    private int nearestSeparatorTarget(double mouseX, double mouseY) {
        int bestTarget = SOCIAL_TUNE_TARGET_VLINE_LEFT;
        double bestDistance = Double.MAX_VALUE;

        int[] vTargets = {SOCIAL_TUNE_TARGET_VLINE_LEFT, SOCIAL_TUNE_TARGET_VLINE_MIDDLE, SOCIAL_TUNE_TARGET_VLINE_RIGHT};
        for (int target : vTargets) {
            double d = Math.abs(mouseX - socialTuneVerticalLineX(target));
            if (d < bestDistance) {
                bestDistance = d;
                bestTarget = target;
            }
        }

        for (int i = 0; i < 4; i++) {
            int target = SOCIAL_TUNE_TARGET_HLINE_1 + i;
            double d = Math.abs(mouseY - socialTuneHorizontalLineY(i));
            if (d < bestDistance) {
                bestDistance = d;
                bestTarget = target;
            }
        }

        double threshold = ui(24);
        return bestDistance <= threshold ? bestTarget : -1;
    }

    private boolean socialTuneTargetContains(int target, double mouseX, double mouseY) {
        int unit16 = ui(16);

        int x;
        int y;
        int w;
        int h;

        switch (target) {
            case SOCIAL_TUNE_TARGET_VLINE_LEFT, SOCIAL_TUNE_TARGET_VLINE_MIDDLE, SOCIAL_TUNE_TARGET_VLINE_RIGHT -> {
                int pad = ui(4);
                x = socialTuneVerticalLineX(target) - pad;
                y = socialTuneVerticalLineStartY(target);
                w = unit16 + pad * 2;
                h = Math.max(0, menuHeight - ui(128));
            }
            case SOCIAL_TUNE_TARGET_HLINE_1, SOCIAL_TUNE_TARGET_HLINE_2, SOCIAL_TUNE_TARGET_HLINE_3, SOCIAL_TUNE_TARGET_HLINE_4 -> {
                int lineIndex = target - SOCIAL_TUNE_TARGET_HLINE_1;
                int pad = ui(4);
                x = socialTuneHorizontalLineX(lineIndex);
                y = socialTuneHorizontalLineY(lineIndex) - pad;
                w = socialPageWidth();
                h = unit16 + pad * 2;
            }
            case SOCIAL_TUNE_TARGET_PORTRAIT -> {
                x = socialPortraitX();
                w = ui(64);
                h = ui(96);
                for (int row = 0; row < SOCIAL_MAX_VISIBLE; row++) {
                    y = socialRowPosition(row) + socialTuneUiY(SOCIAL_TUNE_TARGET_PORTRAIT);
                    if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                        return true;
                    }
                }
                return false;
            }
            case SOCIAL_TUNE_TARGET_NAME -> {
                x = socialNameCenterX() - ui(72);
                w = ui(144);
                h = ui(36);
                for (int row = 0; row < SOCIAL_MAX_VISIBLE; row++) {
                    y = socialRowPosition(row) + ui(16) + socialTuneUiY(SOCIAL_TUNE_TARGET_NAME);
                    if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                        return true;
                    }
                }
                return false;
            }
            case SOCIAL_TUNE_TARGET_HEARTS -> {
                x = socialHeartsBaseX();
                w = ui(320);
                h = ui(48);
                for (int row = 0; row < SOCIAL_MAX_VISIBLE; row++) {
                    y = socialRowPosition(row) + socialHeartsTopRowOffsetY();
                    if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                        return true;
                    }
                }
                return false;
            }
            case SOCIAL_TUNE_TARGET_GIFT_ICON -> {
                x = socialGiftIconX();
                w = ui(56);
                h = ui(48);
                for (int row = 0; row < SOCIAL_MAX_VISIBLE; row++) {
                    y = socialRowPosition(row) + socialGiftIconOffsetY();
                    if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                        return true;
                    }
                }
                return false;
            }
            case SOCIAL_TUNE_TARGET_GIFT_BOX_1 -> {
                x = socialGiftFirstBoxX();
                w = ui(36);
                h = ui(36);
                for (int row = 0; row < SOCIAL_MAX_VISIBLE; row++) {
                    y = socialRowPosition(row) + socialGiftBoxesOffsetY();
                    if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                        return true;
                    }
                }
                return false;
            }
            case SOCIAL_TUNE_TARGET_GIFT_BOX_2 -> {
                x = socialGiftSecondBoxX();
                w = ui(36);
                h = ui(36);
                for (int row = 0; row < SOCIAL_MAX_VISIBLE; row++) {
                    y = socialRowPosition(row) + socialGiftBoxesOffsetY();
                    if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                        return true;
                    }
                }
                return false;
            }
            case SOCIAL_TUNE_TARGET_TALK_ICON -> {
                x = socialTalkIconX();
                w = ui(52);
                h = ui(44);
                for (int row = 0; row < SOCIAL_MAX_VISIBLE; row++) {
                    y = socialRowPosition(row) + socialTalkIconOffsetY();
                    if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                        return true;
                    }
                }
                return false;
            }
            case SOCIAL_TUNE_TARGET_TALK_BOX -> {
                x = socialTalkBoxX();
                w = ui(36);
                h = ui(36);
                for (int row = 0; row < SOCIAL_MAX_VISIBLE; row++) {
                    y = socialRowPosition(row) + socialTalkBoxOffsetY();
                    if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                        return true;
                    }
                }
                return false;
            }
            default -> {
                return false;
            }
        }

        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawSocialTuneOverlay(GuiGraphics graphics) {
        int x = menuX + ui(16);
        int y = menuY - ui(22);
        String label = socialTuneTarget >= 0 && socialTuneTarget < SOCIAL_TUNE_TARGET_LABELS.length
            ? SOCIAL_TUNE_TARGET_LABELS[socialTuneTarget]
            : "NONE";
        String line = "SocialTune[F6] LeftClick=select Drag=move Wheel=step(" + socialTuneStep + ") R=reset selected Shift+R=reset all target=" + label;
        graphics.drawString(this.font, line, x, y, 0xFFEEE2C2, false);
    }

    private boolean isShiftPressed(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    private boolean handleSocialTuneKey(int keyCode, int modifiers) {
        return false;
    }

    private void applySocialTuneDelta(int dx, int dy) {
        if (socialTuneTarget < 0 || socialTuneTarget >= SOCIAL_TUNE_TARGET_COUNT) {
            return;
        }
        socialTuneOffsetX[socialTuneTarget] += dx;
        socialTuneOffsetY[socialTuneTarget] += dy;
    }

    private void resetAllSocialTuneOffsets() {
        for (int i = 0; i < SOCIAL_TUNE_TARGET_COUNT; i++) {
            socialTuneOffsetX[i] = 0;
            socialTuneOffsetY[i] = 0;
        }
    }

    private void resetSocialTuneTarget(int target) {
        if (target < 0 || target >= SOCIAL_TUNE_TARGET_COUNT) {
            return;
        }
        socialTuneOffsetX[target] = 0;
        socialTuneOffsetY[target] = 0;
    }

    private int hoveredTab(int mouseX, int mouseY) {
        for (int i = 0; i < TAB_COUNT; i++) {
            if (tabContains(i, mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    private void drawTabs(GuiGraphics graphics) {
        int yBase = tabY();
        int selectedOffsetY = ui(8);
        float scale = mapping.s4();

        for (int i = 0; i < TAB_COUNT; i++) {
            int x = tabX(i);
            int y = yBase + (currentTab == i ? selectedOffsetY : 0);

            CommonGuiTextures.drawGameMenuTab(graphics, x, y, i, scale);
            if (i == 1) {
                drawSkillsTabFace(graphics, x, y);
            }
        }
    }

    /** Vanilla overlays the current farmer portrait on the otherwise blank skills tab. */
    private void drawSkillsTabFace(GuiGraphics graphics, int tabX, int tabY) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        ResourceLocation skin = this.minecraft.player.getSkin().texture();
        int faceSize = ui(32);
        int faceX = tabX + ui(16);
        int faceY = tabY + ui(16);
        graphics.blit(skin, faceX, faceY, faceSize, faceSize, 8, 8, 8, 8, 64, 64);
        graphics.blit(skin, faceX, faceY, faceSize, faceSize, 40, 8, 8, 8, 64, 64);
    }

    private void drawCloseButton(GuiGraphics graphics) {
        int x = menuX + activeMenuWidth() - ui(CLOSE_X_OFFSET_SDV);
        int y = menuY - ui(CLOSE_Y_OFFSET_SDV);
        CommonGuiTextures.drawCloseButton(graphics, x, y, mapping.s4());
    }

    private void drawCurrentPage(GuiGraphics graphics, int mouseX, int mouseY) {
        if (currentTab == TAB_SOCIAL) {
            drawSocialPage(graphics, mouseX, mouseY);
            return;
        }

        if (currentTab == 0) {
            drawInventoryPage(graphics, mouseX, mouseY);
            return;
        }

        if (currentTab == 1) {
            drawSkillsPage(graphics, mouseX, mouseY);
            return;
        }

        if (currentTab == 4) {
            drawCraftingPage(graphics, mouseX, mouseY);
            return;
        }

        if (currentTab == 3) {
            drawFarmManagementPage(graphics, mouseX, mouseY);
            return;
        }

        if (currentTab == TAB_POWERS) {
            drawPowersPage(graphics, mouseX, mouseY);
            return;
        }

        if (currentTab == TAB_COLLECTIONS) {
            drawCollectionsPage(graphics, mouseX, mouseY);
            return;
        }

        if (currentTab == TAB_OPTIONS) {
            drawOptionsPage(graphics, mouseX, mouseY);
            return;
        }

        Component tabName = Component.translatable(TAB_KEYS[currentTab]);
        Component title = Component.translatable("stardewcraft.game_menu.placeholder_title", tabName);
        Component line = Component.translatable("stardewcraft.game_menu.placeholder_body");

        int titleX = menuX + menuWidth / 2 - this.font.width(title) / 2;
        int lineX = menuX + menuWidth / 2 - this.font.width(line) / 2;
        int centerY = menuY + menuHeight / 2;

        graphics.drawString(this.font, title, titleX, centerY - 10, 0xFFF3E6C6, false);
        graphics.drawString(this.font, line, lineX, centerY + 8, 0xFFD8C9A8, false);
    }

    // ============ Tab 8: Options Page (设置 / 排行榜入口) ============

    private boolean showingLeaderboardPage() {
        return currentTab == TAB_OPTIONS && currentOptionsPage == OPTIONS_PAGE_LEADERBOARD;
    }

    private void drawOptionsPage(GuiGraphics graphics, int mouseX, int mouseY) {
        if (showingLeaderboardPage()) {
            drawLeaderboardPage(graphics, mouseX, mouseY);
            drawOptionsBackButton(graphics, optionsLeaderboardBackX(), optionsLeaderboardBackY(),
                    optionsLeaderboardBackWidth(), optionsButtonHeight(),
                    Component.translatable("stardewcraft.game_menu.options.settings"),
                    optionContains(mouseX, mouseY, optionsLeaderboardBackX(), optionsLeaderboardBackY(),
                            optionsLeaderboardBackWidth(), optionsButtonHeight()));
            return;
        }

        Component title = Component.translatable("stardewcraft.game_menu.options.title");
        graphics.drawString(this.font, title,
                menuX + menuWidth / 2 - this.font.width(title) / 2,
                menuY + ui(88), 0xFF5B2F18, false);

        int rowX = optionsRowX();
        int rowWidth = optionsRowWidth();
        int rowHeight = optionsRowHeight();
        int editorY = optionsRowY(0);
        int leaderboardY = optionsRowY(1);
        drawOptionsButton(graphics, rowX, editorY, rowWidth, rowHeight,
                Component.translatable("stardewcraft.game_menu.options.hud_position"),
                optionContains(mouseX, mouseY, rowX, editorY, rowWidth, rowHeight));
        drawOptionsButton(graphics, rowX, leaderboardY, rowWidth, rowHeight,
                Component.translatable("stardewcraft.game_menu.options.leaderboard"),
                optionContains(mouseX, mouseY, rowX, leaderboardY, rowWidth, rowHeight));
    }

    private void drawOptionsButton(GuiGraphics graphics, int x, int y, int width, int height,
                                   Component label, boolean hovered) {
        int textY = y + (height - this.font.lineHeight) / 2;
        if (hovered) {
            graphics.fill(x, y, x + width, y + height, 0x18FFFFFF);
        }
        graphics.fill(x, y + height - 1, x + width, y + height, 0x508B572A);
        graphics.drawString(this.font, label, x + ui(24), textY, 0xFF4A2815, false);
        float arrowScale = 1.0F;
        int arrowWidth = Math.round(12 * arrowScale);
        int arrowHeight = Math.round(11 * arrowScale);
        CommonGuiTextures.drawForwardArrow(graphics, x + width - ui(24) - arrowWidth,
                y + (height - arrowHeight) / 2, arrowScale);
    }

    private void drawOptionsBackButton(GuiGraphics graphics, int x, int y, int width, int height,
                                       Component label, boolean hovered) {
        if (hovered) {
            graphics.fill(x, y, x + width, y + height, 0x18FFFFFF);
        }
        int arrowWidth = 12;
        int arrowHeight = 11;
        CommonGuiTextures.drawBackArrow(graphics, x + ui(12), y + (height - arrowHeight) / 2, 1.0F);
        graphics.drawString(this.font, label, x + ui(12) + arrowWidth + ui(10),
                y + (height - this.font.lineHeight) / 2, 0xFF4A2815, false);
    }

    private int optionsRowWidth() {
        return Math.min(menuWidth - ui(120), Math.max(ui(420), menuWidth * 3 / 5));
    }

    private int optionsRowX() {
        return menuX + (menuWidth - optionsRowWidth()) / 2;
    }

    private int optionsRowHeight() {
        return Math.max(this.font.lineHeight + 10, ui(64));
    }

    private int optionsRowY(int index) {
        return menuY + ui(220) + index * (optionsRowHeight() + ui(28));
    }

    private int optionsButtonHeight() {
        return Math.max(this.font.lineHeight + 4, ui(48));
    }

    private int optionButtonWidth(Component label, int minimumSdvWidth) {
        return Math.min(menuWidth - ui(80), Math.max(ui(minimumSdvWidth), this.font.width(label) + 12));
    }

    private boolean optionContains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int optionsLeaderboardBackX() {
        return leaderboardLayout().contentX() + ui(8);
    }

    private int optionsLeaderboardBackY() {
        return leaderboardLayout().titleY() - ui(6);
    }

    private int optionsLeaderboardBackWidth() {
        Component label = Component.translatable("stardewcraft.game_menu.options.settings");
        return Math.max(ui(150), this.font.width(label) + ui(44));
    }

    private boolean handleOptionsClick(double mouseX, double mouseY) {
        if (showingLeaderboardPage()) {
            if (optionContains(mouseX, mouseY, optionsLeaderboardBackX(), optionsLeaderboardBackY(),
                    optionsLeaderboardBackWidth(), optionsButtonHeight())) {
                currentOptionsPage = OPTIONS_PAGE_SETTINGS;
                playUiSound(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
                return true;
            }
            return handleLeaderboardClick((int) mouseX, (int) mouseY);
        }

        int rowX = optionsRowX();
        int rowWidth = optionsRowWidth();
        int rowHeight = optionsRowHeight();
        int editorY = optionsRowY(0);
        if (optionContains(mouseX, mouseY, rowX, editorY, rowWidth, rowHeight)) {
            playUiSound(ModSounds.BIG_SELECT.get(), 1.0F, 1.0F);
            this.minecraft.setScreen(new StardewHudLayoutEditorScreen(this));
            return true;
        }

        int leaderboardY = optionsRowY(1);
        if (optionContains(mouseX, mouseY, rowX, leaderboardY, rowWidth, rowHeight)) {
            currentOptionsPage = OPTIONS_PAGE_LEADERBOARD;
            leaderboardScroll = 0;
            requestLeaderboard();
            playUiSound(ModSounds.BIG_SELECT.get(), 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    // ============ Tab 8: Leaderboard Page (排行榜) ============

    private LeaderboardLayout leaderboardLayout() {
        int pageWidth = activeMenuWidth();
        int contentX = menuX + ui(64);
        int contentY = menuY + ui(50);
        int contentW = pageWidth - ui(128);
        int contentBottom = menuY + menuHeight - ui(48);
        int rowH = ui(54);
        int titleY = contentY;
        int titleH = Math.round(this.font.lineHeight * 1.7f);
        int controlY = titleY + titleH + ui(14);
        int controlH = Math.max(ui(36), this.font.lineHeight + ui(12));
        int metricY = menuY + ui(78);
        int headerY = controlY + controlH + ui(26);
        int listY = headerY + this.font.lineHeight + ui(16);
        int selfY = contentBottom - rowH;
        int listBottom = selfY - ui(18);
        int visibleRows = Math.max(1, (listBottom - listY) / rowH);
        Component refresh = Component.translatable("stardewcraft.leaderboard.refresh");
        int refreshW = Math.max(ui(78), this.font.width(refresh) + ui(24));
        int refreshH = controlH;
        int refreshX = contentX + contentW - refreshW;
        int refreshY = controlY;
        return new LeaderboardLayout(contentX, contentY, contentW, titleY, metricY, headerY, listY,
                listBottom, selfY, rowH, visibleRows, refreshX, refreshY, refreshW, refreshH);
    }

    private void drawLeaderboardPage(GuiGraphics graphics, int mouseX, int mouseY) {
        LeaderboardLayout layout = leaderboardLayout();
        leaderboardVisibleRows = layout.visibleRows();
        clampLeaderboardScroll();

        drawLeaderboardHeader(graphics, layout);
        drawLeaderboardMeta(graphics, layout);

        drawLeaderboardMetricButtons(graphics, layout, mouseX, mouseY);
        drawLeaderboardPeriodButtons(graphics, layout, mouseX, mouseY);
        drawLeaderboardRefreshButton(graphics, layout, mouseX, mouseY);
        drawLeaderboardPageControls(graphics, layout, mouseX, mouseY);

        drawLeaderboardListPanel(graphics, layout);

        int rankX = leaderboardRankX(layout);
        int nameX = leaderboardNameX(layout);
        int valueRightX = leaderboardValueRightX(layout);
        graphics.drawString(this.font, Component.literal("#"), rankX, layout.headerY(), 0x8D6E63, false);
        graphics.drawString(this.font, Component.translatable("stardewcraft.leaderboard.player"), nameX, layout.headerY(), 0x8D6E63, false);
        Component valueHeader = Component.translatable("stardewcraft.leaderboard.value");
        graphics.drawString(this.font, valueHeader, valueRightX - this.font.width(valueHeader), layout.headerY(), 0x8D6E63, false);
        drawLeaderboardTableRule(graphics, layout, leaderboardHeaderRuleY(layout));

        if (LeaderboardClientCache.isLoading(leaderboardMetric.id(), leaderboardPeriod.id(), leaderboardPage)) {
            drawLeaderboardCenteredMessage(graphics, layout, Component.translatable("stardewcraft.leaderboard.loading"));
            drawLeaderboardSelfRow(graphics, layout, null);
            drawLeaderboardTooltips(graphics, layout, mouseX, mouseY);
            return;
        }

        if (LeaderboardClientCache.hasError(leaderboardMetric.id(), leaderboardPeriod.id(), leaderboardPage)) {
            drawLeaderboardCenteredMessage(graphics, layout, Component.translatable(LeaderboardClientCache.getErrorKey()));
            drawLeaderboardSelfRow(graphics, layout, null);
            drawLeaderboardTooltips(graphics, layout, mouseX, mouseY);
            return;
        }

        if (!LeaderboardClientCache.hasData(leaderboardMetric.id(), leaderboardPeriod.id(), leaderboardPage)) {
            drawLeaderboardCenteredMessage(graphics, layout, Component.translatable("stardewcraft.leaderboard.loading"));
            drawLeaderboardSelfRow(graphics, layout, null);
            drawLeaderboardTooltips(graphics, layout, mouseX, mouseY);
            return;
        }

        List<LeaderboardSyncPayload.Entry> rows = LeaderboardClientCache.getRows();
        if (rows.isEmpty()) {
            drawLeaderboardCenteredMessage(graphics, layout, Component.translatable("stardewcraft.leaderboard.empty"));
        } else {
            drawLeaderboardRows(graphics, layout, rows, mouseX, mouseY);
        }
        drawLeaderboardScrollBar(graphics, layout, rows.size());
        drawLeaderboardSelfRow(graphics, layout, LeaderboardClientCache.getSelfEntry());
        drawLeaderboardTooltips(graphics, layout, mouseX, mouseY);
    }

    private void drawLeaderboardHeader(GuiGraphics graphics, LeaderboardLayout layout) {
        int bandX = layout.contentX() + ui(2);
        int bandY = layout.titleY() - ui(8);
        int bandW = layout.contentW() - ui(4);
        int bandH = layout.refreshY() + layout.refreshH() - bandY + ui(10);
        graphics.fill(bandX, bandY, bandX + bandW, bandY + bandH, 0x18B08830);
        graphics.fill(bandX, bandY, bandX + bandW, bandY + Math.max(1, ui(2)), 0x66B08830);
        graphics.fill(bandX, bandY + bandH - Math.max(1, ui(2)), bandX + bandW, bandY + bandH, 0x66B08830);

        int titleCenterX = layout.contentX() + layout.contentW() / 2;
        Component title = Component.translatable(leaderboardMetric.titleKey()).withStyle(ChatFormatting.BOLD);
        drawScaledCenteredSdvText(graphics, title.getString(), titleCenterX,
            layout.titleY(), 1.7f, layout.contentW() - ui(72), 0xFF582A11);
    }

    private void drawLeaderboardListPanel(GuiGraphics graphics, LeaderboardLayout layout) {
        int x = layout.contentX() + ui(2);
        int y = layout.headerY() - ui(10);
        int w = layout.contentW() - ui(4);
        int h = layout.selfY() + layout.rowH() - y;
        graphics.fill(x, y, x + w, y + h, 0x10FFFFFF);
        graphics.fill(x, y, x + w, y + Math.max(1, ui(2)), 0x558D6E63);
        graphics.fill(x, y + h - Math.max(1, ui(2)), x + w, y + h, 0x448D6E63);
        graphics.fill(x, y, x + Math.max(1, ui(2)), y + h, 0x338D6E63);
        graphics.fill(x + w - Math.max(1, ui(2)), y, x + w, y + h, 0x338D6E63);
    }

    private void drawLeaderboardMetricButtons(GuiGraphics graphics, LeaderboardLayout layout, int mouseX, int mouseY) {
        for (LeaderboardMetricButtonBounds bounds : leaderboardMetricButtonBounds(layout)) {
            LeaderboardMetric metric = bounds.metric();
            boolean active = metric == leaderboardMetric;
            boolean hovered = inside(mouseX, mouseY, bounds.x(), bounds.y(), bounds.w() + ui(8), bounds.h());
            int color = active ? LEADERBOARD_TAB_ACT : hovered ? LEADERBOARD_TAB_HOV : LEADERBOARD_TAB_NRM;
            int border = active ? LEADERBOARD_GOLD : LEADERBOARD_TAB_BDR;
            int tx = active ? bounds.x() + ui(8) : bounds.x();
            int tw = active ? bounds.w() + ui(8) : bounds.w();

            graphics.fill(tx + 1, bounds.y() + 1, tx + tw - 1, bounds.y() + bounds.h() - 1, color);
            graphics.fill(tx, bounds.y() + 2, tx + 1, bounds.y() + bounds.h() - 2, color);
            graphics.fill(tx + tw - 1, bounds.y() + 2, tx + tw, bounds.y() + bounds.h() - 2, color);
            graphics.fill(tx + 2, bounds.y(), tx + tw - 2, bounds.y() + 1, color);
            graphics.fill(tx + 2, bounds.y() + bounds.h() - 1, tx + tw - 2, bounds.y() + bounds.h(), color);

            graphics.fill(tx + 2, bounds.y(), tx + tw - 2, bounds.y() + 1, border);
            graphics.fill(tx + 2, bounds.y() + bounds.h() - 1, tx + tw - 2, bounds.y() + bounds.h(), border);
            graphics.fill(tx, bounds.y() + 2, tx + 1, bounds.y() + bounds.h() - 2, border);
            if (!active) {
                graphics.fill(tx + tw - 1, bounds.y() + 2, tx + tw, bounds.y() + bounds.h() - 2, border);
            }

            drawLeaderboardMetricIcon(graphics, metric, tx, bounds.y(), tw, bounds.h());
        }
        drawLeaderboardMetricScrollHints(graphics, layout);
    }

    private void drawLeaderboardMetricScrollHints(GuiGraphics graphics, LeaderboardLayout layout) {
        int maxScroll = leaderboardMetricMaxTabScroll(layout);
        if (maxScroll <= 0) {
            return;
        }

        int tabW = leaderboardMetricButtonWidth();
        int x = menuX - tabW - ui(4);
        int hintH = ui(LEADERBOARD_METRIC_SCROLL_HINT_SDV);
        float arrowScale = 0.8f * mapping.s4();
        int arrowW = Math.round(11.0f * arrowScale);
        int arrowH = Math.round(12.0f * arrowScale);
        int arrowX = x + (tabW - arrowW) / 2;
        int upY = layout.metricY() + (hintH - arrowH) / 2;
        int downY = leaderboardMetricStripBottom(layout) + (hintH - arrowH) / 2;

        float upAlpha = leaderboardMetricTabScroll > 0 ? 1.0f : 0.35f;
        float downAlpha = leaderboardMetricTabScroll < maxScroll ? 1.0f : 0.35f;
        CommonGuiTextures.drawScrollArrowUpTint(graphics, arrowX, upY, arrowScale, 1.0f, 1.0f, 1.0f, upAlpha);
        CommonGuiTextures.drawScrollArrowDownTint(graphics, arrowX, downY, arrowScale, 1.0f, 1.0f, 1.0f, downAlpha);
    }

    private void drawLeaderboardPeriodButtons(GuiGraphics graphics, LeaderboardLayout layout, int mouseX, int mouseY) {
        for (LeaderboardPeriodButtonBounds bounds : leaderboardPeriodButtonBounds(layout)) {
            LeaderboardPeriod period = bounds.period();
            boolean enabled = leaderboardMetric.supportsPeriod(period);
            boolean active = period == leaderboardPeriod;
            boolean hovered = inside(mouseX, mouseY, bounds.x(), bounds.y(), bounds.w(), bounds.h());
            drawLeaderboardButtonBg(graphics, bounds.x(), bounds.y(), bounds.w(), bounds.h(), hovered && enabled, enabled);
            if (active) {
                graphics.fill(bounds.x() + ui(3), bounds.y() + bounds.h() - Math.max(1, ui(3)),
                        bounds.x() + bounds.w() - ui(3), bounds.y() + bounds.h() - Math.max(1, ui(1)), LEADERBOARD_GOLD);
            }
            ItemStack icon = leaderboardPeriodIcon(period);
            float iconScale = 0.6f * mapping.s4();
            int scaledSize = CommonGuiTextures.itemSize(iconScale);
            int iconX = bounds.x() + (bounds.w() - scaledSize) / 2;
            int iconY = bounds.y() + (bounds.h() - scaledSize) / 2;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 100);
            CommonGuiTextures.drawItem(graphics, icon, iconX, iconY, iconScale);
            graphics.pose().popPose();
        }
    }

    private void drawLeaderboardMeta(GuiGraphics graphics, LeaderboardLayout layout) {
        if (!LeaderboardClientCache.hasData(leaderboardMetric.id(), leaderboardPeriod.id(), leaderboardPage)) {
            return;
        }
        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - LeaderboardClientCache.getGeneratedAtMillis()) / 1000L);
        Component meta = Component.translatable("stardewcraft.leaderboard.meta", LeaderboardClientCache.getTotalPlayers(), ageSeconds);
        LeaderboardPageControls controls = leaderboardPageControls(layout);
        List<LeaderboardPeriodButtonBounds> periodBounds = leaderboardPeriodButtonBounds(layout);
        int metaX = periodBounds.isEmpty() ? layout.contentX() : periodBounds.get(periodBounds.size() - 1).x() + periodBounds.get(periodBounds.size() - 1).w() + ui(12);
        int maxW = Math.max(ui(80), controls.prevX() - metaX - ui(12));
        graphics.drawString(this.font, Component.literal(ellipsize(meta.getString(), maxW)), metaX,
            layout.refreshY() + (layout.refreshH() - this.font.lineHeight) / 2, 0x8D6E63, false);
    }

    private void drawLeaderboardRefreshButton(GuiGraphics graphics, LeaderboardLayout layout, int mouseX, int mouseY) {
        Component label = Component.translatable("stardewcraft.leaderboard.refresh");
        boolean hovered = inside(mouseX, mouseY, layout.refreshX(), layout.refreshY(), layout.refreshW(), layout.refreshH());
        drawLeaderboardButtonBg(graphics, layout.refreshX(), layout.refreshY(), layout.refreshW(), layout.refreshH(), hovered, true);
        graphics.drawString(this.font, label, layout.refreshX() + (layout.refreshW() - this.font.width(label)) / 2,
                layout.refreshY() + (layout.refreshH() - this.font.lineHeight) / 2, 0xFF582A11, false);
    }

    private void drawLeaderboardPageControls(GuiGraphics graphics, LeaderboardLayout layout, int mouseX, int mouseY) {
        LeaderboardPageControls controls = leaderboardPageControls(layout);
        int pageCount = leaderboardPageCount();
        boolean canPrev = leaderboardPage > 0 && !LeaderboardClientCache.isLoading(leaderboardMetric.id(), leaderboardPeriod.id(), leaderboardPage);
        boolean canNext = leaderboardPage + 1 < pageCount && !LeaderboardClientCache.isLoading(leaderboardMetric.id(), leaderboardPeriod.id(), leaderboardPage);
        drawLeaderboardPageButton(graphics, controls.prevX(), controls.y(), controls.buttonW(), controls.h(), Component.literal("<"), canPrev,
                inside(mouseX, mouseY, controls.prevX(), controls.y(), controls.buttonW(), controls.h()));
        Component page = Component.translatable("stardewcraft.leaderboard.page", leaderboardPage + 1, pageCount);
        graphics.drawString(this.font, page, controls.labelX() + (controls.labelW() - this.font.width(page)) / 2,
                controls.y() + (controls.h() - this.font.lineHeight) / 2, 0xFF582A11, false);
        drawLeaderboardPageButton(graphics, controls.nextX(), controls.y(), controls.buttonW(), controls.h(), Component.literal(">"), canNext,
                inside(mouseX, mouseY, controls.nextX(), controls.y(), controls.buttonW(), controls.h()));
    }

    private void drawLeaderboardPageButton(GuiGraphics graphics, int x, int y, int w, int h, Component label, boolean enabled, boolean hovered) {
        drawLeaderboardButtonBg(graphics, x, y, w, h, hovered, enabled);
        int textColor = enabled ? 0xFF582A11 : 0xFF8D6E63;
        graphics.drawString(this.font, label, x + (w - this.font.width(label)) / 2,
                y + (h - this.font.lineHeight) / 2, textColor, false);
    }

    private void drawLeaderboardButtonBg(GuiGraphics graphics, int x, int y, int w, int h, boolean hovered, boolean enabled) {
        int color = !enabled ? 0x669B7A49 : hovered ? 0xFFE1B86A : 0xFFC99A5A;
        int border = enabled ? 0xFF8C632B : 0x668C632B;
        graphics.fill(x + 1, y, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + w, y + h - 1, color);
        graphics.fill(x + 2, y, x + w - 2, y + 1, border);
        graphics.fill(x + 2, y + h - 1, x + w - 2, y + h, border);
        graphics.fill(x, y + 2, x + 1, y + h - 2, border);
        graphics.fill(x + w - 1, y + 2, x + w, y + h - 2, border);
    }

    private void drawLeaderboardRows(GuiGraphics graphics, LeaderboardLayout layout, List<LeaderboardSyncPayload.Entry> rows, int mouseX, int mouseY) {
        graphics.enableScissor(layout.contentX(), layout.listY(), layout.contentX() + layout.contentW(), layout.listBottom());
        int visible = Math.min(layout.visibleRows(), Math.max(0, rows.size() - leaderboardScroll));
        for (int i = 0; i < visible; i++) {
            int index = leaderboardScroll + i;
            int rowY = layout.listY() + i * layout.rowH();
            drawLeaderboardRow(graphics, layout, rows.get(index), rowY, index, mouseX, mouseY, false);
        }
        graphics.disableScissor();
    }

    private void drawLeaderboardSelfRow(GuiGraphics graphics, LeaderboardLayout layout, LeaderboardSyncPayload.Entry selfEntry) {
        drawLeaderboardTableRule(graphics, layout, leaderboardSelfRuleY(layout));
        if (selfEntry == null) {
            Component text = Component.translatable("stardewcraft.leaderboard.no_self");
            graphics.drawString(this.font, text, layout.contentX() + ui(18), layout.selfY() + (layout.rowH() - this.font.lineHeight) / 2, 0x8D6E63, false);
            return;
        }
        drawLeaderboardRow(graphics, layout, selfEntry, layout.selfY(), selfEntry.rank() - 1, -1, -1, true);
    }

    private void drawLeaderboardRow(GuiGraphics graphics, LeaderboardLayout layout, LeaderboardSyncPayload.Entry row,
                                    int rowY, int index, int mouseX, int mouseY, boolean selfRow) {
        int rowX = layout.contentX() + ui(8);
        int rowW = layout.contentW() - ui(20);
        boolean hovered = !selfRow && inside(mouseX, mouseY, rowX, rowY, rowW, layout.rowH());
        boolean medal = row.rank() >= 1 && row.rank() <= 3;
        int fill = medal ? medalRowFill(row.rank()) : row.self() ? 0x22EADB8C : hovered ? 0x30F0D880 : (index % 2 == 0 ? 0x14FFFFFF : 0x08000000);
        int border = medal ? medalColor(row.rank()) : row.self() ? 0x775E8C3A : 0x338D6E63;
        int y1 = rowY + ui(3);
        int y2 = rowY + layout.rowH() - ui(3);
        graphics.fill(rowX + 1, y1, rowX + rowW - 1, y2, fill);
        graphics.fill(rowX, y1 + 1, rowX + rowW, y2 - 1, fill);
        if (medal) {
            graphics.fill(rowX + ui(4), y1 + ui(4), rowX + rowW - ui(4), y1 + ui(7), medalShineColor(row.rank()));
            graphics.fill(rowX + ui(4), y2 - ui(7), rowX + rowW - ui(4), y2 - ui(4), medalShadowColor(row.rank()));
        }
        graphics.fill(rowX + 2, y1, rowX + rowW - 2, y1 + Math.max(1, ui(2)), border);
        graphics.fill(rowX + 2, y2 - Math.max(1, ui(2)), rowX + rowW - 2, y2, border);
        graphics.fill(rowX, y1 + 2, rowX + Math.max(1, ui(2)), y2 - 2, border);
        graphics.fill(rowX + rowW - Math.max(1, ui(2)), y1 + 2, rowX + rowW, y2 - 2, border);

        int textY = rowY + (layout.rowH() - this.font.lineHeight) / 2;
        int badgeX = leaderboardRankX(layout);
        int badgeW = ui(42);
        int badgeH = Math.max(ui(26), this.font.lineHeight + ui(8));
        int badgeY = rowY + (layout.rowH() - badgeH) / 2;
        int badgeFill = medal ? medalColor(row.rank()) : row.self() ? 0xFFB9D88B : 0xFFE8D8B0;
        int badgeText = medal ? 0xFFFFFFFF : row.self() ? 0xFF2E7D32 : 0xFF582A11;
        graphics.fill(badgeX, badgeY + 1, badgeX + badgeW, badgeY + badgeH - 1, badgeFill);
        graphics.fill(badgeX + 1, badgeY, badgeX + badgeW - 1, badgeY + badgeH, badgeFill);
        graphics.fill(badgeX + 2, badgeY, badgeX + badgeW - 2, badgeY + Math.max(1, ui(2)), medal ? 0xAAFFFFFF : 0x448D6E63);
        graphics.fill(badgeX + 2, badgeY + badgeH - Math.max(1, ui(2)), badgeX + badgeW - 2, badgeY + badgeH, medal ? 0x44000000 : 0x448D6E63);
        Component rank = Component.literal(String.valueOf(row.rank()));
        graphics.drawString(this.font, rank, badgeX + (badgeW - this.font.width(rank)) / 2,
                badgeY + (badgeH - this.font.lineHeight) / 2, badgeText, false);

        int dotX = leaderboardNameX(layout) - ui(18);
        int dotColor = row.online() ? 0xFF4CAF50 : 0xFF9E9E9E;
        int dotSize = Math.max(2, ui(7));
        graphics.fill(dotX, textY + ui(2), dotX + dotSize, textY + ui(2) + dotSize, dotColor);

        int nameMaxW = Math.max(ui(80), leaderboardValueRightX(layout) - leaderboardNameX(layout) - ui(120));
        String name = ellipsize(row.playerName(), nameMaxW);
        int nameColor = medal ? 0xFF3C2410 : row.self() ? 0xFF2E7D32 : 0xFF582A11;
        graphics.drawString(this.font, row.self() ? Component.literal(name).withStyle(ChatFormatting.BOLD) : Component.literal(name),
                leaderboardNameX(layout), textY, nameColor, false);

        Component value = Component.translatable(leaderboardMetric.valueKey(), row.value());
        int valueX = leaderboardValueRightX(layout) - this.font.width(value);
        int pillPad = ui(10);
        int pillX = valueX - pillPad;
        int pillY = textY - ui(4);
        int pillH = this.font.lineHeight + ui(8);
        int pillFill = medal ? medalPillColor(row.rank()) : 0x22B08830;
        graphics.fill(pillX + 1, pillY, leaderboardValueRightX(layout) + pillPad - 1, pillY + pillH, pillFill);
        graphics.fill(pillX, pillY + 1, leaderboardValueRightX(layout) + pillPad, pillY + pillH - 1, pillFill);
        graphics.drawString(this.font, value, valueX, textY, nameColor, false);
    }

    private int medalRowFill(int rank) {
        return switch (rank) {
            case 1 -> 0x55F5C64F;
            case 2 -> 0x44D8D8D8;
            case 3 -> 0x44C58A54;
            default -> 0x14FFFFFF;
        };
    }

    private int medalShineColor(int rank) {
        return switch (rank) {
            case 1 -> 0x66FFF2A6;
            case 2 -> 0x66FFFFFF;
            case 3 -> 0x55F0B77A;
            default -> 0x22FFFFFF;
        };
    }

    private int medalShadowColor(int rank) {
        return switch (rank) {
            case 1 -> 0x33805A12;
            case 2 -> 0x33707070;
            case 3 -> 0x33703F1E;
            default -> 0x22000000;
        };
    }

    private int medalPillColor(int rank) {
        return switch (rank) {
            case 1 -> 0x44FFF2A6;
            case 2 -> 0x44FFFFFF;
            case 3 -> 0x33F0B77A;
            default -> 0x22B08830;
        };
    }

    private int medalColor(int rank) {
        return switch (rank) {
            case 1 -> 0xFFE1B86A;
            case 2 -> 0xFFC7C7C7;
            case 3 -> 0xFFB87943;
            default -> 0x668D6E63;
        };
    }

    private void drawLeaderboardScrollBar(GuiGraphics graphics, LeaderboardLayout layout, int rowCount) {
        if (rowCount <= layout.visibleRows()) {
            return;
        }
        int barX = layout.contentX() + layout.contentW() - ui(6);
        int barTotalH = layout.visibleRows() * layout.rowH();
        int thumbH = Math.max(ui(20), barTotalH * layout.visibleRows() / rowCount);
        int maxScroll = Math.max(1, rowCount - layout.visibleRows());
        int thumbY = layout.listY() + (barTotalH - thumbH) * leaderboardScroll / maxScroll;
        graphics.fill(barX, layout.listY(), barX + ui(3), layout.listY() + barTotalH, 0x22000000);
        graphics.fill(barX, thumbY, barX + ui(3), thumbY + thumbH, 0x66582A11);
    }

    private void drawLeaderboardTableRule(GuiGraphics graphics, LeaderboardLayout layout, int y) {
        int x = layout.contentX() + ui(4);
        int w = layout.contentW() - ui(12);
        int h = Math.max(1, ui(2));
        graphics.fill(x, y, x + w, y + h, 0x668D6E63);
    }

    private int leaderboardHeaderRuleY(LeaderboardLayout layout) {
        return layout.listY() - ui(8);
    }

    private int leaderboardSelfRuleY(LeaderboardLayout layout) {
        return layout.selfY() - ui(8);
    }

    private void drawLeaderboardTooltips(GuiGraphics graphics, LeaderboardLayout layout, int mouseX, int mouseY) {
        LeaderboardMetric hoveredMetric = hoveredLeaderboardMetric(layout, mouseX, mouseY);
        if (hoveredMetric != null) {
            List<Component> lines = List.of(
                    Component.translatable(hoveredMetric.titleKey()).withStyle(ChatFormatting.BOLD),
                    Component.translatable(hoveredMetric.descriptionKey()).withStyle(ChatFormatting.GRAY));
            graphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        for (LeaderboardPeriodButtonBounds bounds : leaderboardPeriodButtonBounds(layout)) {
            if (inside(mouseX, mouseY, bounds.x(), bounds.y(), bounds.w(), bounds.h())) {
                List<Component> lines = List.of(
                        Component.translatable(bounds.period().titleKey()).withStyle(ChatFormatting.BOLD),
                        Component.translatable(bounds.period().descriptionKey()).withStyle(ChatFormatting.GRAY));
                graphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        if (inside(mouseX, mouseY, layout.refreshX(), layout.refreshY(), layout.refreshW(), layout.refreshH())) {
            graphics.renderTooltip(this.font, Component.translatable("stardewcraft.leaderboard.refresh.tooltip"), mouseX, mouseY);
            return;
        }
        LeaderboardPageControls controls = leaderboardPageControls(layout);
        if (inside(mouseX, mouseY, controls.prevX(), controls.y(), controls.buttonW(), controls.h())) {
            graphics.renderTooltip(this.font, Component.translatable("stardewcraft.leaderboard.previous_page"), mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, controls.nextX(), controls.y(), controls.buttonW(), controls.h())) {
            graphics.renderTooltip(this.font, Component.translatable("stardewcraft.leaderboard.next_page"), mouseX, mouseY);
        }
    }

    private LeaderboardMetric hoveredLeaderboardMetric(LeaderboardLayout layout, int mouseX, int mouseY) {
        for (LeaderboardMetricButtonBounds bounds : leaderboardMetricButtonBounds(layout)) {
            if (inside(mouseX, mouseY, bounds.x(), bounds.y(), bounds.w() + ui(8), bounds.h())) {
                return bounds.metric();
            }
        }
        return null;
    }

    private void drawLeaderboardCenteredMessage(GuiGraphics graphics, LeaderboardLayout layout, Component message) {
        int x = layout.contentX() + layout.contentW() / 2 - this.font.width(message) / 2;
        int y = layout.listY() + (layout.listBottom() - layout.listY()) / 2 - this.font.lineHeight / 2;
        graphics.drawString(this.font, message, x, y, 0x8D6E63, false);
    }

    private boolean handleLeaderboardClick(int mouseX, int mouseY) {
        LeaderboardLayout layout = leaderboardLayout();
        if (inside(mouseX, mouseY, layout.refreshX(), layout.refreshY(), layout.refreshW(), layout.refreshH())) {
            requestLeaderboard();
            playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
            return true;
        }

        LeaderboardPageControls controls = leaderboardPageControls(layout);
        if (inside(mouseX, mouseY, controls.prevX(), controls.y(), controls.buttonW(), controls.h())) {
            if (leaderboardPage > 0) {
                leaderboardPage--;
                leaderboardScroll = 0;
                requestLeaderboard();
                playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
            }
            return true;
        }
        if (inside(mouseX, mouseY, controls.nextX(), controls.y(), controls.buttonW(), controls.h())) {
            if (leaderboardPage + 1 < leaderboardPageCount()) {
                leaderboardPage++;
                leaderboardScroll = 0;
                requestLeaderboard();
                playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
            }
            return true;
        }

        for (LeaderboardPeriodButtonBounds bounds : leaderboardPeriodButtonBounds(layout)) {
            LeaderboardPeriod period = bounds.period();
            if (inside(mouseX, mouseY, bounds.x(), bounds.y(), bounds.w(), bounds.h())) {
                if (leaderboardMetric.supportsPeriod(period) && leaderboardPeriod != period) {
                    leaderboardPeriod = period;
                    leaderboardPage = 0;
                    leaderboardScroll = 0;
                    requestLeaderboard();
                    playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                }
                return true;
            }
        }

        int maxTabScroll = leaderboardMetricMaxTabScroll(layout);
        if (maxTabScroll > 0) {
            if (leaderboardMetricScrollUpContains(layout, mouseX, mouseY)) {
                if (leaderboardMetricTabScroll > 0) {
                    leaderboardMetricTabScroll--;
                    playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                }
                return true;
            }
            if (leaderboardMetricScrollDownContains(layout, mouseX, mouseY)) {
                if (leaderboardMetricTabScroll < maxTabScroll) {
                    leaderboardMetricTabScroll++;
                    playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                }
                return true;
            }
        }

        for (LeaderboardMetricButtonBounds bounds : leaderboardMetricButtonBounds(layout)) {
            LeaderboardMetric metric = bounds.metric();
            if (inside(mouseX, mouseY, bounds.x(), bounds.y(), bounds.w() + ui(8), bounds.h())) {
                if (leaderboardMetric != metric) {
                    leaderboardMetric = metric;
                    if (!leaderboardMetric.supportsPeriod(leaderboardPeriod)) {
                        leaderboardPeriod = LeaderboardPeriod.TOTAL;
                    }
                    leaderboardPage = 0;
                    leaderboardScroll = 0;
                    requestLeaderboard();
                    playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                }
                return true;
            }
        }
        return false;
    }

    private void requestLeaderboard() {
        if (!leaderboardMetric.supportsPeriod(leaderboardPeriod)) {
            leaderboardPeriod = LeaderboardPeriod.TOTAL;
        }
        LeaderboardClientCache.request(leaderboardMetric.id(), leaderboardPeriod.id(), leaderboardPage);
        PacketDistributor.sendToServer(new RequestLeaderboardPayload(leaderboardMetric.id(), leaderboardPeriod.id(), leaderboardPage));
    }

    private LeaderboardPageControls leaderboardPageControls(LeaderboardLayout layout) {
        int buttonW = ui(48);
        int h = layout.refreshH();
        int gap = ui(8);
        Component label = Component.translatable("stardewcraft.leaderboard.page", leaderboardPage + 1, leaderboardPageCount());
        int labelW = Math.max(ui(78), this.font.width(label) + ui(16));
        int nextX = layout.refreshX() - ui(14) - buttonW;
        int labelX = nextX - gap - labelW;
        int prevX = labelX - gap - buttonW;
        return new LeaderboardPageControls(prevX, labelX, nextX, layout.refreshY(), buttonW, labelW, h);
    }

    private int leaderboardPageCount() {
        int totalPlayers = LeaderboardClientCache.getTotalPlayers();
        int fromTotal = totalPlayers <= 0 ? 1 : (totalPlayers + LEADERBOARD_PAGE_SIZE - 1) / LEADERBOARD_PAGE_SIZE;
        return Math.max(leaderboardPage + 1, fromTotal);
    }

    private List<LeaderboardMetricButtonBounds> leaderboardMetricButtonBounds(LeaderboardLayout layout) {
        List<LeaderboardMetricButtonBounds> bounds = new ArrayList<>();
        clampLeaderboardMetricTabScroll(layout);
        int w = leaderboardMetricButtonWidth();
        int h = leaderboardMetricButtonHeight();
        int x = menuX - w - ui(4);
        int y = leaderboardMetricStripTop(layout);
        int gap = ui(LEADERBOARD_TAB_GAP_SDV);
        int visibleCount = leaderboardMetricVisibleCount(layout);
        int end = Math.min(LEADERBOARD_METRICS.length, leaderboardMetricTabScroll + visibleCount);
        for (int i = leaderboardMetricTabScroll; i < end; i++) {
            LeaderboardMetric metric = LEADERBOARD_METRICS[i];
            bounds.add(new LeaderboardMetricButtonBounds(metric, x, y, w, h));
            y += h + gap;
        }
        return bounds;
    }

    private int leaderboardMetricVisibleCount(LeaderboardLayout layout) {
        int h = leaderboardMetricButtonHeight();
        int gap = ui(LEADERBOARD_TAB_GAP_SDV);
        return Math.max(1, (leaderboardMetricStripBottom(layout) - leaderboardMetricStripTop(layout) + gap) / Math.max(1, h + gap));
    }

    private int leaderboardMetricMaxTabScroll(LeaderboardLayout layout) {
        return Math.max(0, LEADERBOARD_METRICS.length - leaderboardMetricVisibleCount(layout));
    }

    private void clampLeaderboardMetricTabScroll(LeaderboardLayout layout) {
        leaderboardMetricTabScroll = Mth.clamp(leaderboardMetricTabScroll, 0, leaderboardMetricMaxTabScroll(layout));
    }

    private boolean insideLeaderboardMetricTabStrip(LeaderboardLayout layout, double mouseX, double mouseY) {
        int w = leaderboardMetricButtonWidth();
        int x = menuX - w - ui(4);
        int bottom = menuY + menuHeight - ui(48);
        return mouseX >= x - ui(6) && mouseX <= menuX + ui(12) && mouseY >= layout.metricY() && mouseY <= bottom;
    }

    private boolean leaderboardMetricNeedsScrollHints(LeaderboardLayout layout) {
        int h = leaderboardMetricButtonHeight();
        int gap = ui(LEADERBOARD_TAB_GAP_SDV);
        int bottom = menuY + menuHeight - ui(48);
        int visibleWithoutHints = Math.max(1, (bottom - layout.metricY() + gap) / Math.max(1, h + gap));
        return LEADERBOARD_METRICS.length > visibleWithoutHints;
    }

    private int leaderboardMetricStripTop(LeaderboardLayout layout) {
        return layout.metricY() + (leaderboardMetricNeedsScrollHints(layout) ? ui(LEADERBOARD_METRIC_SCROLL_HINT_SDV) : 0);
    }

    private int leaderboardMetricStripBottom(LeaderboardLayout layout) {
        int bottom = menuY + menuHeight - ui(48);
        return bottom - (leaderboardMetricNeedsScrollHints(layout) ? ui(LEADERBOARD_METRIC_SCROLL_HINT_SDV) : 0);
    }

    private boolean leaderboardMetricScrollUpContains(LeaderboardLayout layout, int mouseX, int mouseY) {
        if (!leaderboardMetricNeedsScrollHints(layout)) {
            return false;
        }
        int w = leaderboardMetricButtonWidth();
        int x = menuX - w - ui(4);
        return inside(mouseX, mouseY, x, layout.metricY(), w + ui(8), ui(LEADERBOARD_METRIC_SCROLL_HINT_SDV));
    }

    private boolean leaderboardMetricScrollDownContains(LeaderboardLayout layout, int mouseX, int mouseY) {
        if (!leaderboardMetricNeedsScrollHints(layout)) {
            return false;
        }
        int w = leaderboardMetricButtonWidth();
        int x = menuX - w - ui(4);
        return inside(mouseX, mouseY, x, leaderboardMetricStripBottom(layout), w + ui(8), ui(LEADERBOARD_METRIC_SCROLL_HINT_SDV));
    }

    private List<LeaderboardPeriodButtonBounds> leaderboardPeriodButtonBounds(LeaderboardLayout layout) {
        List<LeaderboardPeriodButtonBounds> bounds = new ArrayList<>();
        int h = layout.refreshH();
        int w = h;
        int gap = ui(6);
        int x = layout.contentX();
        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            bounds.add(new LeaderboardPeriodButtonBounds(period, x, layout.refreshY(), w, h));
            x += w + gap;
        }
        return bounds;
    }

    private int leaderboardMetricButtonHeight() {
        return ui(LEADERBOARD_TAB_SIZE_SDV);
    }

    private int leaderboardMetricButtonWidth() {
        return ui(LEADERBOARD_TAB_SIZE_SDV);
    }

    private void drawLeaderboardMetricIcon(GuiGraphics graphics, LeaderboardMetric metric, int x, int y, int w, int h) {
        int skillIconRow = leaderboardSkillIconRow(metric);
        if (skillIconRow >= 0) {
            float iconScale = 1.15f * mapping.s4();
            int scaledW = Math.round(10.0f * iconScale);
            int scaledH = Math.round(10.0f * iconScale);
            int iconX = x + (w - scaledW) / 2;
            int iconY = y + (h - scaledH) / 2;
            CommonGuiTextures.drawSkillIconTint(graphics, iconX, iconY, skillIconRow, iconScale, 1.0f, 1.0f, 1.0f, 1.0f);
            return;
        }

        if (metric == LeaderboardMetric.MONEY) {
            float iconScale = 0.9f * mapping.s4();
            int scaledW = Math.round(14.0f * iconScale);
            int scaledH = Math.round(13.0f * iconScale);
            int iconX = x + (w - scaledW) / 2;
            int iconY = y + (h - scaledH) / 2;
            CommonGuiTextures.drawGoldCoin16(graphics, iconX, iconY, iconScale);
            return;
        }

        if (metric == LeaderboardMetric.GIFTS_GIVEN) {
            float iconScale = 0.9f * mapping.s4();
            int scaledW = Math.round(14.0f * iconScale);
            int scaledH = Math.round(12.0f * iconScale);
            int iconX = x + (w - scaledW) / 2;
            int iconY = y + (h - scaledH) / 2;
            CommonGuiTextures.drawSocialGiftIcon(graphics, iconX, iconY, iconScale, 1.0f);
            return;
        }

        ItemStack icon = leaderboardMetricItemIcon(metric);
        if (icon.isEmpty()) {
            return;
        }
        float iconScale = 0.75f * mapping.s4();
        int scaledSize = CommonGuiTextures.itemSize(iconScale);
        int iconX = x + (w - scaledSize) / 2;
        int iconY = y + (h - scaledSize) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);
        CommonGuiTextures.drawItem(graphics, icon, iconX, iconY, iconScale);
        graphics.pose().popPose();
    }

    private int leaderboardSkillIconRow(LeaderboardMetric metric) {
        return switch (metric) {
            case SKILL_FARMING -> 0;
            case SKILL_MINING -> 1;
            case SKILL_FORAGING -> 2;
            case SKILL_FISHING -> 3;
            case SKILL_COMBAT -> 4;
            default -> -1;
        };
    }

    private ItemStack leaderboardMetricItemIcon(LeaderboardMetric metric) {
        Item item = switch (metric) {
            case MONEY -> ModItems.GOLD_BAR.get();
            case MINE_DEPTH -> ModItems.MINE_LADDER.get();
            case MINE_BLOCKS_BROKEN -> ModItems.GOLD_PICKAXE.get();
            case MINE_STONES_BROKEN -> Items.STONE;
            case MINE_ORES_BROKEN -> Items.IRON_ORE;
            case MINE_GEM_ORES_BROKEN -> Items.DIAMOND_ORE;
            case MINE_MINERAL_NODES_BROKEN -> ModItems.THUNDER_EGG.get();
            case MINE_BLOCKS_BOMBED -> ModItems.MEGA_BOMB.get();
            case FISH_CAUGHT -> ModItems.FISHING_ROD.get();
            case ITEMS_SHIPPED -> ModItems.SHIPPING_BIN.get();
            case SHIPPING_VALUE -> ModItems.TREASURE_CHEST.get();
            case SHIPPING_VARIETY -> ModItems.PARSNIP.get();
            case MONSTERS_SLAIN -> ModItems.RUSTY_SWORD.get();
            case SKILL_FARMING -> ModItems.GOLD_HOE.get();
            case SKILL_FISHING -> ModItems.FISHING_ROD.get();
            case SKILL_FORAGING -> ModItems.GOLD_AXE.get();
            case SKILL_MINING -> ModItems.GOLD_PICKAXE.get();
            case SKILL_COMBAT -> ModItems.RUSTY_SWORD.get();
            case GIFTS_GIVEN -> ModItems.SUNFLOWER.get();
            case COOKING_COUNT -> ModItems.COOKING_POT.get();
            case ANIMALS_OWNED -> ModItems.MILK_PAIL.get();
            case ANIMAL_PRODUCTS_COLLECTED -> ModItems.EGG_WHITE.get();
            case PASS_OUTS -> ModItems.BED_1.get();
            case COMBAT_DEATHS -> ModItems.RUSTY_SWORD.get();
            case BASEMENT_SHORTS_STOLEN -> ModItems.LUCKY_PURPLE_SHORTS.get();
            case TRASH_CANS_CHECKED -> ModItems.TRASH_BIN.get();
        };
        return new ItemStack(item);
    }

    private ItemStack leaderboardPeriodIcon(LeaderboardPeriod period) {
        Item item = switch (period) {
            case TOTAL -> ModItems.GRANDFATHER_CLOCK.get();
            case SEASON -> ModItems.SUNFLOWER.get();
            case WEEK -> ModItems.MIXED_SEEDS.get();
            case DAY -> ModItems.PARSNIP.get();
        };
        return new ItemStack(item);
    }

    private int leaderboardRankX(LeaderboardLayout layout) {
        return layout.contentX() + ui(22);
    }

    private int leaderboardNameX(LeaderboardLayout layout) {
        return layout.contentX() + ui(96);
    }

    private int leaderboardValueRightX(LeaderboardLayout layout) {
        return layout.contentX() + layout.contentW() - ui(36);
    }

    private void clampLeaderboardScroll() {
        if (!LeaderboardClientCache.hasData(leaderboardMetric.id(), leaderboardPeriod.id(), leaderboardPage)) {
            leaderboardScroll = 0;
            return;
        }
        int maxScroll = Math.max(0, LeaderboardClientCache.getRows().size() - Math.max(1, leaderboardVisibleRows));
        leaderboardScroll = Mth.clamp(leaderboardScroll, 0, maxScroll);
    }

    private String ellipsize(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int target = Math.max(0, maxWidth - this.font.width(ellipsis));
        String result = text;
        while (!result.isEmpty() && this.font.width(result) > target) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ============ Tab 3: Farm Management Page (农场管理) ============

    private static final int FARM_MGMT_ROW_HEIGHT_SDV = 84;
    private static final int FARM_MGMT_VISIBLE_ROWS = 5;
    private static final String[] PERM_KEYS = {
        "gui.stardewcraft.farm_mgmt.perm_0",
        "gui.stardewcraft.farm_mgmt.perm_1",
        "gui.stardewcraft.farm_mgmt.perm_2"
    };

    private void drawFarmManagementPage(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!com.stardew.craft.client.gui.FarmPermissionClientCache.hasData()) {
            Component loading = Component.translatable("gui.stardewcraft.farm_mgmt.loading");
            int lx = menuX + menuWidth / 2 - this.font.width(loading) / 2;
            graphics.drawString(this.font, loading, lx, menuY + menuHeight / 2, 0x582A11, false);
            return;
        }

        int spaceSide = ui(64);
        int spaceTop = ui(64);
        int contentX = menuX + spaceSide;
        int contentW = menuWidth - spaceSide * 2;
        int y = menuY + spaceTop;

        int rowHGui = ui(FARM_MGMT_ROW_HEIGHT_SDV);

        // 标题
        Component title = Component.translatable("gui.stardewcraft.farm_mgmt.title")
                .withStyle(ChatFormatting.BOLD);
        int titleX = menuX + menuWidth / 2 - this.font.width(title) / 2;
        graphics.drawString(this.font, title, titleX, y, 0x582A11, false);
        y += this.font.lineHeight + ui(12);

        // 默认权限标签
        int defaultPerm = com.stardew.craft.client.gui.FarmPermissionClientCache.getDefaultPerm();
        Component defaultLabel = Component.translatable("gui.stardewcraft.farm_mgmt.default_perm");
        graphics.drawString(this.font, defaultLabel, contentX, y, 0x582A11, false);
        y += this.font.lineHeight + ui(4);

        // 默认权限描述
        Component defaultDesc = Component.translatable("gui.stardewcraft.farm_mgmt.default_perm.desc");
        graphics.drawString(this.font, defaultDesc, contentX, y, 0x8D6E63, false);
        y += this.font.lineHeight + ui(8);

        // 默认权限按钮组 — 等宽按钮，居中排列
        int btnH = this.font.lineHeight + ui(8);
        int btnGap = ui(8);
        // 计算按钮统一宽度（取最宽 + padding）
        int maxLblW = 0;
        for (int lvl = 0; lvl <= 2; lvl++) {
            maxLblW = Math.max(maxLblW, this.font.width(Component.translatable(PERM_KEYS[lvl])));
        }
        int defBtnW = maxLblW + ui(20);
        int totalBtnsW = defBtnW * 3 + btnGap * 2;
        int defBtnStartX = contentX + (contentW - totalBtnsW) / 2;
        for (int lvl = 0; lvl <= 2; lvl++) {
            Component lbl = Component.translatable(PERM_KEYS[lvl]);
            int btnX = defBtnStartX + lvl * (defBtnW + btnGap);
            boolean active = (lvl == defaultPerm);
            int btnColor = active ? 0xFF4CAF50 : 0xFF757575;
            graphics.fill(btnX + 1, y, btnX + defBtnW - 1, y + btnH, btnColor);
            graphics.fill(btnX, y + 1, btnX + defBtnW, y + btnH - 1, btnColor);
            int textX = btnX + (defBtnW - this.font.width(lbl)) / 2;
            int textY = y + (btnH - this.font.lineHeight) / 2;
            graphics.drawString(this.font, lbl, textX, textY, 0xFFFFFFFF, false);
        }
        farmMgmtDefaultPermY = y;
        farmMgmtDefBtnW = defBtnW;
        farmMgmtDefBtnStartX = defBtnStartX;
        y += btnH + ui(16);

        // 分隔线
        StardewGuiUtil.drawHorizontalPartitionSmall(graphics, contentX, y, contentW, mapping.s4());
        y += ui(36);

        // 在线玩家列表标题
        Component playerTitle = Component.translatable("gui.stardewcraft.farm_mgmt.online_players")
                .withStyle(ChatFormatting.BOLD);
        graphics.drawString(this.font, playerTitle, contentX, y, 0x582A11, false);
        y += this.font.lineHeight + ui(8);

        var players = com.stardew.craft.client.gui.FarmPermissionClientCache.getPlayers();
        if (players.isEmpty()) {
            Component noPlayers = Component.translatable("gui.stardewcraft.farm_mgmt.no_players");
            graphics.drawString(this.font, noPlayers, contentX + ui(16), y, 0x8D6E63, false);
            return;
        }

        farmMgmtPlayerListY = y;
        // 动态计算可见行数：剩余空间 / 行高，至少 1 行
        int availableH = (menuY + menuHeight - ui(32)) - y;
        farmMgmtVisibleRows = Math.max(1, availableH / rowHGui);
        // clamp scroll 防止 resize 后越界
        int maxScrollClamp = Math.max(0, players.size() - farmMgmtVisibleRows);
        farmMgmtScroll = Math.min(farmMgmtScroll, maxScrollClamp);
        int maxVisible = Math.min(farmMgmtVisibleRows, players.size() - farmMgmtScroll);

        // 玩家行按钮统一宽度
        int maxPermW = 0;
        for (int lvl = 0; lvl <= 2; lvl++) {
            maxPermW = Math.max(maxPermW, this.font.width(Component.translatable(PERM_KEYS[lvl])));
        }
        int playerBtnW = maxPermW + ui(16);
        int playerBtnGap = ui(6);

        // scissor 裁剪列表区域（clamp 到面板底部）
        int listBottom = Math.min(y + farmMgmtVisibleRows * rowHGui, menuY + menuHeight - ui(16));
        graphics.enableScissor(contentX, y, contentX + contentW, listBottom);

        for (int i = 0; i < maxVisible; i++) {
            int idx = farmMgmtScroll + i;
            var entry = players.get(idx);
            int rowY = y + i * rowHGui;

            // 选中行高亮
            if (idx == farmMgmtSelectedPlayer) {
                graphics.fill(contentX, rowY, contentX + contentW, rowY + rowHGui - 2, 0x33EADB8C);
            }

            // 第一行：玩家名 + "(使用默认)" 标注
            int currentPerm = entry.permission();
            boolean isDefault = (currentPerm == -1);
            int nameY = rowY + ui(6);
            graphics.drawString(this.font, Component.literal(entry.name()),
                    contentX + ui(8), nameY, 0x582A11, false);
            if (isDefault) {
                Component usingDefault = Component.translatable("gui.stardewcraft.farm_mgmt.using_default");
                int udX = contentX + ui(8) + this.font.width(entry.name()) + ui(12);
                graphics.drawString(this.font, usingDefault, udX, nameY, 0x8D6E63, false);
            }

            // 第二行：3 个权限按钮（左对齐，在名字下方）
            int effectivePerm = isDefault ? defaultPerm : currentPerm;
            int btnRowH = this.font.lineHeight + ui(6);
            int btnRowY = nameY + this.font.lineHeight + ui(6);
            for (int lvl = 0; lvl <= 2; lvl++) {
                Component lbl = Component.translatable(PERM_KEYS[lvl]);
                int bX = contentX + ui(8) + lvl * (playerBtnW + playerBtnGap);
                int bColor;
                if (lvl == effectivePerm) {
                    bColor = isDefault ? 0xFF5588AA : 0xFF4CAF50;
                } else {
                    bColor = 0xFF616161;
                }
                graphics.fill(bX + 1, btnRowY, bX + playerBtnW - 1, btnRowY + btnRowH, bColor);
                graphics.fill(bX, btnRowY + 1, bX + playerBtnW, btnRowY + btnRowH - 1, bColor);
                int tX = bX + (playerBtnW - this.font.width(lbl)) / 2;
                int tY = btnRowY + (btnRowH - this.font.lineHeight) / 2;
                graphics.drawString(this.font, lbl, tX, tY, 0xFFFFFFFF, false);
            }
        }

        graphics.disableScissor();
        // 滚动条
        if (players.size() > farmMgmtVisibleRows) {
            int barX = contentX + contentW - ui(6);
            int barTotalH = farmMgmtVisibleRows * rowHGui;
            int thumbH = Math.max(ui(16), barTotalH * farmMgmtVisibleRows / players.size());
            int maxScroll = Math.max(1, players.size() - farmMgmtVisibleRows);
            int thumbY = y + (barTotalH - thumbH) * farmMgmtScroll / maxScroll;
            graphics.fill(barX, y, barX + ui(3), y + barTotalH, 0x22000000);
            graphics.fill(barX, thumbY, barX + ui(3), thumbY + thumbH, 0x66582A11);
        }
    }

    /** 用于 mouseClicked 时定位默认权限按钮的 Y 坐标 */
    private int farmMgmtDefaultPermY = 0;
    /** 默认权限按钮统一宽度和起始 X */
    private int farmMgmtDefBtnW = 0;
    private int farmMgmtDefBtnStartX = 0;
    /** 在线玩家列表起始 Y 坐标 */
    private int farmMgmtPlayerListY = 0;

    private boolean handleFarmMgmtClick(int mouseX, int mouseY) {
        int spaceSide = ui(64);
        int contentX = menuX + spaceSide;
        int contentW = menuWidth - spaceSide * 2;
        int btnH = this.font.lineHeight + ui(8);
        int btnGap = ui(8);
        int rowHGui = ui(FARM_MGMT_ROW_HEIGHT_SDV);
        int defaultPerm = com.stardew.craft.client.gui.FarmPermissionClientCache.getDefaultPerm();
        var players = com.stardew.craft.client.gui.FarmPermissionClientCache.getPlayers();

        // 默认权限按钮点击（等宽按钮，居中排列）
        if (mouseY >= farmMgmtDefaultPermY && mouseY < farmMgmtDefaultPermY + btnH) {
            for (int lvl = 0; lvl <= 2; lvl++) {
                int btnX = farmMgmtDefBtnStartX + lvl * (farmMgmtDefBtnW + btnGap);
                if (mouseX >= btnX && mouseX < btnX + farmMgmtDefBtnW) {
                    if (lvl != defaultPerm) {
                        PacketDistributor.sendToServer(
                            new com.stardew.craft.network.payload.FarmPermissionUpdatePayload(
                                2, new java.util.UUID(0, 0), lvl));
                        com.stardew.craft.client.gui.FarmPermissionClientCache.update(
                            lvl, players);
                        playUiSound(ModSounds.SMALL_SELECT.get(), 1.0f, 1.0f);
                    }
                    return true;
                }
            }
        }

        // 在线玩家权限按钮点击（2-line layout）
        if (!players.isEmpty() && mouseY >= farmMgmtPlayerListY) {
            int maxVisible = Math.min(farmMgmtVisibleRows, players.size() - farmMgmtScroll);
            // 玩家行按钮统一宽度
            int maxPermW = 0;
            for (int lvl = 0; lvl <= 2; lvl++) {
                maxPermW = Math.max(maxPermW, this.font.width(Component.translatable(PERM_KEYS[lvl])));
            }
            int playerBtnW = maxPermW + ui(16);
            int playerBtnGap = ui(6);
            int btnRowH = this.font.lineHeight + ui(6);

            for (int i = 0; i < maxVisible; i++) {
                int idx = farmMgmtScroll + i;
                var entry = players.get(idx);
                int rowY = farmMgmtPlayerListY + i * rowHGui;

                if (mouseY < rowY || mouseY >= rowY + rowHGui) continue;

                // 第二行按钮的 Y 坐标
                int nameY = rowY + ui(6);
                int btnRowY = nameY + this.font.lineHeight + ui(6);
                if (mouseY >= btnRowY && mouseY < btnRowY + btnRowH) {
                    for (int lvl = 0; lvl <= 2; lvl++) {
                        int bX = contentX + ui(8) + lvl * (playerBtnW + playerBtnGap);
                        if (mouseX >= bX && mouseX < bX + playerBtnW) {
                            int currentPerm = entry.permission();
                            int effectivePerm = (currentPerm == -1) ? defaultPerm : currentPerm;
                            if (lvl != effectivePerm || currentPerm == -1) {
                                PacketDistributor.sendToServer(
                                    new com.stardew.craft.network.payload.FarmPermissionUpdatePayload(
                                        0, entry.uuid(), lvl));
                                var newPlayers = new java.util.ArrayList<>(players);
                                newPlayers.set(idx, new com.stardew.craft.network.payload.FarmPermSyncPayload.PlayerPermEntry(
                                    entry.uuid(), entry.name(), lvl));
                                com.stardew.craft.client.gui.FarmPermissionClientCache.update(
                                    com.stardew.craft.client.gui.FarmPermissionClientCache.getDefaultPerm(), newPlayers);
                                playUiSound(ModSounds.SMALL_SELECT.get(), 1.0f, 1.0f);
                            }
                            return true;
                        }
                    }
                }

                // 点击行选中
                farmMgmtSelectedPlayer = idx;
                return true;
            }
        }
        return false;
    }

    // ============ Tab 1: Skills Page (SDV SkillsPage 1:1 parity) ============

    // SDV skill row order: Farming(0), Mining(3), Foraging(2), Fishing(1), Combat(4)
    private static final SkillType[] SKILLS_PAGE_ROW_ORDER = {
        SkillType.FARMING, SkillType.MINING, SkillType.FORAGING, SkillType.FISHING, SkillType.COMBAT
    };

    // SDV skill name i18n keys (matching SDV row order)
    private static final String[] SKILL_NAME_KEYS = {
        "stardewcraft.skills_page.farming",
        "stardewcraft.skills_page.mining",
        "stardewcraft.skills_page.foraging",
        "stardewcraft.skills_page.fishing",
        "stardewcraft.skills_page.combat",
    };

    // SDV skill hover description i18n keys
    private static final String[][] SKILL_HOVER_KEYS = {
        {"stardewcraft.skills_page.farming_hover1", "stardewcraft.skills_page.farming_hover2"}, // Farming: hoe + watercan
        {"stardewcraft.skills_page.mining_hover"},   // Mining: pickaxe
        {"stardewcraft.skills_page.foraging_hover"},  // Foraging: axe
        {"stardewcraft.skills_page.fishing_hover"},   // Fishing: rod
        {"stardewcraft.skills_page.combat_hover"},    // Combat: health
    };

    // Profession IDs at level 5 and 10 per skill row (SDV order: farming, mining, foraging, fishing, combat)
    private static final ProfessionType[][] SKILL_ROW_LV5_PROFS = {
        {ProfessionType.RANCHER, ProfessionType.TILLER},
        {ProfessionType.MINER, ProfessionType.GEOLOGIST},
        {ProfessionType.FORESTER, ProfessionType.GATHERER},
        {ProfessionType.FISHER, ProfessionType.TRAPPER},
        {ProfessionType.FIGHTER, ProfessionType.SCOUT},
    };

    private static final int SKILLS_VERTICAL_SPACING = 68; // SDV px between rows

    // SDV Farmer title level thresholds (based on sum of all 5 skill levels / 2... actually just sum)
    // SDV: Farmer.Level = (farming + mining + foraging + fishing + combat) / 2
    private static final String[] FARMER_TITLE_KEYS = {
        "stardewcraft.farmer_title.farm_king",      // 30+
        "stardewcraft.farmer_title.cropmaster",      // 29
        "stardewcraft.farmer_title.agriculturist",   // 27-28
        "stardewcraft.farmer_title.farmer",          // 25-26
        "stardewcraft.farmer_title.rancher",         // 23-24
        "stardewcraft.farmer_title.planter",         // 21-22
        "stardewcraft.farmer_title.granger",         // 19-20
        "stardewcraft.farmer_title.farmboy",         // 17-18 (gendered)
        "stardewcraft.farmer_title.sodbuster",       // 15-16
        "stardewcraft.farmer_title.smallholder",     // 13-14
        "stardewcraft.farmer_title.tiller",          // 11-12
        "stardewcraft.farmer_title.farmhand",        // 9-10
        "stardewcraft.farmer_title.cowpoke",         // 7-8
        "stardewcraft.farmer_title.bumpkin",         // 5-6
        "stardewcraft.farmer_title.greenhorn",       // 3-4
        "stardewcraft.farmer_title.newcomer",        // 0-2
    };
    private static final int[] FARMER_TITLE_MIN_LEVELS = {
        30, 29, 27, 25, 23, 21, 19, 17, 15, 13, 11, 9, 7, 5, 3, 0
    };

    private record PowerEntry(int iconIndex, String mailFlag, String specialItemId, String tooltipItemId) {
    }

    private static final PowerEntry[] POWER_ENTRIES = new PowerEntry[] {
        new PowerEntry(0, CCStoryFlags.CAN_READ_JUNIMO, "stardewcraft:forest_magic", "stardewcraft:forest_magic"),
        new PowerEntry(1, "HasDwarvishTranslationGuide", "stardewcraft:dwarvish_translation_guide", "stardewcraft:dwarvish_translation_guide"),
        new PowerEntry(2, "HasRustyKey", "stardewcraft:rusty_key", "stardewcraft:rusty_key"),
        new PowerEntry(3, "HasClubCard", "stardewcraft:club_card", "stardewcraft:club_card"),
        new PowerEntry(4, "HasSpecialCharm", "stardewcraft:special_charm", "stardewcraft:special_charm"),
        new PowerEntry(5, CCStoryFlags.HAS_SKULL_KEY, CCStoryFlags.SKULL_KEY_SPECIAL_ITEM, "stardewcraft:skull_key"),
        new PowerEntry(6, "HasMagnifyingGlass", "stardewcraft:magnifying_glass", "stardewcraft:magnifying_glass"),
        new PowerEntry(7, "HasDarkTalisman", "stardewcraft:dark_talisman", "stardewcraft:dark_talisman"),
        new PowerEntry(8, "HasMagicInk", "stardewcraft:magic_ink", "stardewcraft:magic_ink"),
        new PowerEntry(9, SecretNoteStoryFlags.BEAR_KNOWLEDGE,
            SecretNote23Service.SPECIAL_ITEM_ID, SecretNote23Service.SPECIAL_ITEM_ID),
        new PowerEntry(10, "HasSpringOnionMastery", "stardewcraft:spring_onion_mastery", "stardewcraft:spring_onion_mastery"),
        new PowerEntry(11, "HasTownKey", "stardewcraft:key_to_the_town", "stardewcraft:key_to_the_town")
    };

    private String getFarmerTitle() {
        int totalLevel = 0;
        for (SkillType skill : SkillType.values()) {
            totalLevel += ClientPlayerDataCache.getSkillLevel(skill);
        }
        // SDV: Farmer.Level = totalLevel / 2
        int farmerLevel = totalLevel / 2;
        for (int i = 0; i < FARMER_TITLE_MIN_LEVELS.length; i++) {
            if (farmerLevel >= FARMER_TITLE_MIN_LEVELS[i]) {
                return PlayerGenderText.preprocess(Component.translatable(FARMER_TITLE_KEYS[i]).getString());
            }
        }
        return PlayerGenderText.preprocess(
                Component.translatable("stardewcraft.farmer_title.newcomer").getString());
    }

    // Hover state for skills page
    private String skillsHoverText = "";
    private String skillsHoverTitle = "";
    private int skillsHoveredProfessionId = -1;
    private int skillsHoveredBarX = 0;
    private int skillsHoveredBarY = 0;

    private int skillsLowerPartitionY() {
        return menuY + ui(96 + SKILLS_PAGE_HEIGHT_SDV / 2 + 21);
    }

    private boolean usesWideSkillsLayout() {
        if (this.minecraft == null) {
            return false;
        }
        String language = this.minecraft.getLanguageManager().getSelected();
        return "ru_ru".equalsIgnoreCase(language) || "it_it".equalsIgnoreCase(language);
    }

    private boolean usesRussianSkillsLayout() {
        return this.minecraft != null
                && "ru_ru".equalsIgnoreCase(this.minecraft.getLanguageManager().getSelected());
    }

    private int skillsPageWidth() {
        return menuWidth + (usesWideSkillsLayout() ? ui(64) : 0);
    }

    /**
     * SDV NumberSprite.draw equivalent.
     * Draws a number using digit sprites from cursors.png at (512,128), 8x8 per digit, 6 per row (48px wide).
     */
    private void drawNumberSprite(GuiGraphics graphics, int number, int posX, int posY, int color, float alpha) {
        float scale = mapping.s4();
        int digitCount = 0;
        int n = number;
        // Count digits
        do { digitCount++; n /= 10; } while (n > 0);

        // Draw from right to left (least significant digit first)
        int drawX = posX;
        n = number;
        // SDV draws right-to-left from the position, so we need to adjust
        // Actually SDV uses position as the rightmost digit's center, let's replicate exactly
        int tempNumber = number;
        do {
            int currentDigit = tempNumber % 10;
            tempNumber /= 10;

            // Draw the digit - SDV draws centered on (4,4) origin
            graphics.pose().pushPose();
            graphics.pose().translate(drawX, posY, 0);
            graphics.pose().scale(scale, scale, 1.0f);
            // Apply color tint
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            CommonGuiTextures.drawNumberDigitAtCurrentPoseTint(graphics, currentDigit, -4, -4, r, g, b, alpha);
            graphics.pose().popPose();

            // Move left for next digit: SDV spacing = 8*1*4 - 4 = 28 SDV px
            drawX -= ui(28);
        } while (tempNumber > 0);
    }

    private void drawSkillsPage(GuiGraphics graphics, int mouseX, int mouseY) {
        float s4 = mapping.s4();
        int borderWidth = ui(BORDER_WIDTH);
        int spaceSide = ui(32);   // IClickableMenu.spaceToClearSideBorder
        int spaceTop = ui(96);    // IClickableMenu.spaceToClearTopBorder

        // --- Player Panel (left side) ---
        int playerPanelX = menuX + ui(64);
        int playerPanelY = menuY + borderWidth + spaceTop;
        // Draw day/night background (same as inventory page)
        int bgX = playerPanelX - ui(8);
        int bgY = playerPanelY - ui(20);
        Minecraft mc = this.minecraft;
        if (mc != null && mc.player != null) {
            long dayTime = mc.level != null ? mc.level.getDayTime() % 24000 : 0;
            boolean isNight = dayTime >= 13000;
            ResourceLocation bgTex = isNight ? NIGHTBG : DAYBG;
            int bgW = ui(131);
            int bgH = ui(190);
            graphics.blit(bgTex, bgX, bgY, 0, 0, bgW, bgH, bgW, bgH);

            // Draw MC player entity
            int margin = ui(8);
            net.minecraft.client.gui.screens.inventory.InventoryScreen
                .renderEntityInInventoryFollowsMouse(
                    graphics,
                    bgX + margin, bgY + margin,
                    bgX + bgW - margin, bgY + bgH - margin,
                    ui(30), 0.0625F,
                    (float) mouseX, (float) mouseY,
                    mc.player
                );

            // Player name (centered under the panel)
            String playerName = ClientPlayerDataCache.getPlayerDisplayName(mc.player.getName().getString());
            Component boldPlayerName = Component.literal(playerName).withStyle(ChatFormatting.BOLD);
            float nameScale = sdvTextScale();
            int nameRawW = this.font.width(boldPlayerName);
            float nameEffScale = nameScale;
            int namePanelW = ui(128);
            if (nameRawW * nameEffScale > namePanelW) {
                nameEffScale = (float) namePanelW / nameRawW;
            }
            int nameScaledW = Math.round(nameRawW * nameEffScale);
            int nameX = playerPanelX + ui(64) - nameScaledW / 2;
            int nameY = playerPanelY + ui(192 - 17);
            graphics.pose().pushPose();
            graphics.pose().translate(nameX, nameY, 0);
            graphics.pose().scale(nameEffScale, nameEffScale, 1.0f);
            graphics.drawString(this.font, boldPlayerName, 0, 0, 0xFF5B3A1A, false);
            graphics.pose().popPose();

            // Player title (centered below name)
            String playerTitle = getFarmerTitle();
            float titleScale = sdvTextScale() * 0.85f;
            Component boldTitle = Component.literal(playerTitle).withStyle(ChatFormatting.BOLD);
            int titleRawW = this.font.width(boldTitle);
            float titleEffScale = titleScale;
            if (titleRawW * titleEffScale > namePanelW) {
                titleEffScale = (float) namePanelW / titleRawW;
            }
            int titleScaledW = Math.round(titleRawW * titleEffScale);
            int titleX = playerPanelX + ui(64) - titleScaledW / 2;
            int titleY = playerPanelY + ui(256 - 32 - 19);
            graphics.pose().pushPose();
            graphics.pose().translate(titleX, titleY, 0);
            graphics.pose().scale(titleEffScale, titleEffScale, 1.0f);
            graphics.drawString(this.font, boldTitle, 0, 0, 0xFF5B3A1A, false);
            graphics.pose().popPose();
        }

        // --- Horizontal separator ---
        int sepY = skillsLowerPartitionY();
        int sepX = menuX + spaceSide * 2;
        int sepW = skillsPageWidth() - spaceSide * 4 - ui(8);
        graphics.fill(sepX, sepY, sepX + sepW, sepY + ui(4), 0xFFD68F54);

        // --- Skill bars ---
        int drawX = usesWideSkillsLayout()
                ? menuX + skillsPageWidth() - ui(448 + 48) + ui(4)
                : menuX + borderWidth + spaceTop + ui(256 - 8);
        int drawY = menuY + spaceTop + borderWidth - ui(8);
        int verticalSpacing = ui(SKILLS_VERTICAL_SPACING);
        int addedX = 0;

        // Reset hover state
        skillsHoverText = "";
        skillsHoverTitle = "";
        skillsHoveredProfessionId = -1;

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 5; j++) {
                SkillType skill = SKILLS_PAGE_ROW_ORDER[j];
                int skillLevel = ClientPlayerDataCache.getSkillLevel(skill);
                boolean drawRed = skillLevel > i;

                // Draw skill name + icon on the first column only
                if (i == 0) {
                    String skillName = Component.translatable(SKILL_NAME_KEYS[j]).getString();
                    Component boldSkillName = Component.literal(skillName).withStyle(ChatFormatting.BOLD);
                    float skillNameScale = sdvTextScale() * (usesWideSkillsLayout() ? 1.0f : 0.9f);
                    int skillNameRawW = this.font.width(boldSkillName);
                    // Vanilla gives Russian and Italian an extra 64 px page column and draws
                    // skill names at normal small-font size. Use that space instead of crushing
                    // long localized names into the normal 100 px label area.
                    int skillNameMaxW = ui(usesWideSkillsLayout() ? 156 : 100);
                    float skillNameEffScale = skillNameScale;
                    if (skillNameRawW * skillNameEffScale > skillNameMaxW) {
                        skillNameEffScale = (float) skillNameMaxW / skillNameRawW;
                    }
                    int skillNameScaledW = Math.round(skillNameRawW * skillNameEffScale);
                    int nameX = drawX - skillNameScaledW + ui(4) - ui(64);
                    int nameY = drawY + ui(4) + j * verticalSpacing;
                    graphics.pose().pushPose();
                    graphics.pose().translate(nameX, nameY, 0);
                    graphics.pose().scale(skillNameEffScale, skillNameEffScale, 1.0f);
                    graphics.drawString(this.font, boldSkillName, 0, 0, 0xFF5B3A1A, false);
                    graphics.pose().popPose();

                    // Skill icon - shadow first, then normal
                    int iconShadowX = drawX - ui(56);
                    int iconShadowY = drawY + j * verticalSpacing;
                    CommonGuiTextures.drawSkillIconTint(graphics, iconShadowX, iconShadowY, j, s4,
                        0.0f, 0.0f, 0.0f, 0.3f);
                    int iconX = drawX - ui(52);
                    int iconY = drawY - ui(4) + j * verticalSpacing;
                    CommonGuiTextures.drawSkillIconTint(graphics, iconX, iconY, j, s4,
                        1.0f, 1.0f, 1.0f, 1.0f);

                    // Hover detection for skill name area
                    int areaX = drawX - ui(128) - ui(48);
                    int areaY = drawY + j * verticalSpacing;
                    int areaW = ui(148);
                    int areaH = ui(36);
                    if (mouseX >= areaX && mouseX < areaX + areaW && mouseY >= areaY && mouseY < areaY + areaH) {
                        if (skillLevel > 0) {
                            skillsHoverTitle = skillName;
                            String[] hoverKeys = SKILL_HOVER_KEYS[j];
                            StringBuilder sb = new StringBuilder();
                            for (int h = 0; h < hoverKeys.length; h++) {
                                if (h > 0) sb.append("\n");
                                sb.append(Component.translatable(hoverKeys[h], skillLevel).getString());
                            }
                            skillsHoverText = sb.toString();
                        }
                    }
                }

                // Draw bar
                if ((i + 1) % 5 != 0) {
                    // Small bar (non-5th): unlit=(129,338,8,9), lit=(137,338,8,9)
                    // Shadow
                    CommonGuiTextures.drawSkillBarTint(graphics,
                        addedX + drawX - ui(4) + i * ui(36),
                        drawY + j * verticalSpacing,
                        false, false, s4, 0.0f, 0.0f, 0.0f, 0.35f);
                    // Bar
                    CommonGuiTextures.drawSkillBarTint(graphics,
                        addedX + drawX + i * ui(36),
                        drawY - ui(4) + j * verticalSpacing,
                        false, drawRed, s4, 1.0f, 1.0f, 1.0f, drawRed ? 1.0f : 0.65f);
                } else {
                    // Big bar (every 5th): unlit=(145,338,14,9), lit=(159,338,14,9)
                    if (!drawRed) {
                        // Shadow for unlit big bar
                        CommonGuiTextures.drawSkillBarTint(graphics,
                            addedX + drawX - ui(4) + i * ui(36),
                            drawY + j * verticalSpacing,
                            true, false, s4, 0.0f, 0.0f, 0.0f, 0.35f);
                        // Unlit big bar
                        CommonGuiTextures.drawSkillBarTint(graphics,
                            addedX + drawX + i * ui(36),
                            drawY - ui(4) + j * verticalSpacing,
                            true, false, s4, 1.0f, 1.0f, 1.0f, 0.65f);
                    } else {
                        // Lit big bar (profession unlocked) - shadow first (SDV skillBars drawShadow:true)
                        CommonGuiTextures.drawSkillBarTint(graphics,
                            addedX + drawX - ui(4) + i * ui(36),
                            drawY + j * verticalSpacing,
                            true, false, s4, 0.0f, 0.0f, 0.0f, 0.35f);
                        CommonGuiTextures.drawSkillBarTint(graphics,
                            addedX + drawX + i * ui(36),
                            drawY - ui(4) + j * verticalSpacing,
                            true, true, s4, 1.0f, 1.0f, 1.0f, 1.0f);

                        // Profession hover detection for boxes at level 5 and 10
                        int boxX = addedX + drawX - ui(4) + i * ui(36);
                        int boxY = drawY + j * verticalSpacing;
                        int boxW = ui(56);
                        int boxH = ui(36);
                        if (mouseX >= boxX && mouseX < boxX + boxW && mouseY >= boxY && mouseY < boxY + boxH) {
                            // Find which profession the player chose at this level
                            int profLevel = i + 1; // 5 or 10
                            ProfessionType chosenProf = getChosenProfessionForRow(j, profLevel);
                            if (chosenProf != null) {
                                skillsHoverTitle = chosenProf.getDisplayName().getString();
                                skillsHoverText = Component.translatable(
                                    "stardewcraft.profession." + chosenProf.getName() + ".desc").getString();
                                skillsHoveredProfessionId = chosenProf.getId();
                                skillsHoveredBarX = boxX;
                                skillsHoveredBarY = boxY;
                            }
                        }
                    }
                }

                // Draw level number after the last bar (i==9)
                if (i == 9) {
                    int numX = addedX + drawX + (i + 2) * ui(36) + ui(12) + (skillLevel >= 10 ? ui(12) : 0);
                    int numY = drawY + ui(16) + j * verticalSpacing;
                    // Shadow (offset: 0, +4 relative)
                    drawNumberSprite(graphics, skillLevel, numX, numY, 0x000000, 0.35f);
                    // Number (offset: +4, 0 relative to shadow base)
                    int numColor = 0xF4A460; // SandyBrown
                    float numAlpha = (skillLevel == 0) ? 0.75f : 1.0f;
                    drawNumberSprite(graphics, skillLevel,
                        numX + ui(4), numY - ui(4), numColor, numAlpha);
                }
            }
            if ((i + 1) % 5 == 0) {
                addedX += ui(24);
            }
        }

        // --- Profession icon popup on hover ---
        if (skillsHoveredProfessionId >= 0) {
            // SDV: IClickableMenu.drawTextureBox at (c.bounds.X - 16 - 8, c.bounds.Y - 16 - 16, 96, 96)
            int popupX = skillsHoveredBarX - ui(16) - ui(8);
            int popupY = skillsHoveredBarY - ui(16) - ui(16);
            CommonGuiTextures.drawMenuTextureBox(graphics, popupX, popupY, ui(96), ui(96), 1.0f / guiScale(), true);
            // SDV: profession icon at (c.bounds.X - 8, c.bounds.Y - 32 + 16)
            LevelUpMenuTextures.drawProfession(graphics,
                skillsHoveredBarX - ui(8),
                skillsHoveredBarY - ui(32) + ui(16),
                skillsHoveredProfessionId, s4);
        }

        drawSkillsLowerSection(graphics);
        drawSkillsMasteryProgress(graphics);

        // --- Hover tooltip ---
        if (!skillsHoverText.isEmpty()) {
            drawSkillsTooltip(graphics, mouseX, mouseY, skillsHoverTitle, skillsHoverText);
        }
    }

    private void drawSkillsLowerSection(GuiGraphics graphics) {
        float s4 = mapping.s4();
        BundleClientData bundleData = BundleClientData.INSTANCE;

        graphics.enableScissor(menuX, menuY, menuX + skillsPageWidth(), menuY + menuHeight);
        try {
            int x = menuX + ui(32 * 2);
            int y = skillsLowerPartitionY();
            boolean isJoja = ClientPlayerDataCache.hasMailFlag("JojaMember");
            boolean canReadJunimoText = bundleData.canReadJunimoText()
                || ClientPlayerDataCache.hasMailFlag(CCStoryFlags.CAN_READ_JUNIMO)
                || ClientPlayerDataCache.hasMailFlag("canReadJunimoText");

            x += ui(80);
            y += ui(16);
            if (isJoja || canReadJunimoText) {
                if (isJoja) {
                    CommonGuiTextures.drawSkillsJojaLogo16(graphics, x - ui(80), y - ui(16), s4, 0.7f);
                } else {
                    CommonGuiTextures.drawSkillsCcRoom16(graphics, x, y, areaComplete(bundleData, 5, "ccBulletin"), false, s4, 0.7f);
                }
                CommonGuiTextures.drawSkillsCcRoom16(graphics, x + ui(60), y + ui(28), areaComplete(bundleData, 3, "ccBoilerRoom"), isJoja, s4, 0.7f);
                CommonGuiTextures.drawSkillsCcRoom16(graphics, x + ui(60), y + ui(88), areaComplete(bundleData, 4, "ccVault"), isJoja, s4, 0.7f);
                CommonGuiTextures.drawSkillsCcRoom16(graphics, x - ui(60), y + ui(28), areaComplete(bundleData, 1, "ccCraftsRoom"), isJoja, s4, 0.7f);
                CommonGuiTextures.drawSkillsCcRoom16(graphics, x - ui(60), y + ui(88), areaComplete(bundleData, 2, "ccFishTank"), isJoja, s4, 0.7f);
                CommonGuiTextures.drawSkillsCcRoom16(graphics, x, y + ui(120), areaComplete(bundleData, 0, "ccPantry"), isJoja, s4, 0.7f);
            } else {
                CommonGuiTextures.drawSkillsCcUnknown16(graphics, x - ui(80), y - ui(16), s4, 0.7f);
            }

            x += ui(124);
            graphics.fill(x, y - ui(16), x + ui(4), y - ui(16) + ui(600 / 3 - 32 - 4), 0xFFD68F54);

            int xHouseOffset = 0;
            String houseText = Component.translatable("stardewcraft.skills_page.house_level", 1).getString();
            if (Math.round(this.font.width(houseText) * sdvTextScale() * guiScale()) > 120) {
                xHouseOffset -= ui(20);
            }
            y += ui(108);
            x += ui(28);
            CommonGuiTextures.drawSkillsHouseIcon(graphics, x + xHouseOffset + ui(20), y - ui(4), s4, 0.7f);
            drawScaledSdvTextWithShadow(graphics, houseText, x + xHouseOffset + ui(72), y, sdvTextScale(), SDV_TEXT_COLOR);

            x += ui(180);
            y -= ui(8);
            boolean drawSkull = false;
            int lowestLevel = ClientPlayerDataCache.getMaxMineFloorReached();
            if (lowestLevel > 120) {
                lowestLevel -= 120;
                drawSkull = true;
            }
            CommonGuiTextures.drawSkillsMineIcon16(graphics, x + ui(8), y, lowestLevel != 0, s4, 0.7f);
            if (lowestLevel != 0) {
                drawScaledSdvTextWithShadow(graphics, Integer.toString(lowestLevel), x + ui(72) + (drawSkull ? ui(8) : 0), y + ui(8), sdvTextScale(), SDV_TEXT_COLOR);
            }
            if (drawSkull) {
                CommonGuiTextures.drawSkillsSkullIcon16(graphics, x + ui(40), y + ui(24), s4, 0.7f);
            }

            x += ui(120);
            int stardropsFound = Math.max(0, Math.min(7, (ClientPlayerDataCache.getBaseMaxEnergy() - 270) / StardropItem.MAX_ENERGY_GAIN));
            CommonGuiTextures.drawSkillsStardropIcon16(graphics, x + ui(32), y - ui(4), stardropsFound > 0, s4, 0.7f);
            if (stardropsFound > 0) {
                int stardropColor = stardropsFound >= 7 ? 0xFFA01EEB : SDV_TEXT_COLOR;
                drawScaledSdvTextWithShadow(graphics, "x " + stardropsFound, x + ui(88), y + ui(8), sdvTextScale(), stardropColor);
            }

            drawWinterStarSecretFriendReminder(graphics, x, y);
        } finally {
            graphics.disableScissor();
        }
    }

    /** StardewValley.Menus/SkillsPage.cs secret-friend portrait + gift marker. */
    private void drawWinterStarSecretFriendReminder(GuiGraphics graphics, int x, int y) {
        com.stardew.craft.time.StardewTimeManager time =
            com.stardew.craft.client.hud.StardewTimeHud.getClientTimeCache();
        int year = time.getCurrentYear();
        int day = time.getCurrentDay();
        int minute = time.getCurrentTime();
        boolean visibleDate = (day >= 18 && day < 25) || (day == 25 && minute < 15 * 60);
        String recipient = ClientPlayerDataCache.getWinterStarRecipient();
        if (time.getCurrentSeason() != 3
            || !visibleDate
            || !ClientPlayerDataCache.hasMailFlag("sawSecretSanta" + year)
            || recipient == null
            || recipient.isBlank()) {
            return;
        }

        PortraitResource portrait = resolveSocialMugshot(recipient);
        // Vanilla getMugShotSourceRect() is 16x24, then trims five source pixels.
        graphics.blit(portrait.texture(), x + ui(180), y, ui(64), ui(76),
            0, 0, 16, 19, portrait.sheetWidth(), portrait.sheetHeight());
        ResourceLocation cursors = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/cursors.png");
        graphics.blit(cursors, x + ui(244), y + ui(40), ui(40), ui(44),
            147, 412, 10, 11, 704, 2256);
    }

    private boolean areaComplete(BundleClientData bundleData, int areaId, String vanillaMailFlag) {
        return bundleData.isAreaComplete(areaId) || ClientPlayerDataCache.hasMailFlag(vanillaMailFlag);
    }

    private void drawSkillsMasteryProgress(GuiGraphics graphics) {
        long masteryExp = ClientPlayerDataCache.getMasteryExp();
        int masteryBaseYSdv = 492;
        if (masteryExp == 0L) {
            int emptyMasteryWidth = Math.round(142.0f * mapping.s4());
            int emptyMasteryX = menuX + skillsPageWidth() - ui(BORDER_WIDTH) - emptyMasteryWidth;
            CommonGuiTextures.drawSkillsMasteryEmpty16Tint(graphics, emptyMasteryX, menuY + ui(477),
                mapping.s4(), 1.0f, 1.0f, 1.0f, 0.7f);
            return;
        }

        int masteryLevel = MasteryProgress.currentLevel(masteryExp);
        String masteryText = Component.translatable("stardewcraft.mastery.menu.overview").getString();
        if (masteryText.endsWith(":")) {
            masteryText = masteryText.substring(0, masteryText.length() - 1);
        }

        float textScale = sdvTextScale();
        int masteryTextWidthSdv = Math.round(this.font.width(masteryText) * textScale * guiScale());
        int xOffsetSdv = masteryTextWidthSdv - 64;

        drawScaledSdvText(graphics, masteryText, menuX + ui(256), menuY + ui(masteryBaseYSdv), textScale, SDV_TEXT_COLOR);

        int iconX = menuX + ui(xOffsetSdv + 332);
        int iconY = menuY + ui(484);
        CommonGuiTextures.drawMasteryIcon16Tint(graphics, iconX + ui(4), iconY + ui(4),
            mapping.s4(), 0.0f, 0.0f, 0.0f, 0.35f);
        CommonGuiTextures.drawMasteryIcon16Tint(graphics, iconX, iconY,
            mapping.s4(), 1.0f, 1.0f, 1.0f, 1.0f);

        float widthScale = 0.64f - (masteryTextWidthSdv - 100.0f) / 800.0f;
        if (usesRussianSkillsLayout()) {
            widthScale += 0.1f;
        }

        int shadowX = menuX + ui(xOffsetSdv + 380) - Math.max(1, ui(1));
        int darkX = menuX + ui(xOffsetSdv + 384);
        int midX = menuX + ui(xOffsetSdv + 388);
        // Constrain the actual GUI-space track for every locale and reserve room for the
        // NumberSprite drawn after it. This avoids relying on SpriteFont-derived width math.
        int trackRightLimit = menuX + skillsPageWidth() - ui(BORDER_WIDTH + 48);
        int availableTrackWidth = Math.max(1, trackRightLimit - darkX - ui(4) - 1);
        float maxWidthScale = availableTrackWidth * guiScale() / 584.0f;
        widthScale = Math.max(0.1f, Math.min(widthScale, maxWidthScale));

        int masteryBaseY = menuY + ui(masteryBaseYSdv);
        graphics.fill(shadowX, masteryBaseY, shadowX + ui(Math.round(584.0f * widthScale)) + ui(4), masteryBaseY + ui(40), 0x59000000);
        graphics.fill(darkX, masteryBaseY - ui(4), darkX + ui(Math.round(((masteryLevel >= MasteryProgress.MAX_LEVEL) ? 144.0f : 146.0f) * 4.0f * widthScale)) + ui(4), masteryBaseY - ui(4) + ui(40), 0xFF3C3C19);
        graphics.fill(midX, masteryBaseY, midX + ui(Math.round(576.0f * widthScale)), masteryBaseY + ui(32), 0xFFAD814F);

        drawMasteryProgressBar(graphics, menuX + ui(xOffsetSdv + 276), menuY + ui(348), widthScale);

        int levelNumberX = menuX + ui(xOffsetSdv + 408 + Math.round(584.0f * widthScale));
        int levelNumberY = masteryBaseY + ui(20);
        drawNumberSprite(graphics, masteryLevel, levelNumberX, levelNumberY, 0x000000, 0.35f);
        drawNumberSprite(graphics, masteryLevel, levelNumberX + ui(4), levelNumberY - ui(4), 0xF4A460, masteryLevel == 0 ? 0.75f : 1.0f);
    }

    private void drawMasteryProgressBar(GuiGraphics graphics, int topLeftX, int topLeftY, float widthScale) {
        long masteryExp = ClientPlayerDataCache.getMasteryExp();
        int levelsAchieved = MasteryProgress.currentLevel(masteryExp);
        long currentProgressXp = masteryExp - MasteryProgress.expForLevel(levelsAchieved);
        long expNeeded = MasteryProgress.expForLevel(levelsAchieved + 1) - MasteryProgress.expForLevel(levelsAchieved);
        if (expNeeded <= 0L) {
            expNeeded = 1L;
        }

        int barWidthSdv = levelsAchieved >= MasteryProgress.MAX_LEVEL
            ? Math.round(576.0f * widthScale)
            : Math.round(576.0f * currentProgressXp / expNeeded * widthScale);
        if (levelsAchieved < MasteryProgress.MAX_LEVEL && barWidthSdv <= 0) {
            return;
        }

        int light = 0xFF3CB450;
        int med = 0xFF00713E;
        int medDark = 0xFF005032;
        int dark = 0xFF003C1E;
        if (levelsAchieved >= MasteryProgress.MAX_LEVEL) {
            light = 0xFFDCDCDC;
            med = 0xFF8C8C8C;
            medDark = 0xFF505050;
            dark = med;
        } else if (widthScale != 1.0f) {
            dark = medDark;
        }

        int x = topLeftX + ui(112);
        int y = topLeftY + ui(144);
        int barWidth = ui(barWidthSdv);
        graphics.fill(x, y, x + barWidth, y + ui(32), med);
        graphics.fill(x, y + ui(4), x + ui(4), y + ui(32), medDark);
        if (barWidthSdv > 8) {
            graphics.fill(x, y + ui(28), x + barWidth - ui(8), y + ui(32), medDark);
            graphics.fill(x + ui(4), y, x + barWidth, y + ui(4), light);
            graphics.fill(x - ui(8) + barWidth, y, x - ui(4) + barWidth, y + ui(28), light);
            graphics.fill(x - ui(4) + barWidth, y, x + barWidth, y + ui(32), dark);
        }

        if (levelsAchieved < MasteryProgress.MAX_LEVEL) {
            String text = currentProgressXp + "/" + expNeeded;
            float textScale = sdvTextScale();
            int textWidth = Math.round(this.font.width(text) * textScale);
            int textX = topLeftX + ui(112) + ui(Math.round(288.0f * widthScale)) - textWidth / 2;
            int textY = topLeftY + ui(146);
            drawScaledSdvText(graphics, text, textX, textY, textScale, 0xBFFFFFFF);
        }
    }

    private void drawScaledSdvText(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(this.font, Component.literal(text), 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawScaledSdvTextWithShadow(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
        int horizontalShadowOffset = -Math.max(1, ui(2));
        int verticalShadowOffset = Math.max(1, ui(2));
        drawScaledSdvText(graphics, text, x + horizontalShadowOffset, y + verticalShadowOffset, scale, SDV_TEXT_SHADOW);
        drawScaledSdvText(graphics, text, x + horizontalShadowOffset, y, scale, SDV_TEXT_SHADOW);
        drawScaledSdvText(graphics, text, x, y + verticalShadowOffset, scale, SDV_TEXT_SHADOW);
        drawScaledSdvText(graphics, text, x, y, scale, color);
    }

    private void drawPowersPage(GuiGraphics graphics, int mouseX, int mouseY) {
        int baseX = menuX + ui(BORDER_WIDTH) + ui(32);
        int baseY = menuY + ui(BORDER_WIDTH) + ui(96) - ui(16);
        int collectionWidth = 9;
        int slotStep = ui(76);
        int iconSize = ui(64);
        float iconScale = mapping.s4();
        PowerEntry hoveredEntry = null;
        boolean hoveredUnlocked = false;

        for (int index = 0; index < POWER_ENTRIES.length; index++) {
            PowerEntry entry = POWER_ENTRIES[index];
            int x = baseX + index % collectionWidth * slotStep;
            int y = baseY + index / collectionWidth * slotStep;
            boolean unlocked = isPowerUnlocked(entry);
            if (unlocked) {
                CommonGuiTextures.drawPowerIconTint(graphics, x, y, entry.iconIndex(), iconScale,
                    1.0f, 1.0f, 1.0f, 1.0f);
            } else {
                CommonGuiTextures.drawPowerIconTint(graphics, x, y, entry.iconIndex(), iconScale,
                    0.0f, 0.0f, 0.0f, 0.2f);
            }

            if (mouseX >= x && mouseX < x + iconSize && mouseY >= y && mouseY < y + iconSize) {
                hoveredEntry = entry;
                hoveredUnlocked = unlocked;
            }
        }

        if (hoveredEntry != null && !hoveredUnlocked) {
            graphics.renderTooltip(this.font, Component.literal("???").withStyle(ChatFormatting.BOLD), mouseX, mouseY);
            return;
        }
        if (hoveredEntry != null) {
            ItemStack tooltipStack = powerTooltipStack(hoveredEntry.tooltipItemId());
            if (!tooltipStack.isEmpty()) {
                graphics.renderTooltip(this.font, tooltipStack, mouseX, mouseY);
            }
        }
    }

    private void drawCollectionsPage(GuiGraphics graphics, int mouseX, int mouseY) {
        if (currentCollectionTab == COLLECTION_SECRET_NOTES && !hasSecretNotesCollection()) {
            currentCollectionTab = COLLECTION_SHIPPED;
            currentCollectionPage = 0;
        }
        drawCollectionSideTabs(graphics, mouseX, mouseY);
        if (currentCollectionTab == COLLECTION_SECRET_NOTES) {
            drawSecretNotesCollection(graphics, mouseX, mouseY);
            return;
        }
        if (currentCollectionTab == COLLECTION_LETTERS) {
            drawLettersCollection(graphics, mouseX, mouseY);
            return;
        }

        List<CollectionEntry> entries = collectionEntries(currentCollectionTab);
        int pageCount = Math.max(1, (entries.size() + COLLECTION_PAGE_SIZE - 1) / COLLECTION_PAGE_SIZE);
        currentCollectionPage = Mth.clamp(currentCollectionPage, 0, pageCount - 1);
        int first = currentCollectionPage * COLLECTION_PAGE_SIZE;
        int last = Math.min(entries.size(), first + COLLECTION_PAGE_SIZE);
        int hovered = -1;
        for (int index = first; index < last; index++) {
            int local = index - first;
            int x = collectionBaseX() + local % COLLECTION_COLUMNS * ui(68);
            int y = collectionBaseY() + local / COLLECTION_COLUMNS * ui(68);
            boolean isHovered = contains(mouseX, mouseY, x, y, ui(64), ui(64));
            float hoverScale = collectionHoverScale[local] <= 0.0F ? 1.0F : collectionHoverScale[local];
            hoverScale = stepScale(hoverScale, isHovered ? 1.025F : 1.0F, 0.01F);
            collectionHoverScale[local] = hoverScale;

            CollectionEntry entry = entries.get(index);
            float itemScale = mapping.s4() * hoverScale;
            int itemSize = CommonGuiTextures.itemSize(itemScale);
            int itemX = x + (ui(64) - itemSize) / 2;
            int itemY = y + (ui(64) - itemSize) / 2;
            if (entry.discovered()) {
                CommonGuiTextures.drawItem(graphics, entry.stack(), itemX, itemY, itemScale);
            } else if (entry.known()) {
                CommonGuiTextures.drawItemTint(graphics, entry.stack(), itemX, itemY, itemScale,
                        0.41F, 0.41F, 0.41F, 0.4F);
            } else {
                CommonGuiTextures.drawItemTint(graphics, entry.stack(), itemX, itemY, itemScale,
                        0.0F, 0.0F, 0.0F, 0.2F);
            }
            if (isHovered) {
                hovered = index;
            }
        }

        drawCollectionPageArrows(graphics, pageCount);
        if (hovered >= 0) {
            CollectionEntry entry = entries.get(hovered);
            if (entry.discovered() || entry.known()) {
                graphics.renderTooltip(this.font, entry.stack(), mouseX, mouseY);
            } else {
                graphics.renderTooltip(this.font, Component.literal("???"), mouseX, mouseY);
            }
        }
    }

    private void drawLettersCollection(GuiGraphics graphics, int mouseX, int mouseY) {
        List<LetterCollectionEntry> entries = letterCollectionEntries();
        int pageCount = Math.max(1, (entries.size() + COLLECTION_PAGE_SIZE - 1) / COLLECTION_PAGE_SIZE);
        currentCollectionPage = Mth.clamp(currentCollectionPage, 0, pageCount - 1);
        int first = currentCollectionPage * COLLECTION_PAGE_SIZE;
        int last = Math.min(entries.size(), first + COLLECTION_PAGE_SIZE);
        int hovered = -1;
        for (int index = first; index < last; index++) {
            int local = index - first;
            int x = collectionBaseX() + local % COLLECTION_COLUMNS * ui(68);
            int y = collectionBaseY() + local / COLLECTION_COLUMNS * ui(68);
            boolean isHovered = contains(mouseX, mouseY, x, y, ui(64), ui(64));
            float hoverScale = collectionHoverScale[local] <= 0.0F ? 1.0F : collectionHoverScale[local];
            hoverScale = stepScale(hoverScale, isHovered ? 1.025F : 1.0F, 0.01F);
            collectionHoverScale[local] = hoverScale;
            drawVanillaLetterCollectionIcon(graphics, x, y, hoverScale);
            if (isHovered) hovered = index;
        }

        drawCollectionPageArrows(graphics, pageCount);
        if (hovered >= 0) {
            graphics.renderTooltip(this.font, Component.literal(entries.get(hovered).title()), mouseX, mouseY);
        }
    }

    private void drawVanillaLetterCollectionIcon(GuiGraphics graphics, int x, int y, float hoverScale) {
        int baseWidth = ui(56);
        int baseHeight = ui(44);
        int width = Math.round(baseWidth * hoverScale);
        int height = Math.round(baseHeight * hoverScale);
        int drawX = x + (ui(64) - width) / 2;
        int drawY = y + (ui(64) - height) / 2;
        int shadowOffset = ui(4);

        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 0.35F);
        graphics.blit(StardewGuiUtil.CURSORS, drawX + shadowOffset, drawY + shadowOffset,
                width, height, 190, 423, 14, 11, 704, 2256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(StardewGuiUtil.CURSORS, drawX, drawY,
                width, height, 190, 423, 14, 11, 704, 2256);
    }

    private void drawCollectionSideTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int[] tabs = collectionTabs();
        for (int order = 0; order < tabs.length; order++) {
            int tab = tabs[order];
            int x = collectionSideTabX(tab);
            int y = collectionSideTabY(order);
            int sourceX = switch (tab) {
                case COLLECTION_SHIPPED, COLLECTION_FISH -> 640;
                case COLLECTION_ARTIFACTS, COLLECTION_ACHIEVEMENTS -> 656;
                case COLLECTION_MINERALS, COLLECTION_SECRET_NOTES -> 672;
                default -> 688;
            };
            int sourceY = switch (tab) {
                case COLLECTION_SHIPPED, COLLECTION_ACHIEVEMENTS,
                     COLLECTION_SECRET_NOTES, COLLECTION_LETTERS -> 80;
                default -> 64;
            };
            graphics.blit(StardewGuiUtil.CURSORS, x, y, ui(64), ui(64),
                    sourceX, sourceY, 16, 16, 704, 2256);
            if (tab == COLLECTION_SECRET_NOTES && contains(mouseX, mouseY, x, y, ui(64), ui(64))) {
                graphics.renderTooltip(this.font,
                        Component.translatable("stardewcraft.collections.secret_notes"), mouseX, mouseY);
            }
        }
    }

    private void drawSecretNotesCollection(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!hasMagnifyingGlass()) {
            return;
        }
        int hovered = -1;
        for (int number = SecretNoteItem.FIRST_DISPLAY_NOTE;
             number <= SecretNoteItem.LAST_DISPLAY_NOTE;
             number++) {
            int local = number - SecretNoteItem.FIRST_DISPLAY_NOTE;
            int x = collectionBaseX() + local % COLLECTION_COLUMNS * ui(68);
            int y = collectionBaseY() + local / COLLECTION_COLUMNS * ui(68);
            boolean isHovered = contains(mouseX, mouseY, x, y, ui(64), ui(64));
            float hoverScale = collectionHoverScale[local] <= 0.0F ? 1.0F : collectionHoverScale[local];
            hoverScale = stepScale(hoverScale, isHovered ? 1.025F : 1.0F, 0.01F);
            collectionHoverScale[local] = hoverScale;

            ItemStack icon = SecretNoteItem.createCreativeVariant(number);
            float itemScale = mapping.s4() * hoverScale;
            int itemSize = CommonGuiTextures.itemSize(itemScale);
            int itemX = x + (ui(64) - itemSize) / 2;
            int itemY = y + (ui(64) - itemSize) / 2;
            if (hasSeenSecretNote(number)) {
                CommonGuiTextures.drawItem(graphics, icon, itemX, itemY, itemScale);
            } else {
                CommonGuiTextures.drawItemTint(graphics, icon, itemX, itemY, itemScale,
                        0.0F, 0.0F, 0.0F, 0.2F);
            }
            if (isHovered) {
                hovered = number;
            }
        }

        if (hovered > 0) {
            if (hasSeenSecretNote(hovered)) {
                drawSecretNoteTooltip(graphics, mouseX, mouseY, hovered);
            } else {
                graphics.renderTooltip(this.font, Component.literal("???"), mouseX, mouseY);
            }
        }
    }

    private void drawSecretNoteTooltip(GuiGraphics graphics, int mouseX, int mouseY, int noteNumber) {
        ResourceLocation id = SecretNoteRegistry.byDisplayNumber(noteNumber);
        StardewSecretNoteDefinition definition = id == null ? null : SecretNoteRegistry.get(id);
        String title = Component.translatable("stardewcraft.secret_note.title", noteNumber).getString();
        if (definition == null) {
            graphics.renderTooltip(this.font, Component.literal(title), mouseX, mouseY);
            return;
        }
        if (definition.imageIndex() >= 0) {
            graphics.renderTooltip(this.font, Component.literal(title), mouseX, mouseY);
            drawSecretNoteImagePreview(graphics, mouseX, mouseY, definition.imageIndex());
            return;
        }

        String text = Component.translatable(definition.text()).getString().replace('^', '\n');
        if (this.minecraft != null && this.minecraft.player != null) {
            text = text.replace("@", ClientPlayerDataCache.getPlayerDisplayName(
                    this.minecraft.player.getName().getString()));
        }
        drawSecretNoteTextTooltip(graphics, mouseX, mouseY, title, text);
    }

    private void drawSecretNoteTextTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                           String title, String text) {
        float textScale = sdvTextScale();
        int padding = ui(16);
        int maxTextWidth = ui(512);
        int wrapWidth = Math.max(1, Math.round(maxTextWidth / textScale));
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String paragraph : text.split("\\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            } else {
                lines.addAll(this.font.split(Component.literal(paragraph), wrapWidth));
            }
        }
        if (lines.size() > 15) {
            lines = new ArrayList<>(lines.subList(0, 15));
            lines.add(Component.literal("(...)").getVisualOrderText());
        }

        int lineHeight = Math.round(this.font.lineHeight * textScale) + 2;
        int contentWidth = Math.round(this.font.width(title) * textScale);
        for (FormattedCharSequence line : lines) {
            contentWidth = Math.max(contentWidth, Math.round(this.font.width(line) * textScale));
        }
        int boxW = Math.min(maxTextWidth, contentWidth) + padding * 2;
        int boxH = padding * 2 + lineHeight * (lines.size() + 1) + ui(4);
        int boxX = mouseX + ui(32);
        int boxY = mouseY + ui(32);
        if (boxX + boxW > this.width) boxX = mouseX - boxW;
        if (boxY + boxH > this.height) boxY = mouseY - boxH;
        boxX = Math.max(0, boxX);
        boxY = Math.max(0, boxY);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        CommonGuiTextures.drawMenuTextureBox(graphics, boxX, boxY, boxW, boxH,
                1.0F / guiScale(), true);
        int drawX = boxX + padding;
        int drawY = boxY + padding;
        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0);
        graphics.pose().scale(textScale, textScale, 1.0F);
        graphics.drawString(this.font, Component.literal(title).withStyle(ChatFormatting.BOLD),
                0, 0, 0xFF5B3A1A, false);
        graphics.pose().popPose();
        drawY += lineHeight + ui(4);
        for (FormattedCharSequence line : lines) {
            graphics.pose().pushPose();
            graphics.pose().translate(drawX, drawY, 0);
            graphics.pose().scale(textScale, textScale, 1.0F);
            graphics.drawString(this.font, line, 0, 0, 0xFF5B3A1A, false);
            graphics.pose().popPose();
            drawY += lineHeight;
        }
        graphics.pose().popPose();
    }

    private void drawSecretNoteImagePreview(GuiGraphics graphics, int mouseX, int mouseY, int imageIndex) {
        int boxSize = ui(288);
        int boxX = Mth.clamp(mouseX, 0, Math.max(0, this.width - boxSize));
        int boxY = Mth.clamp(mouseY + ui(96), 0, Math.max(0, this.height - boxSize));
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        CommonGuiTextures.drawMenuTextureBox(graphics, boxX, boxY, boxSize, boxSize,
                1.0F / guiScale(), true);
        int sourceX = Math.floorMod(imageIndex, 4) * 64;
        int sourceY = Math.floorDiv(imageIndex, 4) * 64;
        graphics.pose().pushPose();
        graphics.pose().translate(boxX + ui(16), boxY + ui(16), 1);
        graphics.pose().scale(mapping.s4(), mapping.s4(), 1.0F);
        graphics.blit(SECRET_NOTE_IMAGES, 0, 0, sourceX, sourceY,
                64, 64, 256, 256);
        graphics.pose().popPose();
        graphics.pose().popPose();
    }

    private List<CollectionEntry> collectionEntries(int tab) {
        Map<Item, Boolean> cookedOutputs = new HashMap<>();
        Map<Item, Boolean> knownCookingOutputs = new HashMap<>();
        if (tab == COLLECTION_COOKING) {
            for (ResourceLocation recipeId : VanillaCookingRecipeData.getRecipeIds()) {
                ItemStack output = VanillaCookingRecipeData.getOutputStack(recipeId, 1);
                if (output.isEmpty()) continue;
                String storageId = VanillaCookingRecipeData.storageId(recipeId);
                boolean made = ClientPlayerDataCache.getRecipeCraftCount(storageId) > 0;
                boolean known = made || ClientPlayerDataCache.hasRecipe(storageId);
                cookedOutputs.merge(output.getItem(), made, Boolean::logicalOr);
                knownCookingOutputs.merge(output.getItem(), known, Boolean::logicalOr);
            }
        }

        List<CollectionEntry> entries = new ArrayList<>();
        for (VanillaObjectCatalog.Entry source : VanillaObjectCatalog.entriesForCollection(tab)) {
            ItemStack stack = VanillaObjectCatalog.stackFor(source);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String itemId = id.toString();
            if (tab == COLLECTION_FISH) {
                boolean caught = ClientPlayerDataCache.getFishCatchCount(itemId) > 0;
                entries.add(new CollectionEntry(stack, caught, caught, source));
            } else if (tab == COLLECTION_ARTIFACTS) {
                boolean donated = ClientMuseumDonationCache.isDonated(itemId);
                entries.add(new CollectionEntry(stack, donated, donated, source));
            } else if (tab == COLLECTION_MINERALS) {
                boolean donated = ClientMuseumDonationCache.isDonated(itemId);
                entries.add(new CollectionEntry(stack, donated, donated, source));
            } else if (tab == COLLECTION_COOKING) {
                boolean made = cookedOutputs.getOrDefault(item, false);
                boolean known = knownCookingOutputs.getOrDefault(item, false);
                entries.add(new CollectionEntry(stack, made, known, source));
            } else if (tab == COLLECTION_SHIPPED) {
                boolean shipped = ClientPlayerDataCache.hasShippedMatching(
                        shippedId -> VanillaObjectCatalog.matchesItemId(source, shippedId));
                entries.add(new CollectionEntry(stack, shipped, shipped, source));
            }
        }
        entries.sort(Comparator.comparing(CollectionEntry::source, VanillaObjectCatalog.sourceOrder()));
        return entries;
    }

    private List<LetterCollectionEntry> letterCollectionEntries() {
        Set<String> received = ClientPlayerDataCache.getMailFlags();
        List<LetterCollectionEntry> entries = new ArrayList<>();
        for (ClientMailIndex.Entry entry : ClientMailIndex.entries()) {
            if (!received.contains(entry.mailId())) continue;
            String localizedText = Component.translatable(entry.textKey()).getString();
            entries.add(new LetterCollectionEntry(entry.mailId(), MailTextSyntax.title(localizedText)));
        }
        return entries;
    }

    private void drawCollectionPageArrows(GuiGraphics graphics, int pageCount) {
        if (currentCollectionPage > 0) {
            CommonGuiTextures.drawBackArrow(graphics, collectionBackX(), collectionArrowY(), mapping.s4());
        }
        if (currentCollectionPage + 1 < pageCount) {
            CommonGuiTextures.drawForwardArrow(graphics, collectionForwardX(), collectionArrowY(), mapping.s4());
        }
    }

    private int collectionSideTabAt(double mouseX, double mouseY) {
        int[] tabs = collectionTabs();
        for (int order = 0; order < tabs.length; order++) {
            int tab = tabs[order];
            if (contains(mouseX, mouseY, collectionSideTabX(tab), collectionSideTabY(order),
                    ui(64), ui(64))) {
                return tab;
            }
        }
        return -1;
    }

    private int secretNoteAt(double mouseX, double mouseY) {
        if (currentCollectionTab != COLLECTION_SECRET_NOTES || !hasMagnifyingGlass()) {
            return -1;
        }
        for (int number = SecretNoteItem.FIRST_DISPLAY_NOTE;
             number <= SecretNoteItem.LAST_DISPLAY_NOTE;
             number++) {
            int local = number - SecretNoteItem.FIRST_DISPLAY_NOTE;
            int x = collectionBaseX() + local % COLLECTION_COLUMNS * ui(68);
            int y = collectionBaseY() + local / COLLECTION_COLUMNS * ui(68);
            if (contains(mouseX, mouseY, x, y, ui(64), ui(64))) {
                return number;
            }
        }
        return -1;
    }

    private String letterAt(double mouseX, double mouseY) {
        if (currentCollectionTab != COLLECTION_LETTERS) return null;
        List<LetterCollectionEntry> entries = letterCollectionEntries();
        int first = currentCollectionPage * COLLECTION_PAGE_SIZE;
        int last = Math.min(entries.size(), first + COLLECTION_PAGE_SIZE);
        for (int index = first; index < last; index++) {
            int local = index - first;
            int x = collectionBaseX() + local % COLLECTION_COLUMNS * ui(68);
            int y = collectionBaseY() + local / COLLECTION_COLUMNS * ui(68);
            if (contains(mouseX, mouseY, x, y, ui(64), ui(64))) {
                return entries.get(index).mailId();
            }
        }
        return null;
    }

    private int[] collectionTabs() {
        return hasSecretNotesCollection()
                ? new int[] {COLLECTION_SHIPPED, COLLECTION_FISH, COLLECTION_ARTIFACTS,
                        COLLECTION_MINERALS, COLLECTION_COOKING, COLLECTION_ACHIEVEMENTS,
                        COLLECTION_LETTERS, COLLECTION_SECRET_NOTES}
                : new int[] {COLLECTION_SHIPPED, COLLECTION_FISH, COLLECTION_ARTIFACTS,
                        COLLECTION_MINERALS, COLLECTION_COOKING, COLLECTION_ACHIEVEMENTS,
                        COLLECTION_LETTERS};
    }

    private boolean hasSecretNotesCollection() {
        return !ClientPlayerDataCache.getSecretNotesSeen().isEmpty();
    }

    private boolean hasMagnifyingGlass() {
        return ClientPlayerDataCache.hasMailFlag("HasMagnifyingGlass")
                || ClientPlayerDataCache.hasSpecialItem("stardewcraft:magnifying_glass");
    }

    private boolean hasSeenSecretNote(int number) {
        ResourceLocation id = SecretNoteRegistry.byDisplayNumber(number);
        return id != null && ClientPlayerDataCache.hasSeenSecretNote(id.toString());
    }

    private int collectionBaseX() {
        return menuX + ui(40) + ui(16);
    }

    private int collectionBaseY() {
        return menuY + ui(40) + ui(96) - ui(16);
    }

    private int collectionSideTabX(int tab) {
        return menuX - ui(48) + (currentCollectionTab == tab ? ui(8) : 0);
    }

    private int collectionSideTabY(int order) {
        return menuY + ui(64 * (2 + order));
    }

    private int collectionBackX() {
        return menuX + ui(48);
    }

    private int collectionForwardX() {
        return menuX + menuWidth - ui(92);
    }

    private int collectionArrowY() {
        return menuY + menuHeight - ui(80);
    }

    private boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private ItemStack powerTooltipStack(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private ItemStack stackFromEquipmentId(String itemId) {
        if (CombinedRingData.isEncodedEquipmentSlot(itemId)) {
            return CombinedRingData.stackFromEquipmentSlot(itemId);
        }
        return powerTooltipStack(itemId);
    }

    private boolean isPowerUnlocked(PowerEntry entry) {
        return ClientPlayerDataCache.hasMailFlag(entry.mailFlag())
            || ClientPlayerDataCache.hasSpecialItem(entry.specialItemId());
    }

    /**
     * Get the profession a player chose for a given skill row and level.
     * Row order: 0=Farming, 1=Mining, 2=Foraging, 3=Fishing, 4=Combat
     */
    private ProfessionType getChosenProfessionForRow(int rowIndex, int level) {
        SkillType skill = SKILLS_PAGE_ROW_ORDER[rowIndex];
        if (level == 5) {
            ProfessionType[] options = ProfessionType.getLevel5Options(skill);
            for (ProfessionType opt : options) {
                if (ClientPlayerDataCache.hasProfession(opt)) return opt;
            }
        } else if (level == 10) {
            // Need to know the lv5 choice to get lv10 options
            ProfessionType[] lv5Options = ProfessionType.getLevel5Options(skill);
            for (ProfessionType lv5 : lv5Options) {
                if (ClientPlayerDataCache.hasProfession(lv5)) {
                    ProfessionType[] lv10Options = ProfessionType.getLevel10Options(skill, lv5);
                    for (ProfessionType opt : lv10Options) {
                        if (ClientPlayerDataCache.hasProfession(opt)) return opt;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Draw SDV-style hover tooltip (matching IClickableMenu.drawHoverText)
     */
    private void drawSkillsTooltip(GuiGraphics graphics, int mouseX, int mouseY, String title, String text) {
        if (text.isEmpty() && title.isEmpty()) return;

        float ttScale = sdvTextScale();
        int padding = ui(16);
        int maxWidth = ui(300);
        int scaledLineH = Math.round(this.font.lineHeight * ttScale);

        // Calculate text dimensions (at scaled size)
        List<String> textLines = new ArrayList<>();
        if (!text.isEmpty()) {
            for (String line : text.split("\n")) {
                textLines.add(line);
            }
        }

        int textWidth = 0;
        for (String line : textLines) {
            textWidth = Math.max(textWidth, Math.round(this.font.width(line) * ttScale));
        }
        if (!title.isEmpty()) {
            textWidth = Math.max(textWidth, Math.round(this.font.width(title) * ttScale));
        }
        textWidth = Math.min(textWidth, maxWidth);

        int textHeight = textLines.size() * (scaledLineH + 2);
        if (!title.isEmpty()) {
            textHeight += scaledLineH + 4;
        }

        int boxW = textWidth + padding * 2;
        int boxH = textHeight + padding * 2;
        int boxX = mouseX + ui(32);
        int boxY = mouseY + ui(32);

        // Keep on screen
        if (boxX + boxW > this.width) boxX = mouseX - boxW;
        if (boxY + boxH > this.height) boxY = mouseY - boxH;
        if (boxX < 0) boxX = 0;
        if (boxY < 0) boxY = 0;

        // Draw tooltip background using SDV textureBox
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        CommonGuiTextures.drawMenuTextureBox(graphics, boxX, boxY, boxW, boxH, 1.0f / guiScale(), true);

        int contentX = boxX + padding;
        int contentY = boxY + padding;

        if (!title.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(contentX, contentY, 0);
            graphics.pose().scale(ttScale, ttScale, 1.0f);
            graphics.drawString(this.font, Component.literal(title).withStyle(ChatFormatting.BOLD), 0, 0, 0xFF5B3A1A, false);
            graphics.pose().popPose();
            contentY += scaledLineH + 4;
        }

        for (String line : textLines) {
            graphics.pose().pushPose();
            graphics.pose().translate(contentX, contentY, 0);
            graphics.pose().scale(ttScale, ttScale, 1.0f);
            graphics.drawString(this.font, Component.literal(line).withStyle(ChatFormatting.BOLD), 0, 0, 0xFF5B3A1A, false);
            graphics.pose().popPose();
            contentY += scaledLineH + 2;
        }
        graphics.pose().popPose();
    }

    // ============ Tab 0: Inventory Page (SDV InventoryPage 1:1 parity) ============

    // --- Grid layout (top section, SDV pixels from menu origin) ---
    // Keep the established Minecraft layout: three backpack rows with a separate hotbar.
    // The lower SDV composition still reserves one extra 64px row + 4px gap because
    // Minecraft has four visible rows instead of vanilla Stardew's three.
    private static final int INV_PAGE_EXTRA_MC_ROW = 68;
    private static final int INV_PAGE_ROW0_Y = 36;
    private static final int INV_PAGE_ROW_STEP = 68;
    private static final int INV_PAGE_HOTBAR_Y = 252;
    private static final int INV_PAGE_PARTITION_Y = 40 + 96 + 192 + INV_PAGE_EXTRA_MC_ROW;

    // --- Equipment slots (lower-left) ---
    private static final int INV_PAGE_EQUIP_X = 48;
    private static final int INV_PAGE_EQUIP_SIZE = 64;
    private static final int INV_PAGE_EQUIP_Y0 = 40 + 96 + 4 + 256 - 12 + INV_PAGE_EXTRA_MC_ROW;
    private static final int INV_PAGE_EQUIP_Y1 = INV_PAGE_EQUIP_Y0 + 64;
    private static final int INV_PAGE_EQUIP_Y2 = INV_PAGE_EQUIP_Y1 + 64;
    private static final int INV_PAGE_COSMETIC_X = 48 + 208;
    private static final int INV_PAGE_TRINKET_X = 48 + 280;
    private static final int INV_PAGE_TRINKET_Y = INV_PAGE_EQUIP_Y0;

    // Empty-slot placeholder tiles (from menu_tiles.png, SDV: getSourceRectForStandardTileSheet)
    private static final int EMPTY_RING_TILE = 41;
    private static final int EMPTY_BOOTS_TILE = 40;
    private static final int EMPTY_TRINKET_TILE = 70;

    // --- Player model area (lower-center, SDV: x=120, y=296 from menu origin) ---
    private static final int INV_PAGE_PLAYER_BG_X = 120;
    private static final int INV_PAGE_PLAYER_BG_Y = 40 + 96 + 256 - 8 + INV_PAGE_EXTRA_MC_ROW;
    private static final int PLAYER_BG_TEX_W = 128;
    private static final int PLAYER_BG_TEX_H = 192;
    private static final int PLAYER_MODEL_SCALE_SDV = 48;
    private static final int INV_PAGE_PLAYER_NAME_Y = 40 + 96 + 448 + 8 + INV_PAGE_EXTRA_MC_ROW;

    // --- Right info panel (lower-right, text centered at this X) ---
    private static final int INV_PAGE_INFO_CENTER_X = 576;
    private static final int INV_PAGE_INFO_Y0 = 40 + 96 + 256 + 4 + INV_PAGE_EXTRA_MC_ROW;
    private static final int INV_PAGE_INFO_Y1 = INV_PAGE_INFO_Y0 + 64;
    private static final int INV_PAGE_INFO_Y2 = INV_PAGE_INFO_Y1 + 64;

    private static final int INV_PAGE_TRASH_Y = 40 + 96 + 192 + 64 + INV_PAGE_EXTRA_MC_ROW;

    // SDV text color: new Color(86, 22, 12)
    private static final int SDV_TEXT_COLOR = 0xFF56160C;
    // SDV Utility.drawTextWithShadow: Game1.textShadowDarkerColor = new Color(221, 148, 84)
    private static final int SDV_TEXT_SHADOW = 0xFFDD9454;
    private static final int SDV_TEXT_COLOR_DIM = 0xFF45120A; // textColor * 0.8

    // Day/night player backgrounds (extracted from SDV LooseSprites)
    private static final ResourceLocation DAYBG =
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/daybg.png");
    private static final ResourceLocation NIGHTBG =
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/nightbg.png");

    // Junimo Note icon (SDV mouseCursors rect 331,374,15,14 × scale 4 = 60×56 within a 64×64 hover slot)
    private static final ResourceLocation JUNIMO_NOTE_ICON =
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/junimo_note_icon.png");
    private static final int JUNIMO_ICON_SRC_W = 15;
    private static final int JUNIMO_ICON_SRC_H = 14;
    private int junimoNotePulser = 0;
    private long junimoIconLastTickMs = 0L;

    private static final int ORGANIZE_ICON_SIZE = 16;

    // ────────────────────────────────────────────────────────────────

    private void drawInventoryPage(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.player == null) return;

        // ── 1. Inventory grid (top section, SDV-like) ──
        drawInvPageGrid(graphics, mouseX, mouseY);

        // ── 2. Horizontal partition (SDV: drawHorizontalPartition) ──
        StardewGuiUtil.drawHorizontalPartition(graphics, menuX, menuY + ui(INV_PAGE_PARTITION_Y),
                menuWidth, mapping.s4());

        // ── 3. Equipment slots (lower-left, SDV placeholder icons) ──
        ItemStack leftRing = ClientPlayerDataCache.getEquippedLeftRingStack();
        ItemStack rightRing = ClientPlayerDataCache.getEquippedRightRingStack();
        ItemStack boots = ClientPlayerDataCache.getEquippedBootsStack();
        String hatId = ClientPlayerDataCache.getEquippedHat();
        String shirtId = ClientPlayerDataCache.getEquippedShirt();
        String pantsId = ClientPlayerDataCache.getEquippedPants();
        drawInvEquipSlot(graphics, 0, leftRing, mouseX, mouseY);
        drawInvEquipSlot(graphics, 1, rightRing, mouseX, mouseY);
        drawInvEquipSlot(graphics, 2, boots, mouseX, mouseY);
        drawInvCosmeticSlot(graphics, 4, hatId, mouseX, mouseY);
        drawInvCosmeticSlot(graphics, 5, shirtId, mouseX, mouseY);
        drawInvCosmeticSlot(graphics, 6, pantsId, mouseX, mouseY);
        if (ClientPlayerDataCache.getUnlockedTrinketSlots() > 0) {
            drawInvTrinketSlot(graphics, ClientPlayerDataCache.getEquippedTrinket(), mouseX, mouseY);
        }

        // ── 4. Player model with day/night background ──
        drawPlayerModelArea(graphics, mouseX, mouseY);

        // ── 5. Right info panel (farm name / money / date) ──
        drawInfoPanel(graphics);

        // ── 6. Organize button (SDV: organizeButton) ──
        drawOrganizeButton(graphics, mouseX, mouseY);

        // ── 7. Trash can ──
        drawTrashCan(graphics, mouseX, mouseY);

        // ── 8. Junimo Note icon (SDV parity: InventoryPage junimoNoteIcon) ──
        if (shouldShowJunimoNoteIcon()) {
            drawJunimoNoteIcon(graphics, mouseX, mouseY);
        }
    }

    // ------------- Junimo Note icon (SDV parity) -------------

    /**
     * SDV InventoryPage.ShouldShowJunimoNoteIcon():
     *   canReadJunimoText && !JojaMember && !MasterPlayer.hasCompletedCommunityCenter()
     * Client approximation: use BundleClientData for canReadJunimoText and per-area
     * completion (which mirrors the server's SavedData via BundleSyncPayload), and
     * ClientPlayerDataCache.hasMailFlag for JojaMember.
     */
    private boolean shouldShowJunimoNoteIcon() {
        com.stardew.craft.communitycenter.network.BundleClientData cd =
                com.stardew.craft.communitycenter.network.BundleClientData.INSTANCE;
        if (!cd.canReadJunimoText()) return false;
        if (ClientPlayerDataCache.hasMailFlag("JojaMember")) return false;
        // Consider CC complete only if every area (0..5) is marked complete on the client cache.
        boolean allComplete = true;
        for (int area = 0; area < 6; area++) {
            if (!cd.isAreaComplete(area)) { allComplete = false; break; }
        }
        return !allComplete;
    }

    private int junimoIconX() {
        return menuX + menuWidth;
    }

    private int junimoIconY() {
        return menuY + ui(96);
    }

    private int junimoIconHoverSize() {
        // SDV hover box = 64 screen px, at our scaling
        return ui(64);
    }

    private boolean junimoIconContains(double mouseX, double mouseY) {
        int x = junimoIconX();
        int y = junimoIconY();
        int size = junimoIconHoverSize();
        return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
    }

    private void drawJunimoNoteIcon(GuiGraphics graphics, int mouseX, int mouseY) {
        // SDV pulse animation: when hovered, pulser accumulates ms → scale = base + sin(pulser/100)/4
        boolean hovered = junimoIconContains(mouseX, mouseY);
        long now = System.currentTimeMillis();
        long dt = (junimoIconLastTickMs == 0L) ? 0L : (now - junimoIconLastTickMs);
        junimoIconLastTickMs = now;
        if (hovered) {
            junimoNotePulser += (int) dt;
        } else {
            junimoNotePulser = 0;
        }

        // SDV base sprite is 15×14 at ×4 = 60×56 screen px, centered inside 64 hover slot.
        int baseW = ui(JUNIMO_ICON_SRC_W * 4);
        int baseH = ui(JUNIMO_ICON_SRC_H * 4);
        float scale = hovered ? (1.0f + (float) Math.sin(junimoNotePulser / 100.0f) / 4.0f) : 1.0f;

        int hoverSize = junimoIconHoverSize();
        float cx = junimoIconX() + hoverSize / 2.0f;
        float cy = junimoIconY() + hoverSize / 2.0f;

        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-baseW / 2.0f, -baseH / 2.0f, 0);
        graphics.blit(JUNIMO_NOTE_ICON, 0, 0, 0, 0, baseW, baseH, baseW, baseH);
        graphics.pose().popPose();
    }

    // ------------- Inventory grid (top section) -------------

    private void drawInvPageGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.player == null) return;

        // 3 main rows (MC slots 9-35)
        for (int row = 0; row < 3; row++) {
            int rowY = menuY + ui(INV_PAGE_ROW0_Y + row * INV_PAGE_ROW_STEP);
            for (int col = 0; col < INVENTORY_COLS; col++) {
                int invIndex = col + row * 9 + 9;
                int x = invPageSlotX(col);
                boolean hovered = mouseX >= x && mouseX < x + ui(INVENTORY_SLOT_SDV)
                        && mouseY >= rowY && mouseY < rowY + ui(INVENTORY_SLOT_SDV);
                drawInventorySlot(graphics, x, rowY,
                        mc.player.getInventory().getItem(invIndex), hovered, invIndex);
            }
        }

        // Hotbar row (MC slots 0-8, with extra gap)
        int hotbarY = menuY + ui(INV_PAGE_HOTBAR_Y);
        for (int col = 0; col < INVENTORY_COLS; col++) {
            int x = invPageSlotX(col);
            boolean hovered = mouseX >= x && mouseX < x + ui(INVENTORY_SLOT_SDV)
                    && mouseY >= hotbarY && mouseY < hotbarY + ui(INVENTORY_SLOT_SDV);
            drawInventorySlot(graphics, x, hotbarY,
                    mc.player.getInventory().getItem(col), hovered, col);
        }
    }

    private int invPageSlotX(int col) {
        int startX = menuX + (menuWidth - inventoryGridWidth()) / 2;
        return startX + col * ui(INVENTORY_SLOT_SDV + INVENTORY_GAP_SDV);
    }

    private int invPageHoveredSlot(double mouseX, double mouseY) {
        for (int row = 0; row < 3; row++) {
            int rowY = menuY + ui(INV_PAGE_ROW0_Y + row * INV_PAGE_ROW_STEP);
            for (int col = 0; col < INVENTORY_COLS; col++) {
                int x = invPageSlotX(col);
                if (mouseX >= x && mouseX < x + ui(INVENTORY_SLOT_SDV)
                        && mouseY >= rowY && mouseY < rowY + ui(INVENTORY_SLOT_SDV)) {
                    return col + row * 9 + 9;
                }
            }
        }
        int hotbarY = menuY + ui(INV_PAGE_HOTBAR_Y);
        for (int col = 0; col < INVENTORY_COLS; col++) {
            int x = invPageSlotX(col);
            if (mouseX >= x && mouseX < x + ui(INVENTORY_SLOT_SDV)
                    && mouseY >= hotbarY && mouseY < hotbarY + ui(INVENTORY_SLOT_SDV)) {
                return col;
            }
        }
        return -1;
    }

    // ------------- Equipment slots (lower-left) -------------

    private int invEquipSlotY(int index) {
        return menuY + ui(index == 0 ? INV_PAGE_EQUIP_Y0
                : index == 1 ? INV_PAGE_EQUIP_Y1 : INV_PAGE_EQUIP_Y2);
    }

    private int invTrinketSlotX() {
        return menuX + ui(INV_PAGE_TRINKET_X);
    }

    private int invTrinketSlotY() {
        return menuY + ui(INV_PAGE_TRINKET_Y);
    }

    private void drawInvEquipSlot(GuiGraphics graphics, int index, ItemStack stack,
                                   int mouseX, int mouseY) {
        int x = menuX + ui(INV_PAGE_EQUIP_X);
        int y = invEquipSlotY(index);
        int size = ui(INV_PAGE_EQUIP_SIZE);
        boolean hasItem = stack != null && !stack.isEmpty();
        boolean hovered = mouseX >= x && mouseX < x + size
                && mouseY >= y && mouseY < y + size;

        if (hasItem) {
            // SDV: filled slot uses tile 10 + item drawn on top
            CommonGuiTextures.drawMenuTile(graphics, x, y, size, size, 10);
            if (stack != null && !stack.isEmpty()) {
                CommonGuiTextures.drawItemCenteredInBox(graphics, stack, x, y, size, size, mapping.s4());
            }
        } else {
            // SDV: empty ring → tile 41, empty boots → tile 40
            int placeholderTile = (index <= 1) ? EMPTY_RING_TILE : EMPTY_BOOTS_TILE;
            CommonGuiTextures.drawMenuTile(graphics, x, y, size, size, placeholderTile);
        }

        if (hovered) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 100);
            graphics.fill(x, y, x + size, y + size, 0x35FFFFFF);
            graphics.pose().popPose();
        }
    }

    private void drawInvTrinketSlot(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        int x = invTrinketSlotX();
        int y = invTrinketSlotY();
        int size = ui(INV_PAGE_EQUIP_SIZE);
        boolean hovered = mouseX >= x && mouseX < x + size
                && mouseY >= y && mouseY < y + size;

        if (!stack.isEmpty()) {
            CommonGuiTextures.drawMenuTile(graphics, x, y, size, size, 10);
            CommonGuiTextures.drawItemCenteredInBox(graphics, stack, x, y, size, size, mapping.s4());
        } else {
            CommonGuiTextures.drawMenuTile(graphics, x, y, size, size, EMPTY_TRINKET_TILE);
        }

        if (hovered) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 100);
            graphics.fill(x, y, x + size, y + size, 0x35FFFFFF);
            graphics.pose().popPose();
        }
    }

    private void drawInvCosmeticSlot(GuiGraphics graphics, int slotType, String itemId,
                                     int mouseX, int mouseY) {
        int x = menuX + ui(INV_PAGE_COSMETIC_X);
        int y = invEquipSlotY(slotType - 4);
        int size = ui(INV_PAGE_EQUIP_SIZE);
        boolean hasItem = !itemId.isEmpty();
        boolean hovered = mouseX >= x && mouseX < x + size
                && mouseY >= y && mouseY < y + size;

        if (hasItem) {
            CommonGuiTextures.drawMenuTile(graphics, x, y, size, size, 10);
            ItemStack stack = stackFromEquipmentId(itemId);
            if (!stack.isEmpty()) {
                CommonGuiTextures.drawItemCenteredInBox(graphics, stack, x, y, size, size, mapping.s4());
            }
        } else {
            // This project's extracted menu_tiles atlas doesn't contain SDV's
            // clothing placeholder tiles 42/68/69. Tile 70 is the safe local slot.
            CommonGuiTextures.drawMenuTile(graphics, x, y, size, size, EMPTY_TRINKET_TILE);
        }

        if (hovered) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 100);
            graphics.fill(x, y, x + size, y + size, 0x35FFFFFF);
            graphics.pose().popPose();
        }
    }

    private int invPageHoveredEquip(double mouseX, double mouseY) {
        int size = ui(INV_PAGE_EQUIP_SIZE);
        int x = menuX + ui(INV_PAGE_EQUIP_X);
        for (int i = 0; i < 3; i++) {
            int y = invEquipSlotY(i);
            if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
                return i;
            }
        }
        if (ClientPlayerDataCache.getUnlockedTrinketSlots() > 0) {
            int trinketX = invTrinketSlotX();
            int trinketY = invTrinketSlotY();
            if (mouseX >= trinketX && mouseX < trinketX + size && mouseY >= trinketY && mouseY < trinketY + size) {
                return 3;
            }
        }
        int cosmeticX = menuX + ui(INV_PAGE_COSMETIC_X);
        for (int i = 0; i < 3; i++) {
            int y = invEquipSlotY(i);
            if (mouseX >= cosmeticX && mouseX < cosmeticX + size && mouseY >= y && mouseY < y + size) {
                return 4 + i;
            }
        }
        return -1;
    }

    // ------------- Player model area (lower-center) -------------

    private void drawPlayerModelArea(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.player == null) return;

        int bgX = menuX + ui(INV_PAGE_PLAYER_BG_X);
        int bgY = menuY + ui(INV_PAGE_PLAYER_BG_Y);
        int bgW = ui(PLAYER_BG_TEX_W);
        int bgH = ui(PLAYER_BG_TEX_H);

        // SDV: Game1.timeOfDay >= 1900 → nightbg, else daybg
        long dayTime = mc.level != null ? mc.level.getDayTime() % 24000 : 0;
        boolean isNight = dayTime >= 13000;
        ResourceLocation bgTex = isNight ? NIGHTBG : DAYBG;
        graphics.blit(bgTex, bgX, bgY, 0, 0, bgW, bgH, bgW, bgH);

        // Render MC player entity inside the background frame
        int margin = ui(8);
        net.minecraft.client.gui.screens.inventory.InventoryScreen
                .renderEntityInInventoryFollowsMouse(
                        graphics,
                        bgX + margin, bgY + margin,
                        bgX + bgW - margin, bgY + bgH - margin,
                        ui(PLAYER_MODEL_SCALE_SDV), 0.0625F,
                        (float) mouseX, (float) mouseY,
                        mc.player
                );

        // SDV: player name centered below
        String playerName = ClientPlayerDataCache.getPlayerDisplayName(mc.player.getName().getString());
        Component name = Component.literal(playerName);
        float nameScale = sdvTextScale();
        int nameRawW = this.font.width(name);
        float nameEffScale = nameScale;
        if (nameRawW * nameEffScale > bgW) {
            nameEffScale = (float) bgW / nameRawW;
        }
        int nameScaledW = Math.round(nameRawW * nameEffScale);
        int nameX = bgX + (bgW - nameScaledW) / 2;
        int nameY = menuY + ui(INV_PAGE_PLAYER_NAME_Y);
        graphics.pose().pushPose();
        graphics.pose().translate(nameX, nameY, 0);
        graphics.pose().scale(nameEffScale, nameEffScale, 1.0f);
        graphics.drawString(this.font, name, 0, 0, SDV_TEXT_COLOR, false);
        graphics.pose().popPose();
    }

    // ------------- Right info panel (lower-right) -------------

    /** Compute text scale that makes MC font (9px) match SDV SpriteText proportions. */
    private float sdvTextScale() {
        return Math.max(1.0f, (float) ui(24) / 9.0f);
    }

    private void drawInfoPanel(GuiGraphics graphics) {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.player == null) return;

        int centerX = menuX + ui(INV_PAGE_INFO_CENTER_X);
        float textScale = sdvTextScale();
        // Available width: from player model right edge to menu right border
        int maxHalfWidth = menuX + menuWidth - ui(BORDER_WIDTH) - centerX;
        int maxWidth = maxHalfWidth * 2;

        // SDV: "{farmName} Farm"
        String rawFarmName = ClientPlayerDataCache.getFarmName();
        if (rawFarmName == null || rawFarmName.isBlank()) {
            rawFarmName = ClientPlayerDataCache.getPlayerDisplayName(mc.player.getName().getString());
        }
        String farmName = formatFarmName(rawFarmName);
        drawScaledCenteredSdvText(graphics, farmName, centerX,
                menuY + ui(INV_PAGE_INFO_Y0), textScale, maxWidth, SDV_TEXT_COLOR);

        // SDV: "Current Funds: {amount}g"
        int money = ClientPlayerDataCache.getMoney();
        String fundsStr = net.minecraft.client.resources.language.I18n.get(
                "stardewcraft.game_menu.inventory.current_funds",
                String.format("%,d", money));
        drawScaledCenteredSdvText(graphics, fundsStr, centerX,
                menuY + ui(INV_PAGE_INFO_Y1), textScale, maxWidth, SDV_TEXT_COLOR);

        // SDV: date string
        com.stardew.craft.time.StardewTimeManager time =
                com.stardew.craft.client.hud.StardewTimeHud.getClientTimeCache();
        String rawSeasonName = time.getSeasonName();
        String seasonKey = "stardewcraft.season." + rawSeasonName.toLowerCase(Locale.ROOT);
        String localizedSeasonName = net.minecraft.client.resources.language.I18n.get(seasonKey);
        if (localizedSeasonName.equals(seasonKey)) {
            localizedSeasonName = rawSeasonName;
        }
        String dateStr = net.minecraft.client.resources.language.I18n.get(
                "stardewcraft.game_menu.inventory.date",
                localizedSeasonName, time.getCurrentDay(), time.getCurrentYear());
        drawScaledCenteredSdvText(graphics, dateStr, centerX,
                menuY + ui(INV_PAGE_INFO_Y2), textScale, maxWidth, SDV_TEXT_COLOR_DIM);
    }

    private String formatFarmName(String rawFarmName) {
        String formatSuffix = net.minecraft.client.resources.language.I18n.get(
                "stardewcraft.game_menu.inventory.farm_name", "").trim();
        if (!formatSuffix.isEmpty()
                && rawFarmName.toLowerCase(Locale.ROOT).endsWith(formatSuffix.toLowerCase(Locale.ROOT))) {
            return rawFarmName;
        }
        return net.minecraft.client.resources.language.I18n.get(
                "stardewcraft.game_menu.inventory.farm_name", rawFarmName);
    }

    /**
     * Draw text centered at {@code centerX}, scaled up to SDV proportions.
     * Auto-shrinks if the text would exceed {@code maxWidth} pixels.
     */
    private void drawScaledCenteredSdvText(GuiGraphics graphics, String text,
                                            int centerX, int y, float scale,
                                            int maxWidth, int color) {
        Component line = Component.literal(text);
        int rawWidth = this.font.width(line);
        float effectiveScale = scale;
        if (rawWidth * effectiveScale > maxWidth) {
            effectiveScale = (float) maxWidth / rawWidth;
        }
        int scaledWidth = Math.round(rawWidth * effectiveScale);
        int x = centerX - scaledWidth / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(effectiveScale, effectiveScale, 1.0f);
        graphics.drawString(this.font, line, 0, 0, color, false);
        graphics.pose().popPose();
    }

    // ------------- Organize button -------------

    private int organizeButtonX() {
        return menuX + menuWidth;
    }

    private int organizeButtonY() {
        // Stardew InventoryPage: yPositionOnScreen + height / 3 - 64 + 8.
        return menuY + menuHeight / 3 - ui(64) + ui(8);
    }

    private void drawOrganizeButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = organizeButtonX();
        int y = organizeButtonY();
        // SDV: ClickableTextureComponent draws at scale 4, icon 16×16
        CommonGuiTextures.drawGameMenuOrganize(graphics, x, y, mapping.s4());
    }

    private boolean organizeButtonContains(double mouseX, double mouseY) {
        int x = organizeButtonX();
        int y = organizeButtonY();
        int size = ui(64); // 16 * 4 = 64 SDV pixels
        return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
    }

    // ------------- Inventory Page Tooltips -------------

    private void drawInventoryPageTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        // Junimo Note icon tooltip (SDV: Strings\UI:GameMenu_JunimoNote_Hover — "Community Center")
        if (shouldShowJunimoNoteIcon() && junimoIconContains(mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    Component.translatable("stardewcraft.game_menu.junimo_note.hover"),
                    mouseX, mouseY);
            return;
        }
        // Equipment slot tooltips
        int equipSlot = invPageHoveredEquip(mouseX, mouseY);
        if (equipSlot >= 0) {
            if (equipSlot == 3) {
                ItemStack trinket = ClientPlayerDataCache.getEquippedTrinket();
                if (!trinket.isEmpty()) {
                    graphics.renderTooltip(this.font, trinket, mouseX, mouseY);
                    return;
                }
                graphics.renderTooltip(this.font,
                        Component.translatable("stardewcraft.equipment.slot.trinket"),
                        mouseX, mouseY);
                return;
            }
            ItemStack equippedStack = switch (equipSlot) {
                case 0 -> ClientPlayerDataCache.getEquippedLeftRingStack();
                case 1 -> ClientPlayerDataCache.getEquippedRightRingStack();
                case 2 -> ClientPlayerDataCache.getEquippedBootsStack();
                default -> ItemStack.EMPTY;
            };
            if (!equippedStack.isEmpty()) {
                graphics.renderTooltip(this.font, equippedStack, mouseX, mouseY);
                return;
            }
            String itemId = switch (equipSlot) {
                case 4 -> ClientPlayerDataCache.getEquippedHat();
                case 5 -> ClientPlayerDataCache.getEquippedShirt();
                case 6 -> ClientPlayerDataCache.getEquippedPants();
                default -> "";
            };
            if (!itemId.isEmpty()) {
                ItemStack stack = stackFromEquipmentId(itemId);
                if (!stack.isEmpty()) {
                    graphics.renderTooltip(this.font, stack, mouseX, mouseY);
                    return;
                }
            }
            Component label = switch (equipSlot) {
                case 0 -> Component.translatable("stardewcraft.equipment.slot.left_ring");
                case 1 -> Component.translatable("stardewcraft.equipment.slot.right_ring");
                case 2 -> Component.translatable("stardewcraft.equipment.slot.boots");
                case 4 -> Component.translatable("stardewcraft.equipment.slot.hat");
                case 5 -> Component.translatable("stardewcraft.equipment.slot.shirt");
                case 6 -> Component.translatable("stardewcraft.equipment.slot.pants");
                default -> Component.empty();
            };
            graphics.renderTooltip(this.font, label, mouseX, mouseY);
            return;
        }

        // Inventory item tooltips (use invPage positions)
        int invSlot = invPageHoveredSlot(mouseX, mouseY);
        if (invSlot >= 0 && this.minecraft != null && this.minecraft.player != null) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(invSlot);
            if (!stack.isEmpty()) {
                graphics.renderTooltip(this.font, stack, mouseX, mouseY);
                return;
            }
        }

        // Organize button tooltip
        if (organizeButtonContains(mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    Component.translatable("stardewcraft.game_menu.inventory.organize"),
                    mouseX, mouseY);
            return;
        }

        // Trash can tooltip
        if (trashCanContains(mouseX, mouseY)) {
            ItemStack carried = currentCarriedItem();
            if (!carried.isEmpty()) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("stardewcraft.game_menu.crafting.trash_can").withStyle(ChatFormatting.WHITE));
                lines.add(carried.getHoverName().copy().withStyle(ChatFormatting.GRAY));
                graphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
            graphics.renderTooltip(this.font,
                    Component.translatable("stardewcraft.game_menu.crafting.trash_can"),
                    mouseX, mouseY);
        }
    }

    private void drawSocialPage(GuiGraphics graphics, int mouseX, int mouseY) {
        List<NpcFriendshipClientCache.Entry> all = visibleSocialEntries();
        int total = all.size();
        socialScroll = Mth.clamp(socialScroll, 0, socialMaxScroll(total));

        // Exact vanilla separator style for SocialPage: small horizontal + small vertical partitions.
        for (int i = 0; i < 4; i++) {
            StardewGuiUtil.drawHorizontalPartitionSmall(
                graphics,
                socialTuneHorizontalLineX(i),
                socialTuneHorizontalLineY(i),
                socialPageWidth(),
                mapping.s4()
            );
        }

        drawSocialVerticalPartitions(graphics);

        if (!all.isEmpty()) {
            int toIndex = Math.min(total, socialScroll + SOCIAL_MAX_VISIBLE);
            for (int i = socialScroll; i < toIndex; i++) {
                int visibleRow = i - socialScroll;
                drawSocialRow(graphics, all.get(i), visibleRow);
            }
        }

        drawSocialScrollControls(graphics, total);
    }

    private int socialVerticalPartitionClipTopY() {
        // Vanilla rowPosition(numFarmers - 1) resolves to y + 92 when no farmers are listed.
        return Math.max(0, menuY + ui(92));
    }

    private void drawSocialVerticalPartitions(GuiGraphics graphics) {
        int clipTop = socialVerticalPartitionClipTopY();
        int clipBottom = Math.min(this.height, menuY + menuHeight);
        if (clipBottom <= clipTop) {
            return;
        }

        graphics.enableScissor(0, clipTop, this.width, clipBottom);
        try {
            StardewGuiUtil.drawVerticalPartitionSmall(graphics, socialVerticalLeftX(), menuY, menuHeight, mapping.s4());
            StardewGuiUtil.drawVerticalPartitionSmall(graphics, socialVerticalMiddleX(), menuY, menuHeight, mapping.s4());
            StardewGuiUtil.drawVerticalPartitionSmall(graphics, socialVerticalRightX(), menuY, menuHeight, mapping.s4());
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawSocialRow(GuiGraphics graphics,
                               NpcFriendshipClientCache.Entry entry,
                               int visibleRow) {
        int y = socialRowPosition(visibleRow);
        boolean datable = isDatableNpc(entry.npcId());

        String name = socialDisplayName(entry);
        drawSocialPortrait(graphics, entry.npcId(), socialPortraitX(), y);
        // Name centered horizontally between portrait right edge and left partition line
        int nameX = socialNameCenterX() - this.font.width(name) / 2;
        int nameY = y + ui(datable ? 24 : 28);
        graphics.drawString(this.font, name, nameX, nameY, 0xFF7B5A2F, false);

        drawSocialHearts(graphics, entry, y);
        drawSocialGiftAndTalkMarkers(graphics, entry, y);
    }

    private int socialLeftColumnStartX() {
        return menuX + ui(BORDER_WIDTH);
    }

    private int socialLeftColumnEndX() {
        return socialVerticalLeftX();
    }

    private int socialHeartsColumnStartX() {
        return socialVerticalLeftX() + ui(64);
    }

    private int socialHeartsColumnEndX() {
        return socialVerticalMiddleX();
    }

    private int socialGiftColumnStartX() {
        return socialVerticalMiddleX() + ui(64);
    }

    private int socialGiftColumnEndX() {
        return socialVerticalRightX();
    }

    private int socialTalkColumnStartX() {
        return socialVerticalRightX() + ui(64);
    }

    private int socialTalkColumnEndX() {
        return socialPageRightX() - ui(BORDER_WIDTH);
    }

    private int socialRowPosition(int index) {
        return menuY + ui(64 + index * SOCIAL_ROW_HEIGHT_SDV);
    }

    private int socialContentTopY() {
        return socialVerticalStartY();
    }

    private void drawSocialHearts(GuiGraphics graphics, NpcFriendshipClientCache.Entry entry, int rowY) {
        int points = Math.max(0, entry.points());
        int fullHearts = Math.max(0, Math.min(14, points / 250));
        int maxHearts = Math.max(10, fullHearts);
        boolean datableLocked = isDatableHeartLocked(entry);
        for (int hearts = 0; hearts < maxHearts; hearts++) {
            boolean isLockedHeart = datableLocked && hearts >= 8;
            boolean filled = hearts < fullHearts || isLockedHeart;
            int drawX;
            int drawY;
            if (hearts < 10) {
                drawX = socialHeartsBaseX() + ui(hearts * 32);
                drawY = rowY + socialHeartsTopRowOffsetY();
            } else {
                drawX = socialHeartsBaseX() + ui((hearts - 10) * 32);
                drawY = rowY + socialHeartsBottomRowOffsetY();
            }
            if (isLockedHeart && hearts < 10) {
                CommonGuiTextures.drawSocialHeartTint(graphics, drawX, drawY, filled, mapping.s4(), 0.0f, 0.0f, 0.0f, 0.35f);
            } else {
                CommonGuiTextures.drawSocialHeartTint(graphics, drawX, drawY, filled, mapping.s4(), 1.0f, 1.0f, 1.0f, 0.88f);
            }
        }

        drawSocialHeartPartialFill(graphics, rowY, points, fullHearts, maxHearts);
    }

    private void drawSocialHeartPartialFill(GuiGraphics graphics, int rowY, int points, int fullHearts, int maxHearts) {
        if (points <= 0 || fullHearts >= maxHearts) {
            return;
        }

        int pointsToNextHeart = points % 250;
        if (pointsToNextHeart <= 0) {
            return;
        }

        int activeHeart = fullHearts;
        int drawX;
        int drawY;
        if (activeHeart < 10) {
            drawX = socialHeartsBaseX() + ui(activeHeart * 32);
            drawY = rowY + socialHeartsTopRowOffsetY();
        } else {
            drawX = socialHeartsBaseX() + ui((activeHeart - 10) * 32);
            drawY = rowY + socialHeartsBottomRowOffsetY();
        }

        int filledCells = Math.max(0, Math.min(20, (int) (pointsToNextHeart / 12.5f)));
        if (filledCells <= 0) {
            return;
        }

        int cell = Math.max(1, ui(4));
        int offsetX = ui(2);
        int offsetY = ui(2);
        int remaining = filledCells;

        for (int row = 3; row >= 0 && remaining > 0; row--) {
            for (int col = 0; col < 5 && remaining > 0; col++) {
                if (SOCIAL_HEART_FILL_PATTERN[row][col] != 1) {
                    continue;
                }
                int x = drawX + offsetX + col * cell;
                int y = drawY + offsetY + row * cell;
                graphics.fill(x, y, x + cell, y + cell, 0xFFDC143C);
                remaining--;
            }
        }
    }

    private void drawSocialGiftAndTalkMarkers(GuiGraphics graphics, NpcFriendshipClientCache.Entry entry, int rowY) {
        CommonGuiTextures.drawSocialGiftIcon(graphics, socialGiftIconX(), rowY + socialGiftIconOffsetY(), mapping.s4(), 0.88f);
        CommonGuiTextures.drawSocialBox(graphics, socialGiftSecondBoxX(), rowY + socialGiftBoxesOffsetY(), entry.giftsThisWeek() >= 2, mapping.s4(), 0.88f);
        CommonGuiTextures.drawSocialBox(graphics, socialGiftFirstBoxX(), rowY + socialGiftBoxesOffsetY(), entry.giftsThisWeek() >= 1, mapping.s4(), 0.88f);

        CommonGuiTextures.drawSocialTalkIcon(graphics, socialTalkIconX(), rowY + socialTalkIconOffsetY(), mapping.s4(), 0.88f);
        CommonGuiTextures.drawSocialBox(graphics, socialTalkBoxX(), rowY + socialTalkBoxOffsetY(), entry.talkedToday(), mapping.s4(), 0.88f);
    }

    // ─── Vanilla-derived absolute positions (relative to menuX/menuY) ───
    // Match original SocialPage column cuts directly: 268, 620, 752.
    private int socialPortraitX() { return menuX + ui(BORDER_WIDTH + 4); }

    private int socialNameCenterX() { return menuX + ui(188); }

    private int socialVerticalLeftX() { return socialTuneVerticalLineX(SOCIAL_TUNE_TARGET_VLINE_LEFT); }
    private int socialVerticalMiddleX() { return socialTuneVerticalLineX(SOCIAL_TUNE_TARGET_VLINE_MIDDLE); }
    private int socialVerticalRightX() { return socialTuneVerticalLineX(SOCIAL_TUNE_TARGET_VLINE_RIGHT); }

    private int socialHeartsBaseX() { return menuX + ui(316); }

    private int socialHeartsTopRowOffsetY() { return ui(36); }
    private int socialHeartsBottomRowOffsetY() { return ui(64); }

    private int socialGiftIconX() { return menuX + ui(688); }

    private int socialGiftIconOffsetY() { return -ui(4); }

    private int socialGiftFirstBoxX() { return menuX + ui(680); }

    private int socialGiftSecondBoxX() { return menuX + ui(720); }

    private int socialGiftBoxesOffsetY() { return ui(52); }

    private int socialTalkIconX() { return menuX + ui(808); }

    private int socialTalkIconOffsetY() { return 0; }

    private int socialTalkBoxX() { return menuX + ui(816); }

    private int socialTalkBoxOffsetY() { return ui(52); }

    private int socialVerticalStartY() { return menuY + ui(BORDER_WIDTH + 100); }

    private int socialContentLeftX() {
        return menuX + ui(BORDER_WIDTH);
    }

    private int socialContentRightX() {
        return socialPageRightX() - ui(BORDER_WIDTH);
    }

    private int socialContentWidth() {
        return Math.max(1, socialContentRightX() - socialContentLeftX());
    }

    private void drawSocialScrollControls(GuiGraphics graphics, int total) {
        int upX = socialUpButtonX();
        int upY = socialUpButtonY();
        int downX = socialDownButtonX();
        int downY = socialDownButtonY();
        int maxScroll = socialMaxScroll(total);

        if (socialScroll > 0) {
            drawArrowFromCursors(graphics, upX, upY,
                    socialArrowBoundWidth(), socialArrowBoundHeight(), 12, 0.8f);
        }
        if (socialScroll < maxScroll) {
            drawArrowFromCursors(graphics, downX, downY,
                    socialArrowBoundWidth(), socialArrowBoundHeight(), 11, 0.8f);
        }

        CommonGuiTextures.drawScrollTrackBox(
            graphics,
            socialScrollRunnerX(),
            socialScrollRunnerY(),
            socialScrollRunnerWidth(),
            socialScrollRunnerHeight(),
            mapping.s4()
        );
        CommonGuiTextures.drawScrollBarThumb(graphics, socialScrollBarX(), currentSocialScrollBarY(total), mapping.s4());
    }

    private int socialMaxScroll(int total) {
        return Math.max(0, total - SOCIAL_MAX_VISIBLE);
    }

    private int socialUpButtonX() {
        return socialPageRightX() + ui(16);
    }

    private int socialUpButtonY() {
        return menuY + ui(64);
    }

    private int socialDownButtonX() {
        return socialUpButtonX();
    }

    private int socialDownButtonY() {
        return menuY + menuHeight - ui(64);
    }

    private int socialArrowBoundWidth() {
        return ui(44);
    }

    private int socialArrowBoundHeight() {
        return ui(48);
    }

    private int socialScrollBarX() {
        return socialUpButtonX() + ui(12);
    }

    private int socialScrollBarY() {
        return socialUpButtonY() + socialArrowBoundHeight() + ui(4);
    }

    private int socialScrollBarWidth() {
        return ui(24);
    }

    private int socialScrollBarHeight() {
        return ui(40);
    }

    private int socialScrollRunnerX() {
        return socialScrollBarX();
    }

    private int socialScrollRunnerY() {
        return socialScrollBarY();
    }

    private int socialScrollRunnerWidth() {
        return socialScrollBarWidth();
    }

    private int socialScrollRunnerHeight() {
        return menuHeight - ui(128) - socialArrowBoundHeight() - ui(8);
    }

    private int currentSocialScrollBarY(int total) {
        if (total <= 0) {
            return socialScrollRunnerY();
        }
        int maxScroll = socialMaxScroll(total);
        int y = socialScrollRunnerHeight() / Math.max(1, total - SOCIAL_MAX_VISIBLE + 1) * socialScroll + socialUpButtonY() + socialArrowBoundHeight() + ui(4);
        if (maxScroll > 0 && socialScroll == maxScroll) {
            y = socialDownButtonY() - socialScrollBarHeight() - ui(4);
        }
        return y;
    }

    private boolean socialUpButtonContains(double mouseX, double mouseY) {
        return mouseX >= socialUpButtonX() && mouseX < socialUpButtonX() + socialArrowBoundWidth()
            && mouseY >= socialUpButtonY() && mouseY < socialUpButtonY() + socialArrowBoundHeight();
    }

    private boolean socialDownButtonContains(double mouseX, double mouseY) {
        return mouseX >= socialDownButtonX() && mouseX < socialDownButtonX() + socialArrowBoundWidth()
            && mouseY >= socialDownButtonY() && mouseY < socialDownButtonY() + socialArrowBoundHeight();
    }

    private boolean socialScrollBarContains(double mouseX, double mouseY, int total) {
        int y = currentSocialScrollBarY(total);
        return mouseX >= socialScrollBarX() && mouseX < socialScrollBarX() + socialScrollBarWidth()
            && mouseY >= y && mouseY < y + socialScrollBarHeight();
    }

    private boolean socialScrollRunnerContains(double mouseX, double mouseY) {
        return mouseX > socialPageRightX() && mouseX < socialPageRightX() + ui(128)
            && mouseY > menuY && mouseY < menuY + menuHeight
            && !socialDownButtonContains(mouseX, mouseY);
    }

    private void setSocialScrollFromMouse(double mouseY, int total) {
        int maxScroll = socialMaxScroll(total);
        if (maxScroll <= 0) {
            socialScroll = 0;
            return;
        }
        int minY = menuY + ui(68);
        int maxY = menuY + menuHeight - ui(64) - ui(12) - socialScrollBarHeight();
        int clampedY = Mth.clamp((int) Math.round(mouseY), minY, maxY);
        float percentage = (clampedY - socialScrollRunnerY()) / (float) Math.max(1, socialScrollRunnerHeight());
        socialScroll = Mth.clamp((int) (total * percentage), 0, maxScroll);
    }

    private void drawSocialPortrait(GuiGraphics graphics, String npcId, int x, int y) {
        PortraitResource portrait = resolveSocialMugshot(npcId);
        graphics.blit(portrait.texture(), x, y, ui(64), ui(96), 0, 0, 16, 24, portrait.sheetWidth(), portrait.sheetHeight());
    }

    private PortraitResource resolveSocialMugshot(String npcId) {
        com.stardew.craft.api.v1.npc.StardewNpcDisplay display =
                com.stardew.craft.api.v1.npc.StardewNpcDisplays.resolve(npcId);
        ResourceLocation fallback = ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "textures/mugshots/lewis.png");
        ResourceLocation requested =
                display == null ? null : display.mugshotTexture();
        ResourceLocation resolved =
                com.stardew.craft.client.ClientDisplayFallbacks
                        .availableResource(requested, fallback, this::hasResource);
        return loadPortrait(
                resolved,
                resolved.equals(requested) ? display.mugshotSheetWidth() : 16,
                resolved.equals(requested) ? display.mugshotSheetHeight() : 24);
    }

    private List<NpcFriendshipClientCache.Entry> visibleSocialEntries() {
        List<NpcFriendshipClientCache.Entry> all = NpcFriendshipClientCache.entries();
        if (all.isEmpty()) {
            return all;
        }

        List<NpcFriendshipClientCache.Entry> filtered = new ArrayList<>(all.size());
        for (NpcFriendshipClientCache.Entry entry : all) {
            if (entry == null) {
                continue;
            }
            String npcId = normalizeNpcId(entry.npcId());
            if (hasSocialPortraitAndCharacter(npcId)) {
                filtered.add(entry);
            }
        }

        filtered.sort(Comparator
            .comparingInt(NpcFriendshipClientCache.Entry::points).reversed()
            .thenComparingInt(NpcFriendshipClientCache.Entry::metOrder)
            .thenComparing(entry -> socialDisplayName(entry).toLowerCase(Locale.ROOT))
            .thenComparing(NpcFriendshipClientCache.Entry::npcId));
        return filtered;
    }

    private boolean hasSocialPortraitAndCharacter(String normalizedNpcId) {
        com.stardew.craft.api.v1.npc.StardewNpcDisplay display =
                com.stardew.craft.api.v1.npc.StardewNpcDisplays.resolve(
                        normalizedNpcId);
        return display != null;
    }

    private String socialDisplayName(NpcFriendshipClientCache.Entry entry) {
        return entry.met() ? NpcDisplayNames.translated(entry.npcId()) : "???";
    }

    private boolean isDatableHeartLocked(NpcFriendshipClientCache.Entry entry) {
        return isDatableNpc(entry.npcId());
    }

    private boolean isDatableNpc(String npcId) {
        var definition =
                com.stardew.craft.api.v1.npc.StardewNpcProfiles.resolve(npcId)
                        .orElse(null);
        if (definition != null) {
            return definition.profile().datable();
        }
        com.stardew.craft.api.v1.npc.StardewNpcDisplay display =
                com.stardew.craft.api.v1.npc.StardewNpcDisplays.resolve(
                        npcId);
        return DATEABLE_NPCS.contains(normalizeNpcId(npcId))
                || display != null && display.datable();
    }

    private String normalizeNpcId(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return "lewis";
        }
        return npcId.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasResource(ResourceLocation location) {
        ResourceManager resourceManager = this.minecraft == null ? null : this.minecraft.getResourceManager();
        return resourceManager != null && resourceManager.getResource(location).isPresent();
    }

    private PortraitResource loadPortrait(ResourceLocation location, int fallbackW, int fallbackH) {
        PortraitResource cached = SOCIAL_PORTRAIT_CACHE.get(location);
        if (cached != null) {
            return cached;
        }

        ResourceManager resourceManager = this.minecraft == null ? null : this.minecraft.getResourceManager();
        if (resourceManager == null) {
            return new PortraitResource(location, fallbackW, fallbackH);
        }

        int width = fallbackW;
        int height = fallbackH;
        try {
            var resource = resourceManager.getResource(location).orElse(null);
            if (resource != null) {
                try (var stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
                    width = image.getWidth();
                    height = image.getHeight();
                }
            }
        } catch (IOException ignored) {
        }

        PortraitResource resolved = new PortraitResource(location, Math.max(1, width), Math.max(1, height));
        SOCIAL_PORTRAIT_CACHE.put(location, resolved);
        return resolved;
    }

    private record PortraitResource(ResourceLocation texture, int sheetWidth, int sheetHeight) {
    }

    private void rebuildCraftingEntries() {
        List<String> recipeIds = new ArrayList<>(RecipeCatalogData.getCraftingRecipeIds());
        recipeIds.sort(Comparator.comparingInt(StardewGameMenuScreen::vanillaCraftingDisplayRank));

        List<String> validIds = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (String recipeId : recipeIds) {
            if (!ClientPlayerDataCache.hasRecipe(recipeId)) {
                continue;
            }

            ItemStack stack = resolveRecipeResultStack(recipeId);
            if (stack.isEmpty()) {
                continue;
            }
            validIds.add(recipeId);
            stacks.add(stack);
        }

        this.craftingRecipeIds = validIds;
        this.craftingRecipeStacks = stacks;
        this.craftingPages = buildCraftingPages();

        if (craftingRecipeIds.isEmpty()) {
            selectedCraftingIndex = -1;
        } else if (selectedCraftingIndex < 0 || selectedCraftingIndex >= craftingRecipeIds.size()) {
            selectedCraftingIndex = 0;
        }

        clampCraftingPage();
    }

    private ItemStack resolveRecipeResultStack(String recipeId) {
        return StardewCraftingRecipeData.getOutputStack(recipeId);
    }

    private static Map<String, Integer> createVanillaCraftingDisplayRanks() {
        Map<String, Integer> ranks = new HashMap<>();
        for (int index = 0; index < VANILLA_CRAFTING_DISPLAY_ORDER.size(); index++) {
            ranks.put(VANILLA_CRAFTING_DISPLAY_ORDER.get(index), index);
        }
        return Map.copyOf(ranks);
    }

    private static int vanillaCraftingDisplayRank(String recipeId) {
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (recipeId.indexOf(':') >= 0 && (id == null || !StardewCraft.MODID.equals(id.getNamespace()))) {
            return Integer.MAX_VALUE;
        }
        String path = id == null ? recipeId : id.getPath();
        return VANILLA_CRAFTING_DISPLAY_RANKS.getOrDefault(path, Integer.MAX_VALUE);
    }

    private void clampCraftingPage() {
        int maxPage = Math.max(0, craftingPages.size() - 1);
        if (currentCraftingPage < 0) {
            currentCraftingPage = 0;
        }
        if (currentCraftingPage > maxPage) {
            currentCraftingPage = maxPage;
        }
    }

    private List<List<RecipeCell>> buildCraftingPages() {
        List<List<RecipeCell>> pages = new ArrayList<>();
        if (craftingRecipeIds.isEmpty()) {
            pages.add(List.of());
            return pages;
        }

        List<RecipeCell> page = new ArrayList<>();
        boolean[][] occupied = new boolean[CRAFTING_RECIPE_COLUMNS][CRAFTING_RECIPE_ROWS];
        pages.add(page);
        for (int recipeIndex = 0; recipeIndex < craftingRecipeIds.size(); recipeIndex++) {
            boolean bigCraftable = StardewCraftingRecipeData.isBigCraftable(craftingRecipeIds.get(recipeIndex));
            int[] position = firstFreeCraftingCell(occupied, bigCraftable);
            if (position == null) {
                page = new ArrayList<>();
                pages.add(page);
                occupied = new boolean[CRAFTING_RECIPE_COLUMNS][CRAFTING_RECIPE_ROWS];
                position = firstFreeCraftingCell(occupied, bigCraftable);
            }
            int x = position[0];
            int y = position[1];
            occupied[x][y] = true;
            if (bigCraftable) {
                occupied[x][y + 1] = true;
            }
            page.add(new RecipeCell(recipeIndex, x, y, bigCraftable));
        }

        return pages;
    }

    private static int[] firstFreeCraftingCell(boolean[][] occupied, boolean bigCraftable) {
        for (int y = 0; y < CRAFTING_RECIPE_ROWS; y++) {
            for (int x = 0; x < CRAFTING_RECIPE_COLUMNS; x++) {
                if (!occupied[x][y]
                        && (!bigCraftable || y + 1 < CRAFTING_RECIPE_ROWS && !occupied[x][y + 1])) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private void drawCraftingPage(GuiGraphics graphics, int mouseX, int mouseY) {
        StardewGuiUtil.drawHorizontalPartition(graphics, menuX, menuY + ui(CRAFTING_PARTITION_Y_SDV), menuWidth, mapping.s4());

        drawPlayerInventory(graphics, mouseX, mouseY);
        drawTrashCan(graphics, mouseX, mouseY);
        drawRecipeGrid(graphics, mouseX, mouseY);
    }

    private void drawRecipeGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        if (currentCraftingPage < 0 || currentCraftingPage >= craftingPages.size()) {
            return;
        }

        List<RecipeCell> page = craftingPages.get(currentCraftingPage);
        for (int local = 0; local < page.size(); local++) {
            RecipeCell cellData = page.get(local);
            int index = cellData.recipeIndex();
            if (index < 0 || index >= craftingRecipeIds.size()) {
                continue;
            }

            int x = recipeCellX(cellData);
            int y = recipeCellY(cellData);
            int w = recipeCellWidth();
            int h = recipeCellHeight(cellData);

            ItemStack stack = craftingRecipeStacks.get(index);
            String recipeId = craftingRecipeIds.get(index);
            boolean craftable = computeMaxCraftsClient(getRecipeIngredients(recipeId), 1) > 0;
            boolean hovered = recipeContains(mouseX, mouseY, x, y, w, h, ui(4))
                    || craftingKeyboardFocus && selectedCraftingIndex == index;

            if (local >= 0 && local < recipeHoverScale.length) {
                recipeHoverScale[local] = stepScale(recipeHoverScale[local], hovered ? 1.1f : 1.0f, 0.02f);
            }
            float scale = (local >= 0 && local < recipeHoverScale.length && recipeHoverScale[local] > 0.001f)
                    ? recipeHoverScale[local]
                    : 1.0f;

            Integer bigCraftableSprite = cellData.bigCraftable()
                    ? vanillaBigCraftableSprite(recipeId)
                    : null;
            float itemScale = mapping.s4() * (CRAFTING_RECIPE_ITEM_SDV / 64.0f) * scale;
            int itemSize = CommonGuiTextures.itemSize(itemScale);
            int itemX = x + (w - itemSize) / 2;
            int itemY = y + (h - itemSize) / 2;
            if (bigCraftableSprite != null) {
                drawVanillaBigCraftable(graphics, bigCraftableSprite, x, y, w, h, scale, craftable);
                itemX = x;
                itemY = y;
                itemScale = mapping.s4();
            } else if (!craftable) {
                CommonGuiTextures.drawItemTint(graphics, stack, itemX, itemY, itemScale, 0.41F, 0.41F, 0.41F, 0.4F);
            } else {
                CommonGuiTextures.drawItem(graphics, stack, itemX, itemY, itemScale);
            }

            if (hasShiftDown()) {
                int maxCrafts = computeMaxCraftsClient(getRecipeIngredients(recipeId), 999);
                if (maxCrafts > 0) {
                    ItemStack countStack = stack.copy();
                    countStack.setCount(maxCrafts);
                    CommonGuiTextures.drawItemDecorations(graphics, this.font, countStack, itemX, itemY, itemScale);
                } else if (stack.getCount() > 1) {
                    CommonGuiTextures.drawItemDecorations(graphics, this.font, stack, itemX, itemY, itemScale);
                }
            } else if (stack.getCount() > 1) {
                CommonGuiTextures.drawItemDecorations(graphics, this.font, stack, itemX, itemY, itemScale);
            }
        }
    }

    private static Integer vanillaBigCraftableSprite(String recipeId) {
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        String path = id == null ? recipeId : id.getPath();
        return VANILLA_BIG_CRAFTABLE_SPRITES.get(path);
    }

    private void drawVanillaBigCraftable(GuiGraphics graphics, int spriteIndex,
                                         int cellX, int cellY, int cellWidth, int cellHeight,
                                         float hoverScale, boolean craftable) {
        int drawWidth = Math.round(ui(64) * hoverScale);
        int drawHeight = Math.round(ui(128) * hoverScale);
        int drawX = cellX + (cellWidth - drawWidth) / 2;
        int drawY = cellY + (cellHeight - drawHeight) / 2;
        int sourceX = spriteIndex % VANILLA_BIG_CRAFTABLE_COLUMNS * 16;
        int sourceY = spriteIndex / VANILLA_BIG_CRAFTABLE_COLUMNS * 32;

        if (!craftable) {
            RenderSystem.setShaderColor(0.41F, 0.41F, 0.41F, 0.4F);
        }
        graphics.blit(VANILLA_BIG_CRAFTABLES, drawX, drawY, drawWidth, drawHeight,
                sourceX, sourceY, 16, 32,
                VANILLA_BIG_CRAFTABLES_WIDTH, VANILLA_BIG_CRAFTABLES_HEIGHT);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // drawMCStyleCount removed

    private void drawPageArrows(GuiGraphics graphics, int mouseX, int mouseY) {
        int upX = pageButtonX();
        int upY = pageUpButtonY();
        int downX = pageButtonX();
        int downY = pageDownButtonY();

        boolean upHovered = upButtonContains(mouseX, mouseY);
        boolean downHovered = downButtonContains(mouseX, mouseY);
        upButtonScale = stepScale(upButtonScale, upHovered ? 0.9f : 0.8f, 0.025f);
        downButtonScale = stepScale(downButtonScale, downHovered ? 0.9f : 0.8f, 0.025f);

        if (currentCraftingPage > 0) {
            drawArrowFromCursors(graphics, upX, upY, 12, upButtonScale);
        }
        if (currentCraftingPage < Math.max(0, craftingPages.size() - 1)) {
            drawArrowFromCursors(graphics, downX, downY, 11, downButtonScale);
        }
    }

    private void drawArrowFromCursors(GuiGraphics graphics, int boundX, int boundY, int tilePosition, float vanillaScale) {
        drawArrowFromCursors(graphics, boundX, boundY, ui(64), ui(64), tilePosition, vanillaScale);
    }

    private void drawArrowFromCursors(GuiGraphics graphics, int boundX, int boundY,
                                      int boundWidth, int boundHeight,
                                      int tilePosition, float vanillaScale) {
        float drawScale = mapping.s4() * vanillaScale;
        // 11 is down arrow, 12 is up arrow in standard Stardew logic (from Game1.mouseCursors)
        // Up arrow: u=421, v=459, w=11, h=12
        // Down arrow: u=421, v=472, w=11, h=12
        int w = 11;
        int h = 12;

        int offsetX = centeredArrowOffset(boundWidth, w, drawScale);
        int offsetY = centeredArrowOffset(boundHeight, h, drawScale);

        if (tilePosition == 12) {
            CommonGuiTextures.drawScrollArrowUp(graphics, boundX + offsetX, boundY + offsetY, drawScale);
        } else {
            CommonGuiTextures.drawScrollArrowDown(graphics, boundX + offsetX, boundY + offsetY, drawScale);
        }
    }

    static int centeredArrowOffset(int boundSize, int texturePixels, float drawScale) {
        return (boundSize - Math.round(texturePixels * drawScale)) / 2;
    }

    private void drawTrashCan(GuiGraphics graphics, int mouseX, int mouseY) {
        int bodyX = trashCanX();
        int bodyY = trashCanY();
        boolean hovered = trashCanContains(mouseX, mouseY);
        if (hovered && !trashCanLidSoundPlayed) {
            playUiSound(ModSounds.TRASHCANLID.get(), 1.0f, 1.0f);
            trashCanLidSoundPlayed = true;
        }
        if (!hovered) {
            trashCanLidSoundPlayed = false;
        }
        float step = (float) Math.PI / 48.0f;
        if (hovered) {
            trashCanLidRotation = Math.min(trashCanLidRotation + step, (float) Math.PI / 2.0f);
        } else {
            trashCanLidRotation = Math.max(trashCanLidRotation - step, 0.0f);
        }

        CommonGuiTextures.drawGameMenuTrashBody(graphics, bodyX, bodyY, mapping.s4());

        float lidScale = mapping.s4();
        int lidDrawX = bodyX + ui(60);
        int lidDrawY = bodyY + ui(40);
        graphics.pose().pushPose();
        graphics.pose().translate(lidDrawX, lidDrawY, 0);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(trashCanLidRotation));
        graphics.pose().scale(lidScale, lidScale, 1.0f);
        CommonGuiTextures.drawGameMenuTrashLidAtCurrentPose(graphics, -16, -10);
        graphics.pose().popPose();
    }

    private void drawPlayerInventory(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.player == null) {
            return;
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < INVENTORY_COLS; col++) {
                int invIndex = col + row * 9 + 9;
                int x = inventorySlotX(col);
                int y = inventorySlotY(row);
                boolean hovered = mouseX >= x && mouseX < x + ui(INVENTORY_SLOT_SDV) && mouseY >= y && mouseY < y + ui(INVENTORY_SLOT_SDV);
                drawInventorySlot(graphics, x, y, mc.player.getInventory().getItem(invIndex), hovered, invIndex);
            }
        }

        int hotbarY = inventoryHotbarY();
        for (int col = 0; col < INVENTORY_COLS; col++) {
            int x = inventorySlotX(col);
            boolean hovered = mouseX >= x && mouseX < x + ui(INVENTORY_SLOT_SDV) && mouseY >= hotbarY && mouseY < hotbarY + ui(INVENTORY_SLOT_SDV);
            drawInventorySlot(graphics, x, hotbarY, mc.player.getInventory().getItem(col), hovered, col);
        }
    }

    private void drawInventorySlot(GuiGraphics graphics, int x, int y, ItemStack stack,
                                   boolean hovered, int inventoryIndex) {
        CommonGuiTextures.drawMenuTile(graphics, x, y, ui(INVENTORY_SLOT_SDV), ui(INVENTORY_SLOT_SDV), 10);
        if (hovered) {
            graphics.pose().pushPose();
            graphics.fill(x, y, x + ui(INVENTORY_SLOT_SDV), y + ui(INVENTORY_SLOT_SDV),
                    0x35FFFFFF);
            graphics.pose().popPose();
        }
        ItemStack displayedStack = quickCraftPreviewStack(inventoryIndex, stack);
        if (!displayedStack.isEmpty()) {
            int boxW = ui(INVENTORY_SLOT_SDV);
            CommonGuiTextures.drawItemWithDecorationsCenteredInBox(
                    graphics, this.font, displayedStack, x, y, boxW, boxW, mapping.s4());
        }
    }

    private ItemStack quickCraftPreviewStack(int inventoryIndex, ItemStack currentStack) {
        int menuSlot = StardewGameMenu.menuSlotForInventoryIndex(inventoryIndex);
        if (!isQuickCrafting || quickCraftSlots.size() <= 1
                || menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return currentStack;
        }

        Slot slot = menu.slots.get(menuSlot);
        ItemStack carried = currentCarriedItem();
        if (!quickCraftSlots.contains(slot) || carried.isEmpty()
                || !AbstractContainerMenu.canItemQuickReplace(slot, carried, true)
                || !menu.canDragTo(slot)) {
            return currentStack;
        }

        int existing = currentStack.isEmpty() ? 0 : currentStack.getCount();
        int limit = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
        int distributed = AbstractContainerMenu.getQuickCraftPlaceCount(
                quickCraftSlots, quickCraftState().stardewcraft$quickCraftingType(), carried);
        return carried.copyWithCount(Math.min(limit, existing + distributed));
    }

    private ItemStack displayedCarriedItem() {
        ItemStack carried = currentCarriedItem();
        if (carried.isEmpty() || !isQuickCrafting || quickCraftSlots.size() <= 1) {
            return carried;
        }
        int remainder = quickCraftState().stardewcraft$quickCraftingRemainder();
        return remainder <= 0 ? ItemStack.EMPTY : carried.copyWithCount(remainder);
    }

    private QuickCraftStateAccess quickCraftState() {
        return (QuickCraftStateAccess) (Object) this;
    }

    private int inventoryGridWidth() {
        return ui(INVENTORY_COLS * INVENTORY_SLOT_SDV + (INVENTORY_COLS - 1) * INVENTORY_GAP_SDV);
    }

    private int inventorySlotX(int col) {
        int startX = menuX + (menuWidth - inventoryGridWidth()) / 2;
        return startX + col * ui(INVENTORY_SLOT_SDV + INVENTORY_GAP_SDV);
    }

    private int inventorySlotY(int row) {
        return menuY + ui(INVENTORY_TOP_SDV + row * (INVENTORY_SLOT_SDV + INVENTORY_GAP_SDV));
    }

    private int inventoryHotbarY() {
        return menuY + ui(INVENTORY_TOP_SDV + 3 * (INVENTORY_SLOT_SDV + INVENTORY_GAP_SDV));
    }

// unused scaling helpers removed

    private int craftingGridIndexAt(double mouseX, double mouseY) {
        return craftingGridIndexAt(mouseX, mouseY, 0);
    }

    private int craftingGridIndexAt(double mouseX, double mouseY, int pad) {
        if (currentTab != 4) {
            return -1;
        }

        if (currentCraftingPage < 0 || currentCraftingPage >= craftingPages.size()) {
            return -1;
        }

        List<RecipeCell> page = craftingPages.get(currentCraftingPage);
        for (RecipeCell cellData : page) {
            int x = recipeCellX(cellData);
            int y = recipeCellY(cellData);
            if (recipeContains(mouseX, mouseY, x, y, recipeCellWidth(), recipeCellHeight(cellData), pad)) {
                return cellData.recipeIndex();
            }
        }

        return -1;
    }

    private int pageButtonX() {
        return menuX + ui(800);
    }

    private int pageUpButtonY() {
        return craftingGridY();
    }

    private int pageDownButtonY() {
        return craftingGridY() + ui((CRAFTING_RECIPE_ROWS - 1) * CRAFTING_RECIPE_STEP_SDV + 32);
    }

    private int craftingGridX() {
        return menuX + ui(CRAFTING_GRID_X_SDV);
    }

    private int craftingGridY() {
        return menuY + ui(CRAFTING_GRID_Y_SDV);
    }

    private int inventoryStartX() {
        return menuX + ui(BORDER_WIDTH + 16);
    }

    private int inventoryStartY() {
        return menuY + ui(INVENTORY_TOP_SDV);
    }

    private boolean upButtonContains(double mouseX, double mouseY) {
        int x = pageButtonX();
        int y = pageUpButtonY();
        int w = ui(64);
        int h = ui(64);
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean downButtonContains(double mouseX, double mouseY) {
        int x = pageButtonX();
        int y = pageDownButtonY();
        int w = ui(64);
        int h = ui(64);
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean trashCanContains(double mouseX, double mouseY) {
        int x = trashCanX();
        int y = trashCanY();
        int w = ui(64);
        int h = ui(104);
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private int trashCanX() {
        // Stardew InventoryPage: xPositionOnScreen + width / 3 + 576 + 32.
        return menuX + menuWidth / 3 + ui(576 + 32);
    }

    private int trashCanY() {
        return menuY + ui(INV_PAGE_TRASH_Y);
    }

    private int recipeCellX(RecipeCell cellData) {
        return craftingGridX() + ui(cellData.x() * CRAFTING_RECIPE_STEP_SDV);
    }

    private int recipeCellY(RecipeCell cellData) {
        return craftingGridY() + ui(cellData.y() * CRAFTING_RECIPE_STEP_SDV);
    }

    private int recipeCellWidth() {
        return ui(CRAFTING_RECIPE_SLOT_SDV);
    }

    private int recipeCellHeight(RecipeCell cellData) {
        return ui(cellData.bigCraftable() ? CRAFTING_RECIPE_SLOT_SDV * 2 : CRAFTING_RECIPE_SLOT_SDV);
    }

    /** JEI bridge for recipe outputs drawn outside normal container slots. */
    public ClickableItem jeiIngredientAt(double mouseX, double mouseY) {
        int recipeIndex = craftingGridIndexAt(mouseX, mouseY, ui(4));
        if (recipeIndex < 0 || recipeIndex >= craftingRecipeStacks.size()) {
            return null;
        }
        for (RecipeCell cell : craftingPages.get(currentCraftingPage)) {
            if (cell.recipeIndex() == recipeIndex) {
                return new ClickableItem(craftingRecipeStacks.get(recipeIndex).copy(),
                        recipeCellX(cell), recipeCellY(cell), recipeCellWidth(), recipeCellHeight(cell));
            }
        }
        return null;
    }

    public boolean shouldShowJei() {
        return tabSupportsJei(currentTab);
    }

    static boolean tabSupportsJei(int tab) {
        return tab == 0 || tab == 4;
    }

    /**
     * Areas drawn outside the main menu rectangle. JEI uses these to move its
     * ingredient and bookmark panels instead of covering tabs or controls.
     */
    public List<Rect2i> jeiGuiExtraAreas() {
        if (!shouldShowJei()) {
            return this.width > 0 && this.height > 0
                    ? List.of(new Rect2i(0, 0, this.width, this.height))
                    : List.of();
        }

        List<Rect2i> areas = new ArrayList<>(6);
        int firstTabX = tabX(0);
        int tabsRight = tabX(TAB_COUNT - 1) + tabSize();
        areas.add(new Rect2i(firstTabX, tabY(), tabsRight - firstTabX, tabSize()));

        int closeX = menuX + activeMenuWidth() - ui(CLOSE_X_OFFSET_SDV);
        int closeY = menuY - ui(CLOSE_Y_OFFSET_SDV);
        areas.add(new Rect2i(closeX, closeY, ui(CLOSE_SIZE_SDV), ui(CLOSE_SIZE_SDV)));

        if (currentTab == 0) {
            int organizeSize = ui(64);
            areas.add(new Rect2i(organizeButtonX(), organizeButtonY(), organizeSize, organizeSize));
            areas.add(new Rect2i(trashCanX(), trashCanY(), ui(64), ui(104)));

            if (shouldShowJunimoNoteIcon()) {
                int size = junimoIconHoverSize();
                areas.add(new Rect2i(junimoIconX(), junimoIconY(), size, size));
            }
        } else if (currentTab == 4) {
            int controlSize = ui(64);
            areas.add(new Rect2i(trashCanX(), trashCanY(), controlSize, ui(104)));
            if (currentCraftingPage > 0) {
                areas.add(new Rect2i(pageButtonX(), pageUpButtonY(), controlSize, controlSize));
            }
            if (currentCraftingPage < Math.max(0, craftingPages.size() - 1)) {
                areas.add(new Rect2i(pageButtonX(), pageDownButtonY(), controlSize, controlSize));
            }
        }
        return List.copyOf(areas);
    }

    public int jeiGuiLeft() {
        return menuX;
    }

    public int jeiGuiTop() {
        return menuY;
    }

    public int jeiGuiWidth() {
        return menuWidth;
    }

    public int jeiGuiHeight() {
        return menuHeight;
    }

    public record ClickableItem(ItemStack stack, int x, int y, int width, int height) {
    }

    private boolean recipeContains(double mouseX, double mouseY, int x, int y, int w, int h, int pad) {
        return mouseX >= x - pad && mouseX < x + w + pad && mouseY >= y - pad && mouseY < y + h + pad;
    }

    private boolean isCraftableClient(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= craftingRecipeIds.size()) {
            return false;
        }
        return computeMaxCraftsClient(getRecipeIngredients(craftingRecipeIds.get(recipeIndex)), 1) > 0;
    }

    private float stepScale(float current, float target, float step) {
        if (current < target) {
            return Math.min(current + step, target);
        }
        return Math.max(current - step, target);
    }

    private void submitCraftRequest(int requestedCount) {
        if (selectedCraftingIndex < 0 || selectedCraftingIndex >= craftingRecipeIds.size()) {
            return;
        }
        submitCraftRequestForIndex(selectedCraftingIndex, requestedCount);
    }

    private void submitCraftRequestForIndex(int recipeIndex, int requestedCount) {
        if (recipeIndex < 0 || recipeIndex >= craftingRecipeIds.size()) {
            return;
        }
        String recipeId = craftingRecipeIds.get(recipeIndex);
        PacketDistributor.sendToServer(new CraftingMenuCraftSubmitPayload(recipeId, requestedCount));
    }

    private void submitTrashCarriedRequest() {
        PacketDistributor.sendToServer(new CraftingMenuInventoryActionPayload(CraftingMenuInventoryActionPayload.ACTION_TRASH_CARRIED, -1, false));
    }

    private void submitOrganizeInventoryRequest() {
        PacketDistributor.sendToServer(new InventoryOrganizePayload(InventoryOrganizePayload.TARGET_PLAYER_INVENTORY));
    }

    private ItemStack currentCarriedItem() {
        ItemStack carried = this.menu.getCarried();
        return carried == null ? ItemStack.EMPTY : carried;
    }

    private boolean hasCarriedItem() {
        return !currentCarriedItem().isEmpty();
    }

    private int activeInventoryIndexAt(double mouseX, double mouseY) {
        if (currentTab == 0) {
            return invPageHoveredSlot(mouseX, mouseY);
        }
        if (currentTab == 4) {
            return hoveredInventorySlot(mouseX, mouseY);
        }
        return -1;
    }

    private Slot activeMenuSlotAt(double mouseX, double mouseY) {
        int inventoryIndex = activeInventoryIndexAt(mouseX, mouseY);
        int menuSlotIndex = StardewGameMenu.menuSlotForInventoryIndex(inventoryIndex);
        if (menuSlotIndex < 0 || menuSlotIndex >= menu.slots.size()) {
            return null;
        }
        return menu.slots.get(menuSlotIndex);
    }

    /** Used by the container hit-test mixin so the whole SDV tile counts as its Slot. */
    public Slot findVisualInventorySlot(double mouseX, double mouseY) {
        return activeMenuSlotAt(mouseX, mouseY);
    }

    private double mappedSlotMouseX(Slot slot, double fallback) {
        return slot == null ? fallback : leftPos + slot.x + 8.0;
    }

    private double mappedSlotMouseY(Slot slot, double fallback) {
        return slot == null ? fallback : topPos + slot.y + 8.0;
    }

    private boolean handleStandardInventoryClick(double mouseX, double mouseY, int button) {
        Slot slot = activeMenuSlotAt(mouseX, mouseY);
        if (slot == null) {
            return false;
        }
        return super.mouseClicked(mappedSlotMouseX(slot, mouseX), mappedSlotMouseY(slot, mouseY), button);
    }

    private int vanillaLikeCraftAmountOnLeftClick() {
        if (hasShiftDown()) {
            if (hasControlDown()) {
                return 25;
            }
            return 5;
        }
        return 1;
    }

    private List<Ingredient> getRecipeIngredients(String recipePath) {
        return StardewCraftingRecipeData.toExpandedIngredients(recipePath,
            ClientPlayerDataCache.hasProfession(ProfessionType.TRAPPER));
    }

    private List<RecipeRequirement> getRecipeRequirements(String recipePath) {
        List<StardewCraftingRecipeData.IngredientEntry> entries = StardewCraftingRecipeData.getIngredientEntries(recipePath,
            ClientPlayerDataCache.hasProfession(ProfessionType.TRAPPER));
        if (entries.isEmpty()) {
            return List.of();
        }

        Map<String, RecipeRequirement> grouped = new LinkedHashMap<>();
        for (StardewCraftingRecipeData.IngredientEntry entry : entries) {
            Ingredient ingredient = StardewCraftingRecipeData.toIngredient(entry);
            if (ingredient.isEmpty()) {
                continue;
            }
            String key = ingredientSignature(ingredient);
            RecipeRequirement existing = grouped.get(key);
            ItemStack icon = StardewCraftingRecipeData.getDisplayStack(entry);
            if (icon.isEmpty()) {
                icon = resolveIngredientIcon(ingredient);
            }
            Component name = StardewCraftingRecipeData.getDisplayName(entry);
            if (name.getString().isBlank()) {
                name = icon.getHoverName().copy();
            }
            int need = Math.max(1, entry.count());
            if (existing == null) {
                grouped.put(key, new RecipeRequirement(ingredient, icon, name, need));
            } else {
                grouped.put(key, new RecipeRequirement(existing.ingredient(), existing.icon(), existing.name(), existing.need() + need));
            }
        }
        return new ArrayList<>(grouped.values());
    }

    private String ingredientSignature(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return "empty";
        }

        List<String> keys = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (stack.isEmpty()) {
                continue;
            }
            keys.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        keys.sort(String::compareTo);
        return String.join("|", keys);
    }

    private ItemStack resolveIngredientIcon(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return new ItemStack(Items.BARRIER);
        }
        ItemStack[] options = ingredient.getItems();
        if (options.length > 0 && !options[0].isEmpty()) {
            return options[0];
        }
        return new ItemStack(Items.BARRIER);
    }

    private int countMatchingClient(Ingredient ingredient) {
        if (this.minecraft == null || this.minecraft.player == null || ingredient == null || ingredient.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : this.minecraft.player.getInventory().items) {
            if (!stack.isEmpty() && ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int computeMaxCraftsClient(List<Ingredient> ingredients, int cap) {
        if (ingredients == null || ingredients.isEmpty() || this.minecraft == null || this.minecraft.player == null) {
            return 0;
        }

        List<ItemStack> stacks = this.minecraft.player.getInventory().items;
        int[] remain = new int[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            remain[i] = stacks.get(i).getCount();
        }

        int crafted = 0;
        while (crafted < cap && tryConsumeOneCraftClient(remain, stacks, ingredients)) {
            crafted++;
        }
        return crafted;
    }

    private boolean tryConsumeOneCraftClient(int[] remain, List<ItemStack> stacks, List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            int chosen = -1;
            for (int slot = 0; slot < stacks.size(); slot++) {
                if (remain[slot] <= 0) {
                    continue;
                }
                ItemStack stack = stacks.get(slot);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    chosen = slot;
                    break;
                }
            }

            if (chosen < 0) {
                return false;
            }

            remain[chosen]--;
        }
        return true;
    }

    private void drawCraftingTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int invSlot = hoveredInventorySlot(mouseX, mouseY);
        if (invSlot >= 0 && this.minecraft != null && this.minecraft.player != null) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(invSlot);
            if (!stack.isEmpty()) {
                graphics.renderTooltip(this.font, stack, mouseX, mouseY);
                return;
            }
        }

        if (hoveredCraftingIndex >= 0 && hoveredCraftingIndex < craftingRecipeStacks.size()) {
            String recipeId = craftingRecipeIds.get(hoveredCraftingIndex);

            ItemStack output = craftingRecipeStacks.get(hoveredCraftingIndex);
            List<RecipeRequirement> requirements = getRecipeRequirements(recipeId);

            // Build tooltip lines using MC's native renderTooltip
            List<Component> lines = new ArrayList<>();
            // Item tooltip lines (name, category, description, etc.)
            if (this.minecraft != null && this.minecraft.player != null) {
                Item.TooltipContext context = Item.TooltipContext.of(this.minecraft.level);
                TooltipFlag flag = this.minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
                lines.addAll(output.getTooltipLines(context, this.minecraft.player, flag));
            } else {
                lines.add(output.getHoverName().copy());
            }
            // Separator + Ingredients section
            lines.add(Component.empty());
            lines.add(Component.translatable("stardewcraft.crafting.ingredients").withStyle(ChatFormatting.GOLD));
            int ingredientStartLine = lines.size();
            for (RecipeRequirement requirement : requirements) {
                int have = countMatchingClient(requirement.ingredient());
                int need = requirement.need();
                boolean enough = have >= need;
                ChatFormatting color = enough ? ChatFormatting.WHITE : ChatFormatting.RED;
                // "  " prefix = space for icon overlay (about 10px)
                Component line = Component.literal("  ")
                        .append(Component.literal(" " + need + "× ").withStyle(color))
                        .append(requirement.name().copy().withStyle(color));
                lines.add(line);
            }
            lines.add(Component.empty());
            lines.add(Component.translatable("stardewcraft.game_menu.crafting.shortcut_stack")
                    .withStyle(ChatFormatting.GRAY));

            // Render tooltip using MC's native method
            graphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);

            // --- Overlay ingredient icons ---
            // MC tooltip positioning: starts at (mouseX+12, mouseY-12), then clamped
            int tooltipWidth = 0;
            for (Component line : lines) {
                int w = this.font.width(line);
                if (w > tooltipWidth) tooltipWidth = w;
            }
            // MC adds 4px padding on each side inside the tooltip
            int frameWidth = tooltipWidth + 8;
            // Height: first line is 10px tall (lineH + 2px gap), rest are lineH(9) each.
            // But empty lines are only 2px + lineH. MC uses: if line index == 0, height += 2 extra.
            // Actually MC tooltip: first line adds (lineH+2), subsequent lines add lineH each,
            // except if a line is empty it adds lineH/2 instead.
            int tooltipHeight = 8; // top+bottom padding
            for (int i = 0; i < lines.size(); i++) {
                if (i == 0) {
                    tooltipHeight += this.font.lineHeight + 2;
                } else if (lines.get(i).getString().isEmpty()) {
                    tooltipHeight += this.font.lineHeight / 2;
                } else {
                    tooltipHeight += this.font.lineHeight + 1;
                }
            }

            int boxX = mouseX + 12;
            int boxY = mouseY - 12;
            // MC clamping logic
            if (boxX + frameWidth > this.width) {
                boxX -= 28 + frameWidth;
            }
            if (boxY + tooltipHeight + 6 > this.height) {
                boxY = this.height - tooltipHeight - 6;
            }
            if (boxY < 4) {
                boxY = 4;
            }

            // Calculate text start position (4px padding inside box)
            int textX = boxX + 4;
            int textY = boxY + 4;
            // Advance past rendered lines to reach ingredient lines
            for (int i = 0; i < ingredientStartLine; i++) {
                if (i == 0) {
                    textY += this.font.lineHeight + 2;
                } else if (lines.get(i).getString().isEmpty()) {
                    textY += this.font.lineHeight / 2;
                } else {
                    textY += this.font.lineHeight + 1;
                }
            }

            // Draw icons at each ingredient line
            for (int i = 0; i < requirements.size(); i++) {
                RecipeRequirement req = requirements.get(i);
                int lineY = textY;
                // Advance for previous ingredient lines
                for (int j = 0; j < i; j++) {
                    lineY += this.font.lineHeight + 1;
                }
                float s = 0.5f; // 16*0.5=8px, fits in line height of 9
                int iconDrawY = lineY + (this.font.lineHeight - 8) / 2;
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 400.0f);
                CommonGuiTextures.drawItem(graphics, req.icon(), textX, iconDrawY, s);
                graphics.pose().popPose();
            }

            return;
        }

        if (trashCanContains(mouseX, mouseY)) {
            ItemStack carried = currentCarriedItem();
            if (!carried.isEmpty()) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("stardewcraft.game_menu.crafting.trash_can").withStyle(ChatFormatting.WHITE));
                lines.add(carried.getHoverName().copy().withStyle(ChatFormatting.GRAY));
                graphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
            graphics.renderTooltip(this.font, Component.translatable("stardewcraft.game_menu.crafting.trash_can"), mouseX, mouseY);
        }
    }

    private void updateCraftingHoverState(int mouseX, int mouseY) {
        if (craftingKeyboardFocus) {
            hoveredCraftingIndex = selectedCraftingIndex;
            return;
        }

        hoveredCraftingIndex = -1;
        int hoverGridIndex = craftingGridIndexAt(mouseX, mouseY, ui(4));
        if (hoverGridIndex >= 0 && hoverGridIndex < craftingRecipeIds.size()) {
            hoveredCraftingIndex = hoverGridIndex;
        }
    }

    private boolean moveCraftingSelection(int deltaX, int deltaY) {
        if (currentCraftingPage < 0 || currentCraftingPage >= craftingPages.size()) {
            return false;
        }
        List<RecipeCell> page = craftingPages.get(currentCraftingPage);
        if (page.isEmpty()) {
            return false;
        }

        RecipeCell current = page.stream()
                .filter(cell -> cell.recipeIndex() == selectedCraftingIndex)
                .findFirst()
                .orElse(page.getFirst());
        RecipeCell best = null;
        int bestScore = Integer.MAX_VALUE;
        for (RecipeCell candidate : page) {
            if (candidate == current) {
                continue;
            }
            int xDistance = candidate.x() - current.x();
            int yDistance = candidate.y() - current.y();
            if (deltaX < 0 && xDistance >= 0 || deltaX > 0 && xDistance <= 0
                    || deltaY < 0 && yDistance >= 0 || deltaY > 0 && yDistance <= 0) {
                continue;
            }
            int primary = deltaX == 0 ? Math.abs(yDistance) : Math.abs(xDistance);
            int secondary = deltaX == 0 ? Math.abs(xDistance) : Math.abs(yDistance);
            int score = primary * 100 + secondary;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) {
            return false;
        }

        selectedCraftingIndex = best.recipeIndex();
        hoveredCraftingIndex = selectedCraftingIndex;
        craftingKeyboardFocus = true;
        playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
        return true;
    }

    private boolean changeCraftingPage(int delta, boolean keyboardFocus) {
        int oldPage = currentCraftingPage;
        currentCraftingPage += delta;
        clampCraftingPage();
        if (oldPage == currentCraftingPage) {
            return false;
        }
        List<RecipeCell> page = craftingPages.get(currentCraftingPage);
        selectedCraftingIndex = page.isEmpty() ? -1 : page.getFirst().recipeIndex();
        hoveredCraftingIndex = keyboardFocus ? selectedCraftingIndex : -1;
        craftingKeyboardFocus = keyboardFocus;
        playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
        return true;
    }

    private int hoveredInventorySlot(double mouseX, double mouseY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < INVENTORY_COLS; col++) {
                int x = inventorySlotX(col);
                int y = inventorySlotY(row);
                if (mouseX >= x && mouseX < x + ui(INVENTORY_SLOT_SDV) && mouseY >= y && mouseY < y + ui(INVENTORY_SLOT_SDV)) {
                    return col + row * 9 + 9;
                }
            }
        }

        int hotbarY = inventoryHotbarY();
        for (int col = 0; col < INVENTORY_COLS; col++) {
            int x = inventorySlotX(col);
            if (mouseX >= x && mouseX < x + ui(INVENTORY_SLOT_SDV) && mouseY >= hotbarY && mouseY < hotbarY + ui(INVENTORY_SLOT_SDV)) {
                return col;
            }
        }
        return -1;
    }

    private void closeWithSound() {
        if (hasCarriedItem()) {
            playUiSound(ModSounds.CANCEL.get(), 1.0f, 1.0f);
            return;
        }
        playUiSound(ModSounds.BIG_DESELECT.get(), 1.0f, 1.0f);
        this.onClose();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        craftingKeyboardFocus = false;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (currentTab == TAB_SOCIAL && socialTuneMode) {
                int picked = socialTuneTargetAt(mouseX, mouseY);
                if (picked >= 0) {
                    socialTuneTarget = picked;
                    socialTuneDragging = true;
                    playUiSound(ModSounds.SMALL_SELECT.get(), 1.0f, 1.0f);
                    return true;
                }
            }

            if (closeContains(mouseX, mouseY)) {
                closeWithSound();
                return true;
            }
            for (int i = 0; i < TAB_COUNT; i++) {
                if (tabContains(i, mouseX, mouseY)) {
                    if (hasCarriedItem() && i != 0 && i != 4) {
                        playUiSound(ModSounds.CANCEL.get(), 1.0f, 1.0f);
                        return true;
                    }
                    if (currentTab != i) {
                        currentTab = i;
                        positionInventorySlots();
                        if (currentTab == TAB_SOCIAL) {
                            socialScroll = 0;
                            PacketDistributor.sendToServer(new RequestNpcFriendshipOverviewPayload());
                        }
                        if (currentTab == 3) {
                            farmMgmtScroll = 0;
                            PacketDistributor.sendToServer(new com.stardew.craft.network.payload.RequestFarmPermPayload());
                        }
                        if (currentTab == TAB_OPTIONS) {
                            currentOptionsPage = OPTIONS_PAGE_SETTINGS;
                        }
                        playUiSound(ModSounds.SMALL_SELECT.get(), 1.0f, 1.0f);
                    }
                    return true;
                }
            }

            if (currentTab == TAB_SOCIAL) {
                List<NpcFriendshipClientCache.Entry> socialEntries = visibleSocialEntries();
                int total = socialEntries.size();
                int maxScroll = socialMaxScroll(total);

                if (socialUpButtonContains(mouseX, mouseY) && socialScroll > 0) {
                    socialScroll--;
                    playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                    return true;
                }
                if (socialDownButtonContains(mouseX, mouseY) && socialScroll < maxScroll) {
                    socialScroll++;
                    playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                    return true;
                }
                if (socialScrollBarContains(mouseX, mouseY, total)) {
                    socialScrolling = true;
                    return true;
                }
                if (socialScrollRunnerContains(mouseX, mouseY)) {
                    int before = socialScroll;
                    setSocialScrollFromMouse(mouseY, total);
                    if (before != socialScroll) {
                        playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                    }
                    return true;
                }
                for (int visibleRow = 0; visibleRow < SOCIAL_MAX_VISIBLE; visibleRow++) {
                    int index = socialScroll + visibleRow;
                    if (index >= socialEntries.size()) break;
                    int rowY = socialRowPosition(visibleRow);
                    if (contains(mouseX, mouseY, menuX, rowY, socialPageWidth(), ui(SOCIAL_ROW_HEIGHT_SDV))) {
                        this.minecraft.setScreen(new StardewNpcProfileScreen(this, socialEntries.get(index), socialEntries));
                        playUiSound(ModSounds.BIG_SELECT.get(), 1.0F, 1.0F);
                        return true;
                    }
                }
            }

            // Farm management tab click handling
            if (currentTab == 3 && com.stardew.craft.client.gui.FarmPermissionClientCache.hasData()) {
                if (handleFarmMgmtClick((int) mouseX, (int) mouseY)) {
                    return true;
                }
            }

            if (currentTab == TAB_OPTIONS) {
                if (handleOptionsClick(mouseX, mouseY)) {
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }

            if (currentTab == TAB_COLLECTIONS) {
                int sideTab = collectionSideTabAt(mouseX, mouseY);
                if (sideTab >= 0) {
                    if (currentCollectionTab != sideTab) {
                        currentCollectionTab = sideTab;
                        currentCollectionPage = 0;
                        playUiSound(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
                    }
                    return true;
                }

                if (currentCollectionTab != COLLECTION_SECRET_NOTES) {
                    int entryCount = currentCollectionTab == COLLECTION_LETTERS
                            ? letterCollectionEntries().size()
                            : collectionEntries(currentCollectionTab).size();
                    int pageCount = Math.max(1,
                            (entryCount + COLLECTION_PAGE_SIZE - 1) / COLLECTION_PAGE_SIZE);
                    if (currentCollectionPage > 0
                            && contains(mouseX, mouseY, collectionBackX(), collectionArrowY(),
                                    ui(48), ui(44))) {
                        currentCollectionPage--;
                        playUiSound(ModSounds.SHWIP.get(), 1.0F, 1.0F);
                        return true;
                    }
                    if (currentCollectionPage + 1 < pageCount
                            && contains(mouseX, mouseY, collectionForwardX(), collectionArrowY(),
                                    ui(48), ui(44))) {
                        currentCollectionPage++;
                        playUiSound(ModSounds.SHWIP.get(), 1.0F, 1.0F);
                        return true;
                    }
                }

                String mailId = letterAt(mouseX, mouseY);
                if (mailId != null) {
                    PacketDistributor.sendToServer(new OpenSeenMailPayload(mailId));
                    playUiSound(ModSounds.BIG_SELECT.get(), 1.0F, 1.0F);
                    return true;
                }

                int noteNumber = secretNoteAt(mouseX, mouseY);
                if (noteNumber > 0
                        && hasSeenSecretNote(noteNumber)) {
                    ResourceLocation noteId = SecretNoteRegistry.byDisplayNumber(noteNumber);
                    if (noteId != null) {
                        PacketDistributor.sendToServer(
                                new com.stardew.craft.network.payload.OpenSeenSecretNotePayload(noteId.toString()));
                        playUiSound(ModSounds.BIG_SELECT.get(), 1.0F, 1.0F);
                        return true;
                    }
                }
            }

            if (currentTab == 0) {
                // Junimo Note icon click → open read-only bundle viewer (SDV parity)
                if (shouldShowJunimoNoteIcon() && junimoIconContains(mouseX, mouseY)) {
                    PacketDistributor.sendToServer(
                            new com.stardew.craft.communitycenter.network.OpenBundleViewerPayload());
                    playUiSound(ModSounds.BIG_SELECT.get(), 1.0f, 1.0f);
                    return true;
                }
                // Equipment slot click
                int equipSlot = invPageHoveredEquip(mouseX, mouseY);
                if (equipSlot >= 0) {
                    PacketDistributor.sendToServer(new com.stardew.craft.network.payload.EquipmentActionPayload(equipSlot));
                    playUiSound(ModSounds.SMALL_SELECT.get(), 1.0f, 1.0f);
                    return true;
                }
                if (handleStandardInventoryClick(mouseX, mouseY, button)) {
                    return true;
                }
                // Organize button
                if (organizeButtonContains(mouseX, mouseY)) {
                    // SDV: ItemGrabMenu.organizeItemsInList
                    submitOrganizeInventoryRequest();
                    playUiSound(ModSounds.SHIP.get(), 1.0f, 1.0f);
                    return true;
                }
                // Trash can click
                if (trashCanContains(mouseX, mouseY) && hasCarriedItem()) {
                    if (InventoryTrashPolicy.canTrash(currentCarriedItem())) {
                        submitTrashCarriedRequest();
                        playUiSound(ModSounds.THROW_DOWN_ITEM.get(), 1.0f, 1.0f);
                    } else {
                        playUiSound(ModSounds.CANCEL.get(), 1.0f, 1.0f);
                    }
                    return true;
                }
            }

            if (currentTab == 4) {
                if (handleStandardInventoryClick(mouseX, mouseY, button)) {
                    return true;
                }
            }

            if (currentTab == 4 && upButtonContains(mouseX, mouseY) && currentCraftingPage > 0) {
                changeCraftingPage(-1, false);
                return true;
            }

            if (currentTab == 4 && downButtonContains(mouseX, mouseY) && currentCraftingPage < Math.max(0, craftingPages.size() - 1)) {
                changeCraftingPage(1, false);
                return true;
            }

            if (currentTab == 4 && trashCanContains(mouseX, mouseY) && hasCarriedItem()) {
                if (InventoryTrashPolicy.canTrash(currentCarriedItem())) {
                    submitTrashCarriedRequest();
                    playUiSound(ModSounds.THROW_DOWN_ITEM.get(), 1.0f, 1.0f);
                } else {
                    playUiSound(ModSounds.CANCEL.get(), 1.0f, 1.0f);
                }
                return true;
            }

            int gridIndex = craftingGridIndexAt(mouseX, mouseY, ui(4));
            if (gridIndex >= 0 && gridIndex < craftingRecipeIds.size()) {
                craftingKeyboardFocus = false;
                if (selectedCraftingIndex != gridIndex) {
                    selectedCraftingIndex = gridIndex;
                }

                if (currentTab == 4 && isCraftableClient(gridIndex)) {
                    submitCraftRequest(vanillaLikeCraftAmountOnLeftClick());
                    playUiSound(ModSounds.COIN.get(), 1.0f, 1.0f);
                }
                return true;
            }
        }

        if (button == 1 && currentTab == 0) {
            if (handleStandardInventoryClick(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (button == 1 && currentTab == 4) {
            if (handleStandardInventoryClick(mouseX, mouseY, button)) {
                return true;
            }

            int gridIndex = craftingGridIndexAt(mouseX, mouseY, ui(4));
            if (gridIndex >= 0 && gridIndex < craftingRecipeIds.size()) {
                craftingKeyboardFocus = false;
                selectedCraftingIndex = gridIndex;
                if (isCraftableClient(gridIndex)) {
                    submitCraftRequest(1);
                    playUiSound(ModSounds.COIN.get(), 1.0f, 1.0f);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == TAB_SOCIAL && socialTuneMode) {
            if (scrollY > 0) {
                socialTuneStep = Math.min(16, socialTuneStep + 1);
            } else if (scrollY < 0) {
                socialTuneStep = Math.max(1, socialTuneStep - 1);
            }
            return true;
        }

        if (currentTab == TAB_SOCIAL) {
            int total = visibleSocialEntries().size();
            int maxScroll = Math.max(0, total - SOCIAL_MAX_VISIBLE);
            if (maxScroll <= 0) {
                return true;
            }

            int before = socialScroll;
            if (scrollY > 0) {
                socialScroll = Math.max(0, socialScroll - 1);
            } else if (scrollY < 0) {
                socialScroll = Math.min(maxScroll, socialScroll + 1);
            }

            if (before != socialScroll) {
                playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
            }
            return true;
        }

        if (currentTab == 3) {
            var players = com.stardew.craft.client.gui.FarmPermissionClientCache.getPlayers();
            int maxScroll = Math.max(0, players.size() - farmMgmtVisibleRows);
            if (maxScroll > 0) {
                int before = farmMgmtScroll;
                if (scrollY > 0) farmMgmtScroll = Math.max(0, farmMgmtScroll - 1);
                else if (scrollY < 0) farmMgmtScroll = Math.min(maxScroll, farmMgmtScroll + 1);
                if (before != farmMgmtScroll) playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
            }
            return true;
        }

        if (showingLeaderboardPage()) {
            LeaderboardLayout layout = leaderboardLayout();
            if (insideLeaderboardMetricTabStrip(layout, mouseX, mouseY)) {
                int maxTabScroll = leaderboardMetricMaxTabScroll(layout);
                if (maxTabScroll > 0) {
                    int before = leaderboardMetricTabScroll;
                    if (scrollY > 0) leaderboardMetricTabScroll = Math.max(0, leaderboardMetricTabScroll - 1);
                    else if (scrollY < 0) leaderboardMetricTabScroll = Math.min(maxTabScroll, leaderboardMetricTabScroll + 1);
                    if (before != leaderboardMetricTabScroll) playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                }
                return true;
            }
            leaderboardVisibleRows = layout.visibleRows();
                int maxScroll = LeaderboardClientCache.hasData(leaderboardMetric.id(), leaderboardPeriod.id(), leaderboardPage)
                    ? Math.max(0, LeaderboardClientCache.getRows().size() - leaderboardVisibleRows)
                    : 0;
            if (maxScroll > 0) {
                int before = leaderboardScroll;
                if (scrollY > 0) leaderboardScroll = Math.max(0, leaderboardScroll - 1);
                else if (scrollY < 0) leaderboardScroll = Math.min(maxScroll, leaderboardScroll + 1);
                if (before != leaderboardScroll) playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
            }
            return true;
        }

        if (currentTab != 4 || craftingRecipeIds.isEmpty()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        if (scrollY > 0 && changeCraftingPage(-1, false)
                || scrollY < 0 && changeCraftingPage(1, false)) {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (currentTab == TAB_SOCIAL && socialTuneMode && socialTuneDragging && button == 0 && socialTuneTarget >= 0) {
            int dx = Math.round((float) dragX * guiScale());
            int dy = Math.round((float) dragY * guiScale());
            if (dx != 0 || dy != 0) {
                socialTuneOffsetX[socialTuneTarget] += dx;
                socialTuneOffsetY[socialTuneTarget] += dy;
            }
            return true;
        }

        if (currentTab == TAB_SOCIAL && socialScrolling && button == 0) {
            int before = socialScroll;
            setSocialScrollFromMouse(mouseY, visibleSocialEntries().size());
            if (before != socialScroll) {
                playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
            }
            return true;
        }

        if (currentTab == 0 || currentTab == 4) {
            Slot slot = activeMenuSlotAt(mouseX, mouseY);
            return super.mouseDragged(mappedSlotMouseX(slot, mouseX), mappedSlotMouseY(slot, mouseY),
                    button, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            socialScrolling = false;
            socialTuneDragging = false;
        }

        if (currentTab == 0 || currentTab == 4) {
            Slot slot = activeMenuSlotAt(mouseX, mouseY);
            return super.mouseReleased(mappedSlotMouseX(slot, mouseX), mappedSlotMouseY(slot, mouseY), button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || ModKeyMappings.GAME_MENU.matches(keyCode, scanCode)) {
            closeWithSound();
            return true;
        }

        if (handleSocialTuneKey(keyCode, modifiers)) {
            return true;
        }

        if (currentTab == TAB_SOCIAL) {
            int total = visibleSocialEntries().size();
            int maxScroll = Math.max(0, total - SOCIAL_MAX_VISIBLE);
            if (keyCode == 265) {
                int before = socialScroll;
                socialScroll = Math.max(0, socialScroll - 1);
                if (before != socialScroll) {
                    playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                }
                return true;
            }
            if (keyCode == 264) {
                int before = socialScroll;
                socialScroll = Math.min(maxScroll, socialScroll + 1);
                if (before != socialScroll) {
                    playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                }
                return true;
            }
        }
        if (showingLeaderboardPage()) {
            LeaderboardLayout layout = leaderboardLayout();
            leaderboardVisibleRows = layout.visibleRows();
                int maxScroll = LeaderboardClientCache.hasData(leaderboardMetric.id(), leaderboardPeriod.id(), leaderboardPage)
                    ? Math.max(0, LeaderboardClientCache.getRows().size() - leaderboardVisibleRows)
                    : 0;
            if (keyCode == 265) {
                int before = leaderboardScroll;
                leaderboardScroll = Math.max(0, leaderboardScroll - 1);
                if (before != leaderboardScroll) playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                return true;
            }
            if (keyCode == 264) {
                int before = leaderboardScroll;
                leaderboardScroll = Math.min(maxScroll, leaderboardScroll + 1);
                if (before != leaderboardScroll) playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                return true;
            }
        }
        if (currentTab == 4
                && hasControlDown()
                && hasShiftDown()
                && (keyCode == GLFW.GLFW_KEY_1 || keyCode == GLFW.GLFW_KEY_KP_1)) {
            int recipeIndex = hoveredCraftingIndex >= 0 ? hoveredCraftingIndex : selectedCraftingIndex;
            if (recipeIndex >= 0 && recipeIndex < craftingRecipeIds.size() && isCraftableClient(recipeIndex)) {
                selectedCraftingIndex = recipeIndex;
                submitCraftRequestForIndex(recipeIndex, -1);
                playUiSound(ModSounds.COIN.get(), 1.0f, 1.0f);
                return true;
            }
        }
        if (currentTab == 4) {
            if (keyCode == GLFW.GLFW_KEY_LEFT && moveCraftingSelection(-1, 0)
                    || keyCode == GLFW.GLFW_KEY_RIGHT && moveCraftingSelection(1, 0)
                    || keyCode == GLFW.GLFW_KEY_UP && moveCraftingSelection(0, -1)
                    || keyCode == GLFW.GLFW_KEY_DOWN && moveCraftingSelection(0, 1)) {
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP && changeCraftingPage(-1, true)
                    || keyCode == GLFW.GLFW_KEY_PAGE_DOWN && changeCraftingPage(1, true)) {
                return true;
            }
            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_SPACE)
                    && craftingKeyboardFocus
                    && selectedCraftingIndex >= 0 && isCraftableClient(selectedCraftingIndex)) {
                submitCraftRequestForIndex(selectedCraftingIndex, 1);
                playUiSound(ModSounds.COIN.get(), 1.0f, 1.0f);
                return true;
            }
        }

        if (keyCode == InputConstants.KEY_DELETE && (currentTab == 0 || currentTab == 4) && hasCarriedItem()) {
            if (InventoryTrashPolicy.canTrash(currentCarriedItem())) {
                submitTrashCarriedRequest();
                playUiSound(ModSounds.THROW_DOWN_ITEM.get(), 1.0f, 1.0f);
            } else {
                playUiSound(ModSounds.CANCEL.get(), 1.0f, 1.0f);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        Minecraft mc = this.minecraft;
        if (mc != null && mc.player != null && currentTab == 4) {
            rebuildCraftingEntries();
        }
    }

    @Override
    public boolean isPauseScreen() {
        // StardewPauseClientState handles this menu without pausing Minecraft's whole integrated
        // server, which would also freeze dimensions outside the Stardew clock domain.
        return false;
    }
}
