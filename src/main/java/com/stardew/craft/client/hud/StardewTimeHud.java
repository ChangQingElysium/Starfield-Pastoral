package com.stardew.craft.client.hud;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 星露谷物语原版风格HUD
 * 完全按照原版坐标还原
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class StardewTimeHud {
    
    // 纹理资源
    @SuppressWarnings("null")
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/background.png");
    @SuppressWarnings("null")
    private static final ResourceLocation POINTER = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/cursor.png");
    @SuppressWarnings("null")
    private static final ResourceLocation SEASON_SPRING = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/spring.png");
    @SuppressWarnings("null")
    private static final ResourceLocation SEASON_SUMMER = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/summer.png");
    @SuppressWarnings("null")
    private static final ResourceLocation SEASON_FALL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/fall.png");
    @SuppressWarnings("null")
    private static final ResourceLocation SEASON_WINTER = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/winter.png");
    @SuppressWarnings("null")
    private static final ResourceLocation WEATHER_SUNNY = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/sunny.png");
    @SuppressWarnings("null")
    private static final ResourceLocation WEATHER_RAINY = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/rainy.png");
    @SuppressWarnings("null")
    private static final ResourceLocation WEATHER_STORMY = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/stormy.png");
    @SuppressWarnings("null")
    private static final ResourceLocation WEATHER_SNOWY = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/snowy.png");
    @SuppressWarnings("null")
    private static final ResourceLocation WEATHER_WINDY_SPRING = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/windy_spring.png");
    @SuppressWarnings("null")
    private static final ResourceLocation WEATHER_WINDY_FALL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/windy_fall.png");
    private static final ResourceLocation VANILLA_CURSORS = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/cursors.png");
    @SuppressWarnings("null")
    private static final ResourceLocation CALICO_RATING_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/desert_festival/calico_rating_icon.png");
    
    // UI尺寸
    private static final int BG_WIDTH = StardewHudLayout.TIME_BG_WIDTH;
    private static final int BG_HEIGHT = StardewHudLayout.TIME_BG_HEIGHT;
    private static final int POINTER_WIDTH = 7;
    private static final int POINTER_HEIGHT = 19;
    private static final int ICON_WIDTH = 12;
    private static final int ICON_HEIGHT = 8;
    private static final int CALICO_RATING_ICON_WIDTH = 19;
    private static final int CALICO_RATING_ICON_HEIGHT = 21;
    
    // 指针旋转中心（在背景图内的坐标）
    private static final int POINTER_PIVOT_X = 22;  // 右移3格
    private static final int POINTER_PIVOT_Y = 21;  // 下移1格
    
    // 图标位置（在背景图内的坐标）
    private static final int WEATHER_X = 29;
    private static final int WEATHER_Y = 16;
    private static final int SEASON_X = 53;
    private static final int SEASON_Y = 16;
    
    private static StardewTimeManager clientTimeCache = new StardewTimeManager();
    private static volatile boolean timeSyncedFromServer = false;
    private static MoneyDial moneyDial = new MoneyDial(8, true);
    private static int moneyShakeTimer = 0;
    private static int desertFestivalMineRating = 0;
    private static int desertFestivalMineRatingShakeTimer = 0;
    private static boolean fairFishingHudActive = false;
    private static int fairFishingRemainingMs = 0;
    private static int fairFishingScore = 0;
    private static boolean iceFishingHudActive = false;
    private static int iceFishingRemainingMs = 0;
    private static int iceFishingFishCaught = 0;
    @SuppressWarnings("unused")
    private static boolean moneyInitialized = false;
    
    // 对标 SDV DayTimeMoneyBox.timeShakeTimer — 深夜时钟抖动
    private static int timeShakeTimer = 0;

    public static void updateClientTime(StardewTimeManager timeData) {
        clientTimeCache = timeData;
        timeSyncedFromServer = true;
    }

    public static boolean isTimeSynced() {
        return timeSyncedFromServer;
    }

    public static void resetTimeSync() {
        timeSyncedFromServer = false;
        fairFishingHudActive = false;
        iceFishingHudActive = false;
        FestivalCurrencyHudState.reset();
    }
    
    public static void updateClientMoney(int money) {
        // MoneyDial会在draw时自动处理动画，这里只需要标记已初始化
        moneyInitialized = true;
    }
    
    public static void triggerMoneyShake() {
        moneyShakeTimer = 100;
    }

    /** SDV parity: dayTimeMoneyBox.moneyShakeTimer = millis (e.g. 1000 ms for insufficient funds). */
    public static void triggerMoneyShake(int millis) {
        moneyShakeTimer = Math.max(moneyShakeTimer, millis);
    }

    public static void updateDesertFestivalMineRating(int displayRating, boolean shake) {
        desertFestivalMineRating = Math.max(0, displayRating);
        if (shake) {
            desertFestivalMineRatingShakeTimer = Math.max(desertFestivalMineRatingShakeTimer, 1500);
        }
    }
    
    public static StardewTimeManager getClientTimeCache() {
        return clientTimeCache;
    }
    
    /**
     * 触发时钟抖动效果（对标 SDV dayTimeMoneyBox.timeShakeTimer = 2000）
     * 2000ms ≈ 40 ticks
     */
    public static void triggerTimeShake() {
        timeShakeTimer = 2000;
    }
    
    /**
     * 根据季节编号获取对应的季节图标
     * @param season 季节编号 (0=春季, 1=夏季, 2=秋季, 3=冬季)
     * @return 对应季节的ResourceLocation
     */
    private static ResourceLocation getSeasonIcon(int season) {
        return switch (season) {
            case 0 -> SEASON_SPRING;
            case 1 -> SEASON_SUMMER;
            case 2 -> SEASON_FALL;
            case 3 -> SEASON_WINTER;
            default -> SEASON_SPRING; // 默认春季
        };
    }
    
    /**
     * 根据天气类型获取对应的天气图标
     * @param weather 天气类型 (Sun, Rain, Storm, Snow, WindSpring, WindFall, Festival)
     * @return 对应天气的ResourceLocation
     */
    private static ResourceLocation getWeatherIcon(String weather) {
        return switch (weather) {
            case "Rain" -> WEATHER_RAINY;
            case "Storm" -> WEATHER_STORMY;
            case "Snow" -> WEATHER_SNOWY;
            case "WindSpring" -> WEATHER_WINDY_SPRING;
            case "WindFall" -> WEATHER_WINDY_FALL;
            case "Festival" -> WEATHER_SUNNY; // 节日用晴天图标
            default -> WEATHER_SUNNY; // 默认晴天
        };
    }
    
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen instanceof StardewHudLayoutEditorScreen) {
            return;
        }
        @SuppressWarnings("null")
        boolean spectator = mc.player.isSpectator();
        if (mc.options.hideGui || spectator) {
            return;
        }
        
        @SuppressWarnings("null")
        boolean isStardewDimension = mc.level.dimension() == ModDimensions.STARDEW_VALLEY 
            || mc.level.dimension() == com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING;
        if (!isStardewDimension) {
            return;
        }

        if (com.stardew.craft.client.hud.FestivalHudState.hidden()
                && !FestivalCurrencyHudState.active()
                && !isCasinoCurrencyActive()) {
            renderFairFishingHud(event.getGuiGraphics());
            renderIceFishingHud(event.getGuiGraphics());
            return;
        }
        
        renderStardewHUD(event.getGuiGraphics());
        renderFairFishingHud(event.getGuiGraphics());
        renderIceFishingHud(event.getGuiGraphics());
    }
    
    @SuppressWarnings("null")
    private static void renderStardewHUD(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        StardewHudLayout.Placement placement = StardewHudLayout.current(screenWidth, screenHeight);
        renderMainHudAt(graphics, placement.x(), placement.y(), placement.scale());
        renderAttachedCurrency(graphics, placement);
        renderDesertFestivalMineRating(graphics);
    }

    static void renderPreview(GuiGraphics graphics, int x, int y, float scale) {
        renderMainHudAt(graphics, x, y, scale);
    }

    private static void renderMainHudAt(GuiGraphics graphics, int x, int y, float renderScale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(renderScale, renderScale, 1.0F);
        try {
            // 1. 渲染背景
            graphics.blit(BACKGROUND, 0, 0, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT);

            // 2. 渲染天气图标（位置29,16）
            Minecraft mc = Minecraft.getInstance();
            String currentWeather = com.stardew.craft.weather.ClientWeatherCache.getCurrentWeather(mc.level.dimension());
            ResourceLocation weatherIcon = getWeatherIcon(currentWeather);
            graphics.blit(weatherIcon, WEATHER_X, WEATHER_Y, 0, 0,
                    ICON_WIDTH, ICON_HEIGHT, ICON_WIDTH, ICON_HEIGHT);

            // 3. 渲染季节图标（位置53,16）
            ResourceLocation seasonIcon = getSeasonIcon(clientTimeCache.getCurrentSeason());
            graphics.blit(seasonIcon, SEASON_X, SEASON_Y, 0, 0,
                    ICON_WIDTH, ICON_HEIGHT, ICON_WIDTH, ICON_HEIGHT);

            // 4. 渲染旋转指针
            renderPointer(graphics, 0, 0);

            // 5. 渲染文字信息
            renderText(graphics, 0, 0, mc.font);
        } finally {
            graphics.pose().popPose();
        }
    }
    
    /**
     * 渲染时钟指针
     * 旋转中心在(19, 20)，从0度逆时针转到180度
     * 6:00 AM = 0度（正着），2:00 AM = 180度（倒着）
     */
    @SuppressWarnings("null")
    private static void renderPointer(GuiGraphics graphics, int hudX, int hudY) {
        float angle = calculatePointerAngle();
        
        graphics.pose().pushPose();
        
        // 移动到指针底部旋转中心
        graphics.pose().translate(
            hudX + POINTER_PIVOT_X, 
            hudY + POINTER_PIVOT_Y, 
            0
        );
        
        // 逆时针旋转（负角度）
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-angle));
        
        // 绘制指针（底部中心对齐）
        graphics.blit(POINTER, 
            -POINTER_WIDTH / 2, -POINTER_HEIGHT,  // 底部中心对齐
            0, 0, 
            POINTER_WIDTH, POINTER_HEIGHT, 
            POINTER_WIDTH, POINTER_HEIGHT);
        
        graphics.pose().popPose();
    }
    
    /**
     * 计算指针角度
     * 6:00 AM (360分钟) = 180度（反向起点）
     * 2:00 AM (1560分钟) = 0度（正向终点）
     * 顺时针旋转180度
     */
    private static float calculatePointerAngle() {
        int currentTime = clientTimeCache.getCurrentTime();
        
        int offset = currentTime - 360;  // 从6:00 AM开始
        if (offset < 0) offset = 0;
        if (offset > 1200) offset = 1200;
        
        // 从180度开始，顺时针转到0度
        return 180.0f - (offset / 1200.0f) * 180.0f;
    }
    
    /**
     * 渲染文字信息：日期、时间、金钱
     */
    private static void renderText(GuiGraphics graphics, int hudX, int hudY, Font font) {
        // 获取时间数据
        int currentTime = clientTimeCache.getCurrentTime();
        int currentDay = clientTimeCache.getCurrentDay();
        
        // 1. 日期显示 "X日 星期X" (27,5 到 65,12) 黑色细体，缩小0.7倍居中
        String weekdayName = getWeekdayName(currentDay);
        String dateStr = net.minecraft.client.resources.language.I18n.get("stardewcraft.hud.date_format", currentDay, weekdayName);
        graphics.pose().pushPose();
        graphics.pose().scale(0.7f, 0.7f, 1.0f);
        @SuppressWarnings("null")
        int dateWidth = font.width(dateStr);
        int dateAreaLeft = (int)((hudX + 27) / 0.7f);
        int dateAreaWidth = (int)(38 / 0.7f);  // 区域宽度 65-27=38
        int dateCenterX = dateAreaLeft + (dateAreaWidth - dateWidth) / 2;
        int dateY = (int)((hudY + 8) / 0.7f);  // 区域高度8像素，从5到12，中心约8
        graphics.drawString(font, dateStr, dateCenterX, dateY, 0xFF000000, false);
        graphics.pose().popPose();
        
        // 2. 时间显示 "XX:XX" (27,28 到 65,34) 居中，整10分钟显示
        // 对标 SDV: timeOfDay >= 2400 时时钟文字变红 + 抖动
        int hours = (currentTime / 60) % 24;
        int minutes = currentTime % 60;
        int displayMinutes = minutes - (minutes % 10);  // 向下取整到10的倍数
        String timeStr = String.format("%02d:%02d", hours, displayMinutes);
        
        // 午夜(0:00)后时间文字变红，对标 SDV: (Game1.timeOfDay >= 2400) ? Color.Red : textColor
        boolean isLateNight = currentTime >= 1440; // 1440分钟 = 24:00 = 0:00 AM
        int timeColor = isLateNight ? 0xFFFF0000 : 0xFF000000;
        // SDV DayTimeMoneyBox.draw parity: while shouldTimePass() is false, the time text spends
        // one second at full brightness and one second at 50% intensity. A black screen fade is
        // explicitly excluded by the original nofade condition.
        boolean blackFadeActive = com.stardew.craft.cutscene.runtime.EventScreenFade.isActive();
        timeColor = pausedTimeTextColor(
            timeColor,
            com.stardew.craft.client.StardewClientTimeState.isTimeFrozenCurrentLevel(),
            blackFadeActive,
            Util.getMillis()
        );
        
        // 更新抖动计时器
        if (timeShakeTimer > 0) {
            timeShakeTimer -= (int)(Minecraft.getInstance().getTimer().getRealtimeDeltaTicks() * 50);
        }
        
        // 计算时钟抖动偏移（对标 SDV: random ±2px）
        int timeShakeX = (timeShakeTimer > 0) ? (int)(Math.random() * 5 - 2) : 0;
        int timeShakeY = (timeShakeTimer > 0) ? (int)(Math.random() * 5 - 2) : 0;
        
        graphics.pose().pushPose();
        graphics.pose().scale(0.7f, 0.7f, 1.0f);
        @SuppressWarnings("null")
        int timeWidth = font.width(timeStr);
        int timeAreaLeft = (int)((hudX + 27) / 0.7f);
        int timeAreaWidth = (int)(38 / 0.7f);  // 区域宽度 65-27=38
        int timeCenterX = timeAreaLeft + (timeAreaWidth - timeWidth) / 2 + timeShakeX;
        int timeY = (int)((hudY + 31) / 0.7f) + timeShakeY;  // 区域高度6像素，从28到34，中心约31
        graphics.drawString(font, timeStr, timeCenterX, timeY, timeColor, false);
        graphics.pose().popPose();
        
        // 3. 金钱显示 - 使用 MoneyDial 实现滚动和抖动效果
        // MoneyDial 的值由 ClientPlayerDataCache.updateFromNBT 直接设置
        
        // 更新抖动计时器
        if (moneyShakeTimer > 0) {
            moneyShakeTimer -= (int)(Minecraft.getInstance().getTimer().getRealtimeDeltaTicks() * 50);
        }
        
        // 计算抖动偏移
        int shakeX = (moneyShakeTimer > 0) ? (int)(Math.random() * 7 - 3) : 0;
        int shakeY = (moneyShakeTimer > 0) ? (int)(Math.random() * 7 - 3) : 0;
        
        // 使用你给的坐标：金币框从(17, 46)开始
        float moneyX = hudX + 17 + shakeX;
        float moneyY = hudY + 46 + shakeY;
        
        // 从缓存中获取金币值并传递给MoneyDial
        int currentMoney = ClientPlayerDataCache.getMoney();
        moneyDial.draw(graphics, moneyX, moneyY, currentMoney);

    }

    static int pausedTimeTextColor(int baseColor, boolean timeFrozen, boolean blackFadeActive, long elapsedMillis) {
        boolean fullBrightness = !timeFrozen || blackFadeActive || elapsedMillis % 2000L > 1000L;
        if (fullBrightness) {
            return baseColor;
        }
        int red = ((baseColor >>> 16) & 0xFF) / 2;
        int green = ((baseColor >>> 8) & 0xFF) / 2;
        int blue = (baseColor & 0xFF) / 2;
        return 0x80000000 | (red << 16) | (green << 8) | blue;
    }

    public static void setFairFishingHudState(boolean active, int remainingMs, int score) {
        fairFishingHudActive = active;
        fairFishingRemainingMs = Math.max(0, remainingMs);
        fairFishingScore = Math.max(0, score);
    }

    public static void setIceFishingHudState(boolean active, int remainingMs, int fishCaught) {
        iceFishingHudActive = active;
        iceFishingRemainingMs = Math.max(0, remainingMs);
        iceFishingFishCaught = Math.max(0, fishCaught);
    }

    private static void renderFairFishingHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!fairFishingHudActive || mc.player == null || mc.level == null || mc.level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        int seconds = Math.max(0, (int) Math.ceil(fairFishingRemainingMs / 1000.0D));
        String time = String.format("%d:%02d", seconds / 60, seconds % 60);
        StardewHudLayout.Placement placement = StardewHudLayout.current(
                Config.HudElement.FESTIVAL_SCORE, graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushPose();
        graphics.pose().translate(placement.x(), placement.y(), 0.0F);
        graphics.pose().scale(placement.scale(), placement.scale(), 1.0F);
        drawBorderedText(graphics, mc.font, I18n.get("stardewcraft.fair.fishing.score", fairFishingScore), 0, 0, 0xFFFFFFFF, 0xFF000000);
        drawBorderedText(graphics, mc.font, I18n.get("stardewcraft.fair.fishing.time", time), 0, 32, 0xFFFFFFFF, 0xFF000000);
        graphics.pose().popPose();
    }

    private static void renderIceFishingHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!iceFishingHudActive || mc.player == null || mc.level == null || mc.level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        int seconds = Math.max(0, (int) Math.ceil(iceFishingRemainingMs / 1000.0D));
        String time = String.format("%d:%02d", seconds / 60, seconds % 60);
        StardewHudLayout.Placement placement = StardewHudLayout.current(
                Config.HudElement.FESTIVAL_SCORE, graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushPose();
        graphics.pose().translate(placement.x(), placement.y(), 0.0F);
        graphics.pose().scale(placement.scale(), placement.scale(), 1.0F);
        drawBorderedText(graphics, mc.font, I18n.get("stardewcraft.festival.ice_fishing.fish_count", iceFishingFishCaught), 0, 0, 0xFFFFFFFF, 0xFF000000);
        drawBorderedText(graphics, mc.font, I18n.get("stardewcraft.festival.ice_fishing.time", time), 0, 32, 0xFFFFFFFF, 0xFF000000);
        graphics.pose().popPose();
    }

    private static void renderAttachedCurrency(GuiGraphics graphics, StardewHudLayout.Placement placement) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || mc.level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        boolean festivalCurrency = FestivalCurrencyHudState.active();
        boolean casinoCurrency = !festivalCurrency && isCasinoCurrencyActive();
        if (!festivalCurrency && !casinoCurrency) {
            return;
        }
        byte type = FestivalCurrencyHudState.currencyType();
        int count = casinoCurrency
                ? ClientPlayerDataCache.getClubCoins()
                : type == FestivalCurrencyHudState.CALICO_EGG
                ? player.getInventory().countItem(ModItems.CALICO_EGG.get())
                : ClientPlayerDataCache.getFairStarTokens();
        String text = String.valueOf(count);
        int boxWidth = Math.max(42, 24 + mc.font.width(text));
        int boxHeight = 16;
        int questButtonX = BG_WIDTH - 11;
        int boxX = questButtonX - 3 - boxWidth;
        int boxY = BG_HEIGHT + 2;
        graphics.pose().pushPose();
        graphics.pose().translate(placement.x(), placement.y(), 0.0F);
        graphics.pose().scale(placement.scale(), placement.scale(), 1.0F);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xBF000000);
        if (casinoCurrency) {
            com.stardew.craft.client.gui.common.CommonGuiTextures.drawQiCoin(
                    graphics, boxX + 5, boxY + 3, 1.0F);
        } else if (type == FestivalCurrencyHudState.FAIR_STAR_TOKEN) {
            graphics.blit(VANILLA_CURSORS, boxX + 5, boxY + 4, 8, 8,
                    338, 400, 8, 8,
                    704, 2256);
        } else {
            graphics.pose().pushPose();
            graphics.pose().translate(boxX + 3, boxY + 2, 0.0F);
            graphics.pose().scale(0.75F, 0.75F, 1.0F);
            graphics.renderItem(new net.minecraft.world.item.ItemStack(ModItems.CALICO_EGG.get()), 0, 0);
            graphics.pose().popPose();
        }
        drawBorderedText(graphics, mc.font, text, boxX + 18, boxY + 4, 0xFFFFFFFF, 0xB0000000);
        graphics.pose().popPose();
    }

    private static boolean isCasinoCurrencyActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null
                || mc.level.dimension() != ModDimensions.STARDEW_VALLEY
                || mc.screen instanceof com.stardew.craft.client.gui.casino.CalicoJackScreen
                || mc.screen instanceof com.stardew.craft.client.gui.casino.SlotsScreen) {
            return false;
        }
        return com.stardew.craft.interior.InteriorRegionRegistry.fixedInteriorAt(
                        mc.player.blockPosition())
                .map(region -> region.id().equals("casino"))
                .orElse(false);
    }

    private static void drawBorderedText(GuiGraphics graphics, Font font, String text, int x, int y, int color, int borderColor) {
        graphics.drawString(font, text, x - 1, y, borderColor, false);
        graphics.drawString(font, text, x + 1, y, borderColor, false);
        graphics.drawString(font, text, x, y - 1, borderColor, false);
        graphics.drawString(font, text, x, y + 1, borderColor, false);
        graphics.drawString(font, text, x, y, color, false);
    }

    private static void renderDesertFestivalMineRating(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null
                || mc.level.dimension() != com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING
                || desertFestivalMineRating <= 0) {
            return;
        }
        int currentFloor = (int) Math.max(0, Math.round(player.getZ() / com.stardew.craft.mining.MiningCoordinates.FLOOR_SPACING));
        if (currentFloor <= 120) {
            return;
        }
        if (desertFestivalMineRatingShakeTimer > 0) {
            desertFestivalMineRatingShakeTimer -= (int)(mc.getTimer().getRealtimeDeltaTicks() * 50);
        }

        int iconW = CALICO_RATING_ICON_WIDTH;
        int iconH = CALICO_RATING_ICON_HEIGHT;
        StardewHudLayout.Placement placement = StardewHudLayout.current(
                Config.HudElement.FESTIVAL_CURRENCY, graphics.guiWidth(), graphics.guiHeight());
        int x = 0;
        int y = (Config.HudElement.FESTIVAL_CURRENCY.baseHeight() - iconH) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(placement.x(), placement.y(), 0.0F);
        graphics.pose().scale(placement.scale(), placement.scale(), 1.0F);
        if (desertFestivalMineRatingShakeTimer > 0) {
            x += (int)(Math.random() * 7 - 3);
            y += (int)(Math.random() * 7 - 3);
            drawCenteredScaledText(graphics, mc.font, "+1", x + iconW / 2, y - 10, 0xFFFFFFFF, 0.75F, true);
        }
        graphics.blit(CALICO_RATING_ICON, x, y, iconW, iconH, 0, 0,
            CALICO_RATING_ICON_WIDTH, CALICO_RATING_ICON_HEIGHT,
            CALICO_RATING_ICON_WIDTH, CALICO_RATING_ICON_HEIGHT);
        String rating = String.valueOf(desertFestivalMineRating);
        drawCenteredScaledText(graphics, mc.font, rating, x + iconW / 2, y + iconH / 2 - 1, 0xFF3F2A13, 0.75F, true);
        graphics.pose().popPose();
    }

    private static void drawCenteredScaledText(GuiGraphics graphics, Font font, String text, int centerX, int centerY,
                                               int color, float scale, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        int x = Math.round((centerX - font.width(text) * scale / 2.0F) / scale);
        int y = Math.round((centerY - font.lineHeight * scale / 2.0F) / scale);
        graphics.drawString(font, text, x, y, color, shadow);
        graphics.pose().popPose();
    }
    
    /**
     * 获取星期名称 (每月1日是周一)
     */
    @SuppressWarnings("null")
    private static String getWeekdayName(int day) {
        String[] weekdayKeys = {
            "stardewcraft.hud.monday",
            "stardewcraft.hud.tuesday", 
            "stardewcraft.hud.wednesday",
            "stardewcraft.hud.thursday",
            "stardewcraft.hud.friday",
            "stardewcraft.hud.saturday",
            "stardewcraft.hud.sunday"
        };
        int index = Math.floorMod(day - 1, 7);
        return net.minecraft.client.resources.language.I18n.get(weekdayKeys[index]);
    }
}
