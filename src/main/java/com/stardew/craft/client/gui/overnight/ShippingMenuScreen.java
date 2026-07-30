package com.stardew.craft.client.gui.overnight;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.sound.StardewMusicManager;
import com.stardew.craft.network.overnight.ClientOvernightHandler;
import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.weather.ClientWeatherCache;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class ShippingMenuScreen extends Screen {
    private static final int INTRO_DURATION = 3500;
    private static final int OUTRO_FADE_DURATION = 800;
    private static final int OUTRO_DATE_PAUSE = 700;
    private static final int SAVE_MARGIN = 500;
    private static final int SAVE_COMPLETE_PAUSE = 1500;
    private static final int FINAL_OUTRO_DURATION = 2000;

    private final List<OvernightSettlementPayload.ShippedItem> shippedItems;
    private final OvernightSettlementPayload.OvernightContext context;

    private int introTimer = INTRO_DURATION;
    private long lastTime;

    private int[] categoryTotals = new int[6];
    private MoneyDial[] categoryDials = new MoneyDial[6];
    
    // Categories: 0: Farming, 1: Foraging, 2: Fishing, 3: Mining, 4: Other, 5: Total
    private List<List<OvernightSettlementPayload.ShippedItem>> categoryItems;

    private int currentPage = -1;
    
    private int currentTab = 0;
    private int itemsPerCategoryPage = 9;
    private boolean outro;
    private int outroFadeTimer;
    private int outroPauseBeforeDateChange;
    private int finalOutroTimer;
    private int saveTimer = -1;
    private int morningSoundTimer = -1;
    private int dayPlaqueY;
    private boolean newDayPlaque;
    private boolean savedYet;
    private boolean saveCompleteSoundPlayed;
    private int sparklingAmplitude = 32;
    private float sparklingOffsetDecay = 1.0F;
    private float sparklingFrameRemainder;
    private float weatherX;
    private int moonShake = -1;
    private int timesPokedMoon;
    private int smokeTimer;
    private final List<SmokeParticle> smokeParticles = new ArrayList<>();
    private final List<AmbientSprite> ambientSprites = new ArrayList<>();
    private float ambientFrameRemainder;
    private boolean ambientInitialized;

    // UI Layout vars
    private int categoryLabelsWidth = 512;
    private int plusButtonWidth = 40;
    private int itemSlotWidth = 96;
    private int itemAndPlusButtonWidth = plusButtonWidth + itemSlotWidth + 8;
    private int totalWidth = categoryLabelsWidth + itemAndPlusButtonWidth;

    private final List<Screen> siblingScreens;

    public ShippingMenuScreen(
            List<OvernightSettlementPayload.ShippedItem> shippedItems,
            OvernightSettlementPayload.OvernightContext context,
            List<Screen> siblingScreens
    ) {
        super(Component.translatable("stardewcraft.shipping.title"));
        this.shippedItems = shippedItems;
        this.context = context;
        this.siblingScreens = siblingScreens;
        this.categoryItems = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            this.categoryItems.add(new ArrayList<>());
            this.categoryDials[i] = new MoneyDial(7, i == 5);
        }
        
        parseItems();
        configureOriginalShippingMusic();
    }

    private void configureOriginalShippingMusic() {
        // Original ShippingMenu: clear summer nights use nightTime; other
        // clear seasons stop music. Rain keeps the weather ambience.
        if (!isRainLikeWeather()) {
            if (context.newSeason() == 1) {
                StardewMusicManager.playForCutscene(ModSounds.MUSIC_SPRING_NIGHT_AMBIENT.get());
            } else {
                StardewMusicManager.stopForCutsceneSilence();
            }
        }
    }

    private void parseItems() {
        for (OvernightSettlementPayload.ShippedItem item : consolidateStacks(shippedItems)) {
            int category = item.category();
            if (category < 0 || category > 4) {
                category = 4; // default to Other
            }
            categoryItems.get(category).add(item);
            int itemTotal = item.pricePerItem() * item.stack().getCount();
            categoryTotals[category] += itemTotal;
        }

        // Mirror Stardew: build the Total bucket after the 0..4 categories are finalized.
        for (int i = 0; i < 5; i++) {
            categoryTotals[5] += categoryTotals[i];
            categoryItems.get(5).addAll(categoryItems.get(i));
            categoryDials[i].currentValue = categoryTotals[i];
            categoryDials[i].previousTargetValue = categoryTotals[i];
        }
        categoryDials[5].currentValue = categoryTotals[5];
        categoryDials[5].previousTargetValue = categoryTotals[5];
    }

    private static List<OvernightSettlementPayload.ShippedItem> consolidateStacks(
            List<OvernightSettlementPayload.ShippedItem> items
    ) {
        List<OvernightSettlementPayload.ShippedItem> consolidated = new ArrayList<>();
        for (OvernightSettlementPayload.ShippedItem item : items) {
            int matchingIndex = -1;
            for (int i = 0; i < consolidated.size(); i++) {
                OvernightSettlementPayload.ShippedItem existing = consolidated.get(i);
                if (existing.category() == item.category()
                        && existing.pricePerItem() == item.pricePerItem()
                        && ItemStack.isSameItemSameComponents(existing.stack(), item.stack())) {
                    matchingIndex = i;
                    break;
                }
            }
            if (matchingIndex < 0) {
                consolidated.add(new OvernightSettlementPayload.ShippedItem(
                    item.stack().copy(), item.category(), item.pricePerItem()));
            } else {
                OvernightSettlementPayload.ShippedItem existing = consolidated.get(matchingIndex);
                ItemStack merged = existing.stack().copy();
                merged.grow(item.stack().getCount());
                consolidated.set(matchingIndex, new OvernightSettlementPayload.ShippedItem(
                    merged, existing.category(), existing.pricePerItem()));
            }
        }
        return List.copyOf(consolidated);
    }

    @Override
    protected void init() {
        super.init();
        this.lastTime = System.currentTimeMillis();
        refreshScaledLayout();
        this.dayPlaqueY = Math.max(px(-64), this.height / 2 + px(-428));
        initializeAmbientSprites();
        com.stardew.craft.StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] ShippingMenuScreen.init() items={}, introTimer={}", shippedItems.size(), introTimer);
    }

    private void initializeAmbientSprites() {
        if (ambientInitialized) {
            return;
        }
        ambientInitialized = true;
        if (context.newDay() == 25 && context.newSeason() == 3) {
            ambientSprites.add(AmbientSprite.cursor(
                640, 800, 32, 16, 2, 80,
                Math.round(this.width * guiScale()),
                ThreadLocalRandom.current().nextInt(0, 200),
                -4.0f, 0.0f
            ).withDelay(3000));
        }
    }

    private float guiScale() {
        return this.minecraft == null ? 1.0f : (float) this.minecraft.getWindow().getGuiScale();
    }

    private int px(int stardewPixels) {
        return Math.round(stardewPixels / guiScale());
    }

    private float s4() {
        return 4.0f / guiScale();
    }

    private void playUiSound(SoundEvent sound, float volume, float pitch) {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, pitch));
        }
    }

    private SoundEvent getCategorySoundEvent(int which) {
        return switch (which) {
            case 0 -> isFarmingAnimalProduct() ? ModSounds.CLUCK.get() : ModSounds.HARVEST.get();
            case 1 -> ModSounds.LEAFRUSTLE.get();
            case 2 -> ModSounds.BUTTON1.get();
            case 3 -> ModSounds.HAMMER.get();
            case 4 -> ModSounds.COIN.get();
            case 5 -> ModSounds.MONEY.get();
            default -> ModSounds.STONE_STEP.get();
        };
    }

    private boolean isFarmingAnimalProduct() {
        if (categoryItems.get(0).isEmpty()) {
            return false;
        }
        ItemStack first = categoryItems.get(0).get(0).stack();
        return "stardewcraft.type.animal_product".equals(
            com.stardew.craft.api.v1.item.StardewItemDataApi.getTypeKey(first));
    }

    private boolean isLeftMousePressed() {
        if (this.minecraft == null) {
            return false;
        }
        long window = this.minecraft.getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private void refreshScaledLayout() {
        this.categoryLabelsWidth = px(512);
        this.plusButtonWidth = px(40);
        this.itemSlotWidth = px(96);
        this.itemAndPlusButtonWidth = px(40 + 96 + 8);
        this.totalWidth = px(512 + 40 + 96 + 8);

        int stardewViewportHeight = Math.round(this.height * guiScale());
        int stardewSpaceHeight = Math.min(stardewViewportHeight, 920);
        float itemSpace = stardewSpaceHeight - 96f;
        this.itemsPerCategoryPage = Math.max(1, (int) (itemSpace / 68f));
        if (currentPage >= 0) {
            int items = categoryItems.get(currentPage).size();
            int maxTab = Math.max(0, (items - 1) / this.itemsPerCategoryPage);
            currentTab = Math.min(currentTab, maxTab);
        }
    }

    @SuppressWarnings("null")
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshScaledLayout();
        graphics.fill(0, 0, this.width, this.height, 0xFF000000);

        long currentTime = System.currentTimeMillis();
        int delta = Math.max(0, Math.min(100, (int) (currentTime - lastTime)));
        this.lastTime = currentTime;

        if (outro) {
            updateOutro(delta);
            if (!ClientOvernightHandler.isSequenceActive()) {
                return;
            }
        } else {
            updateIntro(delta);
        }
        weatherX += delta * 0.03f;
        updateSmoke(delta);
        updateAmbientSprites(delta);
        if (moonShake > 0) {
            moonShake -= delta;
        }

        // Render the complete scene at full opacity, then composite one high-z black layer over
        // it. Vanilla item models/count decorations live at z=150/200 and otherwise punch through
        // a normal z=0 fill regardless of Java call order.
        drawBackground(graphics, 1.0F);

        if (isGreenRainWeather()) {
            graphics.fill(0, 0, this.width, this.height, 0x1900FF00);
        }

        if (currentPage == -1) {
            drawSummaryPage(graphics, mouseX, mouseY);
        } else {
            drawItemDetail(graphics, mouseX, mouseY);
        }

        if (outro) {
            drawOutro(graphics);
        } else {
            drawBlackOverlay(
                graphics,
                ShippingMenuFadeTimeline.FINAL_BLACKOUT_Z,
                ShippingMenuFadeTimeline.introBlackAlpha(introTimer, INTRO_DURATION)
            );
        }
    }

    private void updateIntro(int delta) {
        int prevIntro = introTimer;
        int introSpeed = isLeftMousePressed() ? 3 : 1;
        introTimer -= delta * introSpeed;
        if (prevIntro >= 0 && introTimer >= 0 && prevIntro % 500 < introTimer % 500 && introTimer <= 3000) {
            int categoryThatPoppedUp = 4 - introTimer / 500;
            if (categoryThatPoppedUp > -1 && categoryThatPoppedUp < 6) {
                if (!categoryItems.get(categoryThatPoppedUp).isEmpty()) {
                    playUiSound(getCategorySoundEvent(categoryThatPoppedUp), 1.0f, 1.0f);
                    categoryDials[categoryThatPoppedUp].currentValue = 0;
                    categoryDials[categoryThatPoppedUp].previousTargetValue = 0;
                } else {
                    playUiSound(ModSounds.STONE_STEP.get(), 0.9f, 1.0f);
                }
            }
        }

        if (prevIntro >= 0 && introTimer < 0) {
            playUiSound(ModSounds.MONEY.get(), 1.0f, 1.0f);
            categoryDials[5].currentValue = 0;
            categoryDials[5].previousTargetValue = 0;
        }
    }

    private void updateSmoke(int delta) {
        smokeParticles.removeIf(particle -> particle.update(delta));
        if (outro || introTimer >= 0 || getCurrentDay() == 28) {
            return;
        }

        smokeTimer -= delta;
        while (smokeTimer <= 0) {
            smokeTimer += 50;
            smokeParticles.add(SmokeParticle.create(
                188.0f,
                Math.round(this.height * guiScale()) - 108.0f,
                isRainLikeWeather()
            ));
        }
    }

    private void drawSmoke(GuiGraphics graphics) {
        for (SmokeParticle particle : smokeParticles) {
            particle.draw(graphics, this::px, s4());
        }
    }

    private void updateAmbientSprites(int delta) {
        ambientSprites.removeIf(sprite -> sprite.update(delta));
        if (outro || introTimer >= 0 || getCurrentDay() == 28 || isRainLikeWeather()) {
            return;
        }

        ambientFrameRemainder += delta;
        float frameDuration = 1000.0f / 60.0f;
        while (ambientFrameRemainder >= frameDuration) {
            ambientFrameRemainder -= frameDuration;
            trySpawnAmbientSprite();
        }
    }

    private void trySpawnAmbientSprite() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int viewportWidth = Math.round(this.width * guiScale());
        int viewportHeight = Math.round(this.height * guiScale());
        if (random.nextDouble() < 0.001) {
            boolean flipped = random.nextBoolean();
            AmbientSprite sprite;
            if (random.nextBoolean()) {
                sprite = AmbientSprite.cursor(
                    640, 826, 16, 8, 4, 40,
                    random.nextInt(Math.max(1, viewportWidth)),
                    random.nextInt(Math.max(1, viewportHeight / 2)),
                    flipped ? -8.0f : 8.0f,
                    8.0f
                ).withFlip(flipped).upsideDown();
            } else {
                sprite = AmbientSprite.cursor(
                    258, 1680, 16, 16, 4, 40,
                    random.nextInt(Math.max(1, viewportWidth)),
                    random.nextInt(Math.max(1, viewportHeight / 2)),
                    flipped ? -8.0f : 8.0f,
                    8.0f
                ).withFlip(flipped).upsideDown();
            }
            ambientSprites.add(sprite);
        } else if (random.nextDouble() < 0.0002) {
            ambientSprites.add(AmbientSprite.pixel(
                viewportWidth,
                random.nextInt(4, 256),
                -0.25f,
                0.25f + random.nextFloat()
            ));
        } else if (random.nextDouble() < 0.00005) {
            int rows = random.nextInt(1, 4);
            float startY = viewportHeight - 192.0f;
            for (int row = 0; row < rows; row++) {
                int step = random.nextInt(15, 18);
                ambientSprites.add(AmbientSprite.cursor(
                    640, 752, 16, 16, 4, random.nextInt(60, 101),
                    viewportWidth + (row + 1) * step,
                    startY - (row + 1) * 20,
                    -1.0f, 0.0f
                ).black());
                ambientSprites.add(AmbientSprite.cursor(
                    640, 752, 16, 16, 4, random.nextInt(60, 101),
                    viewportWidth + (row + 1) * step,
                    startY + (row + 1) * 20,
                    -1.0f, 0.0f
                ).black());
            }
        } else if (random.nextDouble() < 0.00001) {
            ambientSprites.add(AmbientSprite.cursor(
                640, 784, 16, 16, 4, 75,
                viewportWidth,
                random.nextInt(Math.max(1, 200)),
                -3.0f, 0.0f
            ).withPeriodicY(8.0f, 1000));
        }
    }

    private void drawAmbientEffects(GuiGraphics graphics) {
        for (AmbientSprite sprite : ambientSprites) {
            sprite.draw(graphics, this::px, s4());
        }
        drawSmoke(graphics);
    }

    private void updateOutro(int delta) {
        if (outroFadeTimer > 0) {
            outroFadeTimer = Math.max(0, outroFadeTimer - delta);
            return;
        }

        int targetY = this.height / 2 - px(64);
        if (dayPlaqueY < targetY) {
            ambientSprites.clear();
            smokeParticles.clear();
            dayPlaqueY = Math.min(targetY,
                dayPlaqueY + Math.max(1, (int) Math.ceil(delta * 0.35f / guiScale())));
            if (dayPlaqueY >= targetY) {
                outroPauseBeforeDateChange = OUTRO_DATE_PAUSE;
            }
            return;
        }

        if (outroPauseBeforeDateChange > 0) {
            outroPauseBeforeDateChange -= delta;
            if (outroPauseBeforeDateChange <= 0) {
                newDayPlaque = true;
                finalOutroTimer = FINAL_OUTRO_DURATION;
                saveTimer = SAVE_MARGIN + SAVE_COMPLETE_PAUSE;
                morningSoundTimer = 1500;
                playUiSound(ModSounds.NEW_RECIPE.get(), 1.0f, 1.0f);
            }
            return;
        }

        if (morningSoundTimer > 0) {
            morningSoundTimer -= delta;
            if (morningSoundTimer <= 0 && context.newSeason() != 3) {
                playUiSound(isCurrentMorningRain()
                    ? ModSounds.RAIN_SOUND.get()
                    : ModSounds.ROOSTER.get(), 1.0f, 1.0f);
            }
        }

        if (saveTimer > 0) {
            int oldTimer = saveTimer;
            saveTimer = Math.max(0, saveTimer - delta);
            if (!saveCompleteSoundPlayed && oldTimer > SAVE_COMPLETE_PAUSE
                    && saveTimer <= SAVE_COMPLETE_PAUSE) {
                saveCompleteSoundPlayed = true;
                playUiSound(ModSounds.MONEY.get(), 1.0f, 1.0f);
            }
            if (saveTimer <= SAVE_COMPLETE_PAUSE) {
                sparklingFrameRemainder += delta;
                while (sparklingFrameRemainder >= 1000.0F / 60.0F) {
                    sparklingFrameRemainder -= 1000.0F / 60.0F;
                    sparklingOffsetDecay -= 0.001F;
                    sparklingAmplitude = (int) (sparklingAmplitude * sparklingOffsetDecay);
                }
            }
            if (saveTimer == 0) {
                savedYet = true;
            }
            return;
        }

        if (savedYet && finalOutroTimer > 0) {
            finalOutroTimer = Math.max(0, finalOutroTimer - delta);
            if (finalOutroTimer == 0) {
                closeToNextScreen();
            }
        }
    }

    private void drawOutro(GuiGraphics graphics) {
        // Cover the outgoing shipping rows above vanilla's z=150 item models and z=200 count
        // decorations. The date plaque is intentionally one layer higher, matching Stardew's
        // transition where the old scene goes black before the plaque moves/changes.
        drawBlackOverlay(
            graphics,
            ShippingMenuFadeTimeline.CONTENT_BLACKOUT_Z,
            ShippingMenuFadeTimeline.outroBlackAlpha(outroFadeTimer, OUTRO_FADE_DURATION)
        );

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, ShippingMenuFadeTimeline.OUTRO_FOREGROUND_Z);
        drawDatePlaque(graphics, newDayPlaque ? getNewDayLabel() : getYesterdayLabel(), dayPlaqueY);
        drawAmbientEffects(graphics);
        drawSaveStatus(graphics);
        graphics.pose().popPose();

        if (savedYet) {
            drawBlackOverlay(
                graphics,
                ShippingMenuFadeTimeline.FINAL_BLACKOUT_Z,
                ShippingMenuFadeTimeline.outroBlackAlpha(finalOutroTimer, FINAL_OUTRO_DURATION)
            );
        }
    }

    private void drawBlackOverlay(GuiGraphics graphics, int z, float alpha) {
        int color = ShippingMenuFadeTimeline.blackArgb(alpha);
        if ((color >>> 24) == 0) {
            return;
        }
        graphics.fill(0, 0, this.width, this.height, z, color);
    }

    private void drawDatePlaque(GuiGraphics graphics, Component text, int y) {
        Component shown = GuiText.ellipsize(this.font, text, Math.max(1, this.width - px(160)));
        int textWidth = this.font.width(shown);
        int textX = this.width / 2 - textWidth / 2;
        CommonGuiTextures.drawScrollBanner(graphics, textX, y - px(12), textWidth, s4());
        graphics.drawString(this.font, shown, textX, y, 0xFF5B5045, false);
    }

    private void drawSaveStatus(GuiGraphics graphics) {
        if (!newDayPlaque || saveTimer < 0 || savedYet) {
            return;
        }
        if (saveTimer > SAVE_COMPLETE_PAUSE) {
            Component text = Component.translatable("stardewcraft.overnight.saving");
            Component shown = GuiText.ellipsize(font, text, Math.max(1, width - px(128)));
            graphics.drawString(font, shown, px(64), height - px(64), 0xFFFFFFFF, false);
        } else {
            SaveGameMenuScreen.drawSparklingSavedText(
                graphics,
                font,
                Component.translatable("stardewcraft.overnight.saved").getString(),
                px(64),
                height - px(64),
                sparklingAmplitude,
                saveTimer,
                px(2),
                Math.max(1, width - px(128))
            );
        }
    }

    private void beginOutroClose() {
        if (outro) {
            return;
        }
        outro = true;
        outroFadeTimer = OUTRO_FADE_DURATION;
        playUiSound(ModSounds.BIG_DESELECT.get(), 1.0f, 1.0f);
        StardewMusicManager.stopForCutsceneSilence();
    }

    private void closeToNextScreen() {
        com.stardew.craft.StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] ShippingMenuScreen.closeToNextScreen() siblingCount={}",
            this.siblingScreens != null ? this.siblingScreens.size() : -1);
        StardewMusicManager.releaseCutsceneOverride();
        if (ClientOvernightHandler.isSequenceActive()) {
            ClientOvernightHandler.openNextScreen("shipping");
        } else if (this.siblingScreens != null && !this.siblingScreens.isEmpty()) {
            this.minecraft.setScreen(this.siblingScreens.remove(0));
        } else {
            super.onClose();
        }
    }

    private boolean canReceiveInput() {
        return introTimer <= 0 && !outro;
    }

    private boolean showForwardButton() {
        if (currentPage < 0 || currentPage >= categoryItems.size()) {
            return false;
        }
        return categoryItems.get(currentPage).size() > itemsPerCategoryPage * (currentTab + 1);
    }

    private void drawBackground(GuiGraphics graphics, float alpha) {
        int w = this.width;
        int h = this.height;
        int day = getCurrentDay();
        boolean isWinter = getCurrentSeason() == 3;
        boolean rainLike = isRainLikeWeather();
        boolean greenRain = isGreenRainWeather();
        int stardewViewWidth = Math.round(w * guiScale());

        if (rainLike) {
            float skyRed;
            float skyGreen;
            float skyBlue;
            if (isWinter) {
                skyRed = 119.0F / 255.0F;
                skyGreen = 136.0F / 255.0F;
                skyBlue = 153.0F / 255.0F;
            } else if (greenRain) {
                skyRed = 144.0F / 255.0F;
                skyGreen = 238.0F / 255.0F;
                skyBlue = 144.0F / 255.0F;
            } else {
                skyRed = 112.0F / 255.0F;
                skyGreen = 128.0F / 255.0F;
                skyBlue = 144.0F / 255.0F;
            }
            ShippingMenuTextures.drawSkyStrip(graphics, w, h, greenRain, skyRed, skyGreen, skyBlue, alpha);

            if (greenRain) {
                ShippingMenuTextures.drawSkyStrip(graphics, w, h, true, 105.0F / 255.0F, 105.0F / 255.0F, 105.0F / 255.0F, alpha * 0.8f);
            }

            for (int x = -px(244); x < w + px(244); x += px(244)) {
                ShippingMenuTextures.drawWeatherCloudTint(graphics, x + px((int) ((weatherX / 2.0f) % 244f)), px(32), s4(),
                        47.0F / 255.0F, 79.0F / 255.0F, 79.0F / 255.0F, alpha);
            }

            for (int i = 0; i < stardewViewWidth; i += 639) {
                if (isWinter) {
                    float winterBackAlpha = Math.max(0.0f,
                        0.25f * (0.5f - (float) Math.max(0, introTimer) / INTRO_DURATION));
                    ShippingMenuTextures.drawLandBackTint(graphics, px(i * 4), h - px(192), true, s4(), 1.0F, 1.0F, 1.0F, winterBackAlpha);
                    ShippingMenuTextures.drawLandFrontTint(graphics, px(i * 4), h - px(128), true, s4(), 1.0F, 1.0F, 1.0F, alpha * 0.5f);
                } else {
                    float lowerBackAlpha = 0.5f - (float) Math.max(0, introTimer) / 3500.0f;
                    ShippingMenuTextures.drawLandBackTint(graphics, px(i * 4), h - px(192), false, s4(), 30.0F / 255.0F, 62.0F / 255.0F, 50.0F / 255.0F, lowerBackAlpha);
                    ShippingMenuTextures.drawLandFrontTint(graphics, px(i * 4), h - px(128), false, s4(), 30.0F / 255.0F, 62.0F / 255.0F, 50.0F / 255.0F, alpha);
                }
            }

            ShippingMenuTextures.drawShippingBin(graphics, px(160), h - px(128) + px(24), s4(), alpha);

            for (int x = -px(244); x < w + px(244); x += px(244)) {
                ShippingMenuTextures.drawWeatherCloudTint(graphics, x + px((int) (weatherX % 244f)), px(-32), s4(),
                        112.0F / 255.0F, 128.0F / 255.0F, 144.0F / 255.0F, alpha * 0.85f);
            }
            if (!outro) {
                drawAmbientEffects(graphics);
            }
            for (int x = -px(244); x < w + px(244); x += px(244)) {
                ShippingMenuTextures.drawWeatherCloudTint(graphics, x + px((int) ((weatherX * 1.5f) % 244f)), px(-128), s4(),
                        119.0F / 255.0F, 136.0F / 255.0F, 153.0F / 255.0F, alpha);
            }
            return;
        }

        // ShippingMenu base sky strip (no-rain)
        ShippingMenuTextures.drawSkyStrip(graphics, w, h, false, 1.0F, 1.0F, 1.0F, alpha);

        if (!rainLike) {
            for (int x = 0; x < w; x += px(2556)) {
                ShippingMenuTextures.drawStarBackdrop(graphics, x, 0, s4(), alpha);
            }

            if (day == 28) {
                int shakeX = 0;
                int shakeY = 0;
                if (moonShake > 0) {
                    shakeX = ThreadLocalRandom.current().nextInt(-1, 2);
                    shakeY = ThreadLocalRandom.current().nextInt(-1, 2);
                }
                ShippingMenuTextures.drawFullMoon(graphics, w - px(176) + px(shakeX), px(4 + shakeY), s4(), alpha);
                if (timesPokedMoon > 10) {
                    long ms = System.currentTimeMillis();
                    boolean blink = (ms % 4000L < 200L) || (ms % 8000L > 7600L && ms % 8000L < 7800L);
                    ShippingMenuTextures.drawMoonFace(graphics, w - px(136) + px(shakeX), px(48 + shakeY), blink, s4(), alpha);
                }
            }
        }

        float distantAlpha = Math.max(0.0f, Math.min(1.0f, 0.65f - Math.max(0, introTimer) / 3500.0f));
        if (isWinter) {
            ShippingMenuTextures.drawLandBackTint(graphics, px(0), h - px(192), true, s4(), 1.0F, 1.0F, 1.0F, distantAlpha * 0.25f);
            ShippingMenuTextures.drawLandBackTint(graphics, px(2556), h - px(192), true, s4(), 1.0F, 1.0F, 1.0F, distantAlpha * 0.25f);
        } else {
            ShippingMenuTextures.drawLandBackTint(graphics, px(0), h - px(192), false, s4(), 0.0F, 20.0F / 255.0F, 40.0F / 255.0F, distantAlpha);
            ShippingMenuTextures.drawLandBackTint(graphics, px(2556), h - px(192), false, s4(), 0.0F, 20.0F / 255.0F, 40.0F / 255.0F, distantAlpha);
        }

        if (isWinter) {
            ShippingMenuTextures.drawLandFrontTint(graphics, px(0), h - px(128), true, s4(), 1.0F, 1.0F, 1.0F, alpha * 0.5f);
            ShippingMenuTextures.drawLandFrontTint(graphics, px(2556), h - px(128), true, s4(), 1.0F, 1.0F, 1.0F, alpha * 0.5f);
        } else {
            ShippingMenuTextures.drawLandFrontTint(graphics, px(0), h - px(128), false, s4(), 0.0F, 32.0F / 255.0F, 20.0F / 255.0F, alpha);
            ShippingMenuTextures.drawLandFrontTint(graphics, px(2556), h - px(128), false, s4(), 0.0F, 32.0F / 255.0F, 20.0F / 255.0F, alpha);
        }

        // Shipping bin icon in background
        ShippingMenuTextures.drawShippingBin(graphics, px(160), h - px(128) + px(24), s4(), alpha);
        if (!outro) {
            drawAmbientEffects(graphics);
        }
    }

    @SuppressWarnings("null")
    private void drawSummaryPage(GuiGraphics graphics, int mouseX, int mouseY) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int firstCategoryY = centerY + px(-300);
        int scrollDrawY = firstCategoryY + px(-128);

        if (scrollDrawY >= 0) {
            drawDatePlaque(graphics, getYesterdayLabel(), scrollDrawY);
        }

        int yOffset = px(-20);
        
        // Loop through 6 categories
        // Farming, Foraging, Fishing, Mining, Other, Total (Total is 5)
        for (int i = 0; i < 6; i++) {
            if (introTimer < 2500 - i * 500) {
                // Coordinates logic
                int plusButtonX = centerX + totalWidth / 2 - plusButtonWidth;
                int plusButtonY = centerY + px(-300 + i * 27 * 4);

                int startX = plusButtonX + px(12);
                int startY = plusButtonY + px(-8);

                // Plus button + slot preview are only visible for non-empty non-total categories.
                if (i < 5 && !categoryItems.get(i).isEmpty()) {
                    boolean hovering = mouseX >= plusButtonX && mouseX <= plusButtonX + plusButtonWidth && mouseY >= plusButtonY && mouseY <= plusButtonY + px(44);
                    ShippingMenuTextures.drawPlusButton(graphics, plusButtonX, plusButtonY, hovering, s4());

                    // Slot frame behind the preview item
                    CommonGuiTextures.drawRewardSlot(graphics, startX + px(-104), startY + yOffset + px(4), s4());

                    ItemStack firstStack = categoryItems.get(i).get(0).stack();
                    CommonGuiTextures.drawItem(graphics, firstStack, startX + px(-88), startY + yOffset + px(16), s4());
                }

                // Texture Box
                int boxX = startX - itemSlotWidth - categoryLabelsWidth + px(-12);
                int boxY = startY + yOffset;
                CommonGuiTextures.drawTextureBoxNoShadow(graphics, boxX, boxY, categoryLabelsWidth, px(104), s4());

                // Name text
                Component catName = getCategoryName(i);
                graphics.drawString(this.font, GuiText.ellipsize(this.font, catName, categoryLabelsWidth - px(40)),
                    boxX + px(20), boxY + px(24), 0x663300, false);

                int dotsX = startX - itemSlotWidth + px(-192 - 24);
                for (int m = 0; m < 6; m++) {
                    ShippingMenuTextures.drawDialDots(graphics, dotsX + px(m * 6 * 4), startY + px(12), s4());
                }

                // Dial
                int dialX = startX - itemSlotWidth + px(-192 - 48 + 4);
                int dialY = startY + px(20);
                categoryDials[i].draw(graphics, dialX, dialY, categoryTotals[i]);

                // Gold coin icon
                int coinX = startX - itemSlotWidth + px(-64 - 4);
                int coinY = startY + px(12);
                ShippingMenuTextures.drawCoin(graphics, coinX, coinY, s4());
            }
        }
        
        if (introTimer <= 0) {
            // Draw OK button
            int okWidth = px(64);
            int okX = centerX + totalWidth / 2 - itemAndPlusButtonWidth + px(32);
            int okY = centerY + px(300 - 64);
            boolean hoveringText = mouseX >= okX && mouseX <= okX + okWidth && mouseY >= okY && mouseY <= okY + okWidth;
            graphics.pose().pushPose();
            if (hoveringText) {
                graphics.pose().translate(okX + okWidth/2f, okY + okWidth/2f, 0);
                graphics.pose().scale(1.1f, 1.1f, 1f);
                graphics.pose().translate(-(okX + okWidth/2f), -(okY + okWidth/2f), 0);
            }
            ShippingMenuTextures.drawOk(graphics, okX, okY, 1.0f / guiScale());
            graphics.pose().popPose();
        }
    }

    private void drawItemDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        int boxwidth = Math.min(this.width, px(1280));
        int boxheight = Math.min(this.height, px(920));
        int xPos = this.width / 2 - boxwidth / 2;
        int yPos = this.height / 2 - boxheight / 2;

        CommonGuiTextures.drawMenuTextureBox(graphics, xPos, yPos, boxwidth, boxheight, 1.0f / guiScale(), true);

        int currentY = yPos + px(32);
        int startX = xPos + px(32);

        List<OvernightSettlementPayload.ShippedItem> items = categoryItems.get(currentPage);
        int startIndex = currentTab * itemsPerCategoryPage;
        int endIndex = Math.min(startIndex + itemsPerCategoryPage, items.size());

        for (int i = startIndex; i < endIndex; i++) {
            OvernightSettlementPayload.ShippedItem item = items.get(i);
            
            CommonGuiTextures.drawItemWithDecorations(graphics, this.font, item.stack(), startX, currentY, s4());
            
            // Draw Subtotal (Name x Price)
            Component itemName = item.stack().getHoverName();
            String subtotalStr = itemName.getString() + " x" + String.format(Locale.ROOT, "%,d", item.pricePerItem());
            int stackTotal = item.pricePerItem() * item.stack().getCount();
            String totalStr = String.format(Locale.ROOT, "%,d", stackTotal);
            
            String dotsAndName = subtotalStr;
            int totalPosX = startX + boxwidth - px(64) - this.font.width(totalStr);
            int nameX = startX + px(64 + 12);
            int nameMaxWidth = Math.max(1, totalPosX - nameX - px(16));
            
            while (this.font.width(dotsAndName + totalStr) < boxwidth - px(192)) {
                dotsAndName += " .";
            }
            
            graphics.drawString(this.font, GuiText.ellipsize(this.font, Component.literal(dotsAndName), nameMaxWidth),
                nameX, currentY + px(12), 0x553311, false);
            graphics.drawString(this.font, totalStr, totalPosX, currentY + px(12), 0x553311, false);
            
            currentY += px(68);
        }

        // Back button
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int backX = centerX - boxwidth / 2 - px(64);
        int backY = centerY + boxheight / 2 - px(48);
        if (backX < 0) {
            backX = xPos + px(32);
        }
        if (backY > this.height - px(32)) {
            backY = this.height - px(80);
        }
        boolean backHover = mouseX >= backX && mouseX <= backX + px(48) && mouseY >= backY && mouseY <= backY + px(44);
        graphics.pose().pushPose();
        if (backHover) {
               graphics.pose().translate(backX + px(24), backY + px(22), 0);
                         graphics.pose().scale(1.125f, 1.125f, 1f);
               graphics.pose().translate(-(backX + px(24)), -(backY + px(22)), 0);
        }
          ShippingMenuTextures.drawBack(graphics, backX, backY, s4());
        graphics.pose().popPose();

        // Forward button
        if (showForwardButton()) {
            int fwX = centerX + boxwidth / 2 + px(8);
            int fwY = centerY + boxheight / 2 - px(48);
            if (fwX > this.width - px(32)) {
                fwX = xPos + boxwidth - px(32) - px(48);
            }
            if (fwY > this.height - px(32)) {
                fwY = this.height - px(80);
            }
            boolean fwHover = mouseX >= fwX && mouseX <= fwX + px(48) && mouseY >= fwY && mouseY <= fwY + px(44);
            graphics.pose().pushPose();
            if (fwHover) {
                  graphics.pose().translate(fwX + px(24), fwY + px(22), 0);
                 graphics.pose().scale(1.125f, 1.125f, 1f);
                  graphics.pose().translate(-(fwX + px(24)), -(fwY + px(22)), 0);
            }
            ShippingMenuTextures.drawForward(graphics, fwX, fwY, s4());
            graphics.pose().popPose();
        }
    }

    private Component getCategoryName(int id) {
        return switch (id) {
            case 0 -> Component.translatable("stardewcraft.shipping.farming");
            case 1 -> Component.translatable("stardewcraft.shipping.foraging");
            case 2 -> Component.translatable("stardewcraft.shipping.fishing");
            case 3 -> Component.translatable("stardewcraft.shipping.mining");
            case 4 -> Component.translatable("stardewcraft.shipping.other");
            case 5 -> Component.translatable("stardewcraft.shipping.total");
            default -> Component.literal("");
        };
    }

    @Override
    public void onClose() {
        beginOutroClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!canReceiveInput()) {
            return true;
        }
        if (button == 0) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            if (currentPage == -1) {
                // Click OK button
                int okWidth = px(64);
                int okX = centerX + totalWidth / 2 - itemAndPlusButtonWidth + px(32);
                int okY = centerY + px(300 - 64);

                if (mouseX >= okX && mouseX <= okX + okWidth && mouseY >= okY && mouseY <= okY + okWidth) {
                    beginOutroClose();
                    return true;
                }
                
                // Check plus buttons
                for (int i = 0; i < 5; i++) {
                    if (categoryItems.get(i).isEmpty()) continue;
                    int plusButtonX = centerX + totalWidth / 2 - plusButtonWidth;
                    int plusButtonY = centerY + px(-300 + i * 27 * 4);
                    if (mouseX >= plusButtonX && mouseX <= plusButtonX + plusButtonWidth && mouseY >= plusButtonY && mouseY <= plusButtonY + px(44)) {
                        playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                        currentPage = i;
                        currentTab = 0;
                        return true;
                    }
                }

                if (getCurrentDay() == 28 && timesPokedMoon <= 10) {
                    int moonX = this.width - px(176);
                    int moonY = px(4);
                    int moonW = px(172);
                    int moonH = px(172);
                    if (mouseX >= moonX && mouseX <= moonX + moonW && mouseY >= moonY && mouseY <= moonY + moonH) {
                        moonShake = 100;
                        timesPokedMoon++;
                        if (timesPokedMoon > 10) {
                            playUiSound(ModSounds.SHADOW_DIE.get(), 1.0f, 1.0f);
                        } else {
                            playUiSound(ModSounds.THUD_STEP.get(), 1.0f, 1.0f);
                        }
                        return true;
                    }
                }
            } else {
                int boxwidth = Math.min(this.width, px(1280));
                int boxheight = Math.min(this.height, px(920));
                int xPos = this.width / 2 - boxwidth / 2;

                // Back button
                int backX = centerX - boxwidth / 2 - px(64);
                int backY = centerY + boxheight / 2 - px(48);
                if (backX < 0) {
                    backX = xPos + px(32);
                }
                if (backY > this.height - px(32)) {
                    backY = this.height - px(80);
                }
                if (mouseX >= backX && mouseX <= backX + px(48) && mouseY >= backY && mouseY <= backY + px(44)) {
                    playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                    if (currentTab == 0) {
                        currentPage = -1;
                    } else {
                        currentTab--;
                    }
                    return true;
                }

                // Forward button
                if (showForwardButton()) {
                    int fwX = centerX + boxwidth / 2 + px(8);
                    int fwY = centerY + boxheight / 2 - px(48);
                    if (fwX > this.width - px(32)) {
                        fwX = xPos + boxwidth - px(32) - px(48);
                    }
                    if (fwY > this.height - px(32)) {
                        fwY = this.height - px(80);
                    }
                    if (mouseX >= fwX && mouseX <= fwX + px(48) && mouseY >= fwY && mouseY <= fwY + px(44)) {
                        playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
                        currentTab++;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!canReceiveInput()) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (currentPage == -1) {
                beginOutroClose();
            } else if (currentTab == 0) {
                currentPage = -1;
                playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
            } else {
                currentTab--;
                playUiSound(ModSounds.SHWIP.get(), 1.0f, 1.0f);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private int getCurrentDay() {
        return context.newDay();
    }

    private int getCurrentSeason() {
        return context.newSeason();
    }

    private boolean isRainLikeWeather() {
        String weather = context.previousWeather();
        return "Rain".equals(weather) || "Storm".equals(weather)
            || "Snow".equals(weather) || "GreenRain".equals(weather);
    }

    private boolean isGreenRainWeather() {
        return "GreenRain".equals(context.previousWeather());
    }

    private boolean isCurrentMorningRain() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return false;
        }
        String weather = ClientWeatherCache.getCurrentWeather(this.minecraft.level.dimension());
        return "Rain".equals(weather) || "Storm".equals(weather) || "GreenRain".equals(weather);
    }

    private Component getYesterdayLabel() {
        return getDateLabel(context.previousDay(), context.previousSeason(), context.previousYear());
    }

    private Component getNewDayLabel() {
        return getDateLabel(context.newDay(), context.newSeason(), context.newYear());
    }

    private Component getDateLabel(int day, int season, int year) {
        Component seasonName = switch (season) {
            case 0 -> Component.translatable("stardewcraft.shipping.season.spring");
            case 1 -> Component.translatable("stardewcraft.shipping.season.summer");
            case 2 -> Component.translatable("stardewcraft.shipping.season.fall");
            case 3 -> Component.translatable("stardewcraft.shipping.season.winter");
            default -> Component.translatable("stardewcraft.shipping.season.spring");
        };
        return Component.translatable("stardewcraft.shipping.yesterday_label", seasonName, day, year);
    }

    @FunctionalInterface
    private interface PixelScaler {
        int scale(int value);
    }

    private static final class SmokeParticle {
        private float x;
        private float y;
        private float velocityX;
        private final float velocityY;
        private float alpha = 1.0f;
        private final boolean rainTint;

        private SmokeParticle(float x, float y, float velocityY, boolean rainTint) {
            this.x = x;
            this.y = y;
            this.velocityY = velocityY;
            this.rainTint = rainTint;
        }

        private static SmokeParticle create(float x, float y, boolean rainTint) {
            float rise = -ThreadLocalRandom.current().nextInt(25, 75) / 100.0f / 4.0f;
            return new SmokeParticle(x, y, rise, rainTint);
        }

        private boolean update(int deltaMs) {
            float frames = deltaMs / (1000.0f / 60.0f);
            x += velocityX * frames;
            y += velocityY * frames;
            velocityX -= 0.001f * frames;
            alpha -= 0.0025f * frames;
            return alpha <= 0.0f;
        }

        private void draw(GuiGraphics graphics, PixelScaler scaler, float scale) {
            float red = rainTint ? 112.0f / 255.0f : 1.0f;
            float green = rainTint ? 128.0f / 255.0f : 1.0f;
            float blue = rainTint ? 144.0f / 255.0f : 1.0f;
            StardewGuiUtil.drawFromCursorsTint(
                graphics,
                scaler.scale(Math.round(x)),
                scaler.scale(Math.round(y)),
                684,
                1075,
                1,
                1,
                scale,
                red,
                green,
                blue,
                Math.max(0.0f, alpha)
            );
        }
    }

    private static final class AmbientSprite {
        private final int sourceX;
        private final int sourceY;
        private final int sourceWidth;
        private final int sourceHeight;
        private final int frameCount;
        private final int frameDurationMs;
        private float x;
        private float y;
        private final float velocityX;
        private final float velocityY;
        private int delayMs;
        private int animationMs;
        private int lifeMs;
        private boolean flipped;
        private boolean rotated;
        private boolean pixel;
        private float red = 1.0f;
        private float green = 1.0f;
        private float blue = 1.0f;
        private float alpha = 1.0f;
        private float periodicRange;
        private int periodicDurationMs;
        private float periodicBaseY;

        private AmbientSprite(
                int sourceX,
                int sourceY,
                int sourceWidth,
                int sourceHeight,
                int frameCount,
                int frameDurationMs,
                float x,
                float y,
                float velocityX,
                float velocityY
        ) {
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.frameCount = frameCount;
            this.frameDurationMs = frameDurationMs;
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.periodicBaseY = y;
        }

        private static AmbientSprite cursor(
                int sourceX,
                int sourceY,
                int sourceWidth,
                int sourceHeight,
                int frameCount,
                int frameDurationMs,
                float x,
                float y,
                float velocityX,
                float velocityY
        ) {
            return new AmbientSprite(
                sourceX, sourceY, sourceWidth, sourceHeight,
                frameCount, frameDurationMs, x, y, velocityX, velocityY);
        }

        private static AmbientSprite pixel(float x, float y, float velocityX, float alpha) {
            AmbientSprite sprite = new AmbientSprite(
                0, 0, 1, 1, 1, Integer.MAX_VALUE,
                x, y, velocityX, 0.0f);
            sprite.pixel = true;
            sprite.alpha = alpha;
            return sprite;
        }

        private AmbientSprite withDelay(int delayMs) {
            this.delayMs = delayMs;
            return this;
        }

        private AmbientSprite withFlip(boolean flipped) {
            this.flipped = flipped;
            return this;
        }

        private AmbientSprite upsideDown() {
            this.rotated = true;
            return this;
        }

        private AmbientSprite black() {
            this.red = 0.0f;
            this.green = 0.0f;
            this.blue = 0.0f;
            return this;
        }

        private AmbientSprite withPeriodicY(float range, int durationMs) {
            this.periodicRange = range;
            this.periodicDurationMs = durationMs;
            this.periodicBaseY = y;
            return this;
        }

        private boolean update(int deltaMs) {
            lifeMs += deltaMs;
            if (delayMs > 0) {
                delayMs = Math.max(0, delayMs - deltaMs);
                return false;
            }

            animationMs += deltaMs;
            float frames = deltaMs / (1000.0f / 60.0f);
            x += velocityX * frames;
            y += velocityY * frames;
            if (periodicDurationMs > 0) {
                y = periodicBaseY
                    + (float) Math.sin(animationMs * Math.PI * 2.0 / periodicDurationMs)
                    * periodicRange;
            }
            return lifeMs > (pixel ? 180_000 : 30_000)
                || x < -256.0f
                || y < -256.0f
                || y > 10_000.0f;
        }

        private void draw(GuiGraphics graphics, PixelScaler scaler, float scale) {
            if (delayMs > 0) {
                return;
            }
            int drawX = scaler.scale(Math.round(x));
            int drawY = scaler.scale(Math.round(y));
            if (pixel) {
                int size = Math.max(1, scaler.scale(4));
                int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
                graphics.fill(drawX, drawY, drawX + size, drawY + size, alphaByte << 24 | 0xFFFFFF);
                return;
            }

            int frame = frameCount <= 1
                ? 0
                : animationMs / Math.max(1, frameDurationMs) % frameCount;
            if (rotated) {
                float width = sourceWidth * scale;
                float height = sourceHeight * scale;
                graphics.pose().pushPose();
                graphics.pose().translate(drawX + width / 2.0f, drawY + height / 2.0f, 0);
                graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0f));
                graphics.pose().translate(-(drawX + width / 2.0f), -(drawY + height / 2.0f), 0);
            }
            if (flipped) {
                StardewGuiUtil.drawFromCursorsTintFlipped(
                    graphics, drawX, drawY,
                    sourceX + frame * sourceWidth, sourceY,
                    sourceWidth, sourceHeight, scale,
                    red, green, blue, alpha);
            } else {
                StardewGuiUtil.drawFromCursorsTint(
                    graphics, drawX, drawY,
                    sourceX + frame * sourceWidth, sourceY,
                    sourceWidth, sourceHeight, scale,
                    red, green, blue, alpha);
            }
            if (rotated) {
                graphics.pose().popPose();
            }
        }
    }
}
