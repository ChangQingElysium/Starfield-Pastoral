package com.stardew.craft.client.gui.quest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.LocalizedGuiAssets;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalType;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.quest.StardewQuest;
import com.stardew.craft.quest.network.AcceptQuestPayload;
import com.stardew.craft.quest.network.ClientQuestData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SDV Billboard UI — 公告栏界面（日历 + 每日任务 双Tab）
 * 严格复刻 SDV Billboard.cs 布局
 */
@SuppressWarnings("null")
public class BillboardScreen extends Screen {

    // ─── 纹理 ───
    private static final ResourceLocation QUEST_DONE_STAR = billboard("quest_done_star");
    private static final ResourceLocation BILLBOARD_TEXTURE = gui("billboard");
    private static final ResourceLocation CURSORS_TEXTURE = gui("cursors");
    private static final ResourceLocation CURSORS_1_6_TEXTURE = gui("cursors_1_6");
    private static final int CAL_W = 301, CAL_H = 198;
    private static final int QUEST_W = 338, QUEST_H = 198;
    private static final int BILLBOARD_TEXTURE_W = 338, BILLBOARD_TEXTURE_H = 512;
    private static final int CURSORS_TEXTURE_W = 704, CURSORS_TEXTURE_H = 2256;
    private static final int CURSORS_1_6_TEXTURE_W = 512, CURSORS_1_6_TEXTURE_H = 512;

    // ─── 日历格子 ───
    @SuppressWarnings("unused")
    private static final int GRID_COLS = 7, GRID_ROWS = 4;

    // ─── 每日任务完成星标 ───
    private static final int STAR_W = 10, STAR_H = 11;

    // ─── 关闭按钮 ───
    private static final int CLOSE_W = 12, CLOSE_H = 12;

    // ─── 颜色 ───
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int MONEY_COLOR = 0xFF2C6E0F;

    // ─── 状态 ───
    private int currentTab = 0; // 0 = 日历, 1 = 每日任务
    private int windowX, windowY, windowW, windowH;
    private float s4;
    private int closeX, closeY, closeW2, closeH2;
    private float closeScale = 1.0f;

    // Accept button bounds
    private int acceptX, acceptY, acceptW2, acceptH2;

    public BillboardScreen() {
        super(Component.translatable("gui.stardewcraft.billboard"));
        // SDV parity Billboard.cs:171 Game1.playSound("bigSelect")
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                com.stardew.craft.sound.ModSounds.BIG_SELECT.get(), 1.0F, 1.0F));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        s4 = 4.0f / guiScale;

        // Clamp: largest tab is QUEST_W×QUEST_H (338×198), ensure it fits
        float maxTabW = Math.max(CAL_W, QUEST_W);
        float maxTabH = Math.max(CAL_H, QUEST_H);
        float marginH = 48; // for tab labels above + close button
        s4 = Math.min(s4, Math.min((float) width / maxTabW, (float)(height - marginH) / maxTabH));

        recalcLayout();
    }

    private void recalcLayout() {
        if (currentTab == 0) {
            // Calendar: 301×198 → 4×
            windowW = Math.round(CAL_W * s4);
            windowH = Math.round(CAL_H * s4);
        } else {
            // Daily quest: 338×198 → 4×
            windowW = Math.round(QUEST_W * s4);
            windowH = Math.round(QUEST_H * s4);
        }
        windowX = width / 2 - windowW / 2;
        windowY = height / 2 - windowH / 2;

        closeW2 = Math.round(CLOSE_W * s4);
        closeH2 = Math.round(CLOSE_H * s4);
        closeX = windowX + windowW - Math.round(20 * s4);
        closeY = windowY - Math.round(8 * s4);

        // Accept button
        acceptW2 = Math.round(128 * s4);
        acceptH2 = Math.round(32 * s4);
        acceptX = windowX + windowW / 2 - acceptW2 / 2;
        acceptY = windowY + windowH - Math.round(32 * s4);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xBF000000);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        if (currentTab == 0) {
            renderCalendar(g, mouseX, mouseY);
        } else {
            renderDailyQuest(g, mouseX, mouseY);
        }

        // Close button
        boolean closeHov = isIn(mouseX, mouseY, closeX, closeY, closeW2, closeH2);
        closeScale = closeHov ? Math.min(closeScale + 0.04f, 1.2f) : Math.max(1.0f, closeScale - 0.04f);
        float cs = s4 * closeScale;
        int cdx = closeX + closeW2 / 2 - Math.round(CLOSE_W * cs / 2);
        int cdy = closeY + closeH2 / 2 - Math.round(CLOSE_H * cs / 2);
        CommonGuiTextures.drawCloseButton(g, cdx, cdy, cs);

        // Tab indicators — 用加粗 Component 代替 drawShadow（shadow 边缘在高 guiScale 下会变粗变脏）
        int tabY = windowY - Math.round(32 * s4);
        Component calLabel = Component.translatable("gui.stardewcraft.billboard.calendar")
            .withStyle(net.minecraft.ChatFormatting.BOLD);
        Component questLabel = Component.translatable("gui.stardewcraft.billboard.daily_quest")
            .withStyle(net.minecraft.ChatFormatting.BOLD);
        int calLabelX = windowX + Math.round(32 * s4);
        int questLabelX = windowX + windowW - font.width(questLabel) - Math.round(32 * s4);

        g.drawString(font, calLabel, calLabelX, tabY, currentTab == 0 ? 0xFFFFD700 : 0xFFAAAAAA, false);
        g.drawString(font, questLabel, questLabelX, tabY, currentTab == 1 ? 0xFFFFD700 : 0xFFAAAAAA, false);
    }

    /**
     * SDV parity — 日历面板严格按 Billboard.cs:414-457 像素对齐。
     *
     * 关键 SDV 坐标（单位：screen px，= SDV px × 4）：
     * - 背景 billboardTexture[0,198,301,198] 在 (xPos, yPos) scale 4
     * - 季节名 dialogueFont 绘制于 (xPos+160, yPos+80)
     * - 年份字符串于 (xPos+448, yPos+80)
     * - 日格 bounds = (xPos+152 + col*128, yPos+200 + row*128, 124, 124) — 每格 124 screen px，
     *   间距 128 screen px（即 32 SDV px），首格左上角位于 (xPos+152, yPos+200)
     * - 生日 mugshot 绘制于 cell.X+48, cell.Y+28，scale 4
     * - 过去日期覆盖 staminaRect @ cell.bounds，Color.Gray * 0.25
     * - 今日高亮 drawTextureBox(mouseCursors[379,357,3,3], cell.X-o, cell.Y-o,
     *       cell.W+2o, cell.H+2o, Color.Blue, scale 4, shadow=false)
     */
    private void renderCalendar(GuiGraphics g, int mouseX, int mouseY) {
        // ── 背景 ──
        drawFull(g, LocalizedGuiAssets.texture("billboard/calendar_background.png"),
                windowX, windowY, CAL_W, CAL_H, s4);

        int currentDay = getCurrentDay();
        String currentSeason = StardewTimeHud.getClientTimeCache().getSeasonName().toLowerCase();
        Map<Integer, String> birthdaysByDay = buildBirthdayMap(currentSeason);
        Map<Integer, List<CalendarFestivalEvent>> festivalsByDay = buildFestivalMap(
            StardewTimeHud.getClientTimeCache().getCurrentSeason());

        // ── 季节名 + 年份标签（对应 SDV Billboard.cs:416-417） ──
        int seasonX = windowX + Math.round(160 * s4 / 4);
        int seasonY = windowY + Math.round(80 * s4 / 4);
        int yearX = windowX + Math.round(448 * s4 / 4);

        Component seasonLabel = Component.translatable("stardewcraft.season." + currentSeason)
            .withStyle(net.minecraft.ChatFormatting.BOLD);
        Component yearLabel = Component.translatable("stardewcraft.gui.billboard.year",
            StardewTimeHud.getClientTimeCache().getCurrentYear())
            .withStyle(net.minecraft.ChatFormatting.BOLD);
        g.drawString(font, seasonLabel, seasonX, seasonY, TEXT_COLOR, false);
        g.drawString(font, yearLabel, yearX, seasonY, TEXT_COLOR, false);

        // ── 日格参数（SDV pixel-perfect） ──
        // SDV: cell bounds.X = xPos + 152 + col*128 screen px. 我们用 s4 = (4 / guiScale)
        // 换算成 MC draw px：round((152 + col*128) * s4/4)
        int gridOffX = Math.round(152 * s4 / 4);
        int gridOffY = Math.round(200 * s4 / 4);
        int cellStride = Math.round(128 * s4 / 4);   // 每格间距（SDV: 128 screen px = 32 SDV px）
        int cellSize = Math.round(124 * s4 / 4);     // 每格实际大小（SDV: 124 screen px = 31 SDV px）

        for (int day = 1; day <= 28; day++) {
            int idx = day - 1;
            int col = idx % GRID_COLS;
            int row = idx / GRID_COLS;
            int cx = windowX + gridOffX + col * cellStride;
            int cy = windowY + gridOffY + row * cellStride;

            // ── 生日 mugshot（SDV 425：cell.X+48, cell.Y+28 screen px, scale 4） ──
            String birthdayNpc = birthdaysByDay.get(day);
            if (birthdayNpc != null) {
                int mugX = cx + Math.round(48 * s4 / 4);
                int mugY = cy + Math.round(28 * s4 / 4);
                // SDV source 16×24，绘制 scale 4 → 64×96 screen px
                int mugW = Math.round(16 * s4);
                int mugH = Math.round(24 * s4);
                drawNpcMugshotRect(g, birthdayNpc, mugX, mugY, mugW, mugH);
            }

            // SDV Billboard.cs: passive star, active festival flag, then fishing derby fish.
            List<CalendarFestivalEvent> festivalEvents = festivalsByDay.getOrDefault(day, List.of());
            EnumSet<CalendarFestivalType> festivalTypes = EnumSet.noneOf(CalendarFestivalType.class);
            for (CalendarFestivalEvent event : festivalEvents) {
                festivalTypes.add(event.type());
            }
            int pulseOffset = calendarPulseOffset();
            if (festivalTypes.contains(CalendarFestivalType.PASSIVE)) {
                CalendarFestivalEvent passive = festivalEvents.stream()
                    .filter(event -> event.type() == CalendarFestivalType.PASSIVE)
                    .findFirst()
                    .orElseThrow();
                drawCalendarIconWithShadow(g, CURSORS_TEXTURE,
                    cx + Math.round(3 * s4), cy + Math.round(15 * s4) - pulseOffset,
                    346, 392, 8, 8, CURSORS_TEXTURE_W, CURSORS_TEXTURE_H, passive.locked());
            }
            if (festivalTypes.contains(CalendarFestivalType.ACTIVE)) {
                int frame = (int) (net.minecraft.Util.getMillis() % 600L / 100L);
                drawCalendarIconWithShadow(g, BILLBOARD_TEXTURE,
                    cx + Math.round(10 * s4), cy + Math.round(14 * s4) - pulseOffset,
                    1 + frame * 14, 398, 14, 12,
                    BILLBOARD_TEXTURE_W, BILLBOARD_TEXTURE_H, false);
            }
            if (festivalTypes.contains(CalendarFestivalType.FISHING_DERBY)) {
                drawCalendarIconWithShadow(g, CURSORS_1_6_TEXTURE,
                    cx + Math.round(2 * s4), cy + Math.round(15 * s4) - pulseOffset,
                    103, 2, 10, 11, CURSORS_1_6_TEXTURE_W, CURSORS_1_6_TEXTURE_H, false);
            }

            // ── 每日任务完成星标（本模组扩展，SDV 无此功能） ──
            if (ClientQuestData.isDailyQuestCompletedOnDay(day)) {
                int starX = cx + cellSize - Math.round(STAR_W * s4) - Math.round(s4 / 4);
                int starY = cy + cellSize - Math.round(STAR_H * s4) - Math.round(s4 / 4);
                drawFull(g, QUEST_DONE_STAR, starX, starY, STAR_W, STAR_H, s4);
            }

            // ── 过去日期灰盖（SDV 448-451：staminaRect @ cell.bounds, Color.Gray * 0.25） ──
            if (currentDay > day) {
                // 0x40808080 = alpha 25% + RGB 灰
                g.fill(cx, cy, cx + cellSize, cy + cellSize, 0x40808080);
            }
            // ── 今日高亮（SDV 452-456：9-slice mouseCursors[379,357,3,3], Color.Blue） ──
            else if (currentDay == day) {
                // SDV 源是 3×3 tile pattern（每 1 SDV px 一角），drawTextureBox 内部 cornerSize = srcW/3
                g.setColor(0.35F, 0.35F, 1.0F, 1.0F);
                CommonGuiTextures.drawCalendarTodayBox(g, cx, cy, cellSize, cellSize, s4);
                g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        // ── 日历事件 tooltip（SDV：节日在前，生日在后，每项一行） ──
        for (int day = 1; day <= 28; day++) {
            String bNpc = birthdaysByDay.get(day);
            List<CalendarFestivalEvent> festivalEvents = festivalsByDay.getOrDefault(day, List.of());
            if (bNpc == null && festivalEvents.isEmpty()) continue;
            int idx = day - 1;
            int col = idx % GRID_COLS;
            int row = idx / GRID_COLS;
            int cx = windowX + gridOffX + col * cellStride;
            int cy = windowY + gridOffY + row * cellStride;
            if (mouseX >= cx && mouseX < cx + cellSize && mouseY >= cy && mouseY < cy + cellSize) {
                List<Component> lines = new ArrayList<>();
                for (CalendarFestivalEvent event : festivalEvents) {
                    lines.add(event.locked()
                        ? Component.literal("???")
                        : Component.translatable("stardewcraft.festival.calendar."
                            + event.festivalId().toLowerCase(Locale.ROOT)));
                }
                if (bNpc != null) {
                    lines.add(Component.translatable("stardewcraft.gui.billboard.birthday_tooltip",
                        Component.translatable("entity.stardewcraft.npc." + bNpc)));
                }
                g.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }

    /** 把 mugshot 画到指定矩形内（按源 16×24 比例）。 */
    private void drawNpcMugshotRect(GuiGraphics g, String npcId, int x, int y, int w, int h) {
        ResourceLocation mugTex = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/mugshots/" + npcId.toLowerCase() + ".png");
        g.blit(mugTex, x, y, w, h, 0f, 0f, 16, 24, 16, 24);
    }

    /** Build a map of day → npcId for birthdays in the given season */
    private Map<Integer, String> buildBirthdayMap(String season) {
        Map<Integer, String> result = new java.util.HashMap<>();
        try {
            JsonObject root = NpcDataRegistry.events().get("npc_birthdays");
            if (root == null || !root.has("birthdays")) return result;
            JsonObject birthdays = root.getAsJsonObject("birthdays");
            for (Map.Entry<String, JsonElement> entry : birthdays.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject bd = entry.getValue().getAsJsonObject();
                String bdSeason = bd.has("season") ? bd.get("season").getAsString().toLowerCase() : "";
                int bdDay = bd.has("day") ? bd.get("day").getAsInt() : -1;
                if (bdSeason.equals(season) && bdDay >= 1 && bdDay <= 28) {
                    result.put(bdDay, entry.getKey());
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    /** Build the source-ordered festival events shown by vanilla Billboard.GetEventsForDay. */
    private Map<Integer, List<CalendarFestivalEvent>> buildFestivalMap(int season) {
        Map<Integer, List<CalendarFestivalEvent>> result = new HashMap<>();
        for (FestivalDefinition definition : FestivalRegistry.all()) {
            if (definition.season() != season) continue;
            if (!com.stardew.craft.client.ClientFestivalAvailability.allows(definition.id())) continue;

            CalendarFestivalType type;
            if (definition.type() == FestivalType.ACTIVE) {
                type = CalendarFestivalType.ACTIVE;
            } else if (definition.showOnCalendar()) {
                type = CalendarFestivalType.PASSIVE;
            } else if (definition.id().equalsIgnoreCase("TroutDerby")
                    || definition.id().equalsIgnoreCase("SquidFest")) {
                type = CalendarFestivalType.FISHING_DERBY;
            } else {
                continue;
            }

            boolean locked = type == CalendarFestivalType.PASSIVE && isCalendarFestivalLocked(definition);
            for (int day = definition.startDay(); day <= definition.endDay(); day++) {
                result.computeIfAbsent(day, ignored -> new ArrayList<>())
                    .add(new CalendarFestivalEvent(type, definition.id(), locked));
            }
        }
        return result;
    }

    private boolean isCalendarFestivalLocked(FestivalDefinition definition) {
        if (!definition.id().equalsIgnoreCase("DesertFestival")) {
            return false;
        }
        return !ClientPlayerDataCache.hasMailFlag(CCStoryFlags.CC_VAULT)
            && !ClientPlayerDataCache.hasMailFlag(CCStoryFlags.JOJA_VAULT);
    }

    private int calendarPulseOffset() {
        double phase = net.minecraft.Util.getMillis() % 1570L / 500.0D;
        return Math.round((float) (2.0D * s4 * Math.sin(phase)));
    }

    /** SDV Utility.drawWithShadow: shadow at (-4,+4) screen px, then the source-colored icon. */
    private void drawCalendarIconWithShadow(
            GuiGraphics g,
            ResourceLocation texture,
            int x,
            int y,
            int sourceX,
            int sourceY,
            int sourceW,
            int sourceH,
            int textureW,
            int textureH,
            boolean locked) {
        int drawW = Math.round(sourceW * s4);
        int drawH = Math.round(sourceH * s4);
        int shadowOffset = Math.max(1, Math.round(s4));
        g.setColor(0.0F, 0.0F, 0.0F, locked ? 0.105F : 0.35F);
        g.blit(texture, x - shadowOffset, y + shadowOffset, drawW, drawH,
            sourceX, sourceY, sourceW, sourceH, textureW, textureH);
        g.setColor(locked ? 0.0F : 1.0F, locked ? 0.0F : 1.0F, locked ? 0.0F : 1.0F,
            locked ? 0.3F : 1.0F);
        g.blit(texture, x, y, drawW, drawH,
            sourceX, sourceY, sourceW, sourceH, textureW, textureH);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private enum CalendarFestivalType {
        ACTIVE,
        FISHING_DERBY,
        PASSIVE
    }

    private record CalendarFestivalEvent(CalendarFestivalType type, String festivalId, boolean locked) {
    }


    /**
     * SDV parity — 每日任务面板严格按 Billboard.cs:459-494 像素对齐。
     *
     * 关键 SDV 坐标（单位：screen px, = SDV px × 4）：
     * - 背景 billboardTexture[0,0,338,198] 在 (xPos, yPos) scale 4
     * - "Nothing posted" 文本于 (xPos+384, yPos+320) — SDV 88 px, 56 py
     * - 任务描述：parseText(desc, dialogueFont, 640) 于 (xPos+352, yPos+256) — wrap 640 screen px
     * - 接受按钮 9-slice mouseCursors[403,373,9,9]
     *   按钮 bounds = (xPos+width/2-128, yPos+height-128, textW+24, textH+24)
     *   按钮文字 (btn.X+12, btn.Y+16)
     * - 星星：billboardTexture[140,397,10,11]
     *   绘制于 (xPos + (18+12*j)*4, yPos + 36*4) for j=0..2
     */
    private void renderDailyQuest(GuiGraphics g, int mouseX, int mouseY) {
        // ── 背景 ──
        drawFull(g, LocalizedGuiAssets.texture("billboard/daily_quest_background.png"),
                windowX, windowY, QUEST_W, QUEST_H, s4);

        StardewQuest daily = ClientQuestData.getDailyQuest();

        // ── 没有任务 → "今日公告栏没有新任务" ──
        if (daily == null) {
            // SDV: (xPos+384, yPos+320) screen px = SDV (96, 80) from window corner
            int nothingX = windowX + Math.round(384 * s4 / 4);
            int nothingY = windowY + Math.round(320 * s4 / 4);
            Component nothing = Component.translatable("gui.stardewcraft.billboard.no_quest")
                .withStyle(net.minecraft.ChatFormatting.BOLD);
            g.drawString(font, nothing, nothingX, nothingY, TEXT_COLOR, false);
            drawStars(g, 0);
            return;
        }

        boolean alreadyAccepted = daily.isAccepted()
                || daily.isCompleted()
                || ClientQuestData.hasQuest(daily.getId());

        // ── 任务描述（SDV 471-473） ──
        // SDV: new Vector2(xPos + 320 + 32, yPos + 256) = (xPos+352, yPos+256) screen px
        int descX = windowX + Math.round(352 * s4 / 4);
        int descY = windowY + Math.round(256 * s4 / 4);
        int descW = Math.round(640 * s4 / 4);   // SDV wrap 640 screen px
        List<FormattedCharSequence> descLines = font.split(daily.getDescriptionComponent(), descW);
        for (FormattedCharSequence line : descLines) {
            g.drawString(font, line, descX, descY, TEXT_COLOR, false);
            descY += font.lineHeight + 2;
        }

        // 进度（非原版 Billboard 标准 — 原版在 QuestLog 显示目标，但我们让玩家能直接在公告栏看进度）
        descY += Math.round(8 * s4 / 4);
        for (Component obj : daily.getObjectiveComponents()) {
            Component line = Component.literal("> ").append(obj)
                .withStyle(net.minecraft.ChatFormatting.BOLD);
            g.drawString(font, line, descX, descY, TEXT_COLOR, false);
            descY += font.lineHeight + 2;
        }

        // 奖励（非原版 Billboard，SDV 奖励在 questComplete 弹窗里显示 — 这里保留给玩家决策参考）
        if (daily.hasMoneyReward()) {
            descY += Math.round(8 * s4 / 4);
            Component rewardLine = Component.translatable("gui.stardewcraft.quest_log.reward")
                .append(Component.literal(": " + daily.getMoneyReward() + "g"))
                .withStyle(net.minecraft.ChatFormatting.BOLD);
            g.drawString(font, rewardLine, descX, descY, MONEY_COLOR, false);
        }

        // ── 接受按钮（SDV 474-479） ──
        if (alreadyAccepted) {
            Component accepted = Component.translatable("gui.stardewcraft.billboard.already_accepted")
                .withStyle(net.minecraft.ChatFormatting.BOLD);
            g.drawCenteredString(font, accepted,
                windowX + windowW / 2,
                acceptY + acceptH2 / 2 - font.lineHeight / 2,
                0xFF888888);
        } else {
            boolean hov = isIn(mouseX, mouseY, acceptX, acceptY, acceptW2, acceptH2);
            // SDV: (scale>1 ? LightPink : White) — hover 时按钮染粉
            if (hov) g.setColor(1.0F, 0.7F, 0.75F, 1.0F);
            // SDV 源 Rectangle(403,373,9,9) = 3×3 tile pattern，每 3 SDV px 一角
            CommonGuiTextures.drawBillboardAcceptBox(g, acceptX, acceptY, acceptW2, acceptH2, s4);
            if (hov) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);

            // SDV: text at (btn.X+12, btn.Y+16) screen px
            Component acceptText = Component.translatable("gui.stardewcraft.billboard.accept")
                .withStyle(net.minecraft.ChatFormatting.BOLD);
            int textX = acceptX + Math.round(12 * s4 / 4);
            int textY = acceptY + acceptH2 / 2 - font.lineHeight / 2;
            g.drawString(font, acceptText, textX, textY, TEXT_COLOR, false);
        }

        // ── 累计星星（SDV 486-490：billboardQuestsDone % 3，每 3 连击奖励礼包） ──
        int done = ClientQuestData.getBillboardQuestsDone();
        boolean drawAll = done % 3 == 0 && daily.isCompleted();
        drawStars(g, drawAll ? 3 : (done % 3));
    }

    /**
     * 画累计星星 — SDV Billboard.cs:487-490。
     * 位置：(xPos + (18+12*j)*4, yPos + 36*4) screen px, source billboardTexture[140,397,10,11] scale 4.
     */
    private void drawStars(GuiGraphics g, int count) {
        int baseY = windowY + Math.round(36 * s4);      // SDV 36 SDV px = 144 screen px
        for (int j = 0; j < count; j++) {
            int x = windowX + Math.round((18 + 12 * j) * s4);
            drawFull(g, QUEST_DONE_STAR, x, baseY, STAR_W, STAR_H, s4);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        // Close button
        if (isIn(mx, my, closeX, closeY, closeW2, closeH2)) {
            playSound(com.stardew.craft.sound.ModSounds.BIG_DESELECT.get());
            onClose();
            return true;
        }

        // Tab switching via label click（点击区域与 render 保持同样的加粗 Component 宽度）
        int tabY = windowY - Math.round(32 * s4);
        int tabH = font.lineHeight + 4;
        Component calLabel = Component.translatable("gui.stardewcraft.billboard.calendar")
            .withStyle(net.minecraft.ChatFormatting.BOLD);
        Component questLabel = Component.translatable("gui.stardewcraft.billboard.daily_quest")
            .withStyle(net.minecraft.ChatFormatting.BOLD);
        int calLabelX = windowX + Math.round(32 * s4);
        int questLabelX = windowX + windowW - font.width(questLabel) - Math.round(32 * s4);

        if (isIn(mx, my, calLabelX, tabY, font.width(calLabel), tabH) && currentTab != 0) {
            playSound(com.stardew.craft.sound.ModSounds.SMALL_SELECT.get());
            currentTab = 0;
            recalcLayout();
            return true;
        }
        if (isIn(mx, my, questLabelX, tabY, font.width(questLabel), tabH) && currentTab != 1) {
            playSound(com.stardew.craft.sound.ModSounds.SMALL_SELECT.get());
            currentTab = 1;
            recalcLayout();
            return true;
        }

        // Accept button (daily quest tab) — SDV parity Billboard.cs:365 "newArtifact"
        // 只有在：任务存在 + 未接受 + 未完成 + 不在 questLog 里 时才响应点击
        if (currentTab == 1) {
            StardewQuest daily = ClientQuestData.getDailyQuest();
            if (daily != null
                    && !daily.isAccepted()
                    && !daily.isCompleted()
                    && !ClientQuestData.hasQuest(daily.getId())) {
                if (isIn(mx, my, acceptX, acceptY, acceptW2, acceptH2)) {
                    playSound(com.stardew.craft.sound.ModSounds.NEW_ARTIFACT.get());
                    PacketDistributor.sendToServer(new AcceptQuestPayload(daily.getId()));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            currentTab = (currentTab + 1) % 2;
            recalcLayout();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound) {
        if (minecraft != null && minecraft.getSoundManager() != null && sound != null) {
            minecraft.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, 1.0F, 1.0F));
        }
    }

    // ─── 工具方法 ───

    private void drawFull(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, float scale) {
        int dw = Math.round(w * scale);
        int dh = Math.round(h * scale);
        g.blit(texture, x, y, dw, dh, 0.0f, 0.0f, w, h, w, h);
    }

    private static ResourceLocation billboard(String name) {
        return ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/billboard/" + name + ".png");
    }

    private static ResourceLocation gui(String name) {
        return ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/" + name + ".png");
    }

    private int getCurrentDay() {
        return Math.max(1, com.stardew.craft.client.hud.StardewTimeHud.getClientTimeCache().getCurrentDay());
    }

    private boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
